#pragma once
#include <cstdint>

#define NIN_SOCKET_NAME "nin_X9K2q"
#define NIN_TCP_PORT 49221
#define NIN_MAGIC 0x4E494E4D // 'NINM'
#define NIN_MAX_ENTITIES 64
#define NIN_ENTITY_STRIDE 10

enum NinCommand : uint32_t {
    CMD_PING = 1,
    CMD_ATTACH = 2,
    CMD_SCAN_ESP = 3,
    CMD_WRITE_AIM = 4,
    CMD_COMBAT_MOD = 5,
    CMD_GET_BASE = 6,
    CMD_DISCONNECT = 7,
};

struct NinRequest {
    uint32_t magic;
    uint32_t cmd;
    int32_t pid;
    uint32_t isMax;
    float screenW;
    float screenH;
    float aimFov;
    uint64_t localPlayer;
    float targetPitch;
    float targetYaw;
    float smooth;
    uint32_t combatFlags; // 1: fly, 2: speed, 4: ghost, 8: wallbug
    float flyHeight;
    float speedMult;
};

struct NinResponse {
    uint32_t magic;
    int32_t status; // 0 = SUCCESS, -1 = ERROR
    uint32_t renderedCount;
    float entities[NIN_MAX_ENTITIES * NIN_ENTITY_STRIDE];
    uint64_t diag[10];
};
