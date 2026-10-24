#pragma once

#include <imgui.h>
#include <functional>
#include <algorithm>
#include "../../Icon/TextureLoader.h"

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
        return s_IsOpen && !s_PreviewMode;
    }

    static bool IsPreviewMode() {
        return s_PreviewMode;
    }

    static void SetPreviewMode(bool v) {
        s_PreviewMode = v;
    }

    static void Render(std::function<void()> onProceedToMenu) {
        if (!s_IsOpen || s_PreviewMode) return;

        float scrW = ImGui::GetIO().DisplaySize.x > 0 ? ImGui::GetIO().DisplaySize.x : 1080.0f;
        float scrH = ImGui::GetIO().DisplaySize.y > 0 ? ImGui::GetIO().DisplaySize.y : 1920.0f;

        float popupW = std::min(scrW * 0.94f, 440.0f);
        float popupH = std::min(scrH * 0.72f, 320.0f);

        ImGui::SetNextWindowSize(ImVec2(popupW, popupH), ImGuiCond_Always);
        ImGui::SetNextWindowPos(ImVec2((scrW - popupW) * 0.5f, (scrH - popupH) * 0.5f), ImGuiCond_Always);

        ImGuiWindowFlags winFlags = ImGuiWindowFlags_NoTitleBar |
                                    ImGuiWindowFlags_NoResize |
                                    ImGuiWindowFlags_NoMove |
                                    ImGuiWindowFlags_NoScrollbar |
                                    ImGuiWindowFlags_NoCollapse;

        ImGui::PushStyleVar(ImGuiStyleVar_WindowRounding, 12.0f);
        ImGui::PushStyleVar(ImGuiStyleVar_WindowBorderSize, 1.5f);
        ImGui::PushStyleColor(ImGuiCol_WindowBg, ImVec4(0.04f, 0.04f, 0.06f, 0.96f));
        ImGui::PushStyleColor(ImGuiCol_Border, ImVec4(1.0f, 0.45f, 0.20f, 0.80f)); // Amber/Orange Alert Border

        if (ImGui::Begin("##NINMODS_DRIVER_ALERT_MODAL", &s_IsOpen, winFlags)) {
            ImVec2 winPos = ImGui::GetWindowPos();
            ImVec2 winSize = ImGui::GetWindowSize();
            ImVec2 mousePos = ImGui::GetIO().MousePos;
            ImDrawList* drawList = ImGui::GetWindowDrawList();

            float headerH = 46.0f;
            ImVec2 closeBtnPos(winPos.x + winSize.x - 36.0f, winPos.y + (headerH - 24.0f) * 0.5f);
            bool overClose = (mousePos.x >= closeBtnPos.x && mousePos.x <= closeBtnPos.x + 26.0f &&
                              mousePos.y >= closeBtnPos.y && mousePos.y <= closeBtnPos.y + 26.0f);

            if (ImGui::IsMouseClicked(0) && overClose) {
                s_IsOpen = false;
            }

            // 1. Pinned Header Bar
            drawList->AddRectFilled(winPos, ImVec2(winPos.x + winSize.x, winPos.y + headerH), IM_COL32(26, 18, 14, 250), 10.0f, ImDrawFlags_RoundCornersTop);
            drawList->AddLine(ImVec2(winPos.x, winPos.y + headerH), ImVec2(winPos.x + winSize.x, winPos.y + headerH), IM_COL32(115, 60, 40, 160), 1.0f);

            // 2. Eagle Banner on Header
            if (g_EagleLogoTexture == 0) LoadEagleLogoTexture();
            if (g_EagleLogoTexture != 0) {
                float aspect = (g_EagleLogoHeight > 0) ? ((float)g_EagleLogoWidth / (float)g_EagleLogoHeight) : 2.5f;
                float hLogoH = 80.0f;
                float hLogoW = hLogoH * aspect;
                float hLogoX = winPos.x + (winSize.x - hLogoW) * 0.5f;
                float hLogoY = winPos.y + headerH - hLogoH + 3.0f;
                drawList->PushClipRectFullScreen();
                drawList->AddImage((ImTextureID)(uintptr_t)g_EagleLogoTexture, ImVec2(hLogoX, hLogoY), ImVec2(hLogoX + hLogoW, hLogoY + hLogoH));
                drawList->PopClipRect();
            } else {
                drawList->AddText(ImVec2(winPos.x + 14.0f, winPos.y + 12.0f), IM_COL32(255, 120, 40, 250), "[KERNEL STATUS CHECK]");
            }

            // Close [X] Button on Header
            drawList->AddRectFilled(closeBtnPos, ImVec2(closeBtnPos.x + 24.0f, closeBtnPos.y + 24.0f), overClose ? IM_COL32(220, 50, 60, 220) : IM_COL32(88, 50, 40, 160), 5.0f);
            drawList->AddLine(ImVec2(closeBtnPos.x + 6.0f, closeBtnPos.y + 6.0f), ImVec2(closeBtnPos.x + 18.0f, closeBtnPos.y + 18.0f), IM_COL32(255, 255, 255, 250), 2.0f);
            drawList->AddLine(ImVec2(closeBtnPos.x + 18.0f, closeBtnPos.y + 6.0f), ImVec2(closeBtnPos.x + 6.0f, closeBtnPos.y + 18.0f), IM_COL32(255, 255, 255, 250), 2.0f);

            // 3. Modal Body
            ImGui::SetCursorPosY(headerH + 10.0f);
            ImGui::TextColored(ImVec4(1.0f, 0.40f, 0.20f, 1.0f), "KERNEL ENDPOINT (/dev/BYNb) OFFLINE");
            ImGui::Spacing();
            ImGui::TextWrapped("The Linux Ring-0 driver device node was not detected in /dev.");
            ImGui::Spacing();
            ImGui::TextDisabled("- Root users: Launch Driver Loader to insert kernel module.\n- Non-Root users: You can safely explore the menu UI.");
            ImGui::Spacing();
            ImGui::Separator();
            ImGui::Spacing();

            // Actions
            ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, 8.0f);

            // Button 1: Open Driver Loader
            ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.85f, 0.30f, 0.10f, 0.85f));
            if (ImGui::Button("OPEN DRIVER LOADER (ROOT)", ImVec2(-1, 38))) {
                if (s_OnOpenLoaderCallback) s_OnOpenLoaderCallback();
                s_IsOpen = false;
            }
            ImGui::PopStyleColor();

            ImGui::Spacing();

            // Button 2: Non-Root Preview
            ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.18f, 0.32f, 0.50f, 0.75f));
            if (ImGui::Button("VIEW MENU ONLY (PREVIEW)", ImVec2(-1, 36))) {
                s_PreviewMode = true;
                s_IsOpen = false;
                if (onProceedToMenu) onProceedToMenu();
            }
            ImGui::PopStyleColor();

            ImGui::PopStyleVar();
        }
        ImGui::End();
        ImGui::PopStyleColor(2);
        ImGui::PopStyleVar(2);
    }
};

inline bool DriverAlertPopup::s_IsOpen = false;
inline bool DriverAlertPopup::s_PreviewMode = false;
inline std::function<void()> DriverAlertPopup::s_OnOpenLoaderCallback = nullptr;
