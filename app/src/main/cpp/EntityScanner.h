#pragma once
#include <cstdint>
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
