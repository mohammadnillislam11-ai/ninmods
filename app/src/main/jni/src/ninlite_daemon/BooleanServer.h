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
    std::atomic<bool> silen4a{false};
    /* The aim defaults have to match the kernel engine (BooleanClient.h).
     * They used to differ (AimFov 0 vs 60, AimDistance 250 vs 150), so the
     * Daemon aimed at a completely different range than the kernel engine
     * until the first request reached it. */
    std::atomic<float> AimFov{60.0f};
    std::atomic<float> AimDistance{150.0f};
    std::atomic<bool> aimbotlock{false};
    std::atomic<bool> aimlock{false};
    std::atomic<bool> pullenemy{false};
    std::atomic<float> ChestRate{0.0f};
    std::atomic<float> HeadRate{0.0f};
    // Legit / smooth delay applied between two aim writes (milliseconds).
    std::atomic<float> LegitDelay{0.0f};
    std::atomic<bool> DiveKill{false};
    /* The default has to match the kernel engine (BooleanClient.h), where
     * ignoreKnocked starts as true. With false here the Daemon aimed at
     * knocked players until the first MODE_IGNORE_KNOCK request arrived,
     * so the two engines behaved differently on a fresh start. */
    std::atomic<bool> IgnoreKnock{true};
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
