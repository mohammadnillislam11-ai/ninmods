#pragma once

#include <cstdint>
#include <string>
#include <sys/types.h>

/**
 * DEDICATED GAME DETECTION ENGINE
 * -------------------------------
 * The detection of the game process lives in this file and nowhere else.
 *
 * Before this engine existed the detection was spread over three places:
 * the detector in jni/src/Detector, the pid lookup in Includes/Utils.h and
 * the title lookup in Client.cpp. Every one of them answered the question
 * "is the game running" on its own, with its own result - that is why the
 * telemetry panel could report "Waiting for Game" while the engine was
 * already reading the memory of the game.
 *
 * THE PROBLEM THIS ENGINE SOLVES
 * ------------------------------
 * The old detector only walked /proc and read /proc/<pid>/cmdline. Since
 * Android 9 an app may not read the cmdline of a process of another user,
 * so the scan found nothing - the app is NOT root, the game is.
 *
 * The official split of responsibilities is:
 *   - the DAEMON (root, started by the Start button) is the authority for
 *     the game process - the app asks it over the wire protocol
 *     (MODE_DETECT), the daemon scans /proc with root rights,
 *   - the plain /proc scan covers the cases where the app may read it
 *     (emulator, permissive SELinux, game in the same user).
 *
 * The engine therefore has two sources and uses them in this order:
 *
 *   1. CACHE      - the pid that was found before and is still alive.
 *   2. DAEMON     - the root daemon reports the game process.
 *   3. PROC_SCAN  - the plain /proc scan with the rights of the app.
 *
 * Detection NEVER spawns a root shell: every `su` invocation raises a
 * fresh superuser prompt on the device, and the old su-based scan produced
 * a prompt every 1.5 seconds while its pending prompt blocked all further
 * scans. Root is requested exactly once - by the Start button.
 */
namespace NinDetection {

enum class DetectionSource {
    NONE = 0,
    CACHE,       /* the pid of the previous scan is still alive */
    DAEMON,      /* the root daemon reported the game process */
    PROC_SCAN,   /* /proc scan with the rights of the app */
};

struct GameProcess {
    pid_t pid = 0;
    std::string packageName;
    std::string gameTitle;
    bool isMax = false;
    bool isDetected = false;
    DetectionSource source = DetectionSource::NONE;
};

/**
 * Returns the current detection state (cache -> daemon -> plain scan).
 * Never blocks: heavy scanning runs on a background thread.
 */
GameProcess Detect();

/**
 * Drops the cached state. The next Detect() starts from scratch.
 */
void Reset();

/**
 * Forces a new scan even when the cache is still valid.
 */
GameProcess Refresh();

/* --- the single accessors every other layer uses --- */
pid_t GetPid();

std::string GetPackageName();

std::string GetTitle();

bool IsMax();

}  // namespace NinDetection
