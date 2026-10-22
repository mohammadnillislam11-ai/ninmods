#pragma once
#include <cstdint>

// ========================================================
// NINMODS CENTRAL GAME OFFSETS (.env Architecture)
// Synchronized directly with ninmods_offsets.h & New_Brutal
// ========================================================
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
    constexpr uintptr_t MainCameraTransform = 0x3E8ULL; // from ninmods_offsets.h (COW.GamePlay.Player.MainCameraTransform)
    
    // Transform Offsets
    constexpr uintptr_t TransformObj = 0x110;
    constexpr uintptr_t TransformIndex = 0x40;
    constexpr uintptr_t TransformMatrix = 0x38;
    constexpr uintptr_t MatrixList = 0x108;
    constexpr uintptr_t MatrixIndices = 0x20;
    constexpr uintptr_t MatrixDataSize = 0x30;
    constexpr uintptr_t TransformObject = 0x1288ULL; // from ninmods_offsets.h (COW.GamePlay.Player.m_TransformObject)
    
    // Aiming Info
    // Newer Free Fire: AimingInfo = 0xE58, Adjusted = 0xE60, older was 0x80
    constexpr uintptr_t AimingInfo = 0xE58;
    constexpr uintptr_t AimingInfoAdjusted = 0xE60ULL; // from ninmods_offsets.h (COW.GamePlay.Player.LastAimingInfoFromWeaponAdjusted)
    constexpr uintptr_t AimingInfoOld = 0x80;
    constexpr uintptr_t AimingStartPos = 0x4C;
    constexpr uintptr_t AimingDirection = 0x430;
    constexpr uintptr_t AimAssist = 0x638ULL; // from ninmods_offsets.h (COW.GamePlay.Player.m_AimAssist)
    constexpr uintptr_t CurrentAimRotation = 0x1A8CULL; // from ninmods_offsets.h (COW.GamePlay.Player.m_CurrentAimRotation)
    constexpr uintptr_t ActiveUISightingWeapon = 0x600ULL; // from ninmods_offsets.h (COW.GamePlay.Player.ActiveUISightingWeapon)
    constexpr uintptr_t CallSetAimRotationCount = 0x810ULL; // from ninmods_offsets.h (COW.GamePlay.Player.CallSetAimRotationCount)
    
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

    // GameFacade fields from ninmods_offsets.h
    constexpr uintptr_t GameFacade_CurrentGame = 0x0ULL;
    constexpr uintptr_t GameFacade_CurrentMatchGame = 0x8ULL;
    constexpr uintptr_t GameFacade_LocalPlayerUserID = 0x58ULL;
    constexpr uintptr_t GameFacade_GameServerMatchID = 0xA8ULL;
    constexpr uintptr_t GameFacade_IsMatchStarted = 0x209ULL;

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
    constexpr uintptr_t IsDead = 0x7C; // New_Brutal PL_ISDEAD
    
    constexpr uintptr_t HHCBNAPCKHF = 0x19F8;
    constexpr uintptr_t HHCBDeadPtr = 0x20;
    constexpr uintptr_t DeadStatus = 0x10;
    
    // Health / Replication
    constexpr uintptr_t DataPool = 0x70;
    constexpr uintptr_t ReplicationData = 0x10;
    constexpr uintptr_t ReplicationItems = 0x20;
    constexpr uintptr_t Health = 0x18;
    constexpr uintptr_t IsFrozenKnockDown = 0xB0ULL; // from ninmods_offsets.h
    constexpr uintptr_t KnockDownDamagePerSec = 0x3ACULL; // from ninmods_offsets.h
    
    // Collider
    constexpr uintptr_t HeadCollider = 0x740;
    constexpr uintptr_t HeadColliderOld = 0x6E0;
    constexpr uintptr_t HeadColliderWrite = 0x80;
    
    // Position Force / Aim Magnet
    constexpr uintptr_t EnemyPosForce = 0x900;

    // String Offsets
    constexpr uintptr_t StringLen = 0x10;
    constexpr uintptr_t StringChars = 0x14;

    // ========================================================
    // IL2CPP METHOD RVAs (Synchronized directly from ninmods_offsets.h)
    // ========================================================
    namespace RVA {
        constexpr uintptr_t Player_GetChestPos = 0xe70da7a0ULL;
        constexpr uintptr_t Player_RootTF = 0xe70daa60ULL;
        constexpr uintptr_t Player_IsBoneNode = 0xe70dab50ULL;
        constexpr uintptr_t Player_get_HeadCollider = 0xe70dacb0ULL;
        constexpr uintptr_t Player_get_RightHandTF = 0xe70dba80ULL;
        constexpr uintptr_t Player_get_LeftHandTF = 0xe70db9a0ULL;
        constexpr uintptr_t Player_get_SpineBoneTransform = 0xe70d5938ULL;
        constexpr uintptr_t Player_get_RootTransform = 0xe70c1910ULL;
        constexpr uintptr_t Player_get_BoneRootTransform = 0xe70defe8ULL;
        constexpr uintptr_t Player_get_ShoulderBoneTransform = 0xe70c3180ULL;
        constexpr uintptr_t Player_get_RightLegBoneTransform = 0xe70df11cULL;
        constexpr uintptr_t Player_get_HipsBoneTransform = 0xe70df250ULL;
        constexpr uintptr_t Player_get_BipBoneTransform = 0xe70df384ULL;
        constexpr uintptr_t Player_get_HeadBoneTransform = 0xe70df4b8ULL;
        constexpr uintptr_t Player_IsVisible = 0xe70e0108ULL;
        constexpr uintptr_t Player_get_IsDieing = 0xe70c57c0ULL;
        constexpr uintptr_t Player_get_IsReallyDead = 0xe70ddb04ULL;
        constexpr uintptr_t Player_get_MaxJumpHeight = 0xe7196050ULL;
        constexpr uintptr_t Player_get_CustomGravity = 0xe70d9df0ULL;
        constexpr uintptr_t Player_get_RisingGravity = 0xe7195e70ULL;
        constexpr uintptr_t Player_get_FallingGravity = 0xe7195f60ULL;
        constexpr uintptr_t Player_get_InSwapWeaponCD = 0xe70dc688ULL;
        constexpr uintptr_t Player_get_IsGod = 0xe70a7468ULL;
        constexpr uintptr_t GameConfig_get_ResetGuest = 0xe6b9d830ULL;
        constexpr uintptr_t Camera_get_main = 0xea13fd00ULL;
        constexpr uintptr_t Camera_WorldToScreenPoint = 0xea13f654ULL;
        constexpr uintptr_t Camera_get_worldToCameraMatrix = 0xea13f3a8ULL;
        constexpr uintptr_t Camera_get_projectionMatrix = 0xea13f4e0ULL;
        constexpr uintptr_t Transform_get_position = 0xea195a68ULL;
        constexpr uintptr_t Transform_set_position = 0xea195b08ULL;
        constexpr uintptr_t Transform_get_forward = 0xea196040ULL;
        constexpr uintptr_t Transform_get_rotation = 0xea195c58ULL;
    }

    // ========================================================
    // OB54 / OB55 / OB56 PROVEN OFFSETS (Synchronized from NINMODS & New_Brutal)
    // ========================================================
    namespace Ob55 {
        // HitObjectInfo (CGKJLKPMGDJ) field offsets
        constexpr uintptr_t HitObject = 0x10;        // JHAINOBIFMA
        constexpr uintptr_t HitCollider = 0x18;      // JINDNOFABKB
        constexpr uintptr_t HitGroup = 0x20;         // DKPDBLLJEAA
        constexpr uintptr_t HitLocation = 0x24;      // HPCOJHGFEEG
        constexpr uintptr_t HitNormal = 0x30;        // DOJDNHCGFGK
        constexpr uintptr_t RayDir = 0x3C;           // HLDECMJFKJK
        constexpr uintptr_t IgnoreHappens = 0x48;    // EFMBNPNFGHM
        constexpr uintptr_t ViewBlocked = 0x49;      // CEOKOILBODN
        constexpr uintptr_t SpecialHitType = 0x4C;   // CLOLKHNMNEK

        // Game Hooks and Method Names
        constexpr const char* AssemblyCSharp = "Assembly-CSharp.dll";
        constexpr const char* COWGamePlay = "COW.GamePlay";
        constexpr const char* COWGameConfig = "COW";
        constexpr const char* CalcDamageClass = "HBIBDMMOOOK";
        constexpr const char* CalcDamageMethod = "LHNJLFOINDO";
        constexpr const char* HitObjectClass = "CGKJLKPMGDJ";
        constexpr const char* ResetGuestMethod = "get_ResetGuest";
        constexpr const char* MaxJumpHeightMethod = "get_MaxJumpHeight";
        constexpr const char* CustomGravityMethod = "get_CustomGravity";
        constexpr const char* InSwapWeaponCDMethod = "get_InSwapWeaponCD";
        constexpr const char* IsGodMethod = "get_IsGod";
    }
}

#define OFFSET_CAMERA_MATRIX_STR "0x10"
#define OFFSET_CAMERA_MATRIX_ALT "0x100"
