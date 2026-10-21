#pragma once
#include <cstdint>

// Offsets for Free Fire & Free Fire Max (Synchronized with NINMODS & CROSS SRC)
namespace Offsets {
    // Player Component / Bone Offsets (Prioritized for Normal Free Fire)
    constexpr uintptr_t Head = 0x6478;
    constexpr uintptr_t HeadFallback = 0x6A8;
    constexpr uintptr_t HeadOld = 0x640;

    constexpr uintptr_t Spine = 0x6548;
    constexpr uintptr_t SpineFallback = 0x6B0;

    constexpr uintptr_t Feet = 0x60;
    constexpr uintptr_t FeetFallback = 0x6D0;

    constexpr uintptr_t MainCamera = 0x38;
    
    // Transform Offsets
    constexpr uintptr_t TransformObj = 0x110;
    constexpr uintptr_t TransformIndex = 0x40;
    constexpr uintptr_t TransformMatrix = 0x38;
    constexpr uintptr_t MatrixList = 0x108;
    constexpr uintptr_t MatrixIndices = 0x20;
    constexpr uintptr_t MatrixDataSize = 0x30;
    
    // Aiming Info
    // Newer Free Fire: AimingInfo = 0xE58, older was 0x80
    constexpr uintptr_t AimingInfo = 0xE58;
    constexpr uintptr_t AimingInfoOld = 0x80;
    constexpr uintptr_t AimingStartPos = 0x4C;
    constexpr uintptr_t AimingDirection = 0x430;
    
    // Inventory
    // Newer Free Fire: InventoryManager = 0x748, older was 0x6E8
    constexpr uintptr_t InventoryManager = 0x748;
    constexpr uintptr_t InventoryManagerOld = 0x6E8;
    constexpr uintptr_t InventoryItem = 0xA0;
    constexpr uintptr_t ItemData = 0x28;
    constexpr uintptr_t WeaponId = 0x40;
    
    // Game Structure/Match - Prioritized for Normal Free Fire (com.dts.freefireth)
    constexpr uintptr_t GameFacade = 0xA984D48;
    constexpr uintptr_t GameFacadeAlt1 = 0xAD6DC18;
    constexpr uintptr_t GameFacadeAlt2 = 0xAD68E18;
    constexpr uintptr_t GameFacadeGlobal = 0x0C2132B8ULL;

    constexpr uintptr_t FacadeP2 = 0xB8;
    constexpr uintptr_t P2BaseGame = 0xB;
    constexpr uintptr_t P2BaseGameAlt = 0x0;
    constexpr uintptr_t P2BaseGameZero = 0x8;

    constexpr uintptr_t BaseGameMatch = 0x930;
    constexpr uintptr_t BaseGameMatchAlt = 0x90;

    constexpr uintptr_t BaseGameTimeService = 0x20;
    constexpr uintptr_t TimeServiceSpeed = 0x2C;

    constexpr uintptr_t MatchIsRunning = 0xCC;
    constexpr uintptr_t MatchLocalPlayer = 0xD8;
    constexpr uintptr_t MatchLocalPlayerAlt = 0x94;

    constexpr uintptr_t BaseGameDictionary = 0xC0;
    constexpr uintptr_t BaseGameDictionaryAlt = 0x148;
    constexpr uintptr_t DictionaryEntrySize = 0x18;
    constexpr uintptr_t EntryEnemyList = 0x10;
    
    // Camera
    constexpr uintptr_t FollowCamera = 0x38;
    constexpr uintptr_t FollowCameraAlt = 0x698;
    constexpr uintptr_t FollowCameraOld = 0x638;

    constexpr uintptr_t FollowCameraObj = 0x30;
    constexpr uintptr_t CameraIntPtr = 0x10;
    
    // Local Player
    // Newer Free Fire: IsFiring = 0x850 (Older was 0x7E0)
    constexpr uintptr_t IsFiring = 0x850;
    constexpr uintptr_t IsFiringOld = 0x7E0;

    // Newer Free Fire: PlayerRotation = 0x61C (Older was 0x5BC)
    constexpr uintptr_t PlayerRotation = 0x61C;
    constexpr uintptr_t PlayerRotationOld = 0x5BC;

    constexpr uintptr_t WeaponPtr = 0x608;
    constexpr uintptr_t WeaponPtrOld = 0x4C8;
    constexpr uintptr_t WeaponAimlock = 0x644;
    constexpr uintptr_t WeaponAimlockOld = 0x4DC;
    
    // Enemy/Bot State
    // Newer Free Fire: IsClientBot = 0x4A8, NamePtr = 0x498, AvatarManager = 0x778, AvatarSimple = 0x138
    constexpr uintptr_t IsClientBot = 0x4A8;
    constexpr uintptr_t IsClientBotOld = 0x450;

    constexpr uintptr_t NamePtr = 0x498;
    constexpr uintptr_t NamePtrOld = 0x448;

    constexpr uintptr_t AvatarManager = 0x778;
    constexpr uintptr_t AvatarManagerOld = 0x718;

    constexpr uintptr_t AvatarSimple = 0x138;
    constexpr uintptr_t AvatarSimpleOld = 0x128;

    constexpr uintptr_t AvatarVisible = 0x101;
    constexpr uintptr_t UmaData = 0x28;
    constexpr uintptr_t UmaDataCheck = 0x81;
    constexpr uintptr_t EnemyCheck7C = 0x7C;
    
    constexpr uintptr_t HHCBNAPCKHF = 0x19F8;
    constexpr uintptr_t HHCBDeadPtr = 0x20;
    constexpr uintptr_t DeadStatus = 0x10;
    
    // Health / Replication
    constexpr uintptr_t DataPool = 0x70;
    constexpr uintptr_t ReplicationData = 0x10;
    constexpr uintptr_t ReplicationItems = 0x20;
    constexpr uintptr_t Health = 0x18;
    
    // Collider
    constexpr uintptr_t HeadCollider = 0x740;
    constexpr uintptr_t HeadColliderOld = 0x6E0;
    constexpr uintptr_t HeadColliderWrite = 0x80;
    
    // Position Force / Aim Magnet
    constexpr uintptr_t EnemyPosForce = 0x900;

    // String Offsets
    constexpr uintptr_t StringLen = 0x10;
    constexpr uintptr_t StringChars = 0x14;
}

#define OFFSET_CAMERA_MATRIX_STR "0x10"
#define OFFSET_CAMERA_MATRIX_ALT "0x100"
