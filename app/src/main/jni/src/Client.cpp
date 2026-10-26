#if __has_include(<EGL/egl.h>)
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#else
#include "Includes/android_ide_stubs/EGL/egl.h"
#include "Includes/android_ide_stubs/GLES3/gl3.h"
#endif
#include <cstdio>
#include <cmath>
#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <dlfcn.h>
#include <fstream>
#include <iomanip>
#if __has_include(<jni.h>)
#include <jni.h>
#else
#include "Includes/android_ide_stubs/jni.h"
#endif
#include <pthread.h>
#include <random>
#include <signal.h>
#include <sstream>
#include <string>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <thread>
#include <unistd.h>
#if __has_include(<sys/socket.h>)
#include <sys/socket.h>
#include <sys/un.h>
#else
#include "Includes/android_ide_stubs/sys/socket.h"
#include "Includes/android_ide_stubs/sys/un.h"
#endif


#include "BooleanClient.h"
#include "GameEngine.h"
#include "Includes/Logger.h"
#include "Includes/Utils.h"
#include "Includes/obfuscate.h"
#include "Unity/Color.hpp"
#include "Unity/ESP.h"
#include "Unity/Unity.h"
#include "Unity/Vector2.hpp"
#include "Unity/Vector3.hpp"


#include "Icon/Font.h"
#include "Icon/Icon.h"
#include "Icon/Iconcpp.h"
#include "UI/MenuRouter.h"
#include "UI/NativeOverlayController.h"
#include "UI/Popups/LicensePopup.h"
#include "Engine/NinliteEngine.h"
#if __has_include(<imgui.h>)
#include <imgui.h>
#include <imgui_impl_opengl3.h>
#include <imgui_internal.h>
#else
#include "imgui/imgui.h"
#include "imgui/imgui_impl_opengl3.h"
#include "imgui/imgui_internal.h"
#endif

// ============================================
// GLOBAL SYSTEM STATE & PROFILE SETTINGS
// ============================================
static int g_hackVersion = 1;

inline bool isButrulVersion() { return g_hackVersion == 2; }

inline bool securityChecksPassed() { return true; }

inline bool NativeIsLoggedIn() { return true; }

// ============================================
// EXISTING GLOBAL VARIABLES AND FUNCTIONS
// ============================================

float g_espLeftAdjust = 0.0f;
float g_espRightAdjust = 0.0f;

static bool g_ImGuiInitialized = false;
static int g_ScreenWidth = 0;
static int g_ScreenHeight = 0;

template <typename T>
inline constexpr const T &ClampVal(const T &v, const T &lo, const T &hi) {
  return (v < lo) ? lo : (hi < v) ? hi : v;
}

inline ImU32 ToImU32(const Color &c, float alphaMultiplier = 1.0f) {
  int r = (int)ClampVal(c.r, 0.0f, 255.0f);
  int g = (int)ClampVal(c.g, 0.0f, 255.0f);
  int b = (int)ClampVal(c.b, 0.0f, 255.0f);
  int a = (int)ClampVal(c.a * alphaMultiplier, 0.0f, 255.0f);
  return IM_COL32(r, g, b, a);
}

static void EnsureDriverConnected() {
  if (driver && !driver->isConnected()) {
    driver->initDriver();
  }
}

void RenderHardwareESP(int screenWidth, int screenHeight) {
  if (screenWidth <= 0 || screenHeight <= 0)
    return;

  EnsureDriverConnected();

  if (!isConnected()) {
    return;
  }

  ImDrawList *draw = ImGui::GetBackgroundDrawList();

  // 1. Draw FOV Circle
  bool anyAimActive = pPlayer.silen4a || pPlayer.aimbot || pPlayer.aimbotlock || pPlayer.aimMagnet;
  if (Actived.activar && anyAimActive && showFOVCircle &&
      pPlayer.AimFov > 0) {
    float centerX = screenWidth * 0.5f;
    float centerY = screenHeight * 0.5f;
    float radius = (pPlayer.AimFov / 180.0f) * (screenHeight * 0.5f);
    radius *= pPlayer.AimFovScale;
    if (radius < 30.0f)
      radius = 30.0f;

    ImVec2 center(centerX, centerY);
    ImU32 fovColor = ToImU32(pEspPlayer.espColor);
    ImU32 fovGlow = (fovColor & 0x00FFFFFF) | (0x40 << 24);

    draw->AddCircle(center, radius + 2.0f, fovGlow, 64, 4.0f);
    draw->AddCircle(center, radius, fovColor, 64, 1.8f);
    draw->AddCircleFilled(center, 2.5f, IM_COL32(255, 255, 255, 255), 16);
  }

  // 2. Fetch live data from daemon
  Response response = getData(screenWidth, screenHeight);
  if (!response.Success)
    return;

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
      draw->AddRect(minP, maxP, IM_COL32(100, 255, 140, 200), badgeRadius, 0,
                    1.5f);

      const char *txt = "AREA SAFE";
      ImVec2 txtSz = ImGui::CalcTextSize(txt);
      draw->AddText(ImVec2(startX + (badgeW - txtSz.x) * 0.5f,
                           topY + (badgeH - txtSz.y) * 0.5f),
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
        draw->AddRectFilled(minP, maxP, IM_COL32(235, 40, 40, 220),
                            badgeRadius);
        draw->AddRect(minP, maxP, IM_COL32(255, 140, 140, 220), badgeRadius, 0,
                      1.5f);

        char buf[32];
        snprintf(buf, sizeof(buf), "FOE: %d", response.VisibleEnemyCount);
        ImVec2 sz = ImGui::CalcTextSize(buf);
        draw->AddText(ImVec2(curX + (sectionW - sz.x) * 0.5f,
                             topY + (badgeH - sz.y) * 0.5f),
                      IM_COL32(255, 255, 255, 255), buf);
        curX += sectionW + gap;
      }

      if (hasNear) {
        ImVec2 minP(curX, topY);
        ImVec2 maxP(curX + sectionW, topY + badgeH);
        draw->AddRectFilled(minP, maxP, IM_COL32(240, 150, 20, 220),
                            badgeRadius);
        draw->AddRect(minP, maxP, IM_COL32(255, 210, 120, 220), badgeRadius, 0,
                      1.5f);

        char buf[32];
        snprintf(buf, sizeof(buf), "NEAR: %d",
                 response.VisibleNearOffScreenEnemyCount);
        ImVec2 sz = ImGui::CalcTextSize(buf);
        draw->AddText(ImVec2(curX + (sectionW - sz.x) * 0.5f,
                             topY + (badgeH - sz.y) * 0.5f),
                      IM_COL32(255, 255, 255, 255), buf);
      }
    }
  }

  if (response.PlayerCount <= 0 || !Actived.activar)
    return;

  float totalXAdjust = g_espRightAdjust - g_espLeftAdjust;
  float globalThickness =
      pEspPlayer.thickness > 0.5f ? pEspPlayer.thickness : 1.5f;

  for (int i = 0; i < response.PlayerCount; i++) {
    const auto &player = response.Players[i];
    if (player.Head == Vector3::Zero() || player.Toe == Vector3::Zero())
      continue;

    float distance = player.Distancia;
    if (distance < 0.1f || distance > 150.0f)
      continue;

    float headToFeetHeight = std::abs(player.Toe.Y - player.Head.Y);
    float boxHeight = headToFeetHeight * 1.15f;
    float width = boxHeight * 0.48f;

    float boxY = player.Head.Y - (boxHeight * 0.12f);
    float boxX = player.Head.X - (width * 0.5f) + totalXAdjust;
    float adjustedHeadX = player.Head.X + totalXAdjust;

    Color baseColor = pEspPlayer.espColor;
    if (player.Name)
      baseColor = Color::Red(); // Bot
    if (player.IsCaido)
      baseColor = Color(255, 200, 0); // Knocked

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
        draw->AddLine(ImVec2(bMin.x + 1, bMin.y + 1),
                      ImVec2(bMin.x + cs + 1, bMin.y + 1), shadowCol,
                      globalThickness + 1.0f);
        draw->AddLine(ImVec2(bMin.x + 1, bMin.y + 1),
                      ImVec2(bMin.x + 1, bMin.y + cs + 1), shadowCol,
                      globalThickness + 1.0f);
        draw->AddLine(ImVec2(bMax.x + 1, bMin.y + 1),
                      ImVec2(bMax.x - cs + 1, bMin.y + 1), shadowCol,
                      globalThickness + 1.0f);
        draw->AddLine(ImVec2(bMax.x + 1, bMin.y + 1),
                      ImVec2(bMax.x + 1, bMin.y + cs + 1), shadowCol,
                      globalThickness + 1.0f);
        draw->AddLine(ImVec2(bMin.x + 1, bMax.y + 1),
                      ImVec2(bMin.x + cs + 1, bMax.y + 1), shadowCol,
                      globalThickness + 1.0f);
        draw->AddLine(ImVec2(bMin.x + 1, bMax.y + 1),
                      ImVec2(bMin.x + 1, bMax.y - cs + 1), shadowCol,
                      globalThickness + 1.0f);
        draw->AddLine(ImVec2(bMax.x + 1, bMax.y + 1),
                      ImVec2(bMax.x - cs + 1, bMax.y + 1), shadowCol,
                      globalThickness + 1.0f);
        draw->AddLine(ImVec2(bMax.x + 1, bMax.y + 1),
                      ImVec2(bMax.x + 1, bMax.y - cs + 1), shadowCol,
                      globalThickness + 1.0f);

        draw->AddLine(ImVec2(bMin.x, bMin.y), ImVec2(bMin.x + cs, bMin.y),
                      mainCol, globalThickness);
        draw->AddLine(ImVec2(bMin.x, bMin.y), ImVec2(bMin.x, bMin.y + cs),
                      mainCol, globalThickness);
        draw->AddLine(ImVec2(bMax.x, bMin.y), ImVec2(bMax.x - cs, bMin.y),
                      mainCol, globalThickness);
        draw->AddLine(ImVec2(bMax.x, bMin.y), ImVec2(bMax.x, bMin.y + cs),
                      mainCol, globalThickness);
        draw->AddLine(ImVec2(bMin.x, bMax.y), ImVec2(bMin.x + cs, bMax.y),
                      mainCol, globalThickness);
        draw->AddLine(ImVec2(bMin.x, bMax.y), ImVec2(bMin.x, bMax.y - cs),
                      mainCol, globalThickness);
        draw->AddLine(ImVec2(bMax.x, bMax.y), ImVec2(bMax.x - cs, bMax.y),
                      mainCol, globalThickness);
        draw->AddLine(ImVec2(bMax.x, bMax.y), ImVec2(bMax.x, bMax.y - cs),
                      mainCol, globalThickness);
      } else if (pEspPlayer.boxType == BOX_FILLED) {
        ImU32 fillCol = (mainCol & 0x00FFFFFF) | (0x35 << 24);
        draw->AddRectFilled(bMin, bMax, fillCol, 4.0f);
        draw->AddRect(bMin, bMax, mainCol, 4.0f, 0, globalThickness);
      } else {
        draw->AddRect(ImVec2(bMin.x - 1, bMin.y - 1),
                      ImVec2(bMax.x + 1, bMax.y + 1), glowCol, 5.0f, 0, 3.0f);
        draw->AddRect(bMin, bMax, mainCol, 5.0f, 0, globalThickness);
      }
    }

    // --- ESP HEALTH BAR ---
    if (pEspPlayer.espHealth) {
      float curHp = (float)player.Health;
      if (curHp < 0.0f)
        curHp = 0.0f;
      if (curHp > 200.0f)
        curHp = 200.0f;
      float pct = curHp / 200.0f;

      ImU32 hpCol = IM_COL32(70, 225, 90, 255);
      if (pct < 0.30f)
        hpCol = IM_COL32(235, 45, 45, 255);
      else if (pct < 0.60f)
        hpCol = IM_COL32(235, 200, 45, 255);

      float barW = 4.0f;
      float barX = boxX - barW - 4.0f;
      float barY = boxY;
      float barH = boxHeight;

      draw->AddRectFilled(ImVec2(barX, barY), ImVec2(barX + barW, barY + barH),
                          IM_COL32(20, 20, 25, 210), 2.0f);
      float fillH = barH * pct;
      draw->AddRectFilled(ImVec2(barX, barY + (barH - fillH)),
                          ImVec2(barX + barW, barY + barH), hpCol, 2.0f);
      draw->AddRect(ImVec2(barX, barY), ImVec2(barX + barW, barY + barH),
                    IM_COL32(255, 255, 255, 120), 2.0f, 0, 1.0f);
    }

    // --- ESP NAME / DISTANCE ---
    if (pEspPlayer.espName || pEspPlayer.espDistance) {
      char labelBuf[64];
      if (pEspPlayer.espName && pEspPlayer.espDistance) {
        snprintf(labelBuf, sizeof(labelBuf), "%s [%dm]",
                 player.Name ? "BOT" : "ENEMY", (int)distance);
      } else if (pEspPlayer.espDistance) {
        snprintf(labelBuf, sizeof(labelBuf), "[%dm]", (int)distance);
      } else {
        snprintf(labelBuf, sizeof(labelBuf), "%s",
                 player.Name ? "BOT" : "ENEMY");
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

static size_t WriteMemoryCallback(void *contents, size_t size, size_t nmemb,
                                  void *userp) {
  size_t realsize = size * nmemb;
  MemoryChunk *mem = (MemoryChunk *)userp;
  char *new_memory = (char *)realloc(mem->memory, mem->size + realsize + 1);
  if (new_memory == NULL)
    return 0;
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
  if (publicKey.length() < 98)
    return "000000000";
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
  if (fov <= 0)
    return;

  auto screenHeight = esp.getHeight();
  auto screenWidth = esp.getWidth();

  float centerX = screenWidth / 2.0f;
  float centerY = screenHeight / 2.0f;

  float radius = (fov / 180.0f) * (screenHeight / 2.0f);
  radius *= pPlayer.AimFovScale;
  if (radius < 40.0f)
    radius = 40.0f;

  Vector2 centerPos(centerX, centerY);
  esp.DrawCircle(color, 2.0f, centerPos, radius);

  float crossSize = 15.0f;
  esp.DrawLine(color, 1.5f, Vector3(centerX - crossSize, centerY, 0),
               Vector3(centerX + crossSize, centerY, 0));
  esp.DrawLine(color, 1.5f, Vector3(centerX, centerY - crossSize, 0),
               Vector3(centerX, centerY + crossSize, 0));
}

void DrawESP(ESP esp) {
  auto screenHeight = esp.getHeight();
  auto screenWidth = esp.getWidth();

  if (!isConnected())
    return;

  bool anyAimActive = pPlayer.silen4a || pPlayer.aimbot || pPlayer.aimbotlock || pPlayer.aimMagnet;
  if (Actived.activar && anyAimActive && showFOVCircle &&
      pPlayer.AimFov > 0) {
    DrawFOVCircle(esp, pPlayer.AimFov, pEspPlayer.espColor);
  }

  auto response = getData(screenWidth, screenHeight);
  if (!response.Success)
    return;

  // The following block is removed because Response no longer contains
  // VisibleEnemyCount etc. (If you want to keep a counter, you'd need to add
  // those fields to the Response struct
  //  and have the server fill them. For now, just skip drawing the counter.)

  if (response.PlayerCount <= 0)
    return;

  // Draw Live Threat Counter HUD once per frame if enabled
  if (Actived.activar && pEspPlayer.espDrawCount) {
    const bool showOnScreen = response.VisibleEnemyCount > 0;
    const bool showNearScreen = response.VisibleNearOffScreenEnemyCount > 0;
    const bool showProximity = response.ProximityEnemyCount > 0;
    const bool noThreatVisible =
        !showOnScreen && !showNearScreen && !showProximity;

    std::string onScreenLabel = std::to_string(response.VisibleEnemyCount);
    std::string nearScreenLabel =
        std::to_string(response.VisibleNearOffScreenEnemyCount);
    std::string proximityLabel = std::to_string(response.ProximityEnemyCount);

    float countSize = pEspPlayer.textSize + 10.0f;
    if (countSize < 24.0f)
      countSize = 24.0f;

    float topMargin = 80.0f;
    float sectionWidth = 120.0f;
    float sectionHeight = countSize + 22.0f;
    float sectionGap = 14.0f;

    int activeSections = (showOnScreen ? 1 : 0) + (showNearScreen ? 1 : 0) +
                         (showProximity ? 1 : 0);
    float totalWidth =
        sectionWidth * activeSections + sectionGap * (activeSections - 1);
    float startX = (screenWidth - totalWidth) / 2.0f;

    float backgroundY = topMargin;
    float countY = backgroundY + sectionHeight / 2.0f;
    float cornerRadius = sectionHeight * 0.30f;
    Color outlineColor = Color(0, 0, 0, 220);
    Color textColor = Color::White();

    auto DrawRoundedPanel = [&](float x, float y, float width, float height,
                                float radius, Color color) {
      float clampedRadius =
          std::max(1.0f, std::min(radius, std::min(width, height) * 0.5f));
      esp.DrawFilledRect(color, Vector3(x + clampedRadius, y, 0),
                         width - (clampedRadius * 2.0f), height);
      esp.DrawFilledRect(color, Vector3(x, y + clampedRadius, 0),
                         clampedRadius, height - (clampedRadius * 2.0f));
      esp.DrawFilledRect(
          color, Vector3(x + width - clampedRadius, y + clampedRadius, 0),
          clampedRadius, height - (clampedRadius * 2.0f));
      esp.DrawFilledCircle(color,
                           Vector2(x + clampedRadius, y + clampedRadius),
                           clampedRadius);
      esp.DrawFilledCircle(
          color, Vector2(x + width - clampedRadius, y + clampedRadius),
          clampedRadius);
      esp.DrawFilledCircle(
          color, Vector2(x + clampedRadius, y + height - clampedRadius),
          clampedRadius);
      esp.DrawFilledCircle(
          color,
          Vector2(x + width - clampedRadius, y + height - clampedRadius),
          clampedRadius);
    };

    if (noThreatVisible) {
      float safeWidth = 200.0f;
      float safeX = (screenWidth - safeWidth) / 2.0f;
      DrawRoundedPanel(safeX, backgroundY, safeWidth, sectionHeight,
                       cornerRadius, Color(22, 165, 70, 235));
      const char *safeLabel = "SAFE";
      float safeTextX = safeX + (safeWidth * 0.5f);
      esp.DrawText(outlineColor, safeLabel,
                   Vector2(safeTextX - 1, countY - 1), countSize - 2.0f);
      esp.DrawText(outlineColor, safeLabel,
                   Vector2(safeTextX + 1, countY + 1), countSize - 2.0f);
      esp.DrawText(textColor, safeLabel, Vector2(safeTextX, countY),
                   countSize - 2.0f);
    } else {
      Color onScreenBg = Color(255, 215, 0, 230);   // Yellow
      Color nearScreenBg = Color(235, 30, 30, 230); // Red
      Color proximityBg = Color(255, 140, 0, 230);  // Orange
      float nextSectionX = startX;

      if (showOnScreen) {
        DrawRoundedPanel(nextSectionX, backgroundY, sectionWidth,
                         sectionHeight, cornerRadius, onScreenBg);
        float centerX = nextSectionX + (sectionWidth * 0.5f);
        esp.DrawText(outlineColor, onScreenLabel.c_str(),
                     Vector2(centerX - 1, countY - 1), countSize);
        esp.DrawText(outlineColor, onScreenLabel.c_str(),
                     Vector2(centerX + 1, countY + 1), countSize);
        esp.DrawText(textColor, onScreenLabel.c_str(),
                     Vector2(centerX, countY), countSize);
        nextSectionX += sectionWidth + sectionGap;
      }

      if (showNearScreen) {
        DrawRoundedPanel(nextSectionX, backgroundY, sectionWidth,
                         sectionHeight, cornerRadius, nearScreenBg);
        float centerX = nextSectionX + (sectionWidth * 0.5f);
        esp.DrawText(outlineColor, nearScreenLabel.c_str(),
                     Vector2(centerX - 1, countY - 1), countSize);
        esp.DrawText(outlineColor, nearScreenLabel.c_str(),
                     Vector2(centerX + 1, countY + 1), countSize);
        esp.DrawText(textColor, nearScreenLabel.c_str(),
                     Vector2(centerX, countY), countSize);
        nextSectionX += sectionWidth + sectionGap;
      }

      if (showProximity) {
        DrawRoundedPanel(nextSectionX, backgroundY, sectionWidth,
                         sectionHeight, cornerRadius, proximityBg);
        float centerX = nextSectionX + (sectionWidth * 0.5f);
        esp.DrawText(outlineColor, proximityLabel.c_str(),
                     Vector2(centerX - 1, countY - 1), countSize);
        esp.DrawText(outlineColor, proximityLabel.c_str(),
                     Vector2(centerX + 1, countY + 1), countSize);
        esp.DrawText(textColor, proximityLabel.c_str(),
                     Vector2(centerX, countY), countSize);
      }
    }
  }

  float globalThickness = pEspPlayer.thickness;

  for (int i = 0; i < response.PlayerCount; i++) {
    auto player = response.Players[i];
    if (player.Head == Vector3::Zero() || player.Toe == Vector3::Zero())
      continue;
    if (!Actived.activar)
      continue;

    float distance = player.Distancia;
    if (distance < 0.1f || distance > 150.0f)
      continue;

    // Dynamic distance scaling for clean visuals
    float thickness = (distance > 70.0f) ? 1.0f : globalThickness;
    float fSize = (distance > 70.0f) ? 10.0f : pEspPlayer.textSize;

    float headToFeetHeight = std::abs(player.Toe.Y - player.Head.Y);
    float boxHeight = headToFeetHeight * 1.15f;
    float width = boxHeight * 0.48f;

    float totalXAdjust = g_espRightAdjust - g_espLeftAdjust;

    float boxY = player.Head.Y - (boxHeight * 0.12f);
    float boxX = player.Head.X - (width / 2.0f) + totalXAdjust;

    Color currentBoxColor = pEspPlayer.espColor;
    if (player.Name)
      currentBoxColor = Color::Red(); // player.Name == true -> bot
    if (player.IsCaido)
      currentBoxColor = Color::Red(); // knocked (changed from Yellow to Red)
    Color strokeColor = Color(0, 0, 0, 255);

    float adjustedHeadX = player.Head.X + totalXAdjust;

    if (pEspPlayer.espLinha) {
      Vector3 lineStart, lineEnd;
      if (pEspPlayer.lineType == LINE_TOP)
        lineStart = Vector3(screenWidth / 2, 0, 0);
      else if (pEspPlayer.lineType == LINE_CENTER)
        lineStart = Vector3(screenWidth / 2, screenHeight / 2, 0);
      else
        lineStart = Vector3(screenWidth / 2, screenHeight, 0);

      lineEnd = (pEspPlayer.lineType == LINE_BOTTOM)
                    ? Vector3(adjustedHeadX, boxY + boxHeight, 0)
                    : Vector3(adjustedHeadX, boxY, 0);
      esp.DrawLine(currentBoxColor, thickness, lineStart, lineEnd);
    }

    if (pEspPlayer.espCaixa) {
      Rect playerRect(boxX, boxY, width, boxHeight);
      if (pEspPlayer.boxType == BOX_FILLED ||
          pEspPlayer.boxType == BOX_ROUNDED) {
        Color fill = currentBoxColor;
        fill.a = 70;
        esp.DrawFilledRect(fill, Vector3(boxX, boxY, 0), width, boxHeight);
      }
      if (pEspPlayer.boxType == BOX_CORNER)
        esp.DrawBox4Line(boxX, boxY, width, boxHeight, currentBoxColor,
                         thickness);
      else
        esp.DrawBox(currentBoxColor, thickness, playerRect);
    }

    if (pEspPlayer.espName || pEspPlayer.espDistance || pEspPlayer.espHealth) {
      std::string drawName;
      if (pEspPlayer.espName) {
        if (player.Name)
          drawName = "Bot";
        else if (player.PlayerName[0] != '\0')
          drawName = player.PlayerName;
        else
          drawName = "Unknown";
      }

      std::string dStr;
      if (pEspPlayer.espDistance)
        dStr = std::to_string((int)distance) + "M";

      const bool hasName = pEspPlayer.espName && !drawName.empty();
      const bool hasDistance = pEspPlayer.espDistance && !dStr.empty();
      const bool hasHealth = pEspPlayer.espHealth;

      if (hasName || hasDistance || hasHealth) {
        float fontSize = std::max(10.0f, fSize - 1.0f);
        float spacing = 6.0f;        // gap between name and distance segments
        float segmentPadding = 6.0f; // internal padding inside each segment
        float layoutPadding = 6.0f;  // padding around the whole panel
        float textHeight = fontSize + 5.0f; // height of text background

        // Calculate name text width (approximate)
        float nameTextWidth =
            hasName ? (drawName.length() * (fontSize * 0.56f)) : 0.0f;
        float rawNameSegmentWidth =
            (nameTextWidth > 0.0f) ? (nameTextWidth + segmentPadding * 2.0f)
                                   : 0.0f;
        float nameSegmentWidth = rawNameSegmentWidth;

        // Fixed width for distance segment
        const float fixedDistWidth = 80.0f; // adjust as needed
        const float defaultHealthWidth =
            60.0f; // default health bar width when no name/distance

        float distSegmentWidth = hasDistance ? fixedDistWidth : 0.0f;

        // ----- SYNCHRONIZE NAME BACKGROUND AND HEALTH BAR WIDTHS (existing
        // logic) -----
        float healthBarWidth;
        if (hasName) {
          float baseHealthWidth =
              hasDistance ? fixedDistWidth : defaultHealthWidth;
          healthBarWidth = std::max(nameSegmentWidth, baseHealthWidth);
          nameSegmentWidth =
              healthBarWidth; // name background expands to match health bar
        } else {
          healthBarWidth = hasDistance ? fixedDistWidth : defaultHealthWidth;
        }

        // ----- NEW LOGIC: When all three are present, unify total
        // name+distance width with health bar -----
        if (hasName && hasDistance && hasHealth) {
          float combinedWidth =
              nameSegmentWidth + distSegmentWidth +
              spacing; // total width of both segments plus gap
          if (combinedWidth > healthBarWidth) {
            // Need to shrink combined width to match health bar
            float excess = combinedWidth - healthBarWidth;
            // First try to reduce the name segment
            float newNameWidth = nameSegmentWidth - excess;
            // Ensure name doesn't go below its text-based minimum (to avoid
            // clipping)
            float minNameWidth = rawNameSegmentWidth; // original text width
            if (newNameWidth < minNameWidth) {
              // If name would become too small, we also need to shrink the
              // distance segment Calculate how much we need to take from
              // distance
              float nameDeficit =
                  minNameWidth - newNameWidth; // amount name would go below min
              newNameWidth = minNameWidth;     // set name to minimum
              // Reduce distance by the remaining deficit
              float newDistWidth = distSegmentWidth - nameDeficit;
              // Ensure distance doesn't go below a reasonable minimum (e.g.,
              // 30)
              const float minDistWidth = 30.0f;
              if (newDistWidth < minDistWidth) {
                // If both would go below min, we clamp and accept that combined
                // may still be > healthBarWidth
                newDistWidth = minDistWidth;
              }
              distSegmentWidth = newDistWidth;
            }
            nameSegmentWidth = newNameWidth;
            // Recalculate combined width after adjustments
            combinedWidth = nameSegmentWidth + distSegmentWidth + spacing;
            // If still larger, we may need to further adjust, but we'll leave
            // it as is (health bar remains the target)
          } else if (combinedWidth < healthBarWidth) {
            // Shrink health bar to match combined width
            healthBarWidth = combinedWidth;
          }
        }

        const float healthBarHeight = 6.0f; // constant height

        // Total width required for text segments (name + distance + spacing)
        float contentWidth = nameSegmentWidth + distSegmentWidth;
        if (nameSegmentWidth > 0.0f && distSegmentWidth > 0.0f)
          contentWidth += spacing;

        bool hasText = hasName || hasDistance; // are we showing any text?

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
          if (layoutY < 2.0f)
            layoutY = 2.0f;
        }

        // Helper to draw rounded panels
        auto DrawRoundedPanel = [&](float x, float y, float width, float height,
                                    float radius, Color color) {
          float clampedRadius =
              std::max(1.0f, std::min(radius, std::min(width, height) * 0.5f));
          esp.DrawFilledRect(color, Vector3(x + clampedRadius, y, 0),
                             width - clampedRadius * 2.0f, height);
          esp.DrawFilledRect(color, Vector3(x, y + clampedRadius, 0),
                             clampedRadius, height - clampedRadius * 2.0f);
          esp.DrawFilledRect(
              color, Vector3(x + width - clampedRadius, y + clampedRadius, 0),
              clampedRadius, height - clampedRadius * 2.0f);
          esp.DrawFilledCircle(color,
                               Vector2(x + clampedRadius, y + clampedRadius),
                               clampedRadius);
          esp.DrawFilledCircle(
              color, Vector2(x + width - clampedRadius, y + clampedRadius),
              clampedRadius);
          esp.DrawFilledCircle(
              color, Vector2(x + clampedRadius, y + height - clampedRadius),
              clampedRadius);
          esp.DrawFilledCircle(
              color,
              Vector2(x + width - clampedRadius, y + height - clampedRadius),
              clampedRadius);
        };

        // Draw main text panel background
        if (hasText) {
          DrawRoundedPanel(layoutX, layoutY, roundedPanelWidth,
                           roundedPanelHeight, 4.0f, Color(0, 0, 0, 170));
        }

        // ----- DRAW TEXT SEGMENTS -----
        if (hasText) {
          float contentStartX = adjustedHeadX - (layoutInnerWidth * 0.5f);
          float currentX = contentStartX;
          float textCenterY = layoutY + layoutPadding + (textHeight * 0.5f);

          // Name segment (width may have been adjusted)
          if (nameSegmentWidth > 0.0f) {
            DrawRoundedPanel(currentX, layoutY + layoutPadding,
                             nameSegmentWidth, textHeight, 3.0f,
                             Color(30, 30, 30, 210));
            float nameCenterX = currentX + nameSegmentWidth * 0.5f;
            esp.DrawText(strokeColor, drawName.c_str(),
                         Vector2(nameCenterX - 1, textCenterY - 1), fontSize);
            esp.DrawText(strokeColor, drawName.c_str(),
                         Vector2(nameCenterX + 1, textCenterY + 1), fontSize);
            esp.DrawText(Color::White(), drawName.c_str(),
                         Vector2(nameCenterX, textCenterY), fontSize);
            currentX +=
                nameSegmentWidth + (distSegmentWidth > 0.0f ? spacing : 0.0f);
          }

          // Distance segment (width may have been adjusted if needed)
          if (distSegmentWidth > 0.0f) {
            DrawRoundedPanel(currentX, layoutY + layoutPadding,
                             distSegmentWidth, textHeight, 3.0f,
                             Color(30, 30, 30, 210));
            float distCenterX = currentX + distSegmentWidth * 0.5f;
            Color distColor = currentBoxColor;
            esp.DrawText(strokeColor, dStr.c_str(),
                         Vector2(distCenterX - 1, textCenterY - 1), fontSize);
            esp.DrawText(strokeColor, dStr.c_str(),
                         Vector2(distCenterX + 1, textCenterY + 1), fontSize);
            esp.DrawText(distColor, dStr.c_str(),
                         Vector2(distCenterX, textCenterY), fontSize);
          }
        }

        // ----- DRAW HEALTH BAR (width may have been adjusted) -----
        if (hasHealth) {
          float hMax = player.Name ? 100.0f : 200.0f;
          float hpPercent =
              std::min(1.0f, std::max(0.0f, player.Health / hMax));

          // Determine Y position
          float healthY;
          if (hasText) {
            healthY = layoutY + roundedPanelHeight + 2.0f; // below text panel
          } else {
            healthY = boxY - healthBarHeight - 8.0f;
            if (healthY < 2.0f)
              healthY = 2.0f;
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
            if (hpPercent <= 0.6f)
              hpColor = Color(255, 210, 0, 220);
            if (hpPercent < 0.4f)
              hpColor = Color(255, 70, 70, 220);

            DrawRoundedPanel(healthBarX, healthY, hpWidth, healthBarHeight,
                             healthBarHeight * 0.5f, hpColor);
          }
        }
      }
    }
  }
}



extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_overlay_Floater_ChangesID(JNIEnv *env, jclass clazz,
                                                jint id, jint value) {
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
    break;
  case 2:
    pPlayer.aimbot = enabled;
    break;
  case 3:
    pPlayer.fovawm = enabled;
    break;
  case 6:
    pPlayer.aimbotlock = enabled;
    break;
  case 8:
    pPlayer.silen4a = enabled;
    break;
  case 61:
    pPlayer.silentAimHead = (value == 0);
    break;
  case 62:
    pPlayer.targetByDistance = (value == 1);
    break;
  case 63:
    pPlayer.aimMagnet = enabled;
    break;
  case 9:
    pPlayer.AimFov = (float)value;
    break;
  case 10:
    pPlayer.AimDistance = (float)value;
    break;
  case 70:
    pPlayer.headshotRate = value;
    break;
  case 71:
    pPlayer.ignoreBots = enabled;
    break;
  case 72:
    pPlayer.ignoreKnocked = enabled;
    break;
  case 11:
    pEspPlayer.espLinha = enabled;
    break;
  case 12:
    pEspPlayer.espCaixa = enabled;
    break;
  case 13:
    pEspPlayer.espDistance = enabled;
    break;
  case 14:
    pEspPlayer.espSkeleton = enabled;
    break;
  case 15:
    pEspPlayer.espIden360 = enabled;
    break;
  case 16:
    if (value == 0)
      pEspPlayer.espColor = Color(255, 255, 255, 255);
    else if (value == 1)
      pEspPlayer.espColor = Color(0, 255, 0, 255);
    else if (value == 2)
      pEspPlayer.espColor = Color(0, 0, 255, 255);
    else if (value == 3)
      pEspPlayer.espColor = Color(255, 0, 0, 255);
    else if (value == 4)
      pEspPlayer.espColor = Color(0, 0, 0, 255);
    else if (value == 5)
      pEspPlayer.espColor = Color(255, 255, 0, 255);
    else if (value == 6)
      pEspPlayer.espColor = Color(0, 255, 255, 255);
    else if (value == 7)
      pEspPlayer.espColor = Color(255, 0, 255, 255);
    else if (value == 8)
      pEspPlayer.espColor = Color(128, 128, 128, 255);
    else if (value == 9)
      pEspPlayer.espColor = Color(160, 32, 240, 255);
    break;
  case 17:
    pEspPlayer.boxType = static_cast<BoxType>(value);
    break;
  case 18:
    pEspPlayer.lineType = static_cast<LineType>(value);
    break;
  case 19:
    pEspPlayer.espHealth = enabled;
    break;
  case 20:
    pEspPlayer.textSize = (float)value;
    break;
  case 21:
    pEspPlayer.espName = enabled;
    break;
  case 22:
    pEspPlayer.thickness = (float)value;
    break;
  case 23:
    pEspPlayer.espDrawCount = enabled;
    break;
  case 25:
    pPlayer.gameSpeed = (float)value / 10.0f;
    break;
  case 60:
    pPlayer.SpeedHackV2 = enabled;
    break;
  case 27:
    showFOVCircle = enabled;
    break;
  case 30:
    g_espLeftAdjust = (float)value;
    break;
  case 31:
    g_espRightAdjust = (float)value;
    break;
  case 32:
    g_espLeftAdjust = 0.0f;
    g_espRightAdjust = 0.0f;
    break;
  case 33:
    pPlayer.NoRecoil = enabled;
    break;
  case 34:
    break;
  default:
    break;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_overlay_Floater_PxbftKZXivSr(JNIEnv *env, jclass type,
                                                   jobject espView,
                                                   jobject canvas, jint width,
                                                   jint height) {
  if (!SecurityVault_IsSessionValid()) {
    return; // Zero ESP drawing if no genuine verified server session
  }
  if (env->PushLocalFrame(256) < 0) {
    return;
  }
  ESP localEsp = ESP(env, espView, canvas);
  if (localEsp.isValid()) {
    DrawESP(localEsp);
  }
  env->PopLocalFrame(nullptr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_overlay_Floater_ftKZXivSr(JNIEnv *env, jclass clazz,
                                                jobject ctx) {
  if (!SecurityVault_IsSessionValid()) {
    NIN_LOGE("SECURITY", ">>> [ACCESS_DENIED] ftKZXivSr blocked: Unauthorized "
                         "execution without server session <<<");
    return;
  }
  NIN_LOGI("CLIENT", "ftKZXivSr invoked from Java overlay");
  if (driver && !driver->isConnected()) {
    driver->initDriver();
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_overlay_Floater_ftKwCvSr(JNIEnv *env, jclass clazz) {
  NIN_LOGI("CLIENT", "ftKwCvSr invoked from Java overlay");
}

// ========================================================
// GLES3 JNI VIEW NATIVE BRIDGE (OpenGL ES 3.0 / ImGui ESP)
// ========================================================
extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_graphics_GLES3JNIView_nativeInit(JNIEnv *env,
                                                       jclass clazz,
                                                       jfloat density) {
  if (!g_ImGuiInitialized) {
    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO &io = ImGui::GetIO();
    io.IniFilename = nullptr;
    io.LogFilename = nullptr;

    float scale = (float)density;
    if (scale < 1.5f)
      scale = 2.0f;
    if (scale > 4.0f)
      scale = 4.0f;
    static const ImWchar icons_ranges[] = {0xe000, 0xf8ff, 0};
    ImFontConfig icons_config;
    icons_config.MergeMode = true;
    icons_config.PixelSnapH = true;
    icons_config.OversampleH = 3;
    icons_config.OversampleV = 3;

    ImFontConfig customFontCfg;
    customFontCfg.FontDataOwnedByAtlas = false;
    customFontCfg.OversampleH = 3;
    customFontCfg.OversampleV = 3;
    customFontCfg.PixelSnapH = true;

    float targetDpi = (scale >= 1.2f) ? scale : 2.4f;
    float fontSize = std::round(16.0f * targetDpi);

    io.Fonts->AddFontFromMemoryTTF(const_cast<std::uint8_t *>(Custom),
                                   sizeof(Custom), fontSize, &customFontCfg);

    io.Fonts->AddFontFromMemoryCompressedTTF(font_awesome_data,
                                             font_awesome_size, fontSize,
                                             &icons_config, icons_ranges);

    ImFontConfig defCfg;
    defCfg.SizePixels = fontSize;
    io.Fonts->AddFontDefault(&defCfg);

    io.Fonts->Build();

    NativeOverlayController::SetDpiScale(scale);
    NativeOverlayController::ApplyDpiTheme();

    ImGui_ImplOpenGL3_Init("#version 300 es");
    g_ImGuiInitialized = true;
    MenuRouter::Initialize();
    LOGD("GLES3 ImGui Hardware ESP & MenuRouter initialized with DPI scale: "
         "%.2f and TrueType vector font (24px)",
         scale);
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_graphics_GLES3JNIView_nativeResize(JNIEnv *env,
                                                         jclass clazz,
                                                         jint width,
                                                         jint height) {
  g_ScreenWidth = width;
  g_ScreenHeight = height;
  g_screenWidth = width;
  g_screenHeight = height;
  glViewport(0, 0, width, height);
  if (g_ImGuiInitialized) {
    ImGuiIO &io = ImGui::GetIO();
    io.DisplaySize = ImVec2((float)width, (float)height);
  }
  NativeOverlayController::SetScreenSize(width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_graphics_GLES3JNIView_nativeStep(JNIEnv *env,
                                                       jclass clazz) {
  if (!g_ImGuiInitialized)
    return;

  glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
  glClear(GL_COLOR_BUFFER_BIT);

  ImGui_ImplOpenGL3_NewFrame();
  ImGui::NewFrame();

  // 1. ESP Overlay (Pure Hardware ESP drawing loop: Boxes, Skeletons, Lines,
  // Distance, Health)
  RenderHardwareESP(g_ScreenWidth, g_ScreenHeight);

  // Floating Menu and Logo are 100% managed by Android Native WindowManager
  // (FloatingMenuView.java) exactly matching Android-ModMenu-SemiJni-main. Zero
  // DPI issues, pure native touch sliding.

  ImGui::Render();
  ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_graphics_GLES3JNIView_nativeShutdown(JNIEnv *env,
                                                           jclass clazz) {
  if (g_ImGuiInitialized) {
    ImGui_ImplOpenGL3_Shutdown();
    ImGui::DestroyContext();
    g_ImGuiInitialized = false;
    LOGD("GLES3 ImGui Hardware ESP shutdown cleanly");
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_graphics_GLES3JNIView_nativeTouchEvent(
    JNIEnv *env, jclass clazz, jint action, jfloat x, jfloat y) {
  return (jboolean)NativeOverlayController::HandleTouchEvent(action, x, y);
}

// ========================================================
// NATIVE BRIDGE JNI EXPORTS (com.ninmods.main.core.NativeBridge)
// ========================================================
extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_launchBrandOverlay(
    JNIEnv *env, jclass clazz, jstring jBrandId, jstring jHwid,
    jboolean isLicenseActive, jfloat density, jstring jPrefillKey,
    jstring jServerUrl) {
  const char *bId =
      jBrandId ? env->GetStringUTFChars(jBrandId, nullptr) : "nin";
  const char *hwid = jHwid ? env->GetStringUTFChars(jHwid, nullptr) : "";
  const char *prefill =
      jPrefillKey ? env->GetStringUTFChars(jPrefillKey, nullptr) : "";
  const char *sUrl =
      jServerUrl ? env->GetStringUTFChars(jServerUrl, nullptr) : "";

  NativeOverlayController::Initialize(g_ScreenWidth, g_ScreenHeight,
                                      (float)density, bId, hwid,
                                      (bool)isLicenseActive);
  if (!isLicenseActive) {
    LicensePopup::Open(hwid, prefill, sUrl);
  }

  // Register verify handler to call Java AuthApiController
  LicensePopup::SetVerifyHandler([](const std::string &key,
                                    const std::string &brand) {
    if (!g_JavaVM)
      return;
    JNIEnv *jEnv = nullptr;
    bool attached = false;
    if (g_JavaVM->GetEnv((void **)&jEnv, JNI_VERSION_1_6) != JNI_OK) {
      if (g_JavaVM->AttachCurrentThread(&jEnv, nullptr) == JNI_OK) {
        attached = true;
      }
    }
    if (jEnv) {
      jclass bridgeClass =
          jEnv->FindClass("com/ninmods/main/core/NativeBridge");
      if (bridgeClass) {
        jmethodID mid =
            jEnv->GetStaticMethodID(bridgeClass, "verifyLicenseFromNative",
                                    "(Ljava/lang/String;Ljava/lang/String;)V");
        if (mid) {
          jstring jK = jEnv->NewStringUTF(key.c_str());
          jstring jB = jEnv->NewStringUTF(brand.c_str());
          jEnv->CallStaticVoidMethod(bridgeClass, mid, jK, jB);
          jEnv->DeleteLocalRef(jK);
          jEnv->DeleteLocalRef(jB);
        }
        jEnv->DeleteLocalRef(bridgeClass);
      }
      if (attached) {
        g_JavaVM->DetachCurrentThread();
      }
    }
  });

  if (jBrandId)
    env->ReleaseStringUTFChars(jBrandId, bId);
  if (jHwid)
    env->ReleaseStringUTFChars(jHwid, hwid);
  if (jPrefillKey)
    env->ReleaseStringUTFChars(jPrefillKey, prefill);
  if (jServerUrl)
    env->ReleaseStringUTFChars(jServerUrl, sUrl);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isModalPopupActive(JNIEnv *env,
                                                           jclass clazz) {
  return (jboolean)NativeOverlayController::IsModalPopupActive();
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_onLicenseResult(JNIEnv *env,
                                                        jclass clazz,
                                                        jboolean success,
                                                        jstring jMsg) {
  const char *msg = jMsg ? env->GetStringUTFChars(jMsg, nullptr) : "";
  LicensePopup::SetVerificationResult((bool)success, msg);
  if (jMsg)
    env->ReleaseStringUTFChars(jMsg, msg);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_handleTouch(JNIEnv *env, jclass clazz,
                                                    jint action, jfloat x,
                                                    jfloat y) {
  return (jboolean)NativeOverlayController::HandleTouchEvent(action, x, y);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isMenuOpen(JNIEnv *env, jclass clazz) {
  return (jboolean)NativeOverlayController::s_MenuOpen;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_setMenuOpen(JNIEnv *env, jclass clazz,
                                                    jboolean open) {
  NativeOverlayController::s_MenuOpen = (bool)open;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isOverlayActive(JNIEnv *env,
                                                        jclass clazz) {
  return (jboolean)NativeOverlayController::s_Initialized;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_closeOverlay(JNIEnv *env,
                                                     jclass clazz) {
  NativeOverlayController::s_Initialized = false;
  NativeOverlayController::s_MenuOpen = false;
}

static void SyncWithNinLiteDaemon(int mode, bool isOn, int value) {
  int sock = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
  if (sock < 0) return;
  sockaddr_un addr{};
  addr.sun_family = AF_UNIX;
  addr.sun_path[0] = '\0';
  strcpy(&addr.sun_path[1], "ninlite_daemon_stream");
  struct timeval tv{0, 150000};
  setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
  setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
  if (connect(sock, (struct sockaddr*)&addr, sizeof(sa_family_t) + 1 + strlen("ninlite_daemon_stream")) == 0) {
    struct {
      int Mode;
      bool m_IsOn;
      int value;
      int ScreenWidth;
      int ScreenHeight;
    } req{};
    req.Mode = mode;
    req.m_IsOn = isOn;
    req.value = value;
    uint32_t len = htonl(sizeof(req));
    send(sock, &len, sizeof(len), 0);
    send(sock, &req, sizeof(req), 0);
  }
  close(sock);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_setFeatureState(JNIEnv *env,
                                                        jclass clazz,
                                                        jint featId,
                                                        jboolean state) {
  bool bState = (bool)state;
  switch (featId) {
  // ESP Features
  case 1:
    Actived.activar = bState;
    SyncWithNinLiteDaemon(3, bState, 0);
    break;
  case 2:
    pEspPlayer.espCaixa = bState;
    break;
  case 3:
    pEspPlayer.espSkeleton = bState;
    break;
  case 4:
    pEspPlayer.espLinha = bState;
    break;
  case 5:
    pEspPlayer.espHealth = bState;
    break;
  case 6:
    pEspPlayer.espName = bState;
    break;
  case 7:
    pEspPlayer.espDistance = bState;
    break;
  case 8:
    pEspPlayer.espDrawCount = bState;
    break;
  case 9:
    pEspPlayer.espIden360 = bState;
    break;

  // Aimbot Features
  case 20:
    pPlayer.aimbot = bState;
    break;
  case 21:
    pPlayer.aimbotlock = bState;
    break;
  case 22:
    pPlayer.silen4a = bState;
    break;
  case 23:
    pPlayer.aimMagnet = bState;
    break;
  case 24:
    pPlayer.ignoreKnocked = bState;
    break;
  case 25:
    pPlayer.ignoreBots = bState;
    break;
  case 26:
    pPlayer.silentAimHead = bState;
    break;

  // Combat / Memory Features
  case 40:
    pPlayer.NoRecoil = bState;
    break;
  case 41:
    pPlayer.SpeedHackV2 = bState;
    break;
  // NINLITE Checkbox & Switch Features
  case 101:
    pEspPlayer.espCaixa = bState;
    Actived.activar = true;
    break;
  case 102:
    pEspPlayer.espLinha = bState;
    Actived.activar = true;
    break;
  case 103:
    pEspPlayer.espHealth = bState;
    Actived.activar = true;
    break;
  case 104:
    pEspPlayer.espDistance = bState;
    Actived.activar = true;
    break;
  case 105:
    pEspPlayer.espName = bState;
    Actived.activar = true;
    break;
  case 301:
    pPlayer.silen4a = bState;
    pPlayer.aimbot = bState;
    SyncWithNinLiteDaemon(57, bState, 0);
    break;
  case 302:
    pPlayer.ignoreKnocked = bState;
    SyncWithNinLiteDaemon(55, bState, 0);
    break;
  case 999:
    Actived.activar = bState;
    SyncWithNinLiteDaemon(3, bState, 0);
    NIN_LOGI("NINLITE", "Root Engine state toggled: %d", bState ? 1 : 0);
    break;

  default:
    break;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_setFeatureFloat(JNIEnv *env,
                                                        jclass clazz,
                                                        jint featId,
                                                        jfloat value) {
  float fVal = (float)value;
  switch (featId) {
  case 100:
    pPlayer.AimFov = fVal;
    break;
  case 101:
    pPlayer.AimDistance = fVal;
    break;
  case 102:
    pPlayer.gameSpeed = fVal;
    break;

  // NINLITE Sliders & Dropdown Values
  case 201:
    pEspPlayer.boxType = (BoxType)((int)fVal);
    break;
  case 202:
    pEspPlayer.lineType = (LineType)((int)fVal);
    break;
  case 303:
    pPlayer.AimFov = fVal;
    SyncWithNinLiteDaemon(58, true, (int)fVal);
    break;
  case 304:
    pPlayer.AimFovScale = 1.0f + (fVal * 0.1f);
    SyncWithNinLiteDaemon(62, true, (int)fVal);
    break;
  case 305:
    // Silent Target: 0 = Legit, 1 = Head, 2 = Chest, 3 = Neck
    pPlayer.silentAimHead = ((int)fVal == 1 || (int)fVal == 0);
    SyncWithNinLiteDaemon(70, true, ((int)fVal == 1) ? 100 : 0);
    break;
  default:
    break;
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_getFeatureState(JNIEnv *env,
                                                        jclass clazz,
                                                        jint featId) {
  switch (featId) {
  case 1:
    return (jboolean)Actived.activar;
  case 2:
    return (jboolean)pEspPlayer.espCaixa;
  case 3:
    return (jboolean)pEspPlayer.espSkeleton;
  case 4:
    return (jboolean)pEspPlayer.espLinha;
  case 5:
    return (jboolean)pEspPlayer.espHealth;
  case 6:
    return (jboolean)pEspPlayer.espName;
  case 7:
    return (jboolean)pEspPlayer.espDistance;
  case 8:
    return (jboolean)pEspPlayer.espDrawCount;
  case 9:
    return (jboolean)pEspPlayer.espIden360;

  case 20:
    return (jboolean)pPlayer.aimbot;
  case 21:
    return (jboolean)pPlayer.aimbotlock;
  case 22:
    return (jboolean)pPlayer.silen4a;
  case 23:
    return (jboolean)pPlayer.aimMagnet;
  case 24:
    return (jboolean)pPlayer.ignoreKnocked;
  case 25:
    return (jboolean)pPlayer.ignoreBots;
  case 26:
    return (jboolean)pPlayer.silentAimHead;

  case 40:
    return (jboolean)pPlayer.NoRecoil;
  case 41:
    return (jboolean)pPlayer.SpeedHackV2;
  case 42:
    return (jboolean)pPlayer.PlayerToMe;
  case 43:
    return (jboolean)pPlayer.undercam;
  default:
    return JNI_FALSE;
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isKernelDriverConnected(JNIEnv *env,
                                                                jclass clazz) {
  if (driver && driver->isConnected() && driver->getFd() >= 0)
    return JNI_TRUE;
  if (driver && driver->initDriver())
    return JNI_TRUE;
  return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_NativeBridge_getEngineLiveStatus(JNIEnv *env,
                                                            jclass clazz) {
  char buf[512];
  bool connected = (driver && driver->isConnected() && driver->getFd() >= 0);
  if (!connected && driver) {
    connected = driver->initDriver();
  }
  const char *node = (connected && strlen(driver->getNodeName()) > 0)
                         ? driver->getNodeName()
                         : "/dev/BYNb (DISCONNECTED)";
  const char *mode = connected ? driver->getDriverModeName() : "OFFLINE";
  int fd = connected ? driver->getFd() : -1;
  pid_t targetPid = (connected && driver) ? driver->getPid() : 0;

  int entities = connected ? s_cachedResponse.PlayerCount : 0;
  int visible = connected ? s_cachedResponse.VisibleEnemyCount : 0;
  bool espActive = Actived.activar;
  bool aimActive = pPlayer.aimbot;

  snprintf(buf, sizeof(buf),
           "KERNEL DRIVER : %s\n"
           "DEVICE NODE   : %s (fd: %d)\n"
           "CHANNEL MODE  : %s\n"
           "TARGET PID    : %s\n"
           "MEMORY IOCTL  : %s\n"
           "ENTITIES READ : %d TARGETS\n"
           "ENEMIES VIS   : %d VISIBLE\n"
           "HARDWARE ESP  : %s\n"
           "AIM ENGINE    : %s",
           connected ? "ONLINE" : "OFFLINE (NOT LOADED)", node, fd, mode,
           (targetPid > 0) ? std::to_string(targetPid).c_str()
                           : "NONE (NO TARGET)",
           connected ? "ACTIVE (32-BYTE RING 0)" : "STANDBY (LOADER REQUIRED)",
           entities, visible, espActive ? "ACTIVE" : "STANDBY",
           aimActive ? "ENABLED" : "OFF");
  return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ninmods_main_core_NativeBridge_getDetectedGamePid(JNIEnv *env, jclass clazz) {
  if (target_pid > 0 && kill(target_pid, 0) == 0) {
    return (jint)target_pid;
  }
  target_pid = FindGamePid();
  return (jint)target_pid;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isLiveGameDetected(JNIEnv *env, jclass clazz) {
  if (target_pid > 0 && kill(target_pid, 0) == 0) {
    return JNI_TRUE;
  }
  target_pid = FindGamePid();
  return (target_pid > 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_NativeBridge_getNinliteStateJson(JNIEnv *env, jclass clazz) {
  std::string json = NinliteEngine::GetStateJson();
  return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_startNinliteEngineNative(JNIEnv *env, jclass clazz, jstring jNativeLibDir) {
  std::string nativeLibDir = "";
  if (jNativeLibDir) {
    const char *chars = env->GetStringUTFChars(jNativeLibDir, nullptr);
    if (chars) {
      nativeLibDir = chars;
      env->ReleaseStringUTFChars(jNativeLibDir, chars);
    }
  }
  NinliteEngine::StartEngine(nativeLibDir);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_stopNinliteEngineNative(JNIEnv *env, jclass clazz) {
  NinliteEngine::StopEngine();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isNinliteSetupDone(JNIEnv *env, jclass clazz) {
  return NinliteEngine::IsSetupDone() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_NativeBridge_getDetectedGameTitle(JNIEnv *env, jclass clazz) {
  pid_t pid = (target_pid > 0 && kill(target_pid, 0) == 0) ? target_pid : FindGamePid();
  if (pid > 0) {
    target_pid = pid;
    char cmdline[256] = {0};
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
    FILE* f = fopen(path, "r");
    if (f) {
      fgets(cmdline, sizeof(cmdline), f);
      fclose(f);
    }
    if (strstr(cmdline, "com.dts.freefiremax")) {
      return env->NewStringUTF("Free Fire Max");
    } else if (strstr(cmdline, "com.dts.freefireth") || strstr(cmdline, "com.dts.freefirein")) {
      return env->NewStringUTF("Free Fire Normal");
    } else {
      return env->NewStringUTF("Free Fire Active");
    }
  }
  return env->NewStringUTF("Waiting for Game...");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isEmulatorDetected(JNIEnv *env, jclass clazz) {
  return NinliteEngine::CheckEmulatorDetected() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_NativeBridge_getConnectionMode(JNIEnv *env, jclass clazz) {
  return env->NewStringUTF(NinliteEngine::GetConnectionModeStr());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isShellDaemonRunning(JNIEnv *env, jclass clazz) {
  return NinliteEngine::CheckShellDaemonRunning() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_Utils_getGamePackage(JNIEnv *env, jclass clazz) {
  return env->NewStringUTF(OBFUSCATE("com.dts.freefireth"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_Utils_getDaemonName(JNIEnv *env, jclass clazz) {
  return env->NewStringUTF("");
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_DashboardActivity_setModeNative(JNIEnv *env,
                                                           jclass clazz,
                                                           jint mode) {
  g_hackVersion = (mode == 2) ? 2 : 1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_ModeSelectActivity_setModeNative(JNIEnv *env,
                                                            jclass clazz,
                                                            jint mode) {
  g_hackVersion = (mode == 2) ? 2 : 1;
}

extern "C" JavaVM *g_JavaVM = nullptr;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  g_JavaVM = vm;
  signal(SIGPIPE, SIG_IGN);
  return JNI_VERSION_1_6;
}