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

/* The root shell must not be spawned more often than this. */
const int64_t kRootScanThrottleMs = 1500;

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

/**
 * The same scan, but with root rights.
 *
 * The app owns a root grant (the kernel driver loader needs it), so the
 * cmdline of every process can be read. The result is throttled, because
 * spawning su is expensive.
 */
bool RootShellScan(GameProcess &outProcess) {
    static int64_t lastScanMs = 0;
    static pid_t lastPid = 0;
    static bool lastIsMax = false;
    static std::string lastPackage;

    int64_t now = NowMs();
    if (lastPid > 0 && (now - lastScanMs) < kRootScanThrottleMs) {
        FillGameProcess(outProcess, lastPid, lastPackage, lastIsMax, DetectionSource::ROOT_SHELL);
        return true;
    }

    /*
     * The throttle also applies when nothing was found. Without it every
     * single call would spawn a root shell - the panel polls every 500 ms.
     */
    if ((now - lastScanMs) < kRootScanThrottleMs) {
        return false;
    }
    lastScanMs = now;

    static const char *kScript =
            "su -c 'for p in /proc/[0-9]*; do "
            "c=$(cat $p/cmdline 2>/dev/null | tr -d \"\\000\"); "
            "case \"$c\" in "
            "*com.dts.freefiremax*) echo \"${p#/proc/} max\" ;; "
            "*com.dts.freefireth*|*com.dts.freefirein*) echo \"${p#/proc/} normal\" ;; "
            "esac; done' 2>/dev/null";

    FILE *pipe = popen(kScript, "r");
    if (pipe == nullptr) return false;

    pid_t normalPid = 0;
    pid_t maxPid = 0;
    char line[256];
    while (fgets(line, sizeof(line), pipe) != nullptr) {
        char *endptr = nullptr;
        long pid = strtol(line, &endptr, 10);
        if (endptr == line || pid <= 0) continue;

        bool isMax = (strstr(line, "max") != nullptr);
        if (isMax) {
            if (maxPid == 0) maxPid = static_cast<pid_t>(pid);
        } else {
            if (normalPid == 0) normalPid = static_cast<pid_t>(pid);
        }
    }
    pclose(pipe);

    if (normalPid > 0) {
        lastPid = normalPid;
        lastIsMax = false;
        lastPackage = kPackageNormal;
        FillGameProcess(outProcess, normalPid, lastPackage, false, DetectionSource::ROOT_SHELL);
        return true;
    }
    if (maxPid > 0) {
        lastPid = maxPid;
        lastIsMax = true;
        lastPackage = kPackageMax;
        FillGameProcess(outProcess, maxPid, lastPackage, true, DetectionSource::ROOT_SHELL);
        return true;
    }
    return false;
}

/**
 * Asks the daemon for the game process.
 *
 * The daemon runs as root and scans /proc itself, so it can see the game
 * even though the app cannot. The answer is cached for a second, because
 * the panel polls the state every 500 ms.
 */
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

/* Is there a root binary at all? On a non-rooted device there is none,
 * so no scan may ever try to spawn a shell. Checked once, then cached. */
bool RootAvailable() {
    static int s_state = 0; /* 0 = unknown, 1 = yes, -1 = no */
    if (s_state != 0) return s_state == 1;
    const char *paths[] = {"/system/bin/su", "/system/xbin/su", "/system/sbin/su",
                           "/sbin/su", "/vendor/bin/su", "/data/adb/ksu/bin/su",
                           "/data/adb/ap/bin/su"};
    bool found = false;
    for (const char *p : paths) {
        if (access(p, F_OK) == 0 || access(p, X_OK) == 0) {
            found = true;
            break;
        }
    }
    s_state = found ? 1 : -1;
    return found;
}

/* The heavy scan. Runs on its own short-lived thread. */
void RunScan() {
    /* One scan at a time: if the previous one is stuck on a broken root
     * shell, a newer scan must not pile up behind it. */
    static std::atomic<bool> s_inFlight{false};
    bool expected = false;
    if (!s_inFlight.compare_exchange_strong(expected, true)) {
        return;
    }

    GameProcess process;
    bool found = DaemonScan(process);
    if (!found && RootAvailable()) {
        found = RootShellScan(process);
    }
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
 * The heavy scans (the root shell spawn, the full /proc walk) must
 * NEVER run on the calling thread. The menu polls Detect() from the
 * UI thread every 500 ms; a root shell that hangs (a broken or
 * half installed su binary) blocked that thread until Android killed
 * the app as "not responding" - and on a plain non-rooted device the
 * shell spawns plus the /proc walks ran needlessly on every poll.
 *
 * Detect() now only reads the cached state and asks a short-lived
 * background thread to refresh it - at most one request per second.
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
