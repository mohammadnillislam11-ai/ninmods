#include "BullMenu.h"
#include "../NativeOverlayController.h"
#include "../Toggle.h"
#include "../../BooleanClient.h"
#if __has_include(<imgui.h>)
#include <imgui.h>
#else
#include "../../imgui/imgui.h"
#endif

void BullMenu::ApplyTheme() {
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

    // Bull Aggressive Gold / Crimson Palette
    colors[ImGuiCol_WindowBg]             = ImVec4(0.12f, 0.06f, 0.04f, 0.96f);
    colors[ImGuiCol_ChildBg]              = ImVec4(0.16f, 0.08f, 0.06f, 0.85f);
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

    float scrW = (float)NativeOverlayController::s_ScreenWidth.load(std::memory_order_relaxed);
    float scrH = (float)NativeOverlayController::s_ScreenHeight.load(std::memory_order_relaxed);

    ImGui::SetNextWindowPos(ImVec2(0, 0));
    ImGui::SetNextWindowSize(ImVec2(scrW, scrH));

    ImGuiWindowFlags winFlags = ImGuiWindowFlags_NoTitleBar |
                                ImGuiWindowFlags_NoResize |
                                ImGuiWindowFlags_NoMove |
                                ImGuiWindowFlags_NoCollapse;

    if (ImGui::Begin("BULL VIP TACTICAL ##MainWindow", &isOpen, winFlags)) {
        // Top Header
        ImGui::TextColored(GetPrimaryColor(), "[BULL VIP TACTICAL]");
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
            ImGui::TextColored(ImVec4(0.0f, 1.0f, 0.45f, 1.0f), "[ONLINE] BULL KERNEL (/dev/BYNb)");
        } else {
            ImGui::TextColored(ImVec4(1.0f, 0.3f, 0.3f, 1.0f), "[OFFLINE] BULL KERNEL [PREVIEW]");
        }
        ImGui::Separator();

        if (ImGui::BeginTabBar("BullTabBar", ImGuiTabBarFlags_None)) {
            if (ImGui::BeginTabItem("COMBAT")) {
                if (ImGui::BeginChild("CombatChild", ImVec2(0, 0), true)) {
                    ImGui::TextColored(GetPrimaryColor(), "== BULL AGGRESSIVE AIMBOT ==");
                    ImGui::Separator();
                    ImGui::Spacing();

                    AtomicCheckbox("Silent Aim 360", &pPlayer.silen4a);
                    AtomicCheckbox("Aimbot Direct Track", &pPlayer.aimbot);
                    AtomicCheckbox("Auto Aim Lock", &pPlayer.aimbotlock);
                    AtomicCheckbox("Draw FOV Circle", &showFOVCircle);
                    AtomicCheckbox("Aim Magnet V2", &pPlayer.aimMagnet);
                    AtomicCheckbox("Ignore Knocked", &pPlayer.ignoreKnocked);
                    AtomicCheckbox("Ignore Bots", &pPlayer.ignoreBots);

                    ImGui::Spacing();
                    ImGui::Separator();
                    AtomicSliderFloat("FOV Circle", &pPlayer.AimFov, 20.0f, 200.0f, "%.0f deg");
                    AtomicSliderFloat("Lock Range", &pPlayer.AimDistance, 10.0f, 300.0f, "%.0f m");
                    AtomicSliderInt("Headshot Rate", &pPlayer.headshotRate, 0, 100, "%d %%");
                }
                ImGui::EndChild();
                ImGui::EndTabItem();
            }

            if (ImGui::BeginTabItem("VISUALS")) {
                if (ImGui::BeginChild("VisChild", ImVec2(0, 0), true)) {
                    ImGui::TextColored(GetPrimaryColor(), "== BULL ESP SENSORS ==");
                    ImGui::Separator();
                    ImGui::Spacing();

                    AtomicCheckbox("Bull Box ESP", &pEspPlayer.espCaixa);
                    AtomicCheckbox("Bull Tracer Line", &pEspPlayer.espLinha);
                    AtomicCheckbox("Enemy Distance", &pEspPlayer.espDistance);
                    AtomicCheckbox("Health Meter", &pEspPlayer.espHealth);
                    AtomicCheckbox("Skeleton Bones", &pEspPlayer.espSkeleton);
                    AtomicCheckbox("360 Radar Sensor", &pEspPlayer.espIden360);
                    AtomicCheckbox("Threat Counter HUD", &pEspPlayer.espDrawCount);
                }
                ImGui::EndChild();
                ImGui::EndTabItem();
            }

            if (ImGui::BeginTabItem("EXPLOITS")) {
                if (ImGui::BeginChild("ExploitsChild", ImVec2(0, 0), true)) {
                    ImGui::TextColored(GetPrimaryColor(), "== MEMORY MECHANICS ==");
                    ImGui::Separator();
                    ImGui::Spacing();

                    AtomicCheckbox("No Recoil Stabilizer", &pPlayer.NoRecoil);
                    AtomicCheckbox("Bull Speed Boost", &pPlayer.SpeedHackV2);
                    if (pPlayer.SpeedHackV2) {
                        AtomicSliderFloat("Multiplier", &pPlayer.gameSpeed, 1.0f, 3.5f, "%.1fx");
                    }
                    AtomicCheckbox("Memory Anticheat Shield", &Actived.Bypass);
                }
                ImGui::EndChild();
                ImGui::EndTabItem();
            }

            if (ImGui::BeginTabItem("SYSTEM")) {
                if (ImGui::BeginChild("SysChild", ImVec2(0, 0), true)) {
                    ImGui::Text("Brand Engine   : BULL VIP Tactical Mod");
                    ImGui::Text("Kernel Protocol: RT Ring-0 Device Node");
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
