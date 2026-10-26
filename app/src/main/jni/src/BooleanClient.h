#pragma once
#include "Unity/Color.hpp"

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

inline struct PlayerState {
    bool aimbot = false;
    bool aimhard = false;
    bool fovawm = false;
    bool selectTarget = false;
    bool silen4a = false; 
    float AimFov = 60;
    float AimDistance = 150.0f;
    bool NoRecoil = false;
    bool aimbotlock = false;
    bool SpeedHackV2 = false;
    bool undercam = false;
    bool PlayerToMe = false;
    float AimFovScale = 1.20f;
    bool targetByDistance = false;
    bool silentAimHead = true;
    float gameSpeed = 1.0f;
    
    bool aimMagnet = false;   
    int headshotRate = 100;
    bool ignoreBots = true;
    bool ignoreKnocked = true;

} pPlayer;


inline struct ActivedState {
    bool Bypass = false;
    bool activar = false;
} Actived;

inline struct EspPlayerState {
    bool espLinha = false;
    bool espCaixa = false;
    bool espHealth = false;    // Renamed from espInfo for clarity
    bool espDistance = false;  // Renamed from espDistancia
    bool espName = false;
    bool espDrawCount = false;
    bool espSkeleton = false;
    bool espIden360 = false;
    float textSize = 15.0f;    
    Color espColor = Color::White();
    float thickness = 1.0f;
    BoxType boxType = BOX_STROKE;
    LineType lineType = LINE_TOP;
} pEspPlayer;

inline bool showFOVCircle = true;

#if defined(__clang__)
#pragma clang diagnostic pop
#endif
