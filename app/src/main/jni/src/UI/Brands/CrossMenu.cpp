#include "CrossMenu.h"
#include "../NativeOverlayController.h"
#include "../Toggle.h"
#include "../../BooleanClient.h"
#if __has_include(<imgui.h>)
#include <imgui.h>
#else
#include "../../imgui/imgui.h"
#endif

void CrossMenu::ApplyTheme() {
    ImGuiStyle& style = ImGui::GetStyle();
    ImVec4* colors = style.Colors;

    style.WindowRounding    = 14.0f;
    style.ChildRounding     = 10.0f;
    style.FrameRounding     = 8.0f;
    style.PopupRounding     = 12.0f;
    style.ScrollbarRounding = 8.0f;
    style.GrabRounding      = 8.0f;
    style.TabRounding       = 8.0f;
    style.WindowBorderSize  = 2.0f;

    // Cross Electric Cyan Palette
    colors[ImGuiCol_WindowBg]             = ImVec4(0.04f, 0.08f, 0.14f, 0.96f);
    colors[ImGuiCol_ChildBg]              = ImVec4(0.06f, 0.12f, 0.18f, 0.85f);
    colors[ImGuiCol_Border]               = ImVec4(0.0f, 0.90f, 1.0f, 0.85f); // Electric Cyan Border
    colors[ImGuiCol_TitleBg]              = ImVec4(0.05f, 0.15f, 0.22f, 1.0f);
    colors[ImGuiCol_TitleBgActive]        = ImVec4(0.08f, 0.25f, 0.35f, 1.0f);
    colors[ImGuiCol_Header]               = ImVec4(0.0f, 0.90f, 1.0f, 0.45f);
    colors[ImGuiCol_HeaderHovered]        = ImVec4(0.0f, 0.90f, 1.0f, 0.75f);
    colors[ImGuiCol_HeaderActive]         = ImVec4(0.0f, 0.90f, 1.0f, 0.95f);
    colors[ImGuiCol_Button]               = ImVec4(0.0f, 0.65f, 0.85f, 0.65f);
    colors[ImGuiCol_ButtonHovered]        = ImVec4(0.0f, 0.80f, 1.0f, 0.85f);
    colors[ImGuiCol_ButtonActive]         = ImVec4(0.0f, 0.95f, 1.0f, 1.0f);
    colors[ImGuiCol_FrameBg]              = ImVec4(0.08f, 0.16f, 0.24f, 0.85f);
    colors[ImGuiCol_FrameBgHovered]       = ImVec4(0.12f, 0.24f, 0.36f, 0.90f);
    colors[ImGuiCol_FrameBgActive]        = ImVec4(0.16f, 0.32f, 0.48f, 1.0f);
    colors[ImGuiCol_CheckMark]            = ImVec4(0.0f, 1.0f, 0.85f, 1.0f);
    colors[ImGuiCol_SliderGrab]           = ImVec4(0.0f, 0.90f, 1.0f, 0.90f);
    colors[ImGuiCol_SliderGrabActive]     = ImVec4(0.2f, 1.0f, 1.0f, 1.0f);
    colors[ImGuiCol_Tab]                  = ImVec4(0.06f, 0.16f, 0.24f, 0.85f);
    colors[ImGuiCol_TabHovered]           = ImVec4(0.0f, 0.80f, 1.0f, 0.70f);
    colors[ImGuiCol_TabActive]            = ImVec4(0.0f, 0.90f, 1.0f, 0.95f);
}

void CrossMenu::RenderMenu(bool& isOpen, bool isKernelActive) {
    if (!isOpen) return;

    ApplyTheme();

    float scrW = (float)NativeOverlayController::s_ScreenWidth.load(std::memory_order_relaxed);
    float scrH = (float)NativeOverlayController::s_ScreenHeight.load(std::memory_order_relaxed);

    ImGui::SetNextWindowPos(ImVec2(0, 0));
    ImGui::SetNextWindowSize(ImVec2(scrW, scrH));

    ImGuiWindowFlags winFlags = ImGuiWindowFlags_NoTitleBar |
                                ImGuiWindowFlags_NoResize |
                                ImGuiWindowFlags_NoMove |
                                ImGuiWindowFlags_NoCollapse;

    if (ImGui::Begin("CROSS PRO TACTICAL ##MainWindow", &isOpen, winFlags)) {
        ImGui::TextColored(GetPrimaryColor(), "[CROSS PRO TACTICAL]");
        ImGui::SameLine();
        ImGui::TextDisabled("| %s", GetSubtitle());

        float rightEdge = ImGui::GetWindowWidth() - ImGui::GetStyle().WindowPadding.x;
        float btnW = 34.0f * (NativeOverlayController::s_DpiScale.load(std::memory_order_relaxed) * 0.4f);
        if (btnW < 36.0f) btnW = 36.0f;

        ImGui::SameLine(rightEdge - (btnW * 2.2f));
        if (ImGui::Button("-##MinBtn", ImVec2(btnW, btnW * 0.85f))) {
            isOpen = false;
        }
        ImGui::SameLine(rightEdge - btnW);
        if (ImGui::Button("X##CloseBtn", ImVec2(btnW, btnW * 0.85f))) {
            isOpen = false;
        }

        if (isKernelActive) {
            ImGui::TextColored(ImVec4(0.0f, 1.0f, 0.45f, 1.0f), "[ONLINE] CROSS KERNEL (/dev/BYNb)");
        } else {
            ImGui::TextColored(ImVec4(1.0f, 0.3f, 0.3f, 1.0f), "[OFFLINE] CROSS KERNEL [PREVIEW]");
        }
        ImGui::Separator();

        if (ImGui::BeginTabBar("CrossTabBar", ImGuiTabBarFlags_None)) {
            if (ImGui::BeginTabItem("CROSS VISION")) {
                if (ImGui::BeginChild("CrossVisChild", ImVec2(0, 0), true)) {
                    ImGui::TextColored(GetPrimaryColor(), "== PRECISION ESP ==");
                    ImGui::Separator();
                    ImGui::Spacing();

                    AtomicCheckbox("Cross Box", &pEspPlayer.espCaixa);
                    AtomicCheckbox("Cross Line", &pEspPlayer.espLinha);
                    AtomicCheckbox("Cross Distance", &pEspPlayer.espDistance);
                    AtomicCheckbox("Cross Health", &pEspPlayer.espHealth);
                    AtomicCheckbox("Cross Skeleton", &pEspPlayer.espSkeleton);
                    AtomicCheckbox("360 Indicator", &pEspPlayer.espIden360);
                    AtomicCheckbox("Threat Counter", &pEspPlayer.espDrawCount);
                }
                ImGui::EndChild();
                ImGui::EndTabItem();
            }

            if (ImGui::BeginTabItem("CROSS AIM")) {
                if (ImGui::BeginChild("CrossAimChild", ImVec2(0, 0), true)) {
                    ImGui::TextColored(GetPrimaryColor(), "== LOCK TARGETING ==");
                    ImGui::Separator();
                    ImGui::Spacing();

                    AtomicCheckbox("Aim Lock Target", &pPlayer.aimbotlock);
                    AtomicCheckbox("Auto Aim Target", &pPlayer.aimbot);
                    AtomicCheckbox("Silent Headshot", &pPlayer.silen4a);
                    AtomicCheckbox("Draw FOV Circle", &showFOVCircle);
                    AtomicCheckbox("Aim Magnet", &pPlayer.aimMagnet);
                    ImGui::Spacing();
                    AtomicSliderFloat("Target FOV", &pPlayer.AimFov, 15.0f, 180.0f, "%.0f deg");
                    AtomicSliderFloat("Target Range", &pPlayer.AimDistance, 10.0f, 300.0f, "%.0f m");
                    AtomicSliderInt("Headshot Rate", &pPlayer.headshotRate, 0, 100, "%d %%");
                }
                ImGui::EndChild();
                ImGui::EndTabItem();
            }

            if (ImGui::BeginTabItem("SYSTEM")) {
                if (ImGui::BeginChild("CrossSysChild", ImVec2(0, 0), true)) {
                    ImGui::Text("Brand Engine   : CROSS Precision Mod");
                    ImGui::Text("Kernel Protocol: RT Direct Memory Access");
                    ImGui::Spacing();
                    if (ImGui::Button("COLLAPSE TO LOGO", ImVec2(-1, 42))) {
                        isOpen = false;
                    }
                }
                ImGui::EndChild();
                ImGui::EndTabItem();
            }

            ImGui::EndTabBar();
        }
    }
    ImGui::End();
}
