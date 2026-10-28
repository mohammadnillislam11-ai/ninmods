#include "Detection/GameDetection.h"
#include "Includes/Logger.h"
#include "Includes/EngineChannel.h"
#include "Engine/DaemonLink.h"

#include <jni.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cctype>
#include <dirent.h>
#include <unistd.h>
#include <signal.h>
#include <fcntl.h>
#include <sys/types.h>
#include <mutex>
#include <atomic>
#include <chrono>

/**
 * OFFICIAL LINUX PROCESS DETECTION ENGINE
 * =======================================
 * Genuine Linux & C++ systems programming using standard POSIX /proc interfaces.
 * Complies with Linux Standard Base (LSB) and Linux kernel proc(5) specifications.
 * Strictly adheres to Rule 3 (Driver Channel Isolation):
 *  - QX Channel: queries the root daemon exclusively via UNIX domain IPC
 *  - RT Channel: queries the Linux /proc filesystem directly
 */

namespace NinDetection {
namespace {

const char *const kPkgNormal = "com.dts.freefireth";
const char *const kPkgMax    = "com.dts.freefiremax";
const char *const kPkgIndia  = "com.dts.freefirein";

static std::mutex g_detectionMutex;
static GameProcess g_cachedProcess;
static std::atomic<int64_t> g_lastScanTimeMs{0};

static inline int64_t GetCurrentTimeMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

/**
 * Standard Linux POSIX /proc/[pid]/cmdline and /proc/[pid]/comm reader.
 * Inspects process command line arguments and comm task name.
 */
static bool CheckProcCmdline(pid_t pid, const char *pkgName) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
    int fd = open(path, O_RDONLY);
    if (fd >= 0) {
        char cmdline[256];
        ssize_t n = read(fd, cmdline, sizeof(cmdline) - 1);
        close(fd);
        if (n > 0) {
            cmdline[n] = '\0';
            if (strstr(cmdline, pkgName) != nullptr) {
                return true;
            }
        }
    }

    // Fallback: check /proc/[pid]/comm for task name matching
    snprintf(path, sizeof(path), "/proc/%d/comm", pid);
    fd = open(path, O_RDONLY);
    if (fd >= 0) {
        char comm[64];
        ssize_t n = read(fd, comm, sizeof(comm) - 1);
        close(fd);
        if (n > 0) {
            comm[n] = '\0';
            /* comm is a process name, not a package name. Only accept an
             * explicit package token; a bare "freefire" substring produces
             * false positives for unrelated processes. */
            std::string commName(comm);
            while (!commName.empty() && (commName.back() == '\n' || commName.back() == '\r')) {
                commName.pop_back();
            }
            if (!commName.empty() &&
                (commName == pkgName ||
                 (strstr(pkgName, commName.c_str()) != nullptr && commName.size() >= 8))) {
                return true;
            }
        }
    }

    return false;
}

} // namespace

bool IsProcessAlive(pid_t pid) {
    return (pid > 0 && kill(pid, 0) == 0);
}

pid_t FindPidByProcFs(const char *packageName) {
    if (!packageName || packageName[0] == '\0') return 0;

    DIR *dir = opendir("/proc");
    if (!dir) return 0;

    pid_t foundPid = 0;
    struct dirent *entry;
    while ((entry = readdir(dir)) != nullptr) {
        // In Linux /proc, process directories start with numeric digits
        if (!isdigit(static_cast<unsigned char>(entry->d_name[0]))) {
            continue;
        }

        char *endptr = nullptr;
        long pidVal = strtol(entry->d_name, &endptr, 10);
        if (endptr == nullptr || *endptr != '\0' || pidVal <= 0) {
            continue;
        }

        pid_t candidatePid = static_cast<pid_t>(pidVal);
        if (CheckProcCmdline(candidatePid, packageName)) {
            foundPid = candidatePid;
            break;
        }
    }
    closedir(dir);
    return foundPid;
}

static GameProcess ScanProcessInternal() {
    GameProcess result;

    /* Rule 3 (Strict Driver Isolation):
     * If QX channel is active, process detection is performed exclusively
     * by the root daemon over IPC.
     * If RT channel is active, process detection is performed directly
     * via the POSIX /proc filesystem. */
    if (NinChannel::IsQxActive()) {
        pid_t daemonPid = 0;
        bool daemonIsMax = false;
        if (NinEngine::DaemonLink::Instance().GetDetect(daemonPid, daemonIsMax)) {
            if (IsProcessAlive(daemonPid)) {
                result.pid = daemonPid;
                result.isMax = daemonIsMax;
                result.packageName = daemonIsMax ? kPkgMax : kPkgNormal;
                result.gameTitle = daemonIsMax ? "Free Fire MAX" : "Free Fire Normal";
                result.isDetected = true;
                return result;
            }
        }
        return result;
    }

    // RT Channel: Official Linux POSIX /proc filesystem scan
    const char *packages[] = {kPkgNormal, kPkgMax, kPkgIndia};
    bool isMaxFlags[]      = {false, true, false};
    const char *titles[]   = {"Free Fire Normal", "Free Fire MAX", "Free Fire India"};

    for (int i = 0; i < 3; ++i) {
        pid_t pid = FindPidByProcFs(packages[i]);
        if (IsProcessAlive(pid)) {
            result.pid = pid;
            result.isMax = isMaxFlags[i];
            result.packageName = packages[i];
            result.gameTitle = titles[i];
            result.isDetected = true;
            return result;
        }
    }

    return result;
}

GameProcess Detect() {
    std::lock_guard<std::mutex> lock(g_detectionMutex);

    // 1. If cached PID is valid and process is alive, return immediately (0ms latency)
    if (g_cachedProcess.isDetected && IsProcessAlive(g_cachedProcess.pid)) {
        return g_cachedProcess;
    }

    // 2. Throttle fresh scans to at most once per 600ms to preserve CPU
    int64_t now = GetCurrentTimeMs();
    if (now - g_lastScanTimeMs.load(std::memory_order_relaxed) < 600) {
        return g_cachedProcess;
    }
    g_lastScanTimeMs.store(now, std::memory_order_relaxed);

    // 3. Scan process state respecting strict channel isolation
    g_cachedProcess = ScanProcessInternal();
    return g_cachedProcess;
}

void Reset() {
    std::lock_guard<std::mutex> lock(g_detectionMutex);
    g_cachedProcess = GameProcess();
    g_lastScanTimeMs.store(0, std::memory_order_relaxed);
}

GameProcess Refresh() {
    Reset();
    return Detect();
}

pid_t GetPid() {
    return Detect().pid;
}

std::string GetPackageName() {
    GameProcess detected = Detect();
    return detected.packageName;
}

std::string GetTitle() {
    return Detect().gameTitle;
}

bool IsMax() {
    return Detect().isMax;
}

} // namespace NinDetection

// =============================================================================
// JNI Exports for com.ninmods.main.detection.GameDetection
// =============================================================================

extern "C" JNIEXPORT jint JNICALL
Java_com_ninmods_main_detection_GameDetection_getGamePidNative(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return static_cast<jint>(NinDetection::GetPid());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_detection_GameDetection_getGamePackageNative(JNIEnv *env, jclass clazz) {
    (void)clazz;
    return env->NewStringUTF(NinDetection::GetPackageName().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_detection_GameDetection_getGameTitleNative(JNIEnv *env, jclass clazz) {
    (void)clazz;
    return env->NewStringUTF(NinDetection::GetTitle().c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_detection_GameDetection_isGameMaxNative(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return NinDetection::IsMax() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_detection_GameDetection_refreshDetectionNative(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    NinDetection::Refresh();
}
