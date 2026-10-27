#ifndef NIN_JAVA_SHELL_H
#define NIN_JAVA_SHELL_H

#include <jni.h>
#include <string>

extern "C" JavaVM *g_JavaVM;

/**
 * Runs a shell script as root by handing it over to the Java side.
 *
 * WHY NOT JUST spawn() FROM NATIVE CODE
 * --------------------------------------
 * bionic's system(), popen() and exec*() all need a plain fork(). The
 * Android app sandbox does not allow that clone() variant: the seccomp
 * filter of the app zygote answers it with SIGSYS and the whole process
 * dies. That is what crashed the app on rooted devices.
 *
 * Runtime.exec() on the Java side uses posix_spawn() - the
 * clone(CLONE_VM|CLONE_VFORK) variant the sandbox allows - so the spawn has
 * to happen in Java. This helper calls NativeBridge.runAsRoot(String) for
 * that.
 *
 * The pattern (GetEnv / AttachCurrentThread / FindClass /
 * CallStaticIntMethod / DetachCurrentThread) is the same one that
 * Includes/UI/Popups/LicensePopup.h already uses to talk to Java.
 *
 * @param script the shell script to run as root
 * @return the exit code of the script, or -1 when it could not run
 */
inline int RunRootScriptViaJava(const std::string &script) {
    if (!g_JavaVM) {
        return -1;
    }

    JNIEnv *jEnv = nullptr;
    bool attached = false;

    if (g_JavaVM->GetEnv((void **)&jEnv, JNI_VERSION_1_6) != JNI_OK) {
        if (g_JavaVM->AttachCurrentThread(&jEnv, nullptr) != JNI_OK) {
            return -1;
        }
        attached = true;
    }

    int result = -1;

    jclass clazz = jEnv->FindClass("com/ninmods/main/core/NativeBridge");
    if (clazz != nullptr) {
        jmethodID method = jEnv->GetStaticMethodID(clazz, "runAsRoot", "(Ljava/lang/String;)I");
        if (method != nullptr) {
            jstring jScript = jEnv->NewStringUTF(script.c_str());
            if (jScript != nullptr) {
                result = (int)jEnv->CallStaticIntMethod(clazz, method, jScript);
                jEnv->DeleteLocalRef(jScript);
            }
        }
        jEnv->DeleteLocalRef(clazz);
    }

    if (jEnv->ExceptionCheck()) {
        jEnv->ExceptionClear();
    }

    if (attached) {
        g_JavaVM->DetachCurrentThread();
    }

    return result;
}

#endif // NIN_JAVA_SHELL_H
