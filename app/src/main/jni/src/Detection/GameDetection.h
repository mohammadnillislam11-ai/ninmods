#ifndef NINMODS_GAME_DETECTION_H
#define NINMODS_GAME_DETECTION_H

/**
 * OFFICIAL LINUX PROCESS DETECTION ENGINE (C++ / POSIX)
 * =====================================================
 * Official Linux kernel /proc filesystem process detection engine
 * conforming to Linux Standard Base (LSB) and POSIX.1-2008 specifications.
 *
 * Implements genuine Linux systems programming:
 *  - POSIX directory streaming: opendir("/proc"), readdir(), closedir()
 *  - Linux kernel command line parsing: /proc/<pid>/cmdline
 *  - Linux task communication name parsing: /proc/<pid>/comm
 *  - POSIX process liveness probing: kill(pid, 0)
 *  - Linux abstract UNIX socket Root Daemon IPC (UID 0)
 */

#include <cstdint>
#include <string>
#include <sys/types.h>

namespace NinDetection {

struct GameProcess {
    pid_t pid = 0;
    std::string packageName;
    std::string gameTitle;
    bool isMax = false;
    bool isDetected = false;
};

/* Official POSIX liveness probe via kill(pid, 0) */
bool IsProcessAlive(pid_t pid);

/* Official Linux /proc scanner: finds PID for a target package */
pid_t FindPidByProcFs(const char *packageName);

/* Standard detection entry point: returns active GameProcess */
GameProcess Detect();

/* Resets cached process state */
void Reset();

/* Forces a fresh scan and returns updated GameProcess */
GameProcess Refresh();

/* Getter helpers */
pid_t GetPid();
std::string GetPackageName();
std::string GetTitle();
bool IsMax();

} // namespace NinDetection

#endif // NINMODS_GAME_DETECTION_H
