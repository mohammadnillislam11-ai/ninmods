#include "NinMenu.h"
#include "../../BooleanClient.h"
#include <imgui.h>

void NinMenu::ApplyTheme() {
    ImGuiStyle& style = ImGui::GetStyle();
    ImVec4* colors = style.Colors;

    style.WindowRounding = 12.0f;
    style.ChildRounding = 8.0f;
    style.FrameRounding = 6.0f;
    style.PopupRounding = 10.0f;
    style.ScrollbarRounding = 6.0f;
    style.GrabRounding = 6.0f;
    style.TabRounding = 6.0f;
    style.WindowBorderSize = 1.5f;

    // Nin Cyber-Magenta Palette
    colors[ImGuiCol_WindowBg]             = GetBackgroundColor();
    colors[ImGuiCol_Border]               = ImVec4(0.91f, 0.12f, 0.39f, 0.65f); // Neon Magenta Border
    colors[ImGuiCol_TitleBg]              = ImVec4(0.12f, 0.05f, 0.14f, 1.0f);
    colors[ImGuiCol_TitleBgActive]        = ImVec4(0.24f, 0.08f, 0.28f, 1.0f);
    colors[ImGuiCol_Header]               = ImVec4(0.91f, 0.12f, 0.39f, 0.45f);
    colors[ImGuiCol_HeaderHovered]        = ImVec4(0.91f, 0.12f, 0.39f, 0.75f);
    colors[ImGuiCol_HeaderActive]         = ImVec4(0.91f, 0.12f, 0.39f, 0.95f);
    colors[ImGuiCol_Button]               = ImVec4(0.91f, 0.12f, 0.39f, 0.55f);
    colors[ImGuiCol_ButtonHovered]        = ImVec4(0.91f, 0.12f, 0.39f, 0.85f);
    colors[ImGuiCol_ButtonActive]         = ImVec4(0.91f, 0.12f, 0.39f, 1.0f);
    colors[ImGuiCol_FrameBg]              = ImVec4(0.18f, 0.10f, 0.22f, 0.85f);
    colors[ImGuiCol_FrameBgHovered]       = ImVec4(0.28f, 0.15f, 0.35f, 0.90f);
    colors[ImGuiCol_FrameBgActive]        = ImVec4(0.38f, 0.20f, 0.48f, 1.0f);
    colors[ImGuiCol_CheckMark]            = ImVec4(0.0f, 1.0f, 0.65f, 1.0f);    // Bright Mint/Cyan check
    colors[ImGuiCol_SliderGrab]           = ImVec4(0.91f, 0.12f, 0.39f, 0.90f);
    colors[ImGuiCol_SliderGrabActive]     = ImVec4(1.0f, 0.25f, 0.50f, 1.0f);
    colors[ImGuiCol_Tab]                  = ImVec4(0.20f, 0.08f, 0.24f, 0.85f);
    colors[ImGuiCol_TabHovered]           = ImVec4(0.91f, 0.12f, 0.39f, 0.70f);
    colors[ImGuiCol_TabActive]            = ImVec4(0.91f, 0.12f, 0.39f, 0.95f);
}

void NinMenu::RenderMenu(bool& isOpen, bool isKernelActive) {
    if (!isOpen) return;

    ApplyTheme();
    ImGui::SetNextWindowSize(ImVec2(340, 460), ImGuiCond_FirstUseEver);

    if (ImGui::Begin("NIN TACTICAL ##MainWindow", &isOpen, ImGuiWindowFlags_NoCollapse)) {
        // --- 1. Header Banner ---
        ImGui::TextColored(GetPrimaryColor(), "NIN TACTICAL");
        ImGui::SameLine();
        ImGui::TextDisabled("| %s", GetSubtitle());

        // Live Real-Time Driver Badge
        if (isKernelActive) {
            ImGui::TextColored(ImVec4(0.0f, 1.0f, 0.45f, 1.0f), "● KERNEL ONLINE (/dev/BYNb)");
        } else {
            ImGui::TextColored(ImVec4(1.0f, 0.3f, 0.3f, 1.0f), "● KERNEL OFFLINE [PREVIEW MODE]");
        }
        ImGui::Separator();

        // --- 2. Modular Tab System ---
        if (ImGui::BeginTabBar("NinTabBar", ImGuiTabBarFlags_None)) {
            // [TAB 1: ESP VISUALS]
            if (ImGui::BeginTabItem("ESP")) {
                ImGui::Spacing();
                ImGui::Checkbox("ESP Box", &pEspPlayer.espCaixa);
                ImGui::Checkbox("ESP Line", &pEspPlayer.espLinha);
                ImGui::Checkbox("ESP Distance", &pEspPlayer.espDistance);
                ImGui::Checkbox("ESP Health", &pEspPlayer.espHealth);
                ImGui::Checkbox("ESP Name", &pEspPlayer.espName);
                ImGui::Checkbox("ESP Skeleton", &pEspPlayer.espSkeleton);
                ImGui::Checkbox("360 Radar Alert", &pEspPlayer.espIden360);

                ImGui::Spacing();
                ImGui::Separator();
                ImGui::SliderFloat("Line Thickness", &pEspPlayer.thickness, 1.0f, 5.0f, "%.1f px");
                ImGui::SliderFloat("Text Size", &pEspPlayer.textSize, 10.0f, 25.0f, "%.0f sp");

                const char* boxTypes[] = { "Stroke Box", "Filled Box", "Corner Box", "Rounded Box" };
                int currentBox = (int)pEspPlayer.boxType;
                if (ImGui::Combo("Box Style", &currentBox, boxTypes, IM_ARRAYSIZE(boxTypes))) {
                    pEspPlayer.boxType = (BoxType)currentBox;
                }

                const char* lineTypes[] = { "Top of Screen", "Center Screen", "Bottom of Screen" };
                int currentLine = (int)pEspPlayer.lineType;
                if (ImGui::Combo("Line Origin", &currentLine, lineTypes, IM_ARRAYSIZE(lineTypes))) {
                    pEspPlayer.lineType = (LineType)currentLine;
                }

                ImGui::EndTabItem();
            }

            // [TAB 2: AIMBOT COMBAT]
            if (ImGui::BeginTabItem("AIMBOT")) {
                ImGui::Spacing();
                ImGui::Checkbox("Auto Aimbot", &pPlayer.aimbot);
                ImGui::Checkbox("Aimbot Lock", &pPlayer.aimbotlock);
                ImGui::Checkbox("Silent Aim", &pPlayer.silen4a);
                ImGui::Checkbox("Aim Magnet", &pPlayer.aimMagnet);
                ImGui::Checkbox("Ignore Knocked", &pPlayer.ignoreKnocked);
                ImGui::Checkbox("Ignore Bots", &pPlayer.ignoreBots);

                ImGui::Spacing();
                ImGui::Separator();
                ImGui::SliderFloat("Aim FOV", &pPlayer.AimFov, 10.0f, 180.0f, "%.0f deg");
                ImGui::SliderFloat("Max Distance", &pPlayer.AimDistance, 10.0f, 300.0f, "%.0f m");
                ImGui::SliderInt("Headshot Rate", &pPlayer.headshotRate, 0, 100, "%d %%");

                ImGui::EndTabItem();
            }

            // [TAB 3: MEMORY MISC]
            if (ImGui::BeginTabItem("MEMORY")) {
                ImGui::Spacing();
                ImGui::Checkbox("No Recoil", &pPlayer.NoRecoil);
                ImGui::Checkbox("Speed Hack", &pPlayer.SpeedHackV2);
                if (pPlayer.SpeedHackV2) {
                    ImGui::SliderFloat("Game Speed", &pPlayer.gameSpeed, 1.0f, 3.5f, "%.1fx");
                }
                ImGui::Checkbox("Anticheat Bypass", &Actived.Bypass);
                ImGui::EndTabItem();
            }

            // [TAB 4: SETTINGS]
            if (ImGui::BeginTabItem("SYSTEM")) {
                ImGui::Spacing();
                ImGui::Text("Interface Mode: 100%% Pure C++ Native ImGui");
                ImGui::Text("Driver Endpoint: /dev/BYNb (IOCTL Ring 0)");
                ImGui::Spacing();
                if (ImGui::Button("MINIMIZE MENU", ImVec2(-1, 38))) {
                    isOpen = false;
                }
                ImGui::EndTabItem();
            }

            ImGui::EndTabBar();
        }
    }
    ImGui::End();
}
