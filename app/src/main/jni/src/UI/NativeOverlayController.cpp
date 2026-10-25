#include "NativeOverlayController.h"
#include "MenuRouter.h"
#include "Popups/LicensePopup.h"
#include "Popups/DriverAlertPopup.h"
#include "../Icon/TextureLoader.h"
#include <imgui.h>
#include <cmath>
#include <algorithm>

bool NativeOverlayController::s_Initialized = false;
bool NativeOverlayController::s_MenuOpen = false;
bool NativeOverlayController::s_DriverAlertDismissed = false;
int NativeOverlayController::s_ScreenWidth = 1920;
int NativeOverlayController::s_ScreenHeight = 1080;
float NativeOverlayController::s_DpiScale = 2.4f;
float NativeOverlayController::s_LogoX = 80.0f;
float NativeOverlayController::s_LogoY = 160.0f;
std::string NativeOverlayController::s_ActiveBrandId = "nin";
std::string NativeOverlayController::s_DeviceHwid = "";
GLuint NativeOverlayController::s_LogoTexture = 0;
std::function<void()> NativeOverlayController::s_OpenLoaderCallback = nullptr;

void NativeOverlayController::Initialize(int screenWidth, int screenHeight, float dpiScale, const std::string& brandId, const std::string& hwid, bool licenseActive) {
    s_ScreenWidth = screenWidth > 0 ? screenWidth : 1920;
    s_ScreenHeight = screenHeight > 0 ? screenHeight : 1080;
    SetDpiScale(dpiScale);
    s_ActiveBrandId = !brandId.empty() ? brandId : "nin";
    s_DeviceHwid = hwid;

    MenuRouter::Initialize();
    MenuRouter::SelectBrand(s_ActiveBrandId);

    if (licenseActive) {
        LicensePopup::SetVerified(true);
    } else {
        LicensePopup::SetVerified(false);
        LicensePopup::Open(hwid);
    }

    s_DriverAlertDismissed = false;
    s_MenuOpen = false;
    s_Initialized = true;
}

void NativeOverlayController::SetScreenSize(int width, int height) {
    if (width > 0) s_ScreenWidth = width;
    if (height > 0) s_ScreenHeight = height;
}

void NativeOverlayController::SetDpiScale(float dpi) {
    if (dpi > 0.5f) {
        s_DpiScale = dpi;
    } else {
        // Fallback calculation from resolution
        s_DpiScale = (float)std::min(s_ScreenWidth, s_ScreenHeight) / 380.0f;
    }
    if (s_DpiScale < 2.0f) s_DpiScale = 2.0f;
    if (s_DpiScale > 4.2f) s_DpiScale = 4.2f;
}

void NativeOverlayController::SetActiveBrand(const std::string& brandId) {
    s_ActiveBrandId = brandId;
    MenuRouter::SelectBrand(brandId);
}

void NativeOverlayController::SetOpenLoaderCallback(std::function<void()> cb) {
    s_OpenLoaderCallback = cb;
    DriverAlertPopup::Open(cb);
}

void NativeOverlayController::SetLogoTexture(GLuint tex) {
    s_LogoTexture = tex;
}

void NativeOverlayController::ApplyDpiTheme() {
    static float s_LastAppliedDpi = 0.0f;
    if (fabsf(s_LastAppliedDpi - s_DpiScale) < 0.05f) return;

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
    style.TabRounding       = 8.0f;

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

    s_LastAppliedDpi = s_DpiScale;
}

void NativeOverlayController::RenderFrame(bool isKernelActive) {
    if (!s_Initialized) return;

    ApplyDpiTheme();

    IBrandMenu* activeMenu = MenuRouter::GetActiveMenu();
    if (!activeMenu) return;

    ImVec4 themeColor = activeMenu->GetPrimaryColor();

    // ------------------------------------------------------------------
    // [STATE 0] License Key Verification Modal
    // ------------------------------------------------------------------
    if (!LicensePopup::IsVerified()) {
        LicensePopup::Render(activeMenu->GetId(), themeColor, []() {
            // Callback when license verified successfully
        });
        return;
    }

    // ------------------------------------------------------------------
    // [STATE 1] Kernel Driver Status Check Modal
    // ------------------------------------------------------------------
    if (!isKernelActive && !DriverAlertPopup::IsPreviewMode() && !s_DriverAlertDismissed) {
        DriverAlertPopup::Open(s_OpenLoaderCallback);
        DriverAlertPopup::Render([]() {
            s_DriverAlertDismissed = true;
            s_MenuOpen = true; // Proceed to preview mode menu
        });
        return;
    }

    // ------------------------------------------------------------------
    // [STATE 2] Floating Logo Mode (In compact 76dp overlay window)
    // ------------------------------------------------------------------
    if (!s_MenuOpen) {
        ImGui::SetNextWindowPos(ImVec2(0, 0));
        ImGui::SetNextWindowSize(ImVec2((float)s_ScreenWidth, (float)s_ScreenHeight));

        ImGuiWindowFlags logoFlags = ImGuiWindowFlags_NoTitleBar |
                                     ImGuiWindowFlags_NoResize |
                                     ImGuiWindowFlags_NoScrollbar |
                                     ImGuiWindowFlags_NoCollapse |
                                     ImGuiWindowFlags_NoBackground |
                                     ImGuiWindowFlags_NoMove;

        if (ImGui::Begin("##FloatingLogoIcon", NULL, logoFlags)) {
            ImDrawList* drawList = ImGui::GetWindowDrawList();
            ImVec2 center((float)s_ScreenWidth * 0.5f, (float)s_ScreenHeight * 0.5f);
            float radius = (std::min((float)s_ScreenWidth, (float)s_ScreenHeight) * 0.5f) - 6.0f;
            if (radius < 24.0f) radius = 24.0f;

            // Outer Pulsing Neon Glow Ring
            static float pulse = 0.0f;
            pulse += 0.045f;
            float glowAlpha = 0.35f + 0.30f * sinf(pulse);
            ImU32 glowCol = ImColor(themeColor.x, themeColor.y, themeColor.z, glowAlpha);
            drawList->AddCircle(center, radius + 4.0f, glowCol, 48, 3.5f);

            // Inner Dark Disc Background
            ImU32 discBg = ImColor(0.08f, 0.06f, 0.12f, 0.94f);
            drawList->AddCircleFilled(center, radius, discBg, 48);

            // Sharp Metallic Border Ring
            ImU32 ringCol = ImColor(themeColor.x, themeColor.y, themeColor.z, 0.95f);
            drawList->AddCircle(center, radius, ringCol, 48, 2.5f);

            // Authentic NINMODS Logo Texture inside Floating Icon
            if (g_NinLogoTexture == 0) {
                LoadNinLogoTexture();
            }

            if (g_NinLogoTexture != 0) {
                float iconRadius = radius * 0.95f;
                float iconH = iconRadius * 2.0f;
                float iconW = (g_NinLogoHeight > 0) ? (iconH * ((float)g_NinLogoWidth / (float)g_NinLogoHeight)) : iconH;
                ImVec2 pMin = ImVec2(center.x - iconW * 0.5f, center.y - iconH * 0.5f);
                ImVec2 pMax = ImVec2(center.x + iconW * 0.5f, center.y + iconH * 0.5f);
                drawList->AddImage((ImTextureID)(intptr_t)g_NinLogoTexture, pMin, pMax, ImVec2(0, 0), ImVec2(1, 1), IM_COL32(255, 255, 255, 255));
            } else if (s_LogoTexture > 0) {
                float iconHalf = radius * 0.65f;
                ImVec2 imgMin(center.x - iconHalf, center.y - iconHalf);
                ImVec2 imgMax(center.x + iconHalf, center.y + iconHalf);
                drawList->AddImage((ImTextureID)(intptr_t)s_LogoTexture, imgMin, imgMax);
            } else {
                // High-visibility Brand Initial
                const char* id = activeMenu->GetId();
                const char* initial = "NIN";
                if (id) {
                    if (strcmp(id, "bull") == 0) initial = "BULL";
                    else if (strcmp(id, "cross") == 0) initial = "CRS";
                    else if (strcmp(id, "delta") == 0) initial = "DLT";
                }
                ImVec2 txtSz = ImGui::CalcTextSize(initial);
                drawList->AddText(ImVec2(center.x - txtSz.x * 0.5f, center.y - txtSz.y * 0.5f), ringCol, initial);
            }

            // Click detector on floating logo to expand full menu
            ImGui::InvisibleButton("##LogoClickTarget", ImVec2((float)s_ScreenWidth, (float)s_ScreenHeight));
            if (ImGui::IsItemClicked()) {
                s_MenuOpen = true;
            }
        }
        ImGui::End();
    }

    // ------------------------------------------------------------------
    // [STATE 3] Full Modular Tactical Brand Menu (In expanded window)
    // ------------------------------------------------------------------
    if (s_MenuOpen) {
        activeMenu->RenderMenu(s_MenuOpen, isKernelActive);
    }
}

bool NativeOverlayController::IsModalPopupActive() {
    if (!LicensePopup::IsVerified() && LicensePopup::IsOpen()) return true;
    if (DriverAlertPopup::IsOpen() && !DriverAlertPopup::IsPreviewMode() && !s_DriverAlertDismissed) return true;
    return false;
}

bool NativeOverlayController::HandleTouchEvent(int action, float x, float y) {
    ImGuiIO& io = ImGui::GetIO();
    io.MousePos = ImVec2(x, y);

    // ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2, ACTION_CANCEL = 3
    if (action == 0 || action == 2) {
        io.MouseDown[0] = true;
    } else if (action == 1 || action == 3) {
        io.MouseDown[0] = false;
    }

    return io.WantCaptureMouse;
}
