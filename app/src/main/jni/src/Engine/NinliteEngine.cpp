#include <sys/socket.h>
#include <sys/un.h>
#include "NinliteEngine.h"
#include "Includes/Logger.h"
#include "Includes/driver.h"
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

void SyncFeature(int featId, bool enabled, int value) {
    int sock = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (sock < 0) return;

    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';
    strcpy(&addr.sun_path[1], "ninlite_daemon_stream");

    struct timeval tv{0, 200000};
    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));

    if (connect(sock, (struct sockaddr*)&addr, sizeof(sa_family_t) + 1 + strlen("ninlite_daemon_stream")) == 0) {
        char pkt[64];
        snprintf(pkt, sizeof(pkt), "FEAT:%d:%d:%d\n", featId, enabled ? 1 : 0, value);
        send(sock, pkt, strlen(pkt), 0);
    }
    close(sock);
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
    if (driver && driver->initDriver()) {
        return driver->getDriverModeName();
    }
    int sock = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (sock >= 0) {
        sockaddr_un addr{};
        addr.sun_family = AF_UNIX;
        addr.sun_path[0] = '\0';
        strcpy(&addr.sun_path[1], "ninlite_daemon_stream");
        struct timeval tv{0, 150000};
        setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
        setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
        if (connect(sock, (struct sockaddr*)&addr, sizeof(sa_family_t) + 1 + strlen("ninlite_daemon_stream")) == 0) {
            close(sock);
            return "Phantom IPC (Daemon)";
        }
        close(sock);
    }
    return "Phantom (Standby)";
}

bool CheckShellDaemonRunning() {
    int sock = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (sock >= 0) {
        sockaddr_un addr{};
        addr.sun_family = AF_UNIX;
        addr.sun_path[0] = '\0';
        strcpy(&addr.sun_path[1], "ninlite_daemon_stream");
        struct timeval tv{0, 150000};
        setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
        setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
        bool ok = (connect(sock, (struct sockaddr*)&addr, sizeof(sa_family_t) + 1 + strlen("ninlite_daemon_stream")) == 0);
        close(sock);
        if (ok) return true;
    }
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
        // 1. Root Shell Injection via native popen
        FILE* su = popen("su", "w");
        if (su) {
            fprintf(su, "killall -9 ninlite_daemon >/dev/null 2>&1\n");
            fprintf(su, "setenforce 0 >/dev/null 2>&1\n");
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
            fprintf(su, "if command -v magiskpolicy >/dev/null 2>&1; then\n");
            for (const char* rule : selinuxRules) {
                fprintf(su, "    magiskpolicy --live \"%s\" >/dev/null 2>&1\n", rule);
            }
            fprintf(su, "elif command -v supolicy >/dev/null 2>&1; then\n");
            for (const char* rule : selinuxRules) {
                fprintf(su, "    supolicy --live \"%s\" >/dev/null 2>&1\n", rule);
            }
            fprintf(su, "fi\n");

            std::string daemonLibPath = nativeLibDir + "/libninlite_daemon.so";
            const char* finalExecPath = "/data/local/tmp/ninlite_daemon";
            fprintf(su, "if [ -f \"%s\" ]; then\n", daemonLibPath.c_str());
            fprintf(su, "    cp -f \"%s\" %s\n", daemonLibPath.c_str(), finalExecPath);
            fprintf(su, "fi\n");
            fprintf(su, "chmod 777 %s\n", finalExecPath);
            fprintf(su, "nohup %s >/dev/null 2>&1 &\n", finalExecPath);
            fprintf(su, "exit\n");
            fflush(su);
            pclose(su);
        }

        // 2. Poll up to 2 seconds for daemon socket
        for (int i = 0; i < 20; ++i) {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
            int sock = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
            if (sock >= 0) {
                sockaddr_un addr{};
                addr.sun_family = AF_UNIX;
                addr.sun_path[0] = '\0';
                strcpy(&addr.sun_path[1], "ninlite_daemon_stream");
                if (connect(sock, (struct sockaddr*)&addr, sizeof(sa_family_t) + 1 + strlen("ninlite_daemon_stream")) == 0) {
                    close(sock);
                    break;
                }
                close(sock);
            }
        }

        // 3. Sync engine
        SyncFeature(3, true, 0);
        if (driver) {
            driver->initDriver();
        }
        Actived.activar = true;
        g_ninlite_starting = false;
    }).detach();
}

void StopEngine() {
    SyncFeature(3, false, 0);
    Actived.activar = false;
}

bool IsSetupDone() {
    return g_ninlite_setup_done_flag;
}

std::string GetStateJson() {
    bool shellOk = CheckShellDaemonRunning();
    bool driverOk = (driver && driver->isConnected() && driver->getFd() >= 0);
    if (!driverOk && driver) {
        driverOk = driver->initDriver();
    }
    bool shellActive = shellOk || driverOk;

    pid_t pid = (target_pid > 0 && kill(target_pid, 0) == 0) ? target_pid : FindGamePid();
    if (pid > 0) target_pid = pid;
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
