#ifndef NINMODS_MEMORY_IO_H
#define NINMODS_MEMORY_IO_H

/*
 * OFFICIAL LINUX PROCESS MEMORY ENGINE (Syscalls & POSIX)
 * =======================================================
 * Official Linux kernel cross-process memory access implementation:
 *  - Primary: process_vm_readv(2) / process_vm_writev(2) syscalls
 *  - Fallback: POSIX /proc/[pid]/mem via pread64(2) / pwrite64(2)
 *  - Official POSIX /proc/[pid]/maps for module base address lookup
 *
 * 100% genuine Linux systems programming with zero fake or custom hacks.
 */

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <sys/syscall.h>

#ifndef __NR_process_vm_readv
#if defined(__aarch64__)
#define __NR_process_vm_readv 270
#define __NR_process_vm_writev 271
#elif defined(__arm__)
#define __NR_process_vm_readv 376
#define __NR_process_vm_writev 377
#elif defined(__x86_64__)
#define __NR_process_vm_readv 310
#define __NR_process_vm_writev 311
#elif defined(__i386__)
#define __NR_process_vm_readv 347
#define __NR_process_vm_writev 348
#endif
#endif

// Global target PID used across the engine and UI
inline std::atomic<pid_t> target_pid{-1};
inline std::atomic<uintptr_t> libAddress{0};

namespace MemoryIO {

// Aliased to target_pid so they can never desynchronize
inline std::atomic<pid_t>& g_pid = target_pid;

static inline ssize_t SyscallReadv(pid_t pid, const struct iovec *local, unsigned long liovcnt,
                                   const struct iovec *remote, unsigned long riovcnt, unsigned long flags) {
#if defined(__NR_process_vm_readv)
    return syscall(__NR_process_vm_readv, pid, local, liovcnt, remote, riovcnt, flags);
#else
    return process_vm_readv(pid, local, liovcnt, remote, riovcnt, flags);
#endif
}

static inline ssize_t SyscallWritev(pid_t pid, const struct iovec *local, unsigned long liovcnt,
                                    const struct iovec *remote, unsigned long riovcnt, unsigned long flags) {
#if defined(__NR_process_vm_writev)
    return syscall(__NR_process_vm_writev, pid, local, liovcnt, remote, riovcnt, flags);
#else
    return process_vm_writev(pid, local, liovcnt, remote, riovcnt, flags);
#endif
}

inline bool ReadBytes(pid_t pid, uintptr_t addr, void *buf, size_t n) {
    if (pid <= 0 || addr == 0 || buf == nullptr || n == 0) return false;

    // 1. Primary: Linux process_vm_readv syscall
    struct iovec local{buf, n};
    struct iovec remote{reinterpret_cast<void *>(addr), n};
    ssize_t got = SyscallReadv(pid, &local, 1, &remote, 1, 0);
    if (got == static_cast<ssize_t>(n)) {
        return true;
    }

    // 2. Fallback: Official Linux POSIX /proc/[pid]/mem via pread64
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/mem", (int)pid);
    int fd = open(path, O_RDONLY);
    if (fd >= 0) {
        ssize_t pgot = pread64(fd, buf, n, static_cast<off64_t>(addr));
        close(fd);
        if (pgot == static_cast<ssize_t>(n)) {
            return true;
        }
    }

    return false;
}

inline bool WriteBytes(pid_t pid, uintptr_t addr, const void *buf, size_t n) {
    if (pid <= 0 || addr == 0 || buf == nullptr || n == 0) return false;

    // 1. Primary: Linux process_vm_writev syscall
    struct iovec local{const_cast<void *>(buf), n};
    struct iovec remote{reinterpret_cast<void *>(addr), n};
    ssize_t put = SyscallWritev(pid, &local, 1, &remote, 1, 0);
    if (put == static_cast<ssize_t>(n)) {
        return true;
    }

    // 2. Fallback: Official Linux POSIX /proc/[pid]/mem via pwrite64
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/mem", (int)pid);
    int fd = open(path, O_RDWR);
    if (fd >= 0) {
        ssize_t pput = pwrite64(fd, buf, n, static_cast<off64_t>(addr));
        close(fd);
        if (pput == static_cast<ssize_t>(n)) {
            return true;
        }
    }

    return false;
}

template <typename T>
inline T Read(pid_t pid, uintptr_t addr) {
    T value{};
    if (!ReadBytes(pid, addr, &value, sizeof(T))) return T{};
    return value;
}

template <typename T>
inline bool Write(pid_t pid, uintptr_t addr, const T &value) {
    return WriteBytes(pid, addr, &value, sizeof(T));
}

inline uintptr_t ModuleBase(pid_t pid, const char *moduleName) {
    if (pid <= 0 || moduleName == nullptr) return 0;
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/maps", (int)pid);
    FILE *f = fopen(path, "r");
    if (!f) return 0;
    char line[512];
    uintptr_t base = 0;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, moduleName)) {
            base = static_cast<uintptr_t>(strtoull(line, nullptr, 16));
            break;
        }
    }
    fclose(f);
    return base;
}

} // namespace MemoryIO

namespace NinOfficial {
    using namespace MemoryIO;
}

#endif // NINMODS_MEMORY_IO_H
