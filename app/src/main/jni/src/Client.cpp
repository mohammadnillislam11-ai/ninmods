#include <pthread.h>
#include <jni.h>
#include <src/Socket/client.h>
#include "src/Includes/obfuscate.h"
#include <sys/stat.h>
#include <src/Widgets/ImportWidgets.h>
#include <src/Includes/http.h>
#include "src/Unity/Vector3.hpp"
#include "src/Unity/Vector2.hpp"
#include "src/Unity/Unity.h"
#include "src/Unity/ESP.h"
#include "Includes/Logger.h"
#include "Includes/Utils.h"
#include "src/Widgets/Methods.h"

#include "Client.h"
#include "BooleanClient.h"
#include <cstring>
#include <ctime>
#include <random>
#include <sstream>
#include <iomanip>
#include <algorithm>
#include <fstream>
#include <sys/ptrace.h>

#include <unistd.h>
#include <dlfcn.h>
#include <elf.h>                // For ELF structures
#include <thread>


// ============================================
// GLOBAL SYSTEM STATE & PROFILE SETTINGS
// ============================================
static int g_hackVersion = 1;

inline bool isButrulVersion() {
    return g_hackVersion == 2;
}

inline bool securityChecksPassed() {
    return true;
}

inline bool NativeIsLoggedIn() {
    return true;
}

// ============================================
// EXISTING GLOBAL VARIABLES AND FUNCTIONS
// ============================================

float g_espLeftAdjust = 0.0f;
float g_espRightAdjust = 0.0f;

static bool showFOVCircle = false;

struct MemoryChunk {
    char *memory;
    size_t size;
    MemoryChunk() : memory(nullptr), size(0) {}
    ~MemoryChunk() {
        if (memory) {
            free(memory);
            memory = nullptr;
        }
    }
};

static size_t WriteMemoryCallback(void *contents, size_t size, size_t nmemb, void *userp) {
    size_t realsize = size * nmemb;
    MemoryChunk *mem = (MemoryChunk *)userp;
    char *new_memory = (char *)realloc(mem->memory, mem->size + realsize + 1);
    if (new_memory == NULL) return 0;
    mem->memory = new_memory;
    memcpy(&(mem->memory[mem->size]), contents, realsize);
    mem->size += realsize;
    mem->memory[mem->size] = 0;
    return realsize;
}

std::string XOR_decryption(std::string value, std::string key) {
    std::string text = "";
    int klen = key.length();
    for (int v = 0; v < value.length(); v++) {
        text += value[v] ^ key[v % klen];
    }
    return text;
}

std::string get_Key_From_Public(std::string publicKey) {
    if (publicKey.length() < 98) return "000000000";
    std::string keyDes = "";
    keyDes += publicKey[58 - 1];
    keyDes += publicKey[62 - 1];
    keyDes += publicKey[10 - 1];
    keyDes += publicKey[47 - 1];
    keyDes += publicKey[20 - 1];
    keyDes += publicKey[33 - 1];
    keyDes += publicKey[87 - 1];
    keyDes += publicKey[98 - 1];
    keyDes += publicKey[14 - 1];
    return keyDes;
}

#define game_package OBFUSCATE("com.dts.freefireth")
#define LibBypass OBFUSCATE("libanogs.so")

struct {
    bool connectClient = false;
    std::string server_msg = "Waiting...";
} Connection;

ESP espOverlay;

void DrawFOVCircle(ESP esp, float fov, Color color) {
    if (fov <= 0) return;

    auto screenHeight = esp.getHeight();
    auto screenWidth = esp.getWidth();

    float centerX = screenWidth / 2.0f;
    float centerY = screenHeight / 2.0f;

    float radius = (fov / 180.0f) * (screenHeight / 2.0f);
    radius *= pPlayer.AimFovScale;
    if (radius < 40.0f) radius = 40.0f;
    
    Vector2 centerPos(centerX, centerY);
    esp.DrawCircle(color, 2.0f, centerPos, radius);
    
    float crossSize = 15.0f;
    esp.DrawLine(color, 1.5f, 
                 Vector3(centerX - crossSize, centerY, 0), 
                 Vector3(centerX + crossSize, centerY, 0));
    esp.DrawLine(color, 1.5f, 
                 Vector3(centerX, centerY - crossSize, 0), 
                 Vector3(centerX, centerY + crossSize, 0));
}

void DrawESP(ESP esp) {
    auto screenHeight = esp.getHeight();
    auto screenWidth = esp.getWidth();
    
    if (!isConnected()) return;

    if (Actived.activar && pPlayer.silen4a && showFOVCircle && pPlayer.AimFov > 0) {
        DrawFOVCircle(esp, pPlayer.AimFov, pEspPlayer.espColor);
    }

    auto response = getData(screenWidth, screenHeight);
    if (!response.Success) return;

    // The following block is removed because Response no longer contains VisibleEnemyCount etc.
    // (If you want to keep a counter, you'd need to add those fields to the Response struct
    //  and have the server fill them. For now, just skip drawing the counter.)

    if (response.PlayerCount <= 0) return;

    float globalThickness = pEspPlayer.thickness;

    for (int i = 0; i < response.PlayerCount; i++) {
        auto player = response.Players[i];
        if (player.Head == Vector3::Zero() || player.Toe == Vector3::Zero()) continue;
        if (!Actived.activar) continue;

        float distance = player.Distancia;
        if (distance < 0.1f || distance > 150.0f) continue;

        float headToFeetHeight = std::abs(player.Toe.Y - player.Head.Y);
        float boxHeight = headToFeetHeight * 1.15f; 
        float width = boxHeight * 0.48f;
        
        float totalXAdjust = g_espRightAdjust - g_espLeftAdjust;
        
        float boxY = player.Head.Y - (boxHeight * 0.12f);
        float boxX = player.Head.X - (width / 2.0f) + totalXAdjust;

        Color currentBoxColor = pEspPlayer.espColor;
        if (player.Name) currentBoxColor = Color::Red();        // player.Name == true -> bot
        if (player.IsCaido) currentBoxColor = Color::Red();     // knocked (changed from Yellow to Red)
        Color strokeColor = Color(0, 0, 0, 255);
        float fSize = pEspPlayer.textSize;
        
        float adjustedHeadX = player.Head.X + totalXAdjust;
        
        if (pEspPlayer.espLinha) {
            Vector3 lineStart, lineEnd;
            if (pEspPlayer.lineType == LINE_TOP) lineStart = Vector3(screenWidth / 2, 0, 0);
            else if (pEspPlayer.lineType == LINE_CENTER) lineStart = Vector3(screenWidth / 2, screenHeight / 2, 0);
            else lineStart = Vector3(screenWidth / 2, screenHeight, 0);

            lineEnd = (pEspPlayer.lineType == LINE_BOTTOM) ? 
                     Vector3(adjustedHeadX, boxY + boxHeight, 0) : 
                     Vector3(adjustedHeadX, boxY, 0);
            esp.DrawLine(currentBoxColor, globalThickness, lineStart, lineEnd);
        }

        if (pEspPlayer.espCaixa) {
            Rect playerRect(boxX, boxY, width, boxHeight);
            if (pEspPlayer.boxType == BOX_FILLED || pEspPlayer.boxType == BOX_ROUNDED) {
                Color fill = currentBoxColor; fill.a = 70;
                esp.DrawFilledRect(fill, Vector3(boxX, boxY, 0), width, boxHeight);
            }
            if (pEspPlayer.boxType == BOX_CORNER) 
                esp.DrawBox4Line(boxX, boxY, width, boxHeight, currentBoxColor, globalThickness);
            else 
                esp.DrawBox(currentBoxColor, globalThickness, playerRect);
        }
        
        // Client.cpp – inside DrawESP(), after getting response
if (Actived.activar && pEspPlayer.espDrawCount) {
        const bool showOnScreen = response.VisibleEnemyCount > 0;
        const bool showNearScreen = response.VisibleNearOffScreenEnemyCount > 0;
        const bool showProximity = response.ProximityEnemyCount > 0;
        const bool noThreatVisible = !showOnScreen && !showNearScreen && !showProximity;

        std::string onScreenLabel = std::to_string(response.VisibleEnemyCount);
        std::string nearScreenLabel = std::to_string(response.VisibleNearOffScreenEnemyCount);
        std::string proximityLabel = std::to_string(response.ProximityEnemyCount);

        float countSize = pEspPlayer.textSize + 10.0f;
        if (countSize < 24.0f) countSize = 24.0f;

        float topMargin = 80.0f;
        float sectionWidth = 120.0f;
        float sectionHeight = countSize + 22.0f;
        float sectionGap = 14.0f;
        
        int activeSections = (showOnScreen ? 1 : 0) + (showNearScreen ? 1 : 0) + (showProximity ? 1 : 0);
        float totalWidth = sectionWidth * activeSections + sectionGap * (activeSections - 1);
        float startX = (screenWidth - totalWidth) / 2.0f;

        float backgroundY = topMargin;
        float countY = backgroundY + sectionHeight / 2.0f;
        float cornerRadius = sectionHeight * 0.30f;
        Color outlineColor = Color(0, 0, 0, 220);
        Color textColor = Color::White();

        auto DrawRoundedPanel = [&](float x, float y, float width, float height, float radius, Color color) {
            float clampedRadius = std::max(1.0f, std::min(radius, std::min(width, height) * 0.5f));
            esp.DrawFilledRect(color, Vector3(x + clampedRadius, y, 0), width - (clampedRadius * 2.0f), height);
            esp.DrawFilledRect(color, Vector3(x, y + clampedRadius, 0), clampedRadius, height - (clampedRadius * 2.0f));
            esp.DrawFilledRect(color, Vector3(x + width - clampedRadius, y + clampedRadius, 0), clampedRadius, height - (clampedRadius * 2.0f));
            esp.DrawFilledCircle(color, Vector2(x + clampedRadius, y + clampedRadius), clampedRadius);
            esp.DrawFilledCircle(color, Vector2(x + width - clampedRadius, y + clampedRadius), clampedRadius);
            esp.DrawFilledCircle(color, Vector2(x + clampedRadius, y + height - clampedRadius), clampedRadius);
            esp.DrawFilledCircle(color, Vector2(x + width - clampedRadius, y + height - clampedRadius), clampedRadius);
        };

        if (noThreatVisible) {
            float safeWidth = 200.0f;
            float safeX = (screenWidth - safeWidth) / 2.0f;
            DrawRoundedPanel(safeX, backgroundY, safeWidth, sectionHeight, cornerRadius, Color(22, 165, 70, 235));
            const char* safeLabel = "SAFE";
            float safeTextX = safeX + (safeWidth * 0.5f);
            esp.DrawText(outlineColor, safeLabel, Vector2(safeTextX - 1, countY - 1), countSize - 2.0f);
            esp.DrawText(outlineColor, safeLabel, Vector2(safeTextX + 1, countY + 1), countSize - 2.0f);
            esp.DrawText(textColor, safeLabel, Vector2(safeTextX, countY), countSize - 2.0f);
        } else {
            Color onScreenBg = Color(255, 215, 0, 230);     // Yellow
            Color nearScreenBg = Color(235, 30, 30, 230);   // Red
            Color proximityBg = Color(255, 140, 0, 230);    // Orange
            float nextSectionX = startX;

            if (showOnScreen) {
                DrawRoundedPanel(nextSectionX, backgroundY, sectionWidth, sectionHeight, cornerRadius, onScreenBg);
                float centerX = nextSectionX + (sectionWidth * 0.5f);
                esp.DrawText(outlineColor, onScreenLabel.c_str(), Vector2(centerX - 1, countY - 1), countSize);
                esp.DrawText(outlineColor, onScreenLabel.c_str(), Vector2(centerX + 1, countY + 1), countSize);
                esp.DrawText(textColor, onScreenLabel.c_str(), Vector2(centerX, countY), countSize);
                nextSectionX += sectionWidth + sectionGap;
            }

            if (showNearScreen) {
                DrawRoundedPanel(nextSectionX, backgroundY, sectionWidth, sectionHeight, cornerRadius, nearScreenBg);
                float centerX = nextSectionX + (sectionWidth * 0.5f);
                esp.DrawText(outlineColor, nearScreenLabel.c_str(), Vector2(centerX - 1, countY - 1), countSize);
                esp.DrawText(outlineColor, nearScreenLabel.c_str(), Vector2(centerX + 1, countY + 1), countSize);
                esp.DrawText(textColor, nearScreenLabel.c_str(), Vector2(centerX, countY), countSize);
                nextSectionX += sectionWidth + sectionGap;
            }

            if (showProximity) {
                DrawRoundedPanel(nextSectionX, backgroundY, sectionWidth, sectionHeight, cornerRadius, proximityBg);
                float centerX = nextSectionX + (sectionWidth * 0.5f);
                esp.DrawText(outlineColor, proximityLabel.c_str(), Vector2(centerX - 1, countY - 1), countSize);
                esp.DrawText(outlineColor, proximityLabel.c_str(), Vector2(centerX + 1, countY + 1), countSize);
                esp.DrawText(textColor, proximityLabel.c_str(), Vector2(centerX, countY), countSize);
            }
        }
    }
    
    // Show FOV circle if any aim feature is active
    bool anyAimActive = pPlayer.aimbot || pPlayer.silen4a || pPlayer.aimMagnet;
    if (Actived.activar && anyAimActive && showFOVCircle && pPlayer.AimFov > 0) {
        DrawFOVCircle(esp, pPlayer.AimFov, pEspPlayer.espColor);
    }

        if (pEspPlayer.espName || pEspPlayer.espDistance || pEspPlayer.espHealth) {
            std::string drawName;
            if (pEspPlayer.espName) {
                if (player.Name) drawName = "Bot";
                else if (player.PlayerName[0] != '\0') drawName = player.PlayerName;
                else drawName = "Unknown";
            }

            std::string dStr;
            if (pEspPlayer.espDistance) dStr = std::to_string((int)distance) + "M";

            const bool hasName = pEspPlayer.espName && !drawName.empty();
            const bool hasDistance = pEspPlayer.espDistance && !dStr.empty();
            const bool hasHealth = pEspPlayer.espHealth;

            if (hasName || hasDistance || hasHealth) {
    float fontSize = std::max(10.0f, fSize - 1.0f);
    float spacing = 6.0f;                 // gap between name and distance segments
    float segmentPadding = 6.0f;           // internal padding inside each segment
    float layoutPadding = 6.0f;            // padding around the whole panel
    float textHeight = fontSize + 5.0f;     // height of text background

    // Calculate name text width (approximate)
    float nameTextWidth = hasName ? (drawName.length() * (fontSize * 0.56f)) : 0.0f;
    float rawNameSegmentWidth = (nameTextWidth > 0.0f) ? (nameTextWidth + segmentPadding * 2.0f) : 0.0f;
    float nameSegmentWidth = rawNameSegmentWidth;

    // Fixed width for distance segment
    const float fixedDistWidth = 80.0f;      // adjust as needed
    const float defaultHealthWidth = 60.0f;  // default health bar width when no name/distance

    float distSegmentWidth = hasDistance ? fixedDistWidth : 0.0f;

    // ----- SYNCHRONIZE NAME BACKGROUND AND HEALTH BAR WIDTHS (existing logic) -----
    float healthBarWidth;
    if (hasName) {
        float baseHealthWidth = hasDistance ? fixedDistWidth : defaultHealthWidth;
        healthBarWidth = std::max(nameSegmentWidth, baseHealthWidth);
        nameSegmentWidth = healthBarWidth;   // name background expands to match health bar
    } else {
        healthBarWidth = hasDistance ? fixedDistWidth : defaultHealthWidth;
    }

    // ----- NEW LOGIC: When all three are present, unify total name+distance width with health bar -----
    if (hasName && hasDistance && hasHealth) {
        float combinedWidth = nameSegmentWidth + distSegmentWidth + spacing; // total width of both segments plus gap
        if (combinedWidth > healthBarWidth) {
            // Need to shrink combined width to match health bar
            float excess = combinedWidth - healthBarWidth;
            // First try to reduce the name segment
            float newNameWidth = nameSegmentWidth - excess;
            // Ensure name doesn't go below its text-based minimum (to avoid clipping)
            float minNameWidth = rawNameSegmentWidth; // original text width
            if (newNameWidth < minNameWidth) {
                // If name would become too small, we also need to shrink the distance segment
                // Calculate how much we need to take from distance
                float nameDeficit = minNameWidth - newNameWidth; // amount name would go below min
                newNameWidth = minNameWidth; // set name to minimum
                // Reduce distance by the remaining deficit
                float newDistWidth = distSegmentWidth - nameDeficit;
                // Ensure distance doesn't go below a reasonable minimum (e.g., 30)
                const float minDistWidth = 30.0f;
                if (newDistWidth < minDistWidth) {
                    // If both would go below min, we clamp and accept that combined may still be > healthBarWidth
                    newDistWidth = minDistWidth;
                }
                distSegmentWidth = newDistWidth;
            }
            nameSegmentWidth = newNameWidth;
            // Recalculate combined width after adjustments
            combinedWidth = nameSegmentWidth + distSegmentWidth + spacing;
            // If still larger, we may need to further adjust, but we'll leave it as is (health bar remains the target)
        } else if (combinedWidth < healthBarWidth) {
            // Shrink health bar to match combined width
            healthBarWidth = combinedWidth;
        }
    }

    const float healthBarHeight = 6.0f;       // constant height

    // Total width required for text segments (name + distance + spacing)
    float contentWidth = nameSegmentWidth + distSegmentWidth;
    if (nameSegmentWidth > 0.0f && distSegmentWidth > 0.0f) contentWidth += spacing;

    bool hasText = hasName || hasDistance;   // are we showing any text?

    // ----- TEXT PANEL WIDTH (adapts to name + distance) -----
    float layoutInnerWidth;
    if (hasText) {
        layoutInnerWidth = contentWidth;
    } else {
        layoutInnerWidth = defaultHealthWidth; // health‑only mode
    }

    // ----- TEXT PANEL POSITIONING -----
    float layoutHeight = 0.0f;
    float roundedPanelWidth = 0.0f;
    float roundedPanelHeight = 0.0f;
    float layoutX = 0.0f;
    float layoutY = 0.0f;

    if (hasText) {
        layoutHeight = textHeight + layoutPadding * 2.0f;
        roundedPanelWidth = layoutInnerWidth + layoutPadding * 2.0f;
        roundedPanelHeight = layoutHeight;

        layoutX = adjustedHeadX - (layoutInnerWidth * 0.5f) - layoutPadding;
        layoutY = boxY - roundedPanelHeight - 8.0f;
        if (layoutY < 2.0f) layoutY = 2.0f;
    }

    // Helper to draw rounded panels
    auto DrawRoundedPanel = [&](float x, float y, float width, float height, float radius, Color color) {
        float clampedRadius = std::max(1.0f, std::min(radius, std::min(width, height) * 0.5f));
        esp.DrawFilledRect(color, Vector3(x + clampedRadius, y, 0), width - clampedRadius * 2.0f, height);
        esp.DrawFilledRect(color, Vector3(x, y + clampedRadius, 0), clampedRadius, height - clampedRadius * 2.0f);
        esp.DrawFilledRect(color, Vector3(x + width - clampedRadius, y + clampedRadius, 0), clampedRadius, height - clampedRadius * 2.0f);
        esp.DrawFilledCircle(color, Vector2(x + clampedRadius, y + clampedRadius), clampedRadius);
        esp.DrawFilledCircle(color, Vector2(x + width - clampedRadius, y + clampedRadius), clampedRadius);
        esp.DrawFilledCircle(color, Vector2(x + clampedRadius, y + height - clampedRadius), clampedRadius);
        esp.DrawFilledCircle(color, Vector2(x + width - clampedRadius, y + height - clampedRadius), clampedRadius);
    };

    // Draw main text panel background
    if (hasText) {
        DrawRoundedPanel(layoutX, layoutY, roundedPanelWidth, roundedPanelHeight, 4.0f, Color(0, 0, 0, 170));
    }

    // ----- DRAW TEXT SEGMENTS -----
    if (hasText) {
        float contentStartX = adjustedHeadX - (layoutInnerWidth * 0.5f);
        float currentX = contentStartX;
        float textCenterY = layoutY + layoutPadding + (textHeight * 0.5f);

        // Name segment (width may have been adjusted)
        if (nameSegmentWidth > 0.0f) {
            DrawRoundedPanel(currentX, layoutY + layoutPadding, nameSegmentWidth, textHeight, 3.0f, Color(30, 30, 30, 210));
            float nameCenterX = currentX + nameSegmentWidth * 0.5f;
            esp.DrawText(strokeColor, drawName.c_str(), Vector2(nameCenterX - 1, textCenterY - 1), fontSize);
            esp.DrawText(strokeColor, drawName.c_str(), Vector2(nameCenterX + 1, textCenterY + 1), fontSize);
            esp.DrawText(Color::White(), drawName.c_str(), Vector2(nameCenterX, textCenterY), fontSize);
            currentX += nameSegmentWidth + (distSegmentWidth > 0.0f ? spacing : 0.0f);
        }

        // Distance segment (width may have been adjusted if needed)
        if (distSegmentWidth > 0.0f) {
            DrawRoundedPanel(currentX, layoutY + layoutPadding, distSegmentWidth, textHeight, 3.0f, Color(30, 30, 30, 210));
            float distCenterX = currentX + distSegmentWidth * 0.5f;
            Color distColor = currentBoxColor;
            esp.DrawText(strokeColor, dStr.c_str(), Vector2(distCenterX - 1, textCenterY - 1), fontSize);
            esp.DrawText(strokeColor, dStr.c_str(), Vector2(distCenterX + 1, textCenterY + 1), fontSize);
            esp.DrawText(distColor, dStr.c_str(), Vector2(distCenterX, textCenterY), fontSize);
        }
    }

    // ----- DRAW HEALTH BAR (width may have been adjusted) -----
    if (hasHealth) {
        float hMax = player.Name ? 100.0f : 200.0f;
        float hpPercent = std::min(1.0f, std::max(0.0f, player.Health / hMax));

        // Determine Y position
        float healthY;
        if (hasText) {
            healthY = layoutY + roundedPanelHeight + 2.0f; // below text panel
        } else {
            healthY = boxY - healthBarHeight - 8.0f;
            if (healthY < 2.0f) healthY = 2.0f;
        }

        // Center the health bar horizontally
        float healthBarX = adjustedHeadX - healthBarWidth * 0.5f;

        // Draw background
        DrawRoundedPanel(healthBarX, healthY, healthBarWidth, healthBarHeight,
                         healthBarHeight * 0.5f, Color(45, 45, 45, 210));

        // Draw health fill
        float hpWidth = healthBarWidth * hpPercent;
        if (hpWidth > 0.0f) {
            Color hpColor = Color(0, 255, 0, 220);
            if (hpPercent <= 0.6f) hpColor = Color(255, 210, 0, 220);
            if (hpPercent < 0.4f) hpColor = Color(255, 70, 70, 220);

            DrawRoundedPanel(healthBarX, healthY, hpWidth, healthBarHeight,
                             healthBarHeight * 0.5f, hpColor);
        }
    }
}
        }
        
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_power_cheats_Floater_Functions(JNIEnv *env, jclass clazz) {
    Widget widget = Widget(env);

    widget.Category(OBFUSCATE("Aim"));
    widget.Switch(OBFUSCATE("Enable Functions"), 1);
    widget.Switch(OBFUSCATE("Aim External"), 6);
    // Version-agnostic features
    widget.Switch(OBFUSCATE("Aimbot"), 2);
    
    if (isButrulVersion()) {
        widget.Switch(OBFUSCATE("Aim Silent"), 8);
        widget.Switch(OBFUSCATE("Aim Magnet"), 63);
    }

    if (isButrulVersion()) {
        widget.Switch(OBFUSCATE("Speed Hack Enable"), 60);
        widget.SeekBar(OBFUSCATE("Game Speed"), 10, 50, OBFUSCATE("SpeedValue"), 25);
    }

    // Tab Order: Brutal -> Aim Setting is Second
    if (isButrulVersion()) {
        widget.Category(OBFUSCATE("Aim Setting"));
        widget.SeekBar(OBFUSCATE("Silent Aim Target"), 0, 1, OBFUSCATE("SilentAimTarget"), 61);
        widget.SeekBar(OBFUSCATE("Target Priority"), 0, 1, OBFUSCATE("TargetPriority"), 62);
        widget.SeekBar(OBFUSCATE("Aim FOV Radius"), 10, 180, OBFUSCATE(""), 9);
        widget.SeekBar(OBFUSCATE("Aim Distance"), 10, 200, OBFUSCATE(""), 10);
        widget.SeekBar(OBFUSCATE("Headshot %"), 0, 100, OBFUSCATE(""), 70);
    }
    
    widget.Category(OBFUSCATE("Esp"));
    widget.Switch(OBFUSCATE("Draw Line"), 11);
    widget.Switch(OBFUSCATE("Draw Distance"), 13);
    widget.Switch(OBFUSCATE("Draw Aim FOV"), 27);
    widget.Switch(OBFUSCATE("Draw Box"), 12);
    widget.Switch(OBFUSCATE("Draw Name"), 21);
    widget.Switch(OBFUSCATE("Draw Health"), 19);
    widget.Switch(OBFUSCATE("Draw Count"), 23);

    widget.Category(OBFUSCATE("Esp Setting"));
    widget.SeekBar(OBFUSCATE("ESP Color"), 0, 9, OBFUSCATE("Color"), 16);
    widget.SeekBar(OBFUSCATE("Box Type"), 0, 2, OBFUSCATE("BoxType"), 17);
    widget.SeekBar(OBFUSCATE("Line Type"), 0, 2, OBFUSCATE("LineType"), 18);
    widget.SeekBar(OBFUSCATE("Text Size"), 10, 15, OBFUSCATE(""), 20);
    widget.SeekBar(OBFUSCATE("Thickness"), 1, 10, OBFUSCATE(""), 22);
    
    widget.Category(OBFUSCATE("Position Fix"));
    widget.SeekBar(OBFUSCATE("ESP Left Adjust"), 0, 100, OBFUSCATE(""), 30);
    widget.SeekBar(OBFUSCATE("ESP Right Adjust"), 0, 100, OBFUSCATE(""), 31);
    widget.Switch(OBFUSCATE("Reset Adjust"), 32);

    // Tab Order: Safe -> Aim Setting is Last
    if (!isButrulVersion()) {
        widget.Category(OBFUSCATE("Aim Setting"));
        widget.SeekBar(OBFUSCATE("Aim FOV Radius"), 10, 180, OBFUSCATE(""), 9);
        widget.SeekBar(OBFUSCATE("Aim Distance"), 10, 200, OBFUSCATE(""), 10);
        widget.SeekBar(OBFUSCATE("Headshot %"), 0, 100, OBFUSCATE(""), 70);
    }
}
   
    



extern "C"
JNIEXPORT void JNICALL
Java_power_cheats_Floater_ChangesID(JNIEnv *env, jclass clazz, jint id, jint value) {

    // Guard: only send if connected
    if (!isConnected()) {
        LOGD("WARNING: Socket not connected, ignoring toggle ID: %d", id);
        // Still update local state if needed (e.g., ESP settings)
        // ... (local state updates remain)
        return;
    }

    if (!isButrulVersion()) {
        switch (id) {
            case 2:
            case 3:
            case 8:
            case 9:
            case 10:
            case 25:
            case 33:
            case 34:
            case 62:
            case 63:
                LOGD("BLOCKED: write feature on safe mode (ID: %d)", id);
                return;
            default:
                break;
        }
    }
    
    switch (id) {
        case 1: Actived.activar = !Actived.activar; SendBool(3, Actived.activar); break;
        case 2: pPlayer.aimbot = !pPlayer.aimbot; SendBool(5, pPlayer.aimbot); break;
        case 3: pPlayer.fovawm = !pPlayer.fovawm; SendBool(22, pPlayer.fovawm); break;
        case 6: pPlayer.aimbotlock = !pPlayer.aimbotlock; SendBool(54, pPlayer.aimbotlock); break;
        case 8: pPlayer.silen4a = !pPlayer.silen4a; SendBool(57, pPlayer.silen4a); break;
        case 61: pPlayer.silentAimHead = (value == 0); SendBool(61, pPlayer.silentAimHead); break;
        case 62: pPlayer.targetByDistance = (value == 1); SendBool(62, pPlayer.targetByDistance); break;
        case 63: pPlayer.aimMagnet = !pPlayer.aimMagnet; SendBool(63, pPlayer.aimMagnet); break;
        case 9: pPlayer.AimFov = value; SendFloat(58, pPlayer.AimFov); break;
        case 10: pPlayer.AimDistance = value; SendFloat(59, pPlayer.AimDistance); break;
        case 70: pPlayer.headshotRate = value; SendInt(70, pPlayer.headshotRate); break;
        case 71: pPlayer.ignoreBots = !pPlayer.ignoreBots; SendBool(71, pPlayer.ignoreBots); break;
        case 72: pPlayer.ignoreKnocked = !pPlayer.ignoreKnocked; SendBool(72, pPlayer.ignoreKnocked); break;
        case 11: pEspPlayer.espLinha = !pEspPlayer.espLinha; break;
        case 12: pEspPlayer.espCaixa = !pEspPlayer.espCaixa; break;
        case 13: pEspPlayer.espDistance = !pEspPlayer.espDistance; break;
        case 14: pEspPlayer.espSkeleton = !pEspPlayer.espSkeleton; break;
        case 15: pEspPlayer.espIden360 = !pEspPlayer.espIden360; break;
        case 16:
            if (value == 0) pEspPlayer.espColor = Color(255, 255, 255, 255);
            else if (value == 1) pEspPlayer.espColor = Color(0, 255, 0, 255);
            else if (value == 2) pEspPlayer.espColor = Color(0, 0, 255, 255);
            else if (value == 3) pEspPlayer.espColor = Color(255, 0, 0, 255);
            else if (value == 4) pEspPlayer.espColor = Color(0, 0, 0, 255);
            else if (value == 5) pEspPlayer.espColor = Color(255, 255, 0, 255);
            else if (value == 6) pEspPlayer.espColor = Color(0, 255, 255, 255);
            else if (value == 7) pEspPlayer.espColor = Color(255, 0, 255, 255);
            else if (value == 8) pEspPlayer.espColor = Color(128, 128, 128, 255);
            else if (value == 9) pEspPlayer.espColor = Color(160, 32, 240, 255);
            break;
        case 17: pEspPlayer.boxType = static_cast<BoxType>(value); break;
        case 18: pEspPlayer.lineType = static_cast<LineType>(value); break;
        case 19: pEspPlayer.espHealth = !pEspPlayer.espHealth; break;
        case 20: pEspPlayer.textSize = (float)value; break;
        case 21: pEspPlayer.espName = !pEspPlayer.espName; break;
        case 22: pEspPlayer.thickness = (float)value; break;
        case 23: pEspPlayer.espDrawCount = !pEspPlayer.espDrawCount; break;
        case 25: 
            pPlayer.gameSpeed = (float)value / 10.0f;
            SendInt(25, value);
            break;
        case 60: 
            pPlayer.SpeedHackV2 = !pPlayer.SpeedHackV2; // local toggle
            SendBool(60, pPlayer.SpeedHackV2);
            break;
        case 27: showFOVCircle = !showFOVCircle; break;
        case 30: g_espLeftAdjust = (float)value; break;
        case 31: g_espRightAdjust = (float)value; break;
        case 32:
            g_espLeftAdjust = 0.0f;
            g_espRightAdjust = 0.0f;
            break;
        case 33:
            SendBool(59, value != 0);
            break;
        case 34:
            SendFloat(60, (float)value);
            break;

    }
}


extern "C" JNIEXPORT void JNICALL
Java_power_cheats_Floater_PxbftKZXivSr(JNIEnv *env, jclass type, jobject espView, jobject canvas, jint width, jint height) {
    ESP localEsp = ESP(env, espView, canvas);
    if (localEsp.isValid()) { DrawESP(localEsp); }
}

extern "C" JNIEXPORT void JNICALL
Java_power_cheats_Floater_ftKZXivSr(JNIEnv *env, jclass clazz, jobject ctx) {
    if (!Connection.connectClient) {
        startClient();
        Connection.connectClient = true;
        Toast(env, ctx, OBFUSCATE("ACTIVATING..."), 1, 1);
        Toast(env, ctx, OBFUSCATE("MOD ACTIVATED"), 1, 1);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_power_cheats_Floater_ftKwCvSr(JNIEnv *env, jclass clazz) {
    stopClient();
    Connection.connectClient = false;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_power_cheats_Utils_getGamePackage(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("com.dts.freefireth"));
}

extern "C"
JNIEXPORT jstring JNICALL
Java_power_cheats_Utils_getDaemonName(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("Titoo"));
}

extern "C"
JNIEXPORT jstring JNICALL
Java_power_cheats_Floater_getPrefKeyHackVersion(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("profile_mode"));
}

extern "C"
JNIEXPORT jstring JNICALL
Java_power_cheats_Floater_getUiString(JNIEnv *env, jclass clazz, jint id) {
    switch (id) {
        case 1: return env->NewStringUTF(OBFUSCATE("NINMODS PRO"));
        case 2: return env->NewStringUTF(OBFUSCATE("Close"));
        case 3: return env->NewStringUTF(OBFUSCATE("Session valid"));
        default: return env->NewStringUTF(OBFUSCATE(""));
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_power_cheats_Floater_getSellerName(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("NINMODS_OFFICIAL"));
}

extern "C"
JNIEXPORT void JNICALL
Java_power_cheats_MainActivity_setProfileModeNative(JNIEnv *env, jobject thiz, jint mode) {
    g_hackVersion = (mode == 2) ? 2 : 1;
    LOGD("Profile mode selected from MainActivity: %d", g_hackVersion);
}

extern "C"
JNIEXPORT void JNICALL
Java_power_cheats_MainActivity_setHackVersionNative(JNIEnv *env, jobject thiz, jint mode) {
    g_hackVersion = (mode == 2) ? 2 : 1;
    LOGD("Profile mode selected from MainActivity (fallback): %d", g_hackVersion);
}

extern "C"
JNIEXPORT void JNICALL
Java_power_cheats_ConfigModeActivity_setProfileModeNative(JNIEnv *env, jclass clazz, jint version) {
    g_hackVersion = (version == 2) ? 2 : 1;
    LOGD("Profile mode selected: %d", g_hackVersion);
}

extern "C"
JNIEXPORT void JNICALL
Java_power_cheats_ConfigModeActivity_setHackVersionNative(JNIEnv *env, jclass clazz, jint version) {
    g_hackVersion = (version == 2) ? 2 : 1;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_power_cheats_Utils_decryptHexBladeError(JNIEnv *env, jclass clazz, jstring encryptedError) {
    const char *enc_error = env->GetStringUTFChars(encryptedError, 0);
    std::string error_str(enc_error);
    std::string decrypted;
    env->ReleaseStringUTFChars(encryptedError, enc_error);
    return env->NewStringUTF(decrypted.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_power_cheats_Utils_tryDecryptError(JNIEnv *env, jclass clazz) {
    std::string encrypted_error = OBFUSCATE("+/UQ5Vrzujmhrtl8r/suKPBvD1pzCiOK");
    std::string decrypted;
    return env->NewStringUTF(decrypted.c_str());
}

// ==============================================================
// NINMODS PRO (ninmods.pro) OFFICIAL JNI EXPORTS
// ==============================================================
extern "C" JNIEXPORT void JNICALL
Java_ninmods_pro_Floater_Functions(JNIEnv *env, jclass clazz) {
    Java_power_cheats_Floater_Functions(env, clazz);
}

extern "C" JNIEXPORT void JNICALL
Java_ninmods_pro_Floater_ChangesID(JNIEnv *env, jclass clazz, jint id, jint value) {
    Java_power_cheats_Floater_ChangesID(env, clazz, id, value);
}

extern "C" JNIEXPORT void JNICALL
Java_ninmods_pro_Floater_PxbftKZXivSr(JNIEnv *env, jclass type, jobject espView, jobject canvas, jint width, jint height) {
    Java_power_cheats_Floater_PxbftKZXivSr(env, type, espView, canvas, width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_ninmods_pro_Floater_ftKZXivSr(JNIEnv *env, jclass clazz, jobject ctx) {
    Java_power_cheats_Floater_ftKZXivSr(env, clazz, ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_ninmods_pro_Floater_ftKwCvSr(JNIEnv *env, jclass clazz) {
    Java_power_cheats_Floater_ftKwCvSr(env, clazz);
}

extern "C" JNIEXPORT jstring JNICALL
Java_ninmods_pro_Floater_getPrefKeyHackVersion(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("profile_mode"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_ninmods_pro_Floater_getUiString(JNIEnv *env, jclass clazz, jint id) {
    switch (id) {
        case 1: return env->NewStringUTF(OBFUSCATE("NINMODS PRO"));
        case 2: return env->NewStringUTF(OBFUSCATE("Close"));
        case 3: return env->NewStringUTF(OBFUSCATE("Session valid"));
        default: return env->NewStringUTF(OBFUSCATE(""));
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_ninmods_pro_Floater_getSellerName(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("NINMODS_OFFICIAL"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_ninmods_pro_Utils_getGamePackage(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("com.dts.freefireth"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_ninmods_pro_Utils_getDaemonName(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("Titoo"));
}

extern "C" JNIEXPORT void JNICALL
Java_ninmods_pro_MainActivity_setProfileModeNative(JNIEnv *env, jobject thiz, jint mode) {
    g_hackVersion = (mode == 2) ? 2 : 1;
    LOGD("Profile mode selected from MainActivity (ninmods.pro): %d", g_hackVersion);
}

extern "C" JNIEXPORT void JNICALL
Java_ninmods_pro_MainActivity_setHackVersionNative(JNIEnv *env, jobject thiz, jint mode) {
    g_hackVersion = (mode == 2) ? 2 : 1;
}

extern "C" JNIEXPORT void JNICALL
Java_ninmods_pro_ConfigModeActivity_setProfileModeNative(JNIEnv *env, jclass clazz, jint version) {
    g_hackVersion = (version == 2) ? 2 : 1;
}

extern "C" JNIEXPORT void JNICALL
Java_ninmods_pro_ConfigModeActivity_setHackVersionNative(JNIEnv *env, jclass clazz, jint version) {
    g_hackVersion = (version == 2) ? 2 : 1;
}

extern "C" JNIEXPORT void JNICALL
Java_ninmods_pro_MainActivity_showNativeAlertDialog(JNIEnv* env, jobject thiz) {
    // No-op
}

extern "C" JNIEXPORT void JNICALL
Java_nin_app_overlay_Floater_Functions(JNIEnv *env, jclass clazz) {
    Java_power_cheats_Floater_Functions(env, clazz);
}

extern "C" JNIEXPORT void JNICALL
Java_nin_app_overlay_Floater_ChangesID(JNIEnv *env, jclass clazz, jint id, jint value) {
    Java_power_cheats_Floater_ChangesID(env, clazz, id, value);
}

extern "C" JNIEXPORT void JNICALL
Java_nin_app_overlay_Floater_PxbftKZXivSr(JNIEnv *env, jclass type, jobject espView, jobject canvas, jint width, jint height) {
    Java_power_cheats_Floater_PxbftKZXivSr(env, type, espView, canvas, width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_nin_app_overlay_Floater_ftKZXivSr(JNIEnv *env, jclass clazz, jobject ctx) {
    Java_power_cheats_Floater_ftKZXivSr(env, clazz, ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_nin_app_overlay_Floater_ftKwCvSr(JNIEnv *env, jclass clazz) {
    Java_power_cheats_Floater_ftKwCvSr(env, clazz);
}

extern "C" JNIEXPORT jstring JNICALL
Java_nin_app_overlay_Floater_getPrefKeyModeVersion(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("config_mode"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_nin_app_overlay_Floater_getPrefKeyHackVersion(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("config_mode"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_nin_app_overlay_Floater_getUiString(JNIEnv *env, jclass clazz, jint id) {
    switch (id) {
        case 1: return env->NewStringUTF(OBFUSCATE("NINMODS"));
        case 2: return env->NewStringUTF(OBFUSCATE("Close"));
        case 3: return env->NewStringUTF(OBFUSCATE("Session valid"));
        default: return env->NewStringUTF(OBFUSCATE(""));
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_nin_app_overlay_Floater_getSellerName(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("NINMODS_OFFICIAL"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_nin_app_core_Utils_getGamePackage(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("com.dts.freefireth"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_nin_app_core_Utils_getDaemonName(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("Titoo"));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_nin_app_core_DashboardActivity_isLoggedInNative(JNIEnv *env, jclass clazz) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_nin_app_core_DashboardActivity_setModeNative(JNIEnv *env, jclass clazz, jint mode) {
    g_hackVersion = (mode == 2) ? 2 : 1;
    LOGD("Profile mode selected from DashboardActivity (nin.app): %d", g_hackVersion);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_nin_app_core_ModeSelectActivity_isLoggedInNative(JNIEnv *env, jclass clazz) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_nin_app_core_ModeSelectActivity_setModeNative(JNIEnv *env, jclass clazz, jint mode) {
    g_hackVersion = (mode == 2) ? 2 : 1;
    LOGD("Profile mode selected from ModeSelectActivity (nin.app): %d", g_hackVersion);
}

extern "C" JNIEXPORT void JNICALL
Java_nin_app_core_MainActivity_showNativeAlertDialog(JNIEnv* env, jobject thiz) {
    // No-op
}

extern "C" JNIEXPORT void JNICALL
Java_power_cheats_MainActivity_showNativeAlertDialog(JNIEnv* env, jobject thiz) {
    jclass toastClass = env->FindClass("android/widget/Toast");
    if (toastClass == nullptr) return;
    jmethodID makeText = env->GetStaticMethodID(toastClass, "makeText",
        "(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;");
    if (makeText == nullptr) return;
    jmethodID show = env->GetMethodID(toastClass, "show", "()V");
    if (show == nullptr) return;
    jstring message = env->NewStringUTF(OBFUSCATE("Native Library Loaded!"));
    jobject toast = env->CallStaticObjectMethod(toastClass, makeText, thiz, message, 0);
    if (toast != nullptr) env->CallVoidMethod(toast, show);
    env->DeleteLocalRef(message);
}

static bool isRooted() {
    struct stat st;
    if (stat("/system/xbin/su", &st) == 0) return true;
    if (stat("/system/bin/su", &st) == 0) return true;
    if (stat("/sbin/su", &st) == 0) return true;
    return false;
}

void StartDaemon() {
    bool isX86 = false;
#if defined(__x86_64__) || defined(__i386__)
    isX86 = true;
#endif

    const char* dName = isX86 ? "Titoo1" : "Titoo";
    const char* paths[] = {
        "/data/user/0/nin.app/cache/",
        "/data/data/nin.app/cache/",
        "/data/user/0/ninmods.pro/cache/",
        "/data/data/ninmods.pro/cache/"
    };
    
    char path[256];
    struct stat st;
    bool found = false;
    
    for (const char* p : paths) {
        snprintf(path, sizeof(path), "%s%s", p, dName);
        if (stat(path, &st) == 0) {
            found = true;
            break;
        }
    }

    if (!found) {
        LOGE("Daemon binary NOT FOUND at any expected cache path");
        return;
    }

    if (!isRooted()) {
        LOGD("Device is NOT rooted - launching as normal user");
        char cmd[512];
        snprintf(cmd, sizeof(cmd), "chmod 777 %s && %s &", path, path);
        system(cmd);
        return;
    }
    
    LOGI("Launching daemon as ROOT: %s", path);
    char pkill_cmd[256];
    snprintf(pkill_cmd, sizeof(pkill_cmd), "su -c \"pkill -9 -f %s\"", dName);
    system(pkill_cmd);
    
    char chmod_cmd[256];
    snprintf(chmod_cmd, sizeof(chmod_cmd), "su -c \"chmod 777 %s\"", path);
    system(chmod_cmd);
    
    char launch_cmd[512];
    snprintf(launch_cmd, sizeof(launch_cmd), "su -c \"%s &\"", path);
    system(launch_cmd);
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    std::thread(StartDaemon).detach();
    return JNI_VERSION_1_6;
}