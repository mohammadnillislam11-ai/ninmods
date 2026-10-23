#pragma once
#include <jni.h>

class Category {
public:
    void create(JNIEnv* env, const char* name) {
        jclass CMenu = env->FindClass("com/ninmods/main/overlay/Floater");
        if (!CMenu) {
            env->ExceptionClear();
            CMenu = env->FindClass("ninmods/pro/Floater");
        }
        if (!CMenu) {
            env->ExceptionClear();
            CMenu = env->FindClass("com/thiago/php/Floater");
        }
        if (!CMenu) return;
        jmethodID MCategory = env->GetStaticMethodID(CMenu, "addCategory", "(Ljava/lang/String;)V");
        if (!MCategory) return;
        env->CallStaticVoidMethod(CMenu, MCategory, env->NewStringUTF(name));
    }
};