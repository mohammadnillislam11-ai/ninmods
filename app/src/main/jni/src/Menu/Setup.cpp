#include "Menu/Menu.hpp"
#include "Includes/obfuscate.h"

/*
 * Registration of the native methods of the mod menu.
 *
 * The binding is done with RegisterNatives instead of the long
 * Java_com_ninmods_main_* symbol names, so the native entry points stay
 * short and the binding is checked by the VM at load time.
 */

int RegisterNinMenu(JNIEnv *env) {
    JNINativeMethod methods[] = {
            {OBFUSCATE("Icon"), OBFUSCATE("()Ljava/lang/String;"),
             reinterpret_cast<void *>(Icon)},
            {OBFUSCATE("IconWebViewData"), OBFUSCATE("()Ljava/lang/String;"),
             reinterpret_cast<void *>(IconWebViewData)},
            {OBFUSCATE("IsGameLibLoaded"), OBFUSCATE("()Z"),
             reinterpret_cast<void *>(IsGameLibLoaded)},
            {OBFUSCATE("Init"),
             OBFUSCATE("(Landroid/content/Context;Landroid/widget/TextView;Landroid/widget/TextView;)V"),
             reinterpret_cast<void *>(Init)},
            {OBFUSCATE("SettingsList"), OBFUSCATE("()[Ljava/lang/String;"),
             reinterpret_cast<void *>(SettingsList)},
            {OBFUSCATE("GetFeatureList"), OBFUSCATE("()[Ljava/lang/String;"),
             reinterpret_cast<void *>(GetFeatureList)},
    };

    jclass clazz = env->FindClass(OBFUSCATE("com/ninmods/main/menu/ModMenu"));
    if (!clazz) {
        return JNI_ERR;
    }
    if (env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0])) != 0) {
        return JNI_ERR;
    }
    return JNI_OK;
}

int RegisterNinMenuPreferences(JNIEnv *env) {
    JNINativeMethod methods[] = {
            {OBFUSCATE("Changes"),
             OBFUSCATE("(Landroid/content/Context;ILjava/lang/String;IJZLjava/lang/String;)V"),
             reinterpret_cast<void *>(Changes)},
    };

    jclass clazz = env->FindClass(OBFUSCATE("com/ninmods/main/menu/MenuPreferences"));
    if (!clazz) {
        return JNI_ERR;
    }
    if (env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0])) != 0) {
        return JNI_ERR;
    }
    return JNI_OK;
}
