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
#include <map>
#include <utility>
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
#include "ninlite_daemon/Namegun.h"
#include "Includes/Logger.h"
#include "Includes/Utils.h"
#include "Detection/GameDetection.h"
#include "Includes/NativeCrypto.h"
#include "Includes/obfuscate.h"
#include "Unity/Color.hpp"
#include "Unity/ESP.h"
#include "Unity/Unity.h"
#include "Unity/Vector2.hpp"
#include "Unity/Vector3.hpp"


#include "Icon/Font.h"
#include "Icon/Icon.h"
#include "Icon/Iconcpp.h"
#include "UI/NativeOverlayController.h"
#include "UI/Popups/LicensePopup.h"
#include "Engine/NinliteEngine.h"
#include "Includes/FeatureBridge.h"
#include "Menu/Menu.hpp"

// ============================================
// GLOBAL SYSTEM STATE & PROFILE SETTINGS
// ============================================
/* These used to be hard coded to `true`, which meant every native
 * authorisation gate was a no-op. They now mirror the genuine session state
 * that the Java layer pushes through SecurityVault.nativeSetSession(). */
inline bool securityChecksPassed() { return SecurityVault_IsSessionValid(); }

inline bool NativeIsLoggedIn() { return SecurityVault_IsSessionValid(); }

// ============================================
// EXISTING GLOBAL VARIABLES AND FUNCTIONS
// ============================================

float g_espLeftAdjust = 0.0f;
float g_espRightAdjust = 0.0f;
static std::string g_espWatermark = "";

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

void DrawFOVCircle(ESP esp, float fov, Color color) {
  if (fov <= 0)
    return;

  auto screenHeight = esp.getHeight();
  auto screenWidth = esp.getWidth();

  float centerX = screenWidth / 2.0f;
  float centerY = screenHeight / 2.0f;

  /* THE AIM RADIUS - ONE FORMULA FOR THE WHOLE PROJECT
   * --------------------------------------------------
   * The circle that is drawn and the circle that the engine uses to gate
   * the target selection have to be the SAME circle, otherwise the user
   * sees a target inside the circle and the aim does not take it.
   *
   * The reference formula is a share of the screen WIDTH:
   *
   *     radius = (AimFov / 180) * (screenWidth * 0.55)
   *
   * The screen height was used here before, and the daemon used a third
   * variant (screenHeight * 0.55). Both were wrong in a different way:
   * on a 20:9 phone the width based circle is noticeably larger than
   * the height based one, so the drawn circle and the real aim radius
   * drifted apart depending on the aspect ratio of the device.
   *
   * GameEngine.h uses exactly the same expression for the FOV gate, so
   * the two now agree for every screen size.
   */
  float radius = (fov / 180.0f) * (screenWidth * 0.55f);
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

  if (!g_espWatermark.empty()) {
    float wmSize = 13.0f;
    Color wmColor = Color(255, 255, 255, 220);
    Color stroke = Color(0, 0, 0, 200);
    float wmX = screenWidth / 2.0f;
    float wmY = 45.0f;
    esp.DrawText(stroke, g_espWatermark.c_str(), Vector2(wmX - 1, wmY - 1), wmSize);
    esp.DrawText(stroke, g_espWatermark.c_str(), Vector2(wmX + 1, wmY + 1), wmSize);
    esp.DrawText(wmColor, g_espWatermark.c_str(), Vector2(wmX, wmY), wmSize);
  }

  bool anyAimActive = pPlayer.silen4a || pPlayer.aimbot || pPlayer.aimbotlock || pPlayer.aimMagnet;
  if (Actived.activar && anyAimActive && showFOVCircle &&
      pPlayer.AimFov > 0) {
    DrawFOVCircle(esp, pPlayer.AimFov, pEspPlayer.espColor);
  }

  auto response = getData(screenWidth, screenHeight);
  if (!response.Success)
    return;

  if (response.PlayerCount <= 0)
    return;

  // Draw Live Threat Counter HUD once per frame if enabled
  if (Actived.activar && (pEspPlayer.espDrawCount || pEspPlayer.espCount)) {
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

  float globalThickness = pEspPlayer.thickness.load(std::memory_order_relaxed);

  for (int i = 0; i < response.PlayerCount; i++) {
    auto player = response.Players[i];
    if (player.Head.Z <= 0.0f)
      continue;
    if (!Actived.activar)
      continue;

    Vector3 headPos = player.Head;
    Vector3 toePos = player.Toe;
    if (toePos.Z <= 0.0f)
      toePos = headPos;

    float distance = player.Distancia;
    if (distance < 0.1f || distance > 150.0f)
      continue;

    // Dynamic distance scaling for clean visuals
    float thickness = (distance > 70.0f) ? 1.0f : globalThickness;
    float textSizeVal = pEspPlayer.textSize.load(std::memory_order_relaxed);
    float fSize = (distance > 70.0f) ? 10.0f : textSizeVal;

    float headToFeetHeight = std::abs(toePos.Y - headPos.Y);
    if (headToFeetHeight < 3.0f)
      headToFeetHeight = 3.0f;
    float boxHeight = headToFeetHeight * 1.15f;
    float width = boxHeight * 0.48f;

    float totalXAdjust = g_espRightAdjust - g_espLeftAdjust;

    float boxY = headPos.Y - (boxHeight * 0.12f);
    float boxX = headPos.X - (width / 2.0f) + totalXAdjust;

    Color currentBoxColor = pEspPlayer.espColor;
    if (player.Name)
      currentBoxColor = Color::Red(); // player.Name == true -> bot
    if (player.IsCaido)
      currentBoxColor = Color::Red(); // knocked (changed from Yellow to Red)
    Color strokeColor = Color(0, 0, 0, 255);

    float adjustedHeadX = headPos.X + totalXAdjust;

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
      // ZqwClient-style Dual Glow anti-aliasing Tracer Line with distance fade
      float distFactor = 1.0f - (distance / 350.0f);
      if (distFactor < 0.25f) distFactor = 0.25f;
      if (distFactor > 1.0f) distFactor = 1.0f;

      Color glowColor = currentBoxColor;
      glowColor.a = static_cast<int>(80 * distFactor);
      esp.DrawLine(glowColor, thickness + 2.5f, lineStart, lineEnd);

      Color coreColor = currentBoxColor;
      coreColor.a = static_cast<int>(240 * distFactor);
      esp.DrawLine(coreColor, thickness, lineStart, lineEnd);
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
      const bool hasHealth = pEspPlayer.espHealth && player.Health > 0;

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

        // ----- SYNCHRONIZE NAME BACKGROUND AND HEALTH BAR WIDTHS (existing logic) -----
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

        // ----- NEW LOGIC: When all three are present, unify total name+distance width with health bar -----
        if (hasName && hasDistance && hasHealth) {
          float combinedWidth =
              nameSegmentWidth + distSegmentWidth + spacing;
          if (combinedWidth > healthBarWidth) {
            float excess = combinedWidth - healthBarWidth;
            float newNameWidth = nameSegmentWidth - excess;
            float minNameWidth = rawNameSegmentWidth;
            if (newNameWidth < minNameWidth) {
              float nameDeficit = minNameWidth - newNameWidth;
              newNameWidth = minNameWidth;
              float newDistWidth = distSegmentWidth - nameDeficit;
              const float minDistWidth = 30.0f;
              if (newDistWidth < minDistWidth) {
                newDistWidth = minDistWidth;
              }
              distSegmentWidth = newDistWidth;
            }
            nameSegmentWidth = newNameWidth;
            combinedWidth = nameSegmentWidth + distSegmentWidth + spacing;
          } else if (combinedWidth < healthBarWidth) {
            healthBarWidth = combinedWidth;
          }
        }

        const float healthBarHeight = 6.0f; // constant height

        // Total width required for text segments (name + distance + spacing)
        float contentWidth = nameSegmentWidth + distSegmentWidth;
        if (nameSegmentWidth > 0.0f && distSegmentWidth > 0.0f)
          contentWidth += spacing;

        bool hasText = hasName || hasDistance;

        // ----- TEXT PANEL WIDTH (adapts to name + distance) -----
        float layoutInnerWidth;
        if (hasText) {
          layoutInnerWidth = contentWidth;
        } else {
          layoutInnerWidth = defaultHealthWidth; // health-only mode
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

          float healthY;
          if (hasText) {
            healthY = layoutY + roundedPanelHeight + 2.0f;
          } else {
            healthY = boxY - healthBarHeight - 8.0f;
            if (healthY < 2.0f)
              healthY = 2.0f;
          }

          float healthBarX = adjustedHeadX - healthBarWidth * 0.5f;

          DrawRoundedPanel(healthBarX, healthY, healthBarWidth, healthBarHeight,
                           healthBarHeight * 0.5f, Color(45, 45, 45, 210));

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

    if (pEspPlayer.espWeapon && player.WeaponId > 0) {
      std::string wName = Namegun::GetGunName(player.WeaponId);
      if (!wName.empty()) {
        float fontSize = std::max(10.0f, fSize - 2.0f);
        float weaponY = boxY + boxHeight + 8.0f;
        Color wColor = Color(240, 240, 240, 230);
        esp.DrawText(strokeColor, wName.c_str(), Vector2(adjustedHeadX - 1, weaponY - 1), fontSize);
        esp.DrawText(strokeColor, wName.c_str(), Vector2(adjustedHeadX + 1, weaponY + 1), fontSize);
        esp.DrawText(wColor, wName.c_str(), Vector2(adjustedHeadX, weaponY), fontSize);
      }
    }
  }
}


extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_drawEspNative(JNIEnv *env, jclass type,
                                                      jobject espView,
                                                      jobject canvas, jint width,
                                                      jint height) {
  if (!SecurityVault_IsSessionValid()) {
    return; // Zero ESP drawing if no genuine verified server session
  }
  /* The real screen size arrives with every frame. It was never stored, so
   * the engine always worked with the 1920x1080 fallback and the layout was
   * wrong on every other device. */
  if (width > 0 && height > 0) {
    g_ScreenWidth = static_cast<int>(width);
    g_ScreenHeight = static_cast<int>(height);
    NativeOverlayController::SetScreenSize(static_cast<int>(width),
                                           static_cast<int>(height));
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


// ========================================================
// GLES3 JNI VIEW NATIVE BRIDGE (OpenGL ES 3.0 / ImGui ESP)
// ========================================================
extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_launchBrandOverlay(
    JNIEnv *env, jclass clazz, jstring jBrandId, jstring jHwid,
    jboolean isLicenseActive, jfloat density, jstring jPrefillKey,
    jstring jServerUrl) {
  const char *rawBrand = jBrandId ? env->GetStringUTFChars(jBrandId, nullptr) : nullptr;
  const char *rawHwid = jHwid ? env->GetStringUTFChars(jHwid, nullptr) : nullptr;
  const char *rawPrefill =
      jPrefillKey ? env->GetStringUTFChars(jPrefillKey, nullptr) : nullptr;
  const char *rawUrl = jServerUrl ? env->GetStringUTFChars(jServerUrl, nullptr) : nullptr;

  /* GetStringUTFChars can fail and return null. Building a std::string from a
   * null pointer is undefined behaviour, so every value falls back to an empty
   * string instead. The raw pointers stay separate, because only a pointer
   * that really came from GetStringUTFChars may be released again. */
  const char *bId = rawBrand ? rawBrand : "";
  const char *hwid = rawHwid ? rawHwid : "";
  const char *prefill = rawPrefill ? rawPrefill : "";
  const char *sUrl = rawUrl ? rawUrl : "";
  if (rawBrand == nullptr || rawHwid == nullptr || rawPrefill == nullptr ||
      rawUrl == nullptr) {
    if (env->ExceptionCheck()) env->ExceptionClear();
  }

  NativeOverlayController::Initialize(g_ScreenWidth, g_ScreenHeight,
                                      (float)density, bId, hwid,
                                      (bool)isLicenseActive);
  if (!isLicenseActive) {
    LicensePopup::Open(hwid, prefill, sUrl);
  }

  /* Cache the Java NativeBridge class on the JNI thread so the GL render
   * thread can call into it without FindClass() (which fails there). */
  if (g_NativeBridgeClassRef == nullptr) {
    jclass local = env->FindClass("com/ninmods/main/core/NativeBridge");
    if (local != nullptr) {
      g_NativeBridgeClassRef = (jclass)env->NewGlobalRef(local);
      env->DeleteLocalRef(local);
    } else if (env->ExceptionCheck()) {
      env->ExceptionClear();
    }
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

  if (rawBrand)
    env->ReleaseStringUTFChars(jBrandId, rawBrand);
  if (rawHwid)
    env->ReleaseStringUTFChars(jHwid, rawHwid);
  if (rawPrefill)
    env->ReleaseStringUTFChars(jPrefillKey, rawPrefill);
  if (rawUrl)
    env->ReleaseStringUTFChars(jServerUrl, rawUrl);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_setActiveChannel(JNIEnv *env,
                                                          jclass clazz, jint channel) {
  (void) env;
  (void) clazz;
  /* Rule 3: the user selects the channel, the engine never switches it. */
  if (!NinChannel::SetActiveChannel(static_cast<int>(channel))) {
    NIN_LOGE("CHANNEL", "Rejected invalid memory channel: %d", static_cast<int>(channel));
    return;
  }
  StopMemoryWorkersForChannelSwitch();
  NIN_LOGI("CHANNEL", "active memory channel: %s", NinChannel::DescribeActiveChannel());
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

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_setKeyFromJava(JNIEnv *env,
                                                        jclass clazz,
                                                        jstring jKey) {
  /* NativeBridge.java declares setKeyFromJava(String) but there was NO C++
   * export for it, so the VERIFY and PASTE buttons of the licence dialog
   * threw an UnsatisfiedLinkError on the first tap and the verification flow
   * never reached AuthManager.updateActiveLicense(). */
  if (!jKey) return;
  const char* chars = env->GetStringUTFChars(jKey, nullptr);
  if (!chars) {
    if (env->ExceptionCheck()) env->ExceptionClear();
    return;
  }
  std::string key(chars);
  env->ReleaseStringUTFChars(jKey, chars);
  LicensePopup::SetKey(key);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_handleTouch(JNIEnv *env, jclass clazz,
                                                    jint action, jfloat x,
                                                    jfloat y) {
  return (jboolean)NativeOverlayController::HandleTouchEvent(action, x, y);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isMenuOpen(JNIEnv *env, jclass clazz) {
  return (jboolean)NativeOverlayController::s_MenuOpen.load(
      std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_setMenuOpen(JNIEnv *env, jclass clazz,
                                                    jboolean open) {
  NativeOverlayController::s_MenuOpen.store((bool)open,
                                            std::memory_order_relaxed);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isOverlayActive(JNIEnv *env,
                                                        jclass clazz) {
  return (jboolean)NativeOverlayController::s_Initialized.load(
      std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_closeOverlay(JNIEnv *env,
                                                      jclass clazz) {
  NativeOverlayController::s_Initialized.store(false, std::memory_order_relaxed);
  NativeOverlayController::s_MenuOpen.store(false, std::memory_order_relaxed);
}

/* ------------------------------------------------------------------
 * DAEMON CONTROL SYNC (fire and forget)
 * ------------------------------------------------------------------
 * The daemon control messages are pushed from the UI thread (a switch, a
 * slider drag). Doing the socket round trip inline blocked the UI for up to
 * 200 ms per event - a single slider drag produced dozens of sequential
 * blocking round trips.
 *
 * The requests are therefore queued and delivered by one background worker.
 * Only the LATEST value per mode is kept, which is exactly the semantics of
 * a feature flag / slider.
 */
static void SyncWithNinLiteDaemon(int mode, bool isOn, int value) {
  /* Non-blocking: the DaemonLink worker owns the socket and coalesces the
   * newest value per mode. The menu thread never waits for the daemon. */
  NinEngine::DaemonLink::Instance().PushControl(mode, isOn, value);
}

/* The selected silent aim target (0 = Legit, 1 = Head, 2 = Chest, 3 = Neck).
 * The engine only models "head" and "not head", so the raw selection is kept
 * here to let the menu read back exactly what the user picked. */
static std::atomic<int> g_silentTargetSelection{1};

/*
 * The ESP master switch gates the whole entity pipeline of the engine
 * (GameEngine.h returns immediately when it is off), so it is written
 * through one helper: switching on an ESP sub feature switches the master
 * on as well and switching the master off switches every ESP sub feature
 * off again. A menu can therefore never show a switched on ESP that
 * renders nothing.
 */
static void SetEspMasterSwitch(bool on) {
  Actived.activar = on;
  SyncWithNinLiteDaemon(Ninlite::MODE_MASTER, on, 0);
}

/*
 * THE SINGLE ENGINE WRITER
 * ------------------------
 * Every menu of this app (the classic NativeBridge menu and the reference
 * parity DSL menu in Menu/Preferences.cpp) writes through this function, so
 * there is exactly one switch that maps a feature id onto an engine flag.
 *
 * EVERY FEATURE IS MANUAL: the flag is set exactly when the user toggles
 * it in the menu - nothing is refused, nothing is switched on by itself.
 * The old SAFE-mode gate used to refuse ten feature ids behind the
 * user's back, which on a non-rooted test device looked like "the menu
 * is broken". It is gone.
 *
 * A toggle is also the manual activation of the whole memory layer: only
 * from this moment on the memory layer may be used.
 */
void NinFeatureSetState(int featId, bool bState) {
  switch (featId) {
  // ESP Features
  case 1:
    SetEspMasterSwitch(bState);
    if (!bState) {
      /* Master off: every ESP sub feature goes with it. */
      pEspPlayer.espCaixa = false;
      pEspPlayer.espSkeleton = false;
      pEspPlayer.espLinha = false;
      pEspPlayer.espHealth = false;
      pEspPlayer.espName = false;
      pEspPlayer.espDistance = false;
      pEspPlayer.espDrawCount = false;
      pEspPlayer.espIden360 = false;
    }
    break;
  case 2:
    pEspPlayer.espCaixa = bState;
    if (bState) SetEspMasterSwitch(true);
    break;
  case 3:
    pEspPlayer.espSkeleton = bState;
    if (bState) SetEspMasterSwitch(true);
    break;
  case 4:
    pEspPlayer.espLinha = bState;
    if (bState) SetEspMasterSwitch(true);
    break;
  case 5:
    pEspPlayer.espHealth = bState;
    if (bState) SetEspMasterSwitch(true);
    break;
  case 6:
    pEspPlayer.espName = bState;
    if (bState) SetEspMasterSwitch(true);
    break;
  case 7:
    pEspPlayer.espDistance = bState;
    if (bState) SetEspMasterSwitch(true);
    break;
  case 8:
    pEspPlayer.espDrawCount = bState;
    if (bState) SetEspMasterSwitch(true);
    break;
  case 9:
    pEspPlayer.espIden360 = bState;
    if (bState) SetEspMasterSwitch(true);
    break;

  // Aimbot Features
  case 20:
    pPlayer.aimbot = bState;
    SyncWithNinLiteDaemon(Ninlite::MODE_AIM_HARD, bState, 0);
    break;
  case 21:
    pPlayer.aimbotlock = bState;
    SyncWithNinLiteDaemon(Ninlite::MODE_AIM_LOCK, bState, 0);
    break;
  case 22:
    pPlayer.silen4a = bState;
    /* The root Daemon keeps its own copy of the aim flags. Without this the
     * classic menu switch changed the app side only, while the Daemon (the
     * component that actually moves the aim) never heard about it. */
    SyncWithNinLiteDaemon(Ninlite::MODE_SILENT, bState, 0);
    if (bState) SetEspMasterSwitch(true);
    break;
  case 23:
    pPlayer.aimMagnet = bState;
    break;
  case 24:
    pPlayer.ignoreKnocked = bState;
    SyncWithNinLiteDaemon(Ninlite::MODE_IGNORE_KNOCK, bState, 0);
    break;
  case 25:
    pPlayer.ignoreBots = bState;
    break;
  case 26:
    pPlayer.silentAimHead = bState;
    break;
  case 27:
    showFOVCircle = bState;
    break;

  /* Reference engine options: the weapon name, the threat counter and the
   * info line at the bottom of the screen. */
  case 206:
    pEspPlayer.espWeapon = bState;
    break;
  case 207:
    pEspPlayer.espCount = bState;
    break;
  case 208:
    pEspPlayer.espInfo = bState;
    break;
  case 216:
    pPlayer.KillInChest = bState;
    SyncWithNinLiteDaemon(Ninlite::MODE_KILL_IN_CHEST, bState, 0);
    break;

  // Combat / Memory Features
  case 40:
    pPlayer.NoRecoil = bState;
    break;
  case 41:
    pPlayer.SpeedHackV2 = bState;
    speedHackEnabled = bState;
    break;
  case 42:
    pPlayer.PlayerToMe = bState;
    break;
  case 43:
    pPlayer.undercam = bState;
    break;
  case 44:
    Actived.Bypass = bState;
    break;
  // NINLITE Checkbox & Switch Features
  // Ids 111..115: 101/102 belong to the "MAX TARGET DISTANCE" and
  // "GAME SPEED" sliders of the main overlay menu. Sharing the numeric ids
  // meant that a single NINLITE checkbox also overwrote an unrelated value.
  /* 111..115 write exactly ONE flag each. They used to force the master
   * switch on unconditionally, so switching a feature OFF switched the whole
   * ESP master ON and the master could never be turned off from this menu.
   * The master is now handled explicitly by the Java state store, which
   * persists it and mirrors it into the other menu. */
  case 111:
    pEspPlayer.espCaixa = bState;
    break;
  case 112:
    pEspPlayer.espLinha = bState;
    break;
  case 113:
    pEspPlayer.espHealth = bState;
    break;
  case 114:
    pEspPlayer.espDistance = bState;
    break;
  case 115:
    pEspPlayer.espName = bState;
    break;
  case 301:
    /* "Silent Aim Enable" must not silently switch the separate
     * "AIMBOT AUTO LOCK" feature on or off - that used to leave the Java
     * switch showing a stale state while the native flag had changed. */
    pPlayer.silen4a = bState;
    SyncWithNinLiteDaemon(Ninlite::MODE_SILENT, bState, 0);
    break;
  case 302:
    pPlayer.ignoreKnocked = bState;
    SyncWithNinLiteDaemon(Ninlite::MODE_IGNORE_KNOCK, bState, 0);
    break;
  case 999:
    Actived.activar = bState;
    SyncWithNinLiteDaemon(Ninlite::MODE_MASTER, bState, 0);
    NIN_LOGI("NINLITE", "Root Engine state toggled: %d", bState ? 1 : 0);
    break;

  default:
    break;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_setFeatureState(JNIEnv *env,
                                                        jclass clazz,
                                                        jint featId,
                                                        jboolean state) {
  NinFeatureSetState((int)featId, (bool)state);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_setFeatureFloat(JNIEnv *env,
                                                        jclass clazz,
                                                        jint featId,
                                                        jfloat value) {
  NinFeatureSetValue((int)featId, (float)value);
}

void NinFeatureSetValue(int featId, float fVal) {
  switch (featId) {
  case 100:
    pPlayer.AimFov = fVal;
    SyncWithNinLiteDaemon(Ninlite::MODE_AIM_FOV, true, (int)fVal);
    break;
  case 101:
    pPlayer.AimDistance = fVal;
    SyncWithNinLiteDaemon(Ninlite::MODE_AIM_DISTANCE, true, (int)fVal);
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
  /* Reference engine sliders: the size of the ESP text and the head and
   * chest rate of the aim (how many of every ten shots hit the head). */
  case 209:
    pEspPlayer.textSize = fVal;
    break;
  case 214:
    pPlayer.HeadRate = (int)fVal;
    SyncWithNinLiteDaemon(Ninlite::MODE_HEAD_RATE, true, (int)fVal);
    break;
  case 215:
    pPlayer.ChestRate = (int)fVal;
    SyncWithNinLiteDaemon(Ninlite::MODE_CHEST_RATE, true, (int)fVal);
    break;
  case 303:
    pPlayer.AimFov = fVal;
    SyncWithNinLiteDaemon(Ninlite::MODE_AIM_FOV, true, (int)fVal);
    break;
  case 304:
    /* "Legit Delay" (0..10). It used to overwrite the FOV circle scale and
     * was additionally forwarded to the Daemon as "chest shot rate", so one
     * slider controlled three unrelated things. */
    pPlayer.legitDelayMs = fVal;
    SyncWithNinLiteDaemon(Ninlite::MODE_LEGIT_DELAY, true, (int)fVal);
    break;
  case 305:
    /* Silent Target: 0 = Legit, 1 = Head, 2 = Chest, 3 = Neck.
     * The old expression evaluated to true for BOTH "Legit" and "Head",
     * so choosing "Legit" silently enabled head-only aiming. */
    {
      int selection = (int)fVal;
      if (selection < 0) selection = 0;
      if (selection > 3) selection = 3;
      g_silentTargetSelection.store(selection);
      pPlayer.silentAimHead = (selection == 1);
      if (selection == 1) {
        SyncWithNinLiteDaemon(Ninlite::MODE_HEAD_RATE, true, 3);
        SyncWithNinLiteDaemon(Ninlite::MODE_CHEST_RATE, true, 0);
      } else {
        SyncWithNinLiteDaemon(Ninlite::MODE_HEAD_RATE, true, 0);
        SyncWithNinLiteDaemon(Ninlite::MODE_CHEST_RATE, true, 3);
      }
    }
    break;
  default:
    break;
  }
}

/* Reads the live value of a slider / dropdown back out of the engine.
 * Without this the menus could only WRITE the values, so they had to hard
 * code the initial slider position and silently overwrote the engine every
 * time the menu was opened. */
extern "C" JNIEXPORT jfloat JNICALL
Java_com_ninmods_main_core_NativeBridge_getFeatureFloat(JNIEnv *env,
                                                          jclass clazz,
                                                          jint featId) {
  return (jfloat)NinFeatureGetValue((int)featId);
}

float NinFeatureGetValue(int featId) {
  switch (featId) {
  case 100:
  case 303:
    return (jfloat)pPlayer.AimFov.load();
  case 101:
    return (jfloat)pPlayer.AimDistance.load();
  case 102:
    return (jfloat)pPlayer.gameSpeed.load();
  case 201:
    return (jfloat)(int)pEspPlayer.boxType.load();
  case 202:
    return (jfloat)(int)pEspPlayer.lineType.load();
  case 209:
    return (jfloat)pEspPlayer.textSize.load();
  case 214:
    return (jfloat)pPlayer.HeadRate.load();
  case 215:
    return (jfloat)pPlayer.ChestRate.load();
  case 304:
    return (jfloat)pPlayer.legitDelayMs.load();
  case 305:
    return (jfloat)g_silentTargetSelection.load();
default:
    return -1.0f;
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_getFeatureState(JNIEnv *env,
                                                          jclass clazz,
                                                          jint featId) {
  return NinFeatureGetState((int)featId) ? JNI_TRUE : JNI_FALSE;
}

bool NinFeatureGetState(int featId) {
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
  case 27:
    return (jboolean)showFOVCircle;
  case 206:
    return (jboolean)pEspPlayer.espWeapon;
  case 207:
    return (jboolean)pEspPlayer.espCount;
  case 208:
    return (jboolean)pEspPlayer.espInfo;
  case 216:
    return (jboolean)pPlayer.KillInChest;

  case 40:
    return (jboolean)pPlayer.NoRecoil;
  case 41:
    return (jboolean)pPlayer.SpeedHackV2;
  case 42:
    return (jboolean)pPlayer.PlayerToMe;
  case 43:
    return (jboolean)pPlayer.undercam;
  case 44:
    return (jboolean)Actived.Bypass;

  /* NINLITE alias ids. 111..115 and the legacy NINLITE ids 301/302 address
   * the very same engine flags as the classic menu ids, so a menu must be
   * able to READ the live state of every flag it is allowed to write.
   * Without these the getter answered JNI_FALSE for all of them, which is why
   * the menus had to fall back to hard coded check box states. */
  case 111:
    return (jboolean)pEspPlayer.espCaixa;
  case 112:
    return (jboolean)pEspPlayer.espLinha;
  case 113:
    return (jboolean)pEspPlayer.espHealth;
  case 114:
    return (jboolean)pEspPlayer.espDistance;
  case 115:
    return (jboolean)pEspPlayer.espName;
  case 301:
    return (jboolean)pPlayer.silen4a;
  case 302:
    return (jboolean)pPlayer.ignoreKnocked;
  case 999:
    return (jboolean)Actived.activar;
  default:
    return JNI_FALSE;
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isKernelDriverConnected(JNIEnv *env,
                                                                jclass clazz) {
  (void) env;
  (void) clazz;
  /* The answer must describe the driver, not the game: the driver is only
   * "connected" when an IOCTL was answered successfully. */
  return MemoryIO::IsDriverReady() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_NativeBridge_getEngineLiveStatus(JNIEnv *env,
                                                            jclass clazz) {
  (void) clazz;
  char buf[768];
  pid_t targetPid = target_pid.load(std::memory_order_relaxed);
  if (targetPid <= 0) targetPid = FindGamePid();

  /* Every line of this report describes an observed state: the driver node
   * that is open, whether an IOCTL was verified, the errno of the last
   * failure and the state of the QX daemon. */
  bool driverReady = MemoryIO::IsDriverReady();
  int fd = MemoryIO::g_driver_fd.load(std::memory_order_acquire);
  const char *node = MemoryIO::GetDriverNode();
  int lastErrno = MemoryIO::GetLastErrno();
  bool daemonConnected = NinliteEngine::CheckShellDaemonRunning();

  Response cached = GetCachedResponseSnapshot();
  int entities = (targetPid > 0) ? cached.PlayerCount : 0;
  int visible = (targetPid > 0) ? cached.VisibleEnemyCount : 0;
  bool espActive = Actived.activar;
  bool aimActive = pPlayer.aimbot;

  snprintf(buf, sizeof(buf),
           "MEMORY SOURCE: %s\n"
           "DEVICE NODE   : %s (fd: %d)\n"
           "CHANNEL MODE  : %s\n"
           "QX DAEMON     : %s\n"
           "TARGET PID    : %s\n"
           "MEMORY API    : %s\n"
           "LAST IOCTL ERR: %s\n"
           "ENTITIES READ : %d TARGETS\n"
           "ENEMIES VIS   : %d VISIBLE\n"
           "HARDWARE ESP  : %s\n"
           "AIM ENGINE    : %s",
           driverReady ? "ONLINE (Ring-0 IOCTL)" : "OFFLINE (no verified IOCTL)",
           node, fd,
           driverReady ? "RT Ring-0 Driver (IOCTL 0x801/0x802/0x803)"
                       : "RT Ring-0 Driver (NOT VERIFIED)",
           daemonConnected ? "connected" : "not running",
           (targetPid > 0) ? std::to_string(targetPid).c_str() : "NONE (NO TARGET)",
           driverReady ? "IOCTL_RT_READ / IOCTL_RT_WRITE" : "NONE",
           lastErrno > 0 ? strerror(lastErrno) : "NONE",
           entities, visible, espActive ? "ACTIVE" : "STANDBY",
           aimActive ? "ENABLED" : "OFF");
  return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ninmods_main_core_NativeBridge_getDetectedGamePid(JNIEnv *env, jclass clazz) {
  /*
   * The detection lives in the dedicated engine
   * (jni/src/Detection/GameDetection.cpp). This function only keeps the
   * pid of the engine in sync with the one the memory layer uses.
   */
  NinDetection::GameProcess process = NinDetection::Detect();
  if (process.isDetected && process.pid > 0) {
    target_pid.store(process.pid, std::memory_order_relaxed);
    return (jint)process.pid;
  }
  target_pid.store(0, std::memory_order_relaxed);
  return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isLiveGameDetected(JNIEnv *env, jclass clazz) {
  NinDetection::GameProcess process = NinDetection::Detect();
  if (process.isDetected && process.pid > 0) {
    target_pid.store(process.pid, std::memory_order_relaxed);
    return JNI_TRUE;
  }
  return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_NativeBridge_getNinliteStateJson(JNIEnv *env, jclass clazz) {
  std::string json = NinliteEngine::GetStateJson();
  return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_startNinliteEngineNative(JNIEnv *env, jclass clazz, jstring jNativeLibDir) {
  /* Cache the NativeBridge class on THIS JNI thread before the engine is
   * started. The engine runs the root script on a detached native thread,
   * and a thread attached through AttachCurrentThread() uses the system
   * class loader where FindClass() cannot see the app classes. */
  if (g_NativeBridgeClassRef == nullptr && clazz != nullptr) {
    g_NativeBridgeClassRef = (jclass)env->NewGlobalRef(clazz);
  }

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
  /*
   * The title comes from the detection engine. The old implementation read
   * /proc/<pid>/cmdline here a second time - that read fails since Android 9
   * for a process of another user, which is why the panel reported the
   * wrong game (and sometimes none at all).
   */
  NinDetection::GameProcess process = NinDetection::Detect();
  if (process.isDetected && process.pid > 0) {
    target_pid.store(process.pid, std::memory_order_relaxed);
    return env->NewStringUTF(process.gameTitle.c_str());
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
  (void) clazz;
  const std::string packageName = NinDetection::GetPackageName();
  return env->NewStringUTF(packageName.c_str());
}

extern "C" JavaVM *g_JavaVM = nullptr;

/* Global reference to the Java NativeBridge class.
 * Resolved on the JNI thread (which has the app class loader) and reused by
 * the GL render thread, where FindClass() would always fail. */
jclass g_NativeBridgeClassRef = nullptr;

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_NativeBridge_getMemoryAccessMode(JNIEnv *env, jclass clazz) {
  (void) clazz;
  /* The mode must describe the channel that is really active: the verified
   * Ring-0 driver of the app or the QX daemon of the root process. */
  if (MemoryIO::IsDriverReady()) {
    return env->NewStringUTF("Ring-0 Kernel Driver (IOCTL)");
  }
  if (NinliteEngine::CheckShellDaemonRunning()) {
    return env->NewStringUTF("Root Daemon (IPC, process_vm_readv)");
  }
  return env->NewStringUTF("Not connected");
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_setEspWatermark(JNIEnv *env, jclass clazz, jstring text) {
  (void) clazz;
  if (text == nullptr) {
    g_espWatermark.clear();
    return;
  }
  const char *value = env->GetStringUTFChars(text, nullptr);
  if (value != nullptr) {
    g_espWatermark = value;
    env->ReleaseStringUTFChars(text, value);
  }
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  g_JavaVM = vm;
  signal(SIGPIPE, SIG_IGN);
  Namegun::Init();

  /*
   * The mod menu binds its native methods here. A failed binding is logged
   * but does not abort the load: the library also carries the ESP engine,
   * the driver bridge and the licence vault, and those must keep working
   * even if the menu classes are not reachable.
   */
  JNIEnv *env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK && env != nullptr) {
    if (g_NativeBridgeClassRef == nullptr) {
      jclass local = env->FindClass("com/ninmods/main/core/NativeBridge");
      if (local != nullptr) {
        g_NativeBridgeClassRef = (jclass)env->NewGlobalRef(local);
        env->DeleteLocalRef(local);
      } else if (env->ExceptionCheck()) {
        env->ExceptionClear();
      }
    }
    if (RegisterNinMenu(env) != JNI_OK) {
      NIN_LOGI("MENU", "Native mod menu binding failed - the menu will not be available");
    }
    if (RegisterNinMenuPreferences(env) != JNI_OK) {
      NIN_LOGI("MENU", "Native mod menu preference binding failed - the menu will not be available");
    }
  }

  return JNI_VERSION_1_6;
}
