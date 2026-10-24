#include "NinMenu.h"
#include "../NativeOverlayController.h"
#include "../Toggle.h"
#include "../../Icon/TextureLoader.h"
#include "../../BooleanClient.h"
#include <imgui.h>

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

        float headerH = 48.0f;
        ImVec2 closeBtnPos(winPos.x + winSize.x - 38.0f, winPos.y + (headerH - 26.0f) * 0.5f);
        bool overClose = (mousePos.x >= closeBtnPos.x && mousePos.x <= closeBtnPos.x + 28.0f &&
                          mousePos.y >= closeBtnPos.y && mousePos.y <= closeBtnPos.y + 28.0f);

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
            float hLogoH = 88.0f;
            float hLogoW = hLogoH * aspect;
            float hLogoX = winPos.x + (winSize.x - hLogoW) * 0.5f;
            float hLogoY = winPos.y + headerH - hLogoH + 4.0f;
            drawList->PushClipRectFullScreen();
            drawList->AddImage((ImTextureID)(uintptr_t)g_EagleLogoTexture, ImVec2(hLogoX, hLogoY), ImVec2(hLogoX + hLogoW, hLogoY + hLogoH), ImVec2(0, 0), ImVec2(1, 1), IM_COL32(255, 255, 255, 255));
            drawList->PopClipRect();
        } else {
            drawList->AddText(ImVec2(winPos.x + 16.0f, winPos.y + 12.0f), IM_COL32(255, 255, 255, 240), "[NINMODS]");
        }

        // 3. Pinned Close [X] Button on Header
        drawList->AddRectFilled(closeBtnPos, ImVec2(closeBtnPos.x + 26.0f, closeBtnPos.y + 26.0f), overClose ? IM_COL32(220, 50, 60, 220) : IM_COL32(50, 65, 88, 160), 6.0f);
        drawList->AddLine(ImVec2(closeBtnPos.x + 7.0f, closeBtnPos.y + 7.0f), ImVec2(closeBtnPos.x + 19.0f, closeBtnPos.y + 19.0f), IM_COL32(255, 255, 255, 250), 2.2f);
        drawList->AddLine(ImVec2(closeBtnPos.x + 19.0f, closeBtnPos.y + 7.0f), ImVec2(closeBtnPos.x + 7.0f, closeBtnPos.y + 19.0f), IM_COL32(255, 255, 255, 250), 2.2f);

        // 4. Body Watermark Logo Background (Soft 180 opacity)
        if (g_NinLogoTexture == 0) LoadNinLogoTexture();
        if (g_NinLogoTexture != 0) {
            float bodyW = winSize.x, bodyH = winSize.y - headerH;
            float aspect = (g_NinLogoHeight > 0) ? ((float)g_NinLogoWidth / (float)g_NinLogoHeight) : 1.0f;
            float logoW = bodyW * 0.85f, logoH = logoW / aspect;
            float logoX = winPos.x + (bodyW - logoW) * 0.5f, logoY = winPos.y + headerH + (bodyH - logoH) * 0.5f;

            drawList->PushClipRect(ImVec2(winPos.x, winPos.y + headerH), ImVec2(winPos.x + winSize.x, winPos.y + winSize.y), true);
            drawList->AddImage((ImTextureID)(uintptr_t)g_NinLogoTexture, ImVec2(logoX, logoY), ImVec2(logoX + logoW, logoY + logoH), ImVec2(0, 0), ImVec2(1, 1), IM_COL32(255, 255, 255, 180));
            drawList->PopClipRect();
        }

        // 5. Real-Time Driver Badge
        ImGui::SetCursorPosY(headerH + 6.0f);
        if (isKernelActive) {
            ImGui::TextColored(ImVec4(0.0f, 1.0f, 0.45f, 1.0f), "  [ONLINE] KERNEL (/dev/BYNb)");
        } else {
            ImGui::TextColored(ImVec4(1.0f, 0.35f, 0.35f, 1.0f), "  [OFFLINE] KERNEL [PREVIEW MODE]");
        }
        ImGui::Separator();

        // 6. Scrollable Features Area with Custom Animated Pill Toggles & Mobile Sliders
        if (ImGui::BeginChild("NinMenuScrollArea", ImVec2(0, 0), false, ImGuiWindowFlags_AlwaysVerticalScrollbar)) {
            // [ESP FEATURES]
            ImGui::Spacing();
            ImGui::TextColored(ImVec4(0.35f, 0.75f, 1.0f, 1.0f), "=== ESP VISUALS ===");
            ImGui::Spacing();

            Toggle("ESP MASTER SWITCH", &Actived.activar);
            Toggle("ESP BOX & LINE", &pEspPlayer.espCaixa);
            Toggle("ESP SKELETON (BONES)", &pEspPlayer.espSkeleton);
            Toggle("ESP DISTANCE IN METERS", &pEspPlayer.espDistance);
            Toggle("ESP HEALTH BAR", &pEspPlayer.espHealth);
            Toggle("ESP PLAYER NAME", &pEspPlayer.espName);
            Toggle("360 THREAT RADAR", &pEspPlayer.espIden360);
            Toggle("LIVE THREAT COUNTER HUD", &pEspPlayer.espDrawCount);

            ImGui::Spacing();
            ImGui::Separator();
            ImGui::Spacing();

            // [AIMBOT FEATURES]
            ImGui::TextColored(ImVec4(0.35f, 0.75f, 1.0f, 1.0f), "=== AIMBOT & TARGETING ===");
            ImGui::Spacing();

            Toggle("AIMBOT AUTO LOCK", &pPlayer.aimbot);
            Toggle("AIMBOT STICKY TARGET", &pPlayer.aimbotlock);
            Toggle("SILENT AIM (MAGIC BULLET)", &pPlayer.silen4a);
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
            ImGui::Separator();
            ImGui::Spacing();

            // [COMBAT & EXPLOITS]
            ImGui::TextColored(ImVec4(0.35f, 0.75f, 1.0f, 1.0f), "=== COMBAT & SPEED ===");
            ImGui::Spacing();

            Toggle("NO RECOIL (100% STABLE)", &pPlayer.NoRecoil);
            Toggle("SPEED MOVEMENT (2X)", &pPlayer.SpeedHackV2);
            Toggle("ANTICHEAT REALTIME BYPASS", &Actived.Bypass);
            Toggle("UNDERGROUND CAMERA", &pPlayer.undercam);

            ImGui::Spacing();
            ImGui::Separator();
            ImGui::Spacing();

            // [SYSTEM]
            ImGui::TextColored(ImVec4(0.35f, 0.75f, 1.0f, 1.0f), "=== SYSTEM & CONTROL ===");
            ImGui::Spacing();

            if (ImGui::Button("COLLAPSE TO FLOATING LOGO", ImVec2(-1, 38))) {
                isOpen = false;
            }
            ImGui::Spacing();
        }
        ImGui::EndChild();
    }
    ImGui::End();
}
