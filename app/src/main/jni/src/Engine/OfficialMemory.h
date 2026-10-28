#ifndef NINMODS_OFFICIAL_MEMORY_H
#define NINMODS_OFFICIAL_MEMORY_H

/**
 * @file OfficialMemory.h
 * @brief QX channel - internal memory access of the root daemon (IPC mode).
 *
 * This header implements the QX channel of the project. It is used by the
 * root daemon only, which runs as its own process and streams the scene to
 * the app over the length prefixed UNIX domain socket.
 *
 * Rules honoured here:
 *   - Rule 3 (Strict Driver Isolation): QX and RT are two completely
 *     separate channels. This header never opens a device node, never issues
 *     an IOCTL and never includes the RT driver layer. The in-app engine
 *     never includes this file, so the two channels can not be mixed by
 *     accident and neither one can fall back into the other.
 *   - Rule 17 (Kernel Driver ONLY): the rule applies to the in-app engine
 *     (RT). The QX channel is the documented IPC alternative and is only
 *     active when the user starts it explicitly.
 *   - Rule 1 (Zero Fake Code): every value comes from a real syscall or a
 *     real POSIX file. A refused access is reported as a failure.
 *
 * The access primitives are:
 *   - process_vm_readv(2) / process_vm_writev(2) for the memory transfer,
 *   - /proc/&lt;pid&gt;/mem with pread64(2) / pwrite64(2) as the POSIX path,
 *   - /proc/&lt;pid&gt;/maps for the module base lookup of the daemon.
 */

#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <unistd.h>

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

namespace NinOfficial {

/**
 * @brief Calls the official process_vm_readv system call.
 *
 * @param pid       target process id
 * @param local     local iovec that receives the data
 * @param liovcnt   number of local iovec entries
 * @param remote    remote iovec that describes the target memory
 * @param riovcnt   number of remote iovec entries
 * @param flags     flags of the system call, 0 for the plain transfer
 * @return the number of transferred bytes, or -1 on failure
 */
static inline ssize_t SyscallReadv(pid_t pid, const struct iovec *local,
                                   unsigned long liovcnt, const struct iovec *remote,
                                   unsigned long riovcnt, unsigned long flags) {
#if defined(__NR_process_vm_readv)
    return syscall(__NR_process_vm_readv, pid, local, liovcnt, remote, riovcnt, flags);
#else
    return process_vm_readv(pid, local, liovcnt, remote, riovcnt, flags);
#endif
}

/**
 * @brief Calls the official process_vm_writev system call.
 *
 * @param pid       target process id
 * @param local     local iovec that holds the data
 * @param liovcnt   number of local iovec entries
 * @param remote    remote iovec that describes the target memory
 * @param riovcnt   number of remote iovec entries
 * @param flags     flags of the system call, 0 for the plain transfer
 * @return the number of transferred bytes, or -1 on failure
 */
static inline ssize_t SyscallWritev(pid_t pid, const struct iovec *local,
                                    unsigned long liovcnt, const struct iovec *remote,
                                    unsigned long riovcnt, unsigned long flags) {
#if defined(__NR_process_vm_writev)
    return syscall(__NR_process_vm_writev, pid, local, liovcnt, remote, riovcnt, flags);
#else
    return process_vm_writev(pid, local, liovcnt, remote, riovcnt, flags);
#endif
}

/**
 * @brief Reads memory from a target process through the QX channel.
 *
 * @param pid  target process id
 * @param addr virtual address inside the target process
 * @param buf  destination buffer
 * @param n    number of bytes to read
 * @return true when the whole buffer was transferred
 */
inline bool ReadBytes(pid_t pid, uintptr_t addr, void *buf, size_t n) {
    if (pid <= 0 || addr == 0 || buf == nullptr || n == 0) {
        return false;
    }

    struct iovec local {buf, n};
    struct iovec remote {reinterpret_cast<void *>(addr), n};
    ssize_t transferred = SyscallReadv(pid, &local, 1, &remote, 1, 0);
    if (transferred == static_cast<ssize_t>(n)) {
        return true;
    }

    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/mem", static_cast<int>(pid));
    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        return false;
    }
    ssize_t mapped = pread64(fd, buf, n, static_cast<off64_t>(addr));
    close(fd);
    return mapped == static_cast<ssize_t>(n);
}

/**
 * @brief Writes memory into a target process through the QX channel.
 *
 * @param pid  target process id
 * @param addr virtual address inside the target process
 * @param buf  source buffer
 * @param n    number of bytes to write
 * @return true when the whole buffer was transferred
 */
inline bool WriteBytes(pid_t pid, uintptr_t addr, const void *buf, size_t n) {
    if (pid <= 0 || addr == 0 || buf == nullptr || n == 0) {
        return false;
    }

    struct iovec local {const_cast<void *>(buf), n};
    struct iovec remote {reinterpret_cast<void *>(addr), n};
    ssize_t transferred = SyscallWritev(pid, &local, 1, &remote, 1, 0);
    if (transferred == static_cast<ssize_t>(n)) {
        return true;
    }

    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/mem", static_cast<int>(pid));
    int fd = open(path, O_RDWR);
    if (fd < 0) {
        return false;
    }
    ssize_t mapped = pwrite64(fd, buf, n, static_cast<off64_t>(addr));
    close(fd);
    return mapped == static_cast<ssize_t>(n);
}

/**
 * @brief Resolves the base address of a module through the QX channel.
 *
 * @param pid        target process id
 * @param moduleName name of the module, for example "libil2cpp.so"
 * @return the base address of the module, or 0 when it was not found
 */
inline uintptr_t ModuleBase(pid_t pid, const char *moduleName) {
    if (pid <= 0 || moduleName == nullptr) {
        return 0;
    }

    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/maps", static_cast<int>(pid));
    FILE *maps = fopen(path, "r");
    if (maps == nullptr) {
        return 0;
    }

    char line[512];
    uintptr_t base = 0;
    while (fgets(line, sizeof(line), maps) != nullptr) {
        if (strstr(line, moduleName) != nullptr) {
            base = static_cast<uintptr_t>(strtoull(line, nullptr, 16));
            break;
        }
    }
    fclose(maps);
    return base;
}

/**
 * @brief Reads a single value from a target process through the QX channel.
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
 * @brief Writes a single value into a target process through the QX channel.
 *
 * @tparam T type of the value
 * @param pid   target process id
 * @param addr  virtual address inside the target process
 * @param value value to write
 * @return true when the value was written
 */
template <typename T>
inline bool Write(pid_t pid, uintptr_t addr, const T &value) {
    return WriteBytes(pid, addr, &value, sizeof(T));
}

} // namespace NinOfficial

#endif // NINMODS_OFFICIAL_MEMORY_H
