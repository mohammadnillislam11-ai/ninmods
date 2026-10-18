#include <jni.h>
#include <cmath>
#include <algorithm>
#include <android/log.h>
#include "GameOffsets.h"
#include "DriverClient.h"
#include "EntityScanner.h"
#include "UnityTransform.h"
#include "Unity/Vector3.hpp"
#include "Unity/Quaternion.h"

static float s_flyGroundY = 0.0f, s_speedOriginal = 0.0f, s_spinAngle = 0.0f;
static bool s_flyInit = false, s_speedInit = false, s_pcInit = false, s_ghostInit = false, s_scaleInit = false;
static uint64_t s_stateHandle = 0, s_stateModuleBase = 0;
static uint64_t s_lastLocalPlayer = 0, s_lastSpdTarget = 0, s_lastUc = 0, s_lastMatch = 0;
static bool s_lastIs64 = false, s_lastIsMax = false;

static void ResetFeatureState() {
    s_flyGroundY = 0.0f;
    s_speedOriginal = 0.0f;
    s_spinAngle = 0.0f;
    s_flyInit = false;
    s_speedInit = false;
    s_pcInit = false;
    s_ghostInit = false;
    s_scaleInit = false;
}

static inline bool IsValidPtr(uint64_t ptr, bool is64) {
    if (ptr == 0) return false;
    if (is64) {
        if (ptr < 0x10000000ULL || ptr > 0x00007FFFFFFFF000ULL) return false;
        if ((ptr & 3ULL) != 0ULL) return false;
        if ((ptr & 0xFFFFFFFFULL) == 0ULL) return false;
        return true;
    }
    return ptr >= 0x10000ULL && ptr <= 0xFFFFFFFCULL && ((ptr & 3ULL) == 0ULL);
}

template <typename T>
static inline T ReadMem(int fd, uint64_t handle, uint64_t addr) {
    T val{};
    if (addr != 0) DriverRead(fd, handle, addr, &val, sizeof(T));
    return val;
}

static inline uint64_t ReadPtr(int fd, uint64_t handle, uint64_t addr, bool is64) {
    if (addr == 0) return 0ULL;
    if (is64) return ReadMem<uint64_t>(fd, handle, addr);
    return static_cast<uint64_t>(ReadMem<uint32_t>(fd, handle, addr));
}

template <typename T>
static inline bool WriteMem(int fd, uint64_t handle, uint64_t addr, T val) {
    if (addr == 0) return false;
    return DriverWrite(fd, handle, addr, &val, sizeof(T));
}

static uint64_t GetMatrixPtr(int fd, uint64_t handle, uint64_t player, const GameOffsets* off, bool is64) {
    if (player == 0) return 0;
    uint64_t root = ReadPtr(fd, handle, player + off->boneRoot, is64);
    uint64_t t1 = IsValidPtr(root, is64) ? ReadPtr(fd, handle, root + 0x10, is64) : 0;
    uint64_t t2 = IsValidPtr(t1, is64) ? ReadPtr(fd, handle, t1 + 0x10, is64) : 0;
    return IsValidPtr(t2, is64) ? ReadPtr(fd, handle, t2 + 0x38, is64) : 0;
}

static void SetEntitiesScale(int fd, uint64_t h, uint64_t match, uint64_t lp, const GameOffsets* off, bool is64, float s) {
    uint64_t dict = ReadPtr(fd, h, match + off->entityList, is64);
    int dictCount = dict ? ReadMem<int>(fd, h, dict + off->dictCount) : 0;
    if (dictCount <= 0) return;
    uint64_t vBase = ReadPtr(fd, h, dict + off->dictValues, is64);
    uint64_t vPtr = vBase + off->arrayHeader;
    int total = std::min(dictCount, 64);
    float scale[3] = {s, s, s};
    for (int i = 0; i < total; ++i) {
        uint64_t entry = vPtr + (i * off->entryStride);
        uint64_t ent = ReadPtr(fd, h, entry + off->entryValue, is64);
        if (IsValidPtr(ent, is64) && ent != lp) {
            uint64_t em = GetMatrixPtr(fd, h, ent, off, is64);
            if (em != 0) DriverWrite(fd, h, em + 0xAC, scale, sizeof(scale));
        }
    }
}

static void RevertCombatFeatures(int fd, uint64_t h) {
    if (h == 0) {
        ResetFeatureState();
        s_lastLocalPlayer = 0;
        s_lastSpdTarget = 0;
        s_lastUc = 0;
        s_lastMatch = 0;
        return;
    }
    const GameOffsets* off = GetActiveOffsets(s_lastIsMax, s_lastIs64);
    if (s_lastLocalPlayer != 0) {
        if (IsValidPtr(s_lastLocalPlayer, s_lastIs64)) {
            if (s_flyInit) {
                WriteMem<uint8_t>(fd, h, s_lastLocalPlayer + off->flyState, 0);
                if (off->flyStateFallback != 0) WriteMem<uint8_t>(fd, h, s_lastLocalPlayer + off->flyStateFallback, 0);
            }
            if (s_ghostInit) {
                WriteMem<uint8_t>(fd, h, s_lastLocalPlayer + off->ghostHack, 0);
            }
        } else {
            __android_log_print(ANDROID_LOG_WARN, "NIN_COMBAT",
                "RevertCombatFeatures: s_lastLocalPlayer 0x%llx is invalid, skipping direct player restore",
                (unsigned long long)s_lastLocalPlayer);
        }

        if (s_speedInit && s_lastSpdTarget != 0 && s_speedOriginal > 0.0f) {
            if (IsValidPtr(s_lastSpdTarget, s_lastIs64)) {
                WriteMem<float>(fd, h, s_lastSpdTarget + off->forceSpeed, s_speedOriginal);
            } else {
                __android_log_print(ANDROID_LOG_WARN, "NIN_COMBAT",
                    "RevertCombatFeatures: s_lastSpdTarget 0x%llx is invalid, skipping speed restore",
                    (unsigned long long)s_lastSpdTarget);
            }
        }
        if (s_pcInit && s_lastUc != 0) {
            if (IsValidPtr(s_lastUc, s_lastIs64)) {
                WriteMem<int>(fd, h, s_lastUc + 0x90, 0);
                WriteMem<uint8_t>(fd, h, s_lastUc + 0x9C, 0);
            } else {
                __android_log_print(ANDROID_LOG_WARN, "NIN_COMBAT",
                    "RevertCombatFeatures: s_lastUc 0x%llx is invalid, skipping PC movement restore",
                    (unsigned long long)s_lastUc);
            }
        }
    }
    if (s_scaleInit && s_lastMatch != 0) {
        if (IsValidPtr(s_lastMatch, s_lastIs64)) {
            SetEntitiesScale(fd, h, s_lastMatch, s_lastLocalPlayer, off, s_lastIs64, 1.0f);
        } else {
            __android_log_print(ANDROID_LOG_WARN, "NIN_COMBAT",
                "RevertCombatFeatures: s_lastMatch 0x%llx is invalid, skipping scale restore",
                (unsigned long long)s_lastMatch);
        }
    }
    ResetFeatureState();
    s_lastLocalPlayer = 0;
    s_lastSpdTarget = 0;
    s_lastUc = 0;
    s_lastMatch = 0;
}

#include "daemon/SocketClient.h"

extern "C" {

JNIEXPORT void JNICALL
Java_com_ninmods_menu_ipc_DriverBridge_nativeUpdateCombat(
    JNIEnv*, jobject,
    jint pid, jlong handle, jboolean isMax,
    jboolean flyHack, jboolean speedHack, jboolean pcMovement,
    jboolean ghostHack, jboolean tpMark, jboolean spinBot,
    jboolean wallBug, jboolean scaleDown
) {
    if (pid <= 0) return;

    auto& client = GetDaemonClient();
    if (!client.IsConnected()) {
        client.Connect(NIN_SOCKET_NAME);
    }

    if (client.IsConnected()) {
        uint32_t flags = 0;
        if (flyHack) flags |= 1;
        if (speedHack) flags |= 2;
        if (ghostHack) flags |= 4;
        if (wallBug) flags |= 8;
        if (pcMovement) flags |= 16;
        if (tpMark) flags |= 32;
        if (spinBot) flags |= 64;
        if (scaleDown) flags |= 128;
        client.UpdateCombat(pid, flags, 5.0f, 2.5f);
        return;
    }

    const int s_driverFd = DriverGetOpenFd();
    uint64_t h = static_cast<uint64_t>(handle);
    if (h == 0 || s_driverFd < 0) return;

    uint64_t base = DriverGetModuleBase(s_driverFd, static_cast<uint32_t>(pid), "libil2cpp.so");
    if (base == 0) return;
    if (s_stateHandle != h || s_stateModuleBase != base) {
        RevertCombatFeatures(s_driverFd, s_stateHandle);
        s_stateHandle = h;
        s_stateModuleBase = base;
    }
    uint8_t elfClass = ReadMem<uint8_t>(s_driverFd, h, base + 4);
    bool is64 = (elfClass != 1);
    const GameOffsets* off = GetActiveOffsets(isMax == JNI_TRUE, is64);
    s_lastIs64 = is64;
    s_lastIsMax = (isMax == JNI_TRUE);

    bool anyHack = (flyHack || speedHack || pcMovement || ghostHack || tpMark || spinBot || wallBug || scaleDown);
    if (!anyHack) {
        if (s_lastLocalPlayer != 0 || s_flyInit || s_speedInit || s_pcInit || s_ghostInit || s_scaleInit) {
            RevertCombatFeatures(s_driverFd, h);
        }
        return;
    }

    uint64_t resBase = base;
    uint64_t facade = 0;
    uint64_t match = 0;
    if (!ResolveGamePointers(pid, h, s_driverFd, off, is64, resBase, facade, match) || match == 0) {
        if (s_lastLocalPlayer != 0) RevertCombatFeatures(s_driverFd, h);
        return;
    }
    base = resBase;
    uint64_t stClass = ReadPtr(s_driverFd, h, facade + off->staticClassField, is64);
    uint64_t baseGame = IsValidPtr(stClass, is64) ? stClass : facade;

    uint64_t lp = ReadPtr(s_driverFd, h, match + off->localPlayer, is64);
    if (!IsValidPtr(lp, is64) && off->localPlayerFallback != 0) {
        lp = ReadPtr(s_driverFd, h, match + off->localPlayerFallback, is64);
    }
    if (!IsValidPtr(lp, is64)) {
        if (s_lastLocalPlayer != 0) RevertCombatFeatures(s_driverFd, h);
        return;
    }

    if (s_lastLocalPlayer != 0 && s_lastLocalPlayer != lp) {
        RevertCombatFeatures(s_driverFd, h);
    }
    s_lastLocalPlayer = lp;
    s_lastMatch = match;

    uint64_t matrix = GetMatrixPtr(s_driverFd, h, lp, off, is64);

    // 1. Fly Hack (Freeze Y + 4.5f & Fly State)
    if (flyHack && matrix != 0) {
        float curY = ReadMem<float>(s_driverFd, h, matrix + 0x90 + 4);
        if (!s_flyInit && curY != 0.0f) { s_flyGroundY = curY; s_flyInit = true; }
        if (s_flyInit) {
            WriteMem<float>(s_driverFd, h, matrix + 0x90 + 4, s_flyGroundY + 4.5f);
            WriteMem<uint8_t>(s_driverFd, h, lp + off->flyState, 1);
            if (off->flyStateFallback != 0) WriteMem<uint8_t>(s_driverFd, h, lp + off->flyStateFallback, 1);
        }
    } else if (s_flyInit) {
        WriteMem<uint8_t>(s_driverFd, h, lp + off->flyState, 0);
        if (off->flyStateFallback != 0) WriteMem<uint8_t>(s_driverFd, h, lp + off->flyStateFallback, 0);
        s_flyInit = false;
        s_flyGroundY = 0.0f;
    }

    // 2. Speed Hack (ForceSpeed Modifier)
    uint64_t atri = ReadPtr(s_driverFd, h, lp + off->attributes, is64);
    uint64_t spdTarget = (atri != 0) ? atri : lp;
    s_lastSpdTarget = spdTarget;
    if (speedHack && spdTarget != 0) {
        if (!s_speedInit) {
            s_speedOriginal = ReadMem<float>(s_driverFd, h, spdTarget + off->forceSpeed);
            if (s_speedOriginal <= 0.0f || s_speedOriginal > 20.0f) s_speedOriginal = 1.0f;
            s_speedInit = true;
        }
        WriteMem<float>(s_driverFd, h, spdTarget + off->forceSpeed, 6.8f);
    } else if (s_speedInit && spdTarget != 0) {
        WriteMem<float>(s_driverFd, h, spdTarget + off->forceSpeed, (s_speedOriginal > 0.0f) ? s_speedOriginal : 1.0f);
        s_speedInit = false;
        s_speedOriginal = 0.0f;
    }

    // 3. PC Movement (UserControl)
    uint64_t uc = ReadPtr(s_driverFd, h, lp + off->userControl, is64);
    s_lastUc = uc;
    if (pcMovement && uc != 0) {
        WriteMem<int>(s_driverFd, h, uc + 0x90, 1);
        WriteMem<uint8_t>(s_driverFd, h, uc + 0x9C, 1);
        s_pcInit = true;
    } else if (s_pcInit && uc != 0) {
        WriteMem<int>(s_driverFd, h, uc + 0x90, 0);
        WriteMem<uint8_t>(s_driverFd, h, uc + 0x9C, 0);
        s_pcInit = false;
    }

    // 4. Ghost Hack
    if (ghostHack) {
        WriteMem<uint8_t>(s_driverFd, h, lp + off->ghostHack, 1);
        s_ghostInit = true;
    } else if (s_ghostInit) {
        WriteMem<uint8_t>(s_driverFd, h, lp + off->ghostHack, 0);
        s_ghostInit = false;
    }

    // 5. SpinBot using Unity Quaternion::Euler
    if (spinBot && matrix != 0) {
        s_spinAngle = std::fmod(s_spinAngle + 12.0f, 360.0f);
        Quaternion q = Quaternion::Euler(0.0f, s_spinAngle, 0.0f);
        DriverWrite(s_driverFd, h, matrix + 0xA0, &q, sizeof(q));
    }

    // 6. WallBug using Unity Quaternion::Euler
    if (wallBug && matrix != 0) {
        Quaternion q = Quaternion::Euler(75.0f, 0.0f, 0.0f);
        DriverWrite(s_driverFd, h, matrix + 0xA0, &q, sizeof(q));
    }

    // 7. TP Mark (Teleport to Map Pin)
    if (tpMark && matrix != 0) {
        uint64_t scene = ReadPtr(s_driverFd, h, baseGame + off->uiInGameScene, is64);
        uint64_t bigMap = IsValidPtr(scene, is64) ? ReadPtr(s_driverFd, h, scene + off->bigMapCtrl, is64) : 0;
        uint64_t mapContent = IsValidPtr(bigMap, is64) ? ReadPtr(s_driverFd, h, bigMap + off->mapContentCtrl, is64) : 0;
        uint64_t markCtrl = IsValidPtr(mapContent, is64) ? ReadPtr(s_driverFd, h, mapContent + off->localMapMarkCtrl, is64) : 0;
        if (IsValidPtr(markCtrl, is64)) {
            float pos[3] = {0};
            DriverRead(s_driverFd, h, markCtrl + off->markPosition, pos, sizeof(pos));
            if (pos[0] != 0.0f || pos[2] != 0.0f) {
                pos[1] += 1.5f;
                DriverWrite(s_driverFd, h, matrix + 0x90, pos, sizeof(pos));
            }
        }
    }

    // 8. Scale Down
    if (scaleDown) {
        SetEntitiesScale(s_driverFd, h, match, lp, off, is64, -1.3f);
        s_scaleInit = true;
    } else if (s_scaleInit) {
        SetEntitiesScale(s_driverFd, h, match, lp, off, is64, 1.0f);
        s_scaleInit = false;
    }
}

JNIEXPORT void JNICALL
Java_com_ninmods_menu_ipc_DriverBridge_nativeResetCombat(JNIEnv*, jobject) {
    const int s_driverFd = DriverGetOpenFd();
    RevertCombatFeatures(s_driverFd, s_stateHandle);
}

} // extern "C"
