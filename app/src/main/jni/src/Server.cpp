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
        usleep(10000);  // 10ms - stability ke liye
        
        if (!pPlayer.aimMagnet) continue;
        
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
    if (Actived.activar) {
        float bestPriority = 99999.0f;
        bool hasValidTarget = false;
        Vector3 bestTargetPos = Vector3::Zero();
        Vector3 bestTargetPosPe = Vector3::Zero();
        uintptr_t bestTargetObj = 0;
        SendResponse.PlayerCount = 0;
        
        if (libAddress == 0) {
            libAddress = FindLibrary("libil2cpp.so", 1);
            if (libAddress == 0) {
                LOGE("[Hack] libil2cpp.so not found!");
                return;
            }
            LOGI("[Hack] libil2cpp.so found at: %p", (void*)libAddress);
            
            // Inicializa acesso via /proc/pid/mem como fallback
            if (mem_fd <= 0) {
                InitMemoryAccess(target_pid);
                if (mem_fd > 0) LOGI("[Hack] Fallback memory access initialized (fd: %d)", mem_fd);
            }
        }

        static uintptr_t gameFacadeOffset = Offsets::GameFacade;
        auto GameFacade_c = Read<uintptr_t>(libAddress + gameFacadeOffset);
        if (GameFacade_c == 0) GameFacade_c = Read<uintptr_t>(libAddress + Offsets::GameFacadeAlt1);
        if (GameFacade_c == 0) GameFacade_c = Read<uintptr_t>(libAddress + Offsets::GameFacadeAlt2);
        if (GameFacade_c == 0) GameFacade_c = Read<uintptr_t>(libAddress + Offsets::GameFacadeGlobal);
        
        if (GameFacade_c == 0) {
            static bool errorLogged = false;
            if (!errorLogged) {
                LOGE("[Hack] Failed to read GameFacade at offset: %p", (void*)gameFacadeOffset);
                errorLogged = true;
            }
            return;
        }
            auto P2 = Read<uintptr_t>(GameFacade_c + Offsets::FacadeP2);
            if(P2) {
                auto BaseGame = Read<uintptr_t>(P2 + Offsets::P2BaseGame);
                if (!BaseGame) BaseGame = Read<uintptr_t>(P2 + Offsets::P2BaseGameAlt);
                if (!BaseGame) BaseGame = Read<uintptr_t>(P2 + Offsets::P2BaseGameZero);
                if (BaseGame) {
                    auto m_Match = Read<uintptr_t>(BaseGame + Offsets::BaseGameMatch);
                    if (!m_Match) m_Match = Read<uintptr_t>(BaseGame + Offsets::BaseGameMatchAlt);
                    if (m_Match) {
                        auto TimeSerive = Read<uintptr_t>(BaseGame + Offsets::BaseGameTimeService);
                        if (TimeSerive) {
                            if (speedHackEnabled) {
                                // Dynamic speed hack: 0.033 is normal, 0.059 is fast
                                float speedVal = 0.03299999982f * pPlayer.gameSpeed;
                                Write<float>(TimeSerive + Offsets::TimeServiceSpeed, speedVal);
                            } else {
                                Write<float>(TimeSerive + Offsets::TimeServiceSpeed, 0.03299999982f);
                            }
                        }
    
                            auto MatchIsRunning = Read<int>(m_Match + Offsets::MatchIsRunning);
                            if(MatchIsRunning != 4 && MatchIsRunning != 0) {
                                auto localPlayer = Read<uintptr_t>(m_Match + Offsets::MatchLocalPlayer);
                                if (localPlayer == 0) localPlayer = Read<uintptr_t>(m_Match + Offsets::MatchLocalPlayerAlt);
                                if (localPlayer != 0) {
                                    // NEW: Log local player weapon ID
                                    int localWeaponId = GetWeaponId(localPlayer);
                                    LogWeaponData("LocalPlayer", localWeaponId, true);
                                    
                                    auto FollowCamera = Read<uintptr_t>(localPlayer + Offsets::FollowCamera);
                                    if (!FollowCamera) FollowCamera = Read<uintptr_t>(localPlayer + Offsets::FollowCameraAlt);
                                    if (!FollowCamera) FollowCamera = Read<uintptr_t>(localPlayer + Offsets::FollowCameraOld);
                                    if (FollowCamera != 0) {
                                        auto Camera = Read<uintptr_t>(FollowCamera + Offsets::FollowCameraObj);
                                        if (Camera != 0) {
                                            auto IntPtrCam = Read<uintptr_t>(Camera + Offsets::CameraIntPtr);
                                            if (IntPtrCam != 0) {
                                                uintptr_t matrixOffset = string2Offset(WRAPPER_MARCO(OFFSET_CAMERA_MATRIX_STR));
                                                auto matrix = Read<D3DMatrix>(IntPtrCam + matrixOffset);
                                                if (matrix._11 == 0.0f && matrix._22 == 0.0f && matrix._33 == 0.0f) {
                                                    matrix = Read<D3DMatrix>(IntPtrCam + string2Offset(WRAPPER_MARCO(OFFSET_CAMERA_MATRIX_ALT)));
                                                }
                                                
                                                auto myCam = Read<uintptr_t>(localPlayer + Offsets::MainCamera);
                                                Vector3 myCamPos = GetPosition(myCam);
                                                
                                                ViewMatrix = matrix;
                                                MainCameraTransformPos = myCamPos;

                                                auto dictionary = Read<MonoDictionary *>(BaseGame + Offsets::BaseGameDictionary);
                                                if (!dictionary) dictionary = Read<MonoDictionary *>(BaseGame + Offsets::BaseGameDictionaryAlt);
                                                
                                                if (dictionary) {
                                                    int numValues = dictionary->getNumValues();
                                                    auto valuesPtr = dictionary->getValues();
                                                    auto LocalPosition = GetNodePosition(GetPlayerHeadTF(localPlayer));
                                                    
                                                    auto mIsFiring = Read<int>(localPlayer + Offsets::IsFiring);
                                                    if (mIsFiring == 0) mIsFiring = Read<int>(localPlayer + Offsets::IsFiringOld);
                                                    
                                                    for (int x = 0; x < numValues; x++) {
                                                        uintptr_t entryAddr = valuesPtr + (x * Offsets::DictionaryEntrySize);
                                                        auto enemyList = Read<uintptr_t>(entryAddr + Offsets::EntryEnemyList);
                                                        
                                                        if (enemyList == 0 || enemyList < 0x100000000) continue;
                                                        if (enemyList == localPlayer) continue;
                                                                                                                auto isClientBot = Read<bool>(enemyList + Offsets::IsClientBot);
                                                        if (isClientBot != 1 && isClientBot != 0) {
                                                            isClientBot = Read<bool>(enemyList + Offsets::IsClientBotOld);
                                                            if (isClientBot != 1 && isClientBot != 0) continue;
                                                        }
                                                        
                                                        auto AvatarManager = Read<uintptr_t>(enemyList + Offsets::AvatarManager);
                                                        if (AvatarManager == 0) AvatarManager = Read<uintptr_t>(enemyList + Offsets::AvatarManagerOld);
                                                        if (AvatarManager == 0) continue;
                                                        
                                                        auto UmaAvatarSimple = Read<uintptr_t>(AvatarManager + Offsets::AvatarSimple);
                                                        if (UmaAvatarSimple == 0) UmaAvatarSimple = Read<uintptr_t>(AvatarManager + Offsets::AvatarSimpleOld);
                                                        if (UmaAvatarSimple == 0) continue;
                                                        
                                                        auto IsVisible = Read<bool>(UmaAvatarSimple + Offsets::AvatarVisible);
                                                        if (IsVisible != 1) continue;
                                                        
                                                        auto UmaData = Read<uintptr_t>(UmaAvatarSimple + Offsets::UmaData);
                                                        if (UmaData == 0) continue;
                                                        
                                                        auto HHCBNAPCKHF = Read<uintptr_t>(enemyList + Offsets::HHCBNAPCKHF);
                                                        if (HHCBNAPCKHF != 0) {
                                                            bool isDieing = false;
                                                            auto maybeDead = Read<uintptr_t>(HHCBNAPCKHF + Offsets::HHCBDeadPtr);
                                                            if (maybeDead && Read<int>(maybeDead + Offsets::DeadStatus) == 8) isDieing = true;
                                                            
                                                            if (Read<bool>(UmaData + Offsets::UmaDataCheck) != 0) continue;
                                                            if (Read<bool>(enemyList + Offsets::EnemyCheck7C) != 0) continue;
                                                            
                                                            int Health = 0;
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
                                                            
                                                            auto HedColider = Read<uintptr_t>(enemyList + Offsets::HeadCollider);
                                                            if (HedColider == 0) HedColider = Read<uintptr_t>(enemyList + Offsets::HeadColliderOld);
                                                            if (pPlayer.aimbotlock) Write(enemyList + Offsets::HeadColliderWrite, HedColider);
                                                            
                                                            auto weapon = Read<uintptr_t>(localPlayer + Offsets::WeaponPtr);
                                                            if (weapon == 0) weapon = Read<uintptr_t>(localPlayer + Offsets::WeaponPtrOld);
                                                            if (pPlayer.aimbotlock && weapon != 0) {
                                                                float newAimLock = 0.0f;
                                                                Write(weapon + Offsets::WeaponAimlock, newAimLock);
                                                                Write(weapon + Offsets::WeaponAimlockOld, newAimLock);
                                                            }
                                                            
                                                            // Use target bone based on setting
                                                            uintptr_t targetBone = GetTargetBone(enemyList);
                                                            auto EnemyPosition = GetNodePosition(targetBone);
                                                            auto EnemyPositionPe = GetNodePosition(GetPlayerPeTF(enemyList));
                                                            auto CameraPosition = GetPosition(GetPlayerMainCamera(enemyList));
                                                            if (LocalPosition == Vector3::Zero() || EnemyPosition == Vector3::Zero() || CameraPosition == Vector3::Zero()) continue;
                                                            
                                                            float distanceToEnemy = Vector3::Distance(LocalPosition, EnemyPosition);
                                                            auto screenPos = WorldToScreenPoint(matrix, EnemyPosition);
                                                            float fovRadius = (pPlayer.AimFov / 180.0f) * (g_screenHeight / 2.0f);
                                                            
                                                            // SMART FILTERING LOGIC
                                                            bool skipTarget = false;
                                                            if (pPlayer.ignoreBots && isClientBot) skipTarget = true;
                                                            if (pPlayer.ignoreKnocked && isDieing) skipTarget = true;

                                                            // Regular Aimbot (corrected FOV comparison + Headshot % + Filters)
                                                            float screenDist = sqrt(pow(screenPos.X - g_screenWidth/2, 2) + pow(screenPos.Y - g_screenHeight/2, 2));
                                                            if (pPlayer.aimbot && screenDist < fovRadius && mIsFiring > 0 && !skipTarget) {
                                                                // Randomly adjust height based on headshotRate
                                                                float aimHeight = ((rand() % 100) < pPlayer.headshotRate) ? 0.0f : -0.3f;
                                                                Quaternion desiredRot = GetRotationToTheLocation(EnemyPosition, aimHeight, CameraPosition);
                                                                Write<Quaternion>(localPlayer + Offsets::PlayerRotation, desiredRot);
                                                                Write<Quaternion>(localPlayer + Offsets::PlayerRotationOld, desiredRot);
                                                            }
                                                            
                                                            // Silent Aim with filters
                                                            if (pPlayer.silen4a && isWithinFOV(screenPos, fovRadius) && distanceToEnemy <= pPlayer.AimDistance && !skipTarget) {
                                                                float priorityValue = pPlayer.targetByDistance ? distanceToEnemy : screenDist;
                                                                if (priorityValue < bestPriority) {
                                                                    bestPriority = priorityValue;
                                                                    bestTargetPos = EnemyPosition;
                                                                    bestTargetPosPe = EnemyPositionPe;
                                                                    bestTargetObj = enemyList;
                                                                    hasValidTarget = true;
                                                                }
                                                            }
                                                            
                                                            auto *data = &SendResponse.Players[SendResponse.PlayerCount];
                                                            
                                                            memset(data->PlayerName, 0, 64);
                                                            if (isClientBot) {
                                                                strcpy(data->PlayerName, "Enemy");
                                                            } else {
                                                                uintptr_t NamePtr = Read<uintptr_t>(enemyList + Offsets::NamePtr); 
                                                                if (NamePtr <= 0x10000000) NamePtr = Read<uintptr_t>(enemyList + Offsets::NamePtrOld);
                                                                if (NamePtr > 0x10000000) {
                                                                    getUTF8(data->PlayerName, NamePtr);
                                                                }
                                                                if (data->PlayerName[0] == '\0') strcpy(data->PlayerName, "Enemy");
                                                            }

                                                            // Use head position for ESP box
                                                            auto LocationHeadBox = WorldToScreenPoint(matrix, GetNodePosition(GetPlayerHeadTF(enemyList)));
                                                            auto LocationToeBox = WorldToScreenPoint(matrix, EnemyPositionPe);
                                                            if (LocationHeadBox.X < -0 || LocationToeBox.X < -0) continue;
                                                            
                                                            data->Head = LocationHeadBox;
                                                            data->Toe = LocationToeBox;
                                                            data->IsCaido = isDieing;
                                                            data->Distancia = distanceToEnemy;
                                                            data->Name = isClientBot;
                                                            data->Health = Health;
                                                            
                                                            LogPlayerData(data->PlayerName, isClientBot, Health, distanceToEnemy, isDieing);
                                                            
                                                            // NEW: Log enemy weapon ID
                                                            int enemyWeaponId = GetWeaponId(enemyList);
                                                            LogWeaponData(data->PlayerName, enemyWeaponId, false);
                                                            
                                                            ++SendResponse.PlayerCount;
                                                            if (SendResponse.PlayerCount == maxplayerCount) break;
                                                        }
                                                    } // End of For Loop
                                                    
                                                    ShareLocalPlayer = localPlayer;
                                                    
                                                    // Update global states for aim magnet thread
                                                    if(pPlayer.aimMagnet && hasValidTarget) {
                                                        ShareEnemy = bestTargetObj;
                                                    } else {
                                                        ShareEnemy = 0;
                                                        if (!hasValidTarget || mIsFiring <= 0) {
                                                            BasePosMap.clear();
                                                        }
                                                    }
                                                    
                                                } // End of if (dictionary)
                                                
                                                // ========== COUNT VISIBLE, NEAR OFF‑SCREEN, AND PROXIMITY ENEMIES ==========
                                                SendResponse.VisibleEnemyCount = 0;
                                                SendResponse.VisibleNearOffScreenEnemyCount = 0;
                                                SendResponse.ProximityEnemyCount = 0;

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
                                                // =================================================================
                                                
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
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }



__attribute__ ((visibility ("hidden")))
int main(int argc, char*argv[]){
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
            target_pid = FindGamePid();
            if (target_pid > 0) {
                mem_fd = my_open_mem(target_pid);
                libAddress = GetModuleBase(target_pid, "libil2cpp.so");
                NIN_LOGI("DAEMON", "Target game attached! PID: %d, mem_fd: %d, libil2cpp: 0x%lx",
                         target_pid, mem_fd, (unsigned long)libAddress);
            } else {
                NIN_LOGI("DAEMON", "Game not currently running. Will scan dynamically.");
            }
         
            Request request{};
            while (server.receive((void*)&request) > 0) {
                Response SendResponse{};
                if (target_pid <= 0 || mem_fd <= 0 || libAddress == 0) {
                    target_pid = FindGamePid();
                    if (target_pid > 0) {
                        if (mem_fd <= 0) mem_fd = my_open_mem(target_pid);
                        if (libAddress == 0) libAddress = GetModuleBase(target_pid, "libil2cpp.so");
                        NIN_LOGI("DAEMON", "Game detected dynamically: PID=%d, mem_fd=%d, libAddress=0x%lx",
                                 target_pid, mem_fd, (unsigned long)libAddress);
                    }
                }
                if (request.Mode == Mode::InitMode) {
                    SendResponse.Success = true;
                    NIN_LOGI("DAEMON", "InitMode handshake received from client");
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
                } else if(request.Mode == 5){
                    pPlayer.aimbot = request.m_IsOn;
                    SendResponse.Success = true;
                    NIN_LOGI("DAEMON", "Feature: Aimbot = %s", pPlayer.aimbot ? "ON" : "OFF");
                } else if(request.Mode == 22){
                    pPlayer.fovawm = request.m_IsOn;
                    SendResponse.Success = true;
                } else if(request.Mode == 54){
                    pPlayer.aimbotlock = request.m_IsOn;
                    SendResponse.Success = true;
                } else if(request.Mode == 57){
                    pPlayer.silen4a = request.m_IsOn;
                    SendResponse.Success = true;
                } else if(request.Mode == 58){
                    pPlayer.AimFov = (float)request.value;
                    SendResponse.Success = true;
                } else if(request.Mode == 59){
                    pPlayer.AimDistance = (float)request.value;
                    SendResponse.Success = true;
                } else if(request.Mode == 60){
                    speedHackEnabled = request.m_IsOn;
                    SendResponse.Success = true;
                } else if(request.Mode == 25){
                    pPlayer.gameSpeed = (float)request.value / 10.0f; // Input 10-50 -> 1.0x-5.0x
                    SendResponse.Success = true;
                } else if(request.Mode == 61){
                    pPlayer.silentAimHead = request.m_IsOn;
                    SendResponse.Success = true;
                } else if(request.Mode == 62){
                    pPlayer.targetByDistance = request.m_IsOn;
                    SendResponse.Success = true;
                } else if(request.Mode == 63){
                    pPlayer.aimMagnet = request.m_IsOn;
                    SendResponse.Success = true;
                } else if(request.Mode == 70){
                    pPlayer.headshotRate = request.value;
                    if(pPlayer.headshotRate > 100) pPlayer.headshotRate = 100;
                    if(pPlayer.headshotRate < 0) pPlayer.headshotRate = 0;
                    SendResponse.Success = true;
                } else if(request.Mode == 71){
                    pPlayer.ignoreBots = request.m_IsOn;
                    SendResponse.Success = true;
                } else if(request.Mode == 72){
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