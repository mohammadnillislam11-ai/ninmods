#include <string>
#include <fstream>
#include <sstream>
#include <iostream>
#include <iomanip>
#include <mutex>
#include <thread>
#include <chrono>
#include <atomic>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>
#include <time.h>
#include <math.h>
#include <stdarg.h>
#include <sys/stat.h>
#include <signal.h>
#include <android/log.h>

#include "Server.h"
#include "Includes/Encrypt/oxorany_include.h"

// Global to prevent log spamming
uintptr_t last_failed_ptr = 0;

// ========== Unified Logging System ==========
void InitLogs() {
    NIN_LOGI("DAEMON", "=== NINMODS Daemon Session Initialized ===");
}

void CloseLogs() {
    NIN_LOGI("DAEMON", "=== NINMODS Daemon Session Ended ===");
}

void LogPlayerData(const char* name, bool isBot, int health, float distance, bool isDead) {
    NIN_LOGI("DAEMON_ENTITY", "Name: %s | Bot: %s | HP: %d | Dist: %.1f | Dead: %s",
             name, (isBot ? "Yes" : "No"), health, distance, (isDead ? "Yes" : "No"));
}

void LogDebug(const char* format, ...) {
    va_list args;
    va_start(args, format);
    char buffer[512];
    vsnprintf(buffer, sizeof(buffer), format, args);
    va_end(args);
    NIN_LOGI("DAEMON_DEBUG", "%s", buffer);
}

void LogWeaponData(const char* playerName, int weaponId, bool isLocal) {
    NIN_LOGI("DAEMON_WEAPON", "%s | Name: %s | WeaponID: %d", (isLocal ? "Local" : "Enemy"), playerName, weaponId);
}

// ========== Pointer Validation (Strict ARM64 64-bit User Space Pointer) ==========
bool IsValidUserPointer64(uintptr_t ptr) {
    // 1. High 16 bits must be strictly 0x0000 (rejects instructions like 0xa902... or packed opcodes)
    if ((ptr >> 48) != 0) return false;
    // 2. Must be in ARM64 Linux user-space memory range (from 256MB to 128TB)
    if (ptr < 0x10000000ULL || ptr > 0x00007FFFFFFFFFFFULL) return false;
    // 3. Must be aligned to at least 4 bytes (ARM64 heap objects and pointers are aligned)
    if ((ptr & 0x3) != 0) return false;
    return true;
}

// ========== Genuine Player Object Validator ==========
bool IsValidPlayerObject(uintptr_t player) {
    if (!IsValidUserPointer64(player)) return false;
    // 1. Check MainCameraTransform (0x3F0 from pAdress.h / 0x3E8)
    uintptr_t cam = Read<uintptr_t>(player + Offsets::MainCameraTransform);
    if (!IsValidUserPointer64(cam)) cam = Read<uintptr_t>(player + Offsets::MainCameraTransformAlt);
    if (IsValidUserPointer64(cam)) return true;

    // 2. Check HeadBone Transform
    uintptr_t head = Read<uintptr_t>(player + Offsets::Head);
    if (!IsValidUserPointer64(head)) head = Read<uintptr_t>(player + Offsets::HeadFallback);
    if (!IsValidUserPointer64(head)) head = Read<uintptr_t>(player + Offsets::HeadOld);
    if (IsValidUserPointer64(head)) return true;

    // 3. Check Feet Transform
    uintptr_t feet = Read<uintptr_t>(player + Offsets::Feet);
    if (!IsValidUserPointer64(feet)) feet = Read<uintptr_t>(player + Offsets::FeetFallback);
    if (IsValidUserPointer64(feet)) return true;

    // 4. Check Spine Transform
    uintptr_t spine = Read<uintptr_t>(player + Offsets::Spine);
    if (!IsValidUserPointer64(spine)) spine = Read<uintptr_t>(player + Offsets::SpineFallback);
    if (IsValidUserPointer64(spine)) return true;

    return false;
}

// ========== ELF Magic Header Checker ==========
bool is_elf_header(uintptr_t addr) {
    if (addr == 0 || !IsDriverConnected()) return false;
    unsigned char magic[4] = {0};
    if (DriverReadMemory(target_pid, addr, magic, 4)) {
        return (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F');
    }
    magic[0] = Read<uint8_t>(addr);
    magic[1] = Read<uint8_t>(addr + 1);
    magic[2] = Read<uint8_t>(addr + 2);
    magic[3] = Read<uint8_t>(addr + 3);
    return (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F');
}

// ========== ARM64 Instruction Decoders (Auto-Extract Static Pointers from Game Code) ==========
uintptr_t DecodeAdrpLdr(uintptr_t pc) {
    if (pc == 0 || !IsDriverConnected()) return 0;
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

// ========== Module Base Address (ROBUST MULTI-ELF DETECTOR) ==========
uintptr_t GetModuleBase(pid_t pid, const char* moduleName) {
    // 1. First Priority: Direct Kernel Ring 0 Driver Base Address
    uintptr_t kernelBase = DriverGetModuleBase(pid, moduleName);
    if (kernelBase > 0) {
        NIN_LOGI("DAEMON", "GetModuleBase: %s Base = 0x%lx (Ring-0 Kernel Driver Verified)", moduleName, (unsigned long)kernelBase);
        return kernelBase;
    }

    char mapsPath[64];
    snprintf(mapsPath, sizeof(mapsPath), "/proc/%d/maps", pid);
    FILE* fp = fopen(mapsPath, "r");
    if (!fp) {
        NIN_LOGE("DAEMON", "GetModuleBase: Cannot open %s", mapsPath);
        return 0;
    }

    char line[512];
    std::vector<uintptr_t> elfBases;
    std::vector<uintptr_t> allBases;

    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, moduleName)) {
            uintptr_t start = 0, offset = 0;
            char perms[5] = {0};
            if (sscanf(line, "%lx-%*lx %4s %lx", &start, perms, &offset) >= 1) {
                if (offset == 0 && perms[0] == 'r') {
                    allBases.push_back(start);
                    if (is_elf_header(start)) {
                        elfBases.push_back(start);
                        NIN_LOGI("DAEMON", "Found ELF mapping for %s: 0x%lx (perms: %s)", moduleName, (unsigned long)start, perms);
                    }
                }
            }
        }
    }
    fclose(fp);

    if (elfBases.empty()) {
        if (!allBases.empty()) {
            elfBases = allBases;
            NIN_LOGI("DAEMON", "Using fallback mapped bases for %s (count: %zu)", moduleName, elfBases.size());
        } else {
            NIN_LOGE("DAEMON", "No ELF headers found for %s", moduleName);
            return 0;
        }
    }

    const uintptr_t testOffsets[] = {
        Offsets::Normal::GameFacade, // 0xA872380 (Normal Primary from 5_6213160554375160447.h)
        Offsets::Max::GameFacade,    // 0xAD68E18 (MAX Primary from ff max 5_6246536088102380429.h)
        Offsets::GameFacade,         // 0xA872380
        Offsets::GameFacadeMax,      // 0xAD68E18
        Offsets::GameFacadeAlt1      // 0xAD68E18
    };

    // Test each candidate ELF base against our GameFacade offsets to find the live loaded library
    for (uintptr_t b : elfBases) {
        for (uintptr_t off : testOffsets) {
            uintptr_t candidate = Read<uintptr_t>(b + off);
            if (IsValidUserPointer64(candidate)) {
                NIN_LOGI("DAEMON", "VERIFIED libil2cpp base: 0x%lx (tested with offset 0x%lx -> ptr 0x%lx)",
                         (unsigned long)b, (unsigned long)off, (unsigned long)candidate);
                return b;
            }
        }
    }

    // Fallback: If not yet initialized, prefer elf2 if present
    if (elfBases.size() >= 2) {
        NIN_LOGI("DAEMON", "Defaulting to elf2 base: 0x%lx (out of %d ELF mappings)", (unsigned long)elfBases[1], (int)elfBases.size());
        return elfBases[1];
    }

    return elfBases[0];
}

// ========== Original Code ==========

Quaternion GetRotationToTheLocation(Vector3 Target, float Height, Vector3 MyEnemy) {
    return Quaternion::LookRotation((Target + Vector3(0, Height, 0)) - MyEnemy, Vector3(0, 1, 0));
}

bool isLocked = false;
uint64_t lockedEnemyAddress = 0;
Vector3 lockedTargetPos;
std::mutex g_aimStateMutex;

// Helper to restore all enemies to their original positions
void RestoreAllPositions() {
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

uintptr_t currentBestTargetObj = 0;
std::atomic<bool> keepAiming{false};
std::thread aimThread;
Vector3 currentBestTargetPos = Vector3::Zero();

uintptr_t ShareLocalPlayer = 0;
uintptr_t ShareEnemy = 0;
D3DMatrix ViewMatrix;
Vector3 MainCameraTransformPos = Vector3::Zero();

bool speedHackEnabled = false;

static float FloatFromBits(uint32_t bits) {
    float value = 0.0f;
    std::memcpy(&value, &bits, sizeof(value));
    return value;
}

void getUTF8(char* dst, uintptr_t addr) {
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
    DriverReadMemory(target_pid, addr + Offsets::StringChars, raw, toRead);

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

bool isWithinFOV(Vector3 screenPos, float fovRadius) {
    float screenCenterX = g_screenWidth / 2.0f;
    float screenCenterY = g_screenHeight / 2.0f;
    float deltaX = screenPos.X - screenCenterX;
    float deltaY = screenPos.Y - screenCenterY;
    float distanceFromCenter = sqrt(deltaX * deltaX + deltaY * deltaY);
    return distanceFromCenter <= fovRadius;
}

float FastRandomFloat() {
    static uint32_t x = 123456789;
    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    float normalized = x / 4294967295.0f;
    return (normalized - 0.5f) * 2.0f * 0.033;
}

void ContinuousAim(uintptr_t oneself) {
    while (keepAiming.load()) {
        if (!IsValidUserPointer64(oneself)) break;

        Vector3 targetPos = Vector3::Zero();
        uintptr_t targetObj = 0;
        {
            std::lock_guard<std::mutex> lock(g_aimStateMutex);
            targetPos = currentBestTargetPos;
            targetObj = currentBestTargetObj;
        }

        if (targetPos == Vector3::Zero()) {
            usleep(5000);
            continue;
        }

        int mIsFiring = Read<int>(oneself + Offsets::IsFiring);
        if (mIsFiring <= 0) mIsFiring = Read<int>(oneself + Offsets::IsFiringOld);
        if (mIsFiring <= 0) mIsFiring = Read<bool>(oneself + Offsets::Player_FiringStateAlt) ? 1 : 0;

        bool shouldLock = pPlayer.aimbotlock || (mIsFiring > 0);

        // 1. Camera LookRotation Aimbot & ViewRotation
        if ((pPlayer.aimbot || pPlayer.aimbotlock) && shouldLock) {
            Vector3 camPos = MainCameraTransformPos;
            if (camPos == Vector3::Zero()) {
                uintptr_t myCam = GetPlayerMainCamera(oneself);
                if (IsValidUserPointer64(myCam)) camPos = GetPosition(myCam);
            }
            if (camPos == Vector3::Zero()) camPos = GetNodePosition(oneself) + Vector3(0, 1.85f, 0);

            // Dynamic bone height adjustment
            float aimHeight = 0.0f;
            if (!pPlayer.silentAimHead && (rand() % 100) >= pPlayer.headshotRate) {
                aimHeight = -0.35f; // Chest / Neck level
            }

            Quaternion targetRot = GetRotationToTheLocation(targetPos, aimHeight, camPos);

            // Write character orientation (Normal: 0x61C / MAX: 0x214)
            Write<Quaternion>(oneself + Offsets::PlayerRotation, targetRot);
            Write<Quaternion>(oneself + Offsets::PlayerRotationOld, targetRot);

            // Write primary camera view rotation (MAX: 0x214)
            Write<Quaternion>(oneself + Offsets::Player_ViewRotation, targetRot);

            // Increment aim rotation counter to register in Unity engine (0x810)
            int rotCount = Read<int>(oneself + Offsets::CallSetAimRotationCount);
            if (rotCount >= 0) {
                Write<int>(oneself + Offsets::CallSetAimRotationCount, rotCount + 1);
            }
            int rotCountAlt = Read<int>(oneself + Offsets::CallSetAimRotationCountAlt);
            if (rotCountAlt >= 0) {
                Write<int>(oneself + Offsets::CallSetAimRotationCountAlt, rotCountAlt + 1);
            }
        }

        // 2. Silent Bullet Ray Redirection (BulletInfo: Normal 0xE60 / MAX 0xE58)
        if (pPlayer.silen4a && shouldLock) {
            uint64_t aimingInfo = Read<uint64_t>(oneself + Offsets::AimingInfo);
            if (!IsValidUserPointer64(aimingInfo)) aimingInfo = Read<uint64_t>(oneself + Offsets::AimingInfoAlt);
            if (!IsValidUserPointer64(aimingInfo)) aimingInfo = Read<uint64_t>(oneself + Offsets::AimingInfoOld);
            if (IsValidUserPointer64(aimingInfo)) {
                Vector3 rayStart = Read<Vector3>(aimingInfo + Offsets::AimingStartPos);
                if (rayStart == Vector3::Zero()) rayStart = Read<Vector3>(aimingInfo + Offsets::AimingStartPosAlt);
                if (rayStart != Vector3::Zero()) {
                    Vector3 diff = targetPos - rayStart;
                    float dist = Vector3::Magnitude(diff);
                    if (dist >= 0.5f && dist <= 500.0f) {
                        Vector3 dir = diff / dist;
                        Write<Vector3>(aimingInfo + Offsets::AimingDirection, dir);
                        Write<Vector3>(aimingInfo + Offsets::AimingDirectionAlt, dir);
                        Write<bool>(aimingInfo + 0x71, false);
                    }
                }
            }
        }

        // 3. Head Collider Lock (Player_AimTargetWrite = 0x80 from ff max 5_6246536088102380429.h)
        if ((pPlayer.aimbotlock || pPlayer.silen4a || pPlayer.aimbot) && shouldLock && IsValidUserPointer64(targetObj)) {
            uintptr_t headCollider = Read<uintptr_t>(targetObj + Offsets::Player_HeadCollider);
            if (IsValidUserPointer64(headCollider)) {
                Write<uintptr_t>(targetObj + Offsets::Player_AimTargetWrite, headCollider);
            }
        }

        usleep(4000); // 250Hz smooth iteration rate
    }
}

float Map(float value, float inMin, float inMax, float outMin, float outMax) {
    return outMin + (value - inMin) * (outMax - outMin) / (inMax - inMin);
}

// Helper to get the target bone based on fixed setting (Old Silent Aim style)
uintptr_t GetTargetBone(uintptr_t enemy) {
    if (pPlayer.silentAimHead) {
        return GetPlayerHeadTF(enemy); // Head
    } else {
        return GetPlayerSpineTF(enemy); // Body
    }
}

int GetWeaponId(uintptr_t player) {
    // InventoryManager
    uintptr_t inventoryManager = Read<uintptr_t>(player + Offsets::InventoryManager);
    if (!inventoryManager) inventoryManager = Read<uintptr_t>(player + Offsets::InventoryManagerOld);
    if (!inventoryManager) return 0;
    
    // Current weapon item at 0xA0
    uintptr_t item = Read<uintptr_t>(inventoryManager + Offsets::InventoryItem);
    if (!item) return 0;
    
    // ItemData at 0x28
    uintptr_t itemData = Read<uintptr_t>(item + Offsets::ItemData);
    if (!itemData) return 0;
    
    // Weapon ID at 0x40
    return Read<int>(itemData + Offsets::WeaponId);
}

void UpdateAimMagnetFast() {
    while (true) {
        if (!pPlayer.aimMagnet) {
            usleep(100000);  // 100ms idle sleep to save CPU and memory bandwidth
            continue;
        }
        usleep(10000);  // 10ms active sleep
        
        uintptr_t localP = ShareLocalPlayer;
        uintptr_t enemy = ShareEnemy;
        
        if (!localP || !enemy) continue;
        
        int mIsFiring = Read<int>(localP + Offsets::IsFiring);
        if (mIsFiring <= 0) mIsFiring = Read<int>(localP + Offsets::IsFiringOld);
        if (mIsFiring <= 0) continue;
        
        auto headTF = GetPlayerHeadTF(enemy);
        auto feetTF = GetPlayerPeTF(enemy);
        
        if (!headTF || !feetTF) continue;
        
        Vector3 currentHeadPos = GetNodePosition(headTF);
        Vector3 currentRootPos = GetNodePosition(feetTF);
        
        if (currentHeadPos == Vector3::Zero() || currentRootPos == Vector3::Zero()) continue;
        
        Vector3 vForward;  // ← 'forward' ki jagah 'vForward' use kiya (std::forward conflict se bachne ke liye)
        vForward.X = ViewMatrix._13;
        vForward.Y = ViewMatrix._23;
        vForward.Z = ViewMatrix._33;
        
        float len = sqrtf(vForward.X * vForward.X + vForward.Y * vForward.Y + vForward.Z * vForward.Z);
        if (len < 0.001f) continue;
        
        vForward.X /= len;
        vForward.Y /= len;
        vForward.Z /= len;
        
        float depth = Vector3::Distance(MainCameraTransformPos, currentHeadPos);
        if (depth < 0.1f) continue;
        
        Vector3 targetHeadPos;
        targetHeadPos.X = MainCameraTransformPos.X + vForward.X * depth;
        targetHeadPos.Y = MainCameraTransformPos.Y + vForward.Y * depth;
        targetHeadPos.Z = MainCameraTransformPos.Z + vForward.Z * depth;
        
        Vector3 offset;
        offset.X = targetHeadPos.X - currentHeadPos.X;
        offset.Y = targetHeadPos.Y - currentHeadPos.Y;
        offset.Z = targetHeadPos.Z - currentHeadPos.Z;
        
        Vector3 newRootPos;
        newRootPos.X = currentRootPos.X + offset.X;
        newRootPos.Y = currentRootPos.Y + offset.Y;
        newRootPos.Z = currentRootPos.Z + offset.Z;
        
        auto trVal = Read<uintptr_t>(feetTF + Offsets::TransformObj);
        if (trVal) {
            auto p3 = Read<uintptr_t>(trVal + Offsets::TransformObj);
            auto transformPtr = Read<uintptr_t>(p3 + Offsets::TransformMatrix);
            if (transformPtr) {
                Write<Vector3>(transformPtr + Offsets::EnemyPosForce, newRootPos);
            }
        }
    }
}



void CreateDataList(Response& SendResponse) {
    SendResponse.Success = true;
    SendResponse.PlayerCount = 0;
    SendResponse.VisibleEnemyCount = 0;
    SendResponse.VisibleNearOffScreenEnemyCount = 0;
    SendResponse.ProximityEnemyCount = 0;

    if (!Actived.activar) return;

    if (libAddress == 0) {
        libAddress = GetModuleBase(target_pid, "libil2cpp.so");
        if (libAddress == 0) return;
        if (!IsDriverConnected()) InitMemoryAccess(target_pid);
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
            uintptr_t classPtr = Read<uintptr_t>(libAddress + fOff);
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
            NIN_LOGI("DAEMON_ESP", "Scanning game memory in libil2cpp (0x%lx)... Waiting for local player spawn.", (unsigned long)libAddress);
        }
        return;
    }

    // TimeService Speedhack ONLY if explicitly enabled
    if (speedHackEnabled && IsValidUserPointer64(BaseGame)) {
        uintptr_t timeService = Read<uintptr_t>(BaseGame + Offsets::BaseGameTimeService);
        if (IsValidUserPointer64(timeService)) {
            float speedVal = 0.03299999982f * pPlayer.gameSpeed;
            Write<float>(timeService + Offsets::TimeServiceSpeed, speedVal);
        }
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
    if (!IsValidViewMatrix(matrix)) {
        Quaternion myCamRot = GetTransformRotation(myCam);
        float aspect = (g_screenHeight > 0) ? ((float)g_screenWidth / (float)g_screenHeight) : 1.777778f;
        matrix = BuildViewMatrix(myCamPos, myCamRot, 60.0f, aspect);
    }
    ViewMatrix = matrix;
    MainCameraTransformPos = myCamPos;

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

    auto mIsFiring = Read<int>(localPlayer + Offsets::IsFiring);
    if (mIsFiring == 0) mIsFiring = Read<int>(localPlayer + Offsets::IsFiringOld);
    if (mIsFiring == 0) mIsFiring = Read<uint8_t>(localPlayer + Offsets::IsFiringAlt);

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

        auto isClientBot = Read<bool>(enemyList + Offsets::IsClientBot);
        if (isClientBot != 1 && isClientBot != 0) {
            isClientBot = Read<bool>(enemyList + Offsets::IsClientBotOld);
        }

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

        // Screen projection
        auto LocationHeadBox = WorldToScreenPoint(matrix, EnemyPosition);
        auto LocationToeBox = WorldToScreenPoint(matrix, EnemyPositionPe);
        if (LocationHeadBox.X <= 0.0f && LocationHeadBox.Y <= 0.0f && LocationToeBox.X <= 0.0f && LocationToeBox.Y <= 0.0f) continue;
        if (LocationHeadBox.X <= 0.0f && LocationHeadBox.Y <= 0.0f) {
            LocationHeadBox = LocationToeBox;
            LocationHeadBox.Y -= 30.0f;
        }
        if (LocationToeBox.X <= 0.0f && LocationToeBox.Y <= 0.0f) {
            LocationToeBox = LocationHeadBox;
            LocationToeBox.Y += 30.0f;
        }

        float distanceToEnemy = Vector3::Distance(LocalPosition, EnemyPosition);
        float fovRadius = (pPlayer.AimFov / 180.0f) * (g_screenHeight / 2.0f);

        // Populate Response
        auto *data = &SendResponse.Players[SendResponse.PlayerCount];
        data->Head = LocationHeadBox;
        data->Toe = LocationToeBox;
        data->Distancia = distanceToEnemy;
        data->Name = isClientBot;
        data->IsCaido = false;

        // Health
        int Health = 100;
        auto IPRIDataPool = Read<uintptr_t>(enemyList + Offsets::DataPool);
        if (IPRIDataPool != 0) {
            auto ReplicationData = Read<uintptr_t>(IPRIDataPool + Offsets::ReplicationData);
            if (ReplicationData != 0) {
                uintptr_t items = ReplicationData + Offsets::ReplicationItems;
                auto element = Read<uintptr_t>(items + 0 * sizeof(uintptr_t));
                if (element != 0) {
                    Health = Read<int>(element + Offsets::Health);
                }
            }
        }
        data->Health = (Health > 0 && Health <= 500) ? Health : 100;

        // Name
        memset(data->PlayerName, 0, 64);
        if (isClientBot) {
            strcpy(data->PlayerName, "Bot");
        } else {
            uintptr_t NamePtr = Read<uintptr_t>(enemyList + Offsets::NamePtr); 
            if (NamePtr <= 0x10000000) NamePtr = Read<uintptr_t>(enemyList + Offsets::NamePtrOld);
            if (NamePtr > 0x10000000) {
                getUTF8(data->PlayerName, NamePtr);
            }
            if (data->PlayerName[0] == '\0') strcpy(data->PlayerName, "Enemy");
        }

        // Silent Aim & Aimbot evaluation
        bool aimActive = (pPlayer.silen4a || pPlayer.aimbot || pPlayer.aimbotlock);
        float screenDist = sqrt(pow(LocationHeadBox.X - g_screenWidth/2, 2) + pow(LocationHeadBox.Y - g_screenHeight/2, 2));
        if (aimActive && isWithinFOV(LocationHeadBox, fovRadius) && distanceToEnemy <= pPlayer.AimDistance) {
            float priorityValue = pPlayer.targetByDistance ? distanceToEnemy : screenDist;
            if (priorityValue < bestPriority) {
                bestPriority = priorityValue;
                // Target bone selection (Head vs Chest/Body)
                Vector3 targetPos = EnemyPosition;
                if (!pPlayer.silentAimHead && EnemyPositionPe != Vector3::Zero()) {
                    targetPos.X = EnemyPosition.X + (EnemyPositionPe.X - EnemyPosition.X) * 0.35f;
                    targetPos.Y = EnemyPosition.Y + (EnemyPositionPe.Y - EnemyPosition.Y) * 0.35f;
                    targetPos.Z = EnemyPosition.Z + (EnemyPositionPe.Z - EnemyPosition.Z) * 0.35f;
                }
                bestTargetPos = targetPos;
                bestTargetPosPe = EnemyPositionPe;
                bestTargetObj = enemyList;
                hasValidTarget = true;
            }
        }

        SendResponse.PlayerCount++;
    }

    ShareLocalPlayer = localPlayer;
    if (pPlayer.aimMagnet && hasValidTarget) {
        ShareEnemy = bestTargetObj;
    } else {
        ShareEnemy = 0;
        if (!hasValidTarget || mIsFiring <= 0) {
            BasePosMap.clear();
        }
    }

    // Threat Counter calculations
    const float offScreenMargin = 40.0f;
    const float PROXIMITY_DISTANCE = 20.0f;
    for (int i = 0; i < SendResponse.PlayerCount; i++) {
        const PlayerData& player = SendResponse.Players[i];
        Vector3 head = player.Head;
        bool onScreen = (head.X >= 0 && head.X <= g_screenWidth &&
                         head.Y >= 0 && head.Y <= g_screenHeight);
        if (onScreen) {
            SendResponse.VisibleEnemyCount++;
        } else {
            bool nearOffScreen = (head.X >= -offScreenMargin && head.X <= g_screenWidth + offScreenMargin &&
                                  head.Y >= -offScreenMargin && head.Y <= g_screenHeight + offScreenMargin);
            if (nearOffScreen) {
                SendResponse.VisibleNearOffScreenEnemyCount++;
            }
        }
        if (player.Distancia <= PROXIMITY_DISTANCE) {
            SendResponse.ProximityEnemyCount++;
        }
    }

    bool aimActive = (pPlayer.silen4a || pPlayer.aimbot || pPlayer.aimbotlock);
    if (aimActive && hasValidTarget) {
        {
            std::lock_guard<std::mutex> lock(g_aimStateMutex);
            currentBestTargetPos = bestTargetPos;
            currentBestTargetObj = bestTargetObj;
        }
        if (!keepAiming.load()) {
            keepAiming.store(true);
            if (aimThread.joinable()) aimThread.join();
            aimThread = std::thread([localPlayer]() { ContinuousAim(localPlayer); });
        }
    } else if (keepAiming.load()) {
        keepAiming.store(false);
        if (aimThread.joinable()) aimThread.join();
        std::lock_guard<std::mutex> lock(g_aimStateMutex);
        currentBestTargetPos = Vector3::Zero();
        currentBestTargetObj = 0;
    }

    if (shouldLog) {
        lastLogTime = now;
        NIN_LOGI("OFFSET_AUDIT", "--- LIVE OFFSET VERIFICATION REPORT ---");
        NIN_LOGI("OFFSET_AUDIT", "[1] libil2cpp.so Base: 0x%lx [OK]", (unsigned long)libAddress);
        NIN_LOGI("OFFSET_AUDIT", "[2] GameFacade Root: 0x%lx [%s]", (unsigned long)GameFacade_c, (IsValidUserPointer64(GameFacade_c) ? "VALID" : "NOT_FOUND"));
        NIN_LOGI("OFFSET_AUDIT", "[3] CurrentMatch: 0x%lx [%s]", (unsigned long)m_Match, (IsValidUserPointer64(m_Match) ? "IN_MATCH" : "IN_LOBBY"));
        NIN_LOGI("OFFSET_AUDIT", "[4] LocalPlayer: 0x%lx [%s]", (unsigned long)localPlayer, (IsValidUserPointer64(localPlayer) ? "SPAWNED" : "WAITING"));
        NIN_LOGI("OFFSET_AUDIT", "[5] Camera ViewMatrix: _11=%.2f, _22=%.2f [%s]", matrix._11, matrix._22, (IsValidViewMatrix(matrix) ? "VALID" : "PENDING"));
        NIN_LOGI("OFFSET_AUDIT", "[6] PlayerDictionary: Count=%d [%s]", numValues, (numValues > 0 ? "PLAYERS_FOUND" : "EMPTY_OR_SCANNING"));
        NIN_LOGI("OFFSET_AUDIT", "[7] ESP Enemies Rendered: %d", SendResponse.PlayerCount);
        NIN_LOGI("OFFSET_AUDIT", "---------------------------------------");
    }
}



__attribute__ ((visibility ("hidden")))
int main(int argc, char*argv[]){
    signal(SIGPIPE, SIG_IGN);
    InitLogs();
    NIN_LOGI("DAEMON", "=== NINMODS Daemon Starting (PID: %d, UID: %d, GID: %d) ===", getpid(), getuid(), getgid());
    
    // Launch Aim Magnet Thread
    std::thread(UpdateAimMagnetFast).detach();

    if (InitServer() != 0) {
        NIN_LOGE("DAEMON", "InitServer() failed to bind/listen on dual channels!");
        return 1;
    }
    NIN_LOGI("DAEMON", "InitServer() listening on dual channels (UNIX: \\0%s, TCP: %s:%d)", SOCKET_NAME, TCP_HOST, TCP_PORT);

    while (true) {
        NIN_LOGI("DAEMON", "Waiting for client connection...");
        if (server.Accept()) {
            NIN_LOGI("DAEMON", "Client accepted! Session established.");

            Request request{};
            while (server.receive((void*)&request) > 0) {
                Response SendResponse{};

                // Attach / scan target game dynamically if needed (throttled to 1s to prevent kernel mmap_lock starvation)
                if (request.Mode != Mode::InitMode && request.Mode != Mode::HackMode) {
                    if (target_pid <= 0 || !IsDriverConnected() || libAddress == 0) {
                        static time_t lastAttachAttempt = 0;
                        time_t nowScan = time(nullptr);
                        if (nowScan - lastAttachAttempt >= 1) {
                            lastAttachAttempt = nowScan;
                            if (target_pid <= 0) target_pid = FindGamePid();
                            if (target_pid > 0) {
                                if (IsDriverConnected()) {
                                    if (libAddress == 0) libAddress = GetModuleBase(target_pid, "libil2cpp.so");
                                    if (libAddress > 0) {
                                        NIN_LOGI("DAEMON", "Game attached via c_driver: PID=%d, DriverFD=%d (%s), libil2cpp=0x%lx",
                                                 target_pid, driver_fd, active_driver_node, (unsigned long)libAddress);
                                    }
                                } else {
                                    NIN_LOGE("DAEMON", "Target PID %d found, but c_driver not ready yet", target_pid);
                                }
                            }
                        }
                    }
                }

                if (request.Mode == Mode::InitMode) {
                    SendResponse.Success = true;
                    NIN_LOGI("DAEMON", "InitMode handshake received from client, replied Success=true");
                } else if (request.Mode == Mode::HackMode) {
                    SendResponse.Success = true;
                } else if (request.Mode == Mode::EspMode) {
                    g_screenWidth = request.ScreenWidth;
                    g_screenHeight = request.ScreenHeight;
                    CreateDataList(SendResponse);
                    SendResponse.Success = true;
                } else if (request.Mode == 3) {
                    Actived.activar = request.m_IsOn;
                    SendResponse.Success = true;
                    NIN_LOGI("DAEMON", "Feature: ESP Toggle = %s", Actived.activar ? "ON" : "OFF");
                } else if (request.Mode == 5) {
                    pPlayer.aimbot = request.m_IsOn;
                    SendResponse.Success = true;
                    NIN_LOGI("DAEMON", "Feature: Aimbot = %s", pPlayer.aimbot ? "ON" : "OFF");
                } else if (request.Mode == 22) {
                    pPlayer.fovawm = request.m_IsOn;
                    SendResponse.Success = true;
                } else if (request.Mode == 54) {
                    pPlayer.aimbotlock = request.m_IsOn;
                    SendResponse.Success = true;
                } else if (request.Mode == 57) {
                    pPlayer.silen4a = request.m_IsOn;
                    SendResponse.Success = true;
                } else if (request.Mode == 58) {
                    pPlayer.AimFov = FloatFromBits(static_cast<uint32_t>(request.value));
                    SendResponse.Success = true;
                } else if (request.Mode == 59) {
                    pPlayer.AimDistance = FloatFromBits(static_cast<uint32_t>(request.value));
                    SendResponse.Success = true;
                } else if (request.Mode == 60) {
                    speedHackEnabled = request.m_IsOn;
                    SendResponse.Success = true;
                } else if (request.Mode == 25) {
                    pPlayer.gameSpeed = (float)request.value / 10.0f; // Input 10-50 -> 1.0x-5.0x
                    SendResponse.Success = true;
                } else if (request.Mode == 61) {
                    pPlayer.silentAimHead = request.m_IsOn;
                    SendResponse.Success = true;
                } else if (request.Mode == 62) {
                    pPlayer.targetByDistance = request.m_IsOn;
                    SendResponse.Success = true;
                } else if (request.Mode == 63) {
                    pPlayer.aimMagnet = request.m_IsOn;
                    SendResponse.Success = true;
                } else if (request.Mode == 70) {
                    pPlayer.headshotRate = request.value;
                    if (pPlayer.headshotRate > 100) pPlayer.headshotRate = 100;
                    if (pPlayer.headshotRate < 0) pPlayer.headshotRate = 0;
                    SendResponse.Success = true;
                } else if (request.Mode == 71) {
                    pPlayer.ignoreBots = request.m_IsOn;
                    SendResponse.Success = true;
                } else if (request.Mode == 72) {
                    pPlayer.ignoreKnocked = request.m_IsOn;
                    SendResponse.Success = true;
                }

                server.send((void*)& SendResponse, sizeof(SendResponse));
            }
            NIN_LOGI("DAEMON", "Client disconnected from session. Resetting session...");
            server.CloseAccept();
        } else {
            usleep(200000); // 200ms
        }
    }
    CloseLogs();
    return 0;
}