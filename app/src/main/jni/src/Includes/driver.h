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
#include <iostream>
#include <string>
#include <cstdint>
#include <atomic>
#include <mutex>
#include <vector>

#include "Logger.h"

// ══════════════════════════════════════════════════════════════════════
//  1. PROTOCOL OPCODES & IDENTIFIERS (Standardized Across All Systems)
// ══════════════════════════════════════════════════════════════════════

// [Group 1] RT DEV Standard (0x801 / 0x802 / 0x803)
#define DEV_OP_INIT_KEY       0x800
#define DEV_OP_READ_MEM       0x801
#define DEV_OP_WRITE_MEM      0x802
#define DEV_OP_MODULE_BASE    0x803
#define DEV_OP_HIDE_PROCESS   0x804
#define DEV_OP_GET_PID        0x805

// [Group 2] RT Hook / Classic Breakpoint
#define HOOK_OP_READ_MEM      601
#define HOOK_OP_WRITE_MEM     602
#define HOOK_OP_MODULE_BASE   603
#define HOOK_OP_GET_PID       604
#define HOOK_OP_HIDE_PROCESS  605
#define HOOK_OP_UN_HOOK       606
#define HOOK_OP_RECOVER       607
#define HOOK_OP_INIT_KEY      610

// [Group 3] RT-Dev / RTModule (3495161 / 3495162 / 3495163)
#define RT_CMD_READ           3495161
#define RT_CMD_WRITE          3495162
#define RT_CMD_BASE           3495163
#define RT_CMD_GETPID         3495164
#define RT_CMD_HIDE_PROCESS   3495165

// [Group 4] Alternative DEV / PROC
#define PROC_OP_INIT_KEY      0xC00
#define PROC_OP_READ_MEM      0xC01
#define PROC_OP_WRITE_MEM     0xC02
#define PROC_OP_MODULE_BASE   0xC03
#define PROC_OP_HIDE_PROCESS  0xC04

// [Group 5] Socket DGRAM IOCTL (HookRW)
#define SOCK_OP_READ_MEM      8001
#define SOCK_OP_WRITE_MEM     8002
#define SOCK_OP_GET_BASE      8003
#define SOCK_OP_GET_PID       8004
#define SOCK_OP_PROCESS_HIDE  8005

// [Group 6] NA KPM Neo Driver (prctl 0x4E41)
#define NA_PRCTL_MAGIC        0x4E41
#define NA_CMD_READ_MEM       1
#define NA_CMD_WRITE_MEM      2
#define NA_CMD_READ_PHYS      3
#define NA_CMD_WRITE_PHYS     4
#define NA_CMD_GET_PID        5
#define NA_MAX_RW_SIZE        4096
#define NA_MAX_PKG_NAME       256

// [Group 7] LDG KPM Driver (prctl 0x20250901)
#define LDG_PRCTL_READ_MEM    0x20250901
#define LDG_PRCTL_WRITE_MEM   0x20260121
#define LDG_PRCTL_GET_PID     0x20270108
#define LDG_PRCTL_PING        0x20270120  // Returns 0x1354c28

// ══════════════════════════════════════════════════════════════════════
//  2. SHARED DATA STRUCTURES
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

// ══════════════════════════════════════════════════════════════════════
//  3. THE CORE c_driver CLASS (Advanced Multi-Protocol Driver Engine)
// ══════════════════════════════════════════════════════════════════════

enum DriverMode {
    DRIVER_MODE_NONE        = 0,
    DRIVER_MODE_RT_HOOK     = 1, // Opcode 601/602/603
    DRIVER_MODE_RT_DEV      = 2, // Opcode 0x801/0x802/0x803
    DRIVER_MODE_DEV_PROC    = 3, // Opcode 0xC01/0xC02/0xC03
    DRIVER_MODE_QX_DAEMON   = 4, // Dynamic /proc/*/exe mknod
    DRIVER_MODE_KPM_NA      = 5, // prctl 0x4E41
    DRIVER_MODE_RT_MODULE   = 6, // Opcode 3495161/3495162/3495163
    DRIVER_MODE_HOOK_RW     = 7, // Socket DGRAM IOCTL 8001
    DRIVER_MODE_LDG_PRCTL   = 8, // prctl 0x20250901
    DRIVER_MODE_NODELESS    = 9  // Direct ioctl(0, ...)
};

class c_driver {
private:
    int fd;
    pid_t pid;
    DriverMode active_mode;
    int active_read_cmd;
    int active_write_cmd;
    int active_base_cmd;
    bool is_nodeless;
    char active_node_name[128];
    std::atomic<int> in_flight_ops;

    char* execCom(const char *shell) {
        FILE *fp = popen(shell, "r");
        if (!fp) return nullptr;
        char buffer[256];
        char *result = (char *)malloc(1024);
        if (!result) { pclose(fp); return nullptr; }
        result[0] = '\0';
        while (fgets(buffer, sizeof(buffer), fp) != nullptr) {
            strcat(result, buffer);
        }
        pclose(fp);
        return result;
    }

    void createDriverNode(const char *path, int major_number, int minor_number) {
        char cmd[256];
        snprintf(cmd, sizeof(cmd), "mknod %s c %d %d 2>/dev/null && chmod 666 %s 2>/dev/null", path, major_number, minor_number, path);
        system(cmd);
    }

    void removeDeviceNode(const char *path) {
        unlink(path);
    }

    int findFirstMatchingPath(const char *path, regex_t *regex, char *result) {
        DIR *dir = opendir(path);
        if (!dir) return 0;
        struct dirent *entry;
        while ((entry = readdir(dir)) != nullptr) {
            char fullpath[1024];
            snprintf(fullpath, sizeof(fullpath), "%s/%s", path, entry->d_name);
            if (entry->d_type == DT_LNK) {
                char linkpath[1024];
                ssize_t len = readlink(fullpath, linkpath, sizeof(linkpath) - 1);
                if (len != -1) {
                    linkpath[len] = '\0';
                    if (regexec(regex, linkpath, 0, nullptr, 0) == 0) {
                        strcpy(result, fullpath);
                        closedir(dir);
                        return 1;
                    }
                }
            }
        }
        closedir(dir);
        return 0;
    }

    // ──────────────────────────────────────────────────────────────────
    // Heuristic character device discovery in /dev (Creation Time & Inode)
    // ──────────────────────────────────────────────────────────────────
    char* driver_path() {
        const char *dev_path = "/dev";
        DIR *dir = opendir(dev_path);
        if (!dir) return nullptr;

        const char *known_names[] = { 
            "health_storage_service_alloc", "BYNb", "sched_debug", "uevents_records",
            "wanbai", "CheckMe", "Ckanri", "lanran", "video188", "rt_dev", "RTkernel",
            "unc_dr", "unc_ft", "TH520", "QX_driver", "FT_driver" 
        };
        const int known_count = sizeof(known_names) / sizeof(known_names[0]);

        struct dirent *entry;
        while ((entry = readdir(dir)) != nullptr) {
            if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
            if (strstr(entry->d_name, "gpiochip") || strstr(entry->d_name, "stdin") || 
                strstr(entry->d_name, "stdout") || strstr(entry->d_name, "stderr") ||
                strstr(entry->d_name, "null") || strstr(entry->d_name, "zero")) continue;

            char full_path[512];
            snprintf(full_path, sizeof(full_path), "%s/%s", dev_path, entry->d_name);

            // 1. Direct known name match
            for (int i = 0; i < known_count; i++) {
                if (strcmp(entry->d_name, known_names[i]) == 0) {
                    closedir(dir);
                    return strdup(full_path);
                }
            }

            // 2. Inode signature analysis
            struct stat info;
            if (stat(full_path, &info) < 0) continue;

            if (S_ISCHR(info.st_mode) || S_ISBLK(info.st_mode)) {
                if (strchr(entry->d_name, '_') == nullptr && 
                    strchr(entry->d_name, '-') == nullptr && 
                    strchr(entry->d_name, ':') == nullptr) {
                    
                    time_t atime = info.st_atime;
                    time_t ctime = info.st_ctime;
                    size_t name_len = strlen(entry->d_name);

                    // Dynamic kernel character device: atime == ctime, uid/gid 0, size 0, length 5..9
                    if (atime == ctime && info.st_size == 0 && info.st_gid == 0 && info.st_uid == 0 && name_len >= 5 && name_len <= 9) {
                        closedir(dir);
                        return strdup(full_path);
                    }
                }
            }
        }
        closedir(dir);
        return nullptr;
    }

    // ──────────────────────────────────────────────────────────────────
    // Self-test IOCTL read: Verifies real execution with a known magic
    // ──────────────────────────────────────────────────────────────────
    bool test_ioctl_read(int target_fd, int cmd) {
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
    // Probe KPM Neo via prctl
    // ──────────────────────────────────────────────────────────────────
    bool probe_kpm() {
        na_cmd cmd{};
        prctl(0xDEAD, 0, 0, 0, 0);
        memset(&cmd, 0, sizeof(cmd));
        int r = prctl(NA_PRCTL_MAGIC, NA_CMD_GET_PID, (unsigned long)&cmd, 0, 0);
        return (r == 0);
    }

    // ──────────────────────────────────────────────────────────────────
    // Probe LDG KPM via prctl ping
    // ──────────────────────────────────────────────────────────────────
    bool probe_ldg() {
        long ping_res = prctl(LDG_PRCTL_PING, 0, 0, 0, 0);
        if (ping_res == 0x1354c28) return true;
        
        pid_t self_pid = getpid();
        uintptr_t test_magic = 0x5A5AA5A512345678ULL;
        uintptr_t read_back = 0;
        LdgPrctlReadReq req = { (uint32_t)self_pid, 0, (uint64_t)&test_magic, (uint64_t)&read_back, sizeof(read_back) };
        return (prctl(LDG_PRCTL_READ_MEM, (unsigned long)&req, 0, 0, 0) == 0 && read_back == test_magic);
    }

public:
    c_driver() : fd(-1), pid(-1), active_mode(DRIVER_MODE_NONE),
                 active_read_cmd(DEV_OP_READ_MEM), active_write_cmd(DEV_OP_WRITE_MEM),
                 active_base_cmd(DEV_OP_MODULE_BASE), is_nodeless(false), in_flight_ops(0) {
        memset(active_node_name, 0, sizeof(active_node_name));
        initDriver();
    }

    ~c_driver() {
        closeDriver();
    }

    // ──────────────────────────────────────────────────────────────────
    // Unified Auto-Probe & Driver Connection
    // ──────────────────────────────────────────────────────────────────
    bool initDriver() {
        if (fd > 0 && active_mode != DRIVER_MODE_NONE) {
            return true;
        }

        NIN_LOGI("c_driver", "=== [DRIVER_INIT] Starting Advanced Multi-Protocol Auto-Detection ===");

        // [Step 1] Probe KPM Neo (Kernel Patch Module - Zero Device Node)
        if (probe_kpm()) {
            fd = 0;
            active_mode = DRIVER_MODE_KPM_NA;
            is_nodeless = true;
            strncpy(active_node_name, "KPM_NEO_PRCTL", sizeof(active_node_name) - 1);
            NIN_LOGI("c_driver", ">>> [DRIVER_ACTIVE] Mode: KPM Neo (prctl 0x4E41) Nodeless Connected! <<<");
            return true;
        }

        // [Step 2] Probe LDG KPM (prctl 0x20250901)
        if (probe_ldg()) {
            fd = 0;
            active_mode = DRIVER_MODE_LDG_PRCTL;
            is_nodeless = true;
            strncpy(active_node_name, "LDG_KPM_PRCTL", sizeof(active_node_name) - 1);
            NIN_LOGI("c_driver", ">>> [DRIVER_ACTIVE] Mode: LDG KPM (prctl 0x20250901) Connected! <<<");
            return true;
        }

        // [Step 3] Probe Nodeless IOCTL directly hooked on stdin (fd 0)
        const int test_cmds[] = { RT_CMD_READ, DEV_OP_READ_MEM, HOOK_OP_READ_MEM, PROC_OP_READ_MEM };
        for (int cmd : test_cmds) {
            if (test_ioctl_read(0, cmd)) {
                fd = 0;
                active_mode = DRIVER_MODE_NODELESS;
                active_read_cmd = cmd;
                if (cmd == RT_CMD_READ) { active_write_cmd = RT_CMD_WRITE; active_base_cmd = RT_CMD_BASE; }
                else if (cmd == DEV_OP_READ_MEM) { active_write_cmd = DEV_OP_WRITE_MEM; active_base_cmd = DEV_OP_MODULE_BASE; }
                else if (cmd == HOOK_OP_READ_MEM) { active_write_cmd = HOOK_OP_WRITE_MEM; active_base_cmd = HOOK_OP_MODULE_BASE; }
                else { active_write_cmd = PROC_OP_WRITE_MEM; active_base_cmd = PROC_OP_MODULE_BASE; }
                is_nodeless = true;
                strncpy(active_node_name, "SYS_IOCTL_FD0", sizeof(active_node_name) - 1);
                NIN_LOGI("c_driver", ">>> [DRIVER_ACTIVE] Mode: Nodeless ioctl(0) Connected with Opcode %d! <<<", cmd);
                return true;
            }
        }

        // [Step 4] Dedicated and Dynamic Character Device Scanning (/dev)
        char *device_path = driver_path();
        if (device_path != nullptr) {
            int try_fd = open(device_path, O_RDWR);
            if (try_fd >= 0) {
                // Determine opcode through self-test read
                int chosen_read = -1;
                for (int cmd : test_cmds) {
                    if (test_ioctl_read(try_fd, cmd)) {
                        chosen_read = cmd;
                        break;
                    }
                }

                // If test passed or device is our known RT Dev node, accept
                if (chosen_read > 0 || strstr(device_path, "health_storage_service_alloc") || strstr(device_path, "BYNb")) {
                    fd = try_fd;
                    if (chosen_read == RT_CMD_READ) {
                        active_mode = DRIVER_MODE_RT_MODULE;
                        active_read_cmd = RT_CMD_READ;
                        active_write_cmd = RT_CMD_WRITE;
                        active_base_cmd = RT_CMD_BASE;
                    } else if (chosen_read == HOOK_OP_READ_MEM) {
                        active_mode = DRIVER_MODE_RT_HOOK;
                        active_read_cmd = HOOK_OP_READ_MEM;
                        active_write_cmd = HOOK_OP_WRITE_MEM;
                        active_base_cmd = HOOK_OP_MODULE_BASE;
                    } else if (chosen_read == PROC_OP_READ_MEM) {
                        active_mode = DRIVER_MODE_DEV_PROC;
                        active_read_cmd = PROC_OP_READ_MEM;
                        active_write_cmd = PROC_OP_WRITE_MEM;
                        active_base_cmd = PROC_OP_MODULE_BASE;
                    } else {
                        active_mode = DRIVER_MODE_RT_DEV;
                        active_read_cmd = DEV_OP_READ_MEM;
                        active_write_cmd = DEV_OP_WRITE_MEM;
                        active_base_cmd = DEV_OP_MODULE_BASE;
                    }

                    strncpy(active_node_name, device_path, sizeof(active_node_name) - 1);
                    is_nodeless = false;
                    NIN_LOGI("c_driver", ">>> [DRIVER_ACTIVE] Device Node '%s' Connected (FD: %d, Mode: %d, ReadOp: %d) <<<", 
                             device_path, fd, (int)active_mode, active_read_cmd);
                    free(device_path);
                    return true;
                }
                close(try_fd);
            }
            free(device_path);
        }

        // [Step 5] QX Dynamic Daemon Detection (/proc/*/exe -> mknod)
        char *qx_output = execCom("ls -l /proc/*/exe 2>/dev/null | grep -E \"/data/[a-z]{6} \\(deleted\\)\"");
        if (qx_output && strlen(qx_output) > 0) {
            char pid_buf[32] = {0};
            char *pStart = strstr(qx_output, "/proc/");
            if (pStart) {
                char *pidS = pStart + 6;
                char *pidE = strchr(pidS, '/');
                if (pidE) {
                    strncpy(pid_buf, pidS, pidE - pidS);
                    char fd_path[128];
                    snprintf(fd_path, sizeof(fd_path), "/proc/%s/fd", pid_buf);

                    regex_t reg;
                    if (regcomp(&reg, ".*dev.*", 0) == 0) {
                        char match_res[512] = {0};
                        if (findFirstMatchingPath(fd_path, &reg, match_res)) {
                            char cmd_maj[256];
                            snprintf(cmd_maj, sizeof(cmd_maj), "ls -AL -l %s 2>/dev/null | grep -Eo '[0-9]{3},' | grep -Eo '[0-9]{3}'", match_res);
                            char *maj_info = execCom(cmd_maj);
                            if (maj_info && strlen(maj_info) > 0) {
                                int major_num = atoi(maj_info);
                                free(maj_info);

                                const char *tmp_qx_path = "/dev/nin_qx_tmp";
                                createDriverNode(tmp_qx_path, major_num, 0);
                                int qx_fd = open(tmp_qx_path, O_RDWR);
                                removeDeviceNode(tmp_qx_path); // Unlink immediately!

                                if (qx_fd >= 0) {
                                    fd = qx_fd;
                                    active_mode = DRIVER_MODE_QX_DAEMON;
                                    active_read_cmd = DEV_OP_READ_MEM;
                                    active_write_cmd = DEV_OP_WRITE_MEM;
                                    active_base_cmd = DEV_OP_MODULE_BASE;
                                    strncpy(active_node_name, "QX_DYNAMIC_MKNOD", sizeof(active_node_name) - 1);
                                    is_nodeless = false;
                                    NIN_LOGI("c_driver", ">>> [DRIVER_ACTIVE] Mode: QX Dynamic Daemon Node Connected! <<<");
                                    free(qx_output);
                                    regfree(&reg);
                                    return true;
                                }
                            }
                        }
                        regfree(&reg);
                    }
                }
            }
            free(qx_output);
        }

        // [Step 6] Socket DGRAM IOCTL (HookRW)
        int sock = socket(AF_INET, SOCK_DGRAM, 0);
        if (sock >= 0) {
            pid_t self_pid = getpid();
            uintptr_t test_magic = 0x5A5AA5A512345678ULL;
            uintptr_t read_back = 0;
            COPY_MEMORY test_cm = { self_pid, (uintptr_t)&test_magic, &read_back, sizeof(read_back) };
            if (ioctl(sock, SOCK_OP_READ_MEM, &test_cm) == 0 && read_back == test_magic) {
                fd = sock;
                active_mode = DRIVER_MODE_HOOK_RW;
                active_read_cmd = SOCK_OP_READ_MEM;
                active_write_cmd = SOCK_OP_WRITE_MEM;
                active_base_cmd = SOCK_OP_GET_BASE;
                is_nodeless = false;
                strncpy(active_node_name, "SOCKET_DGRAM_HOOK", sizeof(active_node_name) - 1);
                NIN_LOGI("c_driver", ">>> [DRIVER_ACTIVE] Mode: Socket DGRAM HookRW (8001) Connected! <<<");
                return true;
            }
            close(sock);
        }

        NIN_LOGE("c_driver", ">>> [DRIVER_FAIL] All 7 Driver Architectures Unreachable! Ensure Loader is Active. <<<");
        return false;
    }

    void initialize(pid_t target_pid) {
        this->pid = target_pid;
    }

    bool isConnected() {
        if (active_mode == DRIVER_MODE_KPM_NA || active_mode == DRIVER_MODE_LDG_PRCTL || active_mode == DRIVER_MODE_NODELESS) {
            return true;
        }
        return (fd > 0);
    }

    int getFd() const { return fd; }
    const char* getNodeName() const { return active_node_name; }
    DriverMode getDriverMode() const { return active_mode; }

    const char* getDriverModeName() const {
        switch (active_mode) {
            case DRIVER_MODE_RT_HOOK:   return "RT Hook (601)";
            case DRIVER_MODE_RT_DEV:    return "RT DEV (0x801)";
            case DRIVER_MODE_DEV_PROC:  return "DEV PROC (0xC01)";
            case DRIVER_MODE_QX_DAEMON: return "QX Daemon (RAM)";
            case DRIVER_MODE_KPM_NA:    return "KPM Neo (prctl)";
            case DRIVER_MODE_RT_MODULE: return "RT-Dev (3495161)";
            case DRIVER_MODE_HOOK_RW:   return "Socket HookRW (8001)";
            case DRIVER_MODE_LDG_PRCTL: return "LDG KPM (prctl)";
            case DRIVER_MODE_NODELESS:  return "Nodeless (ioctl 0)";
            default:                    return "OFFLINE";
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Core Memory Read (Safe, Chunked, Multi-Backend)
    // ──────────────────────────────────────────────────────────────────
    bool read(uintptr_t addr, void *buffer, size_t size) {
        if (addr == 0 || buffer == nullptr || size == 0 || this->pid <= 0) return false;
        if (!isConnected() && !initDriver()) return false;

        in_flight_ops.fetch_add(1, std::memory_order_relaxed);

        bool success = false;

        // Backend 1: KPM Neo (NA prctl)
        if (active_mode == DRIVER_MODE_KPM_NA) {
            uint8_t *dst = (uint8_t *)buffer;
            size_t remaining = size;
            uintptr_t cur_addr = addr;
            success = true;
            while (remaining > 0) {
                size_t chunk = (remaining > NA_MAX_RW_SIZE) ? NA_MAX_RW_SIZE : remaining;
                na_cmd cmd{};
                cmd.op = NA_CMD_READ_MEM;
                cmd.pid = (uint32_t)this->pid;
                cmd.addr = (uint64_t)cur_addr;
                cmd.size = (uint32_t)chunk;
                if (prctl(NA_PRCTL_MAGIC, NA_CMD_READ_MEM, (unsigned long)&cmd, 0, 0) != 0 || cmd.result != 0) {
                    success = false;
                    break;
                }
                memcpy(dst, cmd.data, chunk);
                dst += chunk;
                cur_addr += chunk;
                remaining -= chunk;
            }
        }
        // Backend 2: LDG KPM (prctl)
        else if (active_mode == DRIVER_MODE_LDG_PRCTL) {
            LdgPrctlReadReq req = { (uint32_t)this->pid, 0, (uint64_t)addr, (uint64_t)buffer, (uint64_t)size };
            success = (prctl(LDG_PRCTL_READ_MEM, (unsigned long)&req, 0, 0, 0) == 0);
        }
        // Backend 3: Character Device / Nodeless / Socket IOCTL
        else {
            COPY_MEMORY cm{};
            cm.pid = this->pid;
            cm.addr = addr;
            cm.buffer = buffer;
            cm.size = size;
            int use_fd = (fd >= 0) ? fd : 0;
            success = (ioctl(use_fd, active_read_cmd, &cm) == 0);
        }

        in_flight_ops.fetch_sub(1, std::memory_order_relaxed);
        return success;
    }

    // ──────────────────────────────────────────────────────────────────
    // Core Memory Write
    // ──────────────────────────────────────────────────────────────────
    bool write(uintptr_t addr, const void *buffer, size_t size) {
        if (addr == 0 || buffer == nullptr || size == 0 || this->pid <= 0) return false;
        if (!isConnected() && !initDriver()) return false;

        in_flight_ops.fetch_add(1, std::memory_order_relaxed);

        bool success = false;

        if (active_mode == DRIVER_MODE_KPM_NA) {
            const uint8_t *src = (const uint8_t *)buffer;
            size_t remaining = size;
            uintptr_t cur_addr = addr;
            success = true;
            while (remaining > 0) {
                size_t chunk = (remaining > NA_MAX_RW_SIZE) ? NA_MAX_RW_SIZE : remaining;
                na_cmd cmd{};
                cmd.op = NA_CMD_WRITE_MEM;
                cmd.pid = (uint32_t)this->pid;
                cmd.addr = (uint64_t)cur_addr;
                cmd.size = (uint32_t)chunk;
                memcpy(cmd.data, src, chunk);
                if (prctl(NA_PRCTL_MAGIC, NA_CMD_WRITE_MEM, (unsigned long)&cmd, 0, 0) != 0 || cmd.result != 0) {
                    success = false;
                    break;
                }
                src += chunk;
                cur_addr += chunk;
                remaining -= chunk;
            }
        }
        else if (active_mode == DRIVER_MODE_LDG_PRCTL) {
            LdgPrctlWriteReq req = { (uint32_t)this->pid, 0, (uint64_t)addr, (uint64_t)buffer, (uint64_t)size };
            success = (prctl(LDG_PRCTL_WRITE_MEM, (unsigned long)&req, 0, 0, 0) == 0);
        }
        else {
            COPY_MEMORY cm{};
            cm.pid = this->pid;
            cm.addr = addr;
            cm.buffer = const_cast<void*>(buffer);
            cm.size = size;
            int use_fd = (fd >= 0) ? fd : 0;
            success = (ioctl(use_fd, active_write_cmd, &cm) == 0);
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
    // Module Base Address Lookup
    // ──────────────────────────────────────────────────────────────────
    uintptr_t getModuleBase(const char *name) {
        if (!name || this->pid <= 0) return 0;
        if (!isConnected() && !initDriver()) return 0;

        // 1. If IOCTL module base command is available
        if (active_mode != DRIVER_MODE_KPM_NA && active_mode != DRIVER_MODE_LDG_PRCTL) {
            MODULE_BASE mb{};
            char buf[128] = {0};
            strncpy(buf, name, sizeof(buf) - 1);
            mb.pid = this->pid;
            mb.name = buf;
            mb.base = 0;
            int use_fd = (fd >= 0) ? fd : 0;
            if (ioctl(use_fd, active_base_cmd, &mb) == 0 && mb.base > 0) {
                return mb.base;
            }
        }

        // 2. Maps fallback for Nodeless & KPM
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

    void hide_process() {
        if (!isConnected()) return;
        int use_fd = (fd >= 0) ? fd : 0;
        if (active_mode == DRIVER_MODE_RT_HOOK) {
            ioctl(use_fd, HOOK_OP_HIDE_PROCESS);
        } else if (active_mode == DRIVER_MODE_RT_DEV || active_mode == DRIVER_MODE_DEV_PROC || active_mode == DRIVER_MODE_QX_DAEMON) {
            ioctl(use_fd, DEV_OP_HIDE_PROCESS);
        } else if (active_mode == DRIVER_MODE_RT_MODULE) {
            ioctl(use_fd, RT_CMD_HIDE_PROCESS);
        } else if (active_mode == DRIVER_MODE_HOOK_RW) {
            ioctl(use_fd, SOCK_OP_PROCESS_HIDE);
        }
    }

    void closeDriver() {
        int old_fd = fd;
        fd = -1;
        active_mode = DRIVER_MODE_NONE;
        // Drain in-flight operations safely
        while (in_flight_ops.load(std::memory_order_relaxed) > 0) {
            usleep(100);
        }
        if (old_fd > 0) {
            close(old_fd);
        }
    }
};

// ══════════════════════════════════════════════════════════════════════
//  4. GLOBAL INSTANCE & NAMESPACE MEMORY (Matching Reference Exactly)
// ══════════════════════════════════════════════════════════════════════

inline c_driver *driver = new c_driver();

namespace Memory {
    inline pid_t g_pid = -1;

    inline int ProcessRead(uintptr_t address, void* buffer, size_t size, pid_t pid = 0) {
        if (!buffer || address == 0) return -1;
        if (pid > 0 && driver) driver->initialize(pid);
        if (driver && driver->read(address, buffer, size)) return 0;
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
}

#endif // UNIFIED_DRIVER_H
