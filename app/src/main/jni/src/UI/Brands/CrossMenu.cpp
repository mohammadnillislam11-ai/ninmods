#include "CrossMenu.h"
#include "../../BooleanClient.h"
#include <imgui.h>

void CrossMenu::ApplyTheme() {
    ImGuiStyle& style = ImGui::GetStyle();
    ImVec4* colors = style.Colors;

    style.WindowRounding = 10.0f;
    style.ChildRounding = 6.0f;
    style.FrameRounding = 5.0f;
    style.WindowBorderSize = 1.5f;

    // Cross Stealth Emerald Green Palette
    colors[ImGuiCol_WindowBg]             = GetBackgroundColor();
    colors[ImGuiCol_Border]               = ImVec4(0.0f, 0.90f, 0.46f, 0.70f);
    colors[ImGuiCol_TitleBg]              = ImVec4(0.04f, 0.15f, 0.08f, 1.0f);
    colors[ImGuiCol_TitleBgActive]        = ImVec4(0.08f, 0.25f, 0.14f, 1.0f);
    colors[ImGuiCol_Header]               = ImVec4(0.0f, 0.90f, 0.46f, 0.45f);
    colors[ImGuiCol_HeaderHovered]        = ImVec4(0.0f, 0.90f, 0.46f, 0.75f);
    colors[ImGuiCol_HeaderActive]         = ImVec4(0.0f, 0.90f, 0.46f, 0.95f);
    colors[ImGuiCol_Button]               = ImVec4(0.0f, 0.75f, 0.38f, 0.65f);
    colors[ImGuiCol_ButtonHovered]        = ImVec4(0.0f, 0.90f, 0.46f, 0.85f);
    colors[ImGuiCol_ButtonActive]         = ImVec4(0.1f, 1.0f, 0.55f, 1.0f);
    colors[ImGuiCol_FrameBg]              = ImVec4(0.08f, 0.18f, 0.12f, 0.85f);
    colors[ImGuiCol_CheckMark]            = ImVec4(0.0f, 1.0f, 0.50f, 1.0f);
    colors[ImGuiCol_SliderGrab]           = ImVec4(0.0f, 0.90f, 0.46f, 0.90f);
    colors[ImGuiCol_Tab]                  = ImVec4(0.06f, 0.16f, 0.10f, 0.85f);
    colors[ImGuiCol_TabHovered]           = ImVec4(0.0f, 0.90f, 0.46f, 0.70f);
    colors[ImGuiCol_TabActive]            = ImVec4(0.0f, 0.90f, 0.46f, 0.95f);
}

void CrossMenu::RenderMenu(bool& isOpen, bool isKernelActive) {
    if (!isOpen) return;

    ApplyTheme();
    ImGui::SetNextWindowSize(ImVec2(340, 460), ImGuiCond_FirstUseEver);

    if (ImGui::Begin("CROSS TARGET ##MainWindow", &isOpen, ImGuiWindowFlags_NoCollapse)) {
        ImGui::TextColored(GetPrimaryColor(), "CROSS TARGET");
        ImGui::SameLine();
        ImGui::TextDisabled("| %s", GetSubtitle());

        if (isKernelActive) {
            ImGui::TextColored(ImVec4(0.0f, 1.0f, 0.45f, 1.0f), "● CROSS KERNEL ONLINE");
        } else {
            ImGui::TextColored(ImVec4(1.0f, 0.3f, 0.3f, 1.0f), "● CROSS KERNEL OFFLINE [PREVIEW]");
        }
        ImGui::Separator();

        if (ImGui::BeginTabBar("CrossTabBar", ImGuiTabBarFlags_None)) {
            if (ImGui::BeginTabItem("RADAR/ESP")) {
                ImGui::Spacing();
                ImGui::Checkbox("Cross Box", &pEspPlayer.espCaixa);
                ImGui::Checkbox("Cross Line", &pEspPlayer.espLinha);
                ImGui::Checkbox("Cross Distance", &pEspPlayer.espDistance);
                ImGui::Checkbox("Cross Health", &pEspPlayer.espHealth);
                ImGui::Checkbox("Cross Skeleton", &pEspPlayer.espSkeleton);
                ImGui::Checkbox("360 Indicator", &pEspPlayer.espIden360);
                ImGui::EndTabItem();
            }

            if (ImGui::BeginTabItem("TARGET")) {
                ImGui::Spacing();
                ImGui::Checkbox("Aim Lock Target", &pPlayer.aimbotlock);
                ImGui::Checkbox("Auto Aim Target", &pPlayer.aimbot);
                ImGui::Checkbox("Silent Headshot", &pPlayer.silen4a);
                ImGui::SliderFloat("Target FOV", &pPlayer.AimFov, 15.0f, 180.0f, "%.0f deg");
                ImGui::SliderFloat("Target Range", &pPlayer.AimDistance, 10.0f, 300.0f, "%.0f m");
                ImGui::EndTabItem();
            }

            if (ImGui::BeginTabItem("SYSTEM")) {
                ImGui::Spacing();
                ImGui::Text("Cross Stealth Pipeline Active");
                ImGui::Spacing();
                if (ImGui::Button("MINIMIZE", ImVec2(-1, 38))) {
                    isOpen = false;
                }
                ImGui::EndTabItem();
            }

            ImGui::EndTabBar();
        }
    }
    ImGui::End();
}
