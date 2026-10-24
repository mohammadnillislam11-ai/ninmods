#include "DeltaMenu.h"
#include "../../BooleanClient.h"
#include <imgui.h>

void DeltaMenu::ApplyTheme() {
    ImGuiStyle& style = ImGui::GetStyle();
    ImVec4* colors = style.Colors;

    style.WindowRounding = 14.0f;
    style.ChildRounding = 8.0f;
    style.FrameRounding = 6.0f;
    style.WindowBorderSize = 1.5f;

    // Delta Electric Blue Palette
    colors[ImGuiCol_WindowBg]             = GetBackgroundColor();
    colors[ImGuiCol_Border]               = ImVec4(0.0f, 0.70f, 1.0f, 0.75f);
    colors[ImGuiCol_TitleBg]              = ImVec4(0.04f, 0.12f, 0.22f, 1.0f);
    colors[ImGuiCol_TitleBgActive]        = ImVec4(0.06f, 0.20f, 0.38f, 1.0f);
    colors[ImGuiCol_Header]               = ImVec4(0.0f, 0.70f, 1.0f, 0.45f);
    colors[ImGuiCol_HeaderHovered]        = ImVec4(0.0f, 0.70f, 1.0f, 0.75f);
    colors[ImGuiCol_HeaderActive]         = ImVec4(0.0f, 0.70f, 1.0f, 0.95f);
    colors[ImGuiCol_Button]               = ImVec4(0.0f, 0.55f, 0.90f, 0.65f);
    colors[ImGuiCol_ButtonHovered]        = ImVec4(0.0f, 0.70f, 1.0f, 0.85f);
    colors[ImGuiCol_ButtonActive]         = ImVec4(0.2f, 0.80f, 1.0f, 1.0f);
    colors[ImGuiCol_FrameBg]              = ImVec4(0.08f, 0.14f, 0.24f, 0.85f);
    colors[ImGuiCol_CheckMark]            = ImVec4(0.0f, 0.85f, 1.0f, 1.0f);
    colors[ImGuiCol_SliderGrab]           = ImVec4(0.0f, 0.70f, 1.0f, 0.90f);
    colors[ImGuiCol_Tab]                  = ImVec4(0.06f, 0.14f, 0.22f, 0.85f);
    colors[ImGuiCol_TabHovered]           = ImVec4(0.0f, 0.70f, 1.0f, 0.70f);
    colors[ImGuiCol_TabActive]            = ImVec4(0.0f, 0.70f, 1.0f, 0.95f);
}

void DeltaMenu::RenderMenu(bool& isOpen, bool isKernelActive) {
    if (!isOpen) return;

    ApplyTheme();
    ImGui::SetNextWindowSize(ImVec2(340, 460), ImGuiCond_FirstUseEver);

    if (ImGui::Begin("DELTA STREAMLINE ##MainWindow", &isOpen, ImGuiWindowFlags_NoCollapse)) {
        ImGui::TextColored(GetPrimaryColor(), "DELTA STREAMLINE");
        ImGui::SameLine();
        ImGui::TextDisabled("| %s", GetSubtitle());

        if (isKernelActive) {
            ImGui::TextColored(ImVec4(0.0f, 1.0f, 0.45f, 1.0f), "● DELTA KERNEL ONLINE");
        } else {
            ImGui::TextColored(ImVec4(1.0f, 0.3f, 0.3f, 1.0f), "● DELTA KERNEL OFFLINE [PREVIEW]");
        }
        ImGui::Separator();

        if (ImGui::BeginTabBar("DeltaTabBar", ImGuiTabBarFlags_None)) {
            if (ImGui::BeginTabItem("SPEED/AIM")) {
                ImGui::Spacing();
                ImGui::Checkbox("Fast Aim Lock", &pPlayer.aimbotlock);
                ImGui::Checkbox("Silent Headshot", &pPlayer.silen4a);
                ImGui::Checkbox("Delta Speed Hack", &pPlayer.SpeedHackV2);
                ImGui::SliderFloat("Delta FOV", &pPlayer.AimFov, 20.0f, 150.0f, "%.0f deg");
                ImGui::EndTabItem();
            }

            if (ImGui::BeginTabItem("ESP VIEW")) {
                ImGui::Spacing();
                ImGui::Checkbox("Delta Box", &pEspPlayer.espCaixa);
                ImGui::Checkbox("Delta Line", &pEspPlayer.espLinha);
                ImGui::Checkbox("Delta Distance", &pEspPlayer.espDistance);
                ImGui::Checkbox("Delta Health", &pEspPlayer.espHealth);
                ImGui::Checkbox("Delta Skeleton", &pEspPlayer.espSkeleton);
                ImGui::EndTabItem();
            }

            if (ImGui::BeginTabItem("CONFIG")) {
                ImGui::Spacing();
                ImGui::Text("Delta Lightweight Core v90");
                ImGui::Spacing();
                if (ImGui::Button("HIDE MENU", ImVec2(-1, 38))) {
                    isOpen = false;
                }
                ImGui::EndTabItem();
            }

            ImGui::EndTabBar();
        }
    }
    ImGui::End();
}
