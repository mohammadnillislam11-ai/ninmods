#ifndef NINMODS_ENGINE_DAEMON_LINK_H
#define NINMODS_ENGINE_DAEMON_LINK_H

/*
 * DAEMON LINK - non blocking
 * --------------------------
 * ONE background worker owns the daemon socket. Every caller (render thread,
 * menu thread, feature toggles) only:
 *   - pushes a request into a queue (a few microseconds, short mutex), or
 *   - reads a cached answer (a few microseconds, separate short mutex).
 *
 * Nobody but the worker ever touches the socket, so a slow or dead daemon can
 * never freeze the render loop or the menu. The old code shared one mutex
 * (g_daemonMutex) between the render path, the menu poller, the toggle queue
 * and the scene worker; a blocking socket call inside it froze all of them.
 */

#include <atomic>
#include <chrono>
#include <cstring>
#include <deque>
#include <mutex>
#include <thread>
#include <cstdint>
#include <sys/types.h>

#include "Includes/NinliteProtocol.h"
#include "Includes/Logger.h"

namespace NinEngine {

struct DaemonCache {
    /* Last answer of MODE_ESP and MODE_DETECT, published by the worker. */
    Ninlite::Response esp{};
    bool espValid = false;
    pid_t detectedPid = 0;
    bool detectedIsMax = false;
    bool detectValid = false;
    bool connected = false;
};

class DaemonLink {
public:
    static DaemonLink& Instance() {
        static DaemonLink inst;
        return inst;
    }

    /* Starts the worker once. Safe to call from any thread, never blocks. */
    void EnsureStarted() {
        if (started_.exchange(true)) return;
        std::thread([this]() { Run(); }).detach();
    }

    /* Queues a control frame (feature toggle, slider). Never blocks. */
    void PushControl(int mode, bool enabled, int value) {
        std::lock_guard<std::mutex> lock(queueMutex_);
        ControlItem item;
        item.mode = mode;
        item.enabled = enabled;
        item.value = value;
        /* One pending item per mode: the newest value wins. */
        for (auto &q : controls_) {
            if (q.mode == mode) { q = item; return; }
        }
        controls_.push_back(item);
        EnsureStarted();
    }

    /* Requests a fresh ESP snapshot (the worker answers on its own pace). */
    void RequestEsp(int screenWidth, int screenHeight) {
        screenW_.store(screenWidth, std::memory_order_relaxed);
        screenH_.store(screenHeight, std::memory_order_relaxed);
        EnsureStarted();
    }

    /* Non-blocking read of the cached ESP snapshot. */
    bool GetEsp(Ninlite::Response &out) {
        std::lock_guard<std::mutex> lock(cacheMutex_);
        if (!cache_.espValid) return false;
        out = cache_.esp;
        return true;
    }

    /* Non-blocking read of the cached detection result. */
    bool GetDetect(pid_t &pid, bool &isMax) {
        std::lock_guard<std::mutex> lock(cacheMutex_);
        pid = cache_.detectedPid;
        isMax = cache_.detectedIsMax;
        return cache_.detectValid;
    }

    /* Non-blocking: true when the worker currently holds a live connection. */
    bool IsConnected() {
        EnsureStarted();
        std::lock_guard<std::mutex> lock(cacheMutex_);
        return cache_.connected;
    }

    /* Asks the worker to (re)check the daemon. Never blocks. */
    void RequestDetect() {
        detectWanted_.store(true, std::memory_order_relaxed);
        EnsureStarted();
    }

private:
    struct ControlItem {
        int mode = 0;
        bool enabled = false;
        int value = 0;
    };

    DaemonLink() = default;

    /* The only place that touches the socket. */
    void Run() {
        Ninlite::SocketClient client;
        auto lastEsp = std::chrono::steady_clock::now() - std::chrono::seconds(1);
        auto lastDetect = std::chrono::steady_clock::now() - std::chrono::seconds(1);
        auto lastConnect = std::chrono::steady_clock::now() - std::chrono::seconds(1);

        NIN_LOGI("DaemonLink", "Worker thread started - beginning IPC connection loop.");
        while (true) {
            /* 0. Keep the connection alive. Connect() runs ONLY here, on the
             * worker, at most once per 200ms during initial handshake. */
            if (!client.IsConnected()) {
                auto tryAt = std::chrono::steady_clock::now();
                if (tryAt - lastConnect >= std::chrono::milliseconds(200)) {
                    lastConnect = tryAt;
                    if (client.Connect()) {
                        std::lock_guard<std::mutex> lock(cacheMutex_);
                        cache_.connected = true;
                        NIN_LOGI("DaemonLink", "Connected to root daemon IPC stream successfully!");
                    }
                }
            }

            /* 1. Pending control frames (toggles, sliders) go first. */
            std::deque<ControlItem> work;
            {
                std::lock_guard<std::mutex> lock(queueMutex_);
                work.swap(controlsWork_);
                for (auto &c : controls_) work.push_back(c);
                controls_.clear();
            }
            for (auto &c : work) {
                Ninlite::Request req;
                memset(&req, 0, sizeof(req));
                req.mode = c.mode;
                req.enabled = c.enabled ? 1u : 0u;
                req.value = c.value;
                Ninlite::Response ignored;
                memset(&ignored, 0, sizeof(ignored));
                if (client.Request(req, &ignored)) {
                    NIN_LOGI("DaemonLink", "Control sent: mode %d, enabled %d, val %d", c.mode, (int)c.enabled, c.value);
                } else {
                    NIN_LOGE("DaemonLink", "Failed to send control: mode %d", c.mode);
                }
            }

            /* 2. Periodic ESP snapshot, at most ~30 Hz, only while asked. */
            auto now = std::chrono::steady_clock::now();
            if (now - lastEsp >= std::chrono::milliseconds(33)) {
                lastEsp = now;
                Ninlite::Request req;
                memset(&req, 0, sizeof(req));
                req.mode = Ninlite::MODE_ESP;
                req.enabled = 1;
                req.screenWidth = screenW_.load(std::memory_order_relaxed);
                req.screenHeight = screenH_.load(std::memory_order_relaxed);
                Ninlite::Response wire;
                memset(&wire, 0, sizeof(wire));
                bool ok = client.Request(req, &wire);
                std::lock_guard<std::mutex> lock(cacheMutex_);
                cache_.connected = client.IsConnected();
                if (ok) {
                    cache_.esp = wire;
                    cache_.espValid = true;
                } else {
                    cache_.espValid = false;
                }
            }

            /* 3. Detection: on request, and at a slow 1 Hz otherwise. */
            now = std::chrono::steady_clock::now();
            bool wantDetect = detectWanted_.exchange(false, std::memory_order_relaxed);
            if (wantDetect || now - lastDetect >= std::chrono::seconds(1)) {
                lastDetect = now;
                Ninlite::Request req;
                memset(&req, 0, sizeof(req));
                req.mode = Ninlite::MODE_DETECT;
                req.enabled = 1;
                Ninlite::Response wire;
                memset(&wire, 0, sizeof(wire));
                if (client.Request(req, &wire)) {
                    std::lock_guard<std::mutex> lock(cacheMutex_);
                    cache_.detectValid = true;
                    cache_.detectedPid = (wire.detectedPid > 0) ? (pid_t)wire.detectedPid : 0;
                    cache_.detectedIsMax = (strstr(wire.detectedPackage, "freefiremax") != nullptr);
                    cache_.connected = true;
                    if (wire.detectedPid > 0) {
                        NIN_LOGI("DaemonLink", "Daemon reports Game PID: %d (%s)", (int)wire.detectedPid, wire.detectedPackage);
                    }
                } else {
                    std::lock_guard<std::mutex> lock(cacheMutex_);
                    cache_.detectValid = false;
                    cache_.detectedPid = 0;
                }
            }

            /* 4. Idle pacing. Nothing to do -> sleep, never spin. */
            std::this_thread::sleep_for(std::chrono::milliseconds(8));
        }
    }

    std::atomic<bool> started_{false};
    std::atomic<bool> detectWanted_{false};
    std::atomic<int> screenW_{0};
    std::atomic<int> screenH_{0};

    std::mutex queueMutex_;
    std::deque<ControlItem> controls_;
    std::deque<ControlItem> controlsWork_;

    std::mutex cacheMutex_;
    DaemonCache cache_;
};

} // namespace NinEngine

#endif // NINMODS_ENGINE_DAEMON_LINK_H
