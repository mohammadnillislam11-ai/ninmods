#include "DriverClient.h"
#include "daemon/DaemonMem.h"
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <cstdio>
#include <mutex>
#include <atomic>
#include <android/log.h>

#define LOG_TAG "NinMasterClient"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex s_driverMutex;
static std::atomic<int> s_driverFd{-1};
static std::atomic<int> s_targetPid{-1};
static std::atomic<DriverMode> s_driverMode{DriverMode::None};
static char s_activeDevPath[256] = "";

const char* DriverGetActiveNode() {
    std::lock_guard<std::mutex> lock(s_driverMutex);
    if (MasterMem::Get().IsKernelMode()) {
        return "KERNEL_RT";
    }
    if (MasterMem::Get().IsAttached()) {
        return "ROOT_IPC";
    }
    return "";
}

int DriverGetOpenFd() {
    return s_driverFd.load(std::memory_order_acquire);
}

int DriverOpen(DriverMode mode) {
    std::lock_guard<std::mutex> lock(s_driverMutex);
    s_driverMode.store(mode, std::memory_order_release);
    int currentFd = s_driverFd.load(std::memory_order_acquire);
    if (currentFd >= 0) {
        return currentFd;
    }

    const char* const candidateNodes[] = {
        "/dev/health_storage_service_alloc",
        "/dev/BYNb",
        "/dev/sched_debug",
        "/dev/uevents_records",
        "/dev/wanbai",
        "/dev/entryi",
        "/dev/cdev"
    };

    for (const char* node : candidateNodes) {
        int fd = open(node, O_RDWR | O_CLOEXEC);
        if (fd >= 0) {
            s_driverFd.store(fd, std::memory_order_release);
            strncpy(s_activeDevPath, node, sizeof(s_activeDevPath) - 1);
            MasterMem::Get().SetDriverFd(fd);
            LOGI("DriverOpen: Successfully opened kernel device node: %s (fd=%d)", node, fd);
            return fd;
        }
    }

    LOGW("DriverOpen: Could not open kernel node directly (sandbox/SELinux mode active)");
    s_driverFd.store(-1, std::memory_order_release);
    return -1;
}

void DriverClose(int fd) {
    std::lock_guard<std::mutex> lock(s_driverMutex);
    int cur = s_driverFd.exchange(-1, std::memory_order_acq_rel);
    if (cur >= 0) {
        close(cur);
    }
    s_driverMode.store(DriverMode::None, std::memory_order_release);
    s_targetPid.store(-1, std::memory_order_release);
    s_activeDevPath[0] = '\0';
    MasterMem::Get().Detach();
}

uint64_t DriverAttach(int fd, uint32_t pid) {
    std::lock_guard<std::mutex> lock(s_driverMutex);
    if (pid == 0) return 0;

    if (fd >= 0) {
        s_driverFd.store(fd, std::memory_order_release);
        MasterMem::Get().SetDriverFd(fd);
    }
    s_targetPid.store(static_cast<int>(pid), std::memory_order_release);
    MasterMem::Get().Init(static_cast<int>(pid), false);

    uint64_t handle = static_cast<uint64_t>(pid);
    LOGI("DriverAttach SUCCESS: targetPid=%u (engine=%s)", pid, MasterMem::Get().GetEngineName());
    return handle;
}

void DriverDetach(int fd, uint64_t handle) {
    std::lock_guard<std::mutex> lock(s_driverMutex);
    s_targetPid.store(-1, std::memory_order_release);
    MasterMem::Get().Detach();
}

bool DriverRead(int fd, uint64_t handle, uint64_t addr, void* buf, size_t size) {
    if (addr == 0 || !buf || size == 0) return false;
    if (fd >= 0) MasterMem::Get().SetDriverFd(fd);
    return MasterMem::Get().Read(addr, buf, size);
}

bool DriverWrite(int fd, uint64_t handle, uint64_t addr, const void* buf, size_t size) {
    if (addr == 0 || !buf || size == 0) return false;
    if (fd >= 0) MasterMem::Get().SetDriverFd(fd);
    return MasterMem::Get().Write(addr, buf, size);
}

uint64_t DriverGetModuleBase(int fd, uint32_t pid, const char* name) {
    if (!name || pid == 0) return 0;
    if (fd >= 0) MasterMem::Get().SetDriverFd(fd);
    if (!MasterMem::Get().IsAttached() || MasterMem::Get().GetPid() != static_cast<int>(pid)) {
        MasterMem::Get().Init(static_cast<int>(pid), false);
    }
    return MasterMem::Get().GetModuleBase(name);
}
