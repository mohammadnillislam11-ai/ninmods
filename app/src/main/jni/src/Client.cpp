#include <pthread.h>
#include <jni.h>
#include <cstdlib>
#include <string>
#include <src/Socket/client.h>
#include <signal.h>
#include "src/Includes/obfuscate.h"
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <src/Widgets/ImportWidgets.h>
#include <src/Includes/http.h>
#include "src/Unity/Vector3.hpp"
#include "src/Unity/Vector2.hpp"
#include "src/Unity/Color.hpp"
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
#include <GLES3/gl3.h>
#include <EGL/egl.h>
#include "imgui.h"
#include "imgui_internal.h"
#include "imgui_impl_opengl3.h"


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

static bool g_ImGuiInitialized = false;
static int g_ScreenWidth = 0;
static int g_ScreenHeight = 0;

template<typename T>
inline constexpr const T& ClampVal(const T& v, const T& lo, const T& hi) {
    return (v < lo) ? lo : (hi < v) ? hi : v;
}

inline ImU32 ToImU32(const Color& c, float alphaMultiplier = 1.0f) {
    int r = (int)ClampVal(c.r, 0.0f, 255.0f);
    int g = (int)ClampVal(c.g, 0.0f, 255.0f);
    int b = (int)ClampVal(c.b, 0.0f, 255.0f);
    int a = (int)ClampVal(c.a * alphaMultiplier, 0.0f, 255.0f);
    return IM_COL32(r, g, b, a);
}

static void EnsureClientConnected() {
    static auto lastConnectAttempt = std::chrono::steady_clock::now();
    if (!isConnected()) {
        auto now = std::chrono::steady_clock::now();
        if (std::chrono::duration_cast<std::chrono::milliseconds>(now - lastConnectAttempt).count() > 1500) {
            lastConnectAttempt = now;
            NIN_LOGI("CLIENT", "Attempting auto-reconnect to daemon...");
            if (startClient() == 0) {
                NIN_LOGI("CLIENT", "Auto-reconnect successful! Syncing active features...");
                if (Actived.activar) SendBool(3, true);
                if (pPlayer.aimbot) SendBool(5, true);
                if (pPlayer.aimbotlock) SendBool(54, true);
                if (pPlayer.silen4a) SendBool(57, true);
                SendFloat(58, pPlayer.AimFov);
                SendFloat(59, pPlayer.AimDistance);
                SendInt(70, pPlayer.headshotRate);
                if (pPlayer.ignoreBots) SendBool(71, true);
                if (pPlayer.ignoreKnocked) SendBool(72, true);
            }
        }
    }
}

void RenderHardwareESP(int screenWidth, int screenHeight) {
    if (screenWidth <= 0 || screenHeight <= 0) return;
    
    EnsureClientConnected();

    if (!isConnected()) {
        ImDrawList* draw = ImGui::GetBackgroundDrawList();
        draw->AddText(ImVec2(24.0f, 40.0f), IM_COL32(255, 180, 0, 230), "NINMODS: CONNECTING DAEMON... (SEE /sdcard/ninmods.log)");
        return;
    }

    ImDrawList* draw = ImGui::GetBackgroundDrawList();

    // 1. Draw FOV Circle
    if (Actived.activar && pPlayer.silen4a && showFOVCircle && pPlayer.AimFov > 0) {
        float centerX = screenWidth * 0.5f;
        float centerY = screenHeight * 0.5f;
        float radius = (pPlayer.AimFov / 180.0f) * (screenHeight * 0.5f);
        radius *= pPlayer.AimFovScale;
        if (radius < 30.0f) radius = 30.0f;

        ImVec2 center(centerX, centerY);
        ImU32 fovColor = ToImU32(pEspPlayer.espColor);
        ImU32 fovGlow = (fovColor & 0x00FFFFFF) | (0x40 << 24);

        draw->AddCircle(center, radius + 2.0f, fovGlow, 64, 4.0f);
        draw->AddCircle(center, radius, fovColor, 64, 1.8f);
        draw->AddCircleFilled(center, 2.5f, IM_COL32(255, 255, 255, 255), 16);
    }

    // 2. Fetch live data from daemon
    Response response = getData(screenWidth, screenHeight);
    if (!response.Success) return;

    // 3. Live Threat Counter HUD (Safe / Danger)
    if (Actived.activar && pEspPlayer.espDrawCount) {
        const bool hasVisible = response.VisibleEnemyCount > 0;
        const bool hasNear = response.VisibleNearOffScreenEnemyCount > 0;

        float topY = 75.0f;
        float badgeH = 32.0f;
        float badgeRadius = 16.0f;

        if (!hasVisible && !hasNear) {
            float badgeW = 140.0f;
            float startX = (screenWidth - badgeW) * 0.5f;
            ImVec2 minP(startX, topY);
            ImVec2 maxP(startX + badgeW, topY + badgeH);

            draw->AddRectFilled(minP, maxP, IM_COL32(20, 160, 60, 220), badgeRadius);
            draw->AddRect(minP, maxP, IM_COL32(100, 255, 140, 200), badgeRadius, 0, 1.5f);

            const char* txt = "AREA SAFE";
            ImVec2 txtSz = ImGui::CalcTextSize(txt);
            draw->AddText(ImVec2(startX + (badgeW - txtSz.x) * 0.5f, topY + (badgeH - txtSz.y) * 0.5f),
                          IM_COL32(255, 255, 255, 255), txt);
        } else {
            float sectionW = 110.0f;
            float gap = 10.0f;
            int sections = (hasVisible ? 1 : 0) + (hasNear ? 1 : 0);
            float totalW = sections * sectionW + (sections - 1) * gap;
            float curX = (screenWidth - totalW) * 0.5f;

            if (hasVisible) {
                ImVec2 minP(curX, topY);
                ImVec2 maxP(curX + sectionW, topY + badgeH);
                draw->AddRectFilled(minP, maxP, IM_COL32(235, 40, 40, 220), badgeRadius);
                draw->AddRect(minP, maxP, IM_COL32(255, 140, 140, 220), badgeRadius, 0, 1.5f);

                char buf[32];
                snprintf(buf, sizeof(buf), "FOE: %d", response.VisibleEnemyCount);
                ImVec2 sz = ImGui::CalcTextSize(buf);
                draw->AddText(ImVec2(curX + (sectionW - sz.x) * 0.5f, topY + (badgeH - sz.y) * 0.5f),
                              IM_COL32(255, 255, 255, 255), buf);
                curX += sectionW + gap;
            }

            if (hasNear) {
                ImVec2 minP(curX, topY);
                ImVec2 maxP(curX + sectionW, topY + badgeH);
                draw->AddRectFilled(minP, maxP, IM_COL32(240, 150, 20, 220), badgeRadius);
                draw->AddRect(minP, maxP, IM_COL32(255, 210, 120, 220), badgeRadius, 0, 1.5f);

                char buf[32];
                snprintf(buf, sizeof(buf), "NEAR: %d", response.VisibleNearOffScreenEnemyCount);
                ImVec2 sz = ImGui::CalcTextSize(buf);
                draw->AddText(ImVec2(curX + (sectionW - sz.x) * 0.5f, topY + (badgeH - sz.y) * 0.5f),
                              IM_COL32(255, 255, 255, 255), buf);
            }
        }
    }

    if (response.PlayerCount <= 0 || !Actived.activar) return;

    float totalXAdjust = g_espRightAdjust - g_espLeftAdjust;
    float globalThickness = pEspPlayer.thickness > 0.5f ? pEspPlayer.thickness : 1.5f;

    for (int i = 0; i < response.PlayerCount; i++) {
        const auto& player = response.Players[i];
        if (player.Head == Vector3::Zero() || player.Toe == Vector3::Zero()) continue;

        float distance = player.Distancia;
        if (distance < 0.1f || distance > 150.0f) continue;

        float headToFeetHeight = std::abs(player.Toe.Y - player.Head.Y);
        float boxHeight = headToFeetHeight * 1.15f;
        float width = boxHeight * 0.48f;

        float boxY = player.Head.Y - (boxHeight * 0.12f);
        float boxX = player.Head.X - (width * 0.5f) + totalXAdjust;
        float adjustedHeadX = player.Head.X + totalXAdjust;

        Color baseColor = pEspPlayer.espColor;
        if (player.Name) baseColor = Color::Red();         // Bot
        if (player.IsCaido) baseColor = Color(255, 200, 0); // Knocked

        ImU32 mainCol = ToImU32(baseColor);
        ImU32 glowCol = (mainCol & 0x00FFFFFF) | (0x44 << 24);
        ImU32 shadowCol = IM_COL32(0, 0, 0, 180);

        // --- ESP LINE ---
        if (pEspPlayer.espLinha) {
            ImVec2 startP;
            if (pEspPlayer.lineType == LINE_TOP) {
                startP = ImVec2(screenWidth * 0.5f, 0.0f);
            } else if (pEspPlayer.lineType == LINE_CENTER) {
                startP = ImVec2(screenWidth * 0.5f, screenHeight * 0.5f);
            } else {
                startP = ImVec2(screenWidth * 0.5f, (float)screenHeight);
            }

            ImVec2 endP = (pEspPlayer.lineType == LINE_BOTTOM)
                ? ImVec2(adjustedHeadX, boxY + boxHeight)
                : ImVec2(adjustedHeadX, boxY);

            draw->AddLine(startP, endP, glowCol, globalThickness + 2.0f);
            draw->AddLine(startP, endP, mainCol, globalThickness);
        }

        // --- ESP BOX ---
        if (pEspPlayer.espCaixa) {
            ImVec2 bMin(boxX, boxY);
            ImVec2 bMax(boxX + width, boxY + boxHeight);

            if (pEspPlayer.boxType == BOX_CORNER) {
                float cs = width * 0.25f;
                draw->AddLine(ImVec2(bMin.x+1, bMin.y+1), ImVec2(bMin.x + cs+1, bMin.y+1), shadowCol, globalThickness + 1.0f);
                draw->AddLine(ImVec2(bMin.x+1, bMin.y+1), ImVec2(bMin.x+1, bMin.y + cs+1), shadowCol, globalThickness + 1.0f);
                draw->AddLine(ImVec2(bMax.x+1, bMin.y+1), ImVec2(bMax.x - cs+1, bMin.y+1), shadowCol, globalThickness + 1.0f);
                draw->AddLine(ImVec2(bMax.x+1, bMin.y+1), ImVec2(bMax.x+1, bMin.y + cs+1), shadowCol, globalThickness + 1.0f);
                draw->AddLine(ImVec2(bMin.x+1, bMax.y+1), ImVec2(bMin.x + cs+1, bMax.y+1), shadowCol, globalThickness + 1.0f);
                draw->AddLine(ImVec2(bMin.x+1, bMax.y+1), ImVec2(bMin.x+1, bMax.y - cs+1), shadowCol, globalThickness + 1.0f);
                draw->AddLine(ImVec2(bMax.x+1, bMax.y+1), ImVec2(bMax.x - cs+1, bMax.y+1), shadowCol, globalThickness + 1.0f);
                draw->AddLine(ImVec2(bMax.x+1, bMax.y+1), ImVec2(bMax.x+1, bMax.y - cs+1), shadowCol, globalThickness + 1.0f);

                draw->AddLine(ImVec2(bMin.x, bMin.y), ImVec2(bMin.x + cs, bMin.y), mainCol, globalThickness);
                draw->AddLine(ImVec2(bMin.x, bMin.y), ImVec2(bMin.x, bMin.y + cs), mainCol, globalThickness);
                draw->AddLine(ImVec2(bMax.x, bMin.y), ImVec2(bMax.x - cs, bMin.y), mainCol, globalThickness);
                draw->AddLine(ImVec2(bMax.x, bMin.y), ImVec2(bMax.x, bMin.y + cs), mainCol, globalThickness);
                draw->AddLine(ImVec2(bMin.x, bMax.y), ImVec2(bMin.x + cs, bMax.y), mainCol, globalThickness);
                draw->AddLine(ImVec2(bMin.x, bMax.y), ImVec2(bMin.x, bMax.y - cs), mainCol, globalThickness);
                draw->AddLine(ImVec2(bMax.x, bMax.y), ImVec2(bMax.x - cs, bMax.y), mainCol, globalThickness);
                draw->AddLine(ImVec2(bMax.x, bMax.y), ImVec2(bMax.x, bMax.y - cs), mainCol, globalThickness);
            } else if (pEspPlayer.boxType == BOX_FILLED) {
                ImU32 fillCol = (mainCol & 0x00FFFFFF) | (0x35 << 24);
                draw->AddRectFilled(bMin, bMax, fillCol, 4.0f);
                draw->AddRect(bMin, bMax, mainCol, 4.0f, 0, globalThickness);
            } else {
                draw->AddRect(ImVec2(bMin.x - 1, bMin.y - 1), ImVec2(bMax.x + 1, bMax.y + 1), glowCol, 5.0f, 0, 3.0f);
                draw->AddRect(bMin, bMax, mainCol, 5.0f, 0, globalThickness);
            }
        }

        // --- ESP HEALTH BAR ---
        if (pEspPlayer.espHealth) {
            float curHp = (float)player.Health;
            if (curHp < 0.0f) curHp = 0.0f;
            if (curHp > 200.0f) curHp = 200.0f;
            float pct = curHp / 200.0f;

            ImU32 hpCol = IM_COL32(70, 225, 90, 255);
            if (pct < 0.30f) hpCol = IM_COL32(235, 45, 45, 255);
            else if (pct < 0.60f) hpCol = IM_COL32(235, 200, 45, 255);

            float barW = 4.0f;
            float barX = boxX - barW - 4.0f;
            float barY = boxY;
            float barH = boxHeight;

            draw->AddRectFilled(ImVec2(barX, barY), ImVec2(barX + barW, barY + barH), IM_COL32(20, 20, 25, 210), 2.0f);
            float fillH = barH * pct;
            draw->AddRectFilled(ImVec2(barX, barY + (barH - fillH)), ImVec2(barX + barW, barY + barH), hpCol, 2.0f);
            draw->AddRect(ImVec2(barX, barY), ImVec2(barX + barW, barY + barH), IM_COL32(255, 255, 255, 120), 2.0f, 0, 1.0f);
        }

        // --- ESP NAME / DISTANCE ---
        if (pEspPlayer.espName || pEspPlayer.espDistance) {
            char labelBuf[64];
            if (pEspPlayer.espName && pEspPlayer.espDistance) {
                snprintf(labelBuf, sizeof(labelBuf), "%s [%dm]", player.Name ? "BOT" : "ENEMY", (int)distance);
            } else if (pEspPlayer.espDistance) {
                snprintf(labelBuf, sizeof(labelBuf), "[%dm]", (int)distance);
            } else {
                snprintf(labelBuf, sizeof(labelBuf), "%s", player.Name ? "BOT" : "ENEMY");
            }

            ImVec2 txtSz = ImGui::CalcTextSize(labelBuf);
            float txtX = boxX + (width - txtSz.x) * 0.5f;
            float txtY = boxY - txtSz.y - 4.0f;

            draw->AddText(ImVec2(txtX + 1, txtY + 1), shadowCol, labelBuf);
            draw->AddText(ImVec2(txtX, txtY), IM_COL32(255, 255, 255, 255), labelBuf);
        }
    }
}

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
    if (!SecurityVault_IsSessionValid()) {
        NIN_LOGE("SECURITY", ">>> [ACCESS_DENIED] Floater Functions blocked: Unauthorized execution without verified server session! <<<");
        return;
    }
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
    bool enabled = (value != 0);

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
        case 1: 
            Actived.activar = enabled; 
            if (isConnected()) SendBool(3, Actived.activar); 
            break;
        case 2: 
            pPlayer.aimbot = enabled; 
            if (isConnected()) SendBool(5, pPlayer.aimbot); 
            break;
        case 3: 
            pPlayer.fovawm = enabled; 
            if (isConnected()) SendBool(22, pPlayer.fovawm); 
            break;
        case 6: 
            pPlayer.aimbotlock = enabled; 
            if (isConnected()) SendBool(54, pPlayer.aimbotlock); 
            break;
        case 8: 
            pPlayer.silen4a = enabled; 
            if (isConnected()) SendBool(57, pPlayer.silen4a); 
            break;
        case 61: 
            pPlayer.silentAimHead = (value == 0); 
            if (isConnected()) SendBool(61, pPlayer.silentAimHead); 
            break;
        case 62: 
            pPlayer.targetByDistance = (value == 1); 
            if (isConnected()) SendBool(62, pPlayer.targetByDistance); 
            break;
        case 63: 
            pPlayer.aimMagnet = enabled; 
            if (isConnected()) SendBool(63, pPlayer.aimMagnet); 
            break;
        case 9: 
            pPlayer.AimFov = (float)value; 
            if (isConnected()) SendFloat(58, pPlayer.AimFov); 
            break;
        case 10: 
            pPlayer.AimDistance = (float)value; 
            if (isConnected()) SendFloat(59, pPlayer.AimDistance); 
            break;
        case 70: 
            pPlayer.headshotRate = value; 
            if (isConnected()) SendInt(70, pPlayer.headshotRate); 
            break;
        case 71: 
            pPlayer.ignoreBots = enabled; 
            if (isConnected()) SendBool(71, pPlayer.ignoreBots); 
            break;
        case 72: 
            pPlayer.ignoreKnocked = enabled; 
            if (isConnected()) SendBool(72, pPlayer.ignoreKnocked); 
            break;
        case 11: pEspPlayer.espLinha = enabled; break;
        case 12: pEspPlayer.espCaixa = enabled; break;
        case 13: pEspPlayer.espDistance = enabled; break;
        case 14: pEspPlayer.espSkeleton = enabled; break;
        case 15: pEspPlayer.espIden360 = enabled; break;
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
        case 19: pEspPlayer.espHealth = enabled; break;
        case 20: pEspPlayer.textSize = (float)value; break;
        case 21: pEspPlayer.espName = enabled; break;
        case 22: pEspPlayer.thickness = (float)value; break;
        case 23: pEspPlayer.espDrawCount = enabled; break;
        case 25: 
            pPlayer.gameSpeed = (float)value / 10.0f;
            if (isConnected()) SendInt(25, value);
            break;
        case 60: 
            pPlayer.SpeedHackV2 = enabled;
            if (isConnected()) SendBool(60, pPlayer.SpeedHackV2);
            break;
        case 27: showFOVCircle = enabled; break;
        case 30: g_espLeftAdjust = (float)value; break;
        case 31: g_espRightAdjust = (float)value; break;
        case 32:
            g_espLeftAdjust = 0.0f;
            g_espRightAdjust = 0.0f;
            break;
        case 33:
            if (isConnected()) SendBool(59, value != 0);
            break;
        case 34:
            if (isConnected()) SendFloat(60, (float)value);
            break;
    }
}


extern "C" JNIEXPORT void JNICALL
Java_power_cheats_Floater_PxbftKZXivSr(JNIEnv *env, jclass type, jobject espView, jobject canvas, jint width, jint height) {
    if (!SecurityVault_IsSessionValid()) {
        return; // Zero ESP drawing if no genuine verified server session
    }
    ESP localEsp = ESP(env, espView, canvas);
    if (localEsp.isValid()) { DrawESP(localEsp); }
}

extern "C" JNIEXPORT void JNICALL
Java_power_cheats_Floater_ftKZXivSr(JNIEnv *env, jclass clazz, jobject ctx) {
    if (!SecurityVault_IsSessionValid()) {
        NIN_LOGE("SECURITY", ">>> [ACCESS_DENIED] ftKZXivSr blocked: Unauthorized execution without server session <<<");
        return;
    }
    NIN_LOGI("CLIENT", "ftKZXivSr invoked from Java overlay");
    if (!isConnected()) {
        startClient();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_power_cheats_Floater_ftKwCvSr(JNIEnv *env, jclass clazz) {
    NIN_LOGI("CLIENT", "ftKwCvSr invoked from Java overlay");
    stopClient();
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

// ========================================================
// GLES3 JNI VIEW NATIVE BRIDGE (OpenGL ES 3.0 / ImGui ESP)
// ========================================================
extern "C" JNIEXPORT void JNICALL
Java_nin_app_graphics_GLES3JNIView_nativeInit(JNIEnv *env, jclass clazz) {
    if (!g_ImGuiInitialized) {
        IMGUI_CHECKVERSION();
        ImGui::CreateContext();
        ImGuiIO& io = ImGui::GetIO();
        io.IniFilename = nullptr;
        io.LogFilename = nullptr;

        ImGuiStyle& style = ImGui::GetStyle();
        style.WindowBorderSize = 0.0f;
        style.WindowPadding = ImVec2(0.0f, 0.0f);
        style.FramePadding = ImVec2(0.0f, 0.0f);
        style.ItemSpacing = ImVec2(0.0f, 0.0f);

        ImGui_ImplOpenGL3_Init("#version 300 es");
        g_ImGuiInitialized = true;
        LOGD("GLES3 ImGui Hardware ESP initialized successfully");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_nin_app_graphics_GLES3JNIView_nativeResize(JNIEnv *env, jclass clazz, jint width, jint height) {
    g_ScreenWidth = width;
    g_ScreenHeight = height;
    glViewport(0, 0, width, height);
    if (g_ImGuiInitialized) {
        ImGuiIO& io = ImGui::GetIO();
        io.DisplaySize = ImVec2((float)width, (float)height);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_nin_app_graphics_GLES3JNIView_nativeStep(JNIEnv *env, jclass clazz) {
    if (!g_ImGuiInitialized) return;

    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    ImGui_ImplOpenGL3_NewFrame();
    ImGui::NewFrame();

    RenderHardwareESP(g_ScreenWidth, g_ScreenHeight);

    ImGui::Render();
    ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
}

extern "C" JNIEXPORT void JNICALL
Java_nin_app_graphics_GLES3JNIView_nativeShutdown(JNIEnv *env, jclass clazz) {
    if (g_ImGuiInitialized) {
        ImGui_ImplOpenGL3_Shutdown();
        ImGui::DestroyContext();
        g_ImGuiInitialized = false;
        LOGD("GLES3 ImGui Hardware ESP shutdown cleanly");
    }
}



extern "C" JNIEXPORT jstring JNICALL
Java_nin_app_core_Utils_getGamePackage(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("com.dts.freefireth"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_nin_app_core_Utils_getDaemonName(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(OBFUSCATE("Titoo"));
}



extern "C" JNIEXPORT void JNICALL
Java_nin_app_core_DashboardActivity_setModeNative(JNIEnv *env, jclass clazz, jint mode) {
    g_hackVersion = (mode == 2) ? 2 : 1;
    LOGD("Profile mode selected from DashboardActivity (com.ninmods.main): %d", g_hackVersion);
}



extern "C" JNIEXPORT void JNICALL
Java_nin_app_core_ModeSelectActivity_setModeNative(JNIEnv *env, jclass clazz, jint mode) {
    g_hackVersion = (mode == 2) ? 2 : 1;
    LOGD("Profile mode selected from ModeSelectActivity (com.ninmods.main): %d", g_hackVersion);
}



static bool isRooted() {
    struct stat st;
    if (stat("/system/xbin/su", &st) == 0) return true;
    if (stat("/system/bin/su", &st) == 0) return true;
    if (stat("/sbin/su", &st) == 0) return true;
    return false;
}

void StartDaemon() {
    signal(SIGPIPE, SIG_IGN);
    NIN_LOGI("CLIENT", "=== StartDaemon() background thread started ===");
    bool isX86 = false;
#if defined(__x86_64__) || defined(__i386__)
    isX86 = true;
#endif

    const char* dName = isX86 ? "Titoo1" : "Titoo";
    const char* paths[] = {
        "/data/user/0/com.ninmods.main/files/",
        "/data/data/com.ninmods.main/files/",
        "/data/user/0/com.ninmods.main/cache/",
        "/data/data/com.ninmods.main/cache/",
        "/data/local/tmp/"
    };
    
    char path[256] = {0};
    struct stat st;
    bool found = false;
    
    for (const char* p : paths) {
        snprintf(path, sizeof(path), "%s%s", p, dName);
        if (stat(path, &st) == 0 && st.st_size > 5000) {
            found = true;
            break;
        }
    }

    if (!found) {
        NIN_LOGE("CLIENT", "Daemon binary %s NOT FOUND at any expected path!", dName);
        return;
    }

    NIN_LOGI("CLIENT", "Found daemon binary at: %s (size: %ld)", path, (long)st.st_size);

    char tmpPath[256];
    snprintf(tmpPath, sizeof(tmpPath), "/data/local/tmp/%s", dName);

    // Set SELinux to permissive mode so unconfined root IPC is fully permitted
    system("su -c \"setenforce 0\"");

    // Copy to /data/local/tmp for unconstrained root execution
    char cp_cmd[512];
    snprintf(cp_cmd, sizeof(cp_cmd), "su -c \"cp -f %s %s && chmod 777 %s\"", path, tmpPath, tmpPath);
    system(cp_cmd);

    char pkill_cmd[256];
    snprintf(pkill_cmd, sizeof(pkill_cmd), "su -c \"pkill -9 -f %s\"", dName);
    system(pkill_cmd);
    usleep(150000); // 150ms

    char launch_cmd[512];
    snprintf(launch_cmd, sizeof(launch_cmd), "su -c \"%s &\"", tmpPath);
    NIN_LOGI("CLIENT", "Launching root daemon: %s", launch_cmd);
    system(launch_cmd);
}

// ============================================================================
// BANKING-GRADE APPLICATION SECURITY & DRIVER INQUIRY JNI EXPORTS
// ============================================================================

extern "C" JNIEXPORT jstring JNICALL
Java_nin_app_core_DriverManager_nativeProbeDriverInfo(JNIEnv *env, jclass clazz) {
    const char* candidate_nodes[] = {
        "/dev/health_storage_service_alloc",
        "/dev/BYNb",
        "/dev/sched_debug",
        "/dev/uevents_records"
    };

    bool is_active = false;
    std::string active_node = "None";
    int maj = 0;
    int min = 0;
    std::string dev_id_str = "None";
    std::string perms_str = "Inaccessible";
    std::string status_text = "Inactive";

    for (const char* node : candidate_nodes) {
        struct stat st;
        if (stat(node, &st) == 0) {
            active_node = node;
            is_active = true;
            if (S_ISCHR(st.st_mode)) {
                maj = major(st.st_rdev);
                min = minor(st.st_rdev);
                char dev_buf[64];
                snprintf(dev_buf, sizeof(dev_buf), "Major: %d, Minor: %d (0x%lX)", maj, min, (unsigned long)st.st_rdev);
                dev_id_str = dev_buf;
            } else {
                dev_id_str = "Character Node Attached";
            }

            int fd = open(node, O_RDWR);
            if (fd >= 0) {
                perms_str = "rw-rw-rw- (FD Open OK)";
                status_text = "ACTIVE & RESPONDING (Ring 0)";
                close(fd);
            } else {
                perms_str = "Node exists (Restricted permission)";
                status_text = "Node detected, run chmod 666 in loader";
            }
            break;
        }
    }

    std::string json = "{";
    json += "\"is_active\":" + std::string(is_active ? "true" : "false") + ",";
    json += "\"active_node\":\"" + active_node + "\",";
    json += "\"major\":" + std::to_string(maj) + ",";
    json += "\"minor\":" + std::to_string(min) + ",";
    json += "\"device_id\":\"" + dev_id_str + "\",";
    json += "\"permissions\":\"" + perms_str + "\",";
    json += "\"status_text\":\"" + status_text + "\"";
    json += "}";

    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_nin_app_auth_SecurityVault_nativeSignRequest(JNIEnv *env, jclass clazz, jstring jPayload, jlong timestamp, jstring jNonce, jstring jHwid) {
    const char* payload = jPayload ? env->GetStringUTFChars(jPayload, nullptr) : "";
    const char* nonce = jNonce ? env->GetStringUTFChars(jNonce, nullptr) : "";
    const char* hwid = jHwid ? env->GetStringUTFChars(jHwid, nullptr) : "";

    std::string data = std::string(payload) + "|" + std::to_string(timestamp) + "|" + nonce + "|" + hwid;
    std::string sig = NativeCrypto::Sha256(data + "N1N_B4NK1NG_S3CUR1TY_V90_K3RN3L");

    if (jPayload) env->ReleaseStringUTFChars(jPayload, payload);
    if (jNonce) env->ReleaseStringUTFChars(jNonce, nonce);
    if (jHwid) env->ReleaseStringUTFChars(jHwid, hwid);

    return env->NewStringUTF(sig.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_nin_app_auth_SecurityVault_nativeVerifyIntegrity(JNIEnv *env, jclass clazz, jobject context, jstring certDigest) {
    // 1. Anti-Debugging Check (/proc/self/status TracerPid)
    FILE* fp = fopen("/proc/self/status", "r");
    if (fp) {
        char line[256];
        while (fgets(line, sizeof(line), fp)) {
            if (strncmp(line, "TracerPid:", 10) == 0) {
                int tracerPid = atoi(line + 10);
                if (tracerPid != 0) {
                    fclose(fp);
                    NIN_LOGE("SECURITY", ">>> [TAMPER_DETECTED] Active TracerPid=%d attached <<<", tracerPid);
                    return JNI_FALSE;
                }
            }
        }
        fclose(fp);
    }

    // 2. Anti-Hook Framework Check (/proc/self/maps)
    FILE* mf = fopen("/proc/self/maps", "r");
    if (mf) {
        char mapLine[512];
        while (fgets(mapLine, sizeof(mapLine), mf)) {
            if (strstr(mapLine, "frida-agent") || strstr(mapLine, "libfrida") ||
                strstr(mapLine, "libxposed") || strstr(mapLine, "libsubstrate")) {
                fclose(mf);
                NIN_LOGE("SECURITY", ">>> [TAMPER_DETECTED] Hooking library mapped: %s <<<", mapLine);
                return JNI_FALSE;
            }
        }
        fclose(mf);
    }

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_nin_app_auth_SecurityVault_nativeSetSession(JNIEnv *env, jclass clazz, jstring jToken, jstring jHwid, jlong expirySec) {
    const char* token = jToken ? env->GetStringUTFChars(jToken, nullptr) : nullptr;
    const char* hwid = jHwid ? env->GetStringUTFChars(jHwid, nullptr) : nullptr;

    if (token && strlen(token) > 0) {
        strncpy(g_security_session, token, sizeof(g_security_session) - 1);
        strncpy(g_security_hwid, hwid ? hwid : "", sizeof(g_security_hwid) - 1);
        g_session_verified = true;
        NIN_LOGI("SECURITY", ">>> [VAULT_UNLOCKED] Verified server session active (HWID: %s) <<<", g_security_hwid);
    }

    if (token) env->ReleaseStringUTFChars(jToken, token);
    if (hwid) env->ReleaseStringUTFChars(jHwid, hwid);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_nin_app_auth_SecurityVault_nativeIsSessionValid(JNIEnv *env, jclass clazz) {
    return g_session_verified ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    signal(SIGPIPE, SIG_IGN);
    std::thread(StartDaemon).detach();
    return JNI_VERSION_1_6;
}