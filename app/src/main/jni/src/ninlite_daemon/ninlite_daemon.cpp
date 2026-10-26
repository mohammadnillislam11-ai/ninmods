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
#include <atomic>
#include <thread>
#include <chrono>
#include <fstream>
#include <cmath>
#include <vector>
#include <string>
#include <cstring>
#include <unordered_map>
#include <unordered_set>
#include <mutex>
#include <random>

#include "NinLiteSocketServer.h"
#include "BooleanServer.h"
#include "Namegun.h"
#include "../Includes/gameoffsets.h"
#include "../Unity/Vector3.hpp"
#include "../Unity/Vector4.hpp"
#include "../Unity/Quaternion.hpp"

#define TAG "NINLITE_DAEMON"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define maxplayerCount 100

// Protocol Data Structures matching STORM_MODS client/server
enum Mode {
    InitMode = 1,
    HackMode = 2,
    StopMode = 98,
    EspMode = 99
};

struct Request {
    int Mode;
    bool m_IsOn;
    int value;
    int ScreenWidth;
    int ScreenHeight;
};

enum class TransType : int {
    None = 0,
    Wukong = 1,
    Oreon = 2,
    Ball = 3,
    WukongSpecial = 4
};

struct PlayerData {
    Vector3 Head;
    Vector3 Toe;
    bool IsCaido;
    float Distancia;
    int Health;
    bool Name;
    char PlayerName[64];
    TransType SpecialAbilities;
    short WeaponId;
};

struct Response {
    bool Success;
    int PlayerCount;
    int EnemiesInFront;
    int EnemiesBehind;
    PlayerData Players[maxplayerCount];
};

struct TransformMatrix {
    Vector4 Position;
    Quaternion Rotation;
    Vector4 Scale;
};

struct D3DMatrix {
    float _11, _12, _13, _14;
    float _21, _22, _23, _24;
    float _31, _32, _33, _34;
    float _41, _42, _43, _44;
};

struct MonoDictionary {
    uintptr_t table;
    uintptr_t linkSlots;
    uintptr_t keys;
    uintptr_t values;
    int count;

    int getNumValues() {
        return count;
    }
    uintptr_t getValues() {
        return values;
    }
};

static pid_t g_target_pid = -1;
static int g_mem_fd = -1;
static uintptr_t g_libAddress = 0;
static bool g_isGameMax = true;
static NinLiteSocketServer g_server;

static int g_screenWidth = 1920;
static int g_screenHeight = 1080;

static uintptr_t g_currentLocalPlayer = 0;
static uintptr_t currentBestTargetObj = 0;
static Vector3 currentBestTargetPos = Vector3::Zero();
static Vector3 finalSilentPos = Vector3::Zero();
static std::atomic<bool> keepAiming{false};

static uintptr_t g_pullEnemyTargetAddr = 0;
static std::atomic<bool> g_hasPullTargetV2{false};

static uintptr_t g_aimHardTargetObj = 0;
static Vector3 g_aimHardTargetPos = Vector3::Zero();
static std::atomic<bool> g_hasAimHardTarget{false};
static std::mutex g_aimHardMutex;

static uintptr_t g_aimbotTargetObj = 0;
static Vector3 g_aimbotTargetPos = Vector3::Zero();

static std::unordered_map<uintptr_t, bool> g_isScaled;

static const std::unordered_set<short> blockedWeaponIds = {
    6016, 10006, -15524, -15521, 601, 603, 1201, 1204, 602, 608, 23, 1401, 1006, 1015
};

// Memory read/write via /proc/<pid>/mem (Direct Root Mode, ZERO kernel driver required)
template <typename T>
static T DaemonRead(uintptr_t addr) {
    T val{};
    if (addr == 0 || g_mem_fd <= 0) return val;
    pread64(g_mem_fd, &val, sizeof(T), (off64_t)addr);
    return val;
}

template <typename T>
static bool DaemonWrite(uintptr_t addr, T val) {
    if (addr == 0 || g_mem_fd <= 0) return false;
    ssize_t ret = pwrite64(g_mem_fd, &val, sizeof(T), (off64_t)addr);
    return ret == sizeof(T);
}

static Vector3 GetCameraWorldPosManual(D3DMatrix m) {
    float a11 = m._11, a12 = m._21, a13 = m._31;
    float a21 = m._12, a22 = m._22, a23 = m._32;
    float a31 = m._14, a32 = m._24, a33 = m._34;
    float b1 = -m._41, b2 = -m._42, b3 = -m._44;

    float det = a11 * (a22 * a33 - a23 * a32)
                - a12 * (a21 * a33 - a23 * a31)
                + a13 * (a21 * a32 - a22 * a31);

    if (fabs(det) < 0.0001f) return Vector3::Zero();
    float inv = 1.0f / det;

    return Vector3(
        (b1 * (a22 * a33 - a23 * a32) - a12 * (b2 * a33 - a23 * b3) + a13 * (b2 * a32 - a22 * b3)) * inv,
        (a11 * (b2 * a33 - a23 * b3) - b1 * (a21 * a33 - a23 * a31) + a13 * (a21 * b3 - b2 * a31)) * inv,
        (a11 * (a22 * b3 - b2 * a32) - a12 * (a21 * b3 - b2 * a31) + b1 * (a21 * a32 - a22 * a31)) * inv
    );
}

static Vector3 WorldToScreenPoint(D3DMatrix viewMatrix, Vector3 ScreenPos) {
    Vector3 result = Vector3(-1, -1, -1);

    float v9 = (ScreenPos.X * viewMatrix._11) + (ScreenPos.Y * viewMatrix._21) + (ScreenPos.Z * viewMatrix._31) + viewMatrix._41;
    float v10 = (ScreenPos.X * viewMatrix._12) + (ScreenPos.Y * viewMatrix._22) + (ScreenPos.Z * viewMatrix._32) + viewMatrix._42;
    float v12 = (ScreenPos.X * viewMatrix._14) + (ScreenPos.Y * viewMatrix._24) + (ScreenPos.Z * viewMatrix._34) + viewMatrix._44;

    if (v12 >= 0.001f) {
        float v13 = (float)g_screenWidth / 2.0f;
        float v14 = (float)g_screenHeight / 2.0f;

        result.X = v13 + (v13 * v9) / v12;
        result.Y = v14 - (v14 * v10) / v12;
    }
    return result;
}

static Vector3 GetPosition(uintptr_t Transform) {
    Vector3 pos = Vector3::Zero();
    uintptr_t transformObjValue = DaemonRead<uintptr_t>(Transform + 0x10);
    if (transformObjValue == 0) return pos;

    uintptr_t indexValue = DaemonRead<uintptr_t>(transformObjValue + 0x40);
    uintptr_t matrixValue = DaemonRead<uintptr_t>(transformObjValue + 0x38);
    if (!matrixValue) return pos;

    uintptr_t matrixListValue = DaemonRead<uintptr_t>(matrixValue + 0x18);
    uintptr_t matrixIndicesValue = DaemonRead<uintptr_t>(matrixValue + 0x20);
    if (!matrixListValue || !matrixIndicesValue) return pos;

    Vector3 resultValue = DaemonRead<Vector3>(matrixListValue + (indexValue * 0x30));
    int maxTries = 50;
    int tries = 0;
    int transformIndexValue = DaemonRead<int>(matrixIndicesValue + (indexValue * 4));

    while (transformIndexValue >= 0 && tries < maxTries) {
        tries++;
        TransformMatrix tMatrixValue = DaemonRead<TransformMatrix>(matrixListValue + (transformIndexValue * 0x30));

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

        transformIndexValue = DaemonRead<int>(matrixIndicesValue + (transformIndexValue * 4));
    }

    if (tries < maxTries) {
        pos = resultValue;
    }
    return pos;
}

static Vector3 GetNodePosition(uintptr_t nodeTransform) {
    if (!nodeTransform) return Vector3::Zero();
    uintptr_t transformValue = DaemonRead<uintptr_t>(nodeTransform + 0x10);
    if (transformValue == 0) return Vector3::Zero();
    return GetPosition(transformValue);
}

static uintptr_t GetPlayerHeadTF(uintptr_t player) {
    uintptr_t headOffset = g_isGameMax ? Offsets::Max::Player_HeadTF : Offsets::Normal::Bone_Head;
    return DaemonRead<uintptr_t>(player + headOffset);
}

static uintptr_t GetPlayerHipTF(uintptr_t player) {
    uintptr_t hipOffset = g_isGameMax ? Offsets::Max::Player_FootTF : Offsets::Normal::Bone_Hip;
    return DaemonRead<uintptr_t>(player + hipOffset);
}

static uintptr_t GetPlayerPeTF(uintptr_t player) {
    uintptr_t rootOffset = g_isGameMax ? Offsets::Max::Player_FootTF : Offsets::Normal::Bone_Root;
    return DaemonRead<uintptr_t>(player + rootOffset);
}

static void getUTF8(char* dst, uintptr_t addr) {
    if (addr < 0x10000000) {
        dst[0] = '\0';
        return;
    }
    int stringLen = DaemonRead<int>(addr + 0x10);
    if (stringLen <= 0 || stringLen > 64) {
        stringLen = 32;
    }
    unsigned char raw[128] = {0};
    for (int i = 0; i < (stringLen * 2) && i < 128; i++) {
        raw[i] = DaemonRead<unsigned char>(addr + 0x14 + i);
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
        } else {
            dst[j++] = (char)((unicode >> 12) | 0xE0);
            dst[j++] = (char)(((unicode >> 6) & 0x3F) | 0x80);
            dst[j++] = (char)((unicode & 0x3F) | 0x80);
        }
        if (j >= 60) break;
    }
    dst[j] = '\0';
}

static uint64_t ReadCurrentWeapon(uintptr_t player) {
    if (!player) return 0;
    uintptr_t weaponPtr = DaemonRead<uintptr_t>(player + Offsets::Normal::Player_CurrentWeapon);
    if (!weaponPtr) return 0;
    return DaemonRead<uint64_t>(weaponPtr + 0x10);
}

static bool isWithinFOV(Vector3 screenPos, float fovRadius) {
    float screenCenterX = g_screenWidth / 2.0f;
    float screenCenterY = g_screenHeight / 2.0f;
    float deltaX = screenPos.X - screenCenterX;
    float deltaY = screenPos.Y - screenCenterY;
    return sqrt(deltaX * deltaX + deltaY * deltaY) <= fovRadius;
}

// ==================== BACKGROUND HACK THREADS (STORM_MODS MOTOR) ====================
static void SilentAimThread() {
    while (true) {
        if (pPlayer.silen4a && g_currentLocalPlayer != 0) {
            bool isFiring = DaemonRead<bool>(g_currentLocalPlayer + (g_isGameMax ? Offsets::Max::Player_IsFiring : Offsets::Normal::Player_IsFiring));
            if (isFiring) {
                uintptr_t aimingInfo = DaemonRead<uintptr_t>(g_currentLocalPlayer + (g_isGameMax ? Offsets::Max::Player_BulletInfo : Offsets::Normal::Player_BulletInfo));
                if (aimingInfo) {
                    Vector3 startPos = DaemonRead<Vector3>(aimingInfo + 0x4C);
                    bool hasTarget = keepAiming && currentBestTargetObj != 0 && currentBestTargetPos != Vector3::Zero();

                    if (hasTarget && startPos.X != 0 && startPos.Y != 0 && startPos.Z != 0) {
                        Vector3 dirToTarget = currentBestTargetPos - startPos;
                        float len = sqrtf(dirToTarget.X * dirToTarget.X + dirToTarget.Y * dirToTarget.Y + dirToTarget.Z * dirToTarget.Z);
                        if (len > 0.0001f) {
                            dirToTarget /= len;
                        } else {
                            dirToTarget = Vector3(0, 0, 1);
                        }
                        DaemonWrite<Vector3>(aimingInfo + 0x40, dirToTarget);
                    }
                }
                std::this_thread::yield();
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
        if (pPlayer.aimhard && g_currentLocalPlayer != 0) {
            bool isFiring = DaemonRead<bool>(g_currentLocalPlayer + (g_isGameMax ? Offsets::Max::Player_IsFiring : Offsets::Normal::Player_IsFiring));
            if (isFiring) {
                std::lock_guard<std::mutex> lock(g_aimHardMutex);
                if (g_aimHardTargetObj != 0 && g_aimHardTargetPos != Vector3::Zero()) {
                    bool isDead = DaemonRead<bool>(g_aimHardTargetObj + (g_isGameMax ? Offsets::Max::Player_IsDead : Offsets::Normal::Player_IsDead));
                    if (!isDead) {
                        uintptr_t followCamera = DaemonRead<uintptr_t>(g_currentLocalPlayer + Offsets::Normal::FollowCamera);
                        uintptr_t camera = followCamera ? DaemonRead<uintptr_t>(followCamera + 0x30) : 0;
                        uintptr_t intPtrCam = camera ? DaemonRead<uintptr_t>(camera + 0x10) : 0;
                        if (intPtrCam) {
                            D3DMatrix matrix = DaemonRead<D3DMatrix>(intPtrCam + 0x100);
                            Vector3 camPos = GetCameraWorldPosManual(matrix);
                            if (camPos != Vector3::Zero()) {
                                Vector3 dir = Vector3::Normalized(g_aimHardTargetPos - camPos);
                                Quaternion targetRot = Quaternion::LookRotation(dir, Vector3(0, 1, 0));
                                DaemonWrite<Quaternion>(g_currentLocalPlayer + (g_isGameMax ? Offsets::Max::Player_Rotation : Offsets::Normal::Player_Rotation), targetRot);
                            }
                        }
                    }
                }
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
}

// ==================== ESP ITERATION & SCENE RECONSTRUCTION ====================
static void CreateDataList(Response& SendResponse) {
    SendResponse.PlayerCount = 0;
    SendResponse.EnemiesInFront = 0;
    SendResponse.EnemiesBehind = 0;

    if (!Actived.activar || g_libAddress == 0 || g_mem_fd <= 0) return;

    uintptr_t gameFacadeOffset = g_isGameMax ? Offsets::Max::GameFacade : Offsets::Normal::GameFacade;
    uintptr_t gameFacade = DaemonRead<uintptr_t>(g_libAddress + gameFacadeOffset);
    if (!gameFacade) return;

    uintptr_t p2 = DaemonRead<uintptr_t>(gameFacade + (g_isGameMax ? Offsets::Max::GameFacade_P2 : Offsets::Normal::GameFacade_Current));
    if (!p2) return;

    uintptr_t baseGame = DaemonRead<uintptr_t>(p2);
    if (!baseGame) return;

    uintptr_t mMatch = DaemonRead<uintptr_t>(baseGame + (g_isGameMax ? Offsets::Max::BaseGame_Match : Offsets::Normal::BaseGame_Match));
    if (!mMatch) return;

    uintptr_t livePlayer = DaemonRead<uintptr_t>(mMatch + (g_isGameMax ? Offsets::Max::Match_LocalPlayer : Offsets::Normal::Match_LocalPlayer));
    g_currentLocalPlayer = livePlayer;

    if (!livePlayer) return;

    uintptr_t followCamera = DaemonRead<uintptr_t>(livePlayer + Offsets::Normal::FollowCamera);
    if (!followCamera) followCamera = DaemonRead<uintptr_t>(livePlayer + Offsets::Normal::FollowCameraAlt);
    if (!followCamera) return;

    uintptr_t camera = DaemonRead<uintptr_t>(followCamera + 0x30);
    if (!camera) return;

    uintptr_t intPtrCam = DaemonRead<uintptr_t>(camera + 0x10);
    if (!intPtrCam) return;

    D3DMatrix matrix = DaemonRead<D3DMatrix>(intPtrCam + 0x100);
    Vector3 camPos = GetCameraWorldPosManual(matrix);

    uintptr_t dictPtr = DaemonRead<uintptr_t>(baseGame + (g_isGameMax ? Offsets::Max::Match_PlayerDict : Offsets::Normal::Match_PlayerDict));
    if (!dictPtr) return;

    int numValues = DaemonRead<int>(dictPtr + Offsets::Normal::Dict_Count);
    uintptr_t valuesArray = DaemonRead<uintptr_t>(dictPtr + Offsets::Normal::Dict_Entries);

    if (numValues <= 0 || numValues > 100 || !valuesArray) return;

    float bestDistance = 999.0f;
    uintptr_t bestTargetObj = 0;
    Vector3 bestTargetPos = Vector3::Zero();

    for (int x = 0; x < numValues; x++) {
        uintptr_t entryAddr = valuesArray + Offsets::Normal::Dict_ArrayHeader + (x * Offsets::Normal::Dict_EntryStride);
        uintptr_t enemy = DaemonRead<uintptr_t>(entryAddr + Offsets::Normal::Dict_EntryValue);

        if (!enemy || enemy == livePlayer) continue;

        bool isDead = DaemonRead<bool>(enemy + (g_isGameMax ? Offsets::Max::Player_IsDead : Offsets::Normal::Player_IsDead));
        if (isDead) continue;

        uintptr_t headTF = GetPlayerHeadTF(enemy);
        uintptr_t hipTF = GetPlayerHipTF(enemy);
        uintptr_t rootTF = GetPlayerPeTF(enemy);

        if (!headTF || !rootTF) continue;

        Vector3 headPos = GetNodePosition(headTF);
        Vector3 rootPos = GetNodePosition(rootTF);

        if (headPos == Vector3::Zero() || rootPos == Vector3::Zero()) continue;

        float distance = Vector3::Distance(camPos, headPos);
        if (distance < 0.5f || distance > 300.0f) continue;

        Vector3 screenHead = WorldToScreenPoint(matrix, headPos);
        Vector3 screenToe = WorldToScreenPoint(matrix, rootPos);

        if (screenHead.X < -100 || screenHead.X > g_screenWidth + 100) continue;

        PlayerData* pData = &SendResponse.Players[SendResponse.PlayerCount];
        pData->Head = screenHead;
        pData->Toe = screenToe;
        pData->Distancia = distance;
        pData->IsCaido = false;
        pData->Health = 100;
        pData->SpecialAbilities = TransType::None;

        bool isClientBot = DaemonRead<bool>(enemy + Offsets::Normal::Player_IsClientBot);
        pData->Name = isClientBot;

        if (isClientBot) {
            strcpy(pData->PlayerName, "Bot");
        } else {
            uintptr_t namePtr = DaemonRead<uintptr_t>(enemy + Offsets::Normal::Player_Name);
            getUTF8(pData->PlayerName, namePtr);
            if (pData->PlayerName[0] == '\0') {
                strcpy(pData->PlayerName, "Enemy");
            }
        }

        uint64_t wId = ReadCurrentWeapon(enemy);
        pData->WeaponId = (short)wId;

        // Target selection for Silent Aim / AimHard
        if (distance < bestDistance && isWithinFOV(screenHead, (pPlayer.AimFov > 0 ? pPlayer.AimFov : 120.0f))) {
            bestDistance = distance;
            bestTargetObj = enemy;
            bestTargetPos = headPos;
        }

        SendResponse.PlayerCount++;
        if (SendResponse.PlayerCount >= maxplayerCount) break;
    }

    if (bestTargetObj != 0) {
        currentBestTargetObj = bestTargetObj;
        currentBestTargetPos = bestTargetPos;
        keepAiming = true;
        {
            std::lock_guard<std::mutex> lock(g_aimHardMutex);
            g_aimHardTargetObj = bestTargetObj;
            g_aimHardTargetPos = bestTargetPos;
            g_hasAimHardTarget = true;
        }
    } else {
        keepAiming = false;
        {
            std::lock_guard<std::mutex> lock(g_aimHardMutex);
            g_hasAimHardTarget = false;
        }
    }
}

static pid_t ScanTargetGamePid() {
    DIR* dir = opendir("/proc");
    if (!dir) return -1;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type == DT_DIR && isdigit(entry->d_name[0])) {
            char cmdPath[64];
            snprintf(cmdPath, sizeof(cmdPath), "/proc/%s/cmdline", entry->d_name);
            FILE* f = fopen(cmdPath, "r");
            if (f) {
                char cmdline[256] = {0};
                if (fgets(cmdline, sizeof(cmdline), f)) {
                    if (strstr(cmdline, "com.dts.freefiremax")) {
                        g_isGameMax = true;
                        fclose(f);
                        closedir(dir);
                        return (pid_t)atoi(entry->d_name);
                    } else if (strstr(cmdline, "com.dts.freefireth") || strstr(cmdline, "com.dts.freefirein")) {
                        g_isGameMax = false;
                        fclose(f);
                        closedir(dir);
                        return (pid_t)atoi(entry->d_name);
                    }
                }
                fclose(f);
            }
        }
    }
    closedir(dir);
    return -1;
}

static void LogToDownload(const char* fmt, ...) {
    char buf[512];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);

    // User specified directory: /storage/emulated/0/Download/ninlite/ninlite.log
    mkdir("/storage/emulated/0/Download/ninlite", 0777);
    const char* logPath = "/storage/emulated/0/Download/ninlite/ninlite.log";
    FILE* f = fopen(logPath, "a");
    if (f) {
        time_t now = time(nullptr);
        char tStr[32];
        strftime(tStr, sizeof(tStr), "%Y-%m-%d %H:%M:%S", localtime(&now));
        fprintf(f, "[%s] %s\n", tStr, buf);
        fclose(f);
    }
}

static bool DaemonReadBytes(uintptr_t addr, void* buf, size_t n) {
    if (addr == 0 || g_mem_fd <= 0) return false;
    return pread64(g_mem_fd, buf, n, (off64_t)addr) == (ssize_t)n;
}

static bool IsElfHeader(uintptr_t addr) {
    if (addr == 0 || g_mem_fd <= 0) return false;
    unsigned char magic[4];
    if (!DaemonReadBytes(addr, magic, 4)) return false;
    return (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F');
}

// 100% Genuine STORM_MODS multi-candidate libil2cpp base locator
static uintptr_t FindCorrectLibBase(pid_t pid) {
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
            if (sscanf(line, "%lx", &start) == 1) {
                if (IsElfHeader(start)) {
                    elfCandidates[count++] = start;
                }
            }
        }
    }
    fclose(maps);

    // Validate candidates by testing GameFacade pointer
    uintptr_t gameFacadeOffset = g_isGameMax ? Offsets::Max::GameFacade : Offsets::Normal::GameFacade;
    for (int i = 0; i < count; i++) {
        if (elfCandidates[i] == 0) continue;
        uintptr_t testFacade = DaemonRead<uintptr_t>(elfCandidates[i] + gameFacadeOffset);
        if (testFacade > 0x10000000ULL && (testFacade >> 48) == 0) {
            LogToDownload("[DAEMON] Validated libil2cpp.so at 0x%lx via GameFacade probe (0x%lx)", 
                          (unsigned long)elfCandidates[i], (unsigned long)testFacade);
            return elfCandidates[i];
        }
    }

    if (count > 0) {
        LogToDownload("[DAEMON] Using primary ELF candidate: 0x%lx", (unsigned long)elfCandidates[0]);
        return elfCandidates[0];
    }
    return 0;
}

int main(int argc, char* argv[]) {
    LOGI("=== NINLITE PURE ROOT DAEMON (STORM_MODS ARCHITECTURE) STARTING ===");
    Namegun::Init();

    if (!g_server.Create()) {
        LOGE("Failed to create socket server");
        return 1;
    }
    if (!g_server.Bind()) {
        LOGE("Failed to bind socket server");
        return 1;
    }
    if (!g_server.Listen()) {
        LOGE("Failed to listen socket server");
        return 1;
    }

    LOGI("Listening on \\0%s", NINLITE_SOCKET_NAME);

    // Launch background worker threads
    std::thread(SilentAimThread).detach();
    std::thread(AimHardThread).detach();

    while (true) {
        if (g_server.Accept()) {
            LOGI("NINLITE UI Client connected to Daemon!");

            g_target_pid = ScanTargetGamePid();
            if (g_target_pid > 0) {
                char memPath[64];
                snprintf(memPath, sizeof(memPath), "/proc/%d/mem", g_target_pid);
                g_mem_fd = open(memPath, O_RDWR);
                if (g_mem_fd < 0) {
                    g_mem_fd = open(memPath, O_RDONLY);
                }
                g_libAddress = FindCorrectLibBase(g_target_pid);
                LOGI("Attached to Game PID: %d (Max: %d), MemFD: %d, libil2cpp: 0x%lx",
                     g_target_pid, g_isGameMax, g_mem_fd, (unsigned long)g_libAddress);
                LogToDownload("Attached to Game PID: %d (Max: %d), MemFD: %d, libil2cpp: 0x%lx",
                              g_target_pid, g_isGameMax, g_mem_fd, (unsigned long)g_libAddress);
            }

            Request req{};
            while (g_server.receive((void*)&req) > 0) {
                Response res{};
                res.Success = true;

                if (req.Mode == Mode::InitMode) {
                    if (g_target_pid <= 0 || kill(g_target_pid, 0) != 0 || g_mem_fd <= 0 || g_libAddress == 0) {
                        g_target_pid = ScanTargetGamePid();
                        if (g_target_pid > 0) {
                            if (g_mem_fd > 0) close(g_mem_fd);
                            char memPath[64];
                            snprintf(memPath, sizeof(memPath), "/proc/%d/mem", g_target_pid);
                            g_mem_fd = open(memPath, O_RDWR);
                            if (g_mem_fd < 0) {
                                g_mem_fd = open(memPath, O_RDONLY);
                            }
                            g_libAddress = FindCorrectLibBase(g_target_pid);
                            LOGI("[DAEMON] Attached to Game PID: %d, libil2cpp: 0x%lx, MemFD: %d",
                                 g_target_pid, (unsigned long)g_libAddress, g_mem_fd);
                            LogToDownload("Attached to Game PID: %d, libil2cpp: 0x%lx, MemFD: %d",
                                          g_target_pid, (unsigned long)g_libAddress, g_mem_fd);
                        }
                    }
                    res.Success = (g_target_pid > 0 && g_mem_fd > 0 && g_libAddress > 0);
                } else if (req.Mode == Mode::HackMode) {
                    res.Success = true;
                } else if (req.Mode == Mode::EspMode) {
                    if (g_target_pid <= 0 || kill(g_target_pid, 0) != 0 || g_mem_fd <= 0 || g_libAddress == 0) {
                        g_target_pid = ScanTargetGamePid();
                        if (g_target_pid > 0) {
                            if (g_mem_fd > 0) close(g_mem_fd);
                            char memPath[64];
                            snprintf(memPath, sizeof(memPath), "/proc/%d/mem", g_target_pid);
                            g_mem_fd = open(memPath, O_RDWR);
                            if (g_mem_fd < 0) g_mem_fd = open(memPath, O_RDONLY);
                            g_libAddress = FindCorrectLibBase(g_target_pid);
                        }
                    }
                    g_screenWidth = req.ScreenWidth;
                    g_screenHeight = req.ScreenHeight;
                    CreateDataList(res);
                    res.Success = (g_target_pid > 0 && g_mem_fd > 0);
                } else if (req.Mode == 3) {
                    Actived.activar = req.m_IsOn;
                    res.Success = true;
                } else if (req.Mode == 51) {
                    pPlayer.aimhard = req.m_IsOn;
                    res.Success = true;
                } else if (req.Mode == 54) {
                    pPlayer.aimbotlock = req.m_IsOn;
                    res.Success = true;
                } else if (req.Mode == 55) {
                    pPlayer.IgnoreKnock = req.m_IsOn;
                    res.Success = true;
                } else if (req.Mode == 57) {
                    pPlayer.silen4a = req.m_IsOn;
                    res.Success = true;
                } else if (req.Mode == 58) {
                    pPlayer.AimFov = (float)req.value;
                    res.Success = true;
                } else if (req.Mode == 62) {
                    pPlayer.ChestRate = (float)req.value;
                    res.Success = true;
                } else if (req.Mode == 70) {
                    pPlayer.HeadRate = (float)req.value;
                    res.Success = true;
                }

                g_server.send((void*)&res, sizeof(res));
            }

            if (g_mem_fd > 0) {
                close(g_mem_fd);
                g_mem_fd = -1;
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(500));
    }

    return 0;
}
