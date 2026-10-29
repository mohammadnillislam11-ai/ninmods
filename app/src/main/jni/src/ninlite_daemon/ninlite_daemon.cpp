/**
 * NINLITE ROOT DAEMON  (QX CHANNEL - IPC MODE)
 * =================================================
 * This is the out-of-process engine of the QX channel. It runs with root
 * privileges, owns the memory access to the game process and streams the
 * reconstructed scene (entities, screen positions, health, weapon, knock
 * state) to the Main App over a length prefixed UNIX domain socket.
 *
 * ARCHITECTURE RULES HONOURED HERE (see AGENTS.md):
 *  - Rule 3 (Strict Driver Isolation): the QX channel is completely separate
 *    from the RT channel. This Daemon never opens a character device and
 *    never issues an IOCTL - the Ring-0 driver belongs to the in-app engine
 *    only. The user selects the channel, neither one falls back into the
 *    other one. The access primitives of this channel are the official Linux
 *    syscalls (process_vm_readv / process_vm_writev) with the POSIX
 *    /proc/<pid>/mem path and /proc/<pid>/maps for the module base.
 *  - Rule 9: all offsets live in the single central file gameoffsets.h.
 *  - Rule 1: every reported value comes from a genuine access result, a
 *    refused access is reported and never faked.
 *
 * RELIABILITY FIXES (all of these were real crashes / dead-ends before):
 *  - The wire protocol is defined once in Includes/NinliteProtocol.h and is
 *    shared with the App, so the two sides can never drift apart.
 *  - Frame length is validated against the destination capacity before any
 *    payload byte is copied (no more stack smashing on a malformed frame).
 *  - A client disconnect only closes the client socket; the listening socket
 *    survives and the Daemon keeps serving.
 *  - The listening socket is re-armed automatically if it was ever closed.
 *  - The game PID / libil2cpp base are re-resolved only when the game process
 *    actually dies, not on every connection.
 */

#if __has_include(<dirent.h>)
#include <dirent.h>
#else
#include "../Includes/android_ide_stubs/dirent.h"
#endif

#if __has_include(<fcntl.h>)
#include <fcntl.h>
#else
#include "../Includes/android_ide_stubs/fcntl.h"
#endif

#if __has_include(<unistd.h>)
#include <unistd.h>
#else
#include "../Includes/android_ide_stubs/unistd.h"
#endif

#if __has_include(<sys/stat.h>)
#include <sys/stat.h>
#else
#include "../Includes/android_ide_stubs/sys/stat.h"
#endif

#if __has_include(<sys/uio.h>)
#include <sys/uio.h>
#else
#include "../Includes/android_ide_stubs/sys/uio.h"
#endif

#if __has_include(<sys/socket.h>)
#include <sys/socket.h>
#else
#include "../Includes/android_ide_stubs/sys/socket.h"
#endif

#if __has_include(<netdb.h>)
#include <netdb.h>
#else
#include "../Includes/android_ide_stubs/netdb.h"
#endif

#if __has_include(<android/log.h>)
#include <android/log.h>
#else
#include "../Includes/android_ide_stubs/android/log.h"
#endif

#include <time.h>
#include <ctype.h>
#include <signal.h>
#include <cstdarg>
#include "../Engine/OfficialMemory.h"
#include <atomic>
#include <thread>
#include <chrono>
#include <cmath>
#include <vector>
#include <string>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <unordered_map>
#include <unordered_set>
#include <mutex>
#include <random>

#include "NinLiteSocketServer.h"
#include "BooleanServer.h"
#include "Namegun.h"
#include "../Includes/gameoffsets.h"
#include <atomic>
#include "../Includes/NinliteProtocol.h"
#include "../Includes/TransformTypes.h"
#include "../Unity/Vector3.hpp"
#include "../Unity/Vector4.hpp"
#include "../Unity/Quaternion.hpp"

#define TAG "NINLITE"

static void DaemonLog(const char* level, const char* format, ...) {
    char buf[1024];
    va_list args;
    va_start(args, format);
    vsnprintf(buf, sizeof(buf), format, args);
    va_end(args);

    __android_log_print(level[0] == 'E' ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO, TAG, "%s", buf);

    time_t now = time(nullptr);
    struct tm tm_buf;
    localtime_r(&now, &tm_buf);
    char timeStr[32];
    snprintf(timeStr, sizeof(timeStr), "%02d:%02d:%02d", tm_buf.tm_hour, tm_buf.tm_min, tm_buf.tm_sec);

    fprintf(stderr, "[%s] [%s] %s\n", timeStr, level, buf);
    fflush(stderr);
}
#define LOGI(...) DaemonLog("INFO", __VA_ARGS__)
#define LOGE(...) DaemonLog("ERROR", __VA_ARGS__)

/* ============================================================== state ==== */

/* The PID, the libil2cpp base and the local player pointer are written by the
 * socket thread (AttachToGame / CreateDataList) and read by the two aim
 * threads. They used to be plain variables - a data race that could make an
 * aim thread act on a stale or foreign game object. */
static std::atomic<pid_t>   g_target_pid{-1};
static std::atomic<uintptr_t> g_libAddress{0};
static std::atomic<bool> g_isGameMax{true};
static NinLiteSocketServer g_server;

static std::atomic<int> g_screenWidth{1920};
static std::atomic<int> g_screenHeight{1080};

static std::atomic<uintptr_t> g_currentLocalPlayer{0};
static uintptr_t currentBestTargetObj = 0;
static Vector3   currentBestTargetPos = Vector3::Zero();
static std::atomic<bool> keepAiming{false};
static std::mutex  g_targetMutex;

static uintptr_t g_aimHardTargetObj = 0;
static Vector3   g_aimHardTargetPos = Vector3::Zero();
static std::atomic<bool> g_hasAimHardTarget{false};
static std::mutex  g_aimHardMutex;

static std::unordered_map<uintptr_t, bool> g_isScaled;
static std::mutex g_scaleMutex;

static const std::unordered_set<short> blockedWeaponIds = {
    6016, 10006, -15524, -15521, 601, 603, 1201, 1204, 602, 608, 23, 1401, 1006, 1015
};

/* Official Linux memory access only (process_vm_readv / process_vm_writev).
 * See Engine/OfficialMemory.h. No kernel driver, no device node, no IOCTL. */
template <typename T>
static T DaemonRead(uintptr_t addr) {
    return NinOfficial::Read<T>(g_target_pid.load(std::memory_order_relaxed), addr);
}

template <typename T>
static bool DaemonWrite(uintptr_t addr, T val) {
    return NinOfficial::Write<T>(g_target_pid.load(std::memory_order_relaxed), addr, val);
}

static bool DaemonReadBytes(uintptr_t addr, void* buf, size_t n) {
    return NinOfficial::ReadBytes(g_target_pid.load(std::memory_order_relaxed), addr, buf, n);
}

/* ============================================================ helpers ==== */

static bool IsValidPtr(uintptr_t p) {
    if ((p >> 48) != 0) return false;
    if (p < 0x10000000ULL || p > 0x00007FFFFFFFFFFFULL) return false;
    return (p & 0x3) == 0;
}

static Vector3 GetCameraWorldPosManual(D3DMatrix m) {
    float a11 = m._11, a12 = m._21, a13 = m._31;
    float a21 = m._12, a22 = m._22, a23 = m._32;
    float a31 = m._14, a32 = m._24, a33 = m._34;
    float b1 = -m._41, b2 = -m._42, b3 = -m._44;

    float det = a11 * (a22 * a33 - a23 * a32)
              - a12 * (a21 * a33 - a23 * a31)
              + a13 * (a21 * a32 - a22 * a31);

    if (fabsf(det) < 0.0001f) return Vector3::Zero();
    float inv = 1.0f / det;

    return Vector3(
        (b1 * (a22 * a33 - a23 * a32) - a12 * (b2 * a33 - a23 * b3) + a13 * (b2 * a32 - a22 * b3)) * inv,
        (a11 * (b2 * a33 - a23 * b3) - b1 * (a21 * a33 - a23 * a31) + a13 * (a21 * b3 - b2 * a31)) * inv,
        (a11 * (a22 * b3 - b2 * a32) - a12 * (a21 * b3 - b2 * a31) + b1 * (a21 * a32 - a22 * a31)) * inv
    );
}

static Vector3 WorldToScreenPoint(D3DMatrix viewMatrix, Vector3 ScreenPos) {
    Vector3 result = Vector3(-1, -1, -1);

    float v9  = (ScreenPos.X * viewMatrix._11) + (ScreenPos.Y * viewMatrix._21) + (ScreenPos.Z * viewMatrix._31) + viewMatrix._41;
    float v10 = (ScreenPos.X * viewMatrix._12) + (ScreenPos.Y * viewMatrix._22) + (ScreenPos.Z * viewMatrix._32) + viewMatrix._42;
    float v12 = (ScreenPos.X * viewMatrix._14) + (ScreenPos.Y * viewMatrix._24) + (ScreenPos.Z * viewMatrix._34) + viewMatrix._44;

    if (v12 >= 0.001f) {
        float v13 = (float)g_screenWidth.load(std::memory_order_relaxed) / 2.0f;
        float v14 = (float)g_screenHeight.load(std::memory_order_relaxed) / 2.0f;
        result.X = v13 + (v13 * v9) / v12;
        result.Y = v14 - (v14 * v10) / v12;
        result.Z = v12;
    }
    return result;
}

static Vector3 GetPosition(uintptr_t Transform) {
    Vector3 pos = Vector3::Zero();
    if (!IsValidPtr(Transform)) return pos;

    uintptr_t transformObjValue = DaemonRead<uintptr_t>(Transform + Offsets::TransformObj);
    if (!IsValidPtr(transformObjValue)) return pos;

    uintptr_t indexValue = DaemonRead<uintptr_t>(transformObjValue + Offsets::TransformIndex);
    uintptr_t matrixValue = DaemonRead<uintptr_t>(transformObjValue + Offsets::TransformMatrix);
    if (!IsValidPtr(matrixValue)) return pos;

    uintptr_t matrixListValue = DaemonRead<uintptr_t>(matrixValue + Offsets::MatrixList);
    uintptr_t matrixIndicesValue = DaemonRead<uintptr_t>(matrixValue + Offsets::MatrixIndices);
    if (!IsValidPtr(matrixListValue) || !IsValidPtr(matrixIndicesValue)) return pos;

    Vector3 resultValue = DaemonRead<Vector3>(matrixListValue + (indexValue * Offsets::MatrixDataSize));

    const int maxTries = 50;
    int tries = 0;
    int transformIndexValue = DaemonRead<int>(matrixIndicesValue + (indexValue * Offsets::TransformIndexSize));

    while (transformIndexValue >= 0 && tries < maxTries) {
        tries++;
        uintptr_t itemPtr = matrixListValue + ((uintptr_t)transformIndexValue * Offsets::MatrixDataSize);
        if (!IsValidPtr(itemPtr)) break;

        Matrix tMatrixValue = DaemonRead<Matrix>(itemPtr);

        float rotX = tMatrixValue.Rotation.X;
        float rotY = tMatrixValue.Rotation.Y;
        float rotZ = tMatrixValue.Rotation.Z;
        float rotW = tMatrixValue.Rotation.W;

        float scaleX = resultValue.X * tMatrixValue.Scale.X;
        float scaleY = resultValue.Y * tMatrixValue.Scale.Y;
        float scaleZ = resultValue.Z * tMatrixValue.Scale.Z;

        resultValue.X = tMatrixValue.Position.X + scaleX +
                        (scaleX * ((rotY * rotY * -2.0) - (rotZ * rotZ * 2.0))) +
                        (scaleY * ((rotW * rotZ * -2.0) - (rotY * rotX * -2.0))) +
                        (scaleZ * ((rotZ * rotX * 2.0) - (rotW * rotY * -2.0)));
        resultValue.Y = tMatrixValue.Position.Y + scaleY +
                        (scaleX * ((rotX * rotY * 2.0) - (rotW * rotZ * -2.0))) +
                        (scaleY * ((rotZ * rotZ * -2.0) - (rotX * rotX * 2.0))) +
                        (scaleZ * ((rotW * rotX * -2.0) - (rotZ * rotY * -2.0)));
        resultValue.Z = tMatrixValue.Position.Z + scaleZ +
                        (scaleX * ((rotW * rotY * -2.0) - (rotX * rotZ * -2.0))) +
                        (scaleY * ((rotY * rotZ * 2.0) - (rotW * rotX * -2.0))) +
                        (scaleZ * ((rotX * rotX * -2.0) - (rotY * rotY * 2.0)));

        uintptr_t indexItemPtr = matrixIndicesValue + ((uintptr_t)transformIndexValue * Offsets::TransformIndexSize);
        if (!IsValidPtr(indexItemPtr)) break;
        transformIndexValue = DaemonRead<int>(indexItemPtr);
    }

    if (tries < maxTries) pos = resultValue;
    return pos;
}

static Vector3 GetNodePosition(uintptr_t nodeTransform) {
    if (!IsValidPtr(nodeTransform)) return Vector3::Zero();
    uintptr_t transformValue = DaemonRead<uintptr_t>(nodeTransform + Offsets::TransformObj);
    if (!IsValidPtr(transformValue)) return Vector3::Zero();
    return GetPosition(transformValue);
}

/* --------------------------------------------------------- bone offsets */

static uintptr_t GetPlayerHeadTF(uintptr_t player) {
    if (!player) return 0;
    uintptr_t off = g_isGameMax.load(std::memory_order_relaxed)
                        ? Offsets::Max::Player_HeadTF : Offsets::Normal::Bone_Head;
    uintptr_t tf = DaemonRead<uintptr_t>(player + off);
    if (!IsValidPtr(tf)) {
        tf = DaemonRead<uintptr_t>(player + Offsets::HeadFallback);
    }
    return IsValidPtr(tf) ? tf : 0;
}

static uintptr_t GetPlayerHipTF(uintptr_t player) {
    if (!player) return 0;
    /* MAX hip bone is 0x6B0 - the previous code wrongly used the foot bone
     * (0x6D0) for the hip, which collapsed the chest interpolation. */
    uintptr_t off = g_isGameMax.load(std::memory_order_relaxed)
                        ? Offsets::Max::Bone_Hip : Offsets::Normal::Bone_Hip;
    uintptr_t tf = DaemonRead<uintptr_t>(player + off);
    return IsValidPtr(tf) ? tf : 0;
}

static uintptr_t GetPlayerRootTF(uintptr_t player) {
    if (!player) return 0;
    uintptr_t off = g_isGameMax.load(std::memory_order_relaxed)
                        ? Offsets::Max::Bone_Feet : Offsets::Feet;
    uintptr_t tf = DaemonRead<uintptr_t>(player + off);
    if (!IsValidPtr(tf)) {
        tf = DaemonRead<uintptr_t>(player + Offsets::FeetFallback);
    }
    return IsValidPtr(tf) ? tf : 0;
}

static void getUTF8(char* dst, size_t dstLen, uintptr_t addr) {
    if (!dst || dstLen == 0) return;
    dst[0] = '\0';
    if (addr < 0x10000000) return;

    int stringLen = DaemonRead<int>(addr + Offsets::StringLen);
    if (stringLen <= 0 || stringLen > 64) stringLen = 32;

    unsigned char raw[128];
    memset(raw, 0, sizeof(raw));
    size_t toRead = (size_t)stringLen * 2;
    if (toRead > sizeof(raw)) toRead = sizeof(raw);
    if (!DaemonReadBytes(addr + Offsets::StringChars, raw, toRead)) return;

    size_t j = 0;
    for (size_t i = 0; i + 1 < toRead; i += 2) {
        unsigned short unicode = (unsigned short)(raw[i] | (raw[i + 1] << 8));
        if (unicode == 0) break;
        if (unicode < 0x80) {
            if (j + 1 >= dstLen) break;
            dst[j++] = (char)unicode;
        } else if (unicode < 0x800) {
            if (j + 2 >= dstLen) break;
            dst[j++] = (char)((unicode >> 6) | 0xC0);
            dst[j++] = (char)((unicode & 0x3F) | 0x80);
        } else if (unicode >= 0xD800 && unicode <= 0xDBFF) {
            i += 2;
            if (i + 1 >= toRead) break;
            unsigned short low = (unsigned short)(raw[i] | (raw[i + 1] << 8));
            unsigned int codepoint = 0x10000 + ((unicode - 0xD800) << 10) + (low - 0xDC00);
            if (j + 4 >= dstLen) break;
            dst[j++] = (char)((codepoint >> 18) | 0xF0);
            dst[j++] = (char)(((codepoint >> 12) & 0x3F) | 0x80);
            dst[j++] = (char)(((codepoint >> 6) & 0x3F) | 0x80);
            dst[j++] = (char)((codepoint & 0x3F) | 0x80);
        } else {
            if (j + 3 >= dstLen) break;
            dst[j++] = (char)((unicode >> 12) | 0xE0);
            dst[j++] = (char)(((unicode >> 6) & 0x3F) | 0x80);
            dst[j++] = (char)((unicode & 0x3F) | 0x80);
        }
    }
    dst[j] = '\0';
}

static uint64_t ReadCurrentWeapon(uintptr_t player) {
    if (!player) return 0;
    uintptr_t inventoryManager = DaemonRead<uintptr_t>(player + Offsets::InventoryManager);
    if (!IsValidPtr(inventoryManager)) {
        inventoryManager = DaemonRead<uintptr_t>(player + Offsets::InventoryManagerOld);
    }
    if (!IsValidPtr(inventoryManager)) return 0;

    bool isCombined = DaemonRead<bool>(inventoryManager + Offsets::IsCombined);
    uintptr_t weapon = isCombined
        ? DaemonRead<uintptr_t>(inventoryManager + Offsets::CombinedWeapon)
        : DaemonRead<uintptr_t>(inventoryManager + Offsets::InventoryItem);
    if (!IsValidPtr(weapon)) return 0;

    uintptr_t itemData = DaemonRead<uintptr_t>(weapon + Offsets::ItemData);
    if (!IsValidPtr(itemData)) return 0;
    return DaemonRead<uint64_t>(itemData + Offsets::WeaponId);
}

static bool isWithinFOV(Vector3 screenPos, float fovRadius) {
    float cx = g_screenWidth.load(std::memory_order_relaxed) / 2.0f;
    float cy = g_screenHeight.load(std::memory_order_relaxed) / 2.0f;
    float dx = screenPos.X - cx;
    float dy = screenPos.Y - cy;
    return sqrtf(dx * dx + dy * dy) <= fovRadius;
}

/* ======================================================= process scan ==== */

static pid_t ScanTargetGamePid() {
    bool isMax = true;
    pid_t fastPid = NinOfficial::FastScanPid(isMax);
    if (fastPid > 0) {
        g_isGameMax.store(isMax, std::memory_order_relaxed);
        return fastPid;
    }

    DIR* dir = opendir("/proc");
    if (!dir) return -1;
    struct dirent* entry;
    pid_t normalFound = -1;
    pid_t maxFound = -1;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type != DT_DIR && entry->d_type != DT_UNKNOWN) continue;
        if (!isdigit((unsigned char)entry->d_name[0])) continue;
        char cmdPath[80];
        snprintf(cmdPath, sizeof(cmdPath), "/proc/%s/cmdline", entry->d_name);
        FILE* f = fopen(cmdPath, "rb");
        if (!f) continue;
        char cmdline[256] = {0};
        size_t n = fread(cmdline, 1, sizeof(cmdline) - 1, f);
        fclose(f);
        if (n == 0) continue;
        cmdline[n] = '\0';
        if (strstr(cmdline, "com.dts.freefiremax")) {
            if (maxFound < 0) maxFound = (pid_t)atoi(entry->d_name);
            continue;
        }
        if (strstr(cmdline, "com.dts.freefireth") || strstr(cmdline, "com.dts.freefirein")) {
            if (normalFound < 0) normalFound = (pid_t)atoi(entry->d_name);
        }
    }
    closedir(dir);

    if (normalFound > 0) {
        g_isGameMax.store(false, std::memory_order_relaxed);
        return normalFound;
    }
    if (maxFound > 0) {
        g_isGameMax.store(true, std::memory_order_relaxed);
        return maxFound;
    }
    return -1;
}

static bool IsElfHeader(uintptr_t addr) {
    if (addr == 0) return false;
    unsigned char magic[4] = {0};
    if (!DaemonReadBytes(addr, magic, 4)) return false;
    return (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F');
}

static uintptr_t FindCorrectLibBase(pid_t pid) {
    uintptr_t fastBase = NinOfficial::FastModuleBase(pid, "libil2cpp.so");
    if (fastBase > 0 && IsElfHeader(fastBase)) {
        return fastBase;
    }

    char mapsPath[64];
    snprintf(mapsPath, sizeof(mapsPath), "/proc/%d/maps", pid);
    FILE* maps = fopen(mapsPath, "r");
    if (!maps) return 0;

    char line[512];
    uintptr_t elfCandidates[8] = {0};
    int count = 0;

    while (fgets(line, sizeof(line), maps) && count < 8) {
        if (strstr(line, "libil2cpp.so")) {
            uintptr_t start = 0;
            if (sscanf(line, "%lx", &start) == 1 && IsElfHeader(start)) {
                elfCandidates[count++] = start;
            }
        }
    }
    fclose(maps);

    uintptr_t gameFacadeOffset = g_isGameMax.load(std::memory_order_relaxed)
                                      ? Offsets::Max::GameFacade : Offsets::Normal::GameFacade;
    for (int i = 0; i < count; i++) {
        if (elfCandidates[i] == 0) continue;
        uintptr_t testFacade = DaemonRead<uintptr_t>(elfCandidates[i] + gameFacadeOffset);
        if (testFacade > 0x10000000ULL && (testFacade >> 48) == 0) {
            return elfCandidates[i];
        }
    }
    return (count > 0) ? elfCandidates[0] : 0;
}

/** Re-resolves the game process and libil2cpp base. Only called when the
 *  previously resolved target is actually gone. */
static bool AttachToGame() {
    pid_t pid = ScanTargetGamePid();
    if (pid <= 0) return false;

    g_target_pid.store(pid, std::memory_order_relaxed);

    uintptr_t libBase = FindCorrectLibBase(pid);
    g_libAddress.store(libBase, std::memory_order_relaxed);

    if (libBase != 0) {
        uint32_t elfMagic = 0;
        bool readOk = NinOfficial::ReadBytes(pid, libBase, &elfMagic, sizeof(elfMagic));
        if (readOk && (elfMagic == 0x464c457f)) {
            LOGI("[DAEMON] Verified genuine process_vm_readv access on libil2cpp (ELF: 0x%08x)", elfMagic);
        } else if (!readOk) {
            LOGE("[DAEMON] process_vm_readv refused at 0x%lx for PID %d (errno: %d - %s)",
                 (unsigned long)libBase, (int)pid, errno, strerror(errno));
        }
    }

    LOGI("[DAEMON] Attached to Game PID: %d (MAX: %d), libil2cpp: 0x%lx, access: %s",
         (int)pid, (int)g_isGameMax.load(std::memory_order_relaxed), (unsigned long)libBase,
         "process_vm_readv");
    return libBase != 0;
}

static bool GameStillAlive() {
    pid_t pid = g_target_pid.load(std::memory_order_relaxed);
    if (pid <= 0) return false;
    if (kill(pid, 0) != 0) return false;
    return true;
}

/* ==================================================== background threads = */

static void SilentAimThread() {
    while (true) {
        uintptr_t local = g_currentLocalPlayer.load(std::memory_order_relaxed);
        if (pPlayer.silen4a && local != 0 && GameStillAlive()) {
            uintptr_t aimInfoOff = g_isGameMax.load(std::memory_order_relaxed)
                                       ? Offsets::AimingInfoAlt : Offsets::AimingInfo;
            uintptr_t aimingInfo = DaemonRead<uintptr_t>(local + aimInfoOff);
            if (aimingInfo) {
                Vector3 startPos = DaemonRead<Vector3>(aimingInfo + Offsets::AimingStartPos);
                Vector3 targetPos = Vector3::Zero();
                bool hasTarget = false;
                {
                    std::lock_guard<std::mutex> lock(g_targetMutex);
                    hasTarget = keepAiming.load(std::memory_order_relaxed) &&
                                currentBestTargetObj != 0 &&
                                currentBestTargetPos != Vector3::Zero();
                    if (hasTarget) targetPos = currentBestTargetPos;
                }

                if (hasTarget && startPos != Vector3::Zero()) {
                    Vector3 dir = targetPos - startPos;
                    float len = Vector3::Magnitude(dir);
                    if (len > 0.0001f) dir = dir / len;
                    DaemonWrite<Vector3>(aimingInfo + Offsets::AimingDirection, dir);
                }
                /* The "Legit Delay" control adds an extra pause between two
                 * aim writes so the movement does not look robotic. */
                int extraDelay = (int)pPlayer.LegitDelay;
                if (extraDelay < 0) extraDelay = 0;
                if (extraDelay > 50) extraDelay = 50;
                std::this_thread::sleep_for(std::chrono::milliseconds(2 + extraDelay));
            } else {
                std::this_thread::sleep_for(std::chrono::milliseconds(15));
            }
        } else {
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
        }
    }
}

static void AimHardThread() {
    while (true) {
        uintptr_t local = g_currentLocalPlayer.load(std::memory_order_relaxed);
        if (pPlayer.aimhard && local != 0 && GameStillAlive()) {
            uintptr_t targetObj = 0;
            Vector3 targetPos = Vector3::Zero();
            {
                std::lock_guard<std::mutex> lock(g_aimHardMutex);
                targetObj = g_aimHardTargetObj;
                targetPos = g_aimHardTargetPos;
            }
            if (targetObj != 0 && targetPos != Vector3::Zero()) {
                uintptr_t followCamera = DaemonRead<uintptr_t>(local + Offsets::FollowCamera);
                uintptr_t camera = followCamera ? DaemonRead<uintptr_t>(followCamera + Offsets::Camera_Follow) : 0;
                uintptr_t intPtrCam = camera ? DaemonRead<uintptr_t>(camera + Offsets::Camera_IntPtr) : 0;
                if (intPtrCam) {
                    D3DMatrix matrix = DaemonRead<D3DMatrix>(intPtrCam + Offsets::Camera_Matrix);
                    Vector3 camPos = GetCameraWorldPosManual(matrix);
                    if (camPos != Vector3::Zero()) {
                        Vector3 dir = Vector3::Normalized(targetPos - camPos);
                        Quaternion targetRot = Quaternion::LookRotation(dir, Vector3(0, 1, 0));
                        uintptr_t rotOff = g_isGameMax.load(std::memory_order_relaxed)
                                               ? Offsets::Max::Player_Rotation
                                               : Offsets::Normal::Player_Rotation;
                        DaemonWrite<Quaternion>(local + rotOff, targetRot);
                    }
                }
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
}

static void AimColliderThread() {
    while (true) {
        uintptr_t local = g_currentLocalPlayer.load(std::memory_order_relaxed);
        if (pPlayer.aimbotlock && local != 0 && GameStillAlive()) {
            uintptr_t firingOff = g_isGameMax.load(std::memory_order_relaxed)
                                      ? Offsets::Max::Player_IsFiring
                                      : Offsets::Normal::Player_IsFiring;
            bool isFiring = DaemonRead<bool>(local + firingOff);
            uintptr_t targetObj = 0;
            Vector3 targetPos = Vector3::Zero();
            {
                std::lock_guard<std::mutex> lock(g_targetMutex);
                targetObj = currentBestTargetObj;
                targetPos = currentBestTargetPos;
            }

            if (isFiring && targetObj != 0 && targetPos != Vector3::Zero()) {
                uintptr_t headCollider = DaemonRead<uintptr_t>(targetObj +
                    (g_isGameMax.load(std::memory_order_relaxed)
                         ? Offsets::Max::Player_HedColider
                         : Offsets::Normal::Player_HedColider));
                if (headCollider != 0) {
                    uintptr_t colWriteOff = g_isGameMax.load(std::memory_order_relaxed)
                                                ? Offsets::Max::Player_ColWrite
                                                : Offsets::Normal::Player_ColWrite;
                    DaemonWrite<uintptr_t>(targetObj + colWriteOff, headCollider);
                }
                int randomDelay = 20 + (rand() % 10);
                std::this_thread::sleep_for(std::chrono::milliseconds(randomDelay));
            } else {
                std::this_thread::sleep_for(std::chrono::milliseconds(25));
            }
        } else {
            std::this_thread::sleep_for(std::chrono::milliseconds(30));
        }
    }
}

/* ======================================================== scene rebuild == */

static void SetEnemyScale(uintptr_t enemyObj, bool scaleDown) {
    if (!enemyObj) return;
    {
        std::lock_guard<std::mutex> lock(g_scaleMutex);
        auto it = g_isScaled.find(enemyObj);
        bool currently = (it != g_isScaled.end()) ? it->second : false;
        if (scaleDown == currently) return;
    }

    uintptr_t rootTF = GetPlayerRootTF(enemyObj);
    if (!rootTF) return;
    uintptr_t t1 = DaemonRead<uintptr_t>(rootTF + Offsets::TransformObj);
    if (!t1) return;
    uintptr_t t2 = DaemonRead<uintptr_t>(t1 + Offsets::TransformObj);
    if (!t2) return;
    uintptr_t matrix = DaemonRead<uintptr_t>(t2 + Offsets::TransformMatrix);
    if (!matrix) return;

    if (scaleDown) {
        Vector3 targetScale = {-1.3f, -1.3f, -1.3f};
        DaemonWrite<Vector3>(matrix + Offsets::Transform_Scale, targetScale);
    } else {
        Vector3 normalScale = {1.0f, 1.0f, 1.0f};
        DaemonWrite<Vector3>(matrix + Offsets::Transform_Scale, normalScale);
    }
    std::lock_guard<std::mutex> lock(g_scaleMutex);
    g_isScaled[enemyObj] = scaleDown;
}

static void CreateDataList(Ninlite::Response& res) {
    res.success = 1;
    res.playerCount = 0;
    res.visibleEnemyCount = 0;
    res.visibleNearOffScreenEnemyCount = 0;
    res.proximityEnemyCount = 0;

    if (!Actived.activar) return;
    if (!GameStillAlive() && !AttachToGame()) return;
    uintptr_t libBase = g_libAddress.load(std::memory_order_relaxed);
    if (libBase == 0) return;
    bool isMax = g_isGameMax.load(std::memory_order_relaxed);

    /* ---- roots ---- */
    uintptr_t gameFacade = DaemonRead<uintptr_t>(libBase +
        (isMax ? Offsets::Max::GameFacade : Offsets::Normal::GameFacade));
    if (!gameFacade) return;

    uintptr_t staticFields = DaemonRead<uintptr_t>(gameFacade +
        (isMax ? Offsets::Max::GameFacade_P2 : Offsets::Normal::GameFacade_Current));
    if (!staticFields) staticFields = gameFacade;

    uintptr_t baseGame = DaemonRead<uintptr_t>(staticFields);
    if (!baseGame) return;

    uintptr_t mMatch = DaemonRead<uintptr_t>(baseGame + Offsets::BaseGame_Match);
    if (!mMatch) return;

    uintptr_t livePlayer = DaemonRead<uintptr_t>(mMatch + Offsets::Match_LocalPlayer);
    if (!livePlayer) return;
    g_currentLocalPlayer.store(livePlayer, std::memory_order_relaxed);

    /* ---- camera / view matrix ---- */
    uintptr_t followCamera = DaemonRead<uintptr_t>(livePlayer + Offsets::FollowCamera);
    if (!followCamera) followCamera = DaemonRead<uintptr_t>(livePlayer + Offsets::FollowCameraAlt);
    if (!followCamera) return;

    uintptr_t camera = DaemonRead<uintptr_t>(followCamera + Offsets::Camera_Follow);
    if (!camera) camera = followCamera;

    uintptr_t intPtrCam = DaemonRead<uintptr_t>(camera + Offsets::Camera_IntPtr);
    if (!intPtrCam) intPtrCam = camera;

    D3DMatrix matrix = DaemonRead<D3DMatrix>(intPtrCam + Offsets::Camera_Matrix);
    if (matrix._11 == 0.0f && matrix._22 == 0.0f) return;   /* not a valid view */

    Vector3 camPos = GetCameraWorldPosManual(matrix);
    Vector3 localHead = GetNodePosition(GetPlayerHeadTF(livePlayer));
    if (camPos == Vector3::Zero()) camPos = localHead;

    /* ---- player dictionary ---- */
    uintptr_t dictPtr = DaemonRead<uintptr_t>(baseGame + Offsets::Match_PlayerDict);
    if (!dictPtr) dictPtr = DaemonRead<uintptr_t>(mMatch + Offsets::Match_PlayerDict);
    if (!dictPtr) return;

    uintptr_t entriesArray = DaemonRead<uintptr_t>(dictPtr + Offsets::Dict_Entries);
    int dictCount = DaemonRead<int>(dictPtr + Offsets::Dict_Count);
    if (!entriesArray) return;

    /* The Unity dictionary array carries its own length; the counter can be
     * stale for a frame. Use whichever is consistent. */
    int maxLen = DaemonRead<int>(entriesArray + 0x18);
    int total = (dictCount > 0 && dictCount <= maxLen) ? dictCount : maxLen;
    if (total <= 0 || total > 512) return;

    uintptr_t valuesPtr = entriesArray + Offsets::Dict_ArrayHeader;

    const float aimDistanceVal = pPlayer.AimDistance.load(std::memory_order_relaxed);
    const float maxDistance = (aimDistanceVal > 1.0f) ? aimDistanceVal : 250.0f;
    const float offScreenMargin = 40.0f;

    /* ------------------------------------------------------------------
     * HEAD / CHEST SHOT PATTERN (reference mechanism)
     * ------------------------------------------------------------------
     * HeadRate (0..3) and ChestRate (0..6) describe how many of every N
     * shots go to the head. The pattern is built exactly like the reference
     * engine and advanced on every new burst of fire.
     * ------------------------------------------------------------------ */
    static int s_patternIndex = 0;
    static bool s_wasFiring = false;

    int headRate = (int)pPlayer.HeadRate;
    int chestRate = (int)pPlayer.ChestRate;
    if (headRate > 3) headRate = 3;
    if (chestRate > 6) chestRate = 6;
    if (headRate < 0) headRate = 0;
    if (chestRate < 0) chestRate = 0;
    if (headRate == 0 && chestRate == 0) headRate = 1;
    /* The shot pattern size is a DIFFERENT quantity than the dictionary entry
     * count above. Both were called `total`, which is a redeclaration inside
     * the same function scope (a compile error) and, worse, made the entity
     * loop below walk only `headRate + chestRate` dictionary slots instead of
     * the real player count. */
    const int patternSize = headRate + chestRate;

    bool pattern[16];
    for (int i = 0; i < 16; i++) pattern[i] = false;
    if (patternSize > 0 && headRate > 0) {
        float step = (float)patternSize / (float)headRate;
        for (int i = 0; i < headRate; ++i) {
            int pos = (int)(i * step + 0.5f);
            if (pos >= patternSize) pos = patternSize - 1;
            if (pos >= 0 && pos < 16) pattern[pos] = true;
        }
    }

    uintptr_t firingOff = g_isGameMax.load(std::memory_order_relaxed)
                              ? Offsets::Max::Player_IsFiring : Offsets::Normal::Player_IsFiring;
    bool isFiringNow = DaemonRead<bool>(livePlayer + firingOff);
    if (isFiringNow && !s_wasFiring) {
        s_patternIndex = (s_patternIndex + 1) % (patternSize > 0 ? patternSize : 1);
    }
    s_wasFiring = isFiringNow;
    const bool aimHead = pattern[s_patternIndex % (patternSize > 0 ? patternSize : 1)];

    float bestPriority = 99999.0f;
    uintptr_t bestTargetObj = 0;
    Vector3 bestTargetPos = Vector3::Zero();
    bool hasValidTarget = false;

    for (int x = 0; x < total && res.playerCount < Ninlite::kMaxPlayers; x++) {
        uintptr_t entryAddr = valuesPtr + ((uintptr_t)x * Offsets::Dict_EntryStride);
        /* An empty dictionary slot carries a negative hash code - it must be
         * skipped, otherwise a stale pointer is treated as a player. */
        int32_t hashCode = DaemonRead<int32_t>(entryAddr);
        if (hashCode < 0) continue;
        uintptr_t enemy = DaemonRead<uintptr_t>(entryAddr + Offsets::Dict_EntryValue);
        if (!IsValidPtr(enemy) || enemy == livePlayer) continue;

        /* dead / eliminated */
        uintptr_t deadOff = g_isGameMax.load(std::memory_order_relaxed)
                                ? Offsets::Max::Player_IsDead : Offsets::Normal::Player_IsDead;
        if (DaemonRead<bool>(enemy + deadOff)) continue;

        /* team mates */
        uintptr_t avatarManager = DaemonRead<uintptr_t>(enemy + Offsets::Player_Avatar);
        if (IsValidPtr(avatarManager)) {
            uintptr_t umaAvatarSimple = DaemonRead<uintptr_t>(avatarManager + Offsets::Avatar_Uma);
            if (IsValidPtr(umaAvatarSimple)) {
                uintptr_t umaData = DaemonRead<uintptr_t>(umaAvatarSimple + Offsets::Uma_Data);
                if (IsValidPtr(umaData) && DaemonRead<bool>(umaData + Offsets::isTeam)) {
                    continue;
                }
            }
        }

        /* knocked / special transformation */
        bool isKnocked = false;
        uintptr_t shadowState = DaemonRead<uintptr_t>(enemy + Offsets::ShadowState);
        if (IsValidPtr(shadowState)) {
            int xPose = DaemonRead<int>(shadowState + Offsets::XPose);
            if (xPose == 8) isKnocked = true;
        }
        int specialStatus = DaemonRead<int>(enemy + Offsets::TransformType);
        bool isSpecial = (specialStatus >= 1 && specialStatus <= 4);

        bool isClientBot = DaemonRead<bool>(enemy + Offsets::Player_IsClientBot);

        uintptr_t headTF = GetPlayerHeadTF(enemy);
        uintptr_t rootTF = GetPlayerRootTF(enemy);
        if (!headTF || !rootTF) continue;

        Vector3 headPos = GetNodePosition(headTF);
        Vector3 rootPos = GetNodePosition(rootTF);
        if (headPos == Vector3::Zero() || rootPos == Vector3::Zero()) continue;

        float distance = (camPos != Vector3::Zero())
            ? Vector3::Distance(camPos, headPos)
            : Vector3::Distance(localHead, headPos);
        if (distance < 0.5f || distance > 300.0f) continue;

        Vector3 screenHead = WorldToScreenPoint(matrix, headPos);
        Vector3 screenToe = WorldToScreenPoint(matrix, rootPos);
        if (screenHead.Z <= 0.0f || screenToe.Z <= 0.0f) continue;

        Ninlite::PlayerData* pData = &res.players[res.playerCount];
        memset(pData, 0, sizeof(*pData));
        pData->head[0] = screenHead.X;
        pData->head[1] = screenHead.Y;
        pData->head[2] = screenHead.Z;
        pData->toe[0] = screenToe.X;
        pData->toe[1] = screenToe.Y;
        pData->toe[2] = screenToe.Z;
        pData->distance = distance;
        pData->specialAbilities = specialStatus;
        pData->weaponId = (uint16_t)(short)ReadCurrentWeapon(enemy);

        uint32_t flags = 0;
        if (isKnocked) flags |= Ninlite::PLAYER_FLAG_KNOCKED;
        if (isClientBot) flags |= Ninlite::PLAYER_FLAG_BOT;
        if (isSpecial) flags |= Ninlite::PLAYER_FLAG_SPECIAL;
        pData->flags = flags;

        /* real health */
        int health = 0;
        uintptr_t hpObj = DaemonRead<uintptr_t>(enemy + Offsets::PlayerData_HP);
        if (IsValidPtr(hpObj)) {
            uintptr_t cur = DaemonRead<uintptr_t>(hpObj + 0x10);
            if (IsValidPtr(cur)) {
                uintptr_t hpArr = DaemonRead<uintptr_t>(cur + 0x20);
                if (IsValidPtr(hpArr)) {
                    short hpVal = DaemonRead<short>(hpArr + Offsets::HP_Offset);
                    if (hpVal > 0 && hpVal <= 1000) health = (int)hpVal;
                }
            }
        }
        pData->health = health;

        /* name */
        if (isClientBot) {
            strncpy(pData->playerName, "Bot", sizeof(pData->playerName) - 1);
        } else {
            uintptr_t namePtr = DaemonRead<uintptr_t>(enemy + Offsets::NamePtr);
            if (namePtr <= 0x10000000) namePtr = DaemonRead<uintptr_t>(enemy + Offsets::NamePtrOld);
            if (namePtr > 0x10000000) getUTF8(pData->playerName, sizeof(pData->playerName), namePtr);
            if (pData->playerName[0] == '\0') strncpy(pData->playerName, "Enemy", sizeof(pData->playerName) - 1);
        }

        /* counters */
        const int screenW = g_screenWidth.load(std::memory_order_relaxed);
        const int screenH = g_screenHeight.load(std::memory_order_relaxed);
        bool onScreen = (screenHead.X >= 0 && screenHead.X <= screenW &&
                         screenHead.Y >= 0 && screenHead.Y <= screenH);
        if (onScreen) {
            res.visibleEnemyCount++;
        } else if (screenHead.X >= -offScreenMargin && screenHead.X <= screenW + offScreenMargin &&
                   screenHead.Y >= -offScreenMargin && screenHead.Y <= screenH + offScreenMargin) {
            res.visibleNearOffScreenEnemyCount++;
        }
        if (distance <= 30.0f) res.proximityEnemyCount++;

        /* THE THREAT COUNTER - enemies in front of / behind the camera
         * The kernel engine (GameEngine.h) computes this with the dot
         * product of the view forward vector and the direction to the
         * enemy. The Daemon used to skip it, so the counter stayed at
         * zero in the NINLITE mode. The formula is the same one. */
        {
            Vector3 viewForward = {matrix._13, matrix._23, matrix._33};
            Vector3 camForCounter = (camPos != Vector3::Zero()) ? camPos : localHead;
            Vector3 toEnemy = headPos - camForCounter;
            float dotProduct = toEnemy.X * viewForward.X + toEnemy.Y * viewForward.Y +
                               toEnemy.Z * viewForward.Z;
            if (dotProduct >= 0.0f) {
                res.enemiesInFront++;
            } else {
                res.enemiesBehind++;
            }
        }

        /* target selection */
        bool aimActive = (pPlayer.silen4a || pPlayer.aimhard || pPlayer.aimbotlock);
        bool ignoreKnocked = pPlayer.IgnoreKnock.load(std::memory_order_relaxed) && isKnocked;
        const float aimFovVal = pPlayer.AimFov.load(std::memory_order_relaxed);
        /* THE AIM RADIUS - ONE FORMULA FOR THE WHOLE PROJECT
         * The circle that is drawn (Client.cpp DrawFOVCircle) and the circle
         * that gates the target selection here have to be the same circle.
         * The reference formula is a share of the screen WIDTH:
         *     radius = (AimFov / 180) * (screenWidth * 0.55)
         * The daemon used (screenHeight * 0.55) before, which made the
         * NINLITE engine pick a different target than the kernel engine
         * on the very same device. */
        float fovRadius = (aimFovVal > 0.0f)
            ? (aimFovVal / 180.0f) * (screenW * 0.55f)
            : (screenW * 0.55f);

        /* THE TARGET SELECTION - SAME RULES AS THE KERNEL ENGINE
         * GameEngine.h picks the target with these rules:
         *   - the target has to be ON the screen,
         *   - the FOV circle is only checked for the silent aim and for the
         *     hard aim (the collider lock uses the closest target),
         *   - the target that is closest to the crosshair wins.
         *
         * The Daemon used a different rule: it always applied the FOV
         * circle, it did not require the target to be on the screen, and it
         * compared min(worldDistance, screenDistance) - a mix of two
         * different units. That made the NINLITE mode aim at a different
         * enemy than the kernel mode on the very same device.
         */
        bool targetOnScreen = (screenHead.X >= 0.0f && screenHead.X <= screenW &&
                               screenHead.Y >= 0.0f && screenHead.Y <= screenH);
        bool fovPass = !(pPlayer.silen4a || pPlayer.aimhard) || isWithinFOV(screenHead, fovRadius);

        if (aimActive && !ignoreKnocked && targetOnScreen && fovPass && distance <= maxDistance) {
            float screenDist = sqrtf((screenHead.X - screenW * 0.5f) * (screenHead.X - screenW * 0.5f) +
                                     (screenHead.Y - screenH * 0.5f) * (screenHead.Y - screenH * 0.5f));
            /* The closest target to the crosshair wins - the world distance
             * must not take part in this comparison. */
            float priority = screenDist;
            if (priority < bestPriority) {
                bestPriority = priority;
                bestTargetObj = enemy;

                /* Head or chest, exactly like the reference engine: the chest
                 * point is interpolated 35% between the head and the hip. */
                Vector3 aimPoint = headPos;
                /* KILL IN CHEST
                 * The reference engine shoots a weak target (below 50 HP)
                 * into the chest. The Daemon never implemented this, so the
                 * feature silently did nothing in the NINLITE mode - the
                 * switch was on and the aim still went to the head.
                 * The health of the enemy is read further up in this loop. */
                bool useChest = false;
                if (pPlayer.KillInChest && health > 0 && health < 50) {
                    useChest = true;
                } else if (!aimHead) {
                    useChest = true;
                }
                if (useChest) {
                    uintptr_t hipTF = GetPlayerHipTF(enemy);
                    Vector3 hipPos = GetNodePosition(hipTF);
                    if (hipPos != Vector3::Zero()) {
                        aimPoint.X = headPos.X + (hipPos.X - headPos.X) * 0.35f;
                        aimPoint.Y = headPos.Y + (hipPos.Y - headPos.Y) * 0.35f;
                        aimPoint.Z = headPos.Z + (hipPos.Z - headPos.Z) * 0.35f;
                    }
                }
                bestTargetPos = aimPoint;
                hasValidTarget = true;
            }
        }

        res.playerCount++;
    }

    /* publish the target for the aim threads */
    {
        std::lock_guard<std::mutex> lock(g_targetMutex);
        currentBestTargetObj = bestTargetObj;
        currentBestTargetPos = bestTargetPos;
        keepAiming.store(hasValidTarget, std::memory_order_relaxed);
    }
    {
        std::lock_guard<std::mutex> lock(g_aimHardMutex);
        g_aimHardTargetObj = bestTargetObj;
        g_aimHardTargetPos = bestTargetPos;
        g_hasAimHardTarget.store(hasValidTarget, std::memory_order_relaxed);
    }
}

/* ============================================================ requests === */

static void ApplyRequest(const Ninlite::Request& req) {
    switch (req.mode) {
        case Ninlite::MODE_MASTER:
            Actived.activar = (req.enabled != 0);
            break;
        case Ninlite::MODE_SILENT:
            pPlayer.silen4a = (req.enabled != 0);
            break;
        case Ninlite::MODE_AIM_HARD:
            pPlayer.aimhard = (req.enabled != 0);
            break;
        case Ninlite::MODE_AIM_LOCK:
            pPlayer.aimbotlock = (req.enabled != 0);
            break;
        case Ninlite::MODE_IGNORE_KNOCK:
            pPlayer.IgnoreKnock = (req.enabled != 0);
            break;
        case Ninlite::MODE_AIM_FOV:
            pPlayer.AimFov = (float)req.value;
            break;
        case Ninlite::MODE_CHEST_RATE:
            pPlayer.ChestRate = (float)req.value;
            break;
        case Ninlite::MODE_HEAD_RATE:
            pPlayer.HeadRate = (float)req.value;
            break;
        case Ninlite::MODE_AIM_DISTANCE:
            pPlayer.AimDistance = (float)req.value;
            break;
        case Ninlite::MODE_LEGIT_DELAY:
            pPlayer.LegitDelay = (float)req.value;
            break;
        case Ninlite::MODE_KILL_IN_CHEST:
            pPlayer.KillInChest = (req.enabled != 0);
            break;
        default:
            break;
    }
}

/* ================================================================ main === */

static bool EnsureServerUp() {
    if (g_server.IsListening()) return true;
    if (!g_server.Create()) return false;
    if (!g_server.Bind()) return false;
    if (!g_server.Listen()) return false;
    LOGI("[DAEMON] Listening on abstract socket \\0%s", NINLITE_SOCKET_NAME);
    return true;
}

/* Brings the control socket up, retrying with a backoff.
 *
 * A bind() can fail while a PREVIOUS daemon instance is still shutting down
 * (the abstract name is still held by the old socket) or while the loader is
 * still preparing the environment. The old code gave up after the very first
 * failed attempt and exited, so the Daemon was simply never reachable. */
static bool BringServerUpWithRetry(int maxAttempts, int delayMs) {
    for (int attempt = 1; attempt <= maxAttempts; ++attempt) {
        if (EnsureServerUp()) return true;
        if (attempt < maxAttempts) {
            LOGE("[DAEMON] Control socket not up yet (attempt %d/%d), retrying in %d ms",
                 attempt, maxAttempts, delayMs);
            std::this_thread::sleep_for(std::chrono::milliseconds(delayMs));
        }
    }
    return false;
}

int main(int argc, char* argv[]) {
    (void)argc; (void)argv;

    /* A client that disconnects in the middle of a write must not kill the
     * root daemon with SIGPIPE. */
    signal(SIGPIPE, SIG_IGN);

    LOGI("=== NINLITE ENGINE (direct svc #0 syscall 270) STARTING ===");

    /* The app-side RT library also links Namegun.cpp for its weapon-name
     * renderer, so initialise the shared lookup table in both binaries. */
    Namegun::Init();

    if (!BringServerUpWithRetry(10, 500)) {
        LOGE("[DAEMON] Failed to bring up the control socket");
        return 1;
    }

    std::thread(SilentAimThread).detach();
    std::thread(AimHardThread).detach();
    std::thread(AimColliderThread).detach();

    /* Pre-resolve the game so the very first ESP request is fast. */
    AttachToGame();

    while (true) {
        if (!EnsureServerUp()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(500));
            continue;
        }

        if (!g_server.Accept()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(200));
            continue;
        }

        LOGI("[DAEMON] UI client connected");

        while (true) {
            Ninlite::Request req;
            memset(&req, 0, sizeof(req));
            size_t got = g_server.receive(&req, sizeof(req));
            if (got != sizeof(req)) {
                /* Clean disconnect or protocol violation - drop the client but
                 * keep the listener alive. */
                break;
            }

            Ninlite::Response res;
            memset(&res, 0, sizeof(res));

            if (req.mode == Ninlite::MODE_ESP) {
                g_screenWidth.store(req.screenWidth > 0 ? req.screenWidth
                                            : g_screenWidth.load(std::memory_order_relaxed),
                                    std::memory_order_relaxed);
                g_screenHeight.store(req.screenHeight > 0 ? req.screenHeight
                                             : g_screenHeight.load(std::memory_order_relaxed),
                                     std::memory_order_relaxed);
                CreateDataList(res);
                res.success = 1;
            } else if (req.mode == Ninlite::MODE_DETECT) {
                /*
                 * THE GAME PROCESS
                 * ----------------
                 * Since Android 9 an App may not read the cmdline of a
                 * process of another user - and the game runs in its own
                 * user. The Daemon runs as root, so it is the authority for
                 * the game process. The App asks this Daemon instead of
                 * scanning /proc on its own.
                 */
                pid_t pid = g_target_pid.load(std::memory_order_relaxed);
                if (pid <= 0 || !GameStillAlive()) {
                    pid = ScanTargetGamePid();
                    if (pid > 0) {
                        g_target_pid.store(pid, std::memory_order_relaxed);
                    }
                }
                bool isMax = g_isGameMax.load(std::memory_order_relaxed);
                snprintf(res.detectedPackage, sizeof(res.detectedPackage), "%s",
                         isMax ? "com.dts.freefiremax" : "com.dts.freefireth");
                res.detectedPid = (int32_t)pid;
                res.success = (pid > 0) ? 1 : 0;
            } else if (req.mode == Ninlite::MODE_INIT) {
                if (!GameStillAlive()) AttachToGame();
                Actived.activar = true;
                res.success = (g_target_pid.load(std::memory_order_relaxed) > 0 &&
                               g_libAddress.load(std::memory_order_relaxed) > 0) ? 1 : 0;
            } else if (req.mode == Ninlite::MODE_STOP) {
                /* MODE_STOP was defined in the protocol but never handled, so
                 * the Daemon could never be shut down cleanly by the App. */
                ApplyRequest(req);
                res.success = 1;
                g_server.send(&res, sizeof(res));
                LOGI("[DAEMON] MODE_STOP received - shutting down");
                g_server.CloseClient();
                g_server.Close();
                return 0;
            } else {
                ApplyRequest(req);
                res.success = 1;
            }

            if (!g_server.send(&res, sizeof(res))) {
                break;   /* client vanished mid-answer */
            }
        }

        g_server.CloseClient();
        LOGI("[DAEMON] UI client disconnected");
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }

    g_server.Close();
    return 0;
}
