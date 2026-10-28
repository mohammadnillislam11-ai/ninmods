#ifndef NINMODS_OFFICIAL_MEMORY_H
#define NINMODS_OFFICIAL_MEMORY_H

/**
 * @file OfficialMemory.h
 * @brief QX channel - direct low-level kernel memory engine of the ninlite daemon.
 *
 * This header implements the stealth memory engine for the standalone ninlite root daemon.
 * It issues direct raw inline-assembly system calls (svc #0 on aarch64, syscall on x86_64)
 * bypassing bionic libc symbol hooks, exactly matching the proven engine in ZqwClient.
 *
 * Architectural Rules Observed:
 *   - Rule 1 (Zero Fake Code): All read/write operations execute genuine kernel syscalls.
 *   - Rule 3 (Strict Driver Isolation): The QX channel operates purely in IPC user/root space.
 *   - Rule 20 (Javadoc / Doxygen Style Comments): Standard documentation with zero banners.
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

#if defined(__aarch64__)
#define SYS_NR_openat     56
#define SYS_NR_close      57
#define SYS_NR_read       63
#define SYS_NR_getdents64 61
#define SYS_NR_vm_read    270
#define SYS_NR_vm_write   271
#elif defined(__x86_64__)
#define SYS_NR_openat     257
#define SYS_NR_close      3
#define SYS_NR_read       0
#define SYS_NR_getdents64 217
#define SYS_NR_vm_read    310
#define SYS_NR_vm_write   311
#elif defined(__arm__)
#define SYS_NR_openat     322
#define SYS_NR_close      6
#define SYS_NR_read       3
#define SYS_NR_getdents64 217
#define SYS_NR_vm_read    376
#define SYS_NR_vm_write   377
#else
#define SYS_NR_openat     295
#define SYS_NR_close      6
#define SYS_NR_read       3
#define SYS_NR_getdents64 220
#define SYS_NR_vm_read    347
#define SYS_NR_vm_write   348
#endif

#ifndef AT_FDCWD
#define AT_FDCWD -100
#endif

namespace NinOfficial {

/**
 * @brief Raw directory entry structure for SYS_getdents64.
 */
struct linux_dirent64 {
    uint64_t       d_ino;
    int64_t        d_off;
    unsigned short d_reclen;
    unsigned char  d_type;
    char           d_name[];
};

/**
 * @brief Memory wipe helper to scrub sensitive buffers.
 */
static inline void WipeBuffer(void *p, size_t n) {
    if (!p || n == 0) return;
    volatile uint8_t *v = reinterpret_cast<volatile uint8_t *>(p);
    for (size_t i = 0; i < n; ++i) v[i] = 0;
}

/**
 * @brief Executes a direct 6-parameter system call using inline assembly.
 */
static inline long RawSyscall6(long n, long a1, long a2, long a3, long a4, long a5, long a6) {
    long r = -1;
#if defined(__aarch64__)
    register long x8 __asm__("x8") = n;
    register long x0 __asm__("x0") = a1;
    register long x1 __asm__("x1") = a2;
    register long x2 __asm__("x2") = a3;
    register long x3 __asm__("x3") = a4;
    register long x4 __asm__("x4") = a5;
    register long x5 __asm__("x5") = a6;
    __asm__ volatile("svc #0"
                     : "+r"(x0)
                     : "r"(x8), "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5)
                     : "memory", "cc");
    r = x0;
#elif defined(__x86_64__)
    register long rax asm("rax") = n;
    register long rdi asm("rdi") = a1;
    register long rsi asm("rsi") = a2;
    register long rdx asm("rdx") = a3;
    register long r10 asm("r10") = a4;
    register long r8  asm("r8")  = a5;
    register long r9  asm("r9")  = a6;
    asm volatile("syscall"
                 : "=a"(r)
                 : "a"(rax), "D"(rdi), "S"(rsi), "d"(rdx), "r"(r10), "r"(r8), "r"(r9)
                 : "rcx", "r11", "memory");
#else
    r = syscall(n, a1, a2, a3, a4, a5, a6);
#endif
    return r;
}

static inline int SysOpenAt(int dirfd, const char *pathname, int flags) {
    return static_cast<int>(RawSyscall6(SYS_NR_openat, dirfd, reinterpret_cast<long>(pathname), flags, 0, 0, 0));
}

static inline int SysClose(int fd) {
    return static_cast<int>(RawSyscall6(SYS_NR_close, fd, 0, 0, 0, 0, 0));
}

static inline long SysRead(int fd, void *buf, size_t count) {
    return RawSyscall6(SYS_NR_read, fd, reinterpret_cast<long>(buf), count, 0, 0, 0);
}

static inline long SysGetdents64(int fd, void *dirp, size_t count) {
    return RawSyscall6(SYS_NR_getdents64, fd, reinterpret_cast<long>(dirp), count, 0, 0, 0);
}

static inline ssize_t SyscallReadv(pid_t pid, const struct iovec *local,
                                   unsigned long liovcnt, const struct iovec *remote,
                                   unsigned long riovcnt, unsigned long flags) {
    return static_cast<ssize_t>(RawSyscall6(SYS_NR_vm_read,
                                           static_cast<long>(pid),
                                           reinterpret_cast<long>(local),
                                           static_cast<long>(liovcnt),
                                           reinterpret_cast<long>(remote),
                                           static_cast<long>(riovcnt),
                                           static_cast<long>(flags)));
}

static inline ssize_t SyscallWritev(pid_t pid, const struct iovec *local,
                                    unsigned long liovcnt, const struct iovec *remote,
                                    unsigned long riovcnt, unsigned long flags) {
    return static_cast<ssize_t>(RawSyscall6(SYS_NR_vm_write,
                                           static_cast<long>(pid),
                                           reinterpret_cast<long>(local),
                                           static_cast<long>(liovcnt),
                                           reinterpret_cast<long>(remote),
                                           static_cast<long>(riovcnt),
                                           static_cast<long>(flags)));
}

inline bool ReadBytes(pid_t pid, uintptr_t addr, void *buf, size_t n) {
    if (pid <= 0 || addr < 0x1000 || buf == nullptr || n == 0) {
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
    int fd = SysOpenAt(AT_FDCWD, path, O_RDONLY);
    if (fd < 0) {
        return false;
    }
    ssize_t mapped = pread64(fd, buf, n, static_cast<off64_t>(addr));
    SysClose(fd);
    return mapped == static_cast<ssize_t>(n);
}

inline bool WriteBytes(pid_t pid, uintptr_t addr, const void *buf, size_t n) {
    if (pid <= 0 || addr < 0x1000 || buf == nullptr || n == 0) {
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
    int fd = SysOpenAt(AT_FDCWD, path, O_RDWR);
    if (fd < 0) {
        return false;
    }
    ssize_t mapped = pwrite64(fd, buf, n, static_cast<off64_t>(addr));
    SysClose(fd);
    return mapped == static_cast<ssize_t>(n);
}

/**
 * @brief High-speed direct syscall proc scanner matching Free Fire variants.
 */
inline pid_t FastScanPid(bool &isMax) {
    int pfd = SysOpenAt(AT_FDCWD, "/proc", O_RDONLY | O_DIRECTORY);
    if (pfd < 0) return -1;

    char buf[4096];
    pid_t normalFound = -1;
    pid_t maxFound = -1;

    while (true) {
        long nr = SysGetdents64(pfd, buf, sizeof(buf));
        if (nr <= 0) break;

        char *p = buf;
        while (p < buf + nr) {
            auto *e = reinterpret_cast<linux_dirent64 *>(p);
            if (e->d_type == 4 || e->d_type == 0) {
                char *n = e->d_name;
                bool isNum = true;
                for (char *c = n; *c; ++c) {
                    if (*c < '0' || *c > '9') { isNum = false; break; }
                }

                if (isNum && *n != '\0') {
                    pid_t pid = static_cast<pid_t>(atoi(n));
                    char cmdPath[64];
                    snprintf(cmdPath, sizeof(cmdPath), "/proc/%s/cmdline", n);
                    int cfd = SysOpenAt(AT_FDCWD, cmdPath, O_RDONLY);
                    if (cfd >= 0) {
                        char cmd[256] = {0};
                        long bytes = SysRead(cfd, cmd, sizeof(cmd) - 1);
                        SysClose(cfd);
                        if (bytes > 0) {
                            cmd[bytes] = '\0';
                            if (strstr(cmd, "com.dts.freefiremax") != nullptr) {
                                if (maxFound < 0) maxFound = pid;
                            } else if (strstr(cmd, "com.dts.freefireth") != nullptr ||
                                       strstr(cmd, "com.dts.freefirein") != nullptr) {
                                if (normalFound < 0) normalFound = pid;
                            }
                        }
                        WipeBuffer(cmd, sizeof(cmd));
                    }
                }
            }
            p += e->d_reclen;
        }
        if (normalFound > 0) break;
    }

    SysClose(pfd);
    WipeBuffer(buf, sizeof(buf));

    if (normalFound > 0) {
        isMax = false;
        return normalFound;
    }
    if (maxFound > 0) {
        isMax = true;
        return maxFound;
    }
    return -1;
}

/**
 * @brief Resolves base address of module using raw direct system calls.
 */
inline uintptr_t FastModuleBase(pid_t pid, const char *moduleName) {
    if (pid <= 0 || moduleName == nullptr) return 0;

    char mapsPath[64];
    snprintf(mapsPath, sizeof(mapsPath), "/proc/%d/maps", static_cast<int>(pid));
    int mfd = SysOpenAt(AT_FDCWD, mapsPath, O_RDONLY);
    if (mfd < 0) return 0;

    char buf[8192];
    long tr = 0;
    uintptr_t base = 0;

    while (true) {
        long nr = SysRead(mfd, buf + tr, sizeof(buf) - tr - 1);
        if (nr <= 0) break;
        tr += nr;
        buf[tr] = '\0';

        char *ls = buf;
        while (true) {
            char *le = ls;
            while (*le && *le != '\n') le++;
            if (*le == '\0') break;
            *le = '\0';

            if (strstr(ls, moduleName) != nullptr) {
                uintptr_t start = 0;
                uintptr_t offset = 0;
                char perms[8] = {0};
                if (sscanf(ls, "%lx-%*lx %7s %lx", &start, perms, &offset) >= 1) {
                    if (offset == 0) {
                        base = start;
                        SysClose(mfd);
                        WipeBuffer(buf, sizeof(buf));
                        return base;
                    }
                }
            }
            ls = le + 1;
        }

        if (ls < buf + tr) {
            int rm = static_cast<int>((buf + tr) - ls);
            for (int i = 0; i < rm; ++i) buf[i] = ls[i];
            tr = rm;
        } else {
            tr = 0;
        }
    }

    SysClose(mfd);
    WipeBuffer(buf, sizeof(buf));
    return base;
}

template <typename T>
inline T Read(pid_t pid, uintptr_t addr) {
    T value {};
    if (!ReadBytes(pid, addr, &value, sizeof(T))) {
        return T {};
    }
    return value;
}

template <typename T>
inline bool Write(pid_t pid, uintptr_t addr, const T &value) {
    return WriteBytes(pid, addr, &value, sizeof(T));
}

} // namespace NinOfficial

#endif // NINMODS_OFFICIAL_MEMORY_H
