#pragma once

#include <imgui.h>
#include <string>
#include <functional>
#include <cstring>
#include <algorithm>
#include <jni.h>
#include "../../Icon/TextureLoader.h"
#include "../../Includes/Logger.h"

// JNI Helpers to interface with Android NativeBridge
inline std::string GetClipboardTextNative() {
    extern JavaVM* g_JavaVM;
    if (!g_JavaVM) return "";
    JNIEnv* jEnv = nullptr;
    bool attached = false;
    if (g_JavaVM->GetEnv((void**)&jEnv, JNI_VERSION_1_6) != JNI_OK) {
        if (g_JavaVM->AttachCurrentThread(&jEnv, nullptr) == JNI_OK) attached = true;
    }
    std::string result = "";
    if (jEnv) {
        jclass bridgeClass = jEnv->FindClass("com/ninmods/main/core/NativeBridge");
        if (bridgeClass) {
            jmethodID mid = jEnv->GetStaticMethodID(bridgeClass, "getClipboardText", "()Ljava/lang/String;");
            if (mid) {
                jstring jStr = (jstring)jEnv->CallStaticObjectMethod(bridgeClass, mid);
                if (jStr) {
                    const char* chars = jEnv->GetStringUTFChars(jStr, nullptr);
                    if (chars) {
                        result = chars;
                        jEnv->ReleaseStringUTFChars(jStr, chars);
                    }
                    jEnv->DeleteLocalRef(jStr);
                }
            }
            jEnv->DeleteLocalRef(bridgeClass);
        }
        if (attached) g_JavaVM->DetachCurrentThread();
    }
    return result;
}

inline void ShowKeyInputDialogNative(const std::string& currentKey) {
    extern JavaVM* g_JavaVM;
    if (!g_JavaVM) return;
    JNIEnv* jEnv = nullptr;
    bool attached = false;
    if (g_JavaVM->GetEnv((void**)&jEnv, JNI_VERSION_1_6) != JNI_OK) {
        if (g_JavaVM->AttachCurrentThread(&jEnv, nullptr) == JNI_OK) attached = true;
    }
    if (jEnv) {
        jclass bridgeClass = jEnv->FindClass("com/ninmods/main/core/NativeBridge");
        if (bridgeClass) {
            jmethodID mid = jEnv->GetStaticMethodID(bridgeClass, "showKeyInputDialog", "(Ljava/lang/String;)V");
            if (mid) {
                jstring jK = jEnv->NewStringUTF(currentKey.c_str());
                jEnv->CallStaticVoidMethod(bridgeClass, mid, jK);
                jEnv->DeleteLocalRef(jK);
            }
            jEnv->DeleteLocalRef(bridgeClass);
        }
        if (attached) g_JavaVM->DetachCurrentThread();
    }
}

/**
 * C++ Dear ImGui Native License Key Verification Modal Popup.
 * Matches NINMODS authentic tactical aesthetic with pinned Eagle Banner,
 * high-visibility text input, one-tap clipboard paste, native soft keyboard input,
 * status indicators, and smooth verification.
 */
class LicensePopup {
public:
    static bool s_IsOpen;
    static bool s_IsVerified;
    static char s_KeyBuffer[64];
    static char s_StatusMessage[128];
    static bool s_IsVerifying;
    static std::string s_Hwid;
    static std::string s_ServerUrl;
    static std::function<void(const std::string& key, const std::string& brand)> s_VerifyHandler;

    static void Open(const std::string& hwid = "", const std::string& prefillKey = "", const std::string& serverUrl = "") {
        s_IsOpen = true;
        s_IsVerifying = false;
        memset(s_StatusMessage, 0, sizeof(s_StatusMessage));
        if (!hwid.empty()) s_Hwid = hwid;
        if (!serverUrl.empty()) s_ServerUrl = serverUrl;
        if (!prefillKey.empty() && s_KeyBuffer[0] == '\0') {
            strncpy(s_KeyBuffer, prefillKey.c_str(), sizeof(s_KeyBuffer) - 1);
        } else if (s_KeyBuffer[0] == '\0') {
            // Auto-check clipboard for valid key (>=6 chars, no newlines)
            std::string clip = GetClipboardTextNative();
            if (clip.length() >= 6 && clip.length() < 64 && clip.find('\n') == std::string::npos && clip.find(' ') == std::string::npos) {
                strncpy(s_KeyBuffer, clip.c_str(), sizeof(s_KeyBuffer) - 1);
            }
        }
    }

    static bool IsOpen() {
        return s_IsOpen && !s_IsVerified;
    }

    static bool IsVerified() {
        return s_IsVerified;
    }

    static void SetVerified(bool v) {
        s_IsVerified = v;
        if (v) s_IsOpen = false;
    }

    static void SetVerificationResult(bool success, const char* message) {
        s_IsVerifying = false;
        if (success) {
            s_IsVerified = true;
            s_IsOpen = false;
            memset(s_StatusMessage, 0, sizeof(s_StatusMessage));
        } else {
            if (message && message[0] != '\0') {
                strncpy(s_StatusMessage, message, sizeof(s_StatusMessage) - 1);
            } else {
                strncpy(s_StatusMessage, "Invalid subscription key.", sizeof(s_StatusMessage) - 1);
            }
        }
    }

    static void SetVerifyHandler(std::function<void(const std::string& key, const std::string& brand)> handler) {
        s_VerifyHandler = handler;
    }

    static void Render(const char* brandId, ImVec4 themeColor, std::function<void()> onSuccessCallback) {
        if (!s_IsOpen || s_IsVerified) return;

        float scrW = ImGui::GetIO().DisplaySize.x > 0 ? ImGui::GetIO().DisplaySize.x : 1080.0f;
        float scrH = ImGui::GetIO().DisplaySize.y > 0 ? ImGui::GetIO().DisplaySize.y : 1920.0f;

        // Fill the entire modal dialog SurfaceView created by NativeOverlayManager
        ImGui::SetNextWindowPos(ImVec2(0, 0));
        ImGui::SetNextWindowSize(ImVec2(scrW, scrH));

        ImGuiWindowFlags winFlags = ImGuiWindowFlags_NoTitleBar |
                                    ImGuiWindowFlags_NoResize |
                                    ImGuiWindowFlags_NoMove |
                                    ImGuiWindowFlags_NoScrollbar |
                                    ImGuiWindowFlags_NoCollapse;

        ImGui::PushStyleVar(ImGuiStyleVar_WindowRounding, 12.0f);
        ImGui::PushStyleVar(ImGuiStyleVar_WindowBorderSize, 1.5f);
        ImGui::PushStyleColor(ImGuiCol_WindowBg, ImVec4(0.04f, 0.04f, 0.06f, 0.98f));
        ImGui::PushStyleColor(ImGuiCol_Border, ImVec4(themeColor.x, themeColor.y, themeColor.z, 0.85f));

        if (ImGui::Begin("##NINMODS_LICENSE_MODAL", &s_IsOpen, winFlags)) {
            ImVec2 winPos = ImGui::GetWindowPos();
            ImVec2 winSize = ImGui::GetWindowSize();
            ImVec2 mousePos = ImGui::GetIO().MousePos;
            ImDrawList* drawList = ImGui::GetWindowDrawList();

            float headerH = 46.0f;
            ImVec2 closeBtnPos(winPos.x + winSize.x - 36.0f, winPos.y + (headerH - 24.0f) * 0.5f);
            bool overClose = (mousePos.x >= closeBtnPos.x && mousePos.x <= closeBtnPos.x + 26.0f &&
                              mousePos.y >= closeBtnPos.y && mousePos.y <= closeBtnPos.y + 26.0f);

            if (ImGui::IsMouseClicked(0) && overClose && !s_IsVerifying) {
                s_IsOpen = false;
            }

            // 1. Pinned Header Bar
            drawList->AddRectFilled(winPos, ImVec2(winPos.x + winSize.x, winPos.y + headerH), IM_COL32(14, 18, 26, 250), 10.0f, ImDrawFlags_RoundCornersTop);
            drawList->AddLine(ImVec2(winPos.x, winPos.y + headerH), ImVec2(winPos.x + winSize.x, winPos.y + headerH), IM_COL32(60, 80, 115, 160), 1.0f);

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
                drawList->AddText(ImVec2(winPos.x + 14.0f, winPos.y + 12.0f), IM_COL32(255, 255, 255, 250), "[VIP LICENSE ACCESS]");
            }

            // Close [X] Button on Header
            drawList->AddRectFilled(closeBtnPos, ImVec2(closeBtnPos.x + 24.0f, closeBtnPos.y + 24.0f), overClose ? IM_COL32(220, 50, 60, 220) : IM_COL32(50, 65, 88, 160), 5.0f);
            drawList->AddLine(ImVec2(closeBtnPos.x + 6.0f, closeBtnPos.y + 6.0f), ImVec2(closeBtnPos.x + 18.0f, closeBtnPos.y + 18.0f), IM_COL32(255, 255, 255, 250), 2.0f);
            drawList->AddLine(ImVec2(closeBtnPos.x + 18.0f, closeBtnPos.y + 6.0f), ImVec2(closeBtnPos.x + 6.0f, closeBtnPos.y + 18.0f), IM_COL32(255, 255, 255, 250), 2.0f);

            // 3. Modal Body
            ImGui::SetCursorPosY(headerH + 10.0f);
            ImGui::TextColored(themeColor, "VIP SUBSCRIPTION KEY REQUIRED");
            ImGui::Spacing();
            ImGui::TextDisabled("Enter or paste your key to activate mod features:");
            ImGui::Spacing();

            // Input field with dark cyber frame (Tapping on it also opens native keyboard dialog)
            ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, 8.0f);
            ImGui::PushStyleVar(ImGuiStyleVar_FramePadding, ImVec2(10.0f, 8.0f));
            ImGui::PushStyleColor(ImGuiCol_FrameBg, ImVec4(0.12f, 0.16f, 0.24f, 0.85f));
            ImGui::PushStyleColor(ImGuiCol_Border, ImVec4(0.35f, 0.50f, 0.70f, 0.60f));
            ImGui::PushItemWidth(-1);
            ImGui::InputTextWithHint("##KeyInput", "Paste license key here...", s_KeyBuffer, sizeof(s_KeyBuffer));
            if (ImGui::IsItemClicked()) {
                ShowKeyInputDialogNative(s_KeyBuffer);
            }
            ImGui::PopItemWidth();
            ImGui::PopStyleColor(2);
            ImGui::PopStyleVar(2);

            ImGui::Spacing();

            // Action Row 1: Direct One-Tap Paste & Soft Keyboard Buttons
            float availW = ImGui::GetContentRegionAvail().x;
            float btnHalfW = (availW - 8.0f) * 0.5f;
            ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, 6.0f);

            // 1. One-Tap Paste from Clipboard
            ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.12f, 0.45f, 0.80f, 0.80f));
            if (ImGui::Button("[PASTE KEY]", ImVec2(btnHalfW, 36))) {
                std::string clip = GetClipboardTextNative();
                if (!clip.empty()) {
                    strncpy(s_KeyBuffer, clip.c_str(), sizeof(s_KeyBuffer) - 1);
                    s_StatusMessage[0] = '\0';
                } else {
                    strncpy(s_StatusMessage, "Clipboard is empty.", sizeof(s_StatusMessage) - 1);
                }
            }
            ImGui::PopStyleColor();

            ImGui::SameLine();

            // 2. Open Native Android Virtual Keyboard Dialog
            ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.20f, 0.30f, 0.45f, 0.80f));
            if (ImGui::Button("[TYPE KEYBOARD]", ImVec2(btnHalfW, 36))) {
                ShowKeyInputDialogNative(s_KeyBuffer);
            }
            ImGui::PopStyleColor();
            ImGui::PopStyleVar();

            // Status message
            if (s_StatusMessage[0] != '\0') {
                ImGui::Spacing();
                ImGui::TextColored(ImVec4(1.0f, 0.35f, 0.35f, 1.0f), "[ERROR] %s", s_StatusMessage);
            }

            ImGui::Spacing();
            ImGui::Separator();
            ImGui::Spacing();

            // Action Row 2: Verify & Close
            if (s_IsVerifying) {
                ImGui::TextColored(ImVec4(0.2f, 0.8f, 1.0f, 1.0f), "Verifying subscription with server...");
            } else {
                float verifyW = availW * 0.65f;
                float closeW = availW - verifyW - 8.0f;

                ImGui::PushStyleColor(ImGuiCol_Button, themeColor);
                ImGui::PushStyleColor(ImGuiCol_ButtonHovered, ImVec4(themeColor.x * 1.15f, themeColor.y * 1.15f, themeColor.z * 1.15f, 1.0f));
                ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, 8.0f);

                if (ImGui::Button("VERIFY LICENSE", ImVec2(verifyW, 38))) {
                    if (strlen(s_KeyBuffer) < 4) {
                        strncpy(s_StatusMessage, "Please enter a valid key.", sizeof(s_StatusMessage) - 1);
                    } else {
                        s_IsVerifying = true;
                        std::string keyStr(s_KeyBuffer);
                        std::string bId(brandId ? brandId : "nin");

                        if (s_VerifyHandler) {
                            s_VerifyHandler(keyStr, bId);
                        } else {
                            // Fallback simulation/offline check
                            s_IsVerified = true;
                            s_IsOpen = false;
                            s_IsVerifying = false;
                            if (onSuccessCallback) onSuccessCallback();
                        }
                    }
                }
                ImGui::PopStyleVar();
                ImGui::PopStyleColor(2);

                ImGui::SameLine();
                if (ImGui::Button("CLOSE", ImVec2(closeW, 38))) {
                    s_IsOpen = false;
                }
            }
        }
        ImGui::End();
        ImGui::PopStyleColor(2);
        ImGui::PopStyleVar(2);
    }
};

inline bool LicensePopup::s_IsOpen = false;
inline bool LicensePopup::s_IsVerified = false;
inline char LicensePopup::s_KeyBuffer[64] = "";
inline char LicensePopup::s_StatusMessage[128] = "";
inline bool LicensePopup::s_IsVerifying = false;
inline std::string LicensePopup::s_Hwid = "";
inline std::string LicensePopup::s_ServerUrl = "";
inline std::function<void(const std::string& key, const std::string& brand)> LicensePopup::s_VerifyHandler = nullptr;
