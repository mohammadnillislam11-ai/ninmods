#include "NinMenu.h"
#include "../NativeOverlayController.h"
#include "../Toggle.h"
#include "../../Icon/TextureLoader.h"
#include "../../BooleanClient.h"
#if __has_include(<imgui.h>)
#include <imgui.h>
#else
#include "../../imgui/imgui.h"
#endif

void NinMenu::ApplyTheme() {
    ImGuiStyle& style = ImGui::GetStyle();
    style.WindowRounding    = 12.0f;
    style.ChildRounding     = 8.0f;
    style.FrameRounding     = 10.0f;
    style.GrabRounding      = 10.0f;
    style.GrabMinSize       = 20.0f;
    style.WindowBorderSize  = 1.2f;

    ImVec4* colors = style.Colors;
    // NINMODS Cyber Dark Translucent Palette
    colors[ImGuiCol_WindowBg]             = ImVec4(0.02f, 0.02f, 0.03f, 0.88f);
    colors[ImGuiCol_Border]               = ImVec4(0.70f, 0.75f, 0.85f, 0.32f);
    colors[ImGuiCol_TitleBg]              = ImVec4(0.07f, 0.07f, 0.09f, 0.65f);
    colors[ImGuiCol_TitleBgActive]        = ImVec4(0.10f, 0.10f, 0.13f, 0.75f);
    colors[ImGuiCol_FrameBg]              = ImVec4(0.12f, 0.18f, 0.28f, 0.65f);
    colors[ImGuiCol_FrameBgHovered]       = ImVec4(0.16f, 0.24f, 0.36f, 0.80f);
    colors[ImGuiCol_FrameBgActive]        = ImVec4(0.20f, 0.30f, 0.45f, 0.90f);
    colors[ImGuiCol_SliderGrab]           = ImVec4(0.95f, 0.98f, 1.00f, 1.00f);
    colors[ImGuiCol_SliderGrabActive]     = ImVec4(0.25f, 0.65f, 1.00f, 1.00f);
    colors[ImGuiCol_Button]               = ImVec4(0.18f, 0.26f, 0.38f, 0.50f);
    colors[ImGuiCol_ButtonHovered]        = ImVec4(0.25f, 0.35f, 0.50f, 0.70f);
    colors[ImGuiCol_ButtonActive]         = ImVec4(0.30f, 0.42f, 0.60f, 0.85f);
}

void NinMenu::RenderMenu(bool& isOpen, bool isKernelActive) {
    if (!isOpen) return;

    ApplyTheme();

    float scrW = (float)NativeOverlayController::s_ScreenWidth;
    float scrH = (float)NativeOverlayController::s_ScreenHeight;

    ImGui::SetNextWindowPos(ImVec2(0, 0));
    ImGui::SetNextWindowSize(ImVec2(scrW, scrH));

    ImGuiWindowFlags winFlags = ImGuiWindowFlags_NoTitleBar |
                                ImGuiWindowFlags_NoResize |
                                ImGuiWindowFlags_NoMove |
                                ImGuiWindowFlags_NoScrollbar |
                                ImGuiWindowFlags_NoCollapse;

    if (ImGui::Begin("NINMODS_TACTICAL_MENU", &isOpen, winFlags)) {
        ImVec2 winPos = ImGui::GetWindowPos();
        ImVec2 winSize = ImGui::GetWindowSize();
        ImVec2 mousePos = ImGui::GetIO().MousePos;
        ImDrawList* drawList = ImGui::GetWindowDrawList();

        float dpi = NativeOverlayController::s_DpiScale > 0.0f ? NativeOverlayController::s_DpiScale : 2.4f;
        float dpiFactor = dpi / 2.4f;
        float headerH = 50.0f * dpiFactor;
        float btnSz = 30.0f * dpiFactor;
        float btnMargin = 10.0f * dpiFactor;

        ImVec2 closeBtnPos(winPos.x + winSize.x - btnSz - btnMargin, winPos.y + (headerH - btnSz) * 0.5f);
        bool overClose = (mousePos.x >= closeBtnPos.x && mousePos.x <= closeBtnPos.x + btnSz &&
                          mousePos.y >= closeBtnPos.y && mousePos.y <= closeBtnPos.y + btnSz);

        if (ImGui::IsMouseClicked(0) && overClose) {
            isOpen = false;
        }

        // 1. Pinned Header Bar Background & Border
        drawList->AddRectFilled(winPos, ImVec2(winPos.x + winSize.x, winPos.y + headerH), IM_COL32(14, 18, 26, 245), 10.0f, ImDrawFlags_RoundCornersTop);
        drawList->AddLine(ImVec2(winPos.x, winPos.y + headerH), ImVec2(winPos.x + winSize.x, winPos.y + headerH), IM_COL32(60, 80, 115, 140), 1.0f);

        // 2. Eagle Logo Header Banner (NINMODS original signature header)
        if (g_EagleLogoTexture == 0) LoadEagleLogoTexture();
        if (g_EagleLogoTexture != 0) {
            float aspect = (g_EagleLogoHeight > 0) ? ((float)g_EagleLogoWidth / (float)g_EagleLogoHeight) : 2.5f;
            float hLogoH = (headerH - 4.0f) * 1.6f;
            float hLogoW = hLogoH * aspect;
            float hLogoX = winPos.x + (winSize.x - hLogoW) * 0.5f;
            float hLogoY = winPos.y + (headerH - hLogoH) * 0.5f;
            drawList->PushClipRectFullScreen();
            drawList->AddImage((ImTextureID)(uintptr_t)g_EagleLogoTexture, ImVec2(hLogoX, hLogoY), ImVec2(hLogoX + hLogoW, hLogoY + hLogoH), ImVec2(0, 0), ImVec2(1, 1), IM_COL32(255, 255, 255, 255));
            drawList->PopClipRect();
        } else {
            drawList->AddText(ImVec2(winPos.x + 16.0f * dpiFactor, winPos.y + (headerH - ImGui::GetFontSize()) * 0.5f), IM_COL32(255, 255, 255, 240), "[NINMODS]");
        }

        // 3. Pinned Close [X] Button on Header
        drawList->AddRectFilled(closeBtnPos, ImVec2(closeBtnPos.x + btnSz, closeBtnPos.y + btnSz), overClose ? IM_COL32(220, 50, 60, 220) : IM_COL32(50, 65, 88, 160), 6.0f * dpiFactor);
        float pad = 7.0f * dpiFactor;
        drawList->AddLine(ImVec2(closeBtnPos.x + pad, closeBtnPos.y + pad), ImVec2(closeBtnPos.x + btnSz - pad, closeBtnPos.y + btnSz - pad), IM_COL32(255, 255, 255, 250), 2.2f * dpiFactor);
        drawList->AddLine(ImVec2(closeBtnPos.x + btnSz - pad, closeBtnPos.y + pad), ImVec2(closeBtnPos.x + pad, closeBtnPos.y + btnSz - pad), IM_COL32(255, 255, 255, 250), 2.2f * dpiFactor);

        // 4. Real-Time Driver Status Badge
        ImGui::SetCursorPosY(headerH + 6.0f);
        if (isKernelActive) {
            ImGui::TextColored(ImVec4(0.0f, 1.0f, 0.45f, 1.0f), " [ONLINE] KERNEL (/dev/BYNb)");
        } else {
            ImGui::TextColored(ImVec4(1.0f, 0.35f, 0.35f, 1.0f), " [OFFLINE] KERNEL [PREVIEW MODE]");
        }
        ImGui::Separator();
        ImGui::Spacing();

        // 5. Categorized Tactical Tabs (Compact, Clean & Organized)
        if (ImGui::BeginTabBar("NinTabBar", ImGuiTabBarFlags_None)) {
            // --- TAB 1: VISUALS (ESP) ---
            if (ImGui::BeginTabItem("VISUALS")) {
                if (ImGui::BeginChild("VisChild", ImVec2(0, 0), false, ImGuiWindowFlags_AlwaysVerticalScrollbar)) {
                    ImGui::Spacing();
                    Toggle("ESP MASTER SWITCH", &Actived.activar);
                    Toggle("ESP BOX", &pEspPlayer.espCaixa);
                    Toggle("ESP LINE", &pEspPlayer.espLinha);
                    Toggle("ESP SKELETON (BONES)", &pEspPlayer.espSkeleton);
                    Toggle("ESP DISTANCE IN METERS", &pEspPlayer.espDistance);
                    Toggle("ESP HEALTH BAR", &pEspPlayer.espHealth);
                    Toggle("ESP PLAYER NAME", &pEspPlayer.espName);
                    Toggle("360 THREAT RADAR", &pEspPlayer.espIden360);
                    Toggle("LIVE THREAT COUNTER HUD", &pEspPlayer.espDrawCount);
                    ImGui::Spacing();
                }
                ImGui::EndChild();
                ImGui::EndTabItem();
            }

            // --- TAB 2: AIMBOT ---
            if (ImGui::BeginTabItem("AIMBOT")) {
                if (ImGui::BeginChild("AimChild", ImVec2(0, 0), false, ImGuiWindowFlags_AlwaysVerticalScrollbar)) {
                    ImGui::Spacing();
                    Toggle("AIMBOT AUTO LOCK", &pPlayer.aimbot);
                    Toggle("AIMBOT STICKY TARGET", &pPlayer.aimbotlock);
                    Toggle("SILENT AIM (MAGIC BULLET)", &pPlayer.silen4a);
                    Toggle("DRAW FOV CIRCLE", &showFOVCircle);
                    Toggle("AIM MAGNET V2", &pPlayer.aimMagnet);
                    Toggle("IGNORE KNOCKED PLAYERS", &pPlayer.ignoreKnocked);
                    Toggle("IGNORE BOTS / AI", &pPlayer.ignoreBots);
                    ImGui::Spacing();
                    ImGui::Text("AIM FOV ANGLE: %.0f deg", pPlayer.AimFov);
                    MobileSliderFloat("##AimFovSlider", &pPlayer.AimFov, 10.0f, 180.0f);
                    ImGui::Spacing();
                    ImGui::Text("MAX TARGET DISTANCE: %.0f M", pPlayer.AimDistance);
                    MobileSliderFloat("##AimDistSlider", &pPlayer.AimDistance, 10.0f, 350.0f);
                    ImGui::Spacing();
                }
                ImGui::EndChild();
                ImGui::EndTabItem();
            }

            // --- TAB 3: COMBAT ---
            if (ImGui::BeginTabItem("COMBAT")) {
                if (ImGui::BeginChild("CombatChild", ImVec2(0, 0), false, ImGuiWindowFlags_AlwaysVerticalScrollbar)) {
                    ImGui::Spacing();
                    Toggle("NO RECOIL (100% STABLE)", &pPlayer.NoRecoil);
                    Toggle("SPEED MOVEMENT (2X)", &pPlayer.SpeedHackV2);
                    Toggle("ANTICHEAT REALTIME BYPASS", &Actived.Bypass);
                    Toggle("UNDERGROUND CAMERA", &pPlayer.undercam);
                    ImGui::Spacing();
                }
                ImGui::EndChild();
                ImGui::EndTabItem();
            }

            // --- TAB 4: SYSTEM ---
            if (ImGui::BeginTabItem("SYSTEM")) {
                if (ImGui::BeginChild("SysChild", ImVec2(0, 0), false, ImGuiWindowFlags_AlwaysVerticalScrollbar)) {
                    ImGui::Spacing();
                    ImGui::TextColored(ImVec4(0.35f, 0.75f, 1.0f, 1.0f), "=== SYSTEM & CONTROL ===");
                    ImGui::Spacing();
                    ImGui::TextDisabled("Status: Authentic Kernel Driver Mode");
                    ImGui::Spacing();
                    if (ImGui::Button("COLLAPSE TO FLOATING LOGO", ImVec2(-1, 38))) {
                        isOpen = false;
                    }
                    ImGui::Spacing();
                }
                ImGui::EndChild();
                ImGui::EndTabItem();
            }

            ImGui::EndTabBar();
        }
    }
    ImGui::End();
}
