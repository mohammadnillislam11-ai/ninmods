#include "DeltaMenu.h"
#include "../NativeOverlayController.h"
#include "../../BooleanClient.h"
#if __has_include(<imgui.h>)
#include <imgui.h>
#else
#include "../../imgui/imgui.h"
#endif

void DeltaMenu::ApplyTheme() {
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

    // Delta Acid Emerald Palette
    colors[ImGuiCol_WindowBg]             = ImVec4(0.04f, 0.10f, 0.06f, 0.96f);
    colors[ImGuiCol_ChildBg]              = ImVec4(0.06f, 0.14f, 0.08f, 0.85f);
    colors[ImGuiCol_Border]               = ImVec4(0.0f, 0.90f, 0.45f, 0.85f); // Acid Emerald Border
    colors[ImGuiCol_TitleBg]              = ImVec4(0.06f, 0.18f, 0.10f, 1.0f);
    colors[ImGuiCol_TitleBgActive]        = ImVec4(0.10f, 0.30f, 0.15f, 1.0f);
    colors[ImGuiCol_Header]               = ImVec4(0.0f, 0.90f, 0.45f, 0.45f);
    colors[ImGuiCol_HeaderHovered]        = ImVec4(0.0f, 0.90f, 0.45f, 0.75f);
    colors[ImGuiCol_HeaderActive]         = ImVec4(0.0f, 0.90f, 0.45f, 0.95f);
    colors[ImGuiCol_Button]               = ImVec4(0.0f, 0.70f, 0.35f, 0.65f);
    colors[ImGuiCol_ButtonHovered]        = ImVec4(0.0f, 0.85f, 0.45f, 0.85f);
    colors[ImGuiCol_ButtonActive]         = ImVec4(0.1f, 1.0f, 0.55f, 1.0f);
    colors[ImGuiCol_FrameBg]              = ImVec4(0.08f, 0.20f, 0.12f, 0.85f);
    colors[ImGuiCol_FrameBgHovered]       = ImVec4(0.12f, 0.28f, 0.16f, 0.90f);
    colors[ImGuiCol_FrameBgActive]        = ImVec4(0.16f, 0.36f, 0.22f, 1.0f);
    colors[ImGuiCol_CheckMark]            = ImVec4(0.2f, 1.0f, 0.6f, 1.0f);
    colors[ImGuiCol_SliderGrab]           = ImVec4(0.0f, 0.90f, 0.45f, 0.90f);
    colors[ImGuiCol_SliderGrabActive]     = ImVec4(0.2f, 1.0f, 0.6f, 1.0f);
    colors[ImGuiCol_Tab]                  = ImVec4(0.06f, 0.18f, 0.10f, 0.85f);
    colors[ImGuiCol_TabHovered]           = ImVec4(0.0f, 0.80f, 0.40f, 0.70f);
    colors[ImGuiCol_TabActive]            = ImVec4(0.0f, 0.90f, 0.45f, 0.95f);
}

void DeltaMenu::RenderMenu(bool& isOpen, bool isKernelActive) {
    if (!isOpen) return;

    ApplyTheme();

    float scrW = (float)NativeOverlayController::s_ScreenWidth;
    float scrH = (float)NativeOverlayController::s_ScreenHeight;

    ImGui::SetNextWindowPos(ImVec2(0, 0));
    ImGui::SetNextWindowSize(ImVec2(scrW, scrH));

    ImGuiWindowFlags winFlags = ImGuiWindowFlags_NoTitleBar |
                                ImGuiWindowFlags_NoResize |
                                ImGuiWindowFlags_NoMove |
                                ImGuiWindowFlags_NoCollapse;

    if (ImGui::Begin("DELTA STEALTH TACTICAL ##MainWindow", &isOpen, winFlags)) {
        ImGui::TextColored(GetPrimaryColor(), "[DELTA STEALTH TACTICAL]");
        ImGui::SameLine();
        ImGui::TextDisabled("| %s", GetSubtitle());

        float rightEdge = ImGui::GetWindowWidth() - ImGui::GetStyle().WindowPadding.x;
        float btnW = 34.0f * (NativeOverlayController::s_DpiScale * 0.4f);
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
            ImGui::TextColored(ImVec4(0.0f, 1.0f, 0.45f, 1.0f), "[ONLINE] DELTA KERNEL (/dev/BYNb)");
        } else {
            ImGui::TextColored(ImVec4(1.0f, 0.3f, 0.3f, 1.0f), "[OFFLINE] DELTA KERNEL [PREVIEW]");
        }
        ImGui::Separator();

        if (ImGui::BeginTabBar("DeltaTabBar", ImGuiTabBarFlags_None)) {
            if (ImGui::BeginTabItem("DELTA SPEED")) {
                if (ImGui::BeginChild("DeltaSpeedChild", ImVec2(0, 0), true)) {
                    ImGui::TextColored(GetPrimaryColor(), "== HIGH SPEED ENGINE ==");
                    ImGui::Separator();
                    ImGui::Spacing();

                    ImGui::Checkbox("Fast Aim Lock", &pPlayer.aimbotlock);
                    ImGui::Checkbox("Silent Headshot", &pPlayer.silen4a);
                    ImGui::Checkbox("Draw FOV Circle", &showFOVCircle);
                    ImGui::Checkbox("Delta Speed Hack", &pPlayer.SpeedHackV2);
                    ImGui::SliderFloat("Delta FOV", &pPlayer.AimFov, 20.0f, 150.0f, "%.0f deg");
                }
                ImGui::EndChild();
                ImGui::EndTabItem();
            }

            if (ImGui::BeginTabItem("DELTA VISION")) {
                if (ImGui::BeginChild("DeltaVisChild", ImVec2(0, 0), true)) {
                    ImGui::TextColored(GetPrimaryColor(), "== STEALTH RADAR & ESP ==");
                    ImGui::Separator();
                    ImGui::Spacing();

                    ImGui::Checkbox("Delta Box", &pEspPlayer.espCaixa);
                    ImGui::Checkbox("Delta Line", &pEspPlayer.espLinha);
                    ImGui::Checkbox("Delta Distance", &pEspPlayer.espDistance);
                    ImGui::Checkbox("Delta Health", &pEspPlayer.espHealth);
                    ImGui::Checkbox("Delta Skeleton", &pEspPlayer.espSkeleton);
                }
                ImGui::EndChild();
                ImGui::EndTabItem();
            }

            if (ImGui::BeginTabItem("SYSTEM")) {
                if (ImGui::BeginChild("DeltaSysChild", ImVec2(0, 0), true)) {
                    ImGui::Text("Brand Engine   : DELTA Stealth Mod");
                    ImGui::Text("Kernel Protocol: RT Memory Virtualization");
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
