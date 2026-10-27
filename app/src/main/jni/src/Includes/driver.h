#ifndef UNIFIED_DRIVER_H
#define UNIFIED_DRIVER_H

#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <stdlib.h>
#include <stdio.h>
#include <dirent.h>
#include <unistd.h>
#include <ctype.h>
#include <time.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <string.h>
#include <regex.h>
#include <sys/prctl.h>
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

static inline ssize_t sys_process_vm_readv(pid_t pid, const struct iovec *local_iov, unsigned long liovcnt,
                                           const struct iovec *remote_iov, unsigned long riovcnt, unsigned long flags) {
    return syscall(__NR_process_vm_readv, pid, local_iov, liovcnt, remote_iov, riovcnt, flags);
}

static inline ssize_t sys_process_vm_writev(pid_t pid, const struct iovec *local_iov, unsigned long liovcnt,
                                            const struct iovec *remote_iov, unsigned long riovcnt, unsigned long flags) {
    return syscall(__NR_process_vm_writev, pid, local_iov, liovcnt, remote_iov, riovcnt, flags);
}

#include <cmath>
#include <iostream>
#include <string>
#include <cstdint>
#include <atomic>
#include <mutex>
#include <vector>
#include <initializer_list>
#include <linux/input.h>
#include <linux/uinput.h>

#include "Logger.h"

// ══════════════════════════════════════════════════════════════════════
//  1. PROTOCOL OPCODES & IDENTIFIERS (Standardized Across All Systems)
// ══════════════════════════════════════════════════════════════════════

// [Group 1] RT DEV Standard (0x801 / 0x802 / 0x803)
#define DEV_OP_INIT_KEY           0x800
#define DEV_OP_READ_MEM           0x801
#define DEV_OP_WRITE_MEM          0x802
#define DEV_OP_MODULE_BASE        0x803
#define DEV_OP_HIDE_PROCESS       0x804
#define DEV_OP_GET_PID            0x805

// [Group 2] Hardware Breakpoints IOCTL
#define RT_CMD_HWBP_ADD           3495169
#define RT_CMD_HWBP_GET_HITS      3495170
#define RT_CMD_HWBP_CLEAR         3495172

// ══════════════════════════════════════════════════════════════════════
//  2. SHARED DATA STRUCTURES & MATH TYPES
// ══════════════════════════════════════════════════════════════════════

#pragma pack(push, 8)
typedef struct _COPY_MEMORY {
    pid_t pid;
    uintptr_t addr;
    void* buffer;
    size_t size;
} COPY_MEMORY, *PCOPY_MEMORY;

typedef struct _MODULE_BASE {
    pid_t pid;
    char* name;
    uintptr_t base;
} MODULE_BASE, *PMODULE_BASE;

typedef struct _MODULE_BASE_FIXED {
    pid_t pid;
    char name[256];
    uintptr_t base;
} MODULE_BASE_FIXED, *PMODULE_BASE_FIXED;
#pragma pack(pop)

// Lightweight 3D Vector for Internal Lead Prediction & Aim Calculations
struct DriverVec3 {
    float x, y, z;
    DriverVec3() : x(0.0f), y(0.0f), z(0.0f) {}
    DriverVec3(float _x, float _y, float _z) : x(_x), y(_y), z(_z) {}
    DriverVec3 operator+(const DriverVec3& o) const { return DriverVec3(x + o.x, y + o.y, z + o.z); }
    DriverVec3 operator-(const DriverVec3& o) const { return DriverVec3(x - o.x, y - o.y, z - o.z); }
    DriverVec3 operator*(float s) const { return DriverVec3(x * s, y * s, z * s); }
    float length() const { return sqrtf(x * x + y * y + z * z); }
    static float distance(const DriverVec3& a, const DriverVec3& b) {
        float dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return sqrtf(dx * dx + dy * dy + dz * dz);
    }
};

// ══════════════════════════════════════════════════════════════════════
//  4. THE ADVANCED c_driver CLASS (Multi-Protocol, Pagemap & HWBP)
// ══════════════════════════════════════════════════════════════════════

enum DriverMode {
    DRIVER_MODE_NONE        = 0,
    DRIVER_MODE_RT_DEV      = 1, // Official Linux Character Device IOCTL (/dev/BYNb, /dev/mem_tool, /dev/nh, /dev/wanbai)
    DRIVER_MODE_RT_PROC     = 2  // Official Linux ProcFS IOCTL (/proc/uevents_records)
};

class c_driver {
private:
    int fd;
    pid_t pid;
    DriverMode active_mode;
    int active_read_cmd;
    int active_write_cmd;
    int active_base_cmd;
    char active_node_name[128];
    std::atomic<int> in_flight_ops;

    /* Serialises every driver operation.
     * The engine reads/writes from the render thread AND from the aim
     * threads at the same time. Without this lock two threads can race on
     * `fd` (one closing it while the other is inside ioctl) and the
     * self-healing `initDriver()` can be executed concurrently by both,
     * leaking a file descriptor and leaving `fd` pointing at a closed
     * descriptor. */
    std::recursive_mutex m_lock;

    enum class ProbeResult {
        Verified,      /* the IOCTL really executed and returned our data  */
        NotSupported,  /* ENOTTY: this node does not implement this IOCTL   */
        Inconclusive   /* any other error: cannot prove it, cannot refute */
    };

    // Self-test IOCTL read: Verifies real execution with a known magic
    ProbeResult test_ioctl_read(int target_fd, int cmd) {
        if (target_fd < 0) return ProbeResult::NotSupported;
        pid_t self_pid = getpid();
        uintptr_t test_magic = 0x5A5AA5A512345678ULL;
        uintptr_t read_back = 0;
        COPY_MEMORY test_cm = { self_pid, (uintptr_t)&test_magic, &read_back, sizeof(read_back) };
        if (ioctl(target_fd, cmd, &test_cm) == 0) {
            return (read_back == test_magic) ? ProbeResult::Verified
                                             : ProbeResult::Inconclusive;
        }
        /* ENOTTY / ENOSYS / EINVAL mean the node simply does not speak this
         * IOCTL protocol - a definitive negative, never a "maybe". */
        if (errno == ENOTTY || errno == ENOSYS || errno == EINVAL) {
            return ProbeResult::NotSupported;
        }
        return ProbeResult::Inconclusive;
    }

    // ──────────────────────────────────────────────────────────────────
    // 1. Linux Official Character Device Nodes (/dev/BYNb, /dev/mem_tool, /dev/nh, etc.)
    // ──────────────────────────────────────────────────────────────────
    bool probe_character_nodes() {
        const char *official_nodes[] = {
            "/dev/BYNb",
            "/dev/mem_tool",
            "/dev/nh",
            "/dev/wanbai"
        };
        for (const char *node : official_nodes) {
            int test_fd = open(node, O_RDWR);
            if (test_fd >= 0) {
                fd = test_fd;
                active_mode = DRIVER_MODE_RT_DEV;
                active_read_cmd = DEV_OP_READ_MEM;
                active_write_cmd = DEV_OP_WRITE_MEM;
                active_base_cmd = DEV_OP_MODULE_BASE;
                strncpy(active_node_name, node, sizeof(active_node_name) - 1);
                if (strcmp(node, "/dev/BYNb") != 0) {
                    symlink(node, "/dev/BYNb");
                    chmod("/dev/BYNb", 0666);
                }
                NIN_LOGI("c_driver", ">>> [DRIVER_ACTIVE] Mode: Linux Character Device (%s) Connected! (FD: %d) <<<", node, fd);
                return true;
            }
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────
    // 2. Linux Official ProcFS Endpoint (/proc/uevents_records)
    // ──────────────────────────────────────────────────────────────────
    bool probe_proc_nodes() {
        const char *node = "/proc/uevents_records";
        int test_fd = open(node, O_RDWR);
        if (test_fd >= 0) {
            fd = test_fd;
            active_mode = DRIVER_MODE_RT_PROC;
            active_read_cmd = DEV_OP_READ_MEM;
            active_write_cmd = DEV_OP_WRITE_MEM;
            active_base_cmd = DEV_OP_MODULE_BASE;
            strncpy(active_node_name, node, sizeof(active_node_name) - 1);
            symlink(node, "/dev/BYNb");
            chmod("/dev/BYNb", 0666);
            NIN_LOGI("c_driver", ">>> [DRIVER_ACTIVE] Mode: Linux ProcFS IOCTL (%s) Connected! (FD: %d) <<<", node, fd);
            return true;
        }
        return false;
    }

public:
    c_driver() : fd(-1), pid(-1), active_mode(DRIVER_MODE_NONE),
                 active_read_cmd(DEV_OP_READ_MEM), active_write_cmd(DEV_OP_WRITE_MEM),
                 active_base_cmd(DEV_OP_MODULE_BASE), in_flight_ops(0) {
        memset(active_node_name, 0, sizeof(active_node_name));
        // Lazy initialization: Hardware driver discovery is performed on demand, never in the global constructor
    }

    ~c_driver() {
        closeDriver();
    }

    // ──────────────────────────────────────────────────────────────────
    // Unified Linux Official Driver Discovery (Strictly Genuine Kernel Nodes)
    // ──────────────────────────────────────────────────────────────────
    bool initDriver() {
        std::lock_guard<std::recursive_mutex> guard(m_lock);
        if (isConnected()) {
            return true;
        }

        NIN_LOGI("c_driver", "=== [DRIVER_INIT] Probing Official Linux Kernel Driver Nodes ===");

        // [Priority 1] Linux Official Character Nodes in /dev: /dev/BYNb, /dev/mem_tool, /dev/nh, /dev/wanbai
        if (probe_character_nodes()) return true;

        // [Priority 2] Linux Official ProcFS Endpoint: /proc/uevents_records
        if (probe_proc_nodes()) return true;

        // Strictly NO fake fallback. If kernel driver is not loaded, stay OFFLINE.
        fd = -1;
        active_mode = DRIVER_MODE_NONE;
        memset(active_node_name, 0, sizeof(active_node_name));
        NIN_LOGE("c_driver", ">>> [DRIVER_FAIL] All Official Linux Driver Endpoints Unreachable! Ensure Loader is Active. <<<");
        return false;
    }

    void initialize(pid_t target_pid) {
        std::lock_guard<std::recursive_mutex> guard(m_lock);
        this->pid = target_pid;
    }

    bool isConnected() {
        return (fd >= 0 && active_mode != DRIVER_MODE_NONE);
    }

    int getFd() const { return fd; }
    pid_t getPid() const { return pid; }
    const char* getNodeName() const { return active_node_name; }
    DriverMode getDriverMode() const { return active_mode; }

    const char* getDriverModeName() const {
        switch (active_mode) {
            case DRIVER_MODE_RT_DEV:       return "Linux RT DEV (/dev/BYNb)";
            case DRIVER_MODE_RT_PROC:      return "Linux RT Proc (/proc/uevents_records)";
            default:                      return "OFFLINE";
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Pagemap Virtual-to-Physical & Page Presence Validation
    // ──────────────────────────────────────────────────────────────────
    bool isPagePresent(uintptr_t vaddr) {
        if (this->pid <= 0 || vaddr == 0) return false;
        long pageSize = sysconf(_SC_PAGESIZE);
        if (pageSize <= 0) pageSize = 4096;
        unsigned long v_pageIndex = vaddr / pageSize;
        unsigned long pfn_offset = v_pageIndex * sizeof(uint64_t);
        uint64_t item = 0;

        char filename[64];
        snprintf(filename, sizeof(filename), "/proc/%d/pagemap", this->pid);
        int page_fd = open(filename, O_RDONLY);
        if (page_fd < 0) return true; // If pagemap unreadable due to SELinux, assume OK

        if (lseek(page_fd, pfn_offset, SEEK_SET) < 0) {
            close(page_fd);
            return false;
        }
        if (::read(page_fd, &item, sizeof(uint64_t)) != sizeof(uint64_t)) {
            close(page_fd);
            return false;
        }
        close(page_fd);
        // Bit 63 is set if page is present in RAM
        return ((item & (1ULL << 63)) != 0);
    }

    // Strict ARM64 User-space 64-bit Pointer Bounds Check
    inline bool isPointerValid(uintptr_t ptr) {
        if ((ptr >> 48) != 0) return false;
        if (ptr < 0x10000000ULL || ptr > 0x00007FFFFFFFFFFFULL) return false;
        if ((ptr & 0x3) != 0) return false;
        return true;
    }

    // ──────────────────────────────────────────────────────────────────
    // Core Memory Read (Official Linux Syscall / Character Device IOCTL)
    // ──────────────────────────────────────────────────────────────────
    bool read(uintptr_t addr, void *buffer, size_t size) {
        if (addr == 0 || buffer == nullptr || size == 0 || this->pid <= 0) return false;
        std::lock_guard<std::recursive_mutex> guard(m_lock);
        if (!isConnected() && !initDriver()) return false;

        in_flight_ops.fetch_add(1, std::memory_order_relaxed);
        bool success = false;

        COPY_MEMORY cm{};
        cm.pid = this->pid;
        cm.addr = addr;
        cm.buffer = buffer;
        cm.size = size;
        success = (ioctl(fd, active_read_cmd, &cm) == 0);

        in_flight_ops.fetch_sub(1, std::memory_order_relaxed);
        return success;
    }

    // Safe Cross-Page Boundary Read (Prevents page-boundary crash)
    bool readSafe(uintptr_t addr, void *buffer, size_t size) {
        if (addr == 0 || buffer == nullptr || size == 0) return false;
        constexpr size_t K_PAGE_SIZE = 4096;
        size_t page_offset = addr % K_PAGE_SIZE;

        if (page_offset + size <= K_PAGE_SIZE) {
            return read(addr, buffer, size);
        }

        uint8_t *dst = (uint8_t *)buffer;
        size_t bytes_left = size;
        uintptr_t curr_addr = addr;

        while (bytes_left > 0) {
            size_t chunk_limit = K_PAGE_SIZE - (curr_addr % K_PAGE_SIZE);
            size_t chunk_size = (bytes_left < chunk_limit) ? bytes_left : chunk_limit;
            if (!read(curr_addr, dst, chunk_size)) {
                return false;
            }
            curr_addr += chunk_size;
            dst += chunk_size;
            bytes_left -= chunk_size;
        }
        return true;
    }

    // ──────────────────────────────────────────────────────────────────
    // Core Memory Write (Official Linux Syscall / Character Device IOCTL)
    // ──────────────────────────────────────────────────────────────────
    bool write(uintptr_t addr, const void *buffer, size_t size) {
        if (addr == 0 || buffer == nullptr || size == 0 || this->pid <= 0) return false;
        std::lock_guard<std::recursive_mutex> guard(m_lock);
        if (!isConnected() && !initDriver()) return false;

        in_flight_ops.fetch_add(1, std::memory_order_relaxed);
        bool success = false;

        COPY_MEMORY cm{};
        cm.pid = this->pid;
        cm.addr = addr;
        cm.buffer = const_cast<void*>(buffer);
        cm.size = size;
        success = (ioctl(fd, active_write_cmd, &cm) == 0);

        in_flight_ops.fetch_sub(1, std::memory_order_relaxed);
        return success;
    }

    template <typename T>
    T read(uintptr_t addr) {
        T res{};
        if (this->read(addr, &res, sizeof(T))) {
            return res;
        }
        return res;
    }

    template <typename T>
    bool write(uintptr_t addr, T value) {
        return this->write(addr, &value, sizeof(T));
    }

    // ──────────────────────────────────────────────────────────────────
    // Multi-Level Pointer Chain Resolution
    // ──────────────────────────────────────────────────────────────────
    uintptr_t readChain(uintptr_t base, const std::vector<uintptr_t>& offsets) {
        if (base == 0) return 0;
        uintptr_t curr = base;
        for (size_t i = 0; i < offsets.size(); i++) {
            curr = read<uintptr_t>(curr + offsets[i]);
            if (!isPointerValid(curr)) return 0;
        }
        return curr;
    }

    uintptr_t readChain(uintptr_t base, const std::initializer_list<uintptr_t>& offsets) {
        std::vector<uintptr_t> off_vec(offsets);
        return readChain(base, off_vec);
    }

    template <typename T>
    T readChainValue(uintptr_t base, const std::vector<uintptr_t>& offsets, uintptr_t final_offset) {
        uintptr_t obj = readChain(base, offsets);
        if (obj == 0) return T{};
        return read<T>(obj + final_offset);
    }

    // ──────────────────────────────────────────────────────────────────
    // Module Base Address Lookup
    // ──────────────────────────────────────────────────────────────────
    uintptr_t getModuleBase(const char *name) {
        if (!name || this->pid <= 0) return 0;
        std::lock_guard<std::recursive_mutex> guard(m_lock);
        if (!isConnected() && !initDriver()) return 0;

        if (fd >= 0) {
            MODULE_BASE mb{};
            char buf[128] = {0};
            strncpy(buf, name, sizeof(buf) - 1);
            mb.pid = this->pid;
            mb.name = buf;
            mb.base = 0;
            if (ioctl(fd, active_base_cmd, &mb) == 0 && mb.base > 0) {
                return mb.base;
            }
        }

        char maps_path[64];
        snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", this->pid);
        FILE *f = fopen(maps_path, "r");
        if (!f) return 0;
        char line[512];
        uintptr_t base = 0;
        while (fgets(line, sizeof(line), f)) {
            if (strstr(line, name)) {
                base = (uintptr_t)strtoull(line, nullptr, 16);
                break;
            }
        }
        fclose(f);
        return base;
    }

    // ──────────────────────────────────────────────────────────────────
    // Bullet Velocity & Movement Lead Prediction Engine
    // ──────────────────────────────────────────────────────────────────
    DriverVec3 predictLeadPosition(const DriverVec3& targetPos, const DriverVec3& targetVelocity,
                                   float distance, float bulletSpeed = 600.0f, float gravity = 9.8f) {
        if (bulletSpeed <= 0.0f) bulletSpeed = 600.0f;
        float travelTime = distance / bulletSpeed;

        // Horizontal lead prediction
        DriverVec3 predicted = targetPos + (targetVelocity * travelTime);

        // Vertical bullet drop compensation: 0.5 * g * t^2
        float dropCompensation = 0.5f * gravity * (travelTime * travelTime);
        predicted.y += dropCompensation;

        return predicted;
    }

    // ──────────────────────────────────────────────────────────────────
    // Hardware Breakpoints Control
    // ──────────────────────────────────────────────────────────────────
    bool addHardwareBreakpoint(uintptr_t addr) {
        if (!isConnected() || addr == 0 || fd < 0) return false;
        return (ioctl(fd, RT_CMD_HWBP_ADD, &addr) == 0);
    }

    bool clearHardwareBreakpoints() {
        if (!isConnected() || fd < 0) return false;
        return (ioctl(fd, RT_CMD_HWBP_CLEAR, 0) == 0);
    }

    int getHardwareBreakpointHits() {
        if (!isConnected() || fd < 0) return 0;
        int hits = 0;
        ioctl(fd, RT_CMD_HWBP_GET_HITS, &hits);
        return hits;
    }

    // ──────────────────────────────────────────────────────────────────
    // Process Hiding via Kernel IOCTL
    // ──────────────────────────────────────────────────────────────────
    void hide_process(int flags = 0) {
        if (!isConnected() || fd < 0) return;
        ioctl(fd, DEV_OP_HIDE_PROCESS);
    }

    void closeDriver() {
        std::lock_guard<std::recursive_mutex> guard(m_lock);
        int old_fd = fd;
        fd = -1;
        active_mode = DRIVER_MODE_NONE;
        memset(active_node_name, 0, sizeof(active_node_name));
        while (in_flight_ops.load(std::memory_order_relaxed) > 0) {
            usleep(100);
        }
        if (old_fd >= 0) {
            close(old_fd);
        }
    }
};

// ══════════════════════════════════════════════════════════════════════
//  5. GLOBAL INSTANCE & NAMESPACE MEMORY (Matching Reference Exactly)
// ══════════════════════════════════════════════════════════════════════

inline c_driver *driver = new c_driver();

namespace Memory {
    inline pid_t g_pid = -1;

    inline int ProcessRead(uintptr_t address, void* buffer, size_t size, pid_t pid = 0) {
        if (!buffer || address == 0) return -1;
        if (pid > 0 && driver) driver->initialize(pid);
        if (driver && driver->readSafe(address, buffer, size)) return 0;
        return -1;
    }

    inline int ProcessWrite(void* address, const void* buffer, size_t size, pid_t pid = 0) {
        if (!buffer || (uintptr_t)address == 0) return -1;
        if (pid > 0 && driver) driver->initialize(pid);
        if (driver && driver->write((uintptr_t)address, buffer, size)) return 0;
        return -1;
    }

    template<typename T>
    inline T Read(uintptr_t address) {
        if (driver) return driver->read<T>(address);
        return T{};
    }

    template<typename T>
    inline bool Write(uintptr_t address, T value) {
        if (driver) return driver->write<T>(address, value);
        return false;
    }

    inline uintptr_t ReadChain(uintptr_t base, const std::initializer_list<uintptr_t>& offsets) {
        if (driver) return driver->readChain(base, offsets);
        return 0;
    }
}

#endif // UNIFIED_DRIVER_H
