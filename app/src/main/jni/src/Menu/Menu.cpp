#include "Menu/Menu.hpp"
#include "Menu/FeatureTable.h"
#include "Includes/Logger.h"
#include "Includes/Utils.h"
#include "Includes/MemoryIO.h"
#include "Includes/obfuscate.h"
#include "Engine/DaemonLink.h"

#include <string>
#include <vector>

/**
 * @brief Native icon of the collapsed menu.
 *
 * NINMODS is a white label app, so the icon must come from the app resources
 * of every build. The native side therefore reports "no native asset" and the
 * Java layer falls back to the launcher icon of the build.
 *
 * @param env   JNI environment
 * @param thiz  calling instance
 * @return always nullptr, the Java layer renders the app icon
 */
jstring Icon(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    return nullptr;
}

/**
 * @brief Native WebView animation of the collapsed menu.
 *
 * A white label build carries its own animation in the app resources, so the
 * native side reports "no native asset" as well.
 *
 * @param env   JNI environment
 * @param thiz  calling instance
 * @return always nullptr, the Java layer renders the app asset
 */
jstring IconWebViewData(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    return nullptr;
}

/**
 * @brief Builds the live engine status of the menu marquee.
 *
 * The text only contains facts that were observed:
 *   - the RT channel reports the driver node, the descriptor and whether an
 *     IOCTL was verified (Includes/MemoryIO.h),
 *   - the QX channel reports whether the root daemon is connected,
 *   - the target pid is the pid the engine is really attached to.
 *
 * @return the status line of the engine
 */
static std::string BuildEngineStatus() {
    std::string status = "Engine: ";
    status += MemoryIO::DescribeChannel();
    status += " | QX Daemon: ";
    status += NinEngine::DaemonLink::Instance().IsConnected() ? "connected" : "not running";

    pid_t pid = target_pid.load(std::memory_order_relaxed);
    if (pid <= 0) {
        pid = FindGamePid();
    }
    status += " | Target PID: ";
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

/**
 * @brief Initializes the native menu.
 *
 * The call is the last step of the menu construction. The marquee subtitle
 * carries the live engine status and the user is warned when no target
 * process is attached.
 *
 * @param env      JNI environment
 * @param thiz     calling instance
 * @param ctx      application context used for the toast
 * @param title    title of the menu, unused by the native layer
 * @param subtitle subtitle that receives the engine status
 */
void Init(JNIEnv *env, jobject thiz, jobject ctx, jobject title, jobject subtitle) {
    (void) thiz;
    (void) title;

    std::string status = BuildEngineStatus();
    setText(env, subtitle, status.c_str());

    pid_t pid = target_pid.load(std::memory_order_relaxed);
    if (pid <= 0) {
        pid = FindGamePid();
    }
    if (pid <= 0) {
        Toast(env, ctx,
              "No target game detected. Start the game, then toggle the features.",
              TOAST_LENGTH_LONG);
    }
}

/**
 * @brief Builds the settings page of the menu.
 *
 * The indices are the reserved ones of the menu system:
 *   -1  save feature preferences
 *   -3  auto size vertically
 *   -6  close the settings page
 *
 * @param env           JNI environment
 * @param activityObject calling instance
 * @return the settings rows of the menu
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

/**
 * @brief Builds the feature page of the menu.
 *
 * The whole structure comes from the native feature table, the Java layer
 * only renders the rows it receives. The live engine status is appended as
 * the last read only row.
 *
 * @param env     JNI environment
 * @param context application context
 * @return the feature rows of the menu
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

/**
 * @brief Reports whether the game library of the target process is mapped.
 *
 * The menu uses this gate to hold back the stored preferences until the
 * game is really there.
 *
 * @param env   JNI environment
 * @param thiz  calling instance
 * @return JNI_TRUE when the engine resolved the game library
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
