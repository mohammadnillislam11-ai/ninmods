#include "EntityScanner.h"
#include "GameOffsets.h"
#include "DriverClient.h"
#include "UnityTransform.h"
#include <algorithm>
#include <cmath>

static inline bool IsValidPtr(uint64_t ptr, bool is64) {
    if (is64) return ptr >= 0x10000000ULL && ptr <= 0x00007FFFFFFFF000ULL && ((ptr & 3ULL) == 0ULL);
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

static const uint64_t kCandidateFacades[] = {
    0x0AD6DC18ULL,
    0x0AD68E18ULL,
    0x0AD3532CULL,
    0x0AD6DE18ULL,
    0x0AD6EE18ULL,
    0x0AD6CE18ULL,
    0x0A3532CULL
};

static bool ReadCamMatrix(int fd, uint64_t handle, const GameOffsets* off, uint64_t lp, bool is64, Matrix4x4& outM) {
    if (lp == 0 || !off) return false;
    uint64_t follow = ReadPtr(fd, handle, lp + off->followCamera, is64);
    if (!IsValidPtr(follow, is64)) follow = ReadPtr(fd, handle, lp + off->followCameraFallback, is64);
    if (!IsValidPtr(follow, is64)) return false;
    uint64_t cam = ReadPtr(fd, handle, follow + off->camera, is64);
    if (!IsValidPtr(cam, is64)) cam = ReadPtr(fd, handle, follow + off->cameraFallback, is64);
    if (!IsValidPtr(cam, is64)) cam = follow;
    if (!IsValidPtr(cam, is64)) return false;
    uint64_t intPtr = ReadPtr(fd, handle, cam + off->intPtrCam, is64);
    if (!IsValidPtr(intPtr, is64)) return false;
    outM = ReadMem<Matrix4x4>(fd, handle, intPtr + off->camMatrix);
    if (outM.m[0] != 0.0f || outM.m[5] != 0.0f || outM.m[10] != 0.0f) return true;
    outM = ReadMem<Matrix4x4>(fd, handle, intPtr + off->camMatrixFallback);
    return (outM.m[0] != 0.0f || outM.m[5] != 0.0f || outM.m[10] != 0.0f);
}

int ScanAndComputeEsp(int pid, uint64_t handle, int driverFd, bool isMax,
                      float screenW, float screenH, float* outRenderData,
                      int maxRender, ScanDiagnostics* outDiag, float aimFov) {
    if (pid <= 0 || handle == 0 || !outRenderData || maxRender <= 0) return 0;
    uint64_t base = DriverGetModuleBase(driverFd, static_cast<uint32_t>(pid), "libil2cpp.so");
    if (outDiag) outDiag->libBase = base;
    if (base == 0) return 0;

    uint8_t elfClass = ReadMem<uint8_t>(driverFd, handle, base + 4);
    bool is64 = (elfClass != 1);
    const GameOffsets* off = GetActiveOffsets(isMax, is64);

    static uint64_t s_cachedBase = 0;
    static uint64_t s_cachedFacade = 0;
    static uint64_t s_cachedMatch = 0;
    static uint64_t s_cachedDict = 0;
    static uint64_t s_cachedValuesBase = 0;
    static int s_cacheFrames = 0;

    if (s_cachedBase != base) {
        s_cachedBase = base;
        s_cachedFacade = 0;
        s_cachedMatch = 0;
        s_cachedDict = 0;
        s_cachedValuesBase = 0;
        s_cacheFrames = 0;
    }

    uint64_t facade = s_cachedFacade;
    uint64_t match = s_cachedMatch;

    bool needRefresh = (facade == 0 || match == 0 || (++s_cacheFrames > 60));
    if (!needRefresh) {
        uint64_t testLp = ReadPtr(driverFd, handle, match + off->localPlayer, is64);
        if (!IsValidPtr(testLp, is64)) {
            needRefresh = true;
        }
    }

    if (needRefresh) {
        s_cacheFrames = 0;
        facade = ReadPtr(driverFd, handle, base + off->gameFacade, is64);
        if (!IsValidPtr(facade, is64) && off->gameFacadeInd != 0) {
            facade = ReadPtr(driverFd, handle, base + off->gameFacadeInd, is64);
        }
        if (!IsValidPtr(facade, is64)) {
            for (uint64_t cand : kCandidateFacades) {
                uint64_t test = ReadPtr(driverFd, handle, base + cand, is64);
                if (IsValidPtr(test, is64)) {
                    facade = test;
                    break;
                }
            }
        }
        if (!IsValidPtr(facade, is64)) {
            s_cachedFacade = 0;
            s_cachedMatch = 0;
            s_cachedDict = 0;
            s_cachedValuesBase = 0;
            if (outDiag) outDiag->gameFacade = 0;
            return 0;
        }

        uint64_t stClass = ReadPtr(driverFd, handle, facade + off->staticClassField, is64);
        uint64_t baseGame = IsValidPtr(stClass, is64) ? stClass : facade;

        match = 0;
        if (IsValidPtr(stClass, is64)) {
            match = ReadPtr(driverFd, handle, stClass + off->currentMatch, is64);
        }
        if (!IsValidPtr(match, is64)) {
            match = ReadPtr(driverFd, handle, facade + off->currentMatch, is64);
        }
        if (!IsValidPtr(match, is64) && IsValidPtr(stClass, is64)) {
            uint64_t derefStClass = ReadPtr(driverFd, handle, stClass, is64);
            if (IsValidPtr(derefStClass, is64)) {
                match = ReadPtr(driverFd, handle, derefStClass + off->currentMatch, is64);
            }
        }
        if (!IsValidPtr(match, is64) && off->currentMatchFallback != 0) {
            if (IsValidPtr(stClass, is64)) match = ReadPtr(driverFd, handle, stClass + off->currentMatchFallback, is64);
            if (!IsValidPtr(match, is64)) match = ReadPtr(driverFd, handle, facade + off->currentMatchFallback, is64);
        }
        if (!IsValidPtr(match, is64)) {
            s_cachedFacade = facade;
            s_cachedMatch = 0;
            s_cachedDict = 0;
            s_cachedValuesBase = 0;
            if (outDiag) {
                outDiag->gameFacade = facade;
                outDiag->matchPtr = 0;
            }
            return 0;
        }
        if (s_cachedMatch != match) {
            s_cachedDict = 0;
            s_cachedValuesBase = 0;
        }
        s_cachedFacade = facade;
        s_cachedMatch = match;
    }

    if (outDiag) {
        outDiag->gameFacade = facade;
        outDiag->matchPtr = match;
    }

    static uint64_t s_savedLocal = 0;
    uint64_t liveLocal = ReadPtr(driverFd, handle, match + off->localPlayer, is64);
    if (IsValidPtr(liveLocal, is64)) s_savedLocal = liveLocal;
    uint64_t localPlayer = IsValidPtr(liveLocal, is64) ? liveLocal : s_savedLocal;
    if (outDiag) outDiag->localPlayer = localPlayer;

    Matrix4x4 camMat{};
    bool hasMatrix = ReadCamMatrix(driverFd, handle, off, localPlayer, is64, camMat);
    if (outDiag) outDiag->matrixValid = hasMatrix;
    if (!hasMatrix) return 0;

    uint64_t dict = s_cachedDict;
    int dictCount = dict ? ReadMem<int>(driverFd, handle, dict + off->dictCount) : 0;
    if (dictCount <= 0 || dictCount > 200) {
        dict = ReadPtr(driverFd, handle, match + off->entityList, is64);
        dictCount = dict ? ReadMem<int>(driverFd, handle, dict + off->dictCount) : 0;
        if (dictCount <= 0 && off->entityListFallback != 0) {
            dict = ReadPtr(driverFd, handle, match + off->entityListFallback, is64);
            dictCount = dict ? ReadMem<int>(driverFd, handle, dict + off->dictCount) : 0;
        }
        if (dictCount > 0 && dictCount <= 200) {
            s_cachedDict = dict;
            s_cachedValuesBase = ReadPtr(driverFd, handle, dict + off->dictValues, is64);
        } else {
            s_cachedDict = 0;
            s_cachedValuesBase = 0;
        }
    }
    if (outDiag) outDiag->dictCount = dictCount;
    if (dictCount <= 0 || dict == 0) return 0;

    uint64_t valuesBase = s_cachedValuesBase;
    if (valuesBase == 0) {
        valuesBase = ReadPtr(driverFd, handle, dict + off->dictValues, is64);
        s_cachedValuesBase = valuesBase;
    }
    if (valuesBase == 0) return 0;
    uint64_t valuesPtr = valuesBase + off->arrayHeader;

    int totalScan = std::min(dictCount, 64);
    int rendered = 0;
    float bestAimDist = 99999.0f;
    Vector3 bestAimHead(0.0f, 0.0f, 0.0f);
    bool hasAim = false;

    Vector3 myPos(0.0f, 0.0f, 0.0f);
    if (localPlayer != 0) {
        uint64_t myHeadBone = ReadPtr(driverFd, handle, localPlayer + off->boneHead, is64);
        if (myHeadBone != 0) myPos = GetNodePosition(myHeadBone, handle, driverFd);
    }

    uint8_t entryBatch[64 * 32] = {0};
    const size_t batchSize = static_cast<size_t>(totalScan) * off->entryStride;
    const bool batchOk = (batchSize <= sizeof(entryBatch)) &&
                         DriverRead(driverFd, handle, valuesPtr, entryBatch, batchSize);

    for (int i = 0; i < totalScan && rendered < maxRender; ++i) {
        uint64_t entityPtr = 0;
        if (batchOk) {
            const size_t entryOffset = (i * off->entryStride) + off->entryValue;
            if (is64) {
                entityPtr = *reinterpret_cast<uint64_t*>(entryBatch + entryOffset);
            } else {
                entityPtr = static_cast<uint64_t>(*reinterpret_cast<uint32_t*>(entryBatch + entryOffset));
            }
        } else {
            uint64_t entryAddr = valuesPtr + (i * off->entryStride);
            entityPtr = ReadPtr(driverFd, handle, entryAddr + off->entryValue, is64);
        }
        if (!IsValidPtr(entityPtr, is64) || (localPlayer != 0 && entityPtr == localPlayer)) continue;

        uint8_t isDead = ReadMem<uint8_t>(driverFd, handle, entityPtr + off->playerIsDead);
        if (isDead == 1) continue;

        int hp = 0;
        uint64_t pData = ReadPtr(driverFd, handle, entityPtr + off->playerData, is64);
        if (pData != 0) {
            uint64_t cur = ReadPtr(driverFd, handle, pData + off->playerDataCur, is64);
            uint64_t hpPtr = cur ? ReadPtr(driverFd, handle, cur + off->curHpPtr, is64) : 0ULL;
            if (hpPtr != 0) {
                short readHp = ReadMem<short>(driverFd, handle, hpPtr + off->playerHp);
                if (readHp > 0 && readHp <= 1000) hp = readHp;
            }
        }

        uint64_t headBone = ReadPtr(driverFd, handle, entityPtr + off->boneHead, is64);
        Vector3 headPos = (headBone != 0) ? GetNodePosition(headBone, handle, driverFd) : Vector3(0.0f, 0.0f, 0.0f);
        uint64_t rootBone = ReadPtr(driverFd, handle, entityPtr + off->boneRoot, is64);
        Vector3 toePos = (rootBone != 0) ? GetNodePosition(rootBone, handle, driverFd) : Vector3(0.0f, 0.0f, 0.0f);
        if (headPos == Vector3::zero() && toePos != Vector3::zero()) {
            headPos = toePos + Vector3(0.0f, 1.9f, 0.0f);
        } else if (toePos == Vector3::zero() && headPos != Vector3::zero()) {
            toePos = headPos - Vector3(0.0f, 1.9f, 0.0f);
        }
        if (headPos == Vector3::zero() || toePos == Vector3::zero()) continue;

        Vector3 headScreen{}, toeScreen{};
        bool headOk = camMat.WorldToScreen(headPos, screenW, screenH, headScreen);
        bool toeOk = camMat.WorldToScreen(toePos, screenW, screenH, toeScreen);
        float worldDist = (myPos != Vector3::zero()) ? Vector3::Distance(headPos, myPos) : 0.0f;
        if (worldDist <= 0.001f) {
            float camDepth = (headOk) ? headScreen.z : std::abs((headPos.x * camMat.m[3]) + (headPos.y * camMat.m[7]) + (headPos.z * camMat.m[11]) + camMat.m[15]);
            if (camDepth > 0.001f) worldDist = camDepth;
        }

        bool onScreen = headOk && toeOk && (headScreen.z >= 1.0f) && (toeScreen.z >= 1.0f) &&
                        (headScreen.x >= -60.0f && headScreen.x <= screenW + 60.0f) &&
                        (headScreen.y >= -60.0f && headScreen.y <= screenH + 60.0f);

        if (onScreen) {
            float headToToeH = std::abs(toeScreen.y - headScreen.y);
            float boxH = headToToeH * 1.18f;
            if (boxH < 5.0f) continue;
            float boxW = boxH * 0.50f;
            float left = headScreen.x - (boxW * 0.5f);
            float top = headScreen.y - (boxH * 0.12f);

            int idx = rendered * 10;
            outRenderData[idx + 0] = left;
            outRenderData[idx + 1] = top;
            outRenderData[idx + 2] = boxW;
            outRenderData[idx + 3] = boxH;
            outRenderData[idx + 4] = headScreen.x;
            outRenderData[idx + 5] = top;
            outRenderData[idx + 6] = worldDist;
            outRenderData[idx + 7] = static_cast<float>(hp);
            outRenderData[idx + 8] = (hp <= 0) ? 1.0f : 0.0f;
            outRenderData[idx + 9] = 0.0f; // on-screen
            rendered++;

            if (hp > 0 && isDead == 0) {
                float distC = std::hypot(headScreen.x - (screenW * 0.5f), headScreen.y - (screenH * 0.5f));
                float maxFov = (aimFov > 0.0f) ? aimFov : 180.0f;
                if (distC <= maxFov && distC < bestAimDist) {
                    bestAimDist = distC;
                    bestAimHead = headPos;
                    hasAim = true;
                }
            }
        } else if (worldDist > 0.1f && worldDist <= 200.0f) {
            // Advanced 360 Off-Screen Tracker calculation using Project360
            Vector3 projPos{};
            camMat.Project360(headPos, screenW, screenH, projPos);
            float centerX = screenW * 0.5f;
            float centerY = screenH * 0.5f;
            float dirX = projPos.x - centerX;
            float dirY = projPos.y - centerY;
            float len = std::hypot(dirX, dirY);
            if (len > 0.001f) {
                dirX /= len;
                dirY /= len;
                float margin = 45.0f;
                float maxX = screenW - margin;
                float maxY = screenH - margin;
                float scale = 9999.0f;
                if (std::abs(dirX) > 0.001f) {
                    float sx = (dirX > 0) ? (maxX - centerX) / dirX : (margin - centerX) / dirX;
                    if (sx > 0 && sx < scale) scale = sx;
                }
                if (std::abs(dirY) > 0.001f) {
                    float sy = (dirY > 0) ? (maxY - centerY) / dirY : (margin - centerY) / dirY;
                    if (sy > 0 && sy < scale) scale = sy;
                }
                float offX = std::clamp(centerX + dirX * scale, margin, maxX);
                float offY = std::clamp(centerY + dirY * scale, margin, maxY);

                int idx = rendered * 10;
                outRenderData[idx + 0] = offX;
                outRenderData[idx + 1] = offY;
                outRenderData[idx + 2] = 0.0f; // width = 0 designates offscreen
                outRenderData[idx + 3] = 0.0f;
                outRenderData[idx + 4] = dirX; // direction vector X
                outRenderData[idx + 5] = dirY; // direction vector Y
                outRenderData[idx + 6] = worldDist;
                outRenderData[idx + 7] = static_cast<float>(hp);
                outRenderData[idx + 8] = (hp <= 0) ? 1.0f : 0.0f;
                outRenderData[idx + 9] = 1.0f; // 1.0f designates off-screen
                rendered++;
            }
        }
    }

    if (hasAim && (myPos != Vector3::zero()) && outDiag) {
        Vector3 d = bestAimHead - myPos;
        float hyp = std::sqrt(d.x * d.x + d.z * d.z);
        outDiag->bestAimPitch = -std::atan2(d.y, hyp) * (180.0f / 3.14159265f);
        outDiag->bestAimYaw = std::atan2(d.x, d.z) * (180.0f / 3.14159265f);
        outDiag->hasAimTarget = true;
    }

    if (outDiag) outDiag->renderedCount = rendered;
    return rendered;
}
