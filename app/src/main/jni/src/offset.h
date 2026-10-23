#pragma once
#include <cstdint>

// ========================================================
// NINMODS CENTRAL GAME OFFSETS (.env Architecture)
// Synchronized directly with ninmods_offsets.h & New_Brutal
// ========================================================
namespace Offsets {
    // Player Component / Bone Offsets (Synchronized with live ninmods_offsets.h)
    constexpr uintptr_t MainCameraTransform = 0x3E8ULL; // Verified from live dump (OFFSET_Player_MainCameraTransform)
    constexpr uintptr_t MainCameraTransformAlt = 0x3E8ULL;
    constexpr uintptr_t MainCamera = 0x3E8ULL;
    constexpr uintptr_t MainCameraAlt = 0x3E8ULL;
    constexpr uintptr_t MainCameraOld = 0x3E8ULL;

    constexpr uintptr_t Head = 0x3E8ULL; // Verified from live dump MainCameraTransform
    constexpr uintptr_t HeadFallback = 0x3E8ULL;
    constexpr uintptr_t HeadOld = 0x3E8ULL; // Old 0x6478 removed, unified to live 0x3E8ULL

    constexpr uintptr_t Spine = 0x3E8ULL; // Verified from live dump (MainCameraTransform)
    constexpr uintptr_t SpineFallback = 0x1288ULL; // Verified from live dump (m_TransformObject)

    constexpr uintptr_t Feet = 0x1288ULL; // Verified from live dump (OFFSET_Player_m_TransformObject)
    constexpr uintptr_t FeetFallback = 0x1288ULL;
    
    // Transform Offsets (Synchronized with Kernel & CROSS SRC ROOT 64-bit Unity Transform structure)
    constexpr uintptr_t TransformObj = 0x10;          // Genuine Unity TransformToObj (0x10)
    constexpr uintptr_t TransformObjAlt = 0x10;       // Legacy 0x110 removed, unified to 0x10
    constexpr uintptr_t TransformIndex = 0x40;        // ObjToIndex
    constexpr uintptr_t TransformMatrix = 0x38;       // ObjToMatrix
    constexpr uintptr_t MatrixList = 0x18;            // Genuine Unity MatrixList array pointer (0x18)
    constexpr uintptr_t MatrixListAlt = 0x18;         // Legacy 0x108 removed, unified to 0x18
    constexpr uintptr_t MatrixIndices = 0x20;         // Transform indices array
    constexpr uintptr_t MatrixDataSize = 0x30;        // sizeof(Matrix) = 48 bytes
    constexpr uintptr_t TransformIndexSize = 0x4;     // int index = 4 bytes
    constexpr uintptr_t TransformObject = 0x1288ULL;  // Verified from live dump (OFFSET_Player_m_TransformObject)
    
    // Aiming Info (Verified directly from live ninmods_offsets.h)
    constexpr uintptr_t AimingInfo = 0xE60ULL;        // Verified from live dump (OFFSET_Player_LastAimingInfoFromWeaponAdjusted)
    constexpr uintptr_t AimingInfoAlt = 0xE60ULL;     // Unified to live 0xE60ULL
    constexpr uintptr_t AimingInfoAdjusted = 0xE60ULL; // Live dump RVA & Offset
    constexpr uintptr_t AimingInfoOld = 0xE60ULL;     // Old 0x80 removed, unified to live 0xE60ULL
    constexpr uintptr_t AimingStartPos = 0x4C;
    constexpr uintptr_t AimingStartPosAlt = 0x40;     // Kernel sAim3
    constexpr uintptr_t AimingDirection = 0x40;       // Genuine Bullet ray direction offset (+0x40)
    constexpr uintptr_t AimingDirectionAlt = 0xD0;    // Kernel sAim4 (+0xD0)
    constexpr uintptr_t AimingDirectionLegacy = 0x40; // Old 0x430 removed, unified to 0x40
    constexpr uintptr_t AimAssist = 0x638ULL;         // Verified from live dump (OFFSET_Player_m_AimAssist)
    constexpr uintptr_t CurrentAimRotation = 0x1A8CULL; // Verified from live dump (OFFSET_Player_m_CurrentAimRotation)
    constexpr uintptr_t ActiveUISightingWeapon = 0x600ULL; // Verified from live dump (OFFSET_Player_ActiveUISightingWeapon)
    constexpr uintptr_t CallSetAimRotationCount = 0x810ULL; // Verified from live dump (OFFSET_Player_CallSetAimRotationCount)
    constexpr uintptr_t Player_FiringStateAlt = 0x259ULL;  // Verified from live dump (OFFSET_Player_UGCStartFiring)
    constexpr uintptr_t Player_HeadCollider = 0x740;        // Head collider component pointer
    constexpr uintptr_t Player_AimTargetWrite = 0x80;       // Aim target collider write offset
    
    // Inventory
    constexpr uintptr_t InventoryManager = 0x748;
    constexpr uintptr_t InventoryManagerOld = 0x748;  // Old 0x6E8 removed, unified to 0x748
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

    // Dictionary Offsets (Mayur ListPlayer / m_ShortIDToPlayers = 0x148)
    constexpr uintptr_t BaseGameDictionary = 0x148;
    constexpr uintptr_t BaseGameDictionaryAlt = 0xC0;
    constexpr uintptr_t BaseGameDictionaryAlt2 = 0x68;
    constexpr uintptr_t BaseGameDictionaryAlt3 = 0x60;
    constexpr uintptr_t BaseGameDictionaryAlt4 = 0x70;
    constexpr uintptr_t BaseGameDictionaryAlt5 = 0x140;
    constexpr uintptr_t DictionaryEntries = 0x18; // Mayur UnityArray<DictionaryEntry>* at 0x18
    constexpr uintptr_t DictionaryCount = 0x20;   // Mayur count at 0x20
    constexpr uintptr_t DictionaryEntrySize = 0x18; // sizeof(DictionaryEntry) = 24 bytes
    constexpr uintptr_t EntryEnemyList = 0x10;    // value is at +0x10
    
    // Camera Components & ViewMatrix (Proven from CROSS SRC ROOT & Bull_Team)
    constexpr uintptr_t FollowCamera = 0x698; // Proven Primary FollowCamera Component on Player
    constexpr uintptr_t FollowCameraAlt = 0x638; // Proven Secondary FollowCamera Component
    constexpr uintptr_t FollowCameraFallback = 0x638;
    constexpr uintptr_t FollowCameraOld = 0x638; // Old 0x6A8 removed, unified to 0x638

    constexpr uintptr_t FollowCameraObj = 0x30;
    constexpr uintptr_t CameraIntPtr = 0x10;
    constexpr uintptr_t Matrix = 0x100;
    constexpr uintptr_t MatrixAlt1 = 0xBC;
    constexpr uintptr_t MatrixAlt2 = 0xDC;
    
    // Local Player (Synchronized with live ninmods_offsets.h)
    constexpr uintptr_t IsFiring = 0x259ULL;          // Verified from live dump (OFFSET_Player_UGCStartFiring)
    constexpr uintptr_t IsFiringOld = 0x259ULL;       // Old 0x7E0/0x850 removed, unified to live 0x259ULL
    constexpr uintptr_t IsFiringAlt = 0x259ULL;       // Verified from live dump

    constexpr uintptr_t PlayerRotation = 0x61C;
    constexpr uintptr_t PlayerRotationOld = 0x61C;    // Old 0x5BC removed, unified to 0x61C
    constexpr uintptr_t Player_ViewRotation = 0x214;  // Primary Camera View Angle
    constexpr uintptr_t CallSetAimRotationCountAlt = 0x810ULL; // Unified to live dump 0x810ULL

    constexpr uintptr_t WeaponPtr = 0x608;
    constexpr uintptr_t WeaponPtrOld = 0x608;         // Old 0x4C8 removed
    constexpr uintptr_t WeaponAimlock = 0x644;
    constexpr uintptr_t WeaponAimlockOld = 0x644;     // Old 0x4DC removed
    constexpr uintptr_t PlayerAttributes = 0x770;
    constexpr uintptr_t ShootNoReload = 0x111;
    
    // Enemy/Bot State (Synchronized with live ninmods_offsets.h)
    constexpr uintptr_t IsClientBot = 0x4A0ULL;       // Verified from live dump (OFFSET_Player_IsClientBot)
    constexpr uintptr_t IsClientBotOld = 0x4A0ULL;    // Old 0x4A8 removed, unified to live 0x4A0ULL

    constexpr uintptr_t NamePtr = 0x498ULL;           // Verified from live dump (OFFSET_Player_OriginalNickName)
    constexpr uintptr_t NamePtrOld = 0x498ULL;        // Old 0x448 removed, unified to live 0x498ULL

    constexpr uintptr_t AvatarManager = 0x778;
    constexpr uintptr_t AvatarManagerOld = 0x778;     // Old 0x718 removed

    constexpr uintptr_t AvatarSimple = 0x138;
    constexpr uintptr_t AvatarSimpleOld = 0x138;      // Old 0x128 removed

    constexpr uintptr_t AvatarVisible = 0x101;
    constexpr uintptr_t UmaData = 0x28;
    constexpr uintptr_t UmaDataCheck = 0x81;
    constexpr uintptr_t EnemyCheck7C = 0x7C;
    constexpr uintptr_t IsDead = 0x7C;
    
    constexpr uintptr_t DeadStatus = 0x7C; // Unified to live IsDead status (0x7C)
    
    // Health / Replication (Synchronized with live ninmods_offsets.h)
    constexpr uintptr_t DataPool = 0x70;
    constexpr uintptr_t ReplicationData = 0x10;
    constexpr uintptr_t ReplicationItems = 0x20;
    constexpr uintptr_t Health = 0x18;
    constexpr uintptr_t IsFrozenKnockDown = 0xB0ULL; // Verified from live dump (OFFSET_Player_IsFrozenKnockDown)
    constexpr uintptr_t KnockDownDamagePerSec = 0x3ACULL; // Verified from live dump (OFFSET_Player_m_KnockDownDamagePerSec)
    
    // Collider
    constexpr uintptr_t HeadCollider = 0x740;
    constexpr uintptr_t HeadColliderOld = 0x740;      // Old 0x6E0 removed, unified to 0x740
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
        constexpr uintptr_t Player_GetChestPos = 0x1691a7a0ULL;
        constexpr uintptr_t Player_RootTF = 0x1691aa60ULL;
        constexpr uintptr_t Player_IsBoneNode = 0x1691ab50ULL;
        constexpr uintptr_t Player_get_HeadCollider = 0x1691acb0ULL;
        constexpr uintptr_t Player_get_RightHandTF = 0x1691ba80ULL;
        constexpr uintptr_t Player_get_LeftHandTF = 0x1691b9a0ULL;
        constexpr uintptr_t Player_get_SpineBoneTransform = 0x16915938ULL;
        constexpr uintptr_t Player_get_RootTransform = 0x16901910ULL;
        constexpr uintptr_t Player_get_BoneRootTransform = 0x1691efe8ULL;
        constexpr uintptr_t Player_get_ShoulderBoneTransform = 0x16903180ULL;
        constexpr uintptr_t Player_get_RightLegBoneTransform = 0x1691f11cULL;
        constexpr uintptr_t Player_get_HipsBoneTransform = 0x1691f250ULL;
        constexpr uintptr_t Player_get_BipBoneTransform = 0x1691f384ULL;
        constexpr uintptr_t Player_get_HeadBoneTransform = 0x1691f4b8ULL;
        constexpr uintptr_t Player_IsVisible = 0x16920108ULL;
        constexpr uintptr_t Player_get_IsDieing = 0x169057c0ULL;
        constexpr uintptr_t Player_get_IsReallyDead = 0x1691db04ULL;
        constexpr uintptr_t Player_get_MaxJumpHeight = 0x169d6050ULL;
        constexpr uintptr_t Player_get_CustomGravity = 0x16919df0ULL;
        constexpr uintptr_t Player_get_RisingGravity = 0x169d5e70ULL;
        constexpr uintptr_t Player_get_FallingGravity = 0x169d5f60ULL;
        constexpr uintptr_t Player_get_InSwapWeaponCD = 0x1691c688ULL;
        constexpr uintptr_t Player_get_IsGod = 0x168e7468ULL;
        constexpr uintptr_t GameConfig_get_ResetGuest = 0x163dd830ULL;
        constexpr uintptr_t Camera_get_main = 0x1997fd00ULL;
        constexpr uintptr_t Camera_WorldToScreenPoint = 0x1997f654ULL;
        constexpr uintptr_t Camera_get_worldToCameraMatrix = 0x1997f3a8ULL;
        constexpr uintptr_t Camera_get_projectionMatrix = 0x1997f4e0ULL;
        constexpr uintptr_t Transform_get_position = 0x199d5a68ULL;
        constexpr uintptr_t Transform_set_position = 0x199d5b08ULL;
        constexpr uintptr_t Transform_get_forward = 0x199d6040ULL;
        constexpr uintptr_t Transform_get_rotation = 0x199d5c58ULL;
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

    // ========================================================
    // MAYUR / UTILITY LIVE OFFSETS (Synchronized from ninmods_offsets.h)
    // ========================================================
    namespace Mayur {
        // Camera (Live 64-bit RVAs from ninmods_offsets.h)
        constexpr uintptr_t Camera_main            = 0x1997fd00ULL; // Live dump RVA_Camera_get_main
        constexpr uintptr_t WorldToScreenPoint     = 0x1997f654ULL; // Live dump RVA_Camera_WorldToScreenPoint
        constexpr uintptr_t Camera_get_fieldOfView = 0x1997e448ULL; // Live dump RVA_Camera_get_fieldOfView
        constexpr uintptr_t Camera_set_fieldOfView = 0x1997e484ULL; // Live dump RVA_Camera_set_fieldOfView

        // Transform (Live 64-bit RVAs from ninmods_offsets.h)
        constexpr uintptr_t GetForward            = 0x199d6040ULL; // Live dump RVA_Transform_get_forward
        constexpr uintptr_t Transform_GetPosition = 0x199d5a68ULL; // Live dump RVA_Transform_get_position
        constexpr uintptr_t Transform_SetPosition = 0x199d5b08ULL; // Live dump RVA_Transform_set_position
        constexpr uintptr_t Transform_Position    = 0x199d5a68ULL; // Live dump RVA_Transform_get_position
        constexpr uintptr_t Transform_Rotation    = 0x199d5c58ULL; // Live dump RVA_Transform_get_rotation

        // Component (Live 64-bit RVAs from ninmods_offsets.h)
        constexpr uintptr_t Component_GetTransform = 0x199c75d0ULL; // Live dump RVA_Component_get_transform

        // Player Offsets (Live dump from ninmods_offsets.h)
        constexpr uintptr_t MainCam        = 0x3E8ULL;       // Live dump OFFSET_Player_MainCameraTransform
        constexpr uintptr_t GetLocalPlayer = 0x16bad10cULL;  // Live dump RVA_GameFacade_CurrentLocalPlayer
        constexpr uintptr_t Current_Match  = 0x16baa1c8ULL;  // Live dump RVA_GameFacade_SetCurrentGame
        constexpr uintptr_t ListPlayer     = 0x148;
        constexpr uintptr_t m_ShortIDToPlayers = 0x148;

        // Player Methods (Live 64-bit RVAs from ninmods_offsets.h)
        constexpr uintptr_t Head         = 0x1691f4b8ULL; // Live dump RVA_Player_get_HeadBoneTransform
        constexpr uintptr_t Hip          = 0x1691f250ULL; // Live dump RVA_Player_get_HipsBoneTransform
        constexpr uintptr_t HeadCollider = 0x1691acb0ULL; // Live dump RVA_Player_get_HeadCollider
        constexpr uintptr_t Name         = 0x16917944ULL; // Live dump RVA_Player_get_NickName
        constexpr uintptr_t MaxHP        = 0x169174c8ULL; // Live dump RVA_Player_get_UserID
        constexpr uintptr_t CurHP        = 0x169057c0ULL; // Live dump RVA_Player_get_IsDieing
        constexpr uintptr_t IsVisible    = 0x16920108ULL; // Live dump RVA_Player_IsVisible
        constexpr uintptr_t IsLocalTeam  = 0x16baed90ULL; // Live dump RVA_GameFacade_CurrentLocalPlayerTeamIndex
        constexpr uintptr_t Die          = 0x1691db04ULL; // Live dump RVA_Player_get_IsReallyDead
        constexpr uintptr_t Scope        = 0x600ULL;      // Live dump OFFSET_Player_ActiveUISightingWeapon
        constexpr uintptr_t Fire         = 0x259ULL;      // Live dump OFFSET_Player_UGCStartFiring
        constexpr uintptr_t LocalBool    = 0x16ba613cULL; // Live dump RVA_GameFacade_get_IsLocalPlayerHideByCameraMode
        constexpr uintptr_t CarLocal     = 0x1691e010ULL; // Live dump RVA_Player_IsPassengerInVehicleCanFire
        constexpr uintptr_t GetCar       = 0x1691e3b8ULL; // Live dump RVA_Player_IsDriverInVehicleCanFire
        constexpr uintptr_t Aim          = 0xE60ULL;      // Live dump OFFSET_Player_LastAimingInfoFromWeaponAdjusted
        constexpr uintptr_t IsGod        = 0x168e7468ULL; // Live dump RVA_Player_get_IsGod

        // Game Timer (Live 64-bit RVAs from ninmods_offsets.h)
        constexpr uintptr_t m_Timer    = 0x16bab478ULL;  // Live dump RVA_GameFacade_CurrentGameTimer
        constexpr uintptr_t m_GetTimer = 0x16bab59cULL;  // Live dump RVA_GameFacade_CurrentGameSimulationTimer
        constexpr uintptr_t m_SetTimer = 0x16bab624ULL;  // Live dump RVA_GameFacade_CurrentGameFixedDeltaTime
    }
}

#define OFFSET_CAMERA_MATRIX_STR "0x10"
#define OFFSET_CAMERA_MATRIX_ALT "0x100"
