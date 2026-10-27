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
 * so the scan found nothing - the app is NOT root, the game is. The engine
 * therefore has three sources and uses them in this order:
 *
 *   1. CACHE      - the pid that was found before and is still alive.
 *   2. ROOT_SHELL - the same /proc scan, but with root rights (su). The
 *                   app already owns a root grant (the driver loader needs
 *                   it), so the scan can read every cmdline.
 *   3. PROC_SCAN  - the plain /proc scan with the rights of the app. It
 *                   still works when the game runs in the same user.
 *
 * The result is cached and the root scan is throttled, so the detection
 * never spawns a root shell more than once every 1.5 seconds.
 *
 * The daemon (which runs as root and is already connected - see the
 * telemetry panel) is asked first: it knows the game process because it
 * scans /proc with root rights. That is the cheapest and most reliable
 * source, and it needs no extra privileges at all.
 */
namespace NinDetection {

enum class DetectionSource {
    NONE = 0,
    CACHE,       /* the pid of the previous scan is still alive */
    DAEMON,      /* the root daemon reported the game process */
    ROOT_SHELL,  /* /proc scan with root rights */
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
 * Runs the full detection (cache -> root shell -> plain scan) and returns
 * the current state.
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
