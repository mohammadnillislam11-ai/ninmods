#include "Menu/Menu.hpp"
#include "Includes/obfuscate.h"

/*
 * Small native -> java helpers. They are the direct counterpart of the
 * helper file of the reference mod menu: the native layer can set a text,
 * raise a toast and show a dialog with a link.
 */

void Toast(JNIEnv *env, jobject ctx, const char *text, int length) {
    if (ctx == nullptr || text == nullptr) {
        return;
    }

    jstring jstr = env->NewStringUTF(text);
    jclass toast = env->FindClass(OBFUSCATE("android/widget/Toast"));
    if (toast == nullptr) {
        return;
    }
    jmethodID makeText = env->GetStaticMethodID(
            toast, OBFUSCATE("makeText"),
            OBFUSCATE("(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;"));
    jobject toastObj = env->CallStaticObjectMethod(toast, makeText, ctx, jstr, length);
    jmethodID show = env->GetMethodID(toast, OBFUSCATE("show"), OBFUSCATE("()V"));
    env->CallVoidMethod(toastObj, show);

    env->DeleteLocalRef(jstr);
    env->DeleteLocalRef(toast);
}

void Dialog(JNIEnv *env, jobject ctx, const char *title, const char *message, const char *openBtn,
            const char *closeBtn, int sec, const char *url) {
    if (ctx == nullptr) {
        return;
    }

    jclass dialogHelperClass = env->FindClass(OBFUSCATE("com/ninmods/main/menu/MenuDialogHelper"));
    if (dialogHelperClass == nullptr) {
        return;
    }
    jmethodID showMethod = env->GetStaticMethodID(
            dialogHelperClass, OBFUSCATE("showDialogWithLink"),
            OBFUSCATE("(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)V"));

    jstring jTitle = env->NewStringUTF(title != nullptr ? title : "");
    jstring jMessage = env->NewStringUTF(message != nullptr ? message : "");
    jstring jOpen = env->NewStringUTF(openBtn != nullptr ? openBtn : "");
    jstring jClose = env->NewStringUTF(closeBtn != nullptr ? closeBtn : "");
    jint jSec = sec;
    jstring jUrl = (url != nullptr) ? env->NewStringUTF(url) : nullptr;

    env->CallStaticVoidMethod(dialogHelperClass, showMethod, ctx, jTitle, jMessage, jOpen, jClose,
                               jSec, jUrl);

    env->DeleteLocalRef(jTitle);
    env->DeleteLocalRef(jMessage);
    env->DeleteLocalRef(jOpen);
    env->DeleteLocalRef(jClose);
    if (jUrl != nullptr) {
        env->DeleteLocalRef(jUrl);
    }
    env->DeleteLocalRef(dialogHelperClass);
}

void setText(JNIEnv *env, jobject obj, const char *text) {
    if (obj == nullptr || text == nullptr) {
        return;
    }

    /* Html.fromHtml("") -> TextView.setText(CharSequence) */
    jclass html = env->FindClass(OBFUSCATE("android/text/Html"));
    if (html == nullptr) {
        return;
    }
    jmethodID fromHtml = env->GetStaticMethodID(
            html, OBFUSCATE("fromHtml"), OBFUSCATE("(Ljava/lang/String;)Landroid/text/Spanned;"));

    jclass textView = env->FindClass(OBFUSCATE("android/widget/TextView"));
    if (textView == nullptr) {
        return;
    }
    jmethodID setTextMethod = env->GetMethodID(textView, OBFUSCATE("setText"),
                                               OBFUSCATE("(Ljava/lang/CharSequence;)V"));

    jstring jstr = env->NewStringUTF(text);
    jobject spanned = env->CallStaticObjectMethod(html, fromHtml, jstr);
    env->CallVoidMethod(obj, setTextMethod, spanned);

    env->DeleteLocalRef(jstr);
    env->DeleteLocalRef(spanned);
    env->DeleteLocalRef(html);
    env->DeleteLocalRef(textView);
}
