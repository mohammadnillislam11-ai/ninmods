#ifndef UTILS
#define UTILS

#include <jni.h>
#include <unistd.h>
#include <cstdio>
#include <cstring>
#include <string>
#include <cstdlib>
#include <vector>
#include <sstream>
#include <fstream>
#include <inttypes.h>
#include <fcntl.h>
#include <sys/uio.h>
#include <chrono>
#include <atomic>
#include "Logger.h"
#include "NativeCrypto.h"
#include "driver.h"

/* The target PID and the libil2cpp base are written by the JNI thread
 * (getDetectedGamePid / getDetectedGameTitle) AND by the scene worker thread
 * (InitMemoryAccess / GetModuleBase), while they are read by the render
 * thread and by the aim workers on every single memory access. They used to
 * be plain variables, which is a data race: a reader could pick up a PID
 * that belongs to a different game process. */
inline std::atomic<pid_t> target_pid{-1};
inline std::atomic<uintptr_t> libAddress{0};
inline uintptr_t anogs = 0;
inline int driver_fd = -1;
inline char active_driver_node[128] = {0};

/* Which build of the game is currently attached.
 * false = Free Fire Normal (com.dts.freefireth / freefirein)
 * true  = Free Fire MAX    (com.dts.freefiremax)
 * The two builds use different field offsets, so the whole engine resolves
 * its offsets through this single flag. */
inline std::atomic<bool> g_isGameMax{false};

static inline int OpenDriver() {
    if (driver && driver->isConnected()) {
        driver_fd = driver->getFd();
        snprintf(active_driver_node, sizeof(active_driver_node), "%s [%s]", driver->getNodeName(), driver->getDriverModeName());
        return driver_fd;
    }

    NIN_LOGI("DRIVER_A2Z", "=== [DRIVER_INIT] Connecting via Unified c_driver Engine ===");
    if (driver && driver->initDriver()) {
        driver_fd = driver->getFd();
        snprintf(active_driver_node, sizeof(active_driver_node), "%s [%s]", driver->getNodeName(), driver->getDriverModeName());
        NIN_LOGI("DRIVER_A2Z", ">>> [DRIVER_CONNECTED] SUCCESS: Attached via '%s' (FD: %d) <<<", active_driver_node, driver_fd);
        return driver_fd;
    }

    NIN_LOGE("DRIVER_A2Z", ">>> [DRIVER_FAIL] All candidate driver modes failed! Ensure Loader is running. <<<");
    return -1;
}

static inline bool IsDriverConnected() {
    if (!driver || !driver->isConnected()) {
        OpenDriver();
    }
    return (driver && driver->isConnected());
}

/**
 * Throttled driver (re)connection.
 *
 * The discovery probe walks /sys/class, /sys/dev/char and four device nodes
 * and performs an IOCTL self test - several hundred syscalls in total.
 * Calling it from the render loop (60 times per second) was a serious
 * performance problem on every device where the driver is not loaded yet.
 * The probe is now rate limited; a live connection is always returned
 * immediately.
 */
static inline bool EnsureDriverConnectedThrottled(int cooldownMs = 2000) {
    if (!driver) return false;
    if (driver->isConnected()) return true;

    static std::atomic<int64_t> s_lastAttempt{0};
    int64_t now = std::chrono::duration_cast<std::chrono::milliseconds>(
                      std::chrono::steady_clock::now().time_since_epoch()).count();
    int64_t last = s_lastAttempt.load(std::memory_order_relaxed);
    if (now - last < cooldownMs) return false;
    s_lastAttempt.store(now, std::memory_order_relaxed);
    return driver->initDriver();
}

static inline bool DriverReadMemory(pid_t pid, uintptr_t address, void* buffer, size_t size) {
    if (pid <= 0 || address == 0 || buffer == nullptr || size == 0) return false;
    if (!driver || !driver->isConnected()) {
        if (OpenDriver() <= 0 && (!driver || !driver->isConnected())) return false;
    }

    driver->initialize(pid);
    bool ret = driver->readSafe(address, buffer, size);
    if (ret) {
        static uint32_t s_read_success_count = 0;
        if ((++s_read_success_count % 500) == 1) {
            /* logcat only: this runs hundreds of times per frame and writing
             * it to the persistent log file would stall the render loop. */
            NIN_LOGD("DRIVER_A2Z", "[IOCTL_READ_OK] Sampled: pid=%d, addr=0x%lx, size=%zu bytes, total_reads=%u", pid, (unsigned long)address, size, s_read_success_count);
        }
        return true;
    } else {
        static uint32_t s_read_err_count = 0;
        if ((++s_read_err_count % 100) == 1) {
            NIN_LOGE("DRIVER_A2Z", "[IOCTL_READ_FAIL] Failed on pid=%d, addr=0x%lx, size=%zu", pid, (unsigned long)address, size);
        }
        return false;
    }
}

static inline bool DriverWriteMemory(pid_t pid, uintptr_t address, const void* buffer, size_t size) {
    if (pid <= 0 || address == 0 || buffer == nullptr || size == 0) return false;
    if (!driver || !driver->isConnected()) {
        if (OpenDriver() <= 0 && (!driver || !driver->isConnected())) return false;
    }

    driver->initialize(pid);
    bool ret = driver->write(address, buffer, size);
    if (ret) {
        /* The aim engine performs several writes per frame at 250 Hz.
         * Logging every single one filled the persistent log file and
         * serialised the whole engine behind the logger mutex, which is a
         * guaranteed frame-rate collapse. Sample it instead. */
        static uint32_t s_write_ok_count = 0;
        if ((++s_write_ok_count % 200) == 1) {
            NIN_LOGD("DRIVER_A2Z", "[IOCTL_WRITE_OK] Sampled: %zu bytes -> pid=%d addr=0x%lx (total=%u)", size, pid, (unsigned long)address, s_write_ok_count);
        }
        return true;
    } else {
        static uint32_t s_write_err_count = 0;
        if ((++s_write_err_count % 50) == 1) {
            NIN_LOGE("DRIVER_A2Z", "[IOCTL_WRITE_FAIL] Write failed on pid=%d at addr=0x%lx, size=%zu", pid, (unsigned long)address, size);
        }
        return false;
    }
}

static inline uintptr_t DriverGetModuleBase(pid_t pid, const char* moduleName) {
    if (pid <= 0 || moduleName == nullptr) return 0;
    if (!driver || !driver->isConnected()) {
        if (OpenDriver() <= 0 && (!driver || !driver->isConnected())) return 0;
    }

    driver->initialize(pid);
    uintptr_t base = driver->getModuleBase(moduleName);
    if (base > 0) {
        NIN_LOGI("DRIVER_A2Z", ">>> [IOCTL_BASE_OK] Module '%s' located at 0x%lx for pid=%d via c_driver <<<", moduleName, (unsigned long)base, pid);
        return base;
    } else {
        NIN_LOGE("DRIVER_A2Z", "[IOCTL_BASE_FAIL] Module '%s' query returned base=0x%lx for pid=%d", moduleName, (unsigned long)base, pid);
        return 0;
    }
}

#define halfShift 10
#define UNI_SUR_HIGH_START  (UTF32)0xD800
#define UNI_SUR_HIGH_END    (UTF32)0xDBFF
#define UNI_SUR_LOW_START   (UTF32)0xDC00
#define UNI_SUR_LOW_END     (UTF32)0xDFFF
#define UNI_REPLACEMENT_CHAR (UTF32)0x0000FFFD

typedef char UTF8;
typedef unsigned short UTF16;
typedef unsigned int UTF32;
typedef unsigned char uint8;
typedef unsigned int uint32;
static const UTF32 halfBase = 0x0010000UL;
static const UTF8 firstByteMark[7] = { 0x00, 0x00, static_cast<UTF8>(0xC0), static_cast<UTF8>(0xE0), static_cast<UTF8>(0xF0), static_cast<UTF8>(0xF8), static_cast<UTF8>(0xFC) };

#include <dirent.h>

// Check if an Android process is actually running in active foreground (not sleeping/cached by OS)
static bool IsProcessActiveForeground(pid_t pid) {
    char oomPath[64];
    snprintf(oomPath, sizeof(oomPath), "/proc/%d/oom_score_adj", pid);
    FILE* fp = fopen(oomPath, "r");
    if (!fp) return false;
    int score = 1000;
    if (fscanf(fp, "%d", &score) == 1) {
        fclose(fp);
        // On Android:
        // Active foreground app has oom_score_adj <= 200 (usually 0)
        // Background cached/dormant processes have oom_score_adj >= 900
        return score < 900;
    }
    fclose(fp);
    return true;
}

// Encontra o PID do processo com prioridade estrita para Free Fire Normal (com.dts.freefireth)
// e validação contra processos suspensos/em cache do Android
inline pid_t FindGamePid() {
    // 1. Pass 1: Look specifically for Normal Free Fire (com.dts.freefireth or com.dts.freefirein)
    DIR* dir = opendir("/proc");
    if (dir) {
        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            if (entry->d_type == DT_DIR) {
                char* endptr = nullptr;
                pid_t pid = strtol(entry->d_name, &endptr, 10);
                if (endptr && *endptr == '\0' && pid > 0) {
                    char cmdlinePath[64];
                    snprintf(cmdlinePath, sizeof(cmdlinePath), "/proc/%d/cmdline", pid);
                    FILE* fp = fopen(cmdlinePath, "rb");
                    if (fp) {
                        char cmdline[256] = {0};
                        size_t n = fread(cmdline, 1, sizeof(cmdline) - 1, fp);
                        fclose(fp);
                        if (n > 0) {
                            cmdline[n] = '\0';
                            if (strstr(cmdline, "com.dts.freefireth") != nullptr || strstr(cmdline, "com.dts.freefirein") != nullptr) {
                                closedir(dir);
                                g_isGameMax.store(false, std::memory_order_relaxed);
                                NIN_LOGI("DAEMON", "Found Game PID: %d (com.dts.freefireth/in via /proc)", pid);
                                return pid;
                            }
                        }
                    }
                }
            }
        }
        closedir(dir);
    }

    // 2. Pass 2: Look for active Free Fire MAX (com.dts.freefiremax)
    dir = opendir("/proc");
    if (dir) {
        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            if (entry->d_type == DT_DIR) {
                char* endptr = nullptr;
                pid_t pid = strtol(entry->d_name, &endptr, 10);
                if (endptr && *endptr == '\0' && pid > 0) {
                    char cmdlinePath[64];
                    snprintf(cmdlinePath, sizeof(cmdlinePath), "/proc/%d/cmdline", pid);
                    FILE* fp = fopen(cmdlinePath, "rb");
                    if (fp) {
                        char cmdline[256] = {0};
                        size_t n = fread(cmdline, 1, sizeof(cmdline) - 1, fp);
                        fclose(fp);
                        if (n > 0) {
                            cmdline[n] = '\0';
                            if (strstr(cmdline, "com.dts.freefiremax") != nullptr) {
                                if (IsProcessActiveForeground(pid)) {
                                    closedir(dir);
                                    g_isGameMax.store(true, std::memory_order_relaxed);
                                    NIN_LOGI("DAEMON", "Found Game PID: %d (com.dts.freefiremax active foreground)", pid);
                                    return pid;
                                } else {
                                    NIN_LOGI("DAEMON", "Ignoring cached dormant background com.dts.freefiremax (PID: %d)", pid);
                                }
                            }
                        }
                    }
                }
            }
        }
        closedir(dir);
    }

    return 0;
}

inline pid_t FindPid(const char *PackageName) {
    if (PackageName != nullptr && (strstr(PackageName, "freefire") != nullptr)) {
        return FindGamePid();
    }
    if (PackageName == nullptr) {
        return 0;
    }

    /*
     * NO PROCESS SPAWNING HERE.
     *
     * This used to run "pidof <package>" through popen(), which forks().
     * The Android app sandbox answers a plain fork() with SIGSYS and kills
     * the process, and spawning a shell just to read a PID is expensive on
     * a phone anyway.
     *
     * The same answer is obtained by reading /proc/<pid>/cmdline directly,
     * which costs no process at all and is what FindGamePid() does.
     */
    DIR *dir = opendir("/proc");
    if (dir == nullptr) {
        return 0;
    }

    pid_t found = 0;
    struct dirent *entry = nullptr;
    while ((entry = readdir(dir)) != nullptr) {
        char *endptr = nullptr;
        pid_t pid = strtol(entry->d_name, &endptr, 10);
        if (endptr == nullptr || *endptr != '\0' || pid <= 0) {
            continue;
        }

        char cmdlinePath[64];
        snprintf(cmdlinePath, sizeof(cmdlinePath), "/proc/%d/cmdline", pid);
        FILE *fp = fopen(cmdlinePath, "rb");
        if (fp == nullptr) {
            continue;
        }
        char cmdline[256] = {0};
        size_t n = fread(cmdline, 1, sizeof(cmdline) - 1, fp);
        fclose(fp);

        if (n > 0) {
            cmdline[n] = '\0';
            if (strstr(cmdline, PackageName) != nullptr) {
                found = pid;
                break;
            }
        }
    }
    closedir(dir);

    return found;
}

// Encontra o endereço base de uma biblioteca dentro do processo alvo
inline uintptr_t FindLibrary(const char* name, int index) {
    int i = 0;
    uintptr_t start = 0;
    char line[1024] = {0};
    char dname[128], fname[128];

    snprintf(dname, sizeof(dname), "%s", name);
    snprintf(fname, sizeof(fname), "/proc/%d/maps", target_pid.load());
    FILE* p = fopen(fname, "r");
    if (p) {
        while (fgets(line, sizeof(line), p)) {
            if (strstr(line, dname) != NULL) {
                i++;
                if (i == index) {
                    sscanf(line, "%" SCNxPTR, &start);
                    break;
                }
            }
        }
        fclose(p);
    }
    return start;
}

// Pure Kernel Driver Memory Read via IOCTL_RT_READ (Ring 0)
template<class T>
T Read(uintptr_t address) {
    T buf{};
    if (address == 0) return buf;
    if (DriverReadMemory(target_pid.load(), address, &buf, sizeof(T))) {
        return buf;
    }
    memset(&buf, 0, sizeof(T));
    return buf;
}

// Pure Kernel Driver String Read via IOCTL_RT_READ (Ring 0)
template<>
inline std::string Read<std::string>(uintptr_t address) {
    std::string result;
    if (address == 0) return result;
    const size_t max_len = 4096;
    char buffer[max_len] = {0};
    
    if (DriverReadMemory(target_pid.load(), address, buffer, max_len - 1)) {
        buffer[max_len - 1] = '\0';
        result = buffer;
    }
    return result;
}

inline size_t vm_readv(long address, void *buffer, size_t size) {
    if (address == 0 || buffer == nullptr || size == 0) return 0;
    if (DriverReadMemory(target_pid.load(), (uintptr_t)address, buffer, size)) {
        return size;
    }
    return 0;
}

// Pure Kernel Driver Memory Write via IOCTL_RT_WRITE (Ring 0)
template<typename T>
inline bool Write(uintptr_t address, T value) {
    if (address == 0) return false;
    return DriverWriteMemory(target_pid.load(), address, &value, sizeof(T));
}

inline uintptr_t getMemoryAddr(uintptr_t address) {
    return Read<uintptr_t>(address);
}

inline std::vector<uint8_t> ParseHexBytes(const std::string& hexString) {
    std::vector<uint8_t> bytes;
    std::istringstream stream(hexString);
    std::string byteStr;
    
    while (stream >> byteStr) {
        try {
            bytes.push_back(static_cast<uint8_t>(std::stoul(byteStr, nullptr, 16)));
        } catch (...) {
            // Ignore invalid bytes
        }
    }
    return bytes;
}

inline bool WriteIO(uintptr_t address, const std::vector<uint8_t>& bytes) {
    if (address == 0 || bytes.empty()) return false;
    return DriverWriteMemory(target_pid.load(), address, bytes.data(), bytes.size());
}

inline bool WriteIO(uintptr_t address, const std::string& hexString) {
    std::vector<uint8_t> bytes = ParseHexBytes(hexString);
    return WriteIO(address, bytes);
}

/* D3DMatrix and Vector4 used to be declared here. They now live in
 * Includes/TransformTypes.h, which is the single shared definition used by the
 * in-process engine (GameEngine.h) AND by the root Daemon. Declaring them in
 * both places produced a duplicate definition (compile error) as soon as both
 * headers were pulled into the same translation unit. */
#include "TransformTypes.h"

class MonoDictionary {
public:
    uintptr_t getValues() {
        return Read<uintptr_t>(reinterpret_cast<uintptr_t>(this) + 0x18) + 0x20;
    }
    
    int getNumValues() {
        return Read<int>(reinterpret_cast<uintptr_t>(this) + 0x20);
    }
};

inline uintptr_t string2Offset(const char *c) {
    int base = 16;
    
    // Safe conversion for all architectures
    std::string str(c);
    if (str.empty()) return 0;
    
    // Remove "0x" prefix if present
    if (str.size() > 2 && str[0] == '0' && (str[1] == 'x' || str[1] == 'X')) {
        str = str.substr(2);
    }
    
    uintptr_t result = 0;
    std::stringstream ss;
    ss << std::hex << str;
    ss >> result;
    
    return result;
}

typedef enum {
    strictConversion = 0,
    lenientConversion
} ConversionFlags;

typedef enum {
    conversionOK,        /* conversion successful */
    sourceExhausted,     /* partial character in source, but hit end */
    targetExhausted,     /* insuff. room in target for conversion */
    sourceIllegal,       /* source sequence is illegal/malformed */
    conversionFailed
} ConversionResult;

inline int Utf16_To_Utf8(const UTF16 *sourceStart, UTF8 *targetStart, size_t outLen,
                  ConversionFlags flags) {
    int result = conversionOK;
    const UTF16 *source = sourceStart;
    UTF8 *target = targetStart;
    UTF8 *targetEnd = targetStart + outLen;

    if ((source == nullptr) || (targetStart == nullptr)) {
        return conversionFailed;
    }

    while (*source) {
        UTF32 ch;
        unsigned short bytesToWrite = 0;
        const UTF32 byteMask = 0xBF;
        const UTF32 byteMark = 0x80;
        const UTF16 *oldSource = source;

        ch = *source++;

        if (ch >= UNI_SUR_HIGH_START && ch <= UNI_SUR_HIGH_END) {
            if (*source) {
                UTF32 ch2 = *source;
                if (ch2 >= UNI_SUR_LOW_START && ch2 <= UNI_SUR_LOW_END) {
                    ch = ((ch - UNI_SUR_HIGH_START) << halfShift) + (ch2 - UNI_SUR_LOW_START) + halfBase;
                    ++source;
                } else if (flags == strictConversion) {
                    --source;
                    result = sourceIllegal;
                    break;
                }
            } else {
                --source;
                result = sourceExhausted;
                break;
            }
        } else if (flags == strictConversion) {
            if (ch >= UNI_SUR_LOW_START && ch <= UNI_SUR_LOW_END) {
                --source;
                result = sourceIllegal;
                break;
            }
        }

        if (ch < (UTF32)0x80) {
            bytesToWrite = 1;
        } else if (ch < (UTF32)0x800) {
            bytesToWrite = 2;
        } else if (ch < (UTF32)0x10000) {
            bytesToWrite = 3;
        } else if (ch < (UTF32)0x110000) {
            bytesToWrite = 4;
        } else {
            bytesToWrite = 3;
            ch = UNI_REPLACEMENT_CHAR;
        }

        target += bytesToWrite;
        if (target > targetEnd) {
            source = oldSource;
            target -= bytesToWrite;
            result = targetExhausted;
            break;
        }
        
        switch (bytesToWrite) {
            case 4:
                *--target = (UTF8)((ch | byteMark) & byteMask);
                ch >>= 6;
                [[fallthrough]];
            case 3:
                *--target = (UTF8)((ch | byteMark) & byteMask);
                ch >>= 6;
                [[fallthrough]];
            case 2:
                *--target = (UTF8)((ch | byteMark) & byteMask);
                ch >>= 6;
                [[fallthrough]];
            case 1:
                *--target = (UTF8)(ch | firstByteMark[bytesToWrite]);
        }
        target += bytesToWrite;
    }
    
    // Null terminate if there's space
    if (target < targetEnd) {
        *target = '\0';
    } else if (target == targetEnd) {
        *(target - 1) = '\0';
    }
    
    return result;
}

inline void getCharacterName(uintptr_t address, UTF8 *transcoding, size_t bufSize) {
    if (bufSize < 1) return;
    
    memset(transcoding, 0, bufSize);
    
    UTF16 buf16[34] = {0};
    int m = 0;
    
    for (int i = 0; i < 4; i++) {
        int classname = Read<int>(address + i * 4);
        int high = (classname >> 16) & 0xFFFF;
        int low = classname & 0xFFFF;
        
        if (low) buf16[m++] = static_cast<UTF16>(low);
        if (high) buf16[m++] = static_cast<UTF16>(high);
        if (m >= 32) break; // Prevent buffer overflow
    }
    
    buf16[m] = 0; // Null terminate UTF16 string
    
    int result = Utf16_To_Utf8(buf16, transcoding, bufSize - 1, strictConversion);
    if (result != conversionOK) {
        // Fallback to empty string
        transcoding[0] = '\0';
    }
}

// Helper functions for common operations via Kernel Driver (Ring 0)
inline bool WriteBytes(uintptr_t address, const void* data, size_t size) {
    if (size == 0) return true;
    if (address == 0 || data == nullptr) return false;
    return DriverWriteMemory(target_pid.load(), address, data, size);
}

inline bool ReadBytes(uintptr_t address, void* buffer, size_t size) {
    if (size == 0 || buffer == nullptr || address == 0) return false;
    return DriverReadMemory(target_pid.load(), address, buffer, size);
}

// Initialize the Kernel Driver connection
inline bool InitMemoryAccess(pid_t pid) {
    target_pid.store(pid, std::memory_order_relaxed);
    return IsDriverConnected();
}

// Cleanup Kernel Driver connection
inline void CleanupMemoryAccess() {
    if (driver) {
        driver->closeDriver();
    }
    driver_fd = -1;
    active_driver_node[0] = '\0';
    target_pid.store(-1, std::memory_order_relaxed);
}

#endif // UTILS
