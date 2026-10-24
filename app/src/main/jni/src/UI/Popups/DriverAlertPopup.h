#pragma once

#include <imgui.h>
#include <functional>

/**
 * C++ Dear ImGui Native Kernel Driver Alert Modal Popup.
 * Handles the state where /dev/BYNb is not active.
 * Provides two clean paths:
 *   1. "OPEN LOADER" for Root users wanting to activate the kernel driver.
 *   2. "VIEW MENU ONLY (NON-ROOT)" for Non-Root users who just want to explore the menu UI.
 */
class DriverAlertPopup {
public:
    static bool s_IsOpen;
    static bool s_PreviewMode;
    static std::function<void()> s_OnOpenLoaderCallback;

    static void Open(std::function<void()> onOpenLoader = nullptr) {
        s_IsOpen = true;
        s_OnOpenLoaderCallback = onOpenLoader;
    }

    static bool IsOpen() {
        return s_IsOpen;
    }

    static bool IsPreviewMode() {
        return s_PreviewMode;
    }

    static void SetPreviewMode(bool v) {
        s_PreviewMode = v;
    }

    static void Render(std::function<void()> onProceedToMenu) {
        if (!s_IsOpen) return;

        ImGui::OpenPopup("Kernel Driver Alert##Modal");

        ImGui::SetNextWindowSize(ImVec2(330, 240), ImGuiCond_Appearing);
        ImGui::SetNextWindowPos(ImVec2(ImGui::GetIO().DisplaySize.x * 0.5f, ImGui::GetIO().DisplaySize.y * 0.5f), ImGuiCond_Appearing, ImVec2(0.5f, 0.5f));

        if (ImGui::BeginPopupModal("Kernel Driver Alert##Modal", NULL, ImGuiWindowFlags_AlwaysAutoResize | ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoCollapse)) {
            ImGui::TextColored(ImVec4(1.0f, 0.35f, 0.35f, 1.0f), "● RING 0 DRIVER OFFLINE");
            ImGui::Separator();
            ImGui::Spacing();

            ImGui::TextWrapped("The kernel device endpoint (/dev/BYNb) is not currently active on this device.");
            ImGui::Spacing();
            ImGui::TextDisabled("• Root users: Launch Driver Loader to load module.\n• Non-Root users: You can preview the menu UI safely.");

            ImGui::Spacing();
            ImGui::Separator();
            ImGui::Spacing();

            // Button 1: Open Loader
            if (ImGui::Button("OPEN LOADER (ROOT)", ImVec2(-1, 38))) {
                if (s_OnOpenLoaderCallback) s_OnOpenLoaderCallback();
                s_IsOpen = false;
                ImGui::CloseCurrentPopup();
            }

            ImGui::Spacing();

            // Button 2: Non-Root Preview
            ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.2f, 0.5f, 0.8f, 0.7f));
            if (ImGui::Button("VIEW MENU ONLY (NON-ROOT)", ImVec2(-1, 36))) {
                s_PreviewMode = true;
                s_IsOpen = false;
                ImGui::CloseCurrentPopup();
                if (onProceedToMenu) onProceedToMenu();
            }
            ImGui::PopStyleColor();

            ImGui::Spacing();
            if (ImGui::Button("CANCEL", ImVec2(-1, 30))) {
                s_IsOpen = false;
                ImGui::CloseCurrentPopup();
            }

            ImGui::EndPopup();
        }
    }
};

inline bool DriverAlertPopup::s_IsOpen = false;
inline bool DriverAlertPopup::s_PreviewMode = false;
inline std::function<void()> DriverAlertPopup::s_OnOpenLoaderCallback = nullptr;
