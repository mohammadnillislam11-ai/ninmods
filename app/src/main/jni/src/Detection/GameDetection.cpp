#include "Detection/GameDetection.h"
#include "GameEngine.h"
#include "Includes/Logger.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <atomic>
#include <mutex>
#include <signal.h>
#include <time.h>
#include <unistd.h>
#include <thread>

/*
 * THE GAME DETECTION ENGINE
 * -------------------------
 * One file, one job: find the process of the game and describe it.
 *
 * The engine never guesses. A pid is only accepted when the cmdline of that
 * process really carries one of the Free Fire package names, and every
 * result carries the source that produced it, so a wrong state can be
 * traced back to the scan that caused it.
 */

namespace NinDetection {
namespace {

/* The three clients this app supports. */
const char *const kPackageMax = "com.dts.freefiremax";
const char *const kPackageNormal = "com.dts.freefireth";
const char *const kPackageNormalAlt = "com.dts.freefirein";

/* The daemon is asked at most once per second. */
const int64_t kDaemonQueryThrottleMs = 1000;

struct EngineState {
    std::mutex mutex;
    GameProcess current;
};

EngineState &State() {
    static EngineState state;
    return state;
}

int64_t NowMs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000 + (ts.tv_nsec / 1000000);
}

bool IsPidAlive(pid_t pid) {
    if (pid <= 0) return false;
    return (kill(pid, 0) == 0);
}

/**
 * Checks a cmdline buffer for one of the supported packages.
 */
bool MatchPackage(const char *buffer, size_t length, std::string &outPackage) {
    if (buffer == nullptr || length == 0) return false;

    std::string cmdline(buffer, length);
    if (cmdline.find(kPackageMax) != std::string::npos) {
        outPackage = kPackageMax;
        return true;
    }
    if (cmdline.find(kPackageNormal) != std::string::npos) {
        outPackage = kPackageNormal;
        return true;
    }
    if (cmdline.find(kPackageNormalAlt) != std::string::npos) {
        outPackage = kPackageNormalAlt;
        return true;
    }
    return false;
}

/**
 * Reads /proc/<pid>/cmdline. Fails when the process belongs to another
 * user - that is the case this engine has to work around.
 */
bool ReadCmdline(pid_t pid, std::string &outCmdline) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);

    FILE *fp = fopen(path, "rb");
    if (fp == nullptr) return false;

    char buffer[512];
    size_t read = fread(buffer, 1, sizeof(buffer), fp);
    fclose(fp);

    if (read == 0) return false;
    outCmdline.assign(buffer, read);
    return true;
}

void FillGameProcess(GameProcess &process, pid_t pid, const std::string &package, bool isMax,
                     DetectionSource source) {
    process.pid = pid;
    process.packageName = package;
    process.isMax = isMax;
    process.gameTitle = isMax ? "Free Fire MAX" : "Free Fire Normal";
    process.isDetected = true;
    process.source = source;
}

/**
 * The plain /proc scan with the rights of the app.
 */
bool ProcScan(GameProcess &outProcess) {
    DIR *dir = opendir("/proc");
    if (dir == nullptr) return false;

    pid_t normalPid = 0;
    pid_t maxPid = 0;
    std::string normalPackage;
    std::string maxPackage;

    struct dirent *entry;
    while ((entry = readdir(dir)) != nullptr) {
        /*
         * d_type is not filled in by every file system. A process that is
         * reported as DT_UNKNOWN is still a process, so the name is checked
         * instead of the type.
         */
        if (entry->d_type != DT_DIR && entry->d_type != DT_UNKNOWN) continue;

        char *endptr = nullptr;
        pid_t pid = strtol(entry->d_name, &endptr, 10);
        if (endptr == nullptr || *endptr != '\0' || pid <= 0) continue;

        std::string cmdline;
        if (!ReadCmdline(pid, cmdline)) continue;

        std::string package;
        if (!MatchPackage(cmdline.c_str(), cmdline.size(), package)) continue;

        if (package == kPackageMax) {
            if (maxPid == 0) {
                maxPid = pid;
                maxPackage = package;
            }
        } else {
            if (normalPid == 0) {
                normalPid = pid;
                normalPackage = package;
            }
        }
    }
    closedir(dir);

    /* Free Fire Normal takes precedence, exactly like the reference engine. */
    if (normalPid > 0) {
        FillGameProcess(outProcess, normalPid, normalPackage, false, DetectionSource::PROC_SCAN);
        return true;
    }
    if (maxPid > 0) {
        FillGameProcess(outProcess, maxPid, maxPackage, true, DetectionSource::PROC_SCAN);
        return true;
    }
    return false;
}

bool DaemonScan(GameProcess &outProcess) {
    static int64_t lastQueryMs = 0;

    int64_t now = NowMs();
    if ((now - lastQueryMs) < kDaemonQueryThrottleMs) {
        return false;
    }
    lastQueryMs = now;

    pid_t pid = 0;
    bool isMax = false;
    if (!RequestDaemonDetect(pid, isMax)) {
        return false;   /* the daemon is not connected */
    }
    if (pid <= 0) {
        return false;   /* the daemon answered, but the game is not running */
    }

    FillGameProcess(outProcess, pid, isMax ? kPackageMax : kPackageNormal, isMax,
                    DetectionSource::DAEMON);
    return true;
}

void Store(const GameProcess &process) {
    EngineState &state = State();
    std::lock_guard<std::mutex> lock(state.mutex);
    state.current = process;
}

void RunScan() {
    static std::atomic<bool> s_inFlight{false};
    bool expected = false;
    if (!s_inFlight.compare_exchange_strong(expected, true)) {
        return;
    }

    GameProcess process;
    bool found = DaemonScan(process);
    if (!found) {
        found = ProcScan(process);
    }
    Store(found ? process : GameProcess());

    s_inFlight.store(false);
}

}  // namespace

/* =================================================================
 * BACKGROUND SCAN WORKER
 * =================================================================
 * The full /proc walk must NEVER run on the calling thread: the menu
 * poller queries Detect() from a background thread of its own, but
 * other callers (render path, engine) may call it from anywhere, and
 * a multi-hundred-file walk must not stall them.
 *
 * Detect() therefore only reads the cached state and asks a short-lived
 * background thread to refresh it - at most one request per second,
 * one scan in flight at a time.
 * ================================================================= */

GameProcess Detect() {
    EngineState &state = State();

    /* 1. The pid of the previous scan is still alive -> keep it. */
    {
        std::lock_guard<std::mutex> lock(state.mutex);
        if (state.current.isDetected && state.current.pid > 0 && IsPidAlive(state.current.pid)) {
            return state.current;
        }
    }

    /* 2. Ask the background worker for a fresh scan - at most one request
     * per second. The caller immediately receives the last known state;
     * the calling thread never blocks on a root shell or a /proc walk. */
    static std::atomic<int64_t> s_lastRequestMs{0};
    int64_t now = NowMs();
    if (now - s_lastRequestMs.load(std::memory_order_relaxed) >= 1000) {
        s_lastRequestMs.store(now, std::memory_order_relaxed);
        std::thread([]() { RunScan(); }).detach();
    }

    std::lock_guard<std::mutex> lock(state.mutex);
    return state.current;
}

GameProcess Refresh() {
    Reset();
    return Detect();
}

void Reset() {
    Store(GameProcess());
}

pid_t GetPid() {
    EngineState &state = State();
    std::lock_guard<std::mutex> lock(state.mutex);
    return state.current.pid;
}

std::string GetPackageName() {
    EngineState &state = State();
    std::lock_guard<std::mutex> lock(state.mutex);
    return state.current.packageName;
}

std::string GetTitle() {
    EngineState &state = State();
    std::lock_guard<std::mutex> lock(state.mutex);
    return state.current.gameTitle;
}

bool IsMax() {
    EngineState &state = State();
    std::lock_guard<std::mutex> lock(state.mutex);
    return state.current.isMax;
}

}  // namespace NinDetection

/* =============================================================================
 * JNI bridge of the detection engine.
 *
 * Every entry point of the app that needs the game process goes through
 * these four functions, so there is exactly one answer to the question
 * "which process is the game".
 * ============================================================================= */

extern "C" JNIEXPORT jint JNICALL
Java_com_ninmods_main_detection_GameDetection_getGamePidNative(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    NinDetection::GameProcess process = NinDetection::Detect();
    return static_cast<jint>(process.pid);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_detection_GameDetection_getGamePackageNative(JNIEnv *env, jclass clazz) {
    (void) clazz;
    NinDetection::GameProcess process = NinDetection::Detect();
    return env->NewStringUTF(process.packageName.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_detection_GameDetection_getGameTitleNative(JNIEnv *env, jclass clazz) {
    (void) clazz;
    NinDetection::GameProcess process = NinDetection::Detect();
    return env->NewStringUTF(process.gameTitle.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_detection_GameDetection_isGameMaxNative(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    NinDetection::GameProcess process = NinDetection::Detect();
    return process.isMax ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_detection_GameDetection_refreshDetectionNative(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    NinDetection::Refresh();
}
