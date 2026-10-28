#ifndef NINMODS_MEMORY_IO_H
#define NINMODS_MEMORY_IO_H

/**
 * @file MemoryIO.h
 * @brief RT channel - Ring-0 kernel driver memory access (IOCTL only).
 *
 * This header implements the RT (kernel mode) channel of the project. Every
 * read, write and module lookup goes through the real kernel driver character
 * device with the documented IOCTL commands:
 *
 *   - IOCTL_RT_READ  (0x801): read memory  - 32 byte COPY_MEMORY payload
 *   - IOCTL_RT_WRITE (0x802): write memory - 32 byte COPY_MEMORY payload
 *   - IOCTL_RT_BASE  (0x803): module base - 24 byte MODULE_BASE payload
 *
 * Rules honoured here:
 *   - Rule 17 (Kernel Driver ONLY): there is NO driverless fallback in this
 *     file. No process_vm_readv, no /proc/&lt;pid&gt;/mem, no /proc/&lt;pid&gt;/maps.
 *     When the driver is missing or the IOCTL fails, the call fails and the
 *     real reason is reported instead of silently switching to another channel.
 *   - Rule 3 (Strict Driver Isolation): the QX / IPC channel lives in
 *     Engine/OfficialMemory.h and is never used by the in-app engine.
 *   - Rule 1 (Zero Fake Code): the driver state is derived from the real
 *     open() and ioctl() results, never from a flag that only looks right.
 *
 * The node list below is the documented loader contract (AGENTS.md 2-খ):
 * the loader prepares /dev/BYNb (plus the alternative node names) and the
 * engine opens the first one that is a real character device.
 */

#include <atomic>
#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

/** Memory read command of the RT kernel driver. */
#define IOCTL_RT_READ   0x801
/** Memory write command of the RT kernel driver. */
#define IOCTL_RT_WRITE  0x802
/** Module base lookup command of the RT kernel driver. */
#define IOCTL_RT_BASE   0x803

/**
 * @brief Payload of IOCTL_RT_READ and IOCTL_RT_WRITE.
 *
 * The layout is the driver ABI: a 32 byte structure on every 64 bit ABI
 * (pid_t + padding, address, userspace buffer, size).
 */
struct COPY_MEMORY {
    pid_t pid;          /**< Target process id. */
    uintptr_t addr;     /**< Virtual address inside the target process. */
    void *buffer;       /**< Buffer of the calling process. */
    size_t size;        /**< Number of bytes to transfer. */
};

/**
 * @brief Payload of IOCTL_RT_BASE.
 *
 * The layout is the driver ABI: a 24 byte structure on every 64 bit ABI
 * (pid_t + padding, module name pointer, resolved base address).
 */
struct MODULE_BASE {
    pid_t pid;          /**< Target process id. */
    char *name;         /**< NUL terminated module name, for example "libil2cpp.so". */
    uintptr_t base;     /**< Resolved base address, written by the driver. */
};

static_assert(sizeof(COPY_MEMORY) == 32,
              "COPY_MEMORY must stay at the 32 byte driver ABI of IOCTL_RT_READ/WRITE");
static_assert(sizeof(MODULE_BASE) == 24,
              "MODULE_BASE must stay at the 24 byte driver ABI of IOCTL_RT_BASE");

/** Target process id shared by the engine and the UI. */
inline std::atomic<pid_t> target_pid{-1};
/** Base address of the game library inside the target process. */
inline std::atomic<uintptr_t> libAddress{0};

namespace MemoryIO {

/** Alias of target_pid so the two can never desynchronize. */
inline std::atomic<pid_t> &g_pid = target_pid;

/** Cached file descriptor of the kernel driver character device. */
inline std::atomic<int> g_driver_fd{-1};
/** Name of the character device that was opened, nullptr when none is open. */
inline std::atomic<const char *> g_driver_node{nullptr};
/** errno of the last failed open() or ioctl() call. */
inline std::atomic<int> g_last_errno{0};
/** True when the last IOCTL of the driver was answered successfully. */
inline std::atomic<bool> g_last_ioctl_ok{false};

/**
 * @brief Character devices the loader may prepare.
 *
 * The first entry is the canonical node, the following ones are the
 * alternative names that the driver loader can create as a symlink.
 */
inline const char *const kDriverNodes[] = {
    "/dev/BYNb",
    "/dev/mem_tool",
    "/dev/nh",
    "/dev/wanbai",
    "/proc/uevents_records"
};

/** Number of entries in kDriverNodes. */
inline constexpr size_t kDriverNodeCount = sizeof(kDriverNodes) / sizeof(kDriverNodes[0]);

/**
 * @brief Opens the kernel driver character device and caches the descriptor.
 *
 * The descriptor is opened once and reused for every IOCTL. Only a real
 * character device is accepted, because a procfs file cannot carry the
 * IOCTL interface of the driver.
 *
 * @return the cached descriptor, or -1 when no driver node could be opened
 */
inline int GetDriverFd() {
    int cached = g_driver_fd.load(std::memory_order_acquire);
    if (cached >= 0) {
        return cached;
    }

    for (size_t i = 0; i < kDriverNodeCount; i++) {
        const char *node = kDriverNodes[i];
        int fd = open(node, O_RDWR | O_CLOEXEC);
        if (fd < 0) {
            g_last_errno.store(errno, std::memory_order_relaxed);
            continue;
        }

        struct stat nodeStat {};
        if (fstat(fd, &nodeStat) != 0) {
            g_last_errno.store(errno, std::memory_order_relaxed);
            close(fd);
            continue;
        }
        if (!S_ISCHR(nodeStat.st_mode)) {
            /* Not a character device: it cannot answer the driver IOCTLs. */
            g_last_errno.store(ENODEV, std::memory_order_relaxed);
            close(fd);
            continue;
        }

        int expected = -1;
        if (g_driver_fd.compare_exchange_strong(expected, fd,
                                                std::memory_order_release,
                                                std::memory_order_acquire)) {
            g_driver_node.store(node, std::memory_order_release);
            g_last_ioctl_ok.store(false, std::memory_order_relaxed);
            return fd;
        }
        close(fd);
        return g_driver_fd.load(std::memory_order_acquire);
    }

    return -1;
}

/**
 * @brief Returns the name of the character device that is currently open.
 *
 * @return the node name, or "none" when no driver node is open
 */
inline const char *GetDriverNode() {
    const char *node = g_driver_node.load(std::memory_order_acquire);
    return (node != nullptr) ? node : "none";
}

/**
 * @brief Returns the errno of the last failed driver call.
 *
 * @return the errno value, or 0 when the last call succeeded
 */
inline int GetLastErrno() {
    return g_last_errno.load(std::memory_order_relaxed);
}

/**
 * @brief Verifies the driver with a real IOCTL call.
 *
 * The verification uses the module base command of the driver. When the game
 * is attached its own library is looked up, otherwise the loader of the app
 * process itself is used. A successful ioctl() proves that the driver is
 * really loaded and answering.
 *
 * @param pid        process the lookup is performed for
 * @param moduleName name of the module that is looked up
 * @return true when the driver answered the request
 */
inline bool ProbeDriver(pid_t pid, const char *moduleName) {
    if (moduleName == nullptr) {
        return false;
    }

    int fd = GetDriverFd();
    if (fd < 0) {
        g_last_ioctl_ok.store(false, std::memory_order_relaxed);
        return false;
    }

    char name[128] = {0};
    strncpy(name, moduleName, sizeof(name) - 1);

    MODULE_BASE request {};
    request.pid = pid;
    request.name = name;
    request.base = 0;

    if (ioctl(fd, IOCTL_RT_BASE, &request) == 0) {
        g_last_ioctl_ok.store(true, std::memory_order_relaxed);
        g_last_errno.store(0, std::memory_order_relaxed);
        return true;
    }

    g_last_ioctl_ok.store(false, std::memory_order_relaxed);
    g_last_errno.store(errno, std::memory_order_relaxed);
    return false;
}

/**
 * @brief Reports whether the kernel driver is really usable.
 *
 * A cached descriptor alone is not enough: the driver node can exist while
 * every IOCTL fails. The status is therefore only "ready" after a successful
 * IOCTL, and a pending verification is performed on demand.
 *
 * @return true when the driver answered an IOCTL successfully
 */
inline bool IsDriverReady() {
    if (GetDriverFd() < 0) {
        return false;
    }
    if (g_last_ioctl_ok.load(std::memory_order_relaxed)) {
        return true;
    }

    pid_t pid = target_pid.load(std::memory_order_relaxed);
    if (pid > 0) {
        return ProbeDriver(pid, "libil2cpp.so");
    }
    return ProbeDriver(getpid(), "libc.so");
}

/**
 * @brief Reads memory from the target process through the kernel driver.
 *
 * @param pid  target process id
 * @param addr virtual address inside the target process
 * @param buf  destination buffer of the calling process
 * @param n    number of bytes to read
 * @return true when the driver transferred the whole buffer
 */
inline bool ReadBytes(pid_t pid, uintptr_t addr, void *buf, size_t n) {
    if (pid <= 0 || addr == 0 || buf == nullptr || n == 0) {
        return false;
    }

    int fd = GetDriverFd();
    if (fd < 0) {
        g_last_ioctl_ok.store(false, std::memory_order_relaxed);
        return false;
    }

    COPY_MEMORY request {};
    request.pid = pid;
    request.addr = addr;
    request.buffer = buf;
    request.size = n;

    if (ioctl(fd, IOCTL_RT_READ, &request) == 0) {
        g_last_ioctl_ok.store(true, std::memory_order_relaxed);
        g_last_errno.store(0, std::memory_order_relaxed);
        return true;
    }

    /* Rule 17: a failing IOCTL is reported, never replaced by a driverless read. */
    g_last_ioctl_ok.store(false, std::memory_order_relaxed);
    g_last_errno.store(errno, std::memory_order_relaxed);
    return false;
}

/**
 * @brief Writes memory into the target process through the kernel driver.
 *
 * @param pid  target process id
 * @param addr virtual address inside the target process
 * @param buf  source buffer of the calling process
 * @param n    number of bytes to write
 * @return true when the driver transferred the whole buffer
 */
inline bool WriteBytes(pid_t pid, uintptr_t addr, const void *buf, size_t n) {
    if (pid <= 0 || addr == 0 || buf == nullptr || n == 0) {
        return false;
    }

    int fd = GetDriverFd();
    if (fd < 0) {
        g_last_ioctl_ok.store(false, std::memory_order_relaxed);
        return false;
    }

    COPY_MEMORY request {};
    request.pid = pid;
    request.addr = addr;
    request.buffer = const_cast<void *>(buf);
    request.size = n;

    if (ioctl(fd, IOCTL_RT_WRITE, &request) == 0) {
        g_last_ioctl_ok.store(true, std::memory_order_relaxed);
        g_last_errno.store(0, std::memory_order_relaxed);
        return true;
    }

    /* Rule 17: a failing IOCTL is reported, never replaced by a driverless write. */
    g_last_ioctl_ok.store(false, std::memory_order_relaxed);
    g_last_errno.store(errno, std::memory_order_relaxed);
    return false;
}

/**
 * @brief Resolves the base address of a module through the kernel driver.
 *
 * @param pid        target process id
 * @param moduleName name of the module, for example "libil2cpp.so"
 * @return the base address of the module, or 0 when the driver could not resolve it
 */
inline uintptr_t ModuleBase(pid_t pid, const char *moduleName) {
    if (pid <= 0 || moduleName == nullptr) {
        return 0;
    }

    int fd = GetDriverFd();
    if (fd < 0) {
        g_last_ioctl_ok.store(false, std::memory_order_relaxed);
        return 0;
    }

    char name[128] = {0};
    strncpy(name, moduleName, sizeof(name) - 1);

    MODULE_BASE request {};
    request.pid = pid;
    request.name = name;
    request.base = 0;

    if (ioctl(fd, IOCTL_RT_BASE, &request) == 0) {
        g_last_ioctl_ok.store(true, std::memory_order_relaxed);
        g_last_errno.store(0, std::memory_order_relaxed);
        return request.base;
    }

    /* Rule 17: no /proc/&lt;pid&gt;/maps fallback for the in-app engine. */
    g_last_ioctl_ok.store(false, std::memory_order_relaxed);
    g_last_errno.store(errno, std::memory_order_relaxed);
    return 0;
}

/**
 * @brief Reads a single value from the target process through the driver.
 *
 * @tparam T type of the value
 * @param pid  target process id
 * @param addr virtual address inside the target process
 * @return the read value, or a zero value when the read failed
 */
template <typename T>
inline T Read(pid_t pid, uintptr_t addr) {
    T value {};
    if (!ReadBytes(pid, addr, &value, sizeof(T))) {
        return T {};
    }
    return value;
}

/**
 * @brief Writes a single value into the target process through the driver.
 *
 * @tparam T type of the value
 * @param pid   target process id
 * @param addr  virtual address inside the target process
 * @param value value to write
 * @return true when the driver wrote the value
 */
template <typename T>
inline bool Write(pid_t pid, uintptr_t addr, const T &value) {
    return WriteBytes(pid, addr, &value, sizeof(T));
}

/**
 * @brief Builds a human readable description of the RT channel state.
 *
 * The description only contains facts that were observed: the node that is
 * open, the descriptor, whether an IOCTL was verified and the errno of the
 * last failure.
 *
 * @return the description of the current driver state
 */
inline std::string DescribeChannel() {
    int fd = g_driver_fd.load(std::memory_order_acquire);
    std::string description = "RT Ring-0 Driver (";

    if (fd < 0) {
        description += "not loaded";
        int error = g_last_errno.load(std::memory_order_relaxed);
        if (error > 0) {
            description += ", last error: ";
            description += std::strerror(error);
        }
        description += ")";
        return description;
    }

    description += GetDriverNode();
    description += ", fd: ";
    description += std::to_string(fd);

    if (g_last_ioctl_ok.load(std::memory_order_relaxed)) {
        description += ", IOCTL verified";
    } else {
        description += ", IOCTL not verified";
        int error = g_last_errno.load(std::memory_order_relaxed);
        if (error > 0) {
            description += " (";
            description += std::strerror(error);
            description += ")";
        }
    }
    description += ")";
    return description;
}

} // namespace MemoryIO

#endif // NINMODS_MEMORY_IO_H
