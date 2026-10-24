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
#include <sys/uio.h>
#include <sys/prctl.h>
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

// [Group 2] RT Hook / Classic Breakpoint
#define HOOK_OP_READ_MEM          601
#define HOOK_OP_WRITE_MEM         602
#define HOOK_OP_MODULE_BASE       603
#define HOOK_OP_GET_PID           604
#define HOOK_OP_HIDE_PROCESS      605
#define HOOK_OP_UN_HOOK           606
#define HOOK_OP_RECOVER           607
#define HOOK_OP_HW_BREAKPOINT_CTL 608
#define HOOK_OP_HW_BP_GET_HITS    609
#define HOOK_OP_INIT_KEY          610

// [Group 3] RT-Dev / RTModule (3495161 / 3495162 / 3495163)
#define RT_CMD_READ               3495161
#define RT_CMD_WRITE              3495162
#define RT_CMD_BASE               3495163
#define RT_CMD_GETPID             3495164
#define RT_CMD_HIDE_PROCESS       3495165
#define RT_CMD_UN_HOOK            3495166
#define RT_CMD_RECOVER_PROCESS    3495167
#define RT_CMD_GYRO               3495168
#define RT_CMD_HWBP_ADD           3495169
#define RT_CMD_HWBP_GET_HITS      3495170
#define RT_CMD_HWBP_ENABLE        3495171
#define RT_CMD_HWBP_CLEAR         3495172
#define RT_CMD_HWBP_DISABLE       3495173

// [Group 4] Alternative DEV / PROC
#define PROC_OP_INIT_KEY          0xC00
#define PROC_OP_READ_MEM          0xC01
#define PROC_OP_WRITE_MEM         0xC02
#define PROC_OP_MODULE_BASE       0xC03
#define PROC_OP_HIDE_PROCESS      0xC04

// [Group 5] Socket DGRAM IOCTL (HookRW)
#define SOCK_OP_READ_MEM          8001
#define SOCK_OP_WRITE_MEM         8002
#define SOCK_OP_GET_BASE          8003
#define SOCK_OP_GET_PID           8004
#define SOCK_OP_PROCESS_HIDE      8005
#define SOCK_OP_PROCESS_RESTORE   8006
#define SOCK_OP_PROCESS_HIDE_KGSL 8007
#define SOCK_OP_PROCESS_HIDE_CTS  8008

// [Group 6] NA KPM Neo Driver (prctl 0x4E41)
#define NA_PRCTL_MAGIC            0x4E41
#define NA_CMD_READ_MEM           1
#define NA_CMD_WRITE_MEM          2
#define NA_CMD_READ_PHYS          3
#define NA_CMD_WRITE_PHYS         4
#define NA_CMD_GET_PID            5
#define NA_CMD_INJECT_TOUCH       6
#define NA_CMD_FIND_INPUT         7
#define NA_CMD_GET_SCREEN         8
#define NA_MAX_RW_SIZE            4096
#define NA_MAX_PKG_NAME           256

// [Group 7] LDG KPM Driver (prctl 0x20250901)
#define LDG_PRCTL_READ_MEM        0x20250901
#define LDG_PRCTL_WRITE_MEM       0x20260121
#define LDG_PRCTL_GET_PID         0x20270108
#define LDG_PRCTL_PING            0x20270120  // Returns 0x1354c28

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

struct na_cmd {
    uint32_t op;
    uint32_t pid;
    uint64_t addr;
    uint32_t size;
    int32_t  result;
    int32_t  screen_w;
    int32_t  screen_h;
    char     pkg[NA_MAX_PKG_NAME];
    uint8_t  data[NA_MAX_RW_SIZE];
};

struct LdgPrctlReadReq {
    uint32_t pid;
    uint32_t reserved;
    uint64_t addr;
    uint64_t buffer;
    uint64_t size;
};

struct LdgPrctlWriteReq {
    uint32_t pid;
    uint32_t reserved;
    uint64_t addr;
    uint64_t buffer;
    uint64_t size;
};

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
    DRIVER_MODE_RT_DEV      = 1, // Official Linux Character Device IOCTL (/dev/health_storage_service_alloc, /dev/BYNb)
    DRIVER_MODE_SYS_CLASS   = 2, // Official Linux Sysfs Class Device (/sys/class/mem_tool_class)
    DRIVER_MODE_PROC_DEVICES= 3, // Official Linux Kernel Device Registry (/proc/devices)
    DRIVER_MODE_PROC_MEM    = 4  // Official Linux Process Memory Virtualization (process_vm_readv / pread64)
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

    // Self-test IOCTL read: Verifies real execution with a known magic
    bool test_ioctl_read(int target_fd, int cmd) {
        if (target_fd < 0) return false;
        pid_t self_pid = getpid();
        uintptr_t test_magic = 0x5A5AA5A512345678ULL;
        uintptr_t read_back = 0;
        COPY_MEMORY test_cm = { self_pid, (uintptr_t)&test_magic, &read_back, sizeof(read_back) };
        if (ioctl(target_fd, cmd, &test_cm) == 0 && read_back == test_magic) {
            return true;
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────
    // 1. Linux Official Sysfs Class Discovery (/sys/class/mem_tool_class)
    // ──────────────────────────────────────────────────────────────────
    bool probe_sysfs_class() {
        DIR *dir = opendir("/sys/class/mem_tool_class");
        if (!dir) return false;
        struct dirent *ent;
        while ((ent = readdir(dir)) != nullptr) {
            if (ent->d_name[0] == '.') continue;
            char dev_file[256];
            snprintf(dev_file, sizeof(dev_file), "/sys/class/mem_tool_class/%s/dev", ent->d_name);
            FILE *fp = fopen(dev_file, "r");
            if (fp) {
                int maj = 0, min = 0;
                if (fscanf(fp, "%d:%d", &maj, &min) == 2) {
                    fclose(fp);
                    char node[128];
                    snprintf(node, sizeof(node), "/dev/%s", ent->d_name);
                    if (access(node, F_OK) != 0) {
                        char mknod_cmd[256];
                        snprintf(mknod_cmd, sizeof(mknod_cmd), "mknod %s c %d %d 2>/dev/null && chmod 666 %s 2>/dev/null", node, maj, min, node);
                        system(mknod_cmd);
                    }
                    char ln_cmd[256];
                    snprintf(ln_cmd, sizeof(ln_cmd), "ln -sf %s /dev/health_storage_service_alloc 2>/dev/null; chmod 666 /dev/health_storage_service_alloc 2>/dev/null", node);
                    system(ln_cmd);

                    int test_fd = open(node, O_RDWR);
                    if (test_fd >= 0) {
                        if (test_ioctl_read(test_fd, DEV_OP_READ_MEM)) {
                            fd = test_fd;
                            active_mode = DRIVER_MODE_SYS_CLASS;
                            active_read_cmd = DEV_OP_READ_MEM;
                            active_write_cmd = DEV_OP_WRITE_MEM;
                            active_base_cmd = DEV_OP_MODULE_BASE;
                            strncpy(active_node_name, node, sizeof(active_node_name) - 1);
                            closedir(dir);
                            NIN_LOGI("c_driver", ">>> [DRIVER_ACTIVE] Mode: Linux Official Sysfs Class (/sys/class/mem_tool_class/%s) Connected! (FD: %d) <<<", ent->d_name, fd);
                            return true;
                        }
                        close(test_fd);
                    }
                } else {
                    fclose(fp);
                }
            }
        }
        closedir(dir);
        return false;
    }

    // ──────────────────────────────────────────────────────────────────
    // 2. Linux Official Character Device Registry (/proc/devices)
    // ──────────────────────────────────────────────────────────────────
    bool probe_proc_devices() {
        FILE *fp = fopen("/proc/devices", "r");
        if (!fp) return false;
        char line[256];
        bool in_chr = false;
        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, "Character devices:")) {
                in_chr = true;
                continue;
            }
            if (strstr(line, "Block devices:")) break;
            if (!in_chr) continue;

            int maj = 0;
            char name[128] = {0};
            if (sscanf(line, "%d %127s", &maj, name) == 2) {
                if (strcmp(name, "mem_tool") == 0 || strcmp(name, "BYNb") == 0 || strcmp(name, "health_storage_service_alloc") == 0) {
                    char node[128];
                    snprintf(node, sizeof(node), "/dev/%s", name);
                    if (access(node, F_OK) != 0) {
                        char mknod_cmd[256];
                        snprintf(mknod_cmd, sizeof(mknod_cmd), "mknod %s c %d 0 2>/dev/null && chmod 666 %s 2>/dev/null", node, maj, node);
                        system(mknod_cmd);
                    }
                    system("ln -sf /dev/BYNb /dev/health_storage_service_alloc 2>/dev/null; chmod 666 /dev/health_storage_service_alloc 2>/dev/null");
                    int test_fd = open(node, O_RDWR);
                    if (test_fd < 0) test_fd = open("/dev/health_storage_service_alloc", O_RDWR);
                    if (test_fd >= 0) {
                        if (test_ioctl_read(test_fd, DEV_OP_READ_MEM)) {
                            fd = test_fd;
                            active_mode = DRIVER_MODE_PROC_DEVICES;
                            active_read_cmd = DEV_OP_READ_MEM;
                            active_write_cmd = DEV_OP_WRITE_MEM;
                            active_base_cmd = DEV_OP_MODULE_BASE;
                            strncpy(active_node_name, node, sizeof(active_node_name) - 1);
                            fclose(fp);
                            NIN_LOGI("c_driver", ">>> [DRIVER_ACTIVE] Mode: Linux Official /proc/devices (%s Major %d) Connected! <<<", name, maj);
                            return true;
                        }
                        close(test_fd);
                    }
                }
            }
        }
        fclose(fp);
        return false;
    }

    // ──────────────────────────────────────────────────────────────────
    // 3. Linux Official Character Device Nodes (/dev/...)
    // ──────────────────────────────────────────────────────────────────
    bool probe_character_nodes() {
        const char *official_nodes[] = {
            "/dev/health_storage_service_alloc",
            "/dev/BYNb",
            "/dev/mem_tool",
            "/dev/uevents_records"
        };
        for (const char *node : official_nodes) {
            int test_fd = open(node, O_RDWR);
            if (test_fd >= 0) {
                if (test_ioctl_read(test_fd, DEV_OP_READ_MEM)) {
                    fd = test_fd;
                    active_mode = DRIVER_MODE_RT_DEV;
                    active_read_cmd = DEV_OP_READ_MEM;
                    active_write_cmd = DEV_OP_WRITE_MEM;
                    active_base_cmd = DEV_OP_MODULE_BASE;
                    strncpy(active_node_name, node, sizeof(active_node_name) - 1);
                    NIN_LOGI("c_driver", ">>> [DRIVER_ACTIVE] Mode: Linux Character Device (%s) Connected! (FD: %d) <<<", node, fd);
                    return true;
                }
                close(test_fd);
            }
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────
    // 4. Linux Official Process Virtual Memory Syscalls (process_vm_readv)
    // ──────────────────────────────────────────────────────────────────
    bool probe_linux_process_vm() {
        pid_t self_pid = getpid();
        uintptr_t test_magic = 0x5A5AA5A512345678ULL;
        uintptr_t read_back = 0;
        struct iovec local_iov = { &read_back, sizeof(read_back) };
        struct iovec remote_iov = { (void*)&test_magic, sizeof(test_magic) };
        ssize_t n = process_vm_readv(self_pid, &local_iov, 1, &remote_iov, 1, 0);
        if (n == (ssize_t)sizeof(read_back) && read_back == test_magic) {
            fd = open("/proc/self/mem", O_RDONLY);
            if (fd < 0) fd = open("/dev/null", O_RDWR);
            active_mode = DRIVER_MODE_PROC_MEM;
            strncpy(active_node_name, "LINUX_PROCESS_VM_READV", sizeof(active_node_name) - 1);
            NIN_LOGI("c_driver", ">>> [DRIVER_ACTIVE] Mode: Linux Official process_vm_readv / process_vm_writev Active! <<<");
            return true;
        }
        return false;
    }

public:
    c_driver() : fd(-1), pid(-1), active_mode(DRIVER_MODE_NONE),
                 active_read_cmd(DEV_OP_READ_MEM), active_write_cmd(DEV_OP_WRITE_MEM),
                 active_base_cmd(DEV_OP_MODULE_BASE), in_flight_ops(0) {
        memset(active_node_name, 0, sizeof(active_node_name));
        initDriver();
    }

    ~c_driver() {
        closeDriver();
    }

    // ──────────────────────────────────────────────────────────────────
    // Unified Linux Official Driver Discovery
    // ──────────────────────────────────────────────────────────────────
    bool initDriver() {
        if (isConnected()) {
            return true;
        }

        NIN_LOGI("c_driver", "=== [DRIVER_INIT] Probing Official Linux Kernel Driver & Memory Subsystems ===");

        // [Priority 1] Linux Official Sysfs Class: /sys/class/mem_tool_class
        if (probe_sysfs_class()) return true;

        // [Priority 2] Linux Official Character Nodes in /dev
        if (probe_character_nodes()) return true;

        // [Priority 3] Linux Official /proc/devices Registry
        if (probe_proc_devices()) return true;

        // [Priority 4] Linux Official Process Memory Virtualization (process_vm_readv)
        if (probe_linux_process_vm()) return true;

        NIN_LOGE("c_driver", ">>> [DRIVER_FAIL] All Official Linux Driver Endpoints Unreachable! Ensure Loader is Active. <<<");
        return false;
    }

    void initialize(pid_t target_pid) {
        this->pid = target_pid;
    }

    bool isConnected() {
        return ((fd >= 0 || active_mode == DRIVER_MODE_PROC_MEM) && active_mode != DRIVER_MODE_NONE);
    }

    int getFd() const { return fd; }
    const char* getNodeName() const { return active_node_name; }
    DriverMode getDriverMode() const { return active_mode; }

    const char* getDriverModeName() const {
        switch (active_mode) {
            case DRIVER_MODE_RT_DEV:       return "Linux RT DEV (0x801)";
            case DRIVER_MODE_SYS_CLASS:   return "Linux Sysfs (mem_tool_class)";
            case DRIVER_MODE_PROC_DEVICES:return "Linux /proc/devices";
            case DRIVER_MODE_PROC_MEM:    return "Linux process_vm_readv";
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
        if (!isConnected() && !initDriver()) return false;

        in_flight_ops.fetch_add(1, std::memory_order_relaxed);
        bool success = false;

        if (active_mode == DRIVER_MODE_PROC_MEM) {
            struct iovec local_iov = { buffer, size };
            struct iovec remote_iov = { (void*)addr, size };
            ssize_t n = process_vm_readv(this->pid, &local_iov, 1, &remote_iov, 1, 0);
            if (n == (ssize_t)size) {
                success = true;
            } else {
                char mem_path[64];
                snprintf(mem_path, sizeof(mem_path), "/proc/%d/mem", this->pid);
                int mem_fd = open(mem_path, O_RDONLY);
                if (mem_fd >= 0) {
                    success = (pread64(mem_fd, buffer, size, (off64_t)addr) == (ssize_t)size);
                    close(mem_fd);
                }
            }
        } else {
            COPY_MEMORY cm{};
            cm.pid = this->pid;
            cm.addr = addr;
            cm.buffer = buffer;
            cm.size = size;
            success = (ioctl(fd, active_read_cmd, &cm) == 0);
        }

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
        if (!isConnected() && !initDriver()) return false;

        in_flight_ops.fetch_add(1, std::memory_order_relaxed);
        bool success = false;

        if (active_mode == DRIVER_MODE_PROC_MEM) {
            struct iovec local_iov = { (void*)buffer, size };
            struct iovec remote_iov = { (void*)addr, size };
            ssize_t n = process_vm_writev(this->pid, &local_iov, 1, &remote_iov, 1, 0);
            if (n == (ssize_t)size) {
                success = true;
            } else {
                char mem_path[64];
                snprintf(mem_path, sizeof(mem_path), "/proc/%d/mem", this->pid);
                int mem_fd = open(mem_path, O_WRONLY);
                if (mem_fd >= 0) {
                    success = (pwrite64(mem_fd, buffer, size, (off64_t)addr) == (ssize_t)size);
                    close(mem_fd);
                }
            }
        } else {
            COPY_MEMORY cm{};
            cm.pid = this->pid;
            cm.addr = addr;
            cm.buffer = const_cast<void*>(buffer);
            cm.size = size;
            success = (ioctl(fd, active_write_cmd, &cm) == 0);
        }
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
        if (!isConnected() && !initDriver()) return 0;

        if (active_mode != DRIVER_MODE_PROC_MEM && fd >= 0) {
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
        int old_fd = fd;
        fd = -1;
        active_mode = DRIVER_MODE_NONE;
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
