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
  uint64_t p2BaseGame;
  uint64_t currentGame;
  uint64_t currentMatchGame;
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
  uint64_t isMatchStarted;
  uint64_t localPlayerUserId;
  uint64_t isFrozenKnockDown;
  uint64_t fixedDeltaTime;
};

// Free Fire Global Offsets (com.dts.freefireth) - Built from Power Project & ninmods_offsets.h
static const GameOffsets kOffsetsFFGlobal = {
    .gameFacade = 0x0A984D48ULL,           // Power: GameFacade = 0xA984D48
    .gameFacadeInd = 0x0C2132B8ULL,        // Alternate Facade fallback (0x0C2132B8)
    .staticClassField = 0xB8ULL,           // Power: FacadeP2 = 0xB8
    .p2BaseGame = 0xBULL,                  // Power: P2BaseGame = 0xB
    .currentGame = 0x0ULL,                 // ninmods_offsets.h: OFFSET_GameFacade_CurrentGame 0x0ULL
    .currentMatchGame = 0x8ULL,            // ninmods_offsets.h: OFFSET_GameFacade_CurrentMatchGame 0x8ULL
    .currentMatch = 0x930ULL,              // Power: BaseGameMatch = 0x930
    .currentMatchFallback = 0x90ULL,       // Alternate match fallback offset
    .matchRunning = 0xCCULL,               // Power: MatchIsRunning = 0xCC (1 = in game)
    .entityList = 0xC0ULL,                 // Power: BaseGameDictionary = 0xC0
    .entityListFallback = 0x148ULL,        // Alternate entity list offset
    .localPlayer = 0xD8ULL,                // Power: MatchLocalPlayer = 0xD8
    .localPlayerFallback = 0x94ULL,        // Match -> LocalPlayer fallback
    .dictValues = 0x18ULL,                 // Power: MonoDictionary getValues() = +0x18
    .dictCount = 0x20ULL,                  // Power: MonoDictionary getNumValues() = +0x20
    .entryValue = 0x10ULL,                 // Power: EntryEnemyList = 0x10
    .entryStride = 0x18ULL,                // Power: DictionaryEntrySize = 0x18
    .arrayHeader = 0x20ULL,                // Power: Array header = +0x20
    .followCamera = 0x38ULL,               // Power: FollowCamera = 0x38
    .followCameraFallback = 0x638ULL,      // FollowCamera fallback
    .camera = 0x30ULL,                     // Power: FollowCameraObj = 0x30
    .cameraFallback = 0x18ULL,             // Camera fallback
    .intPtrCam = 0x10ULL,                  // Power: CameraIntPtr = 0x10
    .camMatrix = 0x10ULL,                  // Power: OFFSET_CAMERA_MATRIX_STR "0x10"
    .camMatrixFallback = 0x100ULL,         // ViewMatrix fallback (0x100 / 0xBC)
    .playerData = 0x70ULL,                 // PlayerData pointer
    .playerDataCur = 0x10ULL,              // ReplicationData pointer
    .playerHp = 0x18ULL,                   // Health value offset
    .curHpPtr = 0x20ULL,                   // ReplicationItems pointer
    .playerIsDead = 0x7CULL,               // IsDead flag offset
    .boneHead = 0x6478ULL,                 // Power: Head = 0x6478
    .boneHip = 0x6548ULL,                  // Power: Spine = 0x6548
    .boneRoot = 0x60ULL,                   // Power: Feet = 0x60
    .aimRotation = 0x5BCULL,               // Power: PlayerRotation = 0x5BC
    .aimRotationFallback = 0x61CULL,       // AimRotation fallback
    .userControl = 0x4D8ULL,               // UserControl pointer
    .userControlFallback = 0xAA0ULL,       // UserControl fallback
    .attributes = 0x770ULL,                // Player attributes pointer
    .forceSpeed = 0x3C8ULL,                // Force speed multiplier offset
    .forceSpeedFallback = 0x560ULL,        // Force speed fallback
    .ghostHack = 0x814ULL,                 // GhostHack state offset
    .flyState = 0x1F14ULL,                 // FlyState offset
    .flyStateFallback = 0x2098ULL,         // FlyState fallback
    .isFiring = 0x7E0ULL,                  // Power: IsFiring = 0x7E0
    .isFiringFallback = 0x850ULL,          // IsFiring fallback
    .aimInfo = 0x80ULL,                    // Power: AimingInfo = 0x80
    .uiInGameScene = 0x10ULL,              // UI scene offset
    .bigMapCtrl = 0x458ULL,                // BigMap controller offset
    .mapContentCtrl = 0xA8ULL,             // MapContent controller offset
    .localMapMarkCtrl = 0xF8ULL,           // LocalMapMark controller offset
    .markPosition = 0xB0ULL,               // Mark position offset
    .isMatchStarted = 0x209ULL,            // ninmods_offsets.h: OFFSET_GameFacade_IsMatchStarted 0x209ULL
    .localPlayerUserId = 0x58ULL,          // ninmods_offsets.h: OFFSET_GameFacade_LocalPlayerUserID 0x58ULL
    .isFrozenKnockDown = 0xB0ULL,          // ninmods_offsets.h: OFFSET_Player_IsFrozenKnockDown 0xb0ULL
    .fixedDeltaTime = 0x2CULL              // ninmods_offsets.h: OFFSET_TimeService_m_FixedDeltaTime 0x2cULL
};

inline const GameOffsets *GetActiveOffsets(bool /*isMax*/ = false, bool /*is64*/ = true) {
  return &kOffsetsFFGlobal;
}
