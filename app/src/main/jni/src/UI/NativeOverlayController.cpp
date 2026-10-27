#include "NativeOverlayController.h"
#include "Popups/LicensePopup.h"
#include "Popups/DriverAlertPopup.h"
#include "../Icon/TextureLoader.h"
#include <imgui.h>
#include <cmath>
#include <cstring>
#include <algorithm>

std::atomic<bool> NativeOverlayController::s_Initialized{false};
std::atomic<bool> NativeOverlayController::s_MenuOpen{false};
std::atomic<bool> NativeOverlayController::s_DriverAlertDismissed{false};
std::atomic<bool> NativeOverlayController::s_WantCaptureMouse{false};
std::atomic<bool> NativeOverlayController::g_ImGuiInitializedForOverlay{false};
std::atomic<int>  NativeOverlayController::s_ScreenWidth{1920};
std::atomic<int>  NativeOverlayController::s_ScreenHeight{1080};
std::atomic<float> NativeOverlayController::s_DpiScale{2.4f};
std::atomic<float> NativeOverlayController::s_LogoX{80.0f};
std::atomic<float> NativeOverlayController::s_LogoY{160.0f};
std::string NativeOverlayController::s_ActiveBrandId = "nin";
std::string NativeOverlayController::s_DeviceHwid = "";
std::atomic<GLuint> NativeOverlayController::s_LogoTexture{0};
std::function<void()> NativeOverlayController::s_OpenLoaderCallback = nullptr;
std::mutex NativeOverlayController::s_StateMutex;

namespace {
struct TouchEvent {
    int   action;
    float x;
    float y;
};
std::mutex           g_TouchMutex;
std::vector<TouchEvent> g_TouchQueue;
} // namespace

void NativeOverlayController::Initialize(int screenWidth, int screenHeight, float dpiScale, const std::string& brandId, const std::string& hwid, bool licenseActive) {
    s_ScreenWidth.store(screenWidth > 0 ? screenWidth : 1920, std::memory_order_relaxed);
    s_ScreenHeight.store(screenHeight > 0 ? screenHeight : 1080, std::memory_order_relaxed);
    SetDpiScale(dpiScale);

    std::string activeBrand;
    {
        std::lock_guard<std::mutex> lock(s_StateMutex);
        s_ActiveBrandId = !brandId.empty() ? brandId : "nin";
        s_DeviceHwid = hwid;
        activeBrand = s_ActiveBrandId;
    }


    if (licenseActive) {
        LicensePopup::SetVerified(true);
    } else {
        LicensePopup::SetVerified(false);
        LicensePopup::Open(hwid);
    }

    s_DriverAlertDismissed.store(false, std::memory_order_relaxed);
    s_MenuOpen.store(false, std::memory_order_relaxed);
    s_Initialized.store(true, std::memory_order_relaxed);
}

void NativeOverlayController::SetScreenSize(int width, int height) {
    if (width > 0) s_ScreenWidth.store(width, std::memory_order_relaxed);
    if (height > 0) s_ScreenHeight.store(height, std::memory_order_relaxed);
}

void NativeOverlayController::SetDpiScale(float dpi) {
    float scale = dpi;
    if (!(dpi > 0.5f)) {
        // Fallback calculation from resolution
        scale = (float)std::min(s_ScreenWidth.load(std::memory_order_relaxed),
                                 s_ScreenHeight.load(std::memory_order_relaxed)) / 380.0f;
    }
    if (scale < 2.0f) scale = 2.0f;
    if (scale > 4.2f) scale = 4.2f;
    s_DpiScale.store(scale, std::memory_order_relaxed);
}

void NativeOverlayController::SetActiveBrand(const std::string& brandId) {
    {
        std::lock_guard<std::mutex> lock(s_StateMutex);
        s_ActiveBrandId = brandId;
    }
}

void NativeOverlayController::SetOpenLoaderCallback(std::function<void()> cb) {
    /* The callback is copied into the popup, so it has to be protected: a
     * concurrent assignment while the render thread copies it destroyed the
     * captured object under the reader. */
    std::lock_guard<std::mutex> lock(s_StateMutex);
    s_OpenLoaderCallback = std::move(cb);
}

void NativeOverlayController::SetLogoTexture(GLuint tex) {
    s_LogoTexture.store(tex, std::memory_order_relaxed);
}

void NativeOverlayController::ApplyDpiTheme() {
    static float s_LastAppliedDpi = 0.0f;
    const float dpi = s_DpiScale.load(std::memory_order_relaxed);
    if (fabsf(s_LastAppliedDpi - dpi) < 0.05f) return;

    ImGuiIO& io = ImGui::GetIO();
    io.FontGlobalScale = 1.0f; // 1:1 Native crisp vector font rendering (Zero pixelation)

    ImGuiStyle& style = ImGui::GetStyle();
    style = ImGuiStyle(); // Reset to fresh baseline
    style.ScaleAllSizes(1.30f); // Balanced touch-friendly scaling as in NINMODS OLD

    // In-Game Injected Mod Menu Aesthetic Styling
    style.WindowRounding    = 12.0f;
    style.ChildRounding     = 8.0f;
    style.FrameRounding     = 8.0f;
    style.PopupRounding     = 10.0f;
    style.ScrollbarRounding = 8.0f;
    style.GrabRounding      = 8.0f;

    style.WindowBorderSize  = 1.2f;
    style.FrameBorderSize   = 0.8f;
    style.PopupBorderSize   = 1.2f;

    style.WindowPadding     = ImVec2(12.0f, 10.0f);
    style.FramePadding      = ImVec2(10.0f, 6.0f);
    style.ItemSpacing       = ImVec2(8.0f, 6.0f);
    style.ItemInnerSpacing  = ImVec2(6.0f, 5.0f);
    style.TouchExtraPadding = ImVec2(3.0f, 3.0f);

    style.ScrollbarSize     = 18.0f;
    style.GrabMinSize       = 20.0f;

    s_LastAppliedDpi = dpi;
}

bool NativeOverlayController::IsModalPopupActive() {
    if (!LicensePopup::IsVerified() && LicensePopup::IsOpen()) return true;
    if (DriverAlertPopup::IsOpen() && !DriverAlertPopup::IsPreviewMode() &&
        !s_DriverAlertDismissed.load(std::memory_order_relaxed)) {
        return true;
    }
    return false;
}

void NativeOverlayController::QueueTouchEvent(int action, float x, float y) {
    std::lock_guard<std::mutex> lock(g_TouchMutex);
    /* Bounded queue: a stuck consumer must never grow the heap forever. */
    if (g_TouchQueue.size() > 64) {
        g_TouchQueue.erase(g_TouchQueue.begin());
    }
    g_TouchQueue.push_back({action, x, y});
}

void NativeOverlayController::DrainTouchEvents() {
    std::vector<TouchEvent> pending;
    {
        std::lock_guard<std::mutex> lock(g_TouchMutex);
        pending.swap(g_TouchQueue);
    }
    if (pending.empty()) return;

    ImGuiIO& io = ImGui::GetIO();
    for (const TouchEvent& ev : pending) {
        io.MousePos = ImVec2(ev.x, ev.y);
        // ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2, ACTION_CANCEL = 3
        if (ev.action == 0 || ev.action == 2) {
            io.MouseDown[0] = true;
        } else if (ev.action == 1 || ev.action == 3) {
            io.MouseDown[0] = false;
        }
    }
}

bool NativeOverlayController::HandleTouchEvent(int action, float x, float y) {
    /* The Android input thread only queues the event; ImGuiIO is written by
     * the render thread inside DrainTouchEvents() so that it never races with
     * ImGui::NewFrame().
     *
     * It must NOT read ImGui::GetIO() here either: the render thread writes
     * ImGuiIO while it builds the frame, so reading it from the input thread
     * was a data race on the whole IO structure. The render thread publishes
     * the value instead (see PublishWantCaptureMouse). */
    QueueTouchEvent(action, x, y);
    return s_WantCaptureMouse.load(std::memory_order_relaxed);
}

void NativeOverlayController::PublishWantCaptureMouse() {
    /* Called by the render thread right after NewFrame(), when WantCaptureMouse
     * is valid for the frame that was just built. */
    if (!g_ImGuiInitializedForOverlay.load(std::memory_order_relaxed)) return;
    s_WantCaptureMouse.store(ImGui::GetIO().WantCaptureMouse, std::memory_order_relaxed);
}

void NativeOverlayController::SetImGuiReady(bool ready) {
    g_ImGuiInitializedForOverlay.store(ready, std::memory_order_relaxed);
    if (!ready) s_WantCaptureMouse.store(false, std::memory_order_relaxed);
}
