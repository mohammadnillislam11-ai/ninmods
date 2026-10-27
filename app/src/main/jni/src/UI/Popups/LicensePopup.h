#pragma once

#include <imgui.h>
#include <string>
#include <functional>
#include <cstring>
#include <algorithm>
#include <atomic>
#include <mutex>
#include <jni.h>
#include "../../Icon/TextureLoader.h"
#include "../../Includes/Logger.h"

extern "C" JavaVM* g_JavaVM;

/**
 * Global reference to com/ninmods/main/core/NativeBridge.
 *
 * The helpers below run on the GL render thread. A thread that was attached
 * through AttachCurrentThread() uses the SYSTEM class loader, so
 * FindClass("com/ninmods/main/core/NativeBridge") always failed there and
 * left an uncleared pending exception on the thread (which makes every
 * later JNI call on it undefined). The class is therefore resolved ONCE on
 * the JNI thread (which has the app class loader) and cached as a global
 * reference.
 */
extern jclass g_NativeBridgeClassRef;

inline jclass ResolveNativeBridgeClass(JNIEnv* jEnv) {
    if (g_NativeBridgeClassRef != nullptr) {
        return g_NativeBridgeClassRef;
    }
    jclass local = jEnv->FindClass("com/ninmods/main/core/NativeBridge");
    if (local == nullptr) {
        if (jEnv->ExceptionCheck()) jEnv->ExceptionClear();
        return nullptr;
    }
    g_NativeBridgeClassRef = (jclass)jEnv->NewGlobalRef(local);
    jEnv->DeleteLocalRef(local);
    return g_NativeBridgeClassRef;
}

// JNI Helpers to interface with Android NativeBridge
inline std::string GetClipboardTextNative() {
    if (!g_JavaVM) return "";
    JNIEnv* jEnv = nullptr;
    bool attached = false;
    if (g_JavaVM->GetEnv((void**)&jEnv, JNI_VERSION_1_6) != JNI_OK) {
        if (g_JavaVM->AttachCurrentThread(&jEnv, nullptr) == JNI_OK) attached = true;
    }
    std::string result = "";
    if (jEnv) {
        jclass bridgeClass = ResolveNativeBridgeClass(jEnv);
        if (bridgeClass) {
            jmethodID mid = jEnv->GetStaticMethodID(bridgeClass, "getClipboardText", "()Ljava/lang/String;");
            if (mid) {
                jstring jStr = (jstring)jEnv->CallStaticObjectMethod(bridgeClass, mid);
                if (jEnv->ExceptionCheck()) jEnv->ExceptionClear();
                if (jStr) {
                    const char* chars = jEnv->GetStringUTFChars(jStr, nullptr);
                    if (chars) {
                        result = chars;
                        jEnv->ReleaseStringUTFChars(jStr, chars);
                    }
                    jEnv->DeleteLocalRef(jStr);
                }
            }
        }
        if (attached) g_JavaVM->DetachCurrentThread();
    }
    return result;
}

inline void ShowKeyInputDialogNative(const std::string& currentKey) {
    if (!g_JavaVM) return;
    JNIEnv* jEnv = nullptr;
    bool attached = false;
    if (g_JavaVM->GetEnv((void**)&jEnv, JNI_VERSION_1_6) != JNI_OK) {
        if (g_JavaVM->AttachCurrentThread(&jEnv, nullptr) == JNI_OK) attached = true;
    }
    if (jEnv) {
        jclass bridgeClass = ResolveNativeBridgeClass(jEnv);
        if (bridgeClass) {
            jmethodID mid = jEnv->GetStaticMethodID(bridgeClass, "showKeyInputDialog", "(Ljava/lang/String;)V");
            if (mid) {
                jstring jK = jEnv->NewStringUTF(currentKey.c_str());
                jEnv->CallStaticVoidMethod(bridgeClass, mid, jK);
                if (jEnv->ExceptionCheck()) jEnv->ExceptionClear();
                jEnv->DeleteLocalRef(jK);
            }
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
    /* The three state flags are written from the JNI thread (licence result)
     * and read on the GL render thread. Plain bools made that a data race. */
    static std::atomic<bool> s_IsOpen;
    static std::atomic<bool> s_IsVerified;
    static std::atomic<bool> s_IsVerifying;
    /* The key buffer and the status message are written from the JNI thread
     * (setKeyFromJava / onLicenseResult) AND read/written by ImGui on the GL
     * render thread. They are therefore guarded by s_StateMutex. */
    static char s_KeyBuffer[64];
    static char s_StatusMessage[128];
    /* Render-thread owned copies. ImGui writes into the key buffer it is
     * handed, so it may never be the shared buffer. */
    static char s_renderKeyBuffer[64];
    static char s_renderStatusBuffer[128];
    static std::string s_Hwid;
    static std::string s_ServerUrl;
    /* The verify handler is installed from the JNI thread (launchBrandOverlay)
     * and invoked on the GL render thread. */
    static std::function<void(const std::string& key, const std::string& brand)> s_VerifyHandler;
    static std::mutex s_StateMutex;

    static void Open(const std::string& hwid = "", const std::string& prefillKey = "", const std::string& serverUrl = "") {
        s_IsOpen.store(true, std::memory_order_relaxed);
        s_IsVerifying.store(false, std::memory_order_relaxed);
        SetStatusMessage("");

        /* Decide what to do with the key buffer FIRST, without holding the
         * lock: the clipboard read below calls into Java, and re-entering the
         * JNI layer while s_StateMutex is held would deadlock (std::mutex is
         * NOT recursive). */
        bool keyEmpty = false;
        {
            std::lock_guard<std::mutex> lock(s_StateMutex);
            if (!hwid.empty()) s_Hwid = hwid;
            if (!serverUrl.empty()) s_ServerUrl = serverUrl;
            keyEmpty = (s_KeyBuffer[0] == '\0');
        }

        std::string autoKey;
        bool useAutoKey = false;
        if (keyEmpty && prefillKey.empty()) {
            /* Auto-check clipboard for a valid key (>=6 chars, no newlines). */
            std::string clip = GetClipboardTextNative();
            if (clip.length() >= 6 && clip.length() < 64 &&
                clip.find('\n') == std::string::npos &&
                clip.find(' ') == std::string::npos) {
                autoKey = clip;
                useAutoKey = true;
            }
        }

        if (keyEmpty && (!prefillKey.empty() || useAutoKey)) {
            std::lock_guard<std::mutex> lock(s_StateMutex);
            /* Re-check: another thread may have filled the buffer meanwhile. */
            if (s_KeyBuffer[0] == '\0') {
                CopyIntoBufferLocked(s_KeyBuffer, sizeof(s_KeyBuffer),
                                     useAutoKey ? autoKey : prefillKey);
            }
        }
    }

    /* Copies a string into a fixed char buffer and ALWAYS terminates it.
     * strncpy() with sizeof-1 never writes the terminator, so a longer key
     * left the tail of the previous key in the buffer. */
    static void CopyIntoBufferLocked(char* dst, size_t dstSize, const std::string& src) {
        memset(dst, 0, dstSize);
        if (src.empty()) return;
        strncpy(dst, src.c_str(), dstSize - 1);
        dst[dstSize - 1] = '\0';
    }

    /* Writes the key buffer and ALWAYS terminates it. */
    static void SetKey(const std::string& key) {
        std::lock_guard<std::mutex> lock(s_StateMutex);
        CopyIntoBufferLocked(s_KeyBuffer, sizeof(s_KeyBuffer), key);
    }

    static void SetStatusMessage(const char* message) {
        std::lock_guard<std::mutex> lock(s_StateMutex);
        memset(s_StatusMessage, 0, sizeof(s_StatusMessage));
        if (message && message[0] != '\0') {
            strncpy(s_StatusMessage, message, sizeof(s_StatusMessage) - 1);
        }
        s_StatusMessage[sizeof(s_StatusMessage) - 1] = '\0';
    }

    static bool IsOpen() {
        return s_IsOpen.load(std::memory_order_relaxed) &&
               !s_IsVerified.load(std::memory_order_relaxed);
    }

    static bool IsVerified() {
        return s_IsVerified.load(std::memory_order_relaxed);
    }

    static void SetVerified(bool v) {
        s_IsVerified.store(v, std::memory_order_relaxed);
        if (v) s_IsOpen.store(false, std::memory_order_relaxed);
    }

    static void SetVerificationResult(bool success, const char* message) {
        s_IsVerifying.store(false, std::memory_order_relaxed);
        if (success) {
            s_IsVerified.store(true, std::memory_order_relaxed);
            s_IsOpen.store(false, std::memory_order_relaxed);
            SetStatusMessage("");
        } else {
            if (message && message[0] != '\0') {
                SetStatusMessage(message);
            } else {
                SetStatusMessage("Invalid subscription key.");
            }
        }
    }

    static void SetVerifyHandler(std::function<void(const std::string& key, const std::string& brand)> handler) {
        std::lock_guard<std::mutex> lock(s_StateMutex);
        s_VerifyHandler = handler;
    }

    static void Render(const char* brandId, ImVec4 themeColor, std::function<void()> onSuccessCallback) {
        if (!s_IsOpen.load(std::memory_order_relaxed) ||
            s_IsVerified.load(std::memory_order_relaxed)) {
            return;
        }
        /* ImGui needs a plain bool* for its open/close flag. */
        bool modalOpen = true;

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

        if (ImGui::Begin("##NINMODS_LICENSE_MODAL", &modalOpen, winFlags)) {
            ImVec2 winPos = ImGui::GetWindowPos();
            ImVec2 winSize = ImGui::GetWindowSize();
            ImVec2 mousePos = ImGui::GetIO().MousePos;
            ImDrawList* drawList = ImGui::GetWindowDrawList();

            float headerH = 46.0f;
            ImVec2 closeBtnPos(winPos.x + winSize.x - 36.0f, winPos.y + (headerH - 24.0f) * 0.5f);
            bool overClose = (mousePos.x >= closeBtnPos.x && mousePos.x <= closeBtnPos.x + 26.0f &&
                              mousePos.y >= closeBtnPos.y && mousePos.y <= closeBtnPos.y + 26.0f);

            if (ImGui::IsMouseClicked(0) && overClose &&
                !s_IsVerifying.load(std::memory_order_relaxed)) {
                modalOpen = false;
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
            /* ImGui writes DIRECTLY into the buffer that is handed to it, so the
             * buffer it owns must belong to the render thread alone. The shared
             * s_KeyBuffer is written from the JNI thread (setKeyFromJava), so
             * the two are synchronised through the mutex:
             *   shared -> local  (before the widget)
             *   local  -> shared (after the widget)
             * Without that, ImGui and the JNI thread wrote the same bytes
             * concurrently. */
            {
                std::lock_guard<std::mutex> lock(s_StateMutex);
                strncpy(s_renderKeyBuffer, s_KeyBuffer, sizeof(s_renderKeyBuffer) - 1);
                s_renderKeyBuffer[sizeof(s_renderKeyBuffer) - 1] = '\0';
            }
            ImGui::InputTextWithHint("##KeyInput", "Paste license key here...",
                                     s_renderKeyBuffer, sizeof(s_renderKeyBuffer));
            {
                std::lock_guard<std::mutex> lock(s_StateMutex);
                strncpy(s_KeyBuffer, s_renderKeyBuffer, sizeof(s_KeyBuffer) - 1);
                s_KeyBuffer[sizeof(s_KeyBuffer) - 1] = '\0';
            }
            if (ImGui::IsItemClicked()) {
                ShowKeyInputDialogNative(s_renderKeyBuffer);
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
                    SetKey(clip);
                    SetStatusMessage("");
                } else {
                    SetStatusMessage("Clipboard is empty.");
                }
            }
            ImGui::PopStyleColor();

            ImGui::SameLine();

            // 2. Open Native Android Virtual Keyboard Dialog
            ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.20f, 0.30f, 0.45f, 0.80f));
            if (ImGui::Button("[TYPE KEYBOARD]", ImVec2(btnHalfW, 36))) {
                ShowKeyInputDialogNative(s_renderKeyBuffer);
            }
            ImGui::PopStyleColor();
            ImGui::PopStyleVar();

            // Status message
            {
                std::lock_guard<std::mutex> lock(s_StateMutex);
                strncpy(s_renderStatusBuffer, s_StatusMessage, sizeof(s_renderStatusBuffer) - 1);
                s_renderStatusBuffer[sizeof(s_renderStatusBuffer) - 1] = '\0';
            }
            if (s_renderStatusBuffer[0] != '\0') {
                ImGui::Spacing();
                ImGui::TextColored(ImVec4(1.0f, 0.35f, 0.35f, 1.0f), "[ERROR] %s", s_renderStatusBuffer);
            }

            ImGui::Spacing();
            ImGui::Separator();
            ImGui::Spacing();

            // Action Row 2: Verify & Close
            if (s_IsVerifying.load(std::memory_order_relaxed)) {
                ImGui::TextColored(ImVec4(0.2f, 0.8f, 1.0f, 1.0f), "Verifying subscription with server...");
            } else {
                float verifyW = availW * 0.65f;
                float closeW = availW - verifyW - 8.0f;

                ImGui::PushStyleColor(ImGuiCol_Button, themeColor);
                ImGui::PushStyleColor(ImGuiCol_ButtonHovered, ImVec4(themeColor.x * 1.15f, themeColor.y * 1.15f, themeColor.z * 1.15f, 1.0f));
                ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, 8.0f);

                if (ImGui::Button("VERIFY LICENSE", ImVec2(verifyW, 38))) {
                    /* Snapshot the key and the handler under the lock so that
                     * a concurrent setKeyFromJava() / SetVerifyHandler() call
                     * from the JNI thread cannot pull the data away while the
                     * render thread is using it. */
                    std::string keyStr;
                    std::function<void(const std::string&, const std::string&)> handler;
                    {
                        std::lock_guard<std::mutex> lock(s_StateMutex);
                        /* strnlen() is POSIX, not ISO C++, and it is missing from
                         * the Android IDE stub headers, so the bounded length
                         * is computed locally instead. */
                        size_t keyLen = 0;
                        while (keyLen < sizeof(s_KeyBuffer) && s_KeyBuffer[keyLen] != '\0') {
                            ++keyLen;
                        }
                        keyStr.assign(s_KeyBuffer, keyLen);
                        handler = s_VerifyHandler;
                    }
                    std::string bId(brandId ? brandId : "nin");

                    if (keyStr.length() < 4) {
                        SetStatusMessage("Please enter a valid key.");
                    } else {
                        s_IsVerifying.store(true, std::memory_order_relaxed);

                        if (handler) {
                            handler(keyStr, bId);
                        } else {
                            /* No handler installed: the popup must not claim
                             * success on its own (rule 1/2: no fake state). */
                            s_IsVerifying.store(false, std::memory_order_relaxed);
                            SetStatusMessage("Verification handler is not available.");
                        }
                    }
                }
                ImGui::PopStyleVar();
                ImGui::PopStyleColor(2);

                ImGui::SameLine();
                if (ImGui::Button("CLOSE", ImVec2(closeW, 38))) {
                    modalOpen = false;
                }
            }
        }
        ImGui::End();
        ImGui::PopStyleColor(2);
        ImGui::PopStyleVar(2);

        if (!modalOpen) {
            /* Closing the modal must not leave the overlay in a dead state:
             * the controller used to render nothing at all afterwards and the
             * menu became unreachable for the rest of the session. */
            s_IsOpen.store(false, std::memory_order_relaxed);
        }
    }
};

inline std::atomic<bool> LicensePopup::s_IsOpen{false};
inline std::atomic<bool> LicensePopup::s_IsVerified{false};
inline std::atomic<bool> LicensePopup::s_IsVerifying{false};
inline char LicensePopup::s_KeyBuffer[64] = "";
inline char LicensePopup::s_StatusMessage[128] = "";
inline char LicensePopup::s_renderKeyBuffer[64] = "";
inline char LicensePopup::s_renderStatusBuffer[128] = "";
inline std::string LicensePopup::s_Hwid = "";
inline std::string LicensePopup::s_ServerUrl = "";
inline std::function<void(const std::string& key, const std::string& brand)> LicensePopup::s_VerifyHandler = nullptr;
inline std::mutex LicensePopup::s_StateMutex;
