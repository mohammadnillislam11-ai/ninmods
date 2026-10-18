#pragma once
#include <cstdint>

struct GameOffsets {
  uint64_t gameFacade;
  uint64_t gameFacadeInd;
  uint64_t staticClassField;
  uint64_t currentMatch;
  uint64_t currentMatchFallback;
  uint64_t matchRunning;
  uint64_t entityList;
  uint64_t entityListFallback;
  uint64_t localPlayer;
  uint64_t dictValues;
  uint64_t dictCount;
  uint64_t entryValue;
  uint64_t entryStride;
  uint64_t arrayHeader;
  uint64_t followCamera;
  uint64_t followCameraFallback;
  uint64_t camera;
  uint64_t cameraFallback;
  uint64_t intPtrCam;
  uint64_t camMatrix;
  uint64_t camMatrixFallback;
  uint64_t playerData;
  uint64_t playerDataCur;
  uint64_t playerHp;
  uint64_t curHpPtr;
  uint64_t playerIsDead;
  uint64_t boneHead;
  uint64_t boneHip;
  uint64_t boneRoot;
  uint64_t aimRotation;
  uint64_t aimRotationFallback;
  uint64_t userControl;
  uint64_t userControlFallback;
  uint64_t attributes;
  uint64_t forceSpeed;
  uint64_t forceSpeedFallback;
  uint64_t ghostHack;
  uint64_t flyState;
  uint64_t flyStateFallback;
  uint64_t isFiring;
  uint64_t isFiringFallback;
  uint64_t aimInfo;
  uint64_t uiInGameScene;
  uint64_t bigMapCtrl;
  uint64_t mapContentCtrl;
  uint64_t localMapMarkCtrl;
  uint64_t markPosition;
};

// 1. Free Fire MAX Offsets (Updated from ninmods_offsets.h reflection dump)
static const GameOffsets kOffsetsFFMax = {
    .gameFacade = 0x0AD6DC18ULL,
    .gameFacadeInd = 0x0AD68E18ULL,
    .staticClassField = 0xB8ULL,
    .currentMatch = 0x8ULL,                // OFFSET_GameFacade_CurrentMatchGame (0x8ULL)
    .currentMatchFallback = 0x90ULL,        // Legacy match fallback
    .matchRunning = 0x209ULL,              // OFFSET_GameFacade_IsMatchStarted (0x209ULL)
    .entityList = 0x148ULL,
    .entityListFallback = 0xC0ULL,
    .localPlayer = 0xD8ULL,
    .dictValues = 0x18ULL,
    .dictCount = 0x20ULL,
    .entryValue = 0x10ULL,
    .entryStride = 0x18ULL,
    .arrayHeader = 0x20ULL,
    .followCamera = 0x3E8ULL,              // OFFSET_Player_MainCameraTransform (0x3E8ULL)
    .followCameraFallback = 0x698ULL,      // OFFSET_Player_EPDCPFACIPG (0x698ULL)
    .camera = 0x30ULL,
    .cameraFallback = 0x18ULL,
    .intPtrCam = 0x10ULL,
    .camMatrix = 0x100ULL,
    .camMatrixFallback = 0xE8ULL,
    .playerData = 0x70ULL,
    .playerDataCur = 0x10ULL,
    .playerHp = 0x18ULL,
    .curHpPtr = 0x20ULL,
    .playerIsDead = 0x7CULL,
    .boneHead = 0x6A8ULL,                  // OFFSET_Player_COBFGNOIPMF (0x6A8ULL)
    .boneHip = 0x6B0ULL,                   // OFFSET_Player_FFFPCADFFGA (0x6B0ULL)
    .boneRoot = 0x6D0ULL,                  // OFFSET_Player_EJJBPONNECG (0x6D0ULL)
    .aimRotation = 0x1A8CULL,              // OFFSET_Player_m_CurrentAimRotation (0x1A8CULL)
    .aimRotationFallback = 0x61CULL,       // Legacy aimRotation fallback
    .userControl = 0xAA0ULL,               // OFFSET_Player_BlockUserControlExceptCancel (0xAA0ULL)
    .userControlFallback = 0x4D8ULL,
    .attributes = 0x770ULL,
    .forceSpeed = 0x560ULL,                // OFFSET_Player_Speed (0x560ULL)
    .forceSpeedFallback = 0x3C8ULL,
    .ghostHack = 0x814ULL,
    .flyState = 0x1F14ULL,                 // OFFSET_Player_FlyUPDistance (0x1F14ULL)
    .flyStateFallback = 0x2098ULL,         // OFFSET_Player_OIENPPNGDMA (0x2098ULL)
    .isFiring = 0x259ULL,                  // OFFSET_Player_UGCStartFiring (0x259ULL)
    .isFiringFallback = 0x850ULL,
    .aimInfo = 0xE60ULL,                   // OFFSET_Player_LastAimingInfoFromWeaponAdjusted (0xE60ULL)
    .uiInGameScene = 0x10ULL,
    .bigMapCtrl = 0x458ULL,
    .mapContentCtrl = 0xA8ULL,
    .localMapMarkCtrl = 0xF8ULL,
    .markPosition = 0xB0ULL};

// 2. Free Fire Global / Normal Offsets (Updated from ninmods_offsets.h reflection dump)
static const GameOffsets kOffsetsFFGlobal = {
    .gameFacade = 0x0AD6DC18ULL,
    .gameFacadeInd = 0x0AD68E18ULL,
    .staticClassField = 0xB8ULL,
    .currentMatch = 0x8ULL,                // OFFSET_GameFacade_CurrentMatchGame (0x8ULL)
    .currentMatchFallback = 0x90ULL,        // Legacy match fallback
    .matchRunning = 0x209ULL,              // OFFSET_GameFacade_IsMatchStarted (0x209ULL)
    .entityList = 0x148ULL,
    .entityListFallback = 0xC0ULL,
    .localPlayer = 0xD8ULL,
    .dictValues = 0x18ULL,
    .dictCount = 0x20ULL,
    .entryValue = 0x10ULL,
    .entryStride = 0x18ULL,
    .arrayHeader = 0x20ULL,
    .followCamera = 0x3E8ULL,              // OFFSET_Player_MainCameraTransform (0x3E8ULL)
    .followCameraFallback = 0x698ULL,      // OFFSET_Player_EPDCPFACIPG (0x698ULL)
    .camera = 0x30ULL,
    .cameraFallback = 0x18ULL,
    .intPtrCam = 0x10ULL,
    .camMatrix = 0x100ULL,
    .camMatrixFallback = 0xE8ULL,
    .playerData = 0x70ULL,
    .playerDataCur = 0x10ULL,
    .playerHp = 0x18ULL,
    .curHpPtr = 0x20ULL,
    .playerIsDead = 0x7CULL,
    .boneHead = 0x6A8ULL,                  // OFFSET_Player_COBFGNOIPMF (0x6A8ULL)
    .boneHip = 0x6B0ULL,                   // OFFSET_Player_FFFPCADFFGA (0x6B0ULL)
    .boneRoot = 0x6D0ULL,                  // OFFSET_Player_EJJBPONNECG (0x6D0ULL)
    .aimRotation = 0x1A8CULL,              // OFFSET_Player_m_CurrentAimRotation (0x1A8CULL)
    .aimRotationFallback = 0x61CULL,       // Legacy aimRotation fallback
    .userControl = 0xAA0ULL,               // OFFSET_Player_BlockUserControlExceptCancel (0xAA0ULL)
    .userControlFallback = 0x364ULL,
    .attributes = 0x500ULL,
    .forceSpeed = 0x560ULL,                // OFFSET_Player_Speed (0x560ULL)
    .forceSpeedFallback = 0x2A8ULL,
    .ghostHack = 0x58CULL,
    .flyState = 0x1F14ULL,                 // OFFSET_Player_FlyUPDistance (0x1F14ULL)
    .flyStateFallback = 0x2098ULL,         // OFFSET_Player_OIENPPNGDMA (0x2098ULL)
    .isFiring = 0x259ULL,                  // OFFSET_Player_UGCStartFiring (0x259ULL)
    .isFiringFallback = 0x5C8ULL,
    .aimInfo = 0x970ULL,                   // OFFSET_Player_FMLJAIMHDOE (0x970ULL)
    .uiInGameScene = 0x10ULL,
    .bigMapCtrl = 0x458ULL,
    .mapContentCtrl = 0xA8ULL,
    .localMapMarkCtrl = 0xF8ULL,
    .markPosition = 0xB0ULL};

inline const GameOffsets *GetActiveOffsets(bool isMax, bool /*is64*/ = true) {
  return isMax ? &kOffsetsFFMax : &kOffsetsFFGlobal;
}
