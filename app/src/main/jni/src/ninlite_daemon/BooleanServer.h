#ifndef NINLITE_BOOLEAN_SERVER_H
#define NINLITE_BOOLEAN_SERVER_H

struct {
    bool aimhard = false;
    bool selectTarget = false;
    bool silen4a = false;
    float AimFov = 0;
    float AimDistance = 250;
    bool aimbotlock = false;
    bool aimlock = false;
    bool pullenemy = false;
    float ChestRate = 0.0f;
    float HeadRate = 0.0f;
    bool DiveKill = false;
    bool IgnoreKnock = false;
    bool SpeedTimer = false;
    bool ScaleDown = false;
    bool WallBug = false;
    bool SpinBot = false;
    bool GhostHack = false;
    bool KillInChest = false;
    bool TPMark = false;
    bool AimLag = false;
    bool PCMovement = false;
    bool FlyHack = false;
    bool TeleKill = false;
} pPlayer;

struct {
    bool activar = false;
} Actived;

#endif // NINLITE_BOOLEAN_SERVER_H
