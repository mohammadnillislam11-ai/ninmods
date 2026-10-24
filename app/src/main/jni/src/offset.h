#pragma once
#include <cstdint>

namespace Offsets {

// ====================================================
// FREE FIRE NORMAL (com.dts.freefireth)
// Sourced from: 5_6213160554375160447.h
// ====================================================
namespace Normal {
constexpr uintptr_t GameFacade = 0xA872380;
constexpr uintptr_t GameFacade_Current = 0x8;
constexpr uintptr_t BaseGame_Match = 0x90;
constexpr uintptr_t BaseGame_Timer = 0x18;
constexpr uintptr_t Timer_FixedDeltaTime = 0x2C;
constexpr uintptr_t Match_Status = 0xD0;
constexpr uintptr_t Match_PlayerDict = 0xC0;
constexpr uintptr_t Match_LocalPlayer = 0xD8;

constexpr uintptr_t Dict_Entries = 0x18;
constexpr uintptr_t Dict_Count = 0x20;
constexpr uintptr_t Dict_ArrayHeader = 0x20;
constexpr uintptr_t Dict_EntryStride = 0x18;
constexpr uintptr_t Dict_EntryValue = 0x10;

constexpr uintptr_t Player_Rotation = 0x61C;
constexpr uintptr_t Player_Name = 0x498;
constexpr uintptr_t Player_IsBot = 0x428;
constexpr uintptr_t Player_IsFiring = 0x259;
constexpr uintptr_t Player_Speed = 0x568;
constexpr uintptr_t Player_IsDead = 0x6C;
constexpr uintptr_t Player_Avatar = 0x778;
constexpr uintptr_t Player_Attributes = 0x770;
constexpr uintptr_t Player_CurrentWeapon = 0x298;
constexpr uintptr_t Player_Ammo = 0x2AC;
constexpr uintptr_t Player_IsClientBot = 0x4A8;
constexpr uintptr_t Player_TeamModeID = 0x43C;

constexpr uintptr_t Camera_Follow = 0x30;
constexpr uintptr_t Camera_IntPtr = 0x10;
constexpr uintptr_t Camera_Matrix = 0x100;

constexpr uintptr_t FollowCamera = 0x698;
constexpr uintptr_t FollowCameraAlt = 0x638;

constexpr uintptr_t Bone_Head = 0x6B0;
constexpr uintptr_t Bone_Neck = 0x710;
constexpr uintptr_t Bone_Spine = 0x718;
constexpr uintptr_t Bone_Hip = 0x6B8;
constexpr uintptr_t Bone_Root = 0x6C0;
constexpr uintptr_t Bone_Eye = 0x6C8;
constexpr uintptr_t Bone_LeftShoulder = 0x6D0;
constexpr uintptr_t Bone_RightShoulder = 0x6D8;
constexpr uintptr_t Bone_LeftHand = 0x6A0;
constexpr uintptr_t Bone_RightHand = 0x6A8;
constexpr uintptr_t Bone_LeftWrist = 0x700;
constexpr uintptr_t Bone_RightWrist = 0x708;
constexpr uintptr_t Bone_LeftLeg = 0x6F0;
constexpr uintptr_t Bone_RightLeg = 0x6F8;
constexpr uintptr_t Bone_LeftCalf = 0x720;
constexpr uintptr_t Bone_RightCalf = 0x728;
constexpr uintptr_t Bone_LeftFoot = 0x6E0;
constexpr uintptr_t Bone_RightFoot = 0x6E8;

constexpr uintptr_t AimAssist = 0x640;
constexpr uintptr_t EAimAssistState = 0x668;
constexpr uintptr_t AimTarget = 0x670;
constexpr uintptr_t WeaponRelated = 0x680;
constexpr uintptr_t Player_BulletInfo = 0xE60;
} // namespace Normal

namespace Max {
constexpr uintptr_t GameFacade = 0xAD68E18;
constexpr uintptr_t GameFacade_P2 = 0xB8;
constexpr uintptr_t BaseGame_Match = 0x90;
constexpr uintptr_t BaseGame_Timer = 0x20;
constexpr uintptr_t Timer_FixedDeltaTime = 0x2C;
constexpr uintptr_t Match_PlayerDict = 0xC0;
constexpr uintptr_t Match_LocalPlayer = 0xD8;

constexpr uintptr_t Dict_Entries = 0x18;
constexpr uintptr_t Dict_Count = 0x20;
constexpr uintptr_t Dict_ArrayHeader = 0x20;
constexpr uintptr_t Dict_EntryStride = 0x18;
constexpr uintptr_t Dict_EntryValue = 0x10;

constexpr uintptr_t Player_Rotation = 0x214;
constexpr uintptr_t Player_HeadTF = 0x6A8;
constexpr uintptr_t Player_FootTF = 0x6D0;
constexpr uintptr_t Player_Camera = 0x698;
constexpr uintptr_t Player_IsFiring = 0x850;
constexpr uintptr_t Player_HP = 0x70;
constexpr uintptr_t Player_IsDead = 0x7C;
constexpr uintptr_t Player_Avatar = 0x778;
constexpr uintptr_t Player_HedColider = 0x740;
constexpr uintptr_t Player_ColWrite = 0x80;
constexpr uintptr_t Player_IsBot = 0x4A8;
constexpr uintptr_t Player_Name = 0x4A0;
constexpr uintptr_t Player_BulletInfo = 0xE58;

constexpr uintptr_t Camera_Follow = 0x30;
constexpr uintptr_t Camera_IntPtr = 0x10;
constexpr uintptr_t Camera_Matrix = 0x100;

constexpr uintptr_t Bone_Head = 0x6A8;
constexpr uintptr_t Bone_Chest = 0x6B8;
constexpr uintptr_t Bone_Hip = 0x6B0;
constexpr uintptr_t Bone_Feet = 0x6D0;
constexpr uintptr_t Bone_Neck = 0x658;
constexpr uintptr_t Bone_Spine = 0x650;
constexpr uintptr_t Bone_LeftShoulder = 0x710;
constexpr uintptr_t Bone_RightShoulder = 0x718;
constexpr uintptr_t Bone_LeftHand = 0x728;
constexpr uintptr_t Bone_RightHand = 0x720;
constexpr uintptr_t Bone_LeftAnkle = 0x6E0;
constexpr uintptr_t Bone_RightAnkle = 0x6E8;
} // namespace Max

// ====================================================
// UNIFIED ADAPTIVE OFFSETS (Supports Both Normal & MAX)
// ====================================================

// Roots
constexpr uintptr_t GameFacade = 0xA872380;     // Normal Primary
constexpr uintptr_t GameFacadeMax = 0xAD68E18;  // MAX Primary
constexpr uintptr_t GameFacadeAlt1 = 0xAD68E18; // MAX Alternative
constexpr uintptr_t GameFacadeAlt2 = 0xA984D48;
constexpr uintptr_t GameFacadeGlobal = 0x0C2132B8;

constexpr uintptr_t GameFacade_Current = 0x8;
constexpr uintptr_t GameFacade_P2 = 0xB8;
constexpr uintptr_t BaseGame_Match = 0x90;
constexpr uintptr_t BaseGameMatch = 0x90;
constexpr uintptr_t BaseGame_Timer = 0x18;
constexpr uintptr_t BaseGameTimeService = 0x18;
constexpr uintptr_t Timer_FixedDeltaTime = 0x2C;
constexpr uintptr_t TimeServiceSpeed = 0x2C;
constexpr uintptr_t Match_Status = 0xD0;
constexpr uintptr_t Match_PlayerDict = 0xC0;
constexpr uintptr_t Match_LocalPlayer = 0xD8;
constexpr uintptr_t MatchLocalPlayer = 0xD8;

// Entity Dictionary Traversal
constexpr uintptr_t BaseGameDictionary = 0xC0;
constexpr uintptr_t BaseGameDictionaryAlt = 0x148;
constexpr uintptr_t BaseGameDictionaryAlt2 = 0x68;
constexpr uintptr_t BaseGameDictionaryAlt3 = 0x60;
constexpr uintptr_t BaseGameDictionaryAlt4 = 0x70;
constexpr uintptr_t BaseGameDictionaryAlt5 = 0x140;

constexpr uintptr_t Dict_Entries = 0x18;
constexpr uintptr_t DictionaryEntries = 0x18;
constexpr uintptr_t Dict_Count = 0x20;
constexpr uintptr_t DictionaryCount = 0x20;
constexpr uintptr_t Dict_ArrayHeader = 0x20;
constexpr uintptr_t Dict_EntryStride = 0x18;
constexpr uintptr_t DictionaryEntrySize = 0x18;
constexpr uintptr_t Dict_EntryValue = 0x10;
constexpr uintptr_t EntryEnemyList = 0x10;

// Bone Transforms (Candidates tested in order: Normal -> MAX)
constexpr uintptr_t Bone_Head = 0x6B0; // Normal (0x6B0) / MAX (0x6A8)
constexpr uintptr_t Head = 0x6B0;
constexpr uintptr_t HeadFallback = 0x6A8; // MAX
constexpr uintptr_t HeadOld = 0x6B0;

constexpr uintptr_t Bone_Neck = 0x710;  // Normal (0x710) / MAX (0x658)
constexpr uintptr_t Bone_Spine = 0x718; // Normal (0x718) / MAX (0x650)
constexpr uintptr_t Spine = 0x718;
constexpr uintptr_t SpineFallback = 0x650; // MAX

constexpr uintptr_t Bone_Hip = 0x6B8; // Normal (0x6B8) / MAX (0x6B0)
constexpr uintptr_t Bone_Root = 0x6C0;
constexpr uintptr_t Bone_Eye = 0x6C8;

constexpr uintptr_t Bone_LeftShoulder = 0x6D0;  // Normal (0x6D0) / MAX (0x710)
constexpr uintptr_t Bone_RightShoulder = 0x6D8; // Normal (0x6D8) / MAX (0x718)
constexpr uintptr_t Bone_LeftHand = 0x6A0;      // Normal (0x6A0) / MAX (0x728)
constexpr uintptr_t Bone_RightHand = 0x6A8;     // Normal (0x6A8) / MAX (0x720)
constexpr uintptr_t Bone_LeftWrist = 0x700;
constexpr uintptr_t Bone_RightWrist = 0x708;

constexpr uintptr_t Bone_LeftLeg = 0x6F0;
constexpr uintptr_t Bone_RightLeg = 0x6F8;
constexpr uintptr_t Bone_LeftCalf = 0x720;
constexpr uintptr_t Bone_RightCalf = 0x728;
constexpr uintptr_t Bone_LeftFoot = 0x6E0; // Normal (0x6E0) / MAX (0x6D0)
constexpr uintptr_t Bone_RightFoot = 0x6E8;
constexpr uintptr_t Feet = 0x6E0;
constexpr uintptr_t FeetFallback = 0x6D0; // MAX

// Camera & Matrix Offsets
constexpr uintptr_t Camera_Follow = 0x30;
constexpr uintptr_t FollowCameraObj = 0x30;
constexpr uintptr_t Camera_IntPtr = 0x10;
constexpr uintptr_t CameraIntPtr = 0x10;
constexpr uintptr_t Camera_Matrix = 0x100;
constexpr uintptr_t Matrix = 0x100;
constexpr uintptr_t MatrixAlt1 = 0xBC;
constexpr uintptr_t MatrixAlt2 = 0xDC;

constexpr uintptr_t FollowCamera = 0x698;
constexpr uintptr_t FollowCameraAlt = 0x638;
constexpr uintptr_t FollowCameraFallback = 0x640;
constexpr uintptr_t FollowCameraOld = 0x6A8;
constexpr uintptr_t MainCameraTransform = 0x3E8;
constexpr uintptr_t MainCameraTransformAlt = 0x3F0;
constexpr uintptr_t MainCamera = 0x3E8;

// Transform Matrix Internals (Unity 64-bit Engine)
constexpr uintptr_t Transform_DataPtr = 0x38;
constexpr uintptr_t TransformMatrix = 0x38;
constexpr uintptr_t TransformObj = 0x10;
constexpr uintptr_t TransformObjAlt = 0x10;
constexpr uintptr_t TransformIndex = 0x40;
constexpr uintptr_t MatrixList_Offset = 0x18;
constexpr uintptr_t MatrixList = 0x18;
constexpr uintptr_t MatrixListAlt = 0x18;
constexpr uintptr_t MatrixIndices = 0x20;
constexpr uintptr_t Matrix_Stride = 0x30;
constexpr uintptr_t MatrixDataSize = 0x30;
constexpr uintptr_t TransformIndexSize = 0x4;
constexpr uintptr_t TMatrix_Position = 0x00;

// Player State & Combat
constexpr uintptr_t Player_Rotation =
    0x61C; // Normal (0x61C) / MAX ViewRotation (0x214)
constexpr uintptr_t PlayerRotation = 0x61C;
constexpr uintptr_t PlayerRotationOld = 0x214; // MAX
constexpr uintptr_t Player_ViewRotation = 0x214;
constexpr uintptr_t CallSetAimRotationCount = 0x810;
constexpr uintptr_t CallSetAimRotationCountAlt = 0x810;

constexpr uintptr_t Player_Name = 0x498; // Normal (0x498) / MAX (0x4A0)
constexpr uintptr_t NamePtr = 0x498;
constexpr uintptr_t NamePtrOld = 0x4A0; // MAX

constexpr uintptr_t Player_IsBot = 0x428;
constexpr uintptr_t Player_IsClientBot = 0x4A8;
constexpr uintptr_t IsClientBot = 0x4A8;
constexpr uintptr_t IsClientBotOld = 0x428;

constexpr uintptr_t Player_IsFiring = 0x259; // Normal (0x259) / MAX (0x850)
constexpr uintptr_t IsFiring = 0x259;
constexpr uintptr_t IsFiringOld = 0x850; // MAX
constexpr uintptr_t IsFiringAlt = 0x259;
constexpr uintptr_t Player_FiringStateAlt = 0x850;

constexpr uintptr_t Player_Speed = 0x568;
constexpr uintptr_t Player_IsDead = 0x6C; // Normal (0x6C) / MAX (0x7C)
constexpr uintptr_t IsDead = 0x6C;
constexpr uintptr_t DeadStatus = 0x7C; // MAX

constexpr uintptr_t Player_Avatar = 0x778;
constexpr uintptr_t AvatarManager = 0x778;
constexpr uintptr_t AvatarManagerOld = 0x778;

constexpr uintptr_t Player_Attributes = 0x770;
constexpr uintptr_t PlayerAttributes = 0x770;
constexpr uintptr_t ShootNoReload = 0x111;

constexpr uintptr_t Player_CurrentWeapon = 0x298;
constexpr uintptr_t Player_Ammo = 0x2AC;
constexpr uintptr_t Player_WeaponModel = 0x2B0;
constexpr uintptr_t Player_WeaponSkin = 0x2B8;
constexpr uintptr_t Player_TeamModeID = 0x43C;

// AimAssist & Targeting
constexpr uintptr_t AimAssist = 0x640;
constexpr uintptr_t AimAssist_IceWall = 0x648;
constexpr uintptr_t EAimAssistState = 0x668;
constexpr uintptr_t AimTarget = 0x670;
constexpr uintptr_t WeaponRelated = 0x680;
constexpr uintptr_t Player_HeadCollider = 0x740;
constexpr uintptr_t Player_AimTargetWrite = 0x80;

constexpr uintptr_t AimingInfo = 0xE60;    // Normal (0xE60) / MAX (0xE58)
constexpr uintptr_t AimingInfoAlt = 0xE58; // MAX
constexpr uintptr_t AimingInfoOld = 0xE60;
constexpr uintptr_t AimingStartPos = 0x4C;
constexpr uintptr_t AimingStartPosAlt = 0x40;
constexpr uintptr_t AimingDirection = 0x40;
constexpr uintptr_t AimingDirectionAlt = 0xD0;

// Health, DataPool & Replication
constexpr uintptr_t m_PRIDataPool = 0x78;
constexpr uintptr_t DataPool = 0x78;
constexpr uintptr_t m_Datas = 0x20;
constexpr uintptr_t ReplicationData = 0x20;
constexpr uintptr_t ReplicationItems = 0x20;
constexpr uintptr_t NetworkID = 0x18;
constexpr uintptr_t Health = 0x18;

// UMA / Visibility
constexpr uintptr_t Avatar_Uma = 0x138;
constexpr uintptr_t Uma_Visible = 0x101;
constexpr uintptr_t Uma_Data = 0x28;
constexpr uintptr_t UmaData_Skip = 0x81;
constexpr uintptr_t IsVisible = 0x101;

// Inventory & Weapon Controller
constexpr uintptr_t Weapon_Controller_Base = 0x800;
constexpr uintptr_t Weapon_Stats = 0x120;
constexpr uintptr_t Weapon_FireRate = 0x24;
constexpr uintptr_t Weapon_Damage = 0x140;
constexpr uintptr_t Weapon_Recoil = 0x150;
constexpr uintptr_t Weapon_Spread = 0x154;
constexpr uintptr_t Weapon_InfiniteAmmo = 0x14C;

constexpr uintptr_t InventoryManager = 0x6E0; // MAX (0x6E0) / Normal (0x748)
constexpr uintptr_t InventoryManagerOld = 0x748;
constexpr uintptr_t InventoryItem = 0xA0;
constexpr uintptr_t ItemData = 0x28;
constexpr uintptr_t WeaponId = 0x40;

// Strings & Position Force
constexpr uintptr_t EnemyPosForce = 0x900;
constexpr uintptr_t StringLen = 0x10;
constexpr uintptr_t StringChars = 0x14;
} // namespace Offsets
