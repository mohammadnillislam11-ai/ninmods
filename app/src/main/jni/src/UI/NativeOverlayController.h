#pragma once

#include <string>
#include <functional>
#include <atomic>
#include <mutex>
#include <vector>
#if __has_include(<GLES3/gl3.h>)
#include <GLES3/gl3.h>
#else
#include "../Includes/android_ide_stubs/GLES3/gl3.h"
#endif
#include "IBrandMenu.h"

/**
 * Master Native ImGui Overlay Controller.
 * Manages the entire C++ rendering state machine:
 *   1. Modal License Verification Popup
 *   2. Modal Driver Status Check Popup (Root / Non-Root preview)
 *   3. Modern Draggable Floating Logo (in compact 76dp overlay window)
 *   4. Active Brand Tactical Mod Menu (in expanded responsive overlay window)
 *
 * THREAD SAFETY: the shared flags are written from the JNI thread and read
 * on the GL render thread. They are atomics now; the strings and the
 * std::function callback are protected by a mutex.
 */
class NativeOverlayController {
public:
    static std::atomic<bool> s_Initialized;
    static std::atomic<bool> s_MenuOpen;
    static std::atomic<bool> s_DriverAlertDismissed;
    /* The screen size / DPI are written from the JNI thread (nativeResize,
     * SetDpiScale) and read on the GL render thread -> they must be atomic. */
    static std::atomic<int> s_ScreenWidth;
    static std::atomic<int> s_ScreenHeight;
    static std::atomic<float> s_DpiScale;
    static std::atomic<float> s_LogoX;
    static std::atomic<float> s_LogoY;
    /* Strings and the callback are protected by s_StateMutex. */
    static std::string s_ActiveBrandId;
    static std::string s_DeviceHwid;
    static std::atomic<GLuint> s_LogoTexture;
    static std::function<void()> s_OpenLoaderCallback;

    static std::mutex s_StateMutex;

    static void Initialize(int screenWidth, int screenHeight, float dpiScale, const std::string& brandId, const std::string& hwid, bool licenseActive = false);
    static void SetScreenSize(int width, int height);
    static void SetDpiScale(float dpi);
    static void SetActiveBrand(const std::string& brandId);
    static void SetOpenLoaderCallback(std::function<void()> cb);
    static void SetLogoTexture(GLuint tex);

    // Apply device-specific DPI and touch-friendly styling
    static void ApplyDpiTheme();

    // Main Render Loop (called every frame on OpenGL ES3 EGL surface)
    static void RenderFrame(bool isKernelActive);

    // Check if any modal popup (License or Driver Alert) is currently requesting input
    static bool IsModalPopupActive();

    /* Touch input.
     * The Android input thread must NOT write into ImGuiIO directly: the GL
     * thread runs NewFrame() at the same time and ImGui is strictly single
     * threaded. The events are queued here and applied by the render thread
     * through DrainTouchEvents() right before NewFrame(). */
    static void QueueTouchEvent(int action, float x, float y);
    static void DrainTouchEvents();
    static bool HandleTouchEvent(int action, float x, float y);

    /* Publishes ImGui::GetIO().WantCaptureMouse for the input thread.
     * MUST be called from the render thread only (after NewFrame). */
    static void PublishWantCaptureMouse();

    /* Marks the ImGui context as existing / destroyed. */
    static void SetImGuiReady(bool ready);

private:
    /* The last published WantCaptureMouse value. The input thread reads this
     * instead of touching ImGuiIO, which the render thread owns. */
    static std::atomic<bool> s_WantCaptureMouse;
    /* True once the ImGui context exists. */
    static std::atomic<bool> g_ImGuiInitializedForOverlay;
};
