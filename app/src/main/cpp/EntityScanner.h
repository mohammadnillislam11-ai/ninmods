#pragma once
#include <stdint.h>
#include <cstdint>

#ifndef UINT64_C
typedef unsigned long long uint64_t;
typedef unsigned int uint32_t;
typedef unsigned short uint16_t;
typedef unsigned char uint8_t;
#endif

#include "Unity/Vector3.hpp"
#include "Unity/Matrix4x4.h"

struct ScanDiagnostics {
    uint64_t libBase;
    uint64_t gameFacade;
    uint64_t matchPtr;
    uint64_t localPlayer;
    int dictCount;
    int renderedCount;
    bool matrixValid;
    float bestAimPitch;
    float bestAimYaw;
    bool hasAimTarget;
};

int ScanAndComputeEsp(
    int pid,
    uint64_t handle,
    int driverFd,
    bool isMax,
    float screenW,
    float screenH,
    float* outRenderData,
    int maxRender,
    ScanDiagnostics* outDiag,
    float aimFov = 0.0f
);

struct GameOffsets;

bool ResolveGamePointers(
    int pid,
    uint64_t handle,
    int driverFd,
    const GameOffsets* off,
    bool is64,
    uint64_t& inOutBase,
    uint64_t& outFacade,
    uint64_t& outMatch,
    uint64_t* outBaseGame = nullptr
);
