#pragma once
#include <jni.h>

class SeekBar {
public:
    void create(JNIEnv* env, const char* name, jint value, jint max, const char* type, jint ID) {
        jclass CMenu = env->FindClass("com/ninmods/main/overlay/Floater");
        if (!CMenu) {
            env->ExceptionClear();
            CMenu = env->FindClass("power/cheats/Floater");
        }
        if (!CMenu) {
            env->ExceptionClear();
            CMenu = env->FindClass("ninmods/pro/Floater");
        }
        if (!CMenu) return;
        jmethodID MSeekBar = env->GetStaticMethodID(CMenu, "addSeekBar", "(Ljava/lang/String;IILjava/lang/String;I)V");
        if (!MSeekBar) return;
        env->CallStaticVoidMethod(CMenu, MSeekBar, env->NewStringUTF(name), value, max, env->NewStringUTF(type), ID);
    }
};