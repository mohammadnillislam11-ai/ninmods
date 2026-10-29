#pragma once
#include "Unity/Color.hpp"
#include <atomic>

// Define enum types first
enum BoxType {
    BOX_STROKE = 0,
    BOX_FILLED = 1,
    BOX_CORNER = 2,
    BOX_ROUNDED = 3
};

enum LineType {
    LINE_TOP = 0,
    LINE_CENTER = 1,
    LINE_BOTTOM = 2
};

#if defined(__clang__)
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
#endif

/**
 * FEATURE FLAG STATE
 * ------------------
 * These flags are written by the Java/JNI thread (every switch, slider and
 * dropdown in the menu) and read by the GL render thread, the scene worker
 * thread and the aim worker threads at the same time.
 *
 * They used to be plain bool/int/float members, which is a data race: a
 * reader could observe a value that was never written. Every scalar is
 * therefore a std::atomic now. All of them are <= 4 bytes, so they are
 * lock-free on AArch64 and cost nothing.
 *
 * `espColor` stays a plain Color on purpose: it is 16 bytes, and a
 * std::atomic<Color> would need 16 byte atomics. A torn read can only ever
 * produce a slightly wrong ESP colour - never a crash or a bad memory
 * access - and the colour only changes when the user picks a new one.
 */
inline struct PlayerState {
    std::atomic<bool> aimbot{false};
    std::atomic<bool> aimhard{false};
    std::atomic<bool> silen4a{false};
    std::atomic<float> AimFov{60.0f};
    std::atomic<float> AimDistance{150.0f};
    std::atomic<bool> NoRecoil{false};
    std::atomic<bool> aimbotlock{false};
    std::atomic<bool> SpeedHackV2{false};
    std::atomic<bool> undercam{false};
    std::atomic<bool> PlayerToMe{false};
    std::atomic<bool> silentAimHead{true};
    std::atomic<float> gameSpeed{1.0f};

    std::atomic<bool> aimMagnet{false};
    std::atomic<int> headshotRate{100};
    std::atomic<bool> ignoreBots{true};
    std::atomic<bool> ignoreKnocked{true};

    /* Reference engine aim options. */
    std::atomic<bool> KillInChest{false};  // aim into the chest when the target is weak
    std::atomic<int> ChestRate{0};         // how many of every ten ticks aim into the chest
    std::atomic<int> HeadRate{0};          // how many of every ten ticks aim into the head

    // Extra delay (milliseconds) applied between two aim writes.
    // Backs the "Legit Delay" control of the NINLITE menu.
    std::atomic<float> legitDelayMs{0.0f};

} pPlayer;


inline struct ActivedState {
    std::atomic<bool> Bypass{false};
    std::atomic<bool> activar{true}; // Always true: individual toggles drive rendering
} Actived;

inline struct EspPlayerState {
    std::atomic<bool> espLinha{false};
    std::atomic<bool> espCaixa{false};
    std::atomic<bool> espHealth{false};    // Renamed from espInfo for clarity
    std::atomic<bool> espDistance{false};  // Renamed from espDistancia
    std::atomic<bool> espName{false};
    std::atomic<bool> espDrawCount{false};
    std::atomic<bool> espSkeleton{false};
    std::atomic<bool> espIden360{false};
    std::atomic<float> textSize{15.0f};
    Color espColor = Color::White();
    std::atomic<float> thickness{1.0f};
    std::atomic<BoxType> boxType{BOX_STROKE};
    std::atomic<LineType> lineType{LINE_TOP};

    /* Reference engine options. */
    std::atomic<bool> espCount{false};   // the threat counter (Enemy / Behind)
    std::atomic<bool> espWeapon{false};  // the weapon name below the box
    std::atomic<bool> espInfo{true};     // the info line at the bottom of the screen
} pEspPlayer;

inline std::atomic<bool> showFOVCircle{true};

#if defined(__clang__)
#pragma clang diagnostic pop
#endif
