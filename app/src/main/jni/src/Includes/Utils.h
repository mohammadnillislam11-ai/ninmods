#ifndef UTILS
#define UTILS

#include <jni.h>
#include <unistd.h>
#include <cstdio>
#include <cstring>
#include <string>
#include <cstdlib>
#include <dirent.h>
#include <inttypes.h>
#include <atomic>
#include "Logger.h"
#include "NativeCrypto.h"
#include "MemoryAccess.h"
#include "TextCodec.h"
#include "TransformTypes.h"
#include "../Engine/OfficialMemory.h"

/* Which build of the game is currently attached.
 * false = Free Fire Normal (com.dts.freefireth / freefirein)
 * true  = Free Fire MAX    (com.dts.freefiremax) */
inline std::atomic<bool> g_isGameMax{false};

/* Anti-hook value, kept for the engine's existing call sites. */
inline uintptr_t anogs = 0;

/* Check if an Android process is actually running in active foreground. */
static bool IsProcessActiveForeground(pid_t pid) {
    char oomPath[64];
    snprintf(oomPath, sizeof(oomPath), "/proc/%d/oom_score_adj", (int)pid);
    FILE *fp = fopen(oomPath, "r");
    if (!fp) {
        /* Not readable on modern Android: assume active, never ignore a game. */
        return true;
    }
    int score = 1000;
    if (fscanf(fp, "%d", &score) == 1) {
        fclose(fp);
        /* Active foreground app: oom_score_adj <= 200. Cached: >= 900. */
        return score < 900;
    }
    fclose(fp);
    return true;
}

#include "Detection/GameDetection.h"

/* Finds the PID of the game process. The detection itself lives in
 * Detection/GameDetection.cpp and reads the root daemon's answer. */
inline pid_t FindGamePid() {
    NinDetection::GameProcess process = NinDetection::Detect();
    if (process.isDetected && process.pid > 0) {
        g_isGameMax.store(process.isMax, std::memory_order_relaxed);
        return process.pid;
    }
    return 0;
}

/* Finds a process by package name. A freefire package is answered by the
 * daemon; any other package is looked up in /proc/<pid>/cmdline. */
inline pid_t FindPid(const char *PackageName) {
    if (PackageName != nullptr && (strstr(PackageName, "freefire") != nullptr)) {
        return FindGamePid();
    }
    if (PackageName == nullptr) {
        return 0;
    }

    DIR *dir = opendir("/proc");
    if (dir == nullptr) {
        return 0;
    }

    pid_t found = 0;
    struct dirent *entry = nullptr;
    while ((entry = readdir(dir)) != nullptr) {
        char *endptr = nullptr;
        pid_t pid = strtol(entry->d_name, &endptr, 10);
        if (endptr == nullptr || *endptr != '\0' || pid <= 0) {
            continue;
        }
        char cmdlinePath[64];
        snprintf(cmdlinePath, sizeof(cmdlinePath), "/proc/%d/cmdline", (int)pid);
        FILE *fp = fopen(cmdlinePath, "rb");
        if (fp == nullptr) {
            continue;
        }
        char cmdline[256] = {0};
        size_t n = fread(cmdline, 1, sizeof(cmdline) - 1, fp);
        fclose(fp);
        if (n > 0 && strstr(cmdline, PackageName) != nullptr) {
            found = pid;
            break;
        }
    }
    closedir(dir);
    return found;
}

/* Base address of the n-th mapping whose path contains `name`. */
inline uintptr_t FindLibrary(const char *name, int index) {
    int i = 0;
    uintptr_t start = 0;
    char line[1024] = {0};
    char fname[128];
    snprintf(fname, sizeof(fname), "/proc/%d/maps", (int)target_pid.load());
    FILE *p = fopen(fname, "r");
    if (p) {
        while (fgets(line, sizeof(line), p)) {
            if (strstr(line, name) != nullptr) {
                i++;
                if (i == index) {
                    sscanf(line, "%" SCNxPTR, &start);
                    break;
                }
            }
        }
        fclose(p);
    }
    return start;
}

class MonoDictionary {
public:
    uintptr_t getValues() {
        return Read<uintptr_t>(reinterpret_cast<uintptr_t>(this) + 0x18) + 0x20;
    }

    int getNumValues() {
        return Read<int>(reinterpret_cast<uintptr_t>(this) + 0x20);
    }
};

#endif  // UTILS
