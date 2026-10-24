#pragma once

#include <string>
#include <functional>
#include <GLES3/gl3.h>
#include "IBrandMenu.h"

/**
 * Master Native ImGui Overlay Controller.
 * Manages the entire C++ rendering state machine:
 *   1. Modal License Verification Popup
 *   2. Modal Driver Status Check Popup (Root / Non-Root preview)
 *   3. Modern Draggable Floating Logo
 *   4. Active Brand Tactical Mod Menu
 */
class NativeOverlayController {
public:
    static bool s_Initialized;
    static bool s_MenuOpen;
    static bool s_DriverAlertDismissed;
    static int s_ScreenWidth;
    static int s_ScreenHeight;
    static float s_LogoX;
    static float s_LogoY;
    static std::string s_ActiveBrandId;
    static std::string s_DeviceHwid;
    static GLuint s_LogoTexture;
    static std::function<void()> s_OpenLoaderCallback;

    static void Initialize(int screenWidth, int screenHeight, const std::string& brandId, const std::string& hwid, bool licenseActive = false);
    static void SetScreenSize(int width, int height);
    static void SetActiveBrand(const std::string& brandId);
    static void SetOpenLoaderCallback(std::function<void()> cb);
    static void SetLogoTexture(GLuint tex);

    // Main Render Loop (called every frame on OpenGL ES3 EGL surface)
    static void RenderFrame(bool isKernelActive);

    // Native Touch Event Dispatcher (bypasses Android 12+ BLOCK_UNTRUSTED_TOUCHES)
    static bool HandleTouchEvent(int action, float x, float y);
};
