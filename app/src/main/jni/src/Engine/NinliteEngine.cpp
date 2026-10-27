#include <sys/socket.h>
#include <sys/un.h>
#include "NinliteEngine.h"
#include "Includes/Logger.h"
#include "Includes/driver.h"
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

/**
 * Sends a control request to the root Daemon.
 *
 * CRITICAL FIX: this used to send an ASCII payload ("FEAT:3:1:0\n") while the
 * Daemon expects a length prefixed BINARY frame. The Daemon therefore read
 * the four ASCII bytes as a ~1.1 GB frame length and smashed its own stack,
 * so the Daemon crashed the first time a feature was toggled - and
 * StartEngine() itself toggles the master switch right after launch.
 * The protocol is now shared with the Daemon through
 * Includes/NinliteProtocol.h.
 */
void SyncFeature(int featId, bool enabled, int value) {
    DaemonSyncRequest(featId, enabled, value);
}

bool CheckEmulatorDetected() {
    if (access("/dev/qemu_pipe", F_OK) == 0 ||
        access("/dev/vboxguest", F_OK) == 0 ||
        access("/dev/vboxuser", F_OK) == 0 ||
        access("/system/bin/nox-prop", F_OK) == 0 ||
        access("/system/bin/androVM-prop", F_OK) == 0) {
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
                return true;
            }
        }
        fclose(f);
    }
    return false;
}

const char* GetConnectionModeStr() {
    if (driver && driver->isConnected()) {
        return driver->getDriverModeName();
    }
    if (EnsureDriverConnectedThrottled()) {
        return driver->getDriverModeName();
    }
    /* Non destructive probe: the shared client keeps the connection open, so
     * the Daemon keeps serving instead of losing its listener. */
    if (isNinLiteDaemonAvailable()) {
        return "Phantom IPC (Daemon)";
    }
    return "Phantom (Standby)";
}

bool CheckShellDaemonRunning() {
    /* Non destructive liveness probe (persistent, shared connection). */
    if (isNinLiteDaemonAvailable()) {
        return true;
    }

    /* Fallback: look for the Daemon process itself. */
    DIR* dir = opendir("/proc");
    if (!dir) return false;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type == DT_DIR && isdigit(entry->d_name[0])) {
            char cmdPath[64];
            snprintf(cmdPath, sizeof(cmdPath), "/proc/%s/cmdline", entry->d_name);
            FILE* f = fopen(cmdPath, "r");
            if (f) {
                char cmdline[128] = {0};
                fgets(cmdline, sizeof(cmdline), f);
                fclose(f);
                if (strstr(cmdline, "ninlite_daemon")) {
                    closedir(dir);
                    return true;
                }
            }
        }
    }
    closedir(dir);
    return false;
}

void StartEngine(const std::string& nativeLibDir) {
    if (g_ninlite_starting.exchange(true)) {
        return; // Already in progress
    }

    std::thread([nativeLibDir]() {
        /* --------------------------------------------------------------
         * 1. Root shell injection.
         *
         * The previous implementation opened an INTERACTIVE `su` shell via
         * popen("su","w"). That shell never terminates on its own: it stays
         * open waiting for input, so the launching thread was parked forever
         * and a SECOND root prompt was raised next to the official one the
         * Java layer already requested (GEMINI.md rule 4).
         *
         * We now execute ONE non interactive `su -c '<script>'` command. It
         * uses the official superuser binary, it returns as soon as the
         * script is done, and the already granted root permission is reused
         * (no second prompt).
         * -------------------------------------------------------------- */
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
        script += "chmod 666 /dev/BYNb /dev/mem_tool /dev/nh /dev/wanbai >/dev/null 2>&1\n";
        script += "nohup " + finalExecPath + " >>" + daemonLog + " 2>&1 &\n";

        /* The script is run by the Java side (NativeBridge.runAsRoot).
         * The app process must not fork(): the Android sandbox answers a
         * plain fork() with SIGSYS and kills the process. Runtime.exec() in
         * Java uses the allowed posix_spawn() path. */
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

        /* 3. Sync engine state over the shared binary protocol. */
        SyncFeature(Ninlite::MODE_MASTER, true, 0);
        if (driver) {
            driver->initDriver();
        }
        Actived.activar = true;
        g_ninlite_starting = false;
    }).detach();
}

void StopEngine() {
    /* MODE_MASTER (3) only disables the feature flags; it does NOT stop the
     * Daemon. MODE_STOP is the request that actually shuts the Daemon down,
     * so both are sent - the master switch first, then the stop. */
    SyncFeature(Ninlite::MODE_MASTER, false, 0);
    Actived.activar = false;
    SyncFeature(Ninlite::MODE_STOP, false, 0);
}

bool IsSetupDone() {
    return g_ninlite_setup_done_flag;
}

std::string GetStateJson() {
    bool shellOk = CheckShellDaemonRunning();
    bool driverOk = (driver && driver->isConnected() && driver->getFd() >= 0);
    if (!driverOk) {
        driverOk = EnsureDriverConnectedThrottled();
    }
    bool shellActive = shellOk || driverOk;

    pid_t known = target_pid.load(std::memory_order_relaxed);
    pid_t pid = (known > 0 && kill(known, 0) == 0) ? known : FindGamePid();
    if (pid > 0) target_pid.store(pid, std::memory_order_relaxed);
    bool gameDetected = (pid > 0);

    const char *gameName = "Free Fire Max";
    if (pid > 0) {
        char cmdline[256] = {0};
        char path[64];
        snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
        FILE* f = fopen(path, "r");
        if (f) {
            fgets(cmdline, sizeof(cmdline), f);
            fclose(f);
        }
        if (strstr(cmdline, "com.dts.freefireth") || strstr(cmdline, "com.dts.freefirein")) {
            gameName = "Free Fire Normal";
        } else if (strstr(cmdline, "com.dts.freefiremax")) {
            gameName = "Free Fire Max";
        } else if (gameDetected) {
            gameName = "Free Fire Active";
        }
    } else {
        gameName = "Waiting for Game...";
    }

    bool isEmu = CheckEmulatorDetected();
    const char *connMode = GetConnectionModeStr();

    std::string statusText;
    std::string buttonText;
    bool readyToDismiss = false;

    if (shellActive && gameDetected) {
        statusText = std::string("Connected: ") + gameName + " (PID " + std::to_string(pid) + ")";
        buttonText = "Connected ✓";
        readyToDismiss = true;
        g_ninlite_setup_done_flag = true;
    } else if (shellActive) {
        statusText = "Shell Injected (Waiting for Game to Start...)";
        buttonText = "Shell Active (Start Game)";
    } else if (g_ninlite_starting.load()) {
        statusText = "Injecting Root Shell & Daemon...";
        buttonText = "Starting...";
    } else {
        statusText = "Standby (Click Start to Inject Shell)";
        buttonText = "Start";
    }

    char buf[2048];
    snprintf(buf, sizeof(buf),
             "{\"isStarting\":%s,\"shellActive\":%s,\"gameDetected\":%s,\"gamePid\":%d,\"gameTitle\":\"%s\","
             "\"isEmulator\":%s,\"connectionMode\":\"%s\",\"statusText\":\"%s\",\"buttonText\":\"%s\","
             "\"readyToDismiss\":%s}",
             g_ninlite_starting.load() ? "true" : "false",
             shellActive ? "true" : "false",
             gameDetected ? "true" : "false",
             (int)pid,
             gameName,
             isEmu ? "true" : "false",
             connMode,
             statusText.c_str(),
             buttonText.c_str(),
             readyToDismiss ? "true" : "false");

    return std::string(buf);
}

} // namespace NinliteEngine
