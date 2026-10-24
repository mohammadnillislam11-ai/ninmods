#include "BullMenu.h"
#include "../../BooleanClient.h"
#include <imgui.h>

void BullMenu::ApplyTheme() {
    ImGuiStyle& style = ImGui::GetStyle();
    ImVec4* colors = style.Colors;

    style.WindowRounding = 8.0f;
    style.ChildRounding = 6.0f;
    style.FrameRounding = 4.0f;
    style.WindowBorderSize = 2.0f;

    // Bull Aggressive Gold / Crimson Palette
    colors[ImGuiCol_WindowBg]             = GetBackgroundColor();
    colors[ImGuiCol_Border]               = ImVec4(1.0f, 0.70f, 0.0f, 0.85f); // Solid Gold Border
    colors[ImGuiCol_TitleBg]              = ImVec4(0.20f, 0.08f, 0.04f, 1.0f);
    colors[ImGuiCol_TitleBgActive]        = ImVec4(0.35f, 0.12f, 0.05f, 1.0f);
    colors[ImGuiCol_Header]               = ImVec4(1.0f, 0.70f, 0.0f, 0.45f);
    colors[ImGuiCol_HeaderHovered]        = ImVec4(1.0f, 0.70f, 0.0f, 0.75f);
    colors[ImGuiCol_HeaderActive]         = ImVec4(1.0f, 0.70f, 0.0f, 0.95f);
    colors[ImGuiCol_Button]               = ImVec4(0.85f, 0.20f, 0.10f, 0.75f); // Crimson Accent Buttons
    colors[ImGuiCol_ButtonHovered]        = ImVec4(0.95f, 0.30f, 0.15f, 0.90f);
    colors[ImGuiCol_ButtonActive]         = ImVec4(1.0f, 0.40f, 0.20f, 1.0f);
    colors[ImGuiCol_FrameBg]              = ImVec4(0.20f, 0.12f, 0.08f, 0.85f);
    colors[ImGuiCol_FrameBgHovered]       = ImVec4(0.30f, 0.18f, 0.12f, 0.90f);
    colors[ImGuiCol_FrameBgActive]        = ImVec4(0.40f, 0.24f, 0.16f, 1.0f);
    colors[ImGuiCol_CheckMark]            = ImVec4(1.0f, 0.75f, 0.0f, 1.0f); // Gold Checkmark
    colors[ImGuiCol_SliderGrab]           = ImVec4(1.0f, 0.70f, 0.0f, 0.90f);
    colors[ImGuiCol_SliderGrabActive]     = ImVec4(1.0f, 0.85f, 0.20f, 1.0f);
    colors[ImGuiCol_Tab]                  = ImVec4(0.22f, 0.10f, 0.05f, 0.85f);
    colors[ImGuiCol_TabHovered]           = ImVec4(0.85f, 0.25f, 0.10f, 0.70f);
    colors[ImGuiCol_TabActive]            = ImVec4(1.0f, 0.70f, 0.0f, 0.95f);
}

void BullMenu::RenderMenu(bool& isOpen, bool isKernelActive) {
    if (!isOpen) return;

    ApplyTheme();
    ImGui::SetNextWindowSize(ImVec2(340, 460), ImGuiCond_FirstUseEver);

    if (ImGui::Begin("BULL VIP TACTICAL ##MainWindow", &isOpen, ImGuiWindowFlags_NoCollapse)) {
        ImGui::TextColored(GetPrimaryColor(), "BULL VIP TACTICAL");
        ImGui::SameLine();
        ImGui::TextDisabled("| %s", GetSubtitle());

        if (isKernelActive) {
            ImGui::TextColored(ImVec4(0.0f, 1.0f, 0.45f, 1.0f), "● BULL KERNEL ONLINE");
        } else {
            ImGui::TextColored(ImVec4(1.0f, 0.3f, 0.3f, 1.0f), "● BULL KERNEL OFFLINE [PREVIEW]");
        }
        ImGui::Separator();

        if (ImGui::BeginTabBar("BullTabBar", ImGuiTabBarFlags_None)) {
            if (ImGui::BeginTabItem("COMBAT")) {
                ImGui::Spacing();
                ImGui::Checkbox("Silent Aim 360", &pPlayer.silen4a);
                ImGui::Checkbox("Aimbot Direct", &pPlayer.aimbot);
                ImGui::Checkbox("Auto Aim Lock", &pPlayer.aimbotlock);
                ImGui::Checkbox("Aim Magnet V2", &pPlayer.aimMagnet);
                ImGui::Spacing();
                ImGui::SliderFloat("FOV Radius", &pPlayer.AimFov, 20.0f, 160.0f, "%.0f deg");
                ImGui::SliderInt("Headshot Accuracy", &pPlayer.headshotRate, 10, 100, "%d %%");
                ImGui::EndTabItem();
            }

            if (ImGui::BeginTabItem("VISUALS")) {
                ImGui::Spacing();
                ImGui::Checkbox("Draw Box", &pEspPlayer.espCaixa);
                ImGui::Checkbox("Draw Line", &pEspPlayer.espLinha);
                ImGui::Checkbox("Draw Distance", &pEspPlayer.espDistance);
                ImGui::Checkbox("Draw Healthbar", &pEspPlayer.espHealth);
                ImGui::Checkbox("Draw Player Name", &pEspPlayer.espName);
                ImGui::Checkbox("Draw Skeleton Bone", &pEspPlayer.espSkeleton);
                ImGui::EndTabItem();
            }

            if (ImGui::BeginTabItem("MODS")) {
                ImGui::Spacing();
                ImGui::Checkbox("Zero Recoil", &pPlayer.NoRecoil);
                ImGui::Checkbox("Speed Boost", &pPlayer.SpeedHackV2);
                ImGui::Checkbox("Bypass Security", &Actived.Bypass);
                ImGui::EndTabItem();
            }

            if (ImGui::BeginTabItem("STATUS")) {
                ImGui::Spacing();
                ImGui::Text("Bull Engine: Pure C++ Native ImGui");
                ImGui::Text("Kernel Protocol: Character Device /dev/BYNb");
                ImGui::Spacing();
                if (ImGui::Button("CLOSE MENU", ImVec2(-1, 38))) {
                    isOpen = false;
                }
                ImGui::EndTabItem();
            }

            ImGui::EndTabBar();
        }
    }
    ImGui::End();
}
