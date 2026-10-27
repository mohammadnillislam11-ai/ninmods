#ifndef NINLITE_BOOLEAN_SERVER_H
#define NINLITE_BOOLEAN_SERVER_H

#include <atomic>

/**
 * DAEMON FEATURE FLAGS
 * --------------------
 * These flags are written by the socket thread (ApplyRequest) and read by the
 * two detached aim threads at the same time. They used to be plain bool/float
 * members, which is a data race. Every scalar is a std::atomic now.
 *
 * The definitions live in a HEADER, so they must be inline - otherwise a
 * second translation unit would produce a duplicate symbol at link time.
 */
inline struct DaemonPlayerState {
    std::atomic<bool> aimhard{false};
    std::atomic<bool> selectTarget{false};
    std::atomic<bool> silen4a{false};
    std::atomic<float> AimFov{0.0f};
    std::atomic<float> AimDistance{250.0f};
    std::atomic<bool> aimbotlock{false};
    std::atomic<bool> aimlock{false};
    std::atomic<bool> pullenemy{false};
    std::atomic<float> ChestRate{0.0f};
    std::atomic<float> HeadRate{0.0f};
    // Legit / smooth delay applied between two aim writes (milliseconds).
    std::atomic<float> LegitDelay{0.0f};
    std::atomic<bool> DiveKill{false};
    std::atomic<bool> IgnoreKnock{false};
    std::atomic<bool> SpeedTimer{false};
    std::atomic<bool> ScaleDown{false};
    std::atomic<bool> WallBug{false};
    std::atomic<bool> SpinBot{false};
    std::atomic<bool> GhostHack{false};
    std::atomic<bool> KillInChest{false};
    std::atomic<bool> TPMark{false};
    std::atomic<bool> AimLag{false};
    std::atomic<bool> PCMovement{false};
    std::atomic<bool> FlyHack{false};
    std::atomic<bool> TeleKill{false};
} pPlayer;

inline struct DaemonActivedState {
    std::atomic<bool> activar{false};
} Actived;

#endif // NINLITE_BOOLEAN_SERVER_H
