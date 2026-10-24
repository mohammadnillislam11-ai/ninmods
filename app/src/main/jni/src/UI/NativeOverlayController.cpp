#include "NativeOverlayController.h"
#include "MenuRouter.h"
#include "Popups/LicensePopup.h"
#include "Popups/DriverAlertPopup.h"
#include <imgui.h>
#include <cmath>

bool NativeOverlayController::s_Initialized = false;
bool NativeOverlayController::s_MenuOpen = false;
bool NativeOverlayController::s_DriverAlertDismissed = false;
int NativeOverlayController::s_ScreenWidth = 1920;
int NativeOverlayController::s_ScreenHeight = 1080;
float NativeOverlayController::s_LogoX = 80.0f;
float NativeOverlayController::s_LogoY = 160.0f;
std::string NativeOverlayController::s_ActiveBrandId = "nin";
std::string NativeOverlayController::s_DeviceHwid = "";
GLuint NativeOverlayController::s_LogoTexture = 0;
std::function<void()> NativeOverlayController::s_OpenLoaderCallback = nullptr;

void NativeOverlayController::Initialize(int screenWidth, int screenHeight, const std::string& brandId, const std::string& hwid, bool licenseActive) {
    s_ScreenWidth = screenWidth > 0 ? screenWidth : 1920;
    s_ScreenHeight = screenHeight > 0 ? screenHeight : 1080;
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

void NativeOverlayController::RenderFrame(bool isKernelActive) {
    if (!s_Initialized) return;

    IBrandMenu* activeMenu = MenuRouter::GetActiveMenu();
    if (!activeMenu) return;

    ImVec4 themeColor = activeMenu->GetPrimaryColor();

    // ──────────────────────────────────────────────────────────────────
    // [STATE 0] License Key Verification Modal
    // ──────────────────────────────────────────────────────────────────
    if (!LicensePopup::IsVerified()) {
        LicensePopup::Render(activeMenu->GetId(), themeColor, []() {
            // Callback when license verified successfully
        });
        return;
    }

    // ──────────────────────────────────────────────────────────────────
    // [STATE 1] Kernel Driver Status Check Modal
    // ──────────────────────────────────────────────────────────────────
    if (!isKernelActive && !DriverAlertPopup::IsPreviewMode() && !s_DriverAlertDismissed) {
        DriverAlertPopup::Open(s_OpenLoaderCallback);
        DriverAlertPopup::Render([]() {
            s_DriverAlertDismissed = true;
            s_MenuOpen = true; // Proceed to preview mode menu
        });
        return;
    }

    // ──────────────────────────────────────────────────────────────────
    // [STATE 2] Draggable Floating Logo Button (When menu is collapsed)
    // ──────────────────────────────────────────────────────────────────
    if (!s_MenuOpen) {
        ImGui::SetNextWindowPos(ImVec2(s_LogoX, s_LogoY), ImGuiCond_FirstUseEver);
        ImGui::SetNextWindowSize(ImVec2(76, 76), ImGuiCond_Always);

        ImGuiWindowFlags logoFlags = ImGuiWindowFlags_NoTitleBar |
                                     ImGuiWindowFlags_NoResize |
                                     ImGuiWindowFlags_NoScrollbar |
                                     ImGuiWindowFlags_NoCollapse |
                                     ImGuiWindowFlags_NoBackground;

        if (ImGui::Begin("##FloatingLogoIcon", NULL, logoFlags)) {
            ImVec2 p = ImGui::GetWindowPos();
            s_LogoX = p.x;
            s_LogoY = p.y;

            ImDrawList* drawList = ImGui::GetWindowDrawList();
            ImVec2 center(p.x + 38.0f, p.y + 38.0f);
            float radius = 32.0f;

            // Outer Pulsing Neon Glow Ring
            static float pulse = 0.0f;
            pulse += 0.04f;
            float glowAlpha = 0.35f + 0.25f * sinf(pulse);
            ImU32 glowCol = ImColor(themeColor.x, themeColor.y, themeColor.z, glowAlpha);
            drawList->AddCircle(center, radius + 3.0f, glowCol, 48, 2.5f);

            // Inner Translucent Disc
            ImU32 discBg = ImColor(0.08f, 0.06f, 0.12f, 0.90f);
            drawList->AddCircleFilled(center, radius, discBg, 48);

            // Border Ring
            ImU32 ringCol = ImColor(themeColor.x, themeColor.y, themeColor.z, 0.95f);
            drawList->AddCircle(center, radius, ringCol, 48, 2.0f);

            // Brand Text or Texture inside Logo
            if (s_LogoTexture > 0) {
                ImVec2 imgMin(center.x - 22.0f, center.y - 22.0f);
                ImVec2 imgMax(center.x + 22.0f, center.y + 22.0f);
                drawList->AddImage((ImTextureID)(intptr_t)s_LogoTexture, imgMin, imgMax);
            } else {
                // High-visibility Brand Initial
                const char* id = activeMenu->GetId();
                char initial[4] = "NIN";
                if (id) {
                    if (strcmp(id, "bull") == 0) strncpy(initial, "BULL", sizeof(initial)-1);
                    else if (strcmp(id, "cross") == 0) strncpy(initial, "CRS", sizeof(initial)-1);
                    else if (strcmp(id, "delta") == 0) strncpy(initial, "DLT", sizeof(initial)-1);
                }
                ImVec2 txtSz = ImGui::CalcTextSize(initial);
                drawList->AddText(ImVec2(center.x - txtSz.x * 0.5f, center.y - txtSz.y * 0.5f), ringCol, initial);
            }

            // Click detector on floating logo to expand full menu
            ImGui::InvisibleButton("##LogoClickTarget", ImVec2(76, 76));
            if (ImGui::IsItemClicked()) {
                s_MenuOpen = true;
            }
        }
        ImGui::End();
    }

    // ──────────────────────────────────────────────────────────────────
    // [STATE 3] Full Modular Tactical Brand Menu
    // ──────────────────────────────────────────────────────────────────
    if (s_MenuOpen) {
        activeMenu->RenderMenu(s_MenuOpen, isKernelActive);
    }
}

bool NativeOverlayController::HandleTouchEvent(int action, float x, float y) {
    ImGuiIO& io = ImGui::GetIO();
    io.MousePos = ImVec2(x, y);

    // ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2
    if (action == 0) {
        io.MouseDown[0] = true;
    } else if (action == 1) {
        io.MouseDown[0] = false;
    }

    // Return true if touch falls on any ImGui active window
    return io.WantCaptureMouse;
}
