#ifndef NIN_JAVA_SHELL_H
#define NIN_JAVA_SHELL_H

#include <jni.h>
#include <string>

extern "C" JavaVM *g_JavaVM;

/*
 * Cached global reference to com/ninmods/main/core/NativeBridge.
 * Defined in Client.cpp.
 *
 * It is resolved on a JNI thread, never on an attached native thread: a
 * thread attached through AttachCurrentThread() uses the SYSTEM class
 * loader, so FindClass() there can never see the app classes and it leaves
 * an uncleared pending exception behind, which makes every later JNI call
 * on that thread undefined. The very same reasoning and the very same
 * global are used by UI/Popups/LicensePopup.h.
 */
extern jclass g_NativeBridgeClassRef;

/**
 * Runs a shell script as root by handing it over to the Java side.
 *
 * WHY NOT spawn() FROM NATIVE CODE
 * ----------------------------------
 * bionic's system(), popen() and exec*() all need a plain fork(). The
 * Android app sandbox does not allow that clone() variant: the seccomp
 * filter of the app zygote answers it with SIGSYS and the whole process
 * dies. That is what crashed the app on rooted devices.
 *
 * Runtime.exec() on the Java side uses posix_spawn() - the
 * clone(CLONE_VM|CLONE_VFORK) variant the sandbox allows - so the spawn has
 * to happen in Java and this helper calls NativeBridge.runAsRoot(String).
 *
 * The script itself is the NINLITE root shell / SELinux policy / daemon
 * launch, and it only ever runs from the "shell start" button of the
 * NINLITE menu (NinLiteFloatingMenuView.startRealShellAndEngine).
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

    /* The class reference is cached on a JNI thread, so it is usable from
     * this attached native thread as well. */
    jclass clazz = g_NativeBridgeClassRef;
    if (clazz != nullptr) {
        jmethodID method = jEnv->GetStaticMethodID(clazz, "runAsRoot", "(Ljava/lang/String;)I");
        if (method != nullptr) {
            jstring jScript = jEnv->NewStringUTF(script.c_str());
            if (jScript != nullptr) {
                result = (int)jEnv->CallStaticIntMethod(clazz, method, jScript);
                jEnv->DeleteLocalRef(jScript);
            }
        }
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
