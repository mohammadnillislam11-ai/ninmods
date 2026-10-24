#pragma once

#include <imgui.h>
#include <string>
#include <functional>
#include "../../Includes/httplib.h"
#include "../../Includes/json.hpp"
#include "../../Includes/Logger.h"

/**
 * C++ Dear ImGui Native License Key Verification Modal Popup.
 * Takes user key input directly in ImGui, verifies online via httplib,
 * and seamlessly transitions into the driver check state.
 */
class LicensePopup {
public:
    static bool s_IsOpen;
    static bool s_IsVerified;
    static char s_KeyBuffer[64];
    static char s_StatusMessage[128];
    static bool s_IsVerifying;
    static std::string s_Hwid;

    static void Open(const std::string& hwid = "") {
        s_IsOpen = true;
        s_IsVerifying = false;
        memset(s_StatusMessage, 0, sizeof(s_StatusMessage));
        if (!hwid.empty()) s_Hwid = hwid;
    }

    static bool IsVerified() {
        return s_IsVerified;
    }

    static void SetVerified(bool v) {
        s_IsVerified = v;
        if (v) s_IsOpen = false;
    }

    static void Render(const char* brandId, ImVec4 themeColor, std::function<void()> onSuccessCallback) {
        if (!s_IsOpen || s_IsVerified) return;

        ImGui::OpenPopup("NINMODS License Verification##Modal");

        ImGui::SetNextWindowSize(ImVec2(320, 230), ImGuiCond_Appearing);
        ImGui::SetNextWindowPos(ImVec2(ImGui::GetIO().DisplaySize.x * 0.5f, ImGui::GetIO().DisplaySize.y * 0.5f), ImGuiCond_Appearing, ImVec2(0.5f, 0.5f));

        if (ImGui::BeginPopupModal("NINMODS License Verification##Modal", NULL, ImGuiWindowFlags_AlwaysAutoResize | ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoCollapse)) {
            ImGui::TextColored(themeColor, "TACTICAL LICENSE ACCESS");
            ImGui::Separator();
            ImGui::Spacing();

            ImGui::TextDisabled("Enter your VIP subscription key:");
            ImGui::PushItemWidth(-1);
            ImGui::InputTextWithHint("##KeyInput", "Paste license key here...", s_KeyBuffer, IM_ARRAYSIZE(s_KeyBuffer));
            ImGui::PopItemWidth();

            if (strlen(s_StatusMessage) > 0) {
                ImGui::Spacing();
                ImGui::TextColored(ImVec4(1.0f, 0.35f, 0.35f, 1.0f), "%s", s_StatusMessage);
            }

            ImGui::Spacing();
            ImGui::Separator();
            ImGui::Spacing();

            if (s_IsVerifying) {
                ImGui::TextColored(ImVec4(0.0f, 0.85f, 1.0f, 1.0f), "Verifying with server database...");
            } else {
                ImGui::PushStyleColor(ImGuiCol_Button, themeColor);
                if (ImGui::Button("VERIFY LICENSE", ImVec2(140, 36))) {
                    if (strlen(s_KeyBuffer) < 4) {
                        strncpy(s_StatusMessage, "Please enter a valid key.", sizeof(s_StatusMessage) - 1);
                    } else {
                        s_IsVerifying = true;
                        std::string keyStr(s_KeyBuffer);
                        std::string bId(brandId ? brandId : "nin");
                        std::string hw = s_Hwid;

                        // Verify in async thread so ImGui UI remains 120 FPS responsive
                        std::thread([keyStr, bId, hw, onSuccessCallback]() {
                            try {
                                httplib::Client cli("https://ninmods.smmtor.com");
                                cli.set_connection_timeout(5, 0);
                                cli.set_read_timeout(5, 0);

                                nlohmann::json payload;
                                payload["licenseKey"] = keyStr;
                                payload["brand"] = bId;
                                payload["hwid"] = hw;

                                auto res = cli.Post("/api/v2/license/verify", payload.dump(), "application/json");
                                if (res && res->status == 200) {
                                    auto respJson = nlohmann::json::parse(res->body);
                                    if (respJson.value("success", false) || respJson.value("status", false)) {
                                        s_IsVerified = true;
                                        s_IsOpen = false;
                                        s_IsVerifying = false;
                                        if (onSuccessCallback) onSuccessCallback();
                                        return;
                                    } else {
                                        std::string err = respJson.value("error", respJson.value("message", "Invalid key"));
                                        strncpy(s_StatusMessage, err.c_str(), sizeof(s_StatusMessage) - 1);
                                    }
                                } else {
                                    strncpy(s_StatusMessage, "Server unreachable. Check internet.", sizeof(s_StatusMessage) - 1);
                                }
                            } catch (...) {
                                strncpy(s_StatusMessage, "Verification error. Please retry.", sizeof(s_StatusMessage) - 1);
                            }
                            s_IsVerifying = false;
                        }).detach();
                    }
                }
                ImGui::PopStyleColor();

                ImGui::SameLine();
                if (ImGui::Button("CANCEL", ImVec2(100, 36))) {
                    s_IsOpen = false;
                    ImGui::CloseCurrentPopup();
                }
            }

            ImGui::EndPopup();
        }
    }
};

inline bool LicensePopup::s_IsOpen = false;
inline bool LicensePopup::s_IsVerified = false;
inline char LicensePopup::s_KeyBuffer[64] = "";
inline char LicensePopup::s_StatusMessage[128] = "";
inline bool LicensePopup::s_IsVerifying = false;
inline std::string LicensePopup::s_Hwid = "";
