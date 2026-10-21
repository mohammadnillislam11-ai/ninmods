#pragma once
#include <jni.h>

class Switch {
public:
    void create(JNIEnv* env, const char* name, jint ID) {
        jclass CMenu = env->FindClass("nin/app/overlay/Floater");
        if (!CMenu) {
            env->ExceptionClear();
            CMenu = env->FindClass("ninmods/pro/Floater");
        }
        if (!CMenu) {
            env->ExceptionClear();
            CMenu = env->FindClass("com/thiago/php/Floater");
        }
        if (!CMenu) return;
        jmethodID MSwitch = env->GetStaticMethodID(CMenu, "addSwitch", "(Ljava/lang/String;I)V");
        if (!MSwitch) return;
        env->CallStaticVoidMethod(CMenu, MSwitch, env->NewStringUTF(name), ID);
    }
};