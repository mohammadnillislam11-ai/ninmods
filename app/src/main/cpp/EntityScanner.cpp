#include "EntityScanner.h"
#include "GameOffsets.h"
#include "DriverClient.h"
#include "UnityTransform.h"
#include <algorithm>
#include <cmath>
#include <vector>
#include <cstdio>
#include <cstring>

static inline bool IsValidPtr(uint64_t ptr, bool is64) {
    if (ptr == 0) return false;
    if (is64) {
        if (ptr < 0x10000000ULL || ptr > 0x00007FFFFFFFF000ULL) return false;
        if ((ptr & 3ULL) != 0ULL) return false;
        // CRITICAL: ARM64 heap/object pointers never have lower 32-bits all zero.
        // Rejects float/integer constants such as 0x40300000000.
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

static inline bool IsElfHeader(int fd, uint64_t handle, uint64_t addr) {
    if (addr == 0) return false;
    unsigned char magic[4] = {0};
    if (!DriverRead(fd, handle, addr, magic, 4)) return false;
    return (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F');
}

static const uint64_t kCandidateFacades[] = {
    0x0A984D48ULL, // Free Fire Normal (com.dts.freefireth) Primary - Bull_Team & treasure
    0x0AD6DC18ULL, // CROSS SRC FFMax
    0x0AD68E18ULL, // CROSS SRC FFMaxInd
    0x0C2132B8ULL, // Legacy Treasure Facade
    0x0AD5B288ULL, // Alternate 1.120
    0x0AD3532CULL, // Alternate 1.119
    0x0A3532CULL
};

static std::vector<uint64_t> GetCandidateBases(int pid, uint64_t handle, int driverFd, uint64_t primaryBase) {
    std::vector<uint64_t> bases;

    // 1. Primary Base from Kernel Driver is ALWAYS tested first
    if (primaryBase != 0 && IsElfHeader(driverFd, handle, primaryBase)) {
        bases.push_back(primaryBase);
    }

    const char* paths[] = {
        "/proc/%d/maps",
        "/data/data/com.ninmods.menu/cache/maps",
        "/data/user/0/com.ninmods.menu/cache/maps",
        "/data/local/tmp/maps",
        nullptr
    };

    uint64_t elf1 = 0, elf2 = 0;
    std::vector<uint64_t> zeroOffsets;

    for (int i = 0; paths[i] != nullptr; ++i) {
        char mapPath[128];
        if (strstr(paths[i], "%d")) {
            snprintf(mapPath, sizeof(mapPath), paths[i], pid);
        } else {
            strncpy(mapPath, paths[i], sizeof(mapPath) - 1);
            mapPath[sizeof(mapPath) - 1] = '\0';
        }

        FILE* fp = fopen(mapPath, "r");
        if (!fp) continue;

        char line[512];
        int zeroCount = 0;
        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, "libil2cpp.so")) {
                unsigned long long start = 0, offset = 0;
                char perms[8] = {0};
                if (sscanf(line, "%llx-%*llx %7s %llx", &start, perms, &offset) == 3) {
                    if (offset == 0 && start != 0) {
                        uint64_t addr = static_cast<uint64_t>(start);
                        if (IsElfHeader(driverFd, handle, addr)) {
                            zeroCount++;
                            zeroOffsets.push_back(addr);
                            if (zeroCount == 1) elf1 = addr;
                            else if (zeroCount == 2) elf2 = addr;
                        }
                    }
                }
            }
        }
        fclose(fp);
        if (!zeroOffsets.empty()) break;
    }

    // Bull_Team pattern: If 2 zero-offset mappings exist, elf2 is the live runtime loaded library image
    if (elf2 != 0) {
        bool exists = false;
        for (uint64_t b : bases) { if (b == elf2) { exists = true; break; } }
        if (!exists) bases.push_back(elf2);
    }
    // Then elf1 (first zero-offset mapping)
    if (elf1 != 0) {
        bool exists = false;
        for (uint64_t b : bases) { if (b == elf1) { exists = true; break; } }
        if (!exists) bases.push_back(elf1);
    }
    // Any remaining zero-offset ELF mappings
    for (uint64_t d : zeroOffsets) {
        bool exists = false;
        for (uint64_t b : bases) { if (b == d) { exists = true; break; } }
        if (!exists) bases.push_back(d);
    }

    if (bases.empty() && primaryBase != 0) bases.push_back(primaryBase);

    return bases;
}

static inline bool IsValidMatch(int fd, uint64_t handle, uint64_t match, bool is64, uint64_t& outLp) {
    if (!IsValidPtr(match, is64)) return false;
    // Check localPlayer at 0xD8 (Bull_Team / CROSS SRC), 0xB0 (treasure), 0x94, or 0x90
    static const uint64_t kLpOffsets[] = {0xD8ULL, 0xB0ULL, 0x94ULL, 0x90ULL};
    for (uint64_t lpOff : kLpOffsets) {
        uint64_t lp = ReadPtr(fd, handle, match + lpOff, is64);
        if (IsValidPtr(lp, is64)) {
            outLp = lp;
            return true;
        }
    }
    // Check match running flag at 0xCC (Bull_Team: 1 = running) or 0xA4 (treasure: 1 = running)
    int running = ReadMem<int>(fd, handle, match + 0xCCULL);
    if (running == 1) return true;
    running = ReadMem<int>(fd, handle, match + 0xA4ULL);
    if (running == 1) return true;
    return false;
}

bool ResolveGamePointers(int pid, uint64_t handle, int driverFd, const GameOffsets* off, bool is64,
                         uint64_t& inOutBase, uint64_t& outFacade, uint64_t& outMatch, uint64_t* outBaseGame) {
    if (!off || handle == 0) return false;

    std::vector<uint64_t> bases = GetCandidateBases(pid, handle, driverFd, inOutBase);
    if (bases.empty() && inOutBase != 0) bases.push_back(inOutBase);
    if (bases.empty()) return false;

    uint64_t bestBase = 0;
    uint64_t bestFacade = 0;
    uint64_t bestMatch = 0;
    uint64_t bestBaseGame = 0;

    for (uint64_t testBase : bases) {
        for (uint64_t cand : kCandidateFacades) {
            uint64_t candFacade = ReadPtr(driverFd, handle, testBase + cand, is64);
            if (!IsValidPtr(candFacade, is64)) continue;

            // Structure A: candFacade is Il2CppClass* (holds static fields)
            uint64_t stClass = ReadPtr(driverFd, handle, candFacade + off->staticClassField, is64);
            if (IsValidPtr(stClass, is64)) {
                uint64_t ptr0 = ReadPtr(driverFd, handle, stClass + 0x0ULL, is64);
                uint64_t ptr8 = ReadPtr(driverFd, handle, stClass + 0x8ULL, is64);

                uint64_t liveMatch = 0;
                uint64_t liveBaseGame = 0;
                uint64_t foundLp = 0;

                // Path 1: ninmods_offsets.h - stClass + 0x8 is CurrentMatchGame directly
                if (IsValidMatch(driverFd, handle, ptr8, is64, foundLp)) {
                    liveMatch = ptr8;
                    liveBaseGame = IsValidPtr(ptr0, is64) ? ptr0 : stClass;
                }
                // Path 2: CROSS SRC / Bull_Team - ptr0 is BaseGame, and BaseGame + 0x90 is Match
                if (liveMatch == 0 && IsValidPtr(ptr0, is64)) {
                    uint64_t mFrom0 = ReadPtr(driverFd, handle, ptr0 + 0x90ULL, is64);
                    if (!IsValidPtr(mFrom0, is64)) mFrom0 = ReadPtr(driverFd, handle, ptr0 + 0x930ULL, is64);
                    if (IsValidMatch(driverFd, handle, mFrom0, is64, foundLp)) {
                        liveMatch = mFrom0;
                        liveBaseGame = ptr0;
                    }
                }
                // Path 3: Bull_Team Server.cpp line 255 - ptr8 is BaseGame, and ptr8 + 0x90 is Match
                if (liveMatch == 0 && IsValidPtr(ptr8, is64)) {
                    uint64_t mFrom8 = ReadPtr(driverFd, handle, ptr8 + 0x90ULL, is64);
                    if (!IsValidPtr(mFrom8, is64)) mFrom8 = ReadPtr(driverFd, handle, ptr8 + 0x930ULL, is64);
                    if (IsValidMatch(driverFd, handle, mFrom8, is64, foundLp)) {
                        liveMatch = mFrom8;
                        liveBaseGame = ptr8;
                    }
                }
                // Path 4: Direct match offset on stClass
                if (liveMatch == 0) {
                    uint64_t mDirect = ReadPtr(driverFd, handle, stClass + off->currentMatch, is64);
                    if (!IsValidPtr(mDirect, is64) && off->currentMatchFallback != 0) {
                        mDirect = ReadPtr(driverFd, handle, stClass + off->currentMatchFallback, is64);
                    }
                    if (IsValidMatch(driverFd, handle, mDirect, is64, foundLp)) {
                        liveMatch = mDirect;
                        liveBaseGame = IsValidPtr(ptr0, is64) ? ptr0 : stClass;
                    }
                }
                // Path 5: Treasure pattern - stClass itself holds LocalPlayer at 0x94 and Dictionary at 0x68
                if (liveMatch == 0) {
                    uint64_t treasureLp = ReadPtr(driverFd, handle, stClass + 0x94ULL, is64);
                    if (IsValidPtr(treasureLp, is64)) {
                        liveMatch = stClass;
                        liveBaseGame = stClass;
                        foundLp = treasureLp;
                    }
                }

                if (IsValidPtr(liveMatch, is64)) {
                    inOutBase = testBase;
                    outFacade = candFacade;
                    outMatch = liveMatch;
                    if (outBaseGame) *outBaseGame = liveBaseGame;
                    return true;
                }

                // If not in match yet (e.g. in Lobby), save bestFacade and bestBaseGame
                if ((IsValidPtr(ptr0, is64) || IsValidPtr(ptr8, is64)) && bestFacade == 0) {
                    bestBase = testBase;
                    bestFacade = candFacade;
                    bestMatch = 0;
                    bestBaseGame = IsValidPtr(ptr0, is64) ? ptr0 : (IsValidPtr(ptr8, is64) ? ptr8 : stClass);
                }
            }
        }
    }

    if (bestFacade != 0) {
        inOutBase = bestBase;
        outFacade = bestFacade;
        outMatch = bestMatch;
        if (outBaseGame) *outBaseGame = bestBaseGame;
        return true;
    }

    return false;
}

static bool ReadCamMatrix(int fd, uint64_t handle, const GameOffsets* off, uint64_t lp, bool is64, Matrix4x4& outM, uint64_t stClass = 0) {
    if (!off) return false;
    if (lp != 0) {
        static const uint64_t kFollowOffsets[] = {0x638ULL, 0x698ULL, 0x3E8ULL, 0x38ULL, 0x5B0ULL};
        uint64_t follow = 0;
        for (uint64_t fOff : kFollowOffsets) {
            follow = ReadPtr(fd, handle, lp + fOff, is64);
            if (IsValidPtr(follow, is64)) break;
        }
        if (!IsValidPtr(follow, is64)) follow = ReadPtr(fd, handle, lp + off->followCamera, is64);
        if (!IsValidPtr(follow, is64)) follow = ReadPtr(fd, handle, lp + off->followCameraFallback, is64);

        if (IsValidPtr(follow, is64)) {
            uint64_t cam = ReadPtr(fd, handle, follow + off->camera, is64);
            if (!IsValidPtr(cam, is64)) cam = ReadPtr(fd, handle, follow + off->cameraFallback, is64);
            if (!IsValidPtr(cam, is64)) cam = ReadPtr(fd, handle, follow + 0x28ULL, is64);
            if (!IsValidPtr(cam, is64)) cam = follow;
            if (IsValidPtr(cam, is64)) {
                uint64_t intPtr = ReadPtr(fd, handle, cam + off->intPtrCam, is64);
                if (IsValidPtr(intPtr, is64)) {
                    static const uint64_t kMatrixOffsets[] = {0xD8ULL, 0x100ULL, 0xBCULL, 0xE8ULL, 0x10ULL, 0x38ULL};
                    for (uint64_t mOff : kMatrixOffsets) {
                        outM = ReadMem<Matrix4x4>(fd, handle, intPtr + mOff);
                        if (outM.m[0] != 0.0f || outM.m[5] != 0.0f || outM.m[10] != 0.0f) return true;
                    }
                }
            }
        }
    }

    // Direct matrix from staticClass (from treasure Offsets::ViewMatrix = 0xBC or 0x100)
    if (IsValidPtr(stClass, is64)) {
        static const uint64_t kStaticMatOffsets[] = {0xBCULL, 0x100ULL, 0xD8ULL, 0xE8ULL};
        for (uint64_t smOff : kStaticMatOffsets) {
            outM = ReadMem<Matrix4x4>(fd, handle, stClass + smOff);
            if (outM.m[0] != 0.0f || outM.m[5] != 0.0f || outM.m[10] != 0.0f) return true;
        }
    }

    return false;
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
    static uint64_t s_cachedBaseGame = 0;
    static uint64_t s_cachedDict = 0;
    static uint64_t s_cachedValuesBase = 0;
    static int s_cacheFrames = 0;

    if (s_cachedBase != base) {
        s_cachedBase = base;
        s_cachedFacade = 0;
        s_cachedMatch = 0;
        s_cachedBaseGame = 0;
        s_cachedDict = 0;
        s_cachedValuesBase = 0;
        s_cacheFrames = 0;
    }

    uint64_t facade = s_cachedFacade;
    uint64_t match = s_cachedMatch;
    uint64_t baseGame = s_cachedBaseGame;

    bool needRefresh = (facade == 0 || match == 0 || (++s_cacheFrames > 60));
    if (!needRefresh) {
        uint64_t testLp = ReadPtr(driverFd, handle, match + off->localPlayer, is64);
        if (!IsValidPtr(testLp, is64) && off->localPlayerFallback != 0) {
            testLp = ReadPtr(driverFd, handle, match + off->localPlayerFallback, is64);
        }
        if (!IsValidPtr(testLp, is64)) {
            needRefresh = true;
        }
    }

    if (needRefresh) {
        s_cacheFrames = 0;
        uint64_t resBase = base;
        uint64_t resFacade = 0;
        uint64_t resMatch = 0;
        uint64_t resBaseGame = 0;

        bool resolved = ResolveGamePointers(pid, handle, driverFd, off, is64, resBase, resFacade, resMatch, &resBaseGame);
        if (resolved) {
            base = resBase;
            facade = resFacade;
            match = resMatch;
            baseGame = resBaseGame;
            s_cachedBase = base;
            s_cachedFacade = facade;
            s_cachedMatch = match;
            s_cachedBaseGame = baseGame;
            if (outDiag) outDiag->libBase = base;
        } else {
            s_cachedFacade = 0;
            s_cachedMatch = 0;
            s_cachedBaseGame = 0;
            s_cachedDict = 0;
            s_cachedValuesBase = 0;
            if (outDiag) {
                outDiag->gameFacade = 0;
                outDiag->matchPtr = 0;
            }
            return 0;
        }
    }

    if (outDiag) {
        outDiag->gameFacade = facade;
        outDiag->matchPtr = match;
    }

    if (match == 0) {
        // Fallback: If match was 0 in lobby/transition, check if baseGame holds localPlayer directly (treasure pattern)
        if (IsValidPtr(baseGame, is64)) {
            uint64_t lpTest = ReadPtr(driverFd, handle, baseGame + off->localPlayer, is64);
            if (!IsValidPtr(lpTest, is64) && off->localPlayerFallback != 0) {
                lpTest = ReadPtr(driverFd, handle, baseGame + off->localPlayerFallback, is64);
            }
            if (IsValidPtr(lpTest, is64)) {
                match = baseGame;
            }
        }
    }

    if (match == 0) {
        return 0;
    }

    static uint64_t s_savedLocal = 0;
    uint64_t liveLocal = ReadPtr(driverFd, handle, match + off->localPlayer, is64);
    if (!IsValidPtr(liveLocal, is64) && off->localPlayerFallback != 0) {
        liveLocal = ReadPtr(driverFd, handle, match + off->localPlayerFallback, is64);
    }
    if (!IsValidPtr(liveLocal, is64) && IsValidPtr(baseGame, is64)) {
        liveLocal = ReadPtr(driverFd, handle, baseGame + off->localPlayer, is64);
        if (!IsValidPtr(liveLocal, is64) && off->localPlayerFallback != 0) {
            liveLocal = ReadPtr(driverFd, handle, baseGame + off->localPlayerFallback, is64);
        }
    }
    if (IsValidPtr(liveLocal, is64)) s_savedLocal = liveLocal;
    uint64_t localPlayer = IsValidPtr(liveLocal, is64) ? liveLocal : s_savedLocal;
    if (outDiag) outDiag->localPlayer = localPlayer;

    Matrix4x4 camMat{};
    uint64_t stClass = IsValidPtr(baseGame, is64) ? baseGame : (facade ? ReadPtr(driverFd, handle, facade + off->staticClassField, is64) : 0);
    bool hasMatrix = ReadCamMatrix(driverFd, handle, off, localPlayer, is64, camMat, stClass);
    if (outDiag) outDiag->matrixValid = hasMatrix;
    if (!hasMatrix) return 0;

    uint64_t dict = s_cachedDict;
    int dictCount = dict ? ReadMem<int>(driverFd, handle, dict + off->dictCount) : 0;
    if (dictCount <= 0 || dictCount > 200) {
        uint64_t dictCandidates[] = {
            baseGame ? ReadPtr(driverFd, handle, baseGame + off->entityList, is64) : 0,         // BaseGame + 0xC0 (Bull_Team / CROSS SRC)
            match ? ReadPtr(driverFd, handle, match + off->entityList, is64) : 0,               // Match + 0xC0
            baseGame ? ReadPtr(driverFd, handle, baseGame + 0x68ULL, is64) : 0,                 // Treasure Dictionary (0x68)
            match ? ReadPtr(driverFd, handle, match + 0x68ULL, is64) : 0,
            baseGame ? ReadPtr(driverFd, handle, baseGame + off->entityListFallback, is64) : 0, // 0x148
            match ? ReadPtr(driverFd, handle, match + off->entityListFallback, is64) : 0
        };
        for (uint64_t candD : dictCandidates) {
            if (!IsValidPtr(candD, is64)) continue;
            int c = ReadMem<int>(driverFd, handle, candD + off->dictCount);
            if (c <= 0 || c > 200) {
                c = ReadMem<int>(driverFd, handle, candD + 0x18ULL); // Unity Dictionary size fallback
            }
            if (c > 0 && c <= 200) {
                dict = candD;
                dictCount = c;
                break;
            }
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
        // Fallback: treasure monoArray<V>* values format (stride 0x8)
        if (!IsValidPtr(entityPtr, is64)) {
            entityPtr = ReadPtr(driverFd, handle, valuesPtr + (i * 0x8ULL), is64);
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

        // Fallback: Direct position from Bull_Team EnemyPosForce (0x900) or CROSS SRC Position (0x90)
        if (headPos == Vector3::zero() && toePos == Vector3::zero()) {
            Vector3 directPos = ReadMem<Vector3>(driverFd, handle, entityPtr + 0x900ULL);
            if (directPos.y <= -500.0f || directPos.y >= 5000.0f || (directPos.x == 0.0f && directPos.y == 0.0f)) {
                directPos = ReadMem<Vector3>(driverFd, handle, entityPtr + 0x90ULL);
            }
            if (directPos.y > -500.0f && directPos.y < 5000.0f && (directPos.x != 0.0f || directPos.z != 0.0f)) {
                toePos = directPos;
                headPos = directPos + Vector3(0.0f, 1.9f, 0.0f);
            }
        }

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
