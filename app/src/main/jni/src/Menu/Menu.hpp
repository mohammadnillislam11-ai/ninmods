#pragma once

#include <jni.h>

/**
 * NINMODS MOD MENU - NATIVE CONTRACT
 * ----------------------------------
 * This is the native half of the reference mod menu system:
 *
 *   com.ninmods.main.menu.ModMenu          -> Init / Icon / IconWebViewData /
 *                                            SettingsList / GetFeatureList /
 *                                            IsGameLibLoaded
 *   com.ninmods.main.menu.MenuPreferences  -> Changes
 *
 * The Java layer owns nothing but the rendering; the native layer owns the
 * menu structure (the feature DSL) and every value that reaches the engine.
 */

/* Same values as android.widget.Toast.LENGTH_SHORT / LENGTH_LONG. */
enum ToastLength {
    TOAST_LENGTH_SHORT = 0,
    TOAST_LENGTH_LONG = 1,
};

/* ----------------------- ModMenu ----------------------- */
void Init(JNIEnv *env, jobject thiz, jobject ctx, jobject title, jobject subtitle);

jstring Icon(JNIEnv *env, jobject thiz);

jstring IconWebViewData(JNIEnv *env, jobject thiz);

jobjectArray SettingsList(JNIEnv *env, jobject activityObject);

jobjectArray GetFeatureList(JNIEnv *env, jobject context);

jboolean IsGameLibLoaded(JNIEnv *env, jobject thiz);

/* -------------------- MenuPreferences ------------------- */
void Changes(JNIEnv *env, jclass clazz, jobject obj, jint featNum, jstring featName,
             jint value, jlong Lvalue, jboolean boolean, jstring text);

/* ------------------ native -> java helpers --------------- */
void Toast(JNIEnv *env, jobject ctx, const char *text, int length);

void Dialog(JNIEnv *env, jobject ctx, const char *title, const char *message,
            const char *openBtn, const char *closeBtn, int sec, const char *url);

void setText(JNIEnv *env, jobject obj, const char *text);

/* ------------------- native registration ----------------- */
int RegisterNinMenu(JNIEnv *env);

int RegisterNinMenuPreferences(JNIEnv *env);
