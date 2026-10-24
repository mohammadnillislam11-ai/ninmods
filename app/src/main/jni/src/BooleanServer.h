#pragma once

struct {
    bool aimbot = false;
    bool fovawm = false;
    bool selectTarget = false;
    bool silen4a = false;  // Silent aim enabled/disabled
    float AimFov = 60;
    float AimDistance = 30;
    bool NoRecoil = false;
    bool aimbotlock = false;
    bool SpeedHackV2 = false;
    bool undercam = false;
    bool PlayerToMe = false;
    float AimFovScale = 1.20f;
    float gameSpeed = 1.0f;
    
    // New aim features
    bool aimMagnet = false;         // Aim magnet toggle
    bool silentAimHead = true;      // true = head, false = body
    bool targetByDistance = false;  // true = closest, false = closest to crosshair
    int headshotRate = 100;         // 0-100% chance for headshot logic
    bool ignoreBots = true;         // Skip bots (Always ON)
    bool ignoreKnocked = true;      // Skip knocked players (Always ON)

} pPlayer;


struct {
    bool Bypass = false;
    bool activar = false;
} Actived;