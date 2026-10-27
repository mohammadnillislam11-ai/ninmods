#include "Menu/Menu.hpp"
#include "Menu/FeatureTable.h"
#include "Includes/Logger.h"
#include "Includes/Utils.h"
#include "Includes/obfuscate.h"

#include <string>
#include <vector>

/*
 * The reference mod menu ships a base64 encoded PNG as the collapsed icon
 * and optionally a WebView data URI for an animated GIF.
 *
 * NINMODS is a white label app, so the icon must come from the app
 * resources (every white label build carries its own launcher icon) and the
 * native side must not embed a foreign asset. Both methods therefore report
 * "no native asset" - exactly the disabled path of the reference
 * implementation - and the Java layer falls back to the app icon.
 */
jstring Icon(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    return nullptr;
}

jstring IconWebViewData(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    return nullptr;
}

/*
 * Live status of the engine. It is written into the marquee subtitle of the
 * menu, which is the same place where the reference menu puts its native
 * status text.
 *
 * Only real values are reported: the driver channel that is currently open,
 * the device node it is attached to and the pid of the game process.
 */
static std::string BuildEngineStatus() {
    std::string status = "Engine: ";

    if (driver != nullptr && driver->isConnected()) {
        status += driver->getDriverModeName();
        const char *node = driver->getNodeName();
        if (node != nullptr && node[0] != '\0') {
            status += " [";
            status += node;
            status += "]";
        }
    } else {
        status += "kernel driver offline";
    }

    pid_t pid = target_pid.load(std::memory_order_relaxed);
    if (pid <= 0) {
        pid = FindGamePid();
    }
    status += " | Game PID: ";
    status += (pid > 0) ? std::to_string(static_cast<long long>(pid)) : std::string("not detected");

    /* The DSL parser splits a row on "_" - the status text must not carry
     * an underscore or the row would be cut into pieces. */
    for (size_t i = 0; i < status.size(); i++) {
        if (status[i] == '_') {
            status[i] = '-';
        }
    }
    return status;
}

/*
 * Init is called by the Java layer as the last step of the menu
 * construction. The reference implementation uses it to publish the native
 * branding, a welcome dialog and a toast.
 *
 * NINMODS keeps the same flow but with real, dynamic information: the
 * marquee subtitle carries the live engine status and the user is warned
 * when the kernel driver is not connected (without a driver the engine
 * stays completely idle).
 */
void Init(JNIEnv *env, jobject thiz, jobject ctx, jobject title, jobject subtitle) {
    (void) thiz;
    (void) title;

    std::string status = BuildEngineStatus();
    setText(env, subtitle, status.c_str());

    if (driver == nullptr || !driver->isConnected()) {
        Toast(env, ctx,
              "Kernel driver is not connected. The engine stays idle until the driver is loaded.",
              TOAST_LENGTH_LONG);
    }
}

/*
 * The settings page of the menu. The indices are the reserved ones of the
 * reference system:
 *   -1  save feature preferences
 *   -3  auto size vertically
 *   -6  close the settings page
 */
jobjectArray SettingsList(JNIEnv *env, jobject activityObject) {
    (void) activityObject;

    const char *settings[] = {
            OBFUSCATE("Category_Settings"),
            OBFUSCATE("-1_Toggle_Save feature preferences"),
            OBFUSCATE("-3_Toggle_Auto size vertically"),
            OBFUSCATE("Category_Menu"),
            OBFUSCATE("-6_Button_<font color='red'>Close settings</font>"),
    };

    int total = static_cast<int>(sizeof(settings) / sizeof(settings[0]));
    jobjectArray ret = static_cast<jobjectArray>(
            env->NewObjectArray(total, env->FindClass(OBFUSCATE("java/lang/String")),
                                env->NewStringUTF("")));
    if (ret == nullptr) {
        return nullptr;
    }
    for (int i = 0; i < total; i++) {
        env->SetObjectArrayElement(ret, i, env->NewStringUTF(settings[i]));
    }
    return ret;
}

/*
 * The mod page of the menu. The whole structure comes from the native
 * feature table, the Java layer only renders the rows it receives.
 */
jobjectArray GetFeatureList(JNIEnv *env, jobject context) {
    (void) context;

    std::vector<std::string> rows = NinMenuDsl::BuildFeatureDsl();
    rows.push_back(std::string("Category_") + "SYSTEM");
    rows.push_back(std::string("RichTextView_") + BuildEngineStatus());

    jobjectArray ret = static_cast<jobjectArray>(
            env->NewObjectArray(static_cast<int>(rows.size()),
                                env->FindClass(OBFUSCATE("java/lang/String")),
                                env->NewStringUTF("")));
    if (ret == nullptr) {
        return nullptr;
    }
    for (size_t i = 0; i < rows.size(); i++) {
        env->SetObjectArrayElement(ret, i, env->NewStringUTF(rows[i].c_str()));
    }
    return ret;
}

/*
 * True as soon as the game library of the target process is mapped, i.e.
 * the engine is able to resolve its offsets and to touch the game.
 *
 * The menu uses this gate to hold back the stored preferences until the
 * game is really there - the same gate the reference menu uses with its
 * libil2cpp.so check.
 */
jboolean IsGameLibLoaded(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;

    pid_t pid = target_pid.load(std::memory_order_relaxed);
    if (pid <= 0) {
        pid = FindGamePid();
    }
    if (pid <= 0) {
        return JNI_FALSE;
    }
    return (libAddress.load(std::memory_order_relaxed) != 0) ? JNI_TRUE : JNI_FALSE;
}
