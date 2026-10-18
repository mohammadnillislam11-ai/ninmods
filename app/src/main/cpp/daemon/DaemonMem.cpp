#include "DaemonMem.h"
#include <inttypes.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/uio.h>
#include <sys/syscall.h>
#include <sys/stat.h>
#include <dirent.h>
#include <cstring>
#include <cstdio>
#include <cerrno>
#include <android/log.h>

#define LOG_TAG "NinMasterMem"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifndef __NR_process_vm_readv
#if defined(__aarch64__)
#define __NR_process_vm_readv 270
#define __NR_process_vm_writev 271
#elif defined(__arm__)
#define __NR_process_vm_readv 376
#define __NR_process_vm_writev 377
#endif
#endif

#define IOCTL_MEM_READ    0x801
#define IOCTL_MEM_WRITE   0x802
#define IOCTL_MODULE_BASE 0x803

struct DriverMemoryIo {
    uint32_t pid;
    uint32_t _pad;
    uint64_t address;
    uint64_t buffer;
    uint64_t size;
};

struct DriverModuleBaseIo {
    uint32_t pid;
    uint32_t _pad;
    uint64_t name_ptr;
    uint64_t base;
};

MasterMem& MasterMem::Get() {
    static MasterMem s_instance;
    return s_instance;
}

MasterMem::MasterMem()
    : m_pid(-1), m_isMax(false), m_driverFd(-1), m_memFd(-1), m_il2cppBase(0) {
    m_activeNode[0] = '\0';
}

MasterMem::~MasterMem() {
    Detach();
}

void MasterMem::ProbeKernelDriver() {
    if (m_driverFd >= 0) return;

    const char* const candidateNodes[] = {
        "/dev/health_storage_service_alloc",
        "/dev/wanbai",
        "/dev/entryi",
        "/dev/BYNb",
        "/dev/uevents_records",
        "/dev/sched_debug",
        "/dev/cdev"
    };

    for (const char* node : candidateNodes) {
        int fd = open(node, O_RDWR | O_CLOEXEC);
        if (fd >= 0) {
            m_driverFd = fd;
            strncpy(m_activeNode, node, sizeof(m_activeNode) - 1);
            LOGI("MasterMem: Kernel driver node connected: %s (fd=%d)", node, fd);
            return;
        }
    }

    LOGI("MasterMem: No kernel node open; using Root process_vm_readv/proc-mem engine");
}

void MasterMem::OpenProcMem() {
    if (m_memFd >= 0) {
        close(m_memFd);
        m_memFd = -1;
    }
    if (m_pid <= 0) return;

    char memPath[64];
    snprintf(memPath, sizeof(memPath), "/proc/%d/mem", m_pid);
    m_memFd = open(memPath, O_RDWR | O_CLOEXEC);
    if (m_memFd < 0) {
        // Retry read-only if O_RDWR fails
        m_memFd = open(memPath, O_RDONLY | O_CLOEXEC);
    }
    if (m_memFd >= 0) {
        LOGI("MasterMem: /proc/%d/mem opened successfully (fd=%d)", m_pid, m_memFd);
    } else {
        LOGW("MasterMem: open(%s) failed: %s", memPath, strerror(errno));
    }
}

bool MasterMem::Init(int pid, bool isMax) {
    if (pid <= 0) return false;
    if (m_pid == pid && m_il2cppBase != 0) return true;

    m_pid = pid;
    m_isMax = isMax;

    if (m_driverFd < 0) {
        ProbeKernelDriver();
    }
    if (m_memFd < 0) {
        OpenProcMem();
    }

    m_il2cppBase = GetModuleBase("libil2cpp.so");
    LOGI("MasterMem::Init pid=%d isMax=%d base=0x%llx (engine=%s)",
         pid, isMax, (unsigned long long)m_il2cppBase, GetEngineName());

    return (m_driverFd >= 0 || m_memFd >= 0 || m_il2cppBase != 0);
}

void MasterMem::Detach() {
    if (m_memFd >= 0) {
        close(m_memFd);
        m_memFd = -1;
    }
    m_pid = -1;
    m_il2cppBase = 0;
}

bool MasterMem::Read(uint64_t addr, void* buf, size_t size) {
    if (m_pid <= 0 || addr == 0 || !buf || size == 0) return false;

    // 1. Kernel Mode IOCTL (Ring-0)
    if (m_driverFd > 2) {
        DriverMemoryIo req{
            static_cast<uint32_t>(m_pid),
            0,
            addr,
            reinterpret_cast<uint64_t>(buf),
            size
        };
        if (ioctl(m_driverFd, IOCTL_MEM_READ, &req) == 0) {
            return true;
        }
    }

    // 2. Root syscall: process_vm_readv
#if defined(__NR_process_vm_readv)
    struct iovec local[1];
    struct iovec remote[1];
    local[0].iov_base = buf;
    local[0].iov_len = size;
    remote[0].iov_base = reinterpret_cast<void*>(addr);
    remote[0].iov_len = size;

    ssize_t n = syscall(__NR_process_vm_readv, static_cast<pid_t>(m_pid), local, 1, remote, 1, 0);
    if (n == static_cast<ssize_t>(size)) {
        return true;
    }
#endif

    // 3. Root fallback: pread64 on /proc/<pid>/mem
    if (m_memFd >= 0) {
        ssize_t n = pread64(m_memFd, buf, size, static_cast<off64_t>(addr));
        if (n == static_cast<ssize_t>(size)) {
            return true;
        }
    }

    return false;
}

bool MasterMem::Write(uint64_t addr, const void* buf, size_t size) {
    if (m_pid <= 0 || addr == 0 || !buf || size == 0) return false;

    // 1. Kernel Mode IOCTL (Ring-0)
    if (m_driverFd > 2) {
        DriverMemoryIo req{
            static_cast<uint32_t>(m_pid),
            0,
            addr,
            reinterpret_cast<uint64_t>(buf),
            size
        };
        if (ioctl(m_driverFd, IOCTL_MEM_WRITE, &req) == 0) {
            return true;
        }
    }

    // 2. Root syscall: process_vm_writev
#if defined(__NR_process_vm_writev)
    struct iovec local[1];
    struct iovec remote[1];
    local[0].iov_base = const_cast<void*>(buf);
    local[0].iov_len = size;
    remote[0].iov_base = reinterpret_cast<void*>(addr);
    remote[0].iov_len = size;

    ssize_t n = syscall(__NR_process_vm_writev, static_cast<pid_t>(m_pid), local, 1, remote, 1, 0);
    if (n == static_cast<ssize_t>(size)) {
        return true;
    }
#endif

    // 3. Root fallback: pwrite64 on /proc/<pid>/mem
    if (m_memFd >= 0) {
        ssize_t n = pwrite64(m_memFd, buf, size, static_cast<off64_t>(addr));
        if (n == static_cast<ssize_t>(size)) {
            return true;
        }
    }

    return false;
}

static bool is_elf_header(MasterMem& mem, uint64_t addr) {
    if (addr == 0) return false;
    unsigned char magic[4] = {0};
    if (!mem.Read(addr, magic, 4)) return false;
    return (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F');
}

uint64_t MasterMem::GetModuleBase(const char* name) {
    if (!name || m_pid <= 0) return 0;

    // Cache hit: If libil2cpp.so already resolved and still valid, return instantly
    if (m_il2cppBase > 0 && strcmp(name, "libil2cpp.so") == 0) {
        return m_il2cppBase;
    }

    // 1. Kernel Mode IOCTL (Ring-0)
    uint64_t kernelBase = 0;
    if (m_driverFd > 2) {
        char safe[256] = {0};
        strncpy(safe, name, sizeof(safe) - 1);
        DriverModuleBaseIo req{
            static_cast<uint32_t>(m_pid),
            0,
            reinterpret_cast<uint64_t>(safe),
            0
        };
        if (ioctl(m_driverFd, IOCTL_MODULE_BASE, &req) == 0 && req.base != 0) {
            kernelBase = req.base;
            if (strcmp(name, "libil2cpp.so") != 0) {
                return kernelBase;
            }
        }
    }

    // 2. Multi-segment ELF probing for libil2cpp.so
    char mapPath[64];
    snprintf(mapPath, sizeof(mapPath), "/proc/%d/maps", m_pid);
    FILE* fp = fopen(mapPath, "r");
    if (!fp) {
        fp = fopen("/data/data/com.ninmods.menu/cache/maps", "r");
    }
    if (!fp) {
        fp = fopen("/data/user/0/com.ninmods.menu/cache/maps", "r");
    }

    if (fp) {
        char line[512];
        uint64_t elfBases[8] = {0};
        int elfCount = 0;
        uint64_t fallbackBase = 0;

        if (kernelBase != 0) {
            elfBases[elfCount++] = kernelBase;
        }

        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, name)) {
                unsigned long long start = 0;
                if (sscanf(line, "%llx-", &start) == 1 && start != 0) {
                    if (fallbackBase == 0) fallbackBase = static_cast<uint64_t>(start);
                    uint64_t sAddr = static_cast<uint64_t>(start);
                    if (is_elf_header(*this, sAddr)) {
                        bool exists = false;
                        for (int i = 0; i < elfCount; ++i) {
                            if (elfBases[i] == sAddr) { exists = true; break; }
                        }
                        if (!exists && elfCount < 8) {
                            elfBases[elfCount++] = sAddr;
                        }
                    }
                }
            }
        }
        fclose(fp);

        static const uint64_t kCandOffsets[] = {
            0x0A984D48ULL, 0x0C2132B8ULL, 0x0AD5B288ULL, 0x0AD3532CULL
        };

        for (int i = 0; i < elfCount; ++i) {
            if (elfBases[i] == 0) continue;
            for (uint64_t off : kCandOffsets) {
                uint64_t facade = 0;
                if (Read(elfBases[i] + off, &facade, sizeof(facade))) {
                    if (facade >= 0x10000000ULL && facade <= 0x00007FFFFFFFF000ULL &&
                        ((facade & 3ULL) == 0ULL) && ((facade & 0xFFFFFFFFULL) != 0ULL)) {
                        uint64_t stClass = 0;
                        Read(facade + 0xB8ULL, &stClass, sizeof(stClass));
                        if (stClass >= 0x10000000ULL && ((stClass & 3ULL) == 0ULL) && ((stClass & 0xFFFFFFFFULL) != 0ULL)) {
                            if (strcmp(name, "libil2cpp.so") == 0) {
                                m_il2cppBase = elfBases[i];
                            }
                            return elfBases[i];
                        }
                    }
                }
            }
        }

        uint64_t resolved = (elfCount > 1 && elfBases[1] != 0) ? elfBases[1] : (elfCount > 0 ? elfBases[0] : fallbackBase);
        if (resolved != 0) {
            if (strcmp(name, "libil2cpp.so") == 0) {
                m_il2cppBase = resolved;
            }
            return resolved;
        }
    }

    if (kernelBase != 0) {
        if (strcmp(name, "libil2cpp.so") == 0) {
            m_il2cppBase = kernelBase;
        }
        return kernelBase;
    }


    return 0;
}
