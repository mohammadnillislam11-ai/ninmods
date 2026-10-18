#pragma once
#include <stdint.h>
#include <cstdint>

#ifndef UINT64_C
typedef unsigned long long uint64_t;
typedef unsigned int uint32_t;
typedef unsigned short uint16_t;
typedef unsigned char uint8_t;
#endif

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
  uint64_t localPlayerFallback;
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

// Free Fire Normal Offsets (com.dts.freefireth) - Verified from Bull_Team & treasure
static const GameOffsets kOffsetsFFGlobal = {
    .gameFacade = 0x0A984D48ULL,           // Bull_Team & treasure: GameFacade = 0xA984D48
    .gameFacadeInd = 0x0C2132B8ULL,        // Legacy treasure facade fallback
    .staticClassField = 0xB8ULL,           // Bull_Team: FacadeP2 = 0xB8
    .currentMatch = 0x90ULL,               // Bull_Team: BaseGameMatch = 0x90
    .currentMatchFallback = 0x930ULL,      // Bull_Team offset.h: 0x930
    .matchRunning = 0xCCULL,               // Bull_Team: MatchIsRunning = 0xCC (1 = in match)
    .entityList = 0xC0ULL,                 // Bull_Team: BaseGameDictionary = 0xC0
    .entityListFallback = 0x148ULL,
    .localPlayer = 0xD8ULL,                // Bull_Team: MatchLocalPlayer = 0xD8
    .localPlayerFallback = 0x94ULL,        // treasure: LocalPlayer = 0x94
    .dictValues = 0x18ULL,
    .dictCount = 0x20ULL,
    .entryValue = 0x10ULL,                 // Bull_Team: EntryEnemyList = 0x10
    .entryStride = 0x18ULL,                // Bull_Team: DictionaryEntrySize = 0x18
    .arrayHeader = 0x20ULL,
    .followCamera = 0x638ULL,              // Bull_Team Server.cpp line 275: 0x638
    .followCameraFallback = 0x698ULL,      // FollowCamera fallback
    .camera = 0x30ULL,                     // Bull_Team: FollowCameraObj = 0x30
    .cameraFallback = 0x18ULL,
    .intPtrCam = 0x10ULL,                  // Bull_Team: CameraIntPtr = 0x10
    .camMatrix = 0x100ULL,                 // Bull_Team: Matrix = 0x100
    .camMatrixFallback = 0xBCULL,          // treasure: ViewMatrix = 0xBC
    .playerData = 0x70ULL,                 // Bull_Team: DataPool = 0x70
    .playerDataCur = 0x10ULL,              // Bull_Team: ReplicationData = 0x10
    .playerHp = 0x18ULL,                   // Bull_Team: Health = 0x18
    .curHpPtr = 0x20ULL,                   // Bull_Team: ReplicationItems = 0x20
    .playerIsDead = 0x7CULL,               // Bull_Team: EnemyCheck7C = 0x7C
    .boneHead = 0x6A8ULL,                  // HeadBone = 0x6A8
    .boneHip = 0x6B0ULL,
    .boneRoot = 0x6D0ULL,
    .aimRotation = 0x61CULL,
    .aimRotationFallback = 0x1A8CULL,
    .userControl = 0x4D8ULL,
    .userControlFallback = 0xAA0ULL,
    .attributes = 0x770ULL,
    .forceSpeed = 0x3C8ULL,
    .forceSpeedFallback = 0x560ULL,
    .ghostHack = 0x814ULL,
    .flyState = 0x1F14ULL,
    .flyStateFallback = 0x2098ULL,
    .isFiring = 0x7E0ULL,                  // Bull_Team: IsFiring = 0x7E0
    .isFiringFallback = 0x850ULL,
    .aimInfo = 0x80ULL,                    // Bull_Team: AimingInfo = 0x80
    .uiInGameScene = 0x10ULL,
    .bigMapCtrl = 0x458ULL,
    .mapContentCtrl = 0xA8ULL,
    .localMapMarkCtrl = 0xF8ULL,
    .markPosition = 0xB0ULL
};

inline const GameOffsets *GetActiveOffsets(bool /*isMax*/ = false, bool /*is64*/ = true) {
  return &kOffsetsFFGlobal;
}
