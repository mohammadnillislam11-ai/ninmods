#pragma once

#include <jni.h>
#include <stdio.h>
#include <wchar.h>
#include <string>
#include <vector>
#include <map>
#include <thread>
#include <chrono>
#include <atomic>
#include <mutex>
#include <cmath>
#include <algorithm>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>
#include <time.h>
#include <signal.h>
#include <sys/socket.h>
#include <sys/un.h>

#include "Includes/Logger.h"
#include "Includes/Utils.h"
#include "Includes/EngineChannel.h"
#include "Includes/TransformTypes.h"
#include "Includes/gameoffsets.h"
#include "Includes/NinliteProtocol.h"
#include "Engine/DaemonLink.h"
#include "BooleanClient.h"
#include "src/Unity/Vector3.hpp"
#include "src/Unity/Vector2.hpp"
#include "src/Unity/Quaternion.hpp"
#include "src/Unity/Color.hpp"

/* The reference engine uses 100 as well. The wire protocol and the daemon
 * both cap the entity list at 100, so a bigger buffer only doubled the
 * per-frame memcpy without ever being used. */
#define maxplayerCount 100

struct PlayerData {
    Vector3 Head;
    Vector3 Toe;
    bool IsCaido;
    float Distancia;
    int Health;
    bool Name;
    char PlayerName[64];
    short WeaponId;
    int SpecialAbilities;
    /** Ninlite::PlayerFlags bitmask (KNOCKED/BOT/SPECIAL/VISIBLE). */
    uint32_t Flags;
};

struct Response {
    bool Success;
    int PlayerCount;
    int VisibleEnemyCount;                // On-screen enemies
    int VisibleNearOffScreenEnemyCount;   // Off-screen but near screen edges
    int ProximityEnemyCount;              // Enemies within 30m of local player
    /* The threat counter of the reference engine: the enemies in front of
     * the camera and the ones behind it. */
    int EnemiesInFront;
    int EnemiesBehind;
    PlayerData Players[maxplayerCount];
};

/* The screen size is written by the GL thread (nativeResize) and read by the
 * scene worker thread (which builds the daemon request) and by the aim
 * threads. It used to be a plain int, which is a data race. */
inline std::atomic<int> g_screenWidth{0};
inline std::atomic<int> g_screenHeight{0};
inline std::map<uintptr_t, Vector3> BasePosMap;

/* ------------------------------------------------------------------
 * GAME VARIANT AWARENESS
 * ------------------------------------------------------------------
 * Free Fire Normal and Free Fire MAX use DIFFERENT field offsets for the
 * very same logical fields (head bone, dead flag, firing flag, aim
 * rotation, ...). The engine used to probe both offsets unconditionally,
 * which produced two classes of real bugs:
 *   - reading the wrong field as "isDead" silently removed live enemies
 *     from the ESP,
 *   - writing the MAX aim rotation into the Normal build corrupted an
 *     unrelated game object field.
 * Every offset that differs between the two builds now goes through the
 * helpers below.
 * ------------------------------------------------------------------ */
inline bool IsGameMaxBuild() { return g_isGameMax.load(std::memory_order_relaxed); }

inline uintptr_t OffIsDead() {
    return IsGameMaxBuild() ? Offsets::Max::Player_IsDead : Offsets::Normal::Player_IsDead;
}
inline uintptr_t OffIsFiring() {
    return IsGameMaxBuild() ? Offsets::Max::Player_IsFiring : Offsets::Normal::Player_IsFiring;
}
inline uintptr_t OffPlayerRotation() {
    return IsGameMaxBuild() ? Offsets::Max::Player_Rotation : Offsets::Normal::Player_Rotation;
}
inline uintptr_t OffAimingInfo() {
    return IsGameMaxBuild() ? Offsets::AimingInfoAlt : Offsets::AimingInfo;
}
inline uintptr_t OffPlayerName() {
    return IsGameMaxBuild() ? Offsets::NamePtrOld : Offsets::NamePtr;
}
inline uintptr_t OffIsClientBot() {
    return IsGameMaxBuild() ? Offsets::Max::Player_IsBot : Offsets::Normal::Player_IsBot;
}

// ========== Pointer Validation (Strict ARM64 64-bit User Space Pointer) ==========
inline bool IsValidUserPointer64(uintptr_t ptr) {
    // 1. High 16 bits must be strictly 0x0000 (rejects instructions like 0xa902... or packed opcodes)
    if ((ptr >> 48) != 0) return false;
    // 2. Must be in ARM64 Linux user-space memory range (from 256MB to 128TB)
    if (ptr < 0x10000000ULL || ptr > 0x00007FFFFFFFFFFFULL) return false;
    // 3. Must be aligned to at least 4 bytes (ARM64 heap objects and pointers are aligned)
    if ((ptr & 0x3) != 0) return false;
    return true;
}

// ==================== ENGINE CORE (TRANSFORMS & MATH) ====================
inline uintptr_t GetPlayerSpineTF(uintptr_t player) {
    uintptr_t off = IsGameMaxBuild() ? Offsets::SpineFallback : Offsets::Spine;
    uintptr_t ptr = Read<uintptr_t>(player + off);
    if (!IsValidUserPointer64(ptr)) {
        ptr = Read<uintptr_t>(player + Offsets::Spine);
    }
    return IsValidUserPointer64(ptr) ? ptr : 0;
}

inline uintptr_t GetPlayerHeadTF(uintptr_t player) {
    /* The previous implementation always probed the NORMAL head offset
     * (0x6B0) first. On Free Fire MAX that address is the HIP bone, so the
     * whole ESP box was anchored on the hip instead of the head. */
    uintptr_t off = IsGameMaxBuild() ? Offsets::Max::Bone_Head : Offsets::Normal::Bone_Head;
    uintptr_t ptr = Read<uintptr_t>(player + off);
    if (!IsValidUserPointer64(ptr)) {
        ptr = Read<uintptr_t>(player + Offsets::HeadFallback);
    }
    /* 0x6B0 is the HIP bone on MAX - never fall back to it as a "head". */
    if (!IsValidUserPointer64(ptr) && !IsGameMaxBuild()) {
        ptr = Read<uintptr_t>(player + Offsets::HeadOld);
    }
    return IsValidUserPointer64(ptr) ? ptr : 0;
}

inline uintptr_t GetPlayerHipTF(uintptr_t player) {
    uintptr_t off = IsGameMaxBuild() ? Offsets::Max::Bone_Hip : Offsets::Normal::Bone_Hip;
    uintptr_t ptr = Read<uintptr_t>(player + off);
    return IsValidUserPointer64(ptr) ? ptr : 0;
}

inline uintptr_t GetPlayerPeTF(uintptr_t player) {
    /* Root / feet bone. MAX uses 0x6D0 (0x6E0 is the ankle there), Normal
     * uses 0x6E0. */
    uintptr_t off = IsGameMaxBuild() ? Offsets::Max::Bone_Feet : Offsets::Feet;
    uintptr_t ptr = Read<uintptr_t>(player + off);
    if (!IsValidUserPointer64(ptr)) {
        ptr = Read<uintptr_t>(player + Offsets::FeetFallback);
    }
    if (!IsValidUserPointer64(ptr) && !IsGameMaxBuild()) {
        ptr = Read<uintptr_t>(player + Offsets::Normal::Bone_Root);
    }
    return IsValidUserPointer64(ptr) ? ptr : 0;
}

inline uintptr_t GetPlayerMainCamera(uintptr_t player) {
    auto ptr = Read<uintptr_t>(player + Offsets::MainCameraTransform);
    if (!ptr || ptr < 0x10000000) {
        ptr = Read<uintptr_t>(player + Offsets::MainCameraTransformAlt);
    }
    if (!ptr || ptr < 0x10000000) {
        ptr = Read<uintptr_t>(player + Offsets::MainCamera);
    }
    return ptr;
}

/* The Unity TRS matrix (position / rotation / scale) is declared ONCE in
 * Includes/TransformTypes.h. It used to be declared here as well, which
 * produced a duplicate definition once the shared header was introduced. */

static Vector3 GetPosition(uintptr_t Transform) {
    auto pos = Vector3::Zero();
    if (!IsValidUserPointer64(Transform)) return pos;

    uintptr_t transformObjValue = Read<uintptr_t>(Transform + Offsets::TransformObj);
    if (!IsValidUserPointer64(transformObjValue)) {
        transformObjValue = Read<uintptr_t>(Transform + Offsets::TransformObjAlt);
    }
    if (!IsValidUserPointer64(transformObjValue)) {
        transformObjValue = Transform;
    }

    auto indexValue = Read<uintptr_t>(transformObjValue + Offsets::TransformIndex);
    auto matrixValue = Read<uintptr_t>(transformObjValue + Offsets::TransformMatrix);
    if (!IsValidUserPointer64(matrixValue)) return pos;

    uintptr_t matrixListValue = Read<uintptr_t>(matrixValue + Offsets::MatrixList);
    if (!IsValidUserPointer64(matrixListValue)) {
        matrixListValue = Read<uintptr_t>(matrixValue + Offsets::MatrixListAlt);
    }
    auto matrixIndicesValue = Read<uintptr_t>(matrixValue + Offsets::MatrixIndices);
    if (!IsValidUserPointer64(matrixListValue) || !IsValidUserPointer64(matrixIndicesValue)) return pos;

    auto resultValue = Read<Vector3>(matrixListValue + (indexValue * Offsets::MatrixDataSize));

    int maxTries = 50;
    int tries = 0;
    auto transformIndexValue = Read<int>(matrixIndicesValue + (indexValue * 4));

    while (transformIndexValue >= 0 && tries < maxTries) {
        tries++;
        uintptr_t matrixItemPtr = matrixListValue + (transformIndexValue * Offsets::MatrixDataSize);
        if (!IsValidUserPointer64(matrixItemPtr)) break;

        auto tMatrixValue = Read<Matrix>(matrixItemPtr);
        auto rotX = tMatrixValue.Rotation.X;
        auto rotY = tMatrixValue.Rotation.Y;
        auto rotZ = tMatrixValue.Rotation.Z;
        auto rotW = tMatrixValue.Rotation.W;

        auto scaleX = resultValue.X * tMatrixValue.Scale.X;
        auto scaleY = resultValue.Y * tMatrixValue.Scale.Y;
        auto scaleZ = resultValue.Z * tMatrixValue.Scale.Z;

        resultValue.X = tMatrixValue.Position.X + scaleX +
                        (scaleX * ((rotY * rotY * -2.0f) - (rotZ * rotZ * 2.0f))) +
                        (scaleY * ((rotW * rotZ * -2.0f) - (rotY * rotX * -2.0f))) +
                        (scaleZ * ((rotZ * rotX * 2.0f) - (rotW * rotY * -2.0f)));
        resultValue.Y = tMatrixValue.Position.Y + scaleY +
                        (scaleX * ((rotX * rotY * 2.0f) - (rotW * rotZ * -2.0f))) +
                        (scaleY * ((rotZ * rotZ * -2.0f) - (rotX * rotX * 2.0f))) +
                        (scaleZ * ((rotW * rotX * -2.0f) - (rotZ * rotY * -2.0f)));
        resultValue.Z = tMatrixValue.Position.Z + scaleZ +
                        (scaleX * ((rotW * rotY * -2.0f) - (rotX * rotZ * -2.0f))) +
                        (scaleY * ((rotY * rotZ * 2.0f) - (rotW * rotX * -2.0f))) +
                        (scaleZ * ((rotX * rotX * -2.0f) - (rotY * rotY * 2.0f)));

        uintptr_t indexItemPtr = matrixIndicesValue + (transformIndexValue * 4);
        if (!IsValidUserPointer64(indexItemPtr)) break;
        transformIndexValue = Read<int>(indexItemPtr);
    }

    if (tries < maxTries) {
        pos = resultValue;
    }

    return pos;
}

static Vector3 GetNodePosition(uintptr_t nodeTransform) {
    if (!IsValidUserPointer64(nodeTransform)) return Vector3::Zero();
    auto transformValue = Read<uintptr_t>(nodeTransform + Offsets::TransformObj);
    if (!IsValidUserPointer64(transformValue)) {
        transformValue = Read<uintptr_t>(nodeTransform + Offsets::TransformObjAlt);
    }
    if (!IsValidUserPointer64(transformValue)) {
        return GetPosition(nodeTransform);
    }
    return GetPosition(transformValue);
}

static Quaternion GetTransformRotation(uintptr_t Transform) {
    if (!IsValidUserPointer64(Transform)) return Quaternion::Identity();
    uintptr_t transformObjValue = Read<uintptr_t>(Transform + Offsets::TransformObj);
    if (!IsValidUserPointer64(transformObjValue)) {
        transformObjValue = Read<uintptr_t>(Transform + Offsets::TransformObjAlt);
    }
    if (!IsValidUserPointer64(transformObjValue)) transformObjValue = Transform;

    auto indexValue = Read<uintptr_t>(transformObjValue + Offsets::TransformIndex);
    auto matrixValue = Read<uintptr_t>(transformObjValue + Offsets::TransformMatrix);
    if (!IsValidUserPointer64(matrixValue)) return Quaternion::Identity();

    uintptr_t matrixListValue = Read<uintptr_t>(matrixValue + Offsets::MatrixList);
    if (!IsValidUserPointer64(matrixListValue)) {
        matrixListValue = Read<uintptr_t>(matrixValue + Offsets::MatrixListAlt);
    }
    auto matrixIndicesValue = Read<uintptr_t>(matrixValue + Offsets::MatrixIndices);
    if (!IsValidUserPointer64(matrixListValue) || !IsValidUserPointer64(matrixIndicesValue)) return Quaternion::Identity();

    auto selfMatrix = Read<Matrix>(matrixListValue + (indexValue * Offsets::MatrixDataSize));
    Quaternion rot = selfMatrix.Rotation;

    int maxTries = 30;
    int tries = 0;
    auto transformIndexValue = Read<int>(matrixIndicesValue + (indexValue * 4));
    while (transformIndexValue >= 0 && tries < maxTries) {
        tries++;
        uintptr_t parentItemPtr = matrixListValue + (transformIndexValue * Offsets::MatrixDataSize);
        if (!IsValidUserPointer64(parentItemPtr)) break;
        auto parentMatrix = Read<Matrix>(parentItemPtr);
        rot = parentMatrix.Rotation * rot;
        uintptr_t indexItemPtr = matrixIndicesValue + (transformIndexValue * 4);
        if (!IsValidUserPointer64(indexItemPtr)) break;
        transformIndexValue = Read<int>(indexItemPtr);
    }
    return rot;
}

static D3DMatrix BuildViewMatrix(Vector3 camPos, Quaternion rot, float fovDeg, float aspect) {
    if (rot.X == 0.0f && rot.Y == 0.0f && rot.Z == 0.0f && rot.W == 0.0f) {
        rot = Quaternion::Identity();
    }
    Vector3 fwd = rot * Vector3(0.0f, 0.0f, 1.0f);
    Vector3 right = rot * Vector3(1.0f, 0.0f, 0.0f);
    Vector3 up = rot * Vector3(0.0f, 1.0f, 0.0f);

    float rad = fovDeg * 0.5f * (3.14159265f / 180.0f);
    float tanHalf = tanf(rad);
    if (tanHalf < 0.01f) tanHalf = 0.57735f; // ~60 deg default
    float fovY = 1.0f / tanHalf;
    float fovX = fovY / (aspect > 0.1f ? aspect : 1.777778f);

    D3DMatrix m = {0};
    m._11 = right.X * fovX;
    m._12 = up.X * fovY;
    m._14 = fwd.X;

    m._21 = right.Y * fovX;
    m._22 = up.Y * fovY;
    m._24 = fwd.Y;

    m._31 = right.Z * fovX;
    m._32 = up.Z * fovY;
    m._34 = fwd.Z;

    m._41 = -Vector3::Dot(camPos, right) * fovX;
    m._42 = -Vector3::Dot(camPos, up) * fovY;
    m._44 = -Vector3::Dot(camPos, fwd);

    return m;
}

static auto WorldToScreenPoint(D3DMatrix viewMatrix, Vector3 ScreenPos) {
    auto result = Vector3(-1, -1, -1);

    auto v9 = (ScreenPos.X * viewMatrix._11) + (ScreenPos.Y * viewMatrix._21) + (ScreenPos.Z * viewMatrix._31) + viewMatrix._41;
    auto v10 = (ScreenPos.X * viewMatrix._12) + (ScreenPos.Y * viewMatrix._22) + (ScreenPos.Z * viewMatrix._32) + viewMatrix._42;
    auto v12 = (ScreenPos.X * viewMatrix._14) + (ScreenPos.Y * viewMatrix._24) + (ScreenPos.Z * viewMatrix._34) + viewMatrix._44;

    if (v12 >= 0.001f) {
        auto v13 = (float)g_screenWidth.load(std::memory_order_relaxed) / 2.0f;
        auto v14 = (float)g_screenHeight.load(std::memory_order_relaxed) / 2.0f;

        result.X = v13 + (v13 * v9) / v12;
        result.Y = v14 - (v14 * v10) / v12;
        result.Z = v12;
    }
    return result;
}

// ==================== ENGINE SCANNER & ENTITIES ====================
// ========== Genuine Player Object Validator ==========
inline bool IsValidPlayerObject(uintptr_t player) {
    if (!IsValidUserPointer64(player)) return false;
    // 1. Check MainCameraTransform (0x3F0 from pAdress.h / 0x3E8)
    uintptr_t cam = Read<uintptr_t>(player + Offsets::MainCameraTransform);
    if (!IsValidUserPointer64(cam)) cam = Read<uintptr_t>(player + Offsets::MainCameraTransformAlt);
    if (IsValidUserPointer64(cam)) return true;

    // 2. Check HeadBone Transform (game build aware)
    if (IsValidUserPointer64(GetPlayerHeadTF(player))) return true;

    // 3. Check Feet Transform (game build aware)
    if (IsValidUserPointer64(GetPlayerPeTF(player))) return true;

    // 4. Check Spine Transform (game build aware)
    if (IsValidUserPointer64(GetPlayerSpineTF(player))) return true;

    return false;
}

// ========== ELF Magic Header Checker ==========
inline bool is_elf_header(uintptr_t addr) {
    if (addr == 0 || target_pid.load() <= 0) return false;
    unsigned char magic[4] = {0};
    if (MemoryIO::ReadBytes(target_pid.load(), addr, magic, 4)) {
        return (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F');
    }
    magic[0] = Read<uint8_t>(addr);
    magic[1] = Read<uint8_t>(addr + 1);
    magic[2] = Read<uint8_t>(addr + 2);
    magic[3] = Read<uint8_t>(addr + 3);
    return (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F');
}

// ========== ARM64 Instruction Decoders (Auto-Extract Static Pointers from Game Code) ==========
inline uintptr_t DecodeAdrpLdr(uintptr_t pc) {
    if (pc == 0 || target_pid.load() <= 0) return 0;
    uint32_t inst1 = Read<uint32_t>(pc);
    uint32_t inst2 = Read<uint32_t>(pc + 4);

    // Check if inst1 is ADRP (bits [31] == 1, [28:24] == 0b10000 -> 0x90000000)
    if ((inst1 & 0x9F000000) != 0x90000000) return 0;

    int64_t immhi = (inst1 >> 5) & 0x7FFFF;
    int64_t immlo = (inst1 >> 29) & 0x3;
    int64_t imm = (immhi << 2) | immlo;
    if (imm & (1 << 20)) imm |= ~((1ULL << 21) - 1); // sign extend

    uintptr_t page = (pc & ~0xFFFULL) + (imm << 12);

    // Check if inst2 is 64-bit LDR (unsigned immediate: 0xF9400000)
    if ((inst2 & 0xFFC00000) == 0xF9400000) {
        uint32_t imm12 = (inst2 >> 10) & 0xFFF;
        uintptr_t offset = (uintptr_t)(imm12 << 3);
        return page + offset;
    }
    return 0;
}

// ========== Mathematical ViewMatrix Validator (Checks Real 3D Perspective Properties) ==========
static bool IsValidViewMatrix(const D3DMatrix& m) {
    if (m._11 != m._11 || m._22 != m._22 || m._33 != m._33 || m._44 != m._44) return false;
    // Perspective projection aspect and FOV scalers must be non-zero and physically reasonable
    if (fabsf(m._11) < 0.15f || fabsf(m._11) > 8.0f) return false;
    if (fabsf(m._22) < 0.15f || fabsf(m._22) > 8.0f) return false;
    return true;
}

/**
 * @brief Resolves the base address of a module through the kernel driver.
 *
 * RT channel only: the lookup is performed by the kernel driver with
 * IOCTL_RT_BASE. There is no /proc/&lt;pid&gt;/maps fallback - Rule 17 forbids
 * every driverless path inside the in-app engine. The answer of the driver is
 * verified with a driver read of the ELF header, so a wrong base is reported
 * instead of being used silently.
 *
 * @param pid        pid of the target process
 * @param moduleName name of the module, for example "libil2cpp.so"
 * @return the base address of the module, or 0 when the driver could not resolve it
 */
inline uintptr_t GetModuleBase(pid_t pid, const char* moduleName) {
    if (pid <= 0 || moduleName == nullptr) {
        return 0;
    }

    uintptr_t base = MemoryIO::ModuleBase(pid, moduleName);
    if (base == 0) {
        NIN_LOGE("RT", "GetModuleBase: driver could not resolve %s (last errno: %d)",
                 moduleName, MemoryIO::GetLastErrno());
        return 0;
    }

    if (is_elf_header(base)) {
        NIN_LOGI("RT", "GetModuleBase: %s base = 0x%lx (Ring-0 IOCTL 0x803, ELF verified)",
                 moduleName, (unsigned long)base);
    } else {
        NIN_LOGE("RT", "GetModuleBase: %s base = 0x%lx carries no ELF header (driver reported it)",
                 moduleName, (unsigned long)base);
    }
    return base;
}

// ========== Original Code ==========

inline Quaternion GetRotationToTheLocation(Vector3 Target, float Height, Vector3 MyEnemy) {
    return Quaternion::LookRotation((Target + Vector3(0, Height, 0)) - MyEnemy, Vector3(0, 1, 0));
}

inline bool isLocked = false;
inline uint64_t lockedEnemyAddress = 0;
inline Vector3 lockedTargetPos;
inline std::mutex g_aimStateMutex;

// Helper to restore all enemies to their original positions
inline void RestoreAllPositions() {
    std::vector<std::pair<uintptr_t, Vector3>> snapshot;
    {
        std::lock_guard<std::mutex> lock(g_aimStateMutex);
        if (BasePosMap.empty()) return;
        snapshot.reserve(BasePosMap.size());
        for (const auto& [enemy, originalPos] : BasePosMap) {
            snapshot.emplace_back(enemy, originalPos);
        }
    }

    for (const auto& [enemy, originalPos] : snapshot) {
        auto feetTF = GetPlayerPeTF(enemy);
        if (feetTF) {
            auto trVal = Read<uintptr_t>(feetTF + Offsets::TransformObj);
            if (trVal) {
                auto p3 = Read<uintptr_t>(trVal + Offsets::TransformObj);
                auto transformPtr = Read<uintptr_t>(p3 + Offsets::TransformMatrix);
                if (transformPtr) {
                    Write<Vector3>(transformPtr + Offsets::EnemyPosForce, originalPos);
                }
            }
        }
    }

    std::lock_guard<std::mutex> lock(g_aimStateMutex);
    BasePosMap.clear();
}

inline uintptr_t currentBestTargetObj = 0;              // guarded by g_aimStateMutex
inline std::atomic<bool> keepAiming{false};
inline std::thread aimThread;
inline Vector3 currentBestTargetPos = Vector3::Zero();  // guarded by g_aimStateMutex

/* Shared between the render thread (producer) and the aim / magnet worker
 * threads (consumers). These were plain globals before, which is a data race:
 * the worker could read a half written pointer and act on a stale or freed
 * game object. */
inline std::atomic<uintptr_t> ShareLocalPlayer{0};
inline std::atomic<uintptr_t> ShareEnemy{0};
inline D3DMatrix ViewMatrix;                             // render thread only
inline Vector3 MainCameraTransformPos = Vector3::Zero(); // render thread only

/* Snapshot of the camera basis/position for the worker threads (lock free). */
inline std::atomic<float> g_camFwdX{0.0f};
inline std::atomic<float> g_camFwdY{0.0f};
inline std::atomic<float> g_camFwdZ{0.0f};
inline std::atomic<float> g_camPosX{0.0f};
inline std::atomic<float> g_camPosY{0.0f};
inline std::atomic<float> g_camPosZ{0.0f};

inline bool speedHackEnabled = false;

static float FloatFromBits(uint32_t bits) {
    float value = 0.0f;
    std::memcpy(&value, &bits, sizeof(value));
    return value;
}

inline void getUTF8(char* dst, uintptr_t addr) {
    if (addr < 0x10000000) {
        dst[0] = '\0';
        return;
    }

    int stringLen = Read<int>(addr + Offsets::StringLen);
    if (stringLen <= 0 || stringLen > 64) {
        stringLen = 32; 
    }

    unsigned char raw[128];
    memset(raw, 0, sizeof(raw));
    size_t toRead = (size_t)(stringLen * 2);
    if (toRead > sizeof(raw)) toRead = sizeof(raw);
    MemoryIO::ReadBytes(target_pid.load(), addr + Offsets::StringChars, raw, toRead);

    int j = 0;
    for (int i = 0; i < (stringLen * 2) && (i + 1) < (int)toRead; i += 2) {
        unsigned short unicode = raw[i] | (raw[i + 1] << 8);
        if (unicode == 0) break;
        if (unicode < 0x80) {
            dst[j++] = (char)unicode;
        } else if (unicode < 0x800) {
            dst[j++] = (char)((unicode >> 6) | 0xC0);
            dst[j++] = (char)((unicode & 0x3F) | 0x80);
        } else if (unicode >= 0xD800 && unicode <= 0xDBFF) { 
            i += 2;
            if ((i + 1) >= (int)toRead) break;
            unsigned short low = raw[i] | (raw[i + 1] << 8);
            unsigned int codepoint = 0x10000 + ((unicode - 0xD800) << 10) + (low - 0xDC00);
            dst[j++] = (char)((codepoint >> 18) | 0xF0);
            dst[j++] = (char)(((codepoint >> 12) & 0x3F) | 0x80);
            dst[j++] = (char)(((codepoint >> 6) & 0x3F) | 0x80);
            dst[j++] = (char)((codepoint & 0x3F) | 0x80);
        } else {
            dst[j++] = (char)((unicode >> 12) | 0xE0);
            dst[j++] = (char)(((unicode >> 6) & 0x3F) | 0x80);
            dst[j++] = (char)((unicode & 0x3F) | 0x80);
        }
        if (j >= 60) break; 
    }
    dst[j] = '\0';
}

inline bool isWithinFOV(Vector3 screenPos, float fovRadius) {
    float screenCenterX = g_screenWidth.load(std::memory_order_relaxed) / 2.0f;
    float screenCenterY = g_screenHeight.load(std::memory_order_relaxed) / 2.0f;
    float deltaX = screenPos.X - screenCenterX;
    float deltaY = screenPos.Y - screenCenterY;
    float distanceFromCenter = sqrt(deltaX * deltaX + deltaY * deltaY);
    return distanceFromCenter <= fovRadius;
}

inline float FastRandomFloat() {
    static uint32_t x = 123456789;
    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    float normalized = x / 4294967295.0f;
    return (normalized - 0.5f) * 2.0f * 0.033;
}

inline uint64_t ReadWeaponShortId(uintptr_t weapon) {
    if (!IsValidUserPointer64(weapon)) return 0;
    uintptr_t itemData = Read<uintptr_t>(weapon + Offsets::ItemData);
    if (!IsValidUserPointer64(itemData)) return 0;
    return Read<uint64_t>(itemData + Offsets::WeaponId);
}

inline uint64_t ReadCurrentWeapon(uintptr_t player) {
    if (!IsValidUserPointer64(player)) return 0;
    uintptr_t inventoryManager = Read<uintptr_t>(player + Offsets::InventoryManager);
    if (!IsValidUserPointer64(inventoryManager)) inventoryManager = Read<uintptr_t>(player + Offsets::InventoryManagerOld);
    if (!IsValidUserPointer64(inventoryManager)) return 0;

    uintptr_t weapon = 0;
    bool isCombined = Read<bool>(inventoryManager + Offsets::IsCombined);
    if (isCombined) {
        weapon = Read<uintptr_t>(inventoryManager + Offsets::CombinedWeapon);
    } else {
        weapon = Read<uintptr_t>(inventoryManager + Offsets::InventoryItem);
    }
    if (!IsValidUserPointer64(weapon)) return 0;
    return ReadWeaponShortId(weapon);
}

inline void ContinuousAimWorker() {
    while (true) {
        if (NinChannel::IsQxActive()) {
            /* In QX channel mode, the root daemon executes all memory operations.
             * The RT aim worker must yield and never write memory. */
            usleep(50000);
            continue;
        }
        bool anyAimActive = pPlayer.silen4a || pPlayer.aimbot || pPlayer.aimbotlock;
        if (!anyAimActive) {
            usleep(20000);
            continue;
        }

        uintptr_t sharedSelf = ShareLocalPlayer.load(std::memory_order_relaxed);
        uintptr_t currentSelf = IsValidPlayerObject(sharedSelf) ? sharedSelf : 0;
        if (!IsValidUserPointer64(currentSelf)) {
            usleep(10000);
            continue;
        }

        Vector3 targetPos = Vector3::Zero();
        uintptr_t targetObj = 0;
        {
            std::lock_guard<std::mutex> lock(g_aimStateMutex);
            targetPos = currentBestTargetPos;
            targetObj = currentBestTargetObj;
        }

        if (targetPos == Vector3::Zero() || !IsValidUserPointer64(targetObj)) {
            usleep(5000);
            continue;
        }

        /* The firing flag is a single byte in both builds (the reference
         * implementation reads it as a bool). Reading it as an int pulled in
         * three neighbouring bytes and produced false "is firing" states. */
        const uintptr_t fireOff = OffIsFiring();
        int mIsFiring = Read<bool>(currentSelf + fireOff) ? 1 : 0;

        bool shouldLock = pPlayer.aimbotlock || (mIsFiring > 0);

        // 1. Camera LookRotation Aimbot & ViewRotation
        if ((pPlayer.aimbot || pPlayer.aimbotlock) && shouldLock) {
            Vector3 camPos(g_camPosX.load(std::memory_order_relaxed),
                           g_camPosY.load(std::memory_order_relaxed),
                           g_camPosZ.load(std::memory_order_relaxed));
            if (camPos == Vector3::Zero()) {
                uintptr_t myCam = GetPlayerMainCamera(currentSelf);
                if (IsValidUserPointer64(myCam)) camPos = GetPosition(myCam);
            }
            if (camPos == Vector3::Zero()) camPos = GetNodePosition(currentSelf) + Vector3(0, 1.85f, 0);

            float aimHeight = 0.0f;
            if (!pPlayer.silentAimHead && (rand() % 100) >= pPlayer.headshotRate) {
                aimHeight = -0.35f; // Chest / Neck level
            }

            Quaternion targetRot = GetRotationToTheLocation(targetPos, aimHeight, camPos);

            /* Write ONLY the rotation field that belongs to the running build.
             * The previous code wrote 0x61C AND 0x214; on Free Fire Normal
             * 0x214 is an unrelated object field, so every aim tick silently
             * corrupted the local player object. */
            Write<Quaternion>(currentSelf + OffPlayerRotation(), targetRot);

            int rotCount = Read<int>(currentSelf + Offsets::CallSetAimRotationCount);
            if (rotCount >= 0) {
                Write<int>(currentSelf + Offsets::CallSetAimRotationCount, rotCount + 1);
            }
        }

        // 2. Silent Bullet Ray Redirection
        if (pPlayer.silen4a && shouldLock) {
            uintptr_t aimInfoOff = OffAimingInfo();
            uint64_t aimingInfo = Read<uint64_t>(currentSelf + aimInfoOff);
            if (!IsValidUserPointer64(aimingInfo)) aimingInfo = Read<uint64_t>(currentSelf + Offsets::AimingInfoOld);
            if (IsValidUserPointer64(aimingInfo)) {
                Vector3 rayStart = Read<Vector3>(aimingInfo + Offsets::AimingStartPos);
                if (rayStart == Vector3::Zero()) rayStart = Read<Vector3>(aimingInfo + Offsets::AimingStartPosAlt);
                if (rayStart != Vector3::Zero()) {
                    Vector3 diff = targetPos - rayStart;
                    float dist = Vector3::Magnitude(diff);
                    if (dist >= 0.5f && dist <= 500.0f) {
                        Vector3 dir = diff / dist;
                        /* 0x40 is the ray direction slot in both builds.
                         * The second write to 0xD0 and the raw 0x71 poke were
                         * magic constants with no reference implementation -
                         * they corrupted unrelated aiming state. */
                        Write<Vector3>(aimingInfo + Offsets::AimingDirection, dir);
                    }
                }
            }
        }

        // 3. Head Collider & Native AimAssist Lock
        if ((pPlayer.aimbotlock || pPlayer.silen4a || pPlayer.aimbot) && shouldLock && IsValidUserPointer64(targetObj)) {
            uintptr_t headCollider = Read<uintptr_t>(targetObj + Offsets::Player_HeadCollider);
            if (IsValidUserPointer64(headCollider)) {
                Write<uintptr_t>(targetObj + Offsets::Player_AimTargetWrite, headCollider);
            }
            /* The aim-target / assist-state offsets (0x670 / 0x668) are only
             * documented for the Normal build. Writing them blindly on MAX
             * corrupted an unrelated field, so the MAX build keeps the head
             * collider redirect only - exactly like the reference engine. */
            if (!IsGameMaxBuild()) {
                Write<uintptr_t>(currentSelf + Offsets::AimTarget, targetObj);
                Write<int>(currentSelf + Offsets::EAimAssistState, 1);
            }
        }

        /* 250Hz smooth iteration rate + the optional "Legit Delay" pause. */
        int extraDelay = (int)pPlayer.legitDelayMs;
        if (extraDelay < 0) extraDelay = 0;
        if (extraDelay > 50) extraDelay = 50;
        usleep((useconds_t)(4000 + extraDelay * 1000));
    }
}

inline void EnsureContinuousAimThreadStarted() {
    static std::atomic<bool> s_started{false};
    if (!s_started.exchange(true)) {
        std::thread([]() { ContinuousAimWorker(); }).detach();
    }
}

inline void UpdateAimMagnetFast() {
    while (true) {
        if (NinChannel::IsQxActive() || (!pPlayer.aimMagnet && !pPlayer.PlayerToMe)) {
            usleep(100000);  // 100ms idle sleep when QX mode or inactive
            continue;
        }
        usleep(10000);  // 10ms active sleep
        
        uintptr_t localP = ShareLocalPlayer.load(std::memory_order_relaxed);
        uintptr_t enemy = ShareEnemy.load(std::memory_order_relaxed);

        if (!localP || !enemy) continue;

        /* Snapshot of the camera state published by the render thread. */
        const float fwdX = g_camFwdX.load(std::memory_order_relaxed);
        const float fwdY = g_camFwdY.load(std::memory_order_relaxed);
        const float fwdZ = g_camFwdZ.load(std::memory_order_relaxed);
        const Vector3 camPos(g_camPosX.load(std::memory_order_relaxed),
                             g_camPosY.load(std::memory_order_relaxed),
                             g_camPosZ.load(std::memory_order_relaxed));

        if (pPlayer.aimMagnet) {
            int mIsFiring = Read<int>(localP + OffIsFiring());
            if (mIsFiring <= 0) mIsFiring = Read<int>(localP + Offsets::IsFiringOld);
            if (mIsFiring <= 0 && !pPlayer.PlayerToMe) continue;
        }

        auto headTF = GetPlayerHeadTF(enemy);
        auto feetTF = GetPlayerPeTF(enemy);

        if (!headTF || !feetTF) continue;

        Vector3 currentHeadPos = GetNodePosition(headTF);
        Vector3 currentRootPos = GetNodePosition(feetTF);

        if (currentHeadPos == Vector3::Zero() || currentRootPos == Vector3::Zero()) continue;

        Vector3 newRootPos;
        if (pPlayer.PlayerToMe) {
            Vector3 localPos = GetNodePosition(localP);
            Vector3 vForward;
            vForward.X = fwdX;
            vForward.Y = 0.0f;
            vForward.Z = fwdZ;
            float fLen = sqrtf(vForward.X * vForward.X + vForward.Z * vForward.Z);
            if (fLen > 0.001f) {
                vForward.X /= fLen;
                vForward.Z /= fLen;
            } else {
                vForward.Z = 1.0f;
            }
            newRootPos = localPos + (vForward * 1.8f);
            newRootPos.Y = localPos.Y;
        } else {
            Vector3 vForward;  // 'vForward' avoids std::forward conflict
            vForward.X = fwdX;
            vForward.Y = fwdY;
            vForward.Z = fwdZ;

            float len = sqrtf(vForward.X * vForward.X + vForward.Y * vForward.Y + vForward.Z * vForward.Z);
            if (len < 0.001f) continue;

            vForward.X /= len;
            vForward.Y /= len;
            vForward.Z /= len;

            float depth = Vector3::Distance(camPos, currentHeadPos);
            if (depth < 0.1f) continue;

            Vector3 targetHeadPos;
            targetHeadPos.X = camPos.X + vForward.X * depth;
            targetHeadPos.Y = camPos.Y + vForward.Y * depth;
            targetHeadPos.Z = camPos.Z + vForward.Z * depth;
            
            Vector3 offset;
            offset.X = targetHeadPos.X - currentHeadPos.X;
            offset.Y = targetHeadPos.Y - currentHeadPos.Y;
            offset.Z = targetHeadPos.Z - currentHeadPos.Z;
            
            newRootPos.X = currentRootPos.X + offset.X;
            newRootPos.Y = currentRootPos.Y + offset.Y;
            newRootPos.Z = currentRootPos.Z + offset.Z;
        }
        
        auto trVal = Read<uintptr_t>(feetTF + Offsets::TransformObj);
        if (trVal) {
            auto p3 = Read<uintptr_t>(trVal + Offsets::TransformObj);
            auto transformPtr = Read<uintptr_t>(p3 + Offsets::TransformMatrix);
            if (transformPtr) {
                {
                    std::lock_guard<std::mutex> lock(g_aimStateMutex);
                    if (BasePosMap.find(enemy) == BasePosMap.end()) {
                        BasePosMap[enemy] = currentRootPos;
                    }
                }
                Write<Vector3>(transformPtr + Offsets::EnemyPosForce, newRootPos);
            }
        }
    }
}

inline void EnsureAimMagnetThreadStarted() {
    static std::atomic<bool> s_started{false};
    if (!s_started.exchange(true)) {
        std::thread([]() { UpdateAimMagnetFast(); }).detach();
    }
}

inline void CreateDataList(Response& SendResponse) {
    SendResponse.Success = true;
    SendResponse.PlayerCount = 0;
    SendResponse.VisibleEnemyCount = 0;
    SendResponse.VisibleNearOffScreenEnemyCount = 0;
    SendResponse.ProximityEnemyCount = 0;
    SendResponse.EnemiesInFront = 0;
    SendResponse.EnemiesBehind = 0;

    if (!Actived.activar) return;

    static pid_t s_last_verified_pid = -1;
    pid_t currentPid = target_pid.load(std::memory_order_relaxed);
    if (currentPid <= 0 || kill(currentPid, 0) != 0) {
        /*
         * THROTTLED GAME LOOKUP
         * ----------------------
         * FindGamePid() walks the whole /proc tree and opens plus reads the
         * cmdline of every single process on the device. That is several
         * hundred file reads.
         *
         * This function runs from the scene worker, which ticks at 60 Hz, and
         * the lookup was repeated on EVERY tick while the game was not
         * running (or had just been closed). The result was a continuous
         * storm of /proc reads - roughly 50 to 60 full process scans per
         * second - which starved the device and made the whole screen appear
         * to freeze. The overlay was open over the launcher in exactly that
         * situation: the game is in the background, so the scan never
         * succeeded and never stopped.
         *
         * The lookup is now done at most once every 1.5 seconds.
         */
        static std::atomic<int64_t> s_next_pid_scan_ms{0};
        const int64_t nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                                  std::chrono::steady_clock::now().time_since_epoch())
                                  .count();
        if (nowMs < s_next_pid_scan_ms.load(std::memory_order_relaxed)) {
            /* Too soon for another scan - keep the previous state and let the
             * next tick try again. */
            return;
        }
        s_next_pid_scan_ms.store(nowMs + 1500, std::memory_order_relaxed);

        currentPid = FindGamePid();
        target_pid.store(currentPid, std::memory_order_relaxed);
        if (currentPid <= 0) {
            libAddress.store(0, std::memory_order_relaxed);
            return;
        }
    }

    if (currentPid != s_last_verified_pid) {
        libAddress.store(0, std::memory_order_relaxed);
        s_last_verified_pid = currentPid;
    }

    if (libAddress.load(std::memory_order_relaxed) == 0) {
        uintptr_t base = GetModuleBase(currentPid, "libil2cpp.so");
        libAddress.store(base, std::memory_order_relaxed);
        if (base == 0) return;
    }

    static time_t lastLogTime = 0;
    time_t now = time(nullptr);
    bool shouldLog = (now - lastLogTime >= 3);

    // 1. Scan candidate roots for genuine LocalPlayer and Match (Normal & MAX)
    uintptr_t GameFacade_c = 0;
    uintptr_t m_Match = 0;
    uintptr_t BaseGame = 0;
    uintptr_t localPlayer = 0;

    const uintptr_t facadeOffsets[] = {
        Offsets::Normal::GameFacade, // 0xA872380 (Normal Primary from 5_6213160554375160447.h)
        Offsets::Max::GameFacade,    // 0xAD68E18 (MAX Primary from ff max 5_6246536088102380429.h)
        Offsets::GameFacade,         // 0xA872380
        Offsets::GameFacadeMax,      // 0xAD68E18
        Offsets::GameFacadeAlt1      // 0xAD68E18
    };

    if (!localPlayer) {
        for (uintptr_t fOff : facadeOffsets) {
            uintptr_t classPtr = Read<uintptr_t>(libAddress.load(std::memory_order_relaxed) + fOff);
        if (!IsValidUserPointer64(classPtr)) continue;

        // Static fields candidates: 0xB8 (GameFacade_P2 in MAX), 0x8 (GameFacade_Current in Normal), 0x0 (Direct)
        const uintptr_t staticOffsets[] = { Offsets::Max::GameFacade_P2, Offsets::Normal::GameFacade_Current, 0xB8, 0x8, 0x0 };
        for (uintptr_t sOff : staticOffsets) {
            uintptr_t staticFields = (sOff == 0) ? classPtr : Read<uintptr_t>(classPtr + sOff);
            if (!IsValidUserPointer64(staticFields)) continue;

            // BaseGame -> Match -> LocalPlayer traversal
            const uintptr_t baseOffsets[] = { 0x0, 0x8 };
            for (uintptr_t bOff : baseOffsets) {
                uintptr_t bg = Read<uintptr_t>(staticFields + bOff);
                if (!IsValidUserPointer64(bg)) continue;

                const uintptr_t matchOffsets[] = { Offsets::BaseGame_Match, 0x90, 0x8 };
                for (uintptr_t mOff : matchOffsets) {
                    uintptr_t match = Read<uintptr_t>(bg + mOff);
                    if (!IsValidUserPointer64(match)) continue;

                    const uintptr_t lpOffsets[] = { Offsets::Match_LocalPlayer, 0xD8, 0x94 };
                    for (uintptr_t lpOff : lpOffsets) {
                        uintptr_t lp = Read<uintptr_t>(match + lpOff);
                        if (IsValidPlayerObject(lp)) {
                            localPlayer = lp;
                            m_Match = match;
                            BaseGame = bg;
                            GameFacade_c = classPtr;
                            break;
                        }
                    }
                    if (localPlayer) break;
                }
                if (localPlayer) break;
            }
            if (localPlayer) break;

            // Direct LocalPlayer offset candidates
            const uintptr_t directLpOffsets[] = { Offsets::Match_LocalPlayer, 0xD8, 0x94 };
            for (uintptr_t dOff : directLpOffsets) {
                uintptr_t lp = Read<uintptr_t>(staticFields + dOff);
                if (IsValidPlayerObject(lp)) {
                    localPlayer = lp;
                    GameFacade_c = classPtr;
                    break;
                }
            }
            if (localPlayer) break;
        }
        if (localPlayer) break;
    }
    }

    if (!IsValidPlayerObject(localPlayer)) {
        if (shouldLog) {
            lastLogTime = now;
            NIN_LOGI("DAEMON_ESP", "Scanning game memory in libil2cpp (0x%lx)... Waiting for local player spawn.", (unsigned long)libAddress.load(std::memory_order_relaxed));
        }
        return;
    }

    // TimeService Speedhack ONLY if explicitly enabled
    static bool speedWasActive = false;
    if ((speedHackEnabled || pPlayer.SpeedHackV2) && IsValidUserPointer64(BaseGame)) {
        uintptr_t timeService = Read<uintptr_t>(BaseGame + Offsets::BaseGameTimeService);
        if (IsValidUserPointer64(timeService)) {
            float speedSetting = pPlayer.gameSpeed.load(std::memory_order_relaxed);
            float mult = (speedSetting > 0.1f) ? speedSetting : 1.5f;
            float speedVal = 0.03299999982f * mult;
            Write<float>(timeService + Offsets::TimeServiceSpeed, speedVal);
            speedWasActive = true;
        }
    } else if (speedWasActive && IsValidUserPointer64(BaseGame)) {
        uintptr_t timeService = Read<uintptr_t>(BaseGame + Offsets::BaseGameTimeService);
        if (IsValidUserPointer64(timeService)) {
            Write<float>(timeService + Offsets::TimeServiceSpeed, 0.03299999982f);
        }
        speedWasActive = false;
    }

    // PlayerAttributes / NoReload (Triggered strictly when pPlayer.NoRecoil is enabled)
    if (IsValidUserPointer64(localPlayer)) {
        uintptr_t attrs = Read<uintptr_t>(localPlayer + Offsets::PlayerAttributes);
        if (IsValidUserPointer64(attrs)) {
            static uintptr_t lastAttrs = 0;
            static bool origSaved = false;
            static bool origValue = false;
            if (attrs != lastAttrs) { lastAttrs = attrs; origSaved = false; origValue = false; }
            if (pPlayer.NoRecoil) {
                if (!origSaved) { origValue = Read<bool>(attrs + Offsets::ShootNoReload); origSaved = true; }
                Write<bool>(attrs + Offsets::ShootNoReload, true);
            } else if (origSaved) {
                Write<bool>(attrs + Offsets::ShootNoReload, origValue);
                origSaved = false;
            }
        }
    }

    // 4. Resolve Camera & ViewMatrix (FollowCamera & MainCamera)
    D3DMatrix matrix = {0};
    uintptr_t Camera = 0;
    uintptr_t IntPtrCam = 0;

    // A. FollowCamera chain on localPlayer
    const uintptr_t followCamOffsets[] = {
        Offsets::FollowCamera,         // 0x698
        Offsets::FollowCameraAlt,      // 0x638
        Offsets::FollowCameraFallback, // 0x640
        Offsets::FollowCameraOld       // 0x6A8
    };

    for (uintptr_t fcOff : followCamOffsets) {
        uintptr_t fc = Read<uintptr_t>(localPlayer + fcOff);
        if (!IsValidUserPointer64(fc)) continue;

        uintptr_t cam = Read<uintptr_t>(fc + Offsets::FollowCameraObj); // 0x30
        if (!IsValidUserPointer64(cam)) cam = fc;

        uintptr_t ipc = Read<uintptr_t>(cam + Offsets::CameraIntPtr); // 0x10
        if (!IsValidUserPointer64(ipc)) ipc = cam;

        const uintptr_t matrixOffsets[] = { 0x100, 0xBC, 0xDC, 0xC0, 0x90, 0x80, 0x10, 0xD0, 0xE0, 0x110, 0x120 };
        for (uintptr_t mOff : matrixOffsets) {
            auto candMat = Read<D3DMatrix>(ipc + mOff);
            if (IsValidViewMatrix(candMat)) {
                matrix = candMat;
                Camera = cam;
                IntPtrCam = ipc;
                break;
            }
        }
        if (IsValidViewMatrix(matrix)) break;
    }

    // B. Static GameFacade Direct ViewMatrix (staticClass + 0xBC / 0x100)
    if (!IsValidViewMatrix(matrix) && IsValidUserPointer64(GameFacade_c)) {
        const uintptr_t staticOffsets[] = { Offsets::Max::GameFacade_P2, Offsets::Normal::GameFacade_Current, 0x0 };
        for (uintptr_t sOff : staticOffsets) {
            uintptr_t staticFields = (sOff == 0) ? GameFacade_c : Read<uintptr_t>(GameFacade_c + sOff);
            if (!IsValidUserPointer64(staticFields)) continue;

            const uintptr_t matOffsets[] = { 0xBC, 0x100, 0xDC, 0xC0 };
            for (uintptr_t mOff : matOffsets) {
                auto candMat = Read<D3DMatrix>(staticFields + mOff);
                if (IsValidViewMatrix(candMat)) {
                    matrix = candMat;
                    break;
                }
            }
            if (IsValidViewMatrix(matrix)) break;
        }
    }

    // C. Direct FollowCamera / MainCamera fallback from LocalPlayer
    if (!IsValidViewMatrix(matrix) && IsValidUserPointer64(localPlayer)) {
        uintptr_t cam = Read<uintptr_t>(localPlayer + Offsets::FollowCamera);
        if (!IsValidUserPointer64(cam)) cam = Read<uintptr_t>(localPlayer + Offsets::MainCameraTransform);
        if (IsValidUserPointer64(cam)) {
            uintptr_t ipc = Read<uintptr_t>(cam + Offsets::CameraIntPtr);
            if (!IsValidUserPointer64(ipc)) ipc = cam;
            const uintptr_t matrixOffsets[] = { 0x100, 0xBC, 0xDC, 0xC0 };
            for (uintptr_t mOff : matrixOffsets) {
                auto candMat = Read<D3DMatrix>(ipc + mOff);
                if (IsValidViewMatrix(candMat)) {
                    matrix = candMat;
                    Camera = cam;
                    IntPtrCam = ipc;
                    break;
                }
            }
        }
    }

    auto myCam = Read<uintptr_t>(localPlayer + Offsets::MainCameraTransform);
    if (!IsValidUserPointer64(myCam)) myCam = Read<uintptr_t>(localPlayer + Offsets::MainCameraTransformAlt);
    if (!IsValidUserPointer64(myCam)) myCam = Read<uintptr_t>(localPlayer + Offsets::MainCamera);
    Vector3 myCamPos = GetNodePosition(myCam);
    if (myCamPos.X == 0 && myCamPos.Y == 0 && myCamPos.Z == 0) {
        myCamPos = GetNodePosition(localPlayer) + Vector3(0, 1.9f, 0);
    }
    if (pPlayer.undercam) {
        myCamPos.Y -= 3.5f;
    }
    if (!IsValidViewMatrix(matrix)) {
        Quaternion myCamRot = GetTransformRotation(myCam);
        float aspect = (g_screenHeight.load(std::memory_order_relaxed) > 0)
                       ? ((float)g_screenWidth.load(std::memory_order_relaxed) /
                          (float)g_screenHeight.load(std::memory_order_relaxed))
                       : 1.777778f;
        matrix = BuildViewMatrix(myCamPos, myCamRot, 60.0f, aspect);
    } else if (pPlayer.undercam) {
        matrix._42 += 3.5f;
    }
    ViewMatrix = matrix;
    MainCameraTransformPos = myCamPos;
    /* Publish the camera basis for the worker threads without a lock. */
    g_camFwdX.store(matrix._13, std::memory_order_relaxed);
    g_camFwdY.store(matrix._23, std::memory_order_relaxed);
    g_camFwdZ.store(matrix._33, std::memory_order_relaxed);
    g_camPosX.store(myCamPos.X, std::memory_order_relaxed);
    g_camPosY.store(myCamPos.Y, std::memory_order_relaxed);
    g_camPosZ.store(myCamPos.Z, std::memory_order_relaxed);

    // 5. Dictionary Resolution (from 5_6213160554375160447.h and ff max 5_6246536088102380429.h: 0xC0)
    uintptr_t dictionary = 0;
    int numValues = 0;
    uintptr_t valuesPtr = 0;
    bool isMonoDict = false;

    const uintptr_t dictOffsets[] = {
        Offsets::Match_PlayerDict,       // 0xC0 (Unified in both Normal & MAX)
        Offsets::BaseGameDictionary,     // 0xC0
        Offsets::BaseGameDictionaryAlt,  // 0x148
        0x68,
        0x60,
        0x70
    };

    std::vector<uintptr_t> roots;
    if (IsValidUserPointer64(m_Match)) roots.push_back(m_Match);
    if (IsValidUserPointer64(BaseGame)) roots.push_back(BaseGame);

    for (uintptr_t root : roots) {
        for (uintptr_t off : dictOffsets) {
            uintptr_t candDict = Read<uintptr_t>(root + off);
            if (!IsValidUserPointer64(candDict)) continue;

            // candDict + 0x18 = UnityArray<DictionaryEntry>* entries (Dict_Entries)
            // candDict + 0x20 = int count (Dict_Count)
            uintptr_t entriesArray = Read<uintptr_t>(candDict + Offsets::DictionaryEntries);
            int dictCount = Read<int>(candDict + Offsets::DictionaryCount);

            if (IsValidUserPointer64(entriesArray)) {
                int maxLen = Read<int>(entriesArray + 0x18);
                if (maxLen > 0 && maxLen <= 512) {
                    int total = (dictCount > 0 && dictCount <= maxLen) ? dictCount : maxLen;
                    if (total > 0 && total <= 120) {
                        dictionary = candDict;
                        numValues = total;
                        valuesPtr = entriesArray + 0x20;
                        isMonoDict = true;
                        break;
                    }
                }
            }

            // Direct Dictionary format (Dict_Count at 0x18 / Dict_EntryValue at 0x10)
            if (numValues == 0) {
                int count = Read<int>(candDict + 0x18);
                if (count > 0 && count <= 120) {
                    uintptr_t entries = Read<uintptr_t>(candDict + 0x10);
                    if (IsValidUserPointer64(entries)) {
                        dictionary = candDict;
                        numValues = count;
                        valuesPtr = entries;
                        isMonoDict = false;
                        break;
                    }
                }
            }

            // Approach C: MonoDictionary standard virtual layout
            if (numValues == 0) {
                auto monoD = (MonoDictionary*)candDict;
                int mCount = monoD->getNumValues();
                uintptr_t mValues = (uintptr_t)monoD->getValues();
                if (mValues != 0 && mCount > 0 && mCount <= 120) {
                    dictionary = candDict;
                    numValues = mCount;
                    valuesPtr = mValues;
                    isMonoDict = true;
                    break;
                }
            }
        }
        if (numValues > 0) break;
    }

    // Fallback: If no populated dictionary found yet, locate first structurally valid dictionary container
    if (!dictionary) {
        for (uintptr_t root : roots) {
            for (uintptr_t off : dictOffsets) {
                uintptr_t candDict = Read<uintptr_t>(root + off);
                if (!IsValidUserPointer64(candDict)) continue;
                uintptr_t entriesArray = Read<uintptr_t>(candDict + Offsets::DictionaryEntries);
                if (IsValidUserPointer64(entriesArray)) {
                    int maxLen = Read<int>(entriesArray + 0x18);
                    int dictCount = Read<int>(candDict + Offsets::DictionaryCount);
                    int total = (dictCount > 0 && dictCount <= maxLen) ? dictCount : maxLen;
                    if (total >= 0 && total <= 120) {
                        dictionary = candDict;
                        numValues = total;
                        valuesPtr = entriesArray + 0x20;
                        isMonoDict = true;
                        break;
                    }
                }
            }
            if (dictionary) break;
        }
    }

    if (!dictionary) {
        if (shouldLog) {
            lastLogTime = now;
            NIN_LOGI("DAEMON_ESP", "In Match: LocalPlayer OK (0x%lx), searching player dictionary...", (unsigned long)localPlayer);
        }
        return;
    }

    auto LocalPosition = GetNodePosition(GetPlayerHeadTF(localPlayer));
    if (LocalPosition == Vector3::Zero()) {
        LocalPosition = GetNodePosition(localPlayer) + Vector3(0.0f, 1.85f, 0.0f);
    }

    auto mIsFiring = Read<bool>(localPlayer + OffIsFiring()) ? 1 : 0;

    float bestPriority = 99999.0f;
    bool hasValidTarget = false;
    Vector3 bestTargetPos = Vector3::Zero();
    Vector3 bestTargetPosPe = Vector3::Zero();
    uintptr_t bestTargetObj = 0;

    for (int x = 0; x < numValues && SendResponse.PlayerCount < maxplayerCount; x++) {
        uintptr_t enemyList = 0;
        if (isMonoDict) {
            uintptr_t entryAddr = valuesPtr + (x * Offsets::DictionaryEntrySize);
            int32_t hashCode = Read<int32_t>(entryAddr);
            if (hashCode < 0) continue; // Skip empty hash slot in Dictionary
            enemyList = Read<uintptr_t>(entryAddr + Offsets::EntryEnemyList);
        } else {
            enemyList = Read<uintptr_t>(valuesPtr + (x * sizeof(uintptr_t)));
        }

        if (!IsValidUserPointer64(enemyList) || enemyList == localPlayer) continue;

        // 1. Skip eliminated / dead players immediately
        //    Only ONE offset is valid per build. Probing both made the engine
        //    read an unrelated field as "isDead" and silently dropped live
        //    enemies from the ESP.
        if (Read<bool>(enemyList + OffIsDead())) {
            continue;
        }

        // 2. Skip friendly teammates in Duo / Squad
        uintptr_t avatarManager = Read<uintptr_t>(enemyList + Offsets::Avatar_Uma);
        if (IsValidUserPointer64(avatarManager)) {
            uintptr_t umaAvatarSimple = Read<uintptr_t>(avatarManager + Offsets::Avatar_Uma);
            if (IsValidUserPointer64(umaAvatarSimple)) {
                uintptr_t umaData = Read<uintptr_t>(umaAvatarSimple + Offsets::Uma_Data);
                if (IsValidUserPointer64(umaData)) {
                    if (Read<bool>(umaData + Offsets::isTeam)) {
                        continue;
                    }
                }
            }
        }

        // 3. Special Active Skill transformations (Wukong, Orion, Ball)
        int specialStatus = Read<int>(enemyList + Offsets::TransformType);
        bool isSpecial = (specialStatus >= 1 && specialStatus <= 4);

        // 4. Knocked State Detection
        bool isKnocked = false;
        uintptr_t shadowState = Read<uintptr_t>(enemyList + Offsets::ShadowState);
        if (IsValidUserPointer64(shadowState)) {
            int xPose = Read<int>(shadowState + Offsets::XPose);
            if (xPose == 8) {
                isKnocked = true;
            }
        }

        auto isClientBot = Read<bool>(enemyList + OffIsClientBot());

        // Get Positions from Bone transforms (Normal: 0x6B0 / MAX: 0x6A8)
        uintptr_t headTF = GetPlayerHeadTF(enemyList);
        if (!IsValidUserPointer64(headTF)) headTF = Read<uintptr_t>(enemyList + Offsets::Bone_Head);
        if (!IsValidUserPointer64(headTF)) headTF = Read<uintptr_t>(enemyList + Offsets::MainCameraTransform);
        Vector3 EnemyPosition = GetNodePosition(headTF);
        Vector3 EnemyPositionPe = GetNodePosition(GetPlayerPeTF(enemyList));

        // If bone reading is null, check root position (GetNodePosition fallback + 1.85f)
        if (EnemyPosition == Vector3::Zero() && EnemyPositionPe == Vector3::Zero()) {
            Vector3 rootPos = GetNodePosition(enemyList);
            if (rootPos != Vector3::Zero()) {
                EnemyPositionPe = rootPos;
                EnemyPosition = rootPos + Vector3(0.0f, 1.85f, 0.0f);
            }
        } else if (EnemyPosition == Vector3::Zero() && EnemyPositionPe != Vector3::Zero()) {
            EnemyPosition = EnemyPositionPe + Vector3(0.0f, 1.85f, 0.0f);
        } else if (EnemyPosition != Vector3::Zero() && EnemyPositionPe == Vector3::Zero()) {
            EnemyPositionPe = EnemyPosition - Vector3(0.0f, 1.85f, 0.0f);
        }

        if (EnemyPosition == Vector3::Zero() || EnemyPositionPe == Vector3::Zero()) continue;

        float distanceToEnemy = (LocalPosition != Vector3::Zero()) ? Vector3::Distance(LocalPosition, EnemyPosition) : 999.0f;
        if (distanceToEnemy <= 20.0f) {
            SendResponse.ProximityEnemyCount++;
        }

        /*
         * THREAT COUNTER (reference engine)
         * ---------------------------------
         * The counter of the reference engine splits the enemies into the
         * ones in front of the camera and the ones behind it. The test is a
         * dot product between the direction to the enemy and the forward
         * vector of the view matrix.
         */
        {
            Vector3 camPos(g_camPosX.load(std::memory_order_relaxed),
                           g_camPosY.load(std::memory_order_relaxed),
                           g_camPosZ.load(std::memory_order_relaxed));
            if (camPos == Vector3::Zero()) {
                camPos = LocalPosition;
            }
            Vector3 viewForward = {matrix._13, matrix._23, matrix._33};
            Vector3 toEnemy = EnemyPosition - camPos;
            float dotProduct = toEnemy.X * viewForward.X + toEnemy.Y * viewForward.Y +
                               toEnemy.Z * viewForward.Z;
            if (dotProduct >= 0.0f) {
                SendResponse.EnemiesInFront++;
            } else {
                SendResponse.EnemiesBehind++;
            }
        }

        // Screen projection.
        // Entities that are completely outside the viewport are dropped, but
        // entities that are only PARTIALLY visible are kept - the previous
        // "X < 0" test silently removed every enemy on the left half of the
        // screen, even when the box was still half on screen.
        auto LocationHeadBox = WorldToScreenPoint(matrix, EnemyPosition);
        auto LocationToeBox = WorldToScreenPoint(matrix, EnemyPositionPe);
        if (LocationHeadBox.Z <= 0.0f) continue;

        {
            const float kMargin = 220.0f;
            float minX = std::min(LocationHeadBox.X, LocationToeBox.X);
            float maxX = std::max(LocationHeadBox.X, LocationToeBox.X);
            float minY = std::min(LocationHeadBox.Y, LocationToeBox.Y);
            float maxY = std::max(LocationHeadBox.Y, LocationToeBox.Y);
            if (maxX < -kMargin ||
                minX > (float)g_screenWidth.load(std::memory_order_relaxed) + kMargin ||
                maxY < -kMargin ||
                minY > (float)g_screenHeight.load(std::memory_order_relaxed) + kMargin) {
                continue;
            }
        }

        /* The radius of the aim circle is the reference formula: the angle
         * is a share of the screen width, not of the screen height. */
        float fovRadius = (pPlayer.AimFov / 180.0f) *
                          (g_screenWidth.load(std::memory_order_relaxed) * 0.55f);

        // Populate Response
        auto *data = &SendResponse.Players[SendResponse.PlayerCount];
        data->Head = LocationHeadBox;
        data->Toe = LocationToeBox;
        data->Distancia = distanceToEnemy;
        data->Name = isClientBot;
        data->IsCaido = isKnocked;
        data->SpecialAbilities = specialStatus;

        // Genuine Health reading.
        // 0 means "unknown" - the renderer skips the health bar instead of
        // showing a fake full bar (the previous default of 100 made every
        // unreadable player look completely healed).
        int Health = 0;
        uintptr_t curHPObj = Read<uintptr_t>(enemyList + Offsets::PlayerData_HP);
        if (!IsValidUserPointer64(curHPObj)) curHPObj = Read<uintptr_t>(enemyList + Offsets::DataPool);
        if (IsValidUserPointer64(curHPObj)) {
            uintptr_t cur = Read<uintptr_t>(curHPObj + 0x10);
            if (IsValidUserPointer64(cur)) {
                uintptr_t hpArr = Read<uintptr_t>(cur + 0x20);
                if (IsValidUserPointer64(hpArr)) {
                    short hpVal = Read<short>(hpArr + Offsets::HP_Offset);
                    if (hpVal > 0 && hpVal <= 500) {
                        Health = (int)hpVal;
                    }
                }
            }
        }
        data->Health = Health;

        // Weapon reading
        data->WeaponId = (short)ReadCurrentWeapon(enemyList);

        // Name
        memset(data->PlayerName, 0, 64);
        if (isClientBot) {
            strcpy(data->PlayerName, "Bot");
        } else {
            uintptr_t NamePtr = Read<uintptr_t>(enemyList + OffPlayerName());
            if (NamePtr <= 0x10000000) NamePtr = Read<uintptr_t>(enemyList + Offsets::NamePtrOld);
            if (NamePtr > 0x10000000) {
                getUTF8(data->PlayerName, NamePtr);
            }
            if (data->PlayerName[0] == '\0') strcpy(data->PlayerName, "Enemy");
        }

        // Silent Aim & Aimbot evaluation
        /*
         * THE TARGET SELECTION OF THE REFERENCE ENGINE
         * ----------------------------------------------
         *  - the target has to be ON the screen,
         *  - the FOV circle is only checked for the silent aim and for the
         *    hard aim (the collider lock uses the closest target),
         *  - the target that is closest to the crosshair wins.
         */
        bool aimActive = (pPlayer.silen4a || pPlayer.aimbot || pPlayer.aimbotlock);
        bool shouldIgnoreForAim = (pPlayer.ignoreKnocked && isKnocked);
        float screenWidth = (float)g_screenWidth.load(std::memory_order_relaxed);
        float screenHeight = (float)g_screenHeight.load(std::memory_order_relaxed);
        float screenDist = sqrt(pow(LocationHeadBox.X - screenWidth / 2, 2) +
                                pow(LocationHeadBox.Y - screenHeight / 2, 2));
        bool targetOnScreen = (LocationHeadBox.X >= 0.0f && LocationHeadBox.X <= screenWidth &&
                               LocationHeadBox.Y >= 0.0f && LocationHeadBox.Y <= screenHeight);
        bool fovPass = !(pPlayer.silen4a || pPlayer.aimbot) || isWithinFOV(LocationHeadBox, fovRadius);
        if (aimActive && !shouldIgnoreForAim && targetOnScreen && fovPass &&
            distanceToEnemy <= pPlayer.AimDistance) {
            float priorityValue = screenDist;
            if (priorityValue < bestPriority) {
                bestPriority = priorityValue;

                /*
                 * THE HEAD / CHEST PATTERN
                 * -------------------------
                 * The chest point is interpolated between the HEAD and the
                 * HIP (exactly like the reference engine). Interpolating
                 * towards the feet produced a point near the knees.
                 */
                Vector3 headPos = EnemyPosition;
                Vector3 chestPos = EnemyPosition;
                Vector3 hipPos = Vector3::Zero();
                uintptr_t hipTF = GetPlayerHipTF(enemyList);
                if (hipTF) hipPos = GetNodePosition(hipTF);
                if (hipPos == Vector3::Zero()) hipPos = EnemyPositionPe;
                if (hipPos != Vector3::Zero()) {
                    chestPos.X = EnemyPosition.X + (hipPos.X - EnemyPosition.X) * 0.35f;
                    chestPos.Y = EnemyPosition.Y + (hipPos.Y - EnemyPosition.Y) * 0.35f;
                    chestPos.Z = EnemyPosition.Z + (hipPos.Z - EnemyPosition.Z) * 0.35f;
                }

                bool useChest = false;
                if (pPlayer.KillInChest && Health > 0 && Health < 50) {
                    /* A weak target is shot into the chest. */
                    useChest = true;
                } else if (pPlayer.HeadRate > 0 || pPlayer.ChestRate > 0) {
                    /* THE HEAD / CHEST SHOT PATTERN
                     * --------------------------------
                     * HeadRate (0..3) and ChestRate (0..6) describe how many of
                     * every N shots go to the head. The pattern is built the
                     * same way as in the reference engine and in the Daemon
                     * (ninlite_daemon.cpp), so the kernel engine and the
                     * NINLITE engine now behave identically.
                     *
                     * The pattern used to be a 100 tick counter that only
                     * looked at ChestRate. That made the "HEAD RATE" slider
                     * do nothing at all in the kernel engine, and the two
                     * engines produced a different hit distribution.
                     */
                    int headRate = (int)pPlayer.HeadRate;
                    int chestRate = (int)pPlayer.ChestRate;
                    if (headRate > 3) headRate = 3;
                    if (chestRate > 6) chestRate = 6;
                    if (headRate < 0) headRate = 0;
                    if (chestRate < 0) chestRate = 0;
                    if (headRate == 0 && chestRate == 0) headRate = 1;

                    const int patternSize = headRate + chestRate;
                    bool pattern[16];
                    for (int i = 0; i < 16; i++) pattern[i] = false;
                    if (headRate > 0) {
                        float step = (float)patternSize / (float)headRate;
                        for (int i = 0; i < headRate; ++i) {
                            int pos = (int)(i * step + 0.5f);
                            if (pos >= patternSize) pos = patternSize - 1;
                            if (pos >= 0 && pos < 16) pattern[pos] = true;
                        }
                    }

                    /* The pattern advances on every new burst of fire. */
                    static int s_shotPatternIndex = 0;
                    static bool s_wasFiring = false;
                    bool isFiringNow = Read<bool>(localPlayer + OffIsFiring());
                    if (isFiringNow && !s_wasFiring) {
                        s_shotPatternIndex = (s_shotPatternIndex + 1) % (patternSize > 0 ? patternSize : 1);
                    }
                    s_wasFiring = isFiringNow;

                    const bool shootHead = pattern[s_shotPatternIndex % (patternSize > 0 ? patternSize : 1)];
                    useChest = !shootHead;
                } else if (!pPlayer.silentAimHead) {
                    useChest = true;
                }

                Vector3 targetPos = useChest ? chestPos : headPos;
                bestTargetPos = targetPos;
                bestTargetPosPe = EnemyPositionPe;
                bestTargetObj = enemyList;
                hasValidTarget = true;
            }
        }

        SendResponse.PlayerCount++;
    }

    ShareLocalPlayer.store(localPlayer, std::memory_order_relaxed);
    if ((pPlayer.aimMagnet || pPlayer.PlayerToMe) && hasValidTarget) {
        EnsureAimMagnetThreadStarted();
        ShareEnemy.store(bestTargetObj, std::memory_order_relaxed);
    } else {
        ShareEnemy.store(0, std::memory_order_relaxed);
        if (!hasValidTarget || (mIsFiring <= 0 && !pPlayer.PlayerToMe)) {
            RestoreAllPositions();
        }
    }

    // Threat Counter calculations (Visible on screen and near off-screen)
    const float offScreenMargin = 40.0f;
    for (int i = 0; i < SendResponse.PlayerCount; i++) {
        const PlayerData& player = SendResponse.Players[i];
        Vector3 head = player.Head;
        if (head.Z <= 0.0f) continue;
        const int screenW = g_screenWidth.load(std::memory_order_relaxed);
        const int screenH = g_screenHeight.load(std::memory_order_relaxed);
        bool onScreen = (head.X >= 0 && head.X <= screenW &&
                         head.Y >= 0 && head.Y <= screenH);
        if (onScreen) {
            SendResponse.VisibleEnemyCount++;
        } else {
            bool nearOffScreen = (head.X >= -offScreenMargin && head.X <= screenW + offScreenMargin &&
                                  head.Y >= -offScreenMargin && head.Y <= screenH + offScreenMargin);
            if (nearOffScreen) {
                SendResponse.VisibleNearOffScreenEnemyCount++;
            }
        }
    }

    bool aimActive = (pPlayer.silen4a || pPlayer.aimbot || pPlayer.aimbotlock);
    EnsureContinuousAimThreadStarted();
    if (aimActive && hasValidTarget) {
        std::lock_guard<std::mutex> lock(g_aimStateMutex);
        currentBestTargetPos = bestTargetPos;
        currentBestTargetObj = bestTargetObj;
    } else {
        std::lock_guard<std::mutex> lock(g_aimStateMutex);
        currentBestTargetPos = Vector3::Zero();
        currentBestTargetObj = 0;
    }

    if (shouldLog) {
        lastLogTime = now;
        NIN_LOGI("OFFSET_AUDIT", "--- LIVE OFFSET VERIFICATION REPORT ---");
        NIN_LOGI("OFFSET_AUDIT", "[1] libil2cpp.so Base: 0x%lx [OK]", (unsigned long)libAddress.load(std::memory_order_relaxed));
        NIN_LOGI("OFFSET_AUDIT", "[2] GameFacade Root: 0x%lx [%s]", (unsigned long)GameFacade_c, (IsValidUserPointer64(GameFacade_c) ? "VALID" : "NOT_FOUND"));
        NIN_LOGI("OFFSET_AUDIT", "[3] CurrentMatch: 0x%lx [%s]", (unsigned long)m_Match, (IsValidUserPointer64(m_Match) ? "IN_MATCH" : "IN_LOBBY"));
        NIN_LOGI("OFFSET_AUDIT", "[4] LocalPlayer: 0x%lx [%s]", (unsigned long)localPlayer, (IsValidUserPointer64(localPlayer) ? "SPAWNED" : "WAITING"));
        NIN_LOGI("OFFSET_AUDIT", "[5] Camera ViewMatrix: _11=%.2f, _22=%.2f [%s]", matrix._11, matrix._22, (IsValidViewMatrix(matrix) ? "VALID" : "PENDING"));
        NIN_LOGI("OFFSET_AUDIT", "[6] PlayerDictionary: Count=%d [%s]", numValues, (numValues > 0 ? "PLAYERS_FOUND" : "EMPTY_OR_SCANNING"));
        NIN_LOGI("OFFSET_AUDIT", "[7] ESP Enemies Rendered: %d", SendResponse.PlayerCount);
        NIN_LOGI("OFFSET_AUDIT", "---------------------------------------");
    }
}

inline std::mutex g_engineTransactionMutex;
inline Response s_cachedResponse{};

/* ------------------------------------------------------------------
 * SCENE WORKER
 * ------------------------------------------------------------------
 * The entity scan touches hundreds of game memory pages through the
 * kernel driver (hundreds of IOCTL round trips). It used to run directly
 * on the GL render thread, so every single frame blocked on the memory
 * scan - the overlay stuttered and the touch input lagged behind.
 *
 * The scan now runs in ONE dedicated worker thread that publishes a
 * finished snapshot. The render thread only copies that snapshot, so it
 * never waits for the memory scan. The worker paces itself: it aims for
 * ~60 Hz while the driver is fast and slows down automatically when the
 * driver is slow instead of queuing up work.
 * ------------------------------------------------------------------ */
inline std::atomic<bool> g_sceneWorkerStarted{false};

/* Forward declaration: the worker below needs the daemon transport, which is
 * defined further down together with the shared client. */
inline Response RequestDaemonESP(int screenWidth, int screenHeight);

inline void SceneWorker() {
    Response scratch{};   /* worker owned - never read by the render thread */

    while (true) {
        auto startedAt = std::chrono::steady_clock::now();
        bool updated = false;

        if (NinChannel::IsQxActive()) {
            /* QX channel: the scene comes from the root daemon over IPC.
             * The in-app engine must not touch the game memory in this mode,
             * the two channels never read the same data. */
            Response daemonResponse = RequestDaemonESP(
                g_screenWidth.load(std::memory_order_relaxed),
                g_screenHeight.load(std::memory_order_relaxed));
            if (daemonResponse.Success) {
                scratch = daemonResponse;
                updated = true;
            }
        } else if (target_pid.load() > 0) {
            /* RT channel: the scene comes from the Ring-0 kernel driver. */
            CreateDataList(scratch);
            updated = true;
        }

        if (updated) {
            /* Only the snapshot swap is locked - a few microseconds. */
            std::lock_guard<std::mutex> lock(g_engineTransactionMutex);
            s_cachedResponse = scratch;
        }

        long elapsed = (long)std::chrono::duration_cast<std::chrono::milliseconds>(
                           std::chrono::steady_clock::now() - startedAt).count();

        long sleepMs = 16 - elapsed;
        if (sleepMs < 4) sleepMs = 4;
        if (sleepMs > 50) sleepMs = 50;

        /*
         * Nothing was updated - the game is not running (or not reachable).
         * Ticking at 60 Hz in that state is pointless work: the worker would
         * hammer the driver/proc lookups for nothing and keep the device
         * busy. Back off to a slow poll instead.
         */
        if (!updated) {
            sleepMs = 250;
        }

        std::this_thread::sleep_for(std::chrono::milliseconds(sleepMs));
    }
}

inline void EnsureSceneWorkerStarted() {
    if (g_sceneWorkerStarted.exchange(true)) return;
    std::thread(SceneWorker).detach();
}

/* ------------------------------------------------------------------
 * DAEMON LINK
 * ------------------------------------------------------------------
 * The wire structures live in Includes/NinliteProtocol.h and are shared
 * with the Daemon, so the two sides can never disagree about the frame
 * layout. The client keeps ONE persistent connection:
 *  - a new socket per frame forced the Daemon to re-scan /proc and re-open
 *    the game memory on every single frame,
 *  - and every probe used to destroy the Daemon listener (the Daemon closed
 *    its listening socket as soon as a client disconnected).
 * ------------------------------------------------------------------ */
/* The daemon socket lives in Engine/DaemonLink.h - ONE worker thread owns it.
 * Nothing in this file touches the socket or waits for it any more. */
inline bool isNinLiteDaemonAvailable() {
    return NinEngine::DaemonLink::Instance().IsConnected();
}

/* Queues a control frame for the daemon. Never blocks. */
inline bool DaemonSyncRequest(int mode, bool enabled, int value) {
    NinEngine::DaemonLink::Instance().PushControl(mode, enabled, value);
    return true;
}

/**
 * Asks the Daemon (which runs as root) for the process of the game.
 *
 * Since Android 9 an App may not read /proc/<pid>/cmdline of a process of
 * another user, so the App cannot find the game on its own. The Daemon can
 * do it, because it runs with root rights.
 *
 * @param outPid    the pid of the game process (0 when it was not found)
 * @param outIsMax  true for Free Fire MAX, false for Free Fire Normal
 * @return true when the Daemon answered
 */
inline bool RequestDaemonDetect(pid_t &outPid, bool &outIsMax) {
    NinEngine::DaemonLink::Instance().RequestDetect();
    outPid = 0;
    outIsMax = false;
    return NinEngine::DaemonLink::Instance().GetDetect(outPid, outIsMax);
}

inline Response RequestDaemonESP(int screenWidth, int screenHeight) {
    Response result{};
    NinEngine::DaemonLink::Instance().RequestEsp(screenWidth, screenHeight);

    Ninlite::Response wire;
    if (!NinEngine::DaemonLink::Instance().GetEsp(wire)) {
        return result;
    }

    result.Success = (wire.success != 0);
    int count = wire.playerCount;
    if (count < 0 || count > Ninlite::kMaxPlayers) count = 0;
    result.PlayerCount = count;
    result.VisibleEnemyCount = wire.visibleEnemyCount;
    result.VisibleNearOffScreenEnemyCount = wire.visibleNearOffScreenEnemyCount;
    result.ProximityEnemyCount = wire.proximityEnemyCount;
    result.EnemiesInFront = wire.enemiesInFront;
    result.EnemiesBehind = wire.enemiesBehind;

    for (int i = 0; i < count && i < maxplayerCount; i++) {
        const Ninlite::PlayerData& src = wire.players[i];
        PlayerData& dst = result.Players[i];
        dst.Head = Vector3(src.head[0], src.head[1], src.head[2]);
        dst.Toe = Vector3(src.toe[0], src.toe[1], src.toe[2]);
        dst.Distancia = src.distance;
        dst.Health = src.health;
        dst.SpecialAbilities = src.specialAbilities;
        dst.WeaponId = (short)src.weaponId;
        dst.IsCaido = (src.flags & Ninlite::PLAYER_FLAG_KNOCKED) != 0;
        dst.Name = (src.flags & Ninlite::PLAYER_FLAG_BOT) != 0;
        dst.Flags  = src.flags;  // full bitmask pass-through (includes VISIBLE)
        memset(dst.PlayerName, 0, sizeof(dst.PlayerName));
        strncpy(dst.PlayerName, src.playerName, sizeof(dst.PlayerName) - 1);
        dst.PlayerName[sizeof(dst.PlayerName) - 1] = '\0';
    }
    return result;
}

inline Response getData(int screenWidth, int screenHeight) {
    g_screenWidth.store(screenWidth, std::memory_order_relaxed);
    g_screenHeight.store(screenHeight, std::memory_order_relaxed);
    EnsureSceneWorkerStarted();
    std::lock_guard<std::mutex> lock(g_engineTransactionMutex);
    return s_cachedResponse;
}

/* Lock protected access to the cached snapshot.
 *
 * The scene worker publishes the snapshot under g_engineTransactionMutex, so
 * every reader MUST take the same lock. The JNI status query used to read
 * s_cachedResponse directly without the lock, which is a data race on the
 * whole structure (100 players). */
inline Response GetCachedResponseSnapshot() {
    std::lock_guard<std::mutex> lock(g_engineTransactionMutex);
    return s_cachedResponse;
}

inline bool isConnected() {
    /* The connection state belongs to the selected channel: the RT channel is
     * only connected when the driver answered an IOCTL, the QX channel only
     * when the root daemon is reachable. */
    if (NinChannel::IsQxActive()) {
        return NinEngine::DaemonLink::Instance().IsConnected();
    }
    return MemoryIO::IsDriverReady();
}

/**
 * @brief Stops all app-side memory workers when the selected channel changes.
 *
 * QX owns the game memory through the daemon. RT workers must not continue
 * writing through the driver after QX is selected, and vice versa.
 */
inline void StopMemoryWorkersForChannelSwitch() {
    keepAiming.store(false, std::memory_order_relaxed);
    pPlayer.aimbot.store(false, std::memory_order_relaxed);
    pPlayer.aimbotlock.store(false, std::memory_order_relaxed);
    pPlayer.silen4a.store(false, std::memory_order_relaxed);
    pPlayer.aimMagnet.store(false, std::memory_order_relaxed);
    pPlayer.PlayerToMe.store(false, std::memory_order_relaxed);
    pPlayer.NoRecoil.store(false, std::memory_order_relaxed);
    pPlayer.SpeedHackV2.store(false, std::memory_order_relaxed);
    pPlayer.undercam.store(false, std::memory_order_relaxed);
    ShareLocalPlayer.store(0, std::memory_order_relaxed);
    ShareEnemy.store(0, std::memory_order_relaxed);
    Actived.activar.store(false, std::memory_order_relaxed);
    {
        std::lock_guard<std::mutex> lock(g_engineTransactionMutex);
        s_cachedResponse = Response{};
    }
    NinEngine::DaemonLink::Instance().PushControl(Ninlite::MODE_MASTER, false, 0);
}