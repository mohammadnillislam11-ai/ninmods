#include <sys/socket.h>
#include <sys/un.h>
#include "NinliteEngine.h"
#include "Includes/Logger.h"
#include "Includes/JavaShell.h"
#include "GameEngine.h"
#include <dirent.h>
#include <unistd.h>
#include <atomic>
#include <thread>
#include <chrono>
#include <string>
#include <cstdio>
#include <cstring>

namespace NinliteEngine {

static bool g_ninlite_setup_done_flag = false;
static std::atomic<bool> g_ninlite_starting{false};
static std::atomic<bool> g_ninlite_engine_started{false};

/**
 * Sends a control request to the root Daemon.
 *
 * The Daemon expects a length prefixed BINARY frame.
 * The protocol is shared with the Daemon through
 * Includes/NinliteProtocol.h.
 */
void SyncFeature(int featId, bool enabled, int value) {
    DaemonSyncRequest(featId, enabled, value);
}

bool CheckEmulatorDetected() {
    static int s_emuState = -1;
    if (s_emuState != -1) return s_emuState == 1;

    if (access("/dev/qemu_pipe", F_OK) == 0 ||
        access("/dev/vboxguest", F_OK) == 0 ||
        access("/dev/vboxuser", F_OK) == 0 ||
        access("/system/bin/nox-prop", F_OK) == 0 ||
        access("/system/bin/androVM-prop", F_OK) == 0) {
        s_emuState = 1;
        return true;
    }
    FILE* f = fopen("/system/build.prop", "r");
    if (f) {
        char line[256];
        while (fgets(line, sizeof(line), f)) {
            if (strstr(line, "ro.kernel.qemu") ||
                strstr(line, "vbox") ||
                strstr(line, "goldfish") ||
                strstr(line, "genymotion") ||
                strstr(line, "bluestacks")) {
                fclose(f);
                s_emuState = 1;
                return true;
            }
        }
        fclose(f);
    }
    s_emuState = 0;
    return false;
}

const char* GetConnectionModeStr() {
    pid_t pid = target_pid.load(std::memory_order_relaxed);
    if (pid <= 0) pid = NinDetection::GetPid();
    if (pid > 0) {
        return "Official Linux (process_vm_readv)";
    }
    if (isNinLiteDaemonAvailable()) {
        return "Root Daemon (Standby)";
    }
    return "Official Linux /proc (Standby)";
}

bool CheckShellDaemonRunning() {
    /* Non destructive liveness probe (persistent, shared connection). */
    return isNinLiteDaemonAvailable();
}

void StartEngine(const std::string& nativeLibDir) {
    if (g_ninlite_starting.exchange(true)) {
        return; // Already in progress
    }

    std::thread([nativeLibDir]() {
        /* --------------------------------------------------------------
         * 1. Root shell injection - EXACTLY ONCE.
         *
         * Every `su -c` invocation raises a fresh superuser prompt on the
         * device. When the daemon is already running (a previous Start, a
         * previous app launch), requesting root again would pop the prompt
         * again for nothing.
         *
         * The official flow is:
         *   - daemon not running -> ONE `su -c` with the injection script,
         *   - daemon running     -> no root request at all, just re-sync the
         *     engine state over the existing connection.
         * -------------------------------------------------------------- */
        if (isNinLiteDaemonAvailable()) {
            NIN_LOGI("NINLITE", "daemon already running - reusing the existing root session (no new superuser prompt)");
        } else {
            std::string script;
            script += "killall -9 ninlite_daemon >/dev/null 2>&1\n";
            script += "setenforce 0 >/dev/null 2>&1\n";

            const char* selinuxRules[] = {
                "allow su untrusted_app process ptrace",
                "allow su untrusted_app_32 process ptrace",
                "allow su untrusted_app_30 process ptrace",
                "allow su untrusted_app_29 process ptrace",
                "allow su priv_app process ptrace",
                "allow su untrusted_app file { read write open getattr }",
                "allow su untrusted_app_32 file { read write open getattr }",
                "allow su untrusted_app_30 file { read write open getattr }",
                "allow su priv_app file { read write open getattr }",
                "allow su su file { execute execute_no_trans open read write }"
            };
            script += "if command -v magiskpolicy >/dev/null 2>&1; then\n";
            for (const char* rule : selinuxRules) {
                script += "  magiskpolicy --live ";
                script += rule;
                script += " >/dev/null 2>&1\n";
            }
            script += "elif command -v supolicy >/dev/null 2>&1; then\n";
            for (const char* rule : selinuxRules) {
                script += "  supolicy --live ";
                script += rule;
                script += " >/dev/null 2>&1\n";
            }
            script += "fi\n";

            const std::string daemonLibPath = nativeLibDir + "/libninlite_daemon.so";
            const std::string daemonAltPath = nativeLibDir + "/ninlite_daemon";
            const std::string finalExecPath = "/data/local/tmp/ninlite_daemon";
            /* Rule 15: the Daemon log also belongs in the Download folder. */
            const std::string daemonLog = "/storage/emulated/0/Download/ninmods/ninlite_daemon.log";

            script += "mkdir -p /storage/emulated/0/Download/ninmods >/dev/null 2>&1\n";
            script += "if [ -f " + daemonLibPath + " ]; then cp -f " + daemonLibPath + " " + finalExecPath + " >/dev/null 2>&1; fi\n";
            script += "if [ ! -f " + finalExecPath + " ] && [ -f " + daemonAltPath + " ]; then cp -f " + daemonAltPath + " " + finalExecPath + " >/dev/null 2>&1; fi\n";
            script += "chmod 755 " + finalExecPath + " >/dev/null 2>&1\n";
            script += "nohup " + finalExecPath + " >>" + daemonLog + " 2>&1 &\n";

            int rc = RunRootScriptViaJava(script);
            if (rc != 0) {
                NIN_LOGE("NINLITE", "su -c returned %d - daemon launch failed", rc);
            }

            /* 2. Poll up to ~3 seconds for the Daemon socket (non destructive). */
            for (int i = 0; i < 30; ++i) {
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
                if (isNinLiteDaemonAvailable()) {
                    break;
                }
            }
        } /* end: injection done */

        /* 3. Sync engine state over the shared binary protocol. */
        SyncFeature(Ninlite::MODE_MASTER, true, 0);
        Actived.activar = true;
        g_ninlite_engine_started = true;
        g_ninlite_starting = false;
    }).detach();
}

void StopEngine() {
    SyncFeature(Ninlite::MODE_MASTER, false, 0);
    Actived.activar = false;
    SyncFeature(Ninlite::MODE_STOP, false, 0);
    g_ninlite_engine_started = false;
}

bool IsSetupDone() {
    return g_ninlite_setup_done_flag;
}

std::string GetStateJson() {
    bool isStarting = g_ninlite_starting.load();
    bool isEngineStarted = g_ninlite_engine_started.load();

    NinDetection::GameProcess detected = NinDetection::Detect();
    pid_t pid = detected.pid;
    if (pid > 0) target_pid.store(pid, std::memory_order_relaxed);
    bool gameDetected = (detected.isDetected && pid > 0);

    const char *gameName = "Waiting for Game...";
    if (gameDetected) {
        gameName = detected.gameTitle.c_str();
    }

    bool isEmu = CheckEmulatorDetected();
    const char *connMode = GetConnectionModeStr();

    std::string statusText;
    std::string buttonText;
    bool canStart = false;

    if (isEngineStarted) {
        if (gameDetected) {
            statusText = std::string("Connected: ") + gameName + " (PID " + std::to_string(pid) + ")";
            buttonText = "Active ✓";
        } else {
            statusText = "Engine Active (Waiting for Game...)";
            buttonText = "Active ✓";
        }
        canStart = false;
        g_ninlite_setup_done_flag = true;
    } else if (isStarting) {
        statusText = "Injecting Root Shell & Engine...";
        buttonText = "Starting...";
        canStart = false;
    } else if (gameDetected) {
        statusText = std::string("Game Detected: ") + gameName + " (Ready to Start)";
        buttonText = "Start";
        canStart = true;
    } else {
        statusText = "Standby (Tap Start to Initialize Engine)";
        buttonText = "Start";
        canStart = true;
    }

    char buf[2048];
    snprintf(buf, sizeof(buf),
             "{\"isStarting\":%s,\"isEngineStarted\":%s,\"canStart\":%s,\"gameDetected\":%s,\"gamePid\":%d,\"gameTitle\":\"%s\","
             "\"isEmulator\":%s,\"connectionMode\":\"%s\",\"statusText\":\"%s\",\"buttonText\":\"%s\","
             "\"readyToDismiss\":false}",
             isStarting ? "true" : "false",
             isEngineStarted ? "true" : "false",
             canStart ? "true" : "false",
             gameDetected ? "true" : "false",
             (int)pid,
             gameName,
             isEmu ? "true" : "false",
             connMode,
             statusText.c_str(),
             buttonText.c_str());

    return std::string(buf);
}

} // namespace NinliteEngine
