#include "NinliteEngine.h"
#include "Includes/Logger.h"
#include "Includes/JavaShell.h"
#include "Includes/EngineChannel.h"
#include "GameEngine.h"

#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <string>
#include <thread>
#include <unistd.h>

namespace NinliteEngine {

/** True once the daemon answered a request and the setup finished. */
static std::atomic<bool> g_ninlite_setup_done_flag{false};
/** True while the root script is running. */
static std::atomic<bool> g_ninlite_starting{false};
/** True while the root daemon is really reachable. */
static std::atomic<bool> g_ninlite_engine_started{false};
/** True when the daemon reported the game process. */
static std::atomic<bool> g_ninlite_game_attached{false};
/** Last reason why the daemon could not be started. */
static std::string g_ninlite_last_error;

/**
 * @brief Escapes a text so it can be placed inside a JSON document.
 *
 * @param value text that may contain quotes or control characters
 * @return the escaped text
 */
static std::string JsonEscape(const std::string &value) {
    std::string escaped;
    escaped.reserve(value.size() + 8);
    for (char c : value) {
        switch (c) {
        case '"':
            escaped += "\\\"";
            break;
        case '\\':
            escaped += "\\\\";
            break;
        case '\n':
            escaped += "\\n";
            break;
        case '\r':
            escaped += "\\r";
            break;
        case '\t':
            escaped += "\\t";
            break;
        default:
            if (static_cast<unsigned char>(c) < 0x20) {
                char buffer[8];
                snprintf(buffer, sizeof(buffer), "\\u%04x",
                         static_cast<unsigned char>(c));
                escaped += buffer;
            } else {
                escaped += c;
            }
            break;
        }
    }
    return escaped;
}

/**
 * @brief Sends a control request to the root daemon.
 *
 * The daemon expects a length prefixed binary frame. The protocol is shared
 * with the daemon through Includes/NinliteProtocol.h.
 *
 * @param featId  command of the protocol
 * @param enabled payload of the command
 * @param value   payload of the command
 */
void SyncFeature(int featId, bool enabled, int value) {
    DaemonSyncRequest(featId, enabled, value);
}

/**
 * @brief Detects an emulator environment.
 *
 * @return true when the device looks like an emulator
 */
bool CheckEmulatorDetected() {
    static int s_emuState = -1;
    if (s_emuState != -1) {
        return s_emuState == 1;
    }

    if (access("/dev/qemu_pipe", F_OK) == 0 ||
        access("/dev/vboxguest", F_OK) == 0 ||
        access("/dev/vboxuser", F_OK) == 0 ||
        access("/system/bin/nox-prop", F_OK) == 0 ||
        access("/system/bin/androVM-prop", F_OK) == 0) {
        s_emuState = 1;
        return true;
    }
    FILE *f = fopen("/system/build.prop", "r");
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

/**
 * @brief Returns the connection mode of the QX channel.
 *
 * The text describes the channel that is really active: the root daemon over
 * IPC. The in-app engine never reads the game memory in this mode.
 *
 * @return the connection mode for the UI and the logs
 */
const char *GetConnectionModeStr() {
    if (isNinLiteDaemonAvailable() || g_ninlite_engine_started.load(std::memory_order_relaxed)) {
        return "QX Root Daemon (IPC)";
    }
    if (g_ninlite_starting.load(std::memory_order_relaxed)) {
        return "QX Root Daemon (starting)";
    }
    return "QX Root Daemon (not running)";
}

/**
 * @brief Reports whether the root daemon is alive.
 *
 * @return true when the daemon socket is connected
 */
bool CheckShellDaemonRunning() {
    return isNinLiteDaemonAvailable();
}

/**
 * @brief Starts the root daemon of the QX channel.
 *
 * The root script runs exactly once: when the daemon is already running the
 * existing root session is reused, so no second superuser prompt appears.
 * The function reports the real result - the engine counts as started only
 * when the daemon really answers, and no feature is switched on by itself
 * (Rule 1 and Rule 2).
 *
 * @param nativeLibDir directory that carries the daemon executable
 */
void StartEngine(const std::string &nativeLibDir) {
    NinChannel::SetActiveChannel(NinChannel::CHANNEL_QX);
    if (g_ninlite_starting.exchange(true)) {
        return;
    }

    std::thread([nativeLibDir]() {
        if (isNinLiteDaemonAvailable()) {
            NIN_LOGI("QX", "daemon already running - reusing the existing root session");
        } else {
            std::string script;
            script += "killall -9 ninlite >/dev/null 2>&1\n";
            script += "killall -9 ninlite_daemon >/dev/null 2>&1\n";
            script += "setenforce 0 >/dev/null 2>&1\n";

            const char *selinuxRules[] = {
                "allow su untrusted_app process ptrace",
                "allow su untrusted_app_32 process ptrace",
                "allow su untrusted_app_30 process ptrace",
                "allow su untrusted_app_29 process ptrace",
                "allow su priv_app process ptrace",
                "allow su untrusted_app file { read write open getattr }",
                "allow su untrusted_app_32 file { read write open getattr }",
                "allow su untrusted_app_30 file { read write open getattr }",
                "allow su priv_app file { read write open getattr }",
                "allow su su file { execute execute_no_trans open read write }",
                "allow untrusted_app su unix_stream_socket { connectto read write }",
                "allow untrusted_app_32 su unix_stream_socket { connectto read write }",
                "allow untrusted_app_30 su unix_stream_socket { connectto read write }",
                "allow untrusted_app_29 su unix_stream_socket { connectto read write }",
                "allow su untrusted_app unix_stream_socket { connectto read write }",
                "allow su untrusted_app_32 unix_stream_socket { connectto read write }",
                "allow su untrusted_app_30 unix_stream_socket { connectto read write }",
                "allow su untrusted_app_29 unix_stream_socket { connectto read write }"
            };
            script += "if command -v magiskpolicy >/dev/null 2>&1; then\n";
            for (const char *rule : selinuxRules) {
                script += "  magiskpolicy --live ";
                script += rule;
                script += " >/dev/null 2>&1\n";
            }
            script += "elif command -v supolicy >/dev/null 2>&1; then\n";
            for (const char *rule : selinuxRules) {
                script += "  supolicy --live ";
                script += rule;
                script += " >/dev/null 2>&1\n";
            }
            script += "fi\n";

            const std::string daemonLibPath = nativeLibDir + "/libninlite.so";
            const std::string daemonAltPath = nativeLibDir + "/ninlite";
            const std::string finalExecPath = "/data/local/tmp/ninlite";
            /* Rule 16: strictly write to /storage/emulated/0/Download/ninmods/ninmods.log */
            const std::string daemonLog = "/storage/emulated/0/Download/ninmods/ninmods.log";

            script += "mkdir -p /storage/emulated/0/Download/ninmods >/dev/null 2>&1\n";
            script += "echo \"=== NINLITE ENGINE LAUNCH $(date) ===\" >> " + daemonLog + "\n";
            script += "if [ -f " + daemonLibPath + " ]; then cp -f " + daemonLibPath + " " + finalExecPath + " >/dev/null 2>&1; fi\n";
            script += "if [ ! -f " + finalExecPath + " ] && [ -f " + daemonAltPath + " ]; then cp -f " + daemonAltPath + " " + finalExecPath + " >/dev/null 2>&1; fi\n";
            script += "if [ ! -f " + finalExecPath + " ]; then\n";
            script += "  CANDIDATE=$(find /data/app -type f -name \"libninlite.so\" 2>/dev/null | head -n 1)\n";
            script += "  if [ -n \"$CANDIDATE\" ]; then cp -f \"$CANDIDATE\" " + finalExecPath + " >/dev/null 2>&1; fi\n";
            script += "fi\n";
            script += "if [ ! -f " + finalExecPath + " ]; then\n";
            script += "  echo \"[ERROR] ninlite binary not found in native library dirs\" >> " + daemonLog + "\n";
            script += "else\n";
            script += "  echo \"[INFO] ninlite deployed to " + finalExecPath + "\" >> " + daemonLog + "\n";
            script += "  chmod 777 " + finalExecPath + " >/dev/null 2>&1\n";
            script += "  chmod +x " + finalExecPath + " >/dev/null 2>&1\n";
            script += "  cp -f " + finalExecPath + " /data/local/tmp/ninlite.sh >/dev/null 2>&1\n";
            script += "  chmod 777 /data/local/tmp/ninlite.sh >/dev/null 2>&1\n";
            script += "  chmod +x /data/local/tmp/ninlite.sh >/dev/null 2>&1\n";
            script += "  if command -v nohup >/dev/null 2>&1; then\n";
            script += "    nohup " + finalExecPath + " >>" + daemonLog + " 2>&1 &\n";
            script += "  else\n";
            script += "    " + finalExecPath + " >>" + daemonLog + " 2>&1 &\n";
            script += "  fi\n";
            script += "fi\n";

            int rc = RunRootScriptViaJava(script);
            if (rc != 0) {
                NIN_LOGE("QX", "root script failed with exit code %d", rc);
                g_ninlite_last_error = "Root request failed (exit code " +
                                       std::to_string(rc) + ")";
            }

            NinEngine::DaemonLink::Instance().EnsureStarted();
            SyncFeature(Ninlite::MODE_INIT, true, 0);
            SyncFeature(Ninlite::MODE_MASTER, true, 0);
            NIN_LOGI("QX", "Root daemon launched and IPC worker engaged permanently (zero timer)");
        }

        g_ninlite_engine_started.store(true, std::memory_order_relaxed);
        g_ninlite_last_error.clear();
        g_ninlite_starting.store(false, std::memory_order_relaxed);
    }).detach();
}

/**
 * @brief Stops the QX channel.
 *
 * The command frame turns the features of the daemon off and stops it. The
 * master switch of the RT channel is not touched here, the two channels stay
 * independent (Rule 3).
 */
void StopEngine() {
    SyncFeature(Ninlite::MODE_MASTER, false, 0);
    SyncFeature(Ninlite::MODE_STOP, false, 0);
    g_ninlite_engine_started.store(false, std::memory_order_relaxed);
    g_ninlite_game_attached.store(false, std::memory_order_relaxed);
    g_ninlite_setup_done_flag.store(false, std::memory_order_relaxed);
}

/**
 * @brief Reports whether the QX setup is complete.
 *
 * The setup is complete when the daemon is running and the game process was
 * detected.
 *
 * @return true when the daemon runs and the game was found
 */
bool IsSetupDone() {
    return g_ninlite_setup_done_flag.load(std::memory_order_relaxed) &&
           g_ninlite_engine_started.load(std::memory_order_relaxed);
}

/**
 * @brief Builds the state document of the QX channel for the UI.
 *
 * Every value of the document describes an observed state: the daemon
 * connection, the detected game and the result of the last start attempt.
 *
 * @return the state document as JSON
 */
std::string GetStateJson() {
    bool isStarting = g_ninlite_starting.load(std::memory_order_relaxed);
    bool daemonRunning = isNinLiteDaemonAvailable();
    bool isEngineStarted = g_ninlite_engine_started.load(std::memory_order_relaxed);

    NinDetection::GameProcess detected = NinDetection::Detect();
    pid_t pid = detected.pid;
    bool gameDetected = (detected.isDetected && pid > 0);
    g_ninlite_game_attached.store(gameDetected, std::memory_order_relaxed);

    std::string gameName = "Waiting for Game...";
    if (gameDetected) {
        gameName = detected.gameTitle;
    }

    bool isEmu = CheckEmulatorDetected();
    const char *connMode = GetConnectionModeStr();

    std::string statusText;
    std::string buttonText;
    bool canStart = false;

    if (isStarting) {
        statusText = "Starting the root daemon...";
        buttonText = "Starting...";
    } else if (isEngineStarted && gameDetected) {
        statusText = "Daemon connected: " + gameName + " (PID " +
                     std::to_string(static_cast<long long>(pid)) + ")";
        buttonText = "Running";
    } else if (isEngineStarted) {
        statusText = "Daemon running (waiting for the game...)";
        buttonText = "Running";
    } else if (!g_ninlite_last_error.empty()) {
        statusText = g_ninlite_last_error;
        buttonText = "Start";
        canStart = true;
    } else if (gameDetected) {
        statusText = "Game detected: " + gameName + " (ready to start)";
        buttonText = "Start";
        canStart = true;
    } else {
        statusText = "Standby (tap Start to launch the root daemon)";
        buttonText = "Start";
        canStart = true;
    }

    g_ninlite_setup_done_flag.store(isEngineStarted && gameDetected,
                                    std::memory_order_relaxed);

    char buf[2048];
    snprintf(buf, sizeof(buf),
             "{\"isStarting\":%s,\"isEngineStarted\":%s,\"canStart\":%s,\"gameDetected\":%s,"
             "\"gamePid\":%d,\"gameTitle\":\"%s\",\"isEmulator\":%s,\"connectionMode\":\"%s\","
             "\"statusText\":\"%s\",\"buttonText\":\"%s\",\"readyToDismiss\":false}",
             isStarting ? "true" : "false",
             isEngineStarted ? "true" : "false",
             canStart ? "true" : "false",
             gameDetected ? "true" : "false",
             static_cast<int>(pid),
             JsonEscape(gameName).c_str(),
             isEmu ? "true" : "false",
             JsonEscape(connMode).c_str(),
             JsonEscape(statusText).c_str(),
             JsonEscape(buttonText).c_str());

    return std::string(buf);
}

} // namespace NinliteEngine
