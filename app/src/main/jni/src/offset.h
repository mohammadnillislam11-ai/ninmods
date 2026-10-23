#pragma once
#include <cstdint>

// ========================================================
// NINMODS CENTRAL GAME OFFSETS (.env Architecture)
// Synchronized directly with ninmods_offsets.h & New_Brutal
// ========================================================
namespace Offsets {
    // Player Component / Bone Offsets (Synchronized with live ninmods_offsets.h)
    constexpr uintptr_t MainCameraTransform = 0x3E8ULL; // Verified from live dump (OFFSET_Player_MainCameraTransform)
    constexpr uintptr_t MainCameraTransformAlt = 0x3F0; // Mayur fallback
    constexpr uintptr_t MainCamera = 0x3E8ULL;
    constexpr uintptr_t MainCameraAlt = 0x3F0;
    constexpr uintptr_t MainCameraOld = 0x38;

    constexpr uintptr_t Head = 0x3E8ULL; // Verified from live dump MainCameraTransform
    constexpr uintptr_t HeadFallback = 0x3F0;
    constexpr uintptr_t HeadOld = 0x6478;

    constexpr uintptr_t Spine = 0x6548;
    constexpr uintptr_t SpineFallback = 0x6B0;

    constexpr uintptr_t Feet = 0x60;
    constexpr uintptr_t FeetFallback = 0x6D0;
    
    // Transform Offsets (Synchronized with Kernel & CROSS SRC ROOT 64-bit Unity Transform structure)
    constexpr uintptr_t TransformObj = 0x10;          // Genuine Unity TransformToObj (0x10)
    constexpr uintptr_t TransformObjAlt = 0x110;      // Legacy fallback
    constexpr uintptr_t TransformIndex = 0x40;        // ObjToIndex
    constexpr uintptr_t TransformMatrix = 0x38;       // ObjToMatrix
    constexpr uintptr_t MatrixList = 0x18;            // Genuine Unity MatrixList array pointer (0x18)
    constexpr uintptr_t MatrixListAlt = 0x108;        // Legacy fallback
    constexpr uintptr_t MatrixIndices = 0x20;         // Transform indices array
    constexpr uintptr_t MatrixDataSize = 0x30;        // sizeof(Matrix) = 48 bytes
    constexpr uintptr_t TransformIndexSize = 0x4;     // int index = 4 bytes
    constexpr uintptr_t TransformObject = 0x1288ULL;  // from ninmods_offsets.h (COW.GamePlay.Player.m_TransformObject)
    
    // Aiming Info (Verified against Kernel & Scarecrow)
    // Newer Free Fire: AimingInfo = 0xE58, Alt = 0xE50 (sAim2), Adjusted = 0xE60, older was 0x80
    constexpr uintptr_t AimingInfo = 0xE58;
    constexpr uintptr_t AimingInfoAlt = 0xE50;        // Kernel sAim2
    constexpr uintptr_t AimingInfoAdjusted = 0xE60ULL; // from ninmods_offsets.h (COW.GamePlay.Player.LastAimingInfoFromWeaponAdjusted)
    constexpr uintptr_t AimingInfoOld = 0x80;
    constexpr uintptr_t AimingStartPos = 0x4C;
    constexpr uintptr_t AimingStartPosAlt = 0x40;     // Kernel sAim3
    constexpr uintptr_t AimingDirection = 0x40;       // Genuine Bullet ray direction offset (+0x40)
    constexpr uintptr_t AimingDirectionAlt = 0xD0;    // Kernel sAim4 (+0xD0)
    constexpr uintptr_t AimingDirectionLegacy = 0x430;
    constexpr uintptr_t AimAssist = 0x638ULL;         // from ninmods_offsets.h (COW.GamePlay.Player.m_AimAssist)
    constexpr uintptr_t CurrentAimRotation = 0x1A8CULL; // from ninmods_offsets.h (COW.GamePlay.Player.m_CurrentAimRotation)
    constexpr uintptr_t ActiveUISightingWeapon = 0x600ULL; // from ninmods_offsets.h (COW.GamePlay.Player.ActiveUISightingWeapon)
    constexpr uintptr_t CallSetAimRotationCount = 0x810ULL; // from ninmods_offsets.h (COW.GamePlay.Player.CallSetAimRotationCount)
    constexpr uintptr_t Player_FiringStateAlt = 0x848;      // Kernel sAim1 firing state
    constexpr uintptr_t Player_HeadCollider = 0x740;        // Head collider component pointer
    constexpr uintptr_t Player_AimTargetWrite = 0x80;       // Aim target collider write offset
    
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
    constexpr uintptr_t FollowCameraFallback = 0x640;
    constexpr uintptr_t FollowCameraOld = 0x6A8;

    constexpr uintptr_t FollowCameraObj = 0x30;
    constexpr uintptr_t CameraIntPtr = 0x10;
    constexpr uintptr_t Matrix = 0x100;
    constexpr uintptr_t MatrixAlt1 = 0xBC;
    constexpr uintptr_t MatrixAlt2 = 0xDC;
    
    // Local Player
    // Newer Free Fire: IsFiring = 0x850 (Older was 0x7E0)
    constexpr uintptr_t IsFiring = 0x850;
    constexpr uintptr_t IsFiringOld = 0x7E0;
    constexpr uintptr_t IsFiringAlt = 0x259; // UGCStartFiring from ninmods_offsets.h

    // Newer Free Fire: PlayerRotation = 0x61C (Older was 0x5BC)
    constexpr uintptr_t PlayerRotation = 0x61C;
    constexpr uintptr_t PlayerRotationOld = 0x5BC;
    constexpr uintptr_t Player_ViewRotation = 0x214;       // Scarecrow & Kernel Primary Camera View Angle
    constexpr uintptr_t CallSetAimRotationCountAlt = 0x79C; // Scarecrow CallSetAimRotationCount

    constexpr uintptr_t WeaponPtr = 0x608;
    constexpr uintptr_t WeaponPtrOld = 0x4C8;
    constexpr uintptr_t WeaponAimlock = 0x644;
    constexpr uintptr_t WeaponAimlockOld = 0x4DC;
    constexpr uintptr_t PlayerAttributes = 0x770;        // Scarecrow PlayerAttributes
    constexpr uintptr_t ShootNoReload = 0x111;           // Scarecrow ShootNoReload trigger
    
    // Enemy/Bot State
    constexpr uintptr_t IsClientBot = 0x4A0; // Corrected from 0x4A8 to 0x4A0 (ninmods_offsets.h OFFSET_Player_IsClientBot)
    constexpr uintptr_t IsClientBotOld = 0x4A8;

    constexpr uintptr_t NamePtr = 0x498; // ninmods_offsets.h OFFSET_Player_OriginalNickName
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
    // MAYUR OB55 LIVE OFFSETS (Synchronized from pAdress.h)
    // ========================================================
    namespace Mayur {
        // Camera
        constexpr uintptr_t Camera_main            = 0xa0c9acc;
        constexpr uintptr_t WorldToScreenPoint     = 0xa0c9750;
        constexpr uintptr_t Camera_get_fieldOfView = 0xa0c819c;
        constexpr uintptr_t Camera_set_fieldOfView = 0xa0c81d8;

        // Input
        constexpr uintptr_t Input_get_touchCount    = 0xA15304C;
        constexpr uintptr_t Input_GetTouch          = 0xA152A74;
        constexpr uintptr_t Input_get_mousePosition = 0xA152D94;

        // Screen
        constexpr uintptr_t Screen_get_width   = 0xa0d71d4;
        constexpr uintptr_t Screen_get_height  = 0xa0d71fc;
        constexpr uintptr_t Screen_get_density = 0xa0d7224;

        // Transform
        constexpr uintptr_t GetForward            = 0xa1201c8;
        constexpr uintptr_t Transform_GetPosition = 0xa11fc4c;
        constexpr uintptr_t Transform_SetPosition = 0xa11fce4;
        constexpr uintptr_t Transform_Position    = 0xa11fbf0;
        constexpr uintptr_t Transform_Rotation    = 0xa11fde0;

        // Component
        constexpr uintptr_t Component_GetTransform = 0xa1116f8;

        // Player Offsets
        constexpr uintptr_t MainCam        = 0x3f0;
        constexpr uintptr_t GetLocalPlayer = 0x4e76054;
        constexpr uintptr_t Current_Match  = 0x75ebd88;
        constexpr uintptr_t ListPlayer     = 0x148;
        constexpr uintptr_t m_ShortIDToPlayers = 0x148;

        // Player Methods
        constexpr uintptr_t Head         = 0x7099fbc;
        constexpr uintptr_t Hip          = 0x709a148;
        constexpr uintptr_t HeadCollider = 0x7015e3c;
        constexpr uintptr_t Name         = 0x7012ad4;
        constexpr uintptr_t MaxHP        = 0x6ff81b8;
        constexpr uintptr_t CurHP        = 0x707e2e4;
        constexpr uintptr_t IsVisible    = 0x701b26c;
        constexpr uintptr_t IsLocalTeam  = 0x75ec964;
        constexpr uintptr_t Die          = 0x7000800;
        constexpr uintptr_t Scope        = 0x700c05c;
        constexpr uintptr_t Fire         = 0x7003790;
        constexpr uintptr_t LocalBool    = 0x6fe2244;
        constexpr uintptr_t CarLocal     = 0x7018ff8;
        constexpr uintptr_t GetCar       = 0x6ff9dec;
        constexpr uintptr_t Aim          = 0x7014450;
        constexpr uintptr_t IsGod        = 0x6fe24a4;

        // String
        constexpr uintptr_t u3str   = 0x9EE6714;
        constexpr uintptr_t CharGet = 0x9daae88;

        // Raycast
        constexpr uintptr_t Raycast = 0x7c5289c;

        // Game Timer
        constexpr uintptr_t m_Timer    = 0x75EA614;
        constexpr uintptr_t m_GetTimer = 0x83583E0;
        constexpr uintptr_t m_SetTimer = 0x8358414;

        // Unity Time
        constexpr uintptr_t Time_deltaTime = 0xA109624;

        // Aim Collider
        constexpr uintptr_t LAimCollider = 0x7246a50;
    }
}

#define OFFSET_CAMERA_MATRIX_STR "0x10"
#define OFFSET_CAMERA_MATRIX_ALT "0x100"
