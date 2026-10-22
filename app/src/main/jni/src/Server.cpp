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
    NIN_LOGI("DAEMON", "=== Titoo Daemon Session Initialized ===");
}

void CloseLogs() {
    NIN_LOGI("DAEMON", "=== Titoo Daemon Session Ended ===");
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

// ========== Module Base Address (ROBUST) ==========
uintptr_t GetModuleBase(pid_t pid, const char* moduleName) {
    char mapsPath[64];
    snprintf(mapsPath, sizeof(mapsPath), "/proc/%d/maps", pid);
    FILE* fp = fopen(mapsPath, "r");
    if (!fp) {
        NIN_LOGE("DAEMON", "GetModuleBase: Cannot open %s", mapsPath);
        return 0;
    }

    char line[512];
    uintptr_t base = 0;

    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, moduleName)) {
            uintptr_t start = 0, offset = 0;
            char perms[5] = {0};
            if (sscanf(line, "%lx-%*lx %4s %lx", &start, perms, &offset) >= 1) {
                NIN_LOGI("DAEMON", "Maps match: %s", line);
                if (base == 0) {
                    base = start;
                }
                if (offset == 0 && (perms[0] == 'r' || perms[2] == 'x')) {
                    base = start;
                    break;
                }
            }
        }
    }

    fclose(fp);
    NIN_LOGI("DAEMON", "GetModuleBase(%s) = 0x%lx", moduleName, (unsigned long)base);
    return base;
}

// ========== Original Code ==========

Quaternion GetRotationToTheLocation(Vector3 Target, float Height, Vector3 MyEnemy) {
    return Quaternion::LookRotation((Target + Vector3(0, Height, 0)) - MyEnemy, Vector3(0, 1, 0));
}

bool isLocked = false;
uint64_t lockedEnemyAddress = 0;
Vector3 lockedTargetPos;

// Helper to restore all enemies to their original positions
void RestoreAllPositions() {
    if (BasePosMap.empty()) return;
    for (auto const& [enemy, originalPos] : BasePosMap) {
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
    for (int i = 0; i < (stringLen * 2); i++) {
        raw[i] = Read<unsigned char>(addr + Offsets::StringChars + i);
    }

    int j = 0;
    for (int i = 0; i < (stringLen * 2); i += 2) {
        unsigned short unicode = raw[i] | (raw[i + 1] << 8);
        if (unicode == 0) break;
        if (unicode < 0x80) {
            dst[j++] = (char)unicode;
        } else if (unicode < 0x800) {
            dst[j++] = (char)((unicode >> 6) | 0xC0);
            dst[j++] = (char)((unicode & 0x3F) | 0x80);
        } else if (unicode >= 0xD800 && unicode <= 0xDBFF) { 
            i += 2;
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
        uint64_t aimingInfo = Read<uint64_t>(oneself + Offsets::AimingInfo);
        if (!aimingInfo) aimingInfo = Read<uint64_t>(oneself + Offsets::AimingInfoOld);
        if (aimingInfo) {
            Vector3 startPos = Read<Vector3>(aimingInfo + Offsets::AimingStartPos);
            Vector3 dir = currentBestTargetPos - startPos;  
            Write<Vector3>(aimingInfo + Offsets::AimingDirection, dir);
        }
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
        libAddress = FindLibrary("libil2cpp.so", 1);
        if (libAddress == 0) return;
        if (mem_fd <= 0) InitMemoryAccess(target_pid);
    }

    static time_t lastLogTime = 0;
    time_t now = time(nullptr);
    bool shouldLog = (now - lastLogTime >= 3);

    // 1. Resolve GameFacade root static class pointer (Multi-Candidate)
    uintptr_t GameFacade_c = 0;
    const uintptr_t facadeOffsets[] = {
        Offsets::GameFacade,
        Offsets::GameFacadeAlt1,
        Offsets::GameFacadeAlt2,
        Offsets::GameFacadeGlobal
    };
    for (uintptr_t off : facadeOffsets) {
        auto ptr = Read<uintptr_t>(libAddress + off);
        if (ptr > 0x10000000) {
            GameFacade_c = ptr;
            break;
        }
    }

    if (GameFacade_c == 0 || GameFacade_c < 0x10000000) {
        if (shouldLog) {
            lastLogTime = now;
            NIN_LOGI("DAEMON_ESP", "Searching GameFacade root in libil2cpp (0x%lx)...", (unsigned long)libAddress);
        }
        return;
    }

    // 2. Resolve Match object via Modern Direct (0x8) or Legacy 5-Level Chain
    uintptr_t m_Match = 0;
    uintptr_t BaseGame = 0;

    // Path A: Modern Direct CurrentMatchGame (0x8) from GameFacade (ninmods_offsets.h & New_Brutal)
    m_Match = Read<uintptr_t>(GameFacade_c + Offsets::GameFacade_CurrentMatchGame);
    if (!m_Match || m_Match < 0x10000000) {
        m_Match = Read<uintptr_t>(GameFacade_c + Offsets::GameFacade_CurrentGame);
    }

    // Path B: Legacy 5-Level Indirect Chain (GameFacade -> P2 -> BaseGame -> Match)
    auto P2 = Read<uintptr_t>(GameFacade_c + Offsets::FacadeP2);
    if (P2 > 0x10000000) {
        BaseGame = Read<uintptr_t>(P2 + Offsets::P2BaseGame);
        if (!BaseGame) BaseGame = Read<uintptr_t>(P2 + Offsets::P2BaseGameAlt);
        if (!BaseGame) BaseGame = Read<uintptr_t>(P2 + Offsets::P2BaseGameZero);
        if (BaseGame > 0x10000000 && (!m_Match || m_Match < 0x10000000)) {
            m_Match = Read<uintptr_t>(BaseGame + Offsets::BaseGameMatch);
            if (!m_Match) m_Match = Read<uintptr_t>(BaseGame + Offsets::BaseGameMatchAlt);
        }
    }

    // If still in Lobby or Match not active
    if (!m_Match || m_Match < 0x10000000) {
        if (shouldLog) {
            lastLogTime = now;
            NIN_LOGI("DAEMON_ESP", "Lobby State: GameFacade=0x%lx valid, waiting for match start...", (unsigned long)GameFacade_c);
        }
        return;
    }

    // TimeService Speedhack ONLY if explicitly enabled
    if (speedHackEnabled && BaseGame > 0x10000000) {
        uintptr_t timeService = Read<uintptr_t>(BaseGame + Offsets::BaseGameTimeService);
        if (timeService > 0x10000000) {
            float speedVal = 0.03299999982f * pPlayer.gameSpeed;
            Write<float>(timeService + Offsets::TimeServiceSpeed, speedVal);
        }
    }

    // 3. Resolve LocalPlayer
    auto localPlayer = Read<uintptr_t>(m_Match + Offsets::MatchLocalPlayer);
    if (localPlayer == 0) localPlayer = Read<uintptr_t>(m_Match + Offsets::MatchLocalPlayerAlt);

    if (localPlayer == 0 || localPlayer < 0x10000000) {
        if (shouldLog) {
            lastLogTime = now;
            NIN_LOGI("DAEMON_ESP", "Match found (0x%lx), waiting for LocalPlayer spawn...", (unsigned long)m_Match);
        }
        return;
    }

    // 4. Resolve Camera & ViewMatrix
    auto FollowCamera = Read<uintptr_t>(localPlayer + Offsets::FollowCamera);
    if (!FollowCamera) FollowCamera = Read<uintptr_t>(localPlayer + Offsets::FollowCameraAlt);
    if (!FollowCamera) FollowCamera = Read<uintptr_t>(localPlayer + Offsets::FollowCameraOld);
    if (FollowCamera == 0 || FollowCamera < 0x10000000) return;

    auto Camera = Read<uintptr_t>(FollowCamera + Offsets::FollowCameraObj);
    if (Camera == 0 || Camera < 0x10000000) return;

    auto IntPtrCam = Read<uintptr_t>(Camera + Offsets::CameraIntPtr);
    if (IntPtrCam == 0 || IntPtrCam < 0x10000000) return;

    uintptr_t matrixOffset = string2Offset(WRAPPER_MARCO(OFFSET_CAMERA_MATRIX_STR));
    auto matrix = Read<D3DMatrix>(IntPtrCam + matrixOffset);
    if (matrix._11 == 0.0f && matrix._22 == 0.0f && matrix._33 == 0.0f) {
        matrix = Read<D3DMatrix>(IntPtrCam + string2Offset(WRAPPER_MARCO(OFFSET_CAMERA_MATRIX_ALT)));
    }

    auto myCam = Read<uintptr_t>(localPlayer + Offsets::MainCamera);
    Vector3 myCamPos = GetPosition(myCam);
    ViewMatrix = matrix;
    MainCameraTransformPos = myCamPos;

    // 5. Resolve Player Dictionary from Match (New_Brutal & Bull_Team) or BaseGame
    MonoDictionary* dictionary = nullptr;
    const uintptr_t dictOffsets[] = {
        Offsets::BaseGameDictionary,     // 0xC0
        Offsets::BaseGameDictionaryAlt,  // 0x148
        0x60,                            // New_Brutal ListPlayer
        0x68,
        0x70,
        0x140
    };

    // Check on m_Match first
    for (uintptr_t off : dictOffsets) {
        auto d = Read<MonoDictionary*>(m_Match + off);
        if (d && (uintptr_t)d > 0x10000000 && d->getNumValues() > 0 && d->getNumValues() < 120) {
            dictionary = d;
            break;
        }
    }

    // Fallback check on BaseGame
    if (!dictionary && BaseGame > 0x10000000) {
        for (uintptr_t off : dictOffsets) {
            auto d = Read<MonoDictionary*>(BaseGame + off);
            if (d && (uintptr_t)d > 0x10000000 && d->getNumValues() > 0 && d->getNumValues() < 120) {
                dictionary = d;
                break;
            }
        }
    }

    if (!dictionary) {
        if (shouldLog) {
            lastLogTime = now;
            NIN_LOGI("DAEMON_ESP", "In Match: LocalPlayer OK (0x%lx), searching player dictionary...", (unsigned long)localPlayer);
        }
        return;
    }

    int numValues = dictionary->getNumValues();
    auto valuesPtr = dictionary->getValues();
    auto LocalPosition = GetNodePosition(GetPlayerHeadTF(localPlayer));

    auto mIsFiring = Read<int>(localPlayer + Offsets::IsFiring);
    if (mIsFiring == 0) mIsFiring = Read<int>(localPlayer + Offsets::IsFiringOld);

    float bestPriority = 99999.0f;
    bool hasValidTarget = false;
    Vector3 bestTargetPos = Vector3::Zero();
    Vector3 bestTargetPosPe = Vector3::Zero();
    uintptr_t bestTargetObj = 0;

    for (int x = 0; x < numValues && SendResponse.PlayerCount < maxplayerCount; x++) {
        uintptr_t entryAddr = valuesPtr + (x * Offsets::DictionaryEntrySize);
        auto enemyList = Read<uintptr_t>(entryAddr + Offsets::EntryEnemyList);

        if (enemyList == 0 || enemyList < 0x100000000) continue;
        if (enemyList == localPlayer) continue;

        // Check if dead using New_Brutal PL_ISDEAD (0x7C)
        int isDead = Read<int>(enemyList + Offsets::IsDead);
        if (isDead != 0) continue;

        auto isClientBot = Read<bool>(enemyList + Offsets::IsClientBot);
        if (isClientBot != 1 && isClientBot != 0) {
            isClientBot = Read<bool>(enemyList + Offsets::IsClientBotOld);
        }

        // Get Positions (using New_Brutal toe + height logic for 100% reliable boxes)
        uintptr_t targetBone = GetTargetBone(enemyList);
        Vector3 EnemyPosition = GetNodePosition(targetBone);
        Vector3 EnemyPositionPe = GetNodePosition(GetPlayerPeTF(enemyList));

        // If bone reading is null, use toe + 1.85m offset (New_Brutal formula)
        if (EnemyPosition == Vector3::Zero() && EnemyPositionPe != Vector3::Zero()) {
            EnemyPosition = EnemyPositionPe + Vector3(0.0f, 1.85f, 0.0f);
        } else if (EnemyPosition != Vector3::Zero() && EnemyPositionPe == Vector3::Zero()) {
            EnemyPositionPe = EnemyPosition - Vector3(0.0f, 1.85f, 0.0f);
        }

        if (EnemyPosition == Vector3::Zero() || EnemyPositionPe == Vector3::Zero()) continue;

        // Screen projection
        auto LocationHeadBox = WorldToScreenPoint(matrix, EnemyPosition);
        auto LocationToeBox = WorldToScreenPoint(matrix, EnemyPositionPe);
        if (LocationHeadBox.X <= 0.0f && LocationHeadBox.Y <= 0.0f) continue;
        if (LocationToeBox.X <= 0.0f && LocationToeBox.Y <= 0.0f) continue;

        float distanceToEnemy = Vector3::Distance(LocalPosition, EnemyPosition);
        float fovRadius = (pPlayer.AimFov / 180.0f) * (g_screenHeight / 2.0f);

        // Populate Response
        auto *data = &SendResponse.Players[SendResponse.PlayerCount];
        data->Head = LocationHeadBox;
        data->Toe = LocationToeBox;
        data->Distancia = distanceToEnemy;
        data->Name = isClientBot;

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
        float screenDist = sqrt(pow(LocationHeadBox.X - g_screenWidth/2, 2) + pow(LocationHeadBox.Y - g_screenHeight/2, 2));
        if (pPlayer.silen4a && isWithinFOV(LocationHeadBox, fovRadius) && distanceToEnemy <= pPlayer.AimDistance) {
            float priorityValue = pPlayer.targetByDistance ? distanceToEnemy : screenDist;
            if (priorityValue < bestPriority) {
                bestPriority = priorityValue;
                bestTargetPos = EnemyPosition;
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

    if (pPlayer.silen4a && hasValidTarget) {
        currentBestTargetPos = bestTargetPos;
        currentBestTargetObj = bestTargetObj;
        if (!keepAiming.load()) {
            keepAiming.store(true);
            if (aimThread.joinable()) aimThread.join();  
            aimThread = std::thread([localPlayer]() { ContinuousAim(localPlayer); });  
        }
    } else if (keepAiming.load()) {
        keepAiming.store(false);
        if (aimThread.joinable()) aimThread.join();  
        currentBestTargetPos = Vector3::Zero();  
    }

    if (shouldLog) {
        lastLogTime = now;
        NIN_LOGI("OFFSET_AUDIT", "--- LIVE OFFSET VERIFICATION REPORT ---");
        NIN_LOGI("OFFSET_AUDIT", "[1] libil2cpp.so Base: 0x%lx [OK]", (unsigned long)libAddress);
        NIN_LOGI("OFFSET_AUDIT", "[2] GameFacade Root: 0x%lx [%s]", (unsigned long)GameFacade_c, (GameFacade_c > 0x10000000 ? "VALID" : "NOT_FOUND"));
        NIN_LOGI("OFFSET_AUDIT", "[3] CurrentMatch: 0x%lx [%s]", (unsigned long)m_Match, (m_Match > 0x10000000 ? "IN_MATCH" : "IN_LOBBY"));
        NIN_LOGI("OFFSET_AUDIT", "[4] LocalPlayer: 0x%lx [%s]", (unsigned long)localPlayer, (localPlayer > 0x10000000 ? "SPAWNED" : "WAITING"));
        NIN_LOGI("OFFSET_AUDIT", "[5] Camera ViewMatrix: _11=%.2f, _22=%.2f [%s]", matrix._11, matrix._22, (matrix._11 != 0.0f ? "VALID" : "PENDING"));
        NIN_LOGI("OFFSET_AUDIT", "[6] PlayerDictionary: Count=%d [%s]", (dictionary ? dictionary->getNumValues() : 0), (dictionary && dictionary->getNumValues() > 0 ? "PLAYERS_FOUND" : "EMPTY_OR_SCANNING"));
        NIN_LOGI("OFFSET_AUDIT", "[7] ESP Enemies Rendered: %d", SendResponse.PlayerCount);
        NIN_LOGI("OFFSET_AUDIT", "---------------------------------------");
    }
}



__attribute__ ((visibility ("hidden")))
int main(int argc, char*argv[]){
    signal(SIGPIPE, SIG_IGN);
    InitLogs();
    NIN_LOGI("DAEMON", "=== Titoo Daemon Starting (PID: %d, UID: %d, GID: %d) ===", getpid(), getuid(), getgid());
    
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
                    if (target_pid <= 0 || mem_fd <= 0 || libAddress == 0) {
                        static time_t lastAttachAttempt = 0;
                        time_t nowScan = time(nullptr);
                        if (nowScan - lastAttachAttempt >= 1) {
                            lastAttachAttempt = nowScan;
                            if (target_pid <= 0) target_pid = FindGamePid();
                            if (target_pid > 0) {
                                if (mem_fd <= 0) mem_fd = my_open_mem(target_pid);
                                if (libAddress == 0) libAddress = GetModuleBase(target_pid, "libil2cpp.so");
                                if (libAddress > 0) {
                                    NIN_LOGI("DAEMON", "Game attached successfully: PID=%d, mem_fd=%d, libil2cpp=0x%lx",
                                             target_pid, mem_fd, (unsigned long)libAddress);
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
                    pPlayer.AimFov = (float)request.value;
                    SendResponse.Success = true;
                } else if (request.Mode == 59) {
                    pPlayer.AimDistance = (float)request.value;
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