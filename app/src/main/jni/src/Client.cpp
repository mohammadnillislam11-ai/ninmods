#if __has_include(<EGL/egl.h>)
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#else
#include "Includes/android_ide_stubs/EGL/egl.h"
#include "Includes/android_ide_stubs/GLES3/gl3.h"
#endif
#include <cstdio>
#include <cmath>
#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <dlfcn.h>
#include <fstream>
#include <iomanip>
#if __has_include(<jni.h>)
#include <jni.h>
#else
#include "Includes/android_ide_stubs/jni.h"
#endif
#include <pthread.h>
#include <random>
#include <signal.h>
#include <sstream>
#include <string>
#include <map>
#include <utility>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <thread>
#include <unistd.h>
#if __has_include(<sys/socket.h>)
#include <sys/socket.h>
#include <sys/un.h>
#else
#include "Includes/android_ide_stubs/sys/socket.h"
#include "Includes/android_ide_stubs/sys/un.h"
#endif


#include "BooleanClient.h"
#include "GameEngine.h"
#include "ninlite_daemon/Namegun.h"
#include "Includes/Logger.h"
#include "Includes/Utils.h"
#include "Includes/obfuscate.h"
#include "Unity/Color.hpp"
#include "Unity/ESP.h"
#include "Unity/Unity.h"
#include "Unity/Vector2.hpp"
#include "Unity/Vector3.hpp"


#include "Icon/Font.h"
#include "Icon/Icon.h"
#include "Icon/Iconcpp.h"
#include "UI/NativeOverlayController.h"
#include "UI/Popups/LicensePopup.h"
#include "Engine/NinliteEngine.h"
#include "Includes/FeatureBridge.h"
#include "Menu/Menu.hpp"

// ============================================
// GLOBAL SYSTEM STATE & PROFILE SETTINGS
// ============================================
static int g_hackVersion = 1;

inline bool isButrulVersion() { return g_hackVersion == 2; }

/* These used to be hard coded to `true`, which meant every native
 * authorisation gate was a no-op. They now mirror the genuine session state
 * that the Java layer pushes through SecurityVault.nativeSetSession(). */
inline bool securityChecksPassed() { return SecurityVault_IsSessionValid(); }

inline bool NativeIsLoggedIn() { return SecurityVault_IsSessionValid(); }

// ============================================
// EXISTING GLOBAL VARIABLES AND FUNCTIONS
// ============================================

float g_espLeftAdjust = 0.0f;
float g_espRightAdjust = 0.0f;

static int g_ScreenWidth = 0;
static int g_ScreenHeight = 0;

template <typename T>
inline constexpr const T &ClampVal(const T &v, const T &lo, const T &hi) {
  return (v < lo) ? lo : (hi < v) ? hi : v;
}

inline ImU32 ToImU32(const Color &c, float alphaMultiplier = 1.0f) {
  int r = (int)ClampVal(c.r, 0.0f, 255.0f);
  int g = (int)ClampVal(c.g, 0.0f, 255.0f);
  int b = (int)ClampVal(c.b, 0.0f, 255.0f);
  int a = (int)ClampVal(c.a * alphaMultiplier, 0.0f, 255.0f);
  return IM_COL32(r, g, b, a);
}

static void EnsureDriverConnected() {
  /* Rate limited: the discovery probe costs several hundred syscalls and this
   * function runs once per rendered frame. */
  EnsureDriverConnectedThrottled();
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_overlay_Floater_ChangesID(JNIEnv *env, jclass clazz,
                                                jint id, jint value) {
  bool enabled = (value != 0);

  if (!isButrulVersion()) {
    switch (id) {
    case 2:
    case 3:
    case 8:
    case 9:
    case 10:
    case 25:
    case 33:
    case 34:
    case 62:
    case 63:
      LOGD("BLOCKED: write feature on safe mode (ID: %d)", id);
      return;
    default:
      break;
    }
  }

  switch (id) {
  case 1:
    Actived.activar = enabled;
    break;
  case 2:
    pPlayer.aimbot = enabled;
    break;
  case 3:
    pPlayer.fovawm = enabled;
    break;
  case 6:
    pPlayer.aimbotlock = enabled;
    break;
  case 8:
    pPlayer.silen4a = enabled;
    break;
  case 61:
    /* Legacy id. Was inverted relative to every other entry point, so the
     * same switch could turn head aim on when it was off. */
    pPlayer.silentAimHead = (value != 0);
    break;
  case 62:
    pPlayer.targetByDistance = (value == 1);
    break;
  case 63:
    pPlayer.aimMagnet = enabled;
    break;
  case 9:
    pPlayer.AimFov = (float)value;
    break;
  case 10:
    pPlayer.AimDistance = (float)value;
    break;
  case 70:
    pPlayer.headshotRate = value;
    break;
  case 71:
    pPlayer.ignoreBots = enabled;
    break;
  case 72:
    pPlayer.ignoreKnocked = enabled;
    break;
  case 11:
    pEspPlayer.espLinha = enabled;
    break;
  case 12:
    pEspPlayer.espCaixa = enabled;
    break;
  case 13:
    pEspPlayer.espDistance = enabled;
    break;
  case 14:
    pEspPlayer.espSkeleton = enabled;
    break;
  case 15:
    pEspPlayer.espIden360 = enabled;
    break;
  case 16:
    if (value == 0)
      pEspPlayer.espColor = Color(255, 255, 255, 255);
    else if (value == 1)
      pEspPlayer.espColor = Color(0, 255, 0, 255);
    else if (value == 2)
      pEspPlayer.espColor = Color(0, 0, 255, 255);
    else if (value == 3)
      pEspPlayer.espColor = Color(255, 0, 0, 255);
    else if (value == 4)
      pEspPlayer.espColor = Color(0, 0, 0, 255);
    else if (value == 5)
      pEspPlayer.espColor = Color(255, 255, 0, 255);
    else if (value == 6)
      pEspPlayer.espColor = Color(0, 255, 255, 255);
    else if (value == 7)
      pEspPlayer.espColor = Color(255, 0, 255, 255);
    else if (value == 8)
      pEspPlayer.espColor = Color(128, 128, 128, 255);
    else if (value == 9)
      pEspPlayer.espColor = Color(160, 32, 240, 255);
    break;
  case 17:
    pEspPlayer.boxType = static_cast<BoxType>(value);
    break;
  case 18:
    pEspPlayer.lineType = static_cast<LineType>(value);
    break;
  case 19:
    pEspPlayer.espHealth = enabled;
    break;
  case 20:
    pEspPlayer.textSize = (float)value;
    break;
  case 21:
    pEspPlayer.espName = enabled;
    break;
  case 22:
    pEspPlayer.thickness = (float)value;
    break;
  case 23:
    pEspPlayer.espDrawCount = enabled;
    break;
  case 25:
    pPlayer.gameSpeed = (float)value / 10.0f;
    break;
  case 60:
    pPlayer.SpeedHackV2 = enabled;
    break;
  case 27:
    showFOVCircle = enabled;
    break;
  case 30:
    g_espLeftAdjust = (float)value;
    break;
  case 31:
    g_espRightAdjust = (float)value;
    break;
  case 32:
    g_espLeftAdjust = 0.0f;
    g_espRightAdjust = 0.0f;
    break;
  case 33:
    pPlayer.NoRecoil = enabled;
    break;
  case 34:
    break;
  default:
    break;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_drawEspNative(JNIEnv *env, jclass type,
                                                     jobject espView,
                                                     jobject canvas, jint width,
                                                     jint height) {
  if (!SecurityVault_IsSessionValid()) {
    return; // Zero ESP drawing if no genuine verified server session
  }
  if (env->PushLocalFrame(256) < 0) {
    return;
  }
  ESP localEsp = ESP(env, espView, canvas);
  if (localEsp.isValid()) {
    DrawESP(localEsp);
  }
  env->PopLocalFrame(nullptr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_overlay_Floater_PxbftKZXivSr(JNIEnv *env, jclass type,
                                                   jobject espView,
                                                   jobject canvas, jint width,
                                                   jint height) {
  if (!SecurityVault_IsSessionValid()) {
    return; // Zero ESP drawing if no genuine verified server session
  }
  if (env->PushLocalFrame(256) < 0) {
    return;
  }
  ESP localEsp = ESP(env, espView, canvas);
  if (localEsp.isValid()) {
    DrawESP(localEsp);
  }
  env->PopLocalFrame(nullptr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_overlay_Floater_ftKZXivSr(JNIEnv *env, jclass clazz,
                                                jobject ctx) {
  if (!SecurityVault_IsSessionValid()) {
    NIN_LOGE("SECURITY", ">>> [ACCESS_DENIED] ftKZXivSr blocked: Unauthorized "
                         "execution without server session <<<");
    return;
  }
  NIN_LOGI("CLIENT", "ftKZXivSr invoked from Java overlay");
  if (driver && !driver->isConnected()) {
    driver->initDriver();
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_overlay_Floater_ftKwCvSr(JNIEnv *env, jclass clazz) {
  NIN_LOGI("CLIENT", "ftKwCvSr invoked from Java overlay");
}

// ========================================================
// GLES3 JNI VIEW NATIVE BRIDGE (OpenGL ES 3.0 / ImGui ESP)
// ========================================================
extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_launchBrandOverlay(
    JNIEnv *env, jclass clazz, jstring jBrandId, jstring jHwid,
    jboolean isLicenseActive, jfloat density, jstring jPrefillKey,
    jstring jServerUrl) {
  const char *bId =
      jBrandId ? env->GetStringUTFChars(jBrandId, nullptr) : "nin";
  const char *hwid = jHwid ? env->GetStringUTFChars(jHwid, nullptr) : "";
  const char *prefill =
      jPrefillKey ? env->GetStringUTFChars(jPrefillKey, nullptr) : "";
  const char *sUrl =
      jServerUrl ? env->GetStringUTFChars(jServerUrl, nullptr) : "";

  NativeOverlayController::Initialize(g_ScreenWidth, g_ScreenHeight,
                                      (float)density, bId, hwid,
                                      (bool)isLicenseActive);
  if (!isLicenseActive) {
    LicensePopup::Open(hwid, prefill, sUrl);
  }

  /* Cache the Java NativeBridge class on the JNI thread so the GL render
   * thread can call into it without FindClass() (which fails there). */
  if (g_NativeBridgeClassRef == nullptr) {
    jclass local = env->FindClass("com/ninmods/main/core/NativeBridge");
    if (local != nullptr) {
      g_NativeBridgeClassRef = (jclass)env->NewGlobalRef(local);
      env->DeleteLocalRef(local);
    } else if (env->ExceptionCheck()) {
      env->ExceptionClear();
    }
  }

  // Register verify handler to call Java AuthApiController
  LicensePopup::SetVerifyHandler([](const std::string &key,
                                    const std::string &brand) {
    if (!g_JavaVM)
      return;
    JNIEnv *jEnv = nullptr;
    bool attached = false;
    if (g_JavaVM->GetEnv((void **)&jEnv, JNI_VERSION_1_6) != JNI_OK) {
      if (g_JavaVM->AttachCurrentThread(&jEnv, nullptr) == JNI_OK) {
        attached = true;
      }
    }
    if (jEnv) {
      jclass bridgeClass =
          jEnv->FindClass("com/ninmods/main/core/NativeBridge");
      if (bridgeClass) {
        jmethodID mid =
            jEnv->GetStaticMethodID(bridgeClass, "verifyLicenseFromNative",
                                    "(Ljava/lang/String;Ljava/lang/String;)V");
        if (mid) {
          jstring jK = jEnv->NewStringUTF(key.c_str());
          jstring jB = jEnv->NewStringUTF(brand.c_str());
          jEnv->CallStaticVoidMethod(bridgeClass, mid, jK, jB);
          jEnv->DeleteLocalRef(jK);
          jEnv->DeleteLocalRef(jB);
        }
        jEnv->DeleteLocalRef(bridgeClass);
      }
      if (attached) {
        g_JavaVM->DetachCurrentThread();
      }
    }
  });

  if (jBrandId)
    env->ReleaseStringUTFChars(jBrandId, bId);
  if (jHwid)
    env->ReleaseStringUTFChars(jHwid, hwid);
  if (jPrefillKey)
    env->ReleaseStringUTFChars(jPrefillKey, prefill);
  if (jServerUrl)
    env->ReleaseStringUTFChars(jServerUrl, sUrl);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isModalPopupActive(JNIEnv *env,
                                                           jclass clazz) {
  return (jboolean)NativeOverlayController::IsModalPopupActive();
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_onLicenseResult(JNIEnv *env,
                                                        jclass clazz,
                                                        jboolean success,
                                                        jstring jMsg) {
  const char *msg = jMsg ? env->GetStringUTFChars(jMsg, nullptr) : "";
  LicensePopup::SetVerificationResult((bool)success, msg);
  if (jMsg)
    env->ReleaseStringUTFChars(jMsg, msg);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_setKeyFromJava(JNIEnv *env,
                                                        jclass clazz,
                                                        jstring jKey) {
  /* NativeBridge.java declares setKeyFromJava(String) but there was NO C++
   * export for it, so the VERIFY and PASTE buttons of the licence dialog
   * threw an UnsatisfiedLinkError on the first tap and the verification flow
   * never reached AuthManager.updateActiveLicense(). */
  if (!jKey) return;
  const char* chars = env->GetStringUTFChars(jKey, nullptr);
  if (!chars) {
    if (env->ExceptionCheck()) env->ExceptionClear();
    return;
  }
  std::string key(chars);
  env->ReleaseStringUTFChars(jKey, chars);
  LicensePopup::SetKey(key);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_handleTouch(JNIEnv *env, jclass clazz,
                                                    jint action, jfloat x,
                                                    jfloat y) {
  return (jboolean)NativeOverlayController::HandleTouchEvent(action, x, y);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isMenuOpen(JNIEnv *env, jclass clazz) {
  return (jboolean)NativeOverlayController::s_MenuOpen.load(
      std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_setMenuOpen(JNIEnv *env, jclass clazz,
                                                    jboolean open) {
  NativeOverlayController::s_MenuOpen.store((bool)open,
                                            std::memory_order_relaxed);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isOverlayActive(JNIEnv *env,
                                                        jclass clazz) {
  return (jboolean)NativeOverlayController::s_Initialized.load(
      std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_closeOverlay(JNIEnv *env,
                                                      jclass clazz) {
  NativeOverlayController::s_Initialized.store(false, std::memory_order_relaxed);
  NativeOverlayController::s_MenuOpen.store(false, std::memory_order_relaxed);
}

/* ------------------------------------------------------------------
 * DAEMON CONTROL SYNC (fire and forget)
 * ------------------------------------------------------------------
 * The daemon control messages are pushed from the UI thread (a switch, a
 * slider drag). Doing the socket round trip inline blocked the UI for up to
 * 200 ms per event - a single slider drag produced dozens of sequential
 * blocking round trips.
 *
 * The requests are therefore queued and delivered by one background worker.
 * Only the LATEST value per mode is kept, which is exactly the semantics of
 * a feature flag / slider.
 */
static std::mutex g_daemonSyncMutex;
static std::map<int, std::pair<bool, int>> g_daemonSyncQueue;
static std::atomic<bool> g_daemonSyncWorkerStarted{false};

static void SyncWithNinLiteDaemon(int mode, bool isOn, int value) {
  {
    std::lock_guard<std::mutex> lock(g_daemonSyncMutex);
    g_daemonSyncQueue[mode] = std::make_pair(isOn, value);
  }

  if (!g_daemonSyncWorkerStarted.exchange(true)) {
    std::thread([]() {
      while (true) {
        std::map<int, std::pair<bool, int>> pending;
        {
          std::lock_guard<std::mutex> lock(g_daemonSyncMutex);
          if (g_daemonSyncQueue.empty()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
            continue;
          }
          pending.swap(g_daemonSyncQueue);
        }
        for (auto &entry : pending) {
          DaemonSyncRequest(entry.first, entry.second.first, entry.second.second);
        }
      }
    }).detach();
  }
}

/* The selected silent aim target (0 = Legit, 1 = Head, 2 = Chest, 3 = Neck).
 * The engine only models "head" and "not head", so the raw selection is kept
 * here to let the menu read back exactly what the user picked. */
static std::atomic<int> g_silentTargetSelection{1};

/*
 * The ESP master switch gates the whole entity pipeline of the engine
 * (GameEngine.h returns immediately when it is off), so it is written
 * through one helper: switching on an ESP sub feature switches the master
 * on as well and switching the master off switches every ESP sub feature
 * off again. A menu can therefore never show a switched on ESP that
 * renders nothing.
 */
static void SetEspMasterSwitch(bool on) {
  Actived.activar = on;
  SyncWithNinLiteDaemon(Ninlite::MODE_MASTER, on, 0);
}

/*
 * THE SINGLE ENGINE WRITER
 * ------------------------
 * Every menu of this app (the classic NativeBridge menu and the reference
 * parity DSL menu in Menu/Preferences.cpp) writes through this function, so
 * there is exactly one switch that maps a feature id onto an engine flag.
 */
void NinFeatureSetState(int featId, bool bState) {
  switch (featId) {
  // ESP Features
  case 1:
    SetEspMasterSwitch(bState);
    if (!bState) {
      /* Master off: every ESP sub feature goes with it. */
      pEspPlayer.espCaixa = false;
      pEspPlayer.espSkeleton = false;
      pEspPlayer.espLinha = false;
      pEspPlayer.espHealth = false;
      pEspPlayer.espName = false;
      pEspPlayer.espDistance = false;
      pEspPlayer.espDrawCount = false;
      pEspPlayer.espIden360 = false;
    }
    break;
  case 2:
    pEspPlayer.espCaixa = bState;
    if (bState) SetEspMasterSwitch(true);
    break;
  case 3:
    pEspPlayer.espSkeleton = bState;
    if (bState) SetEspMasterSwitch(true);
    break;
  case 4:
    pEspPlayer.espLinha = bState;
    if (bState) SetEspMasterSwitch(true);
    break;
  case 5:
    pEspPlayer.espHealth = bState;
    if (bState) SetEspMasterSwitch(true);
    break;
  case 6:
    pEspPlayer.espName = bState;
    if (bState) SetEspMasterSwitch(true);
    break;
  case 7:
    pEspPlayer.espDistance = bState;
    if (bState) SetEspMasterSwitch(true);
    break;
  case 8:
    pEspPlayer.espDrawCount = bState;
    if (bState) SetEspMasterSwitch(true);
    break;
  case 9:
    pEspPlayer.espIden360 = bState;
    if (bState) SetEspMasterSwitch(true);
    break;

  // Aimbot Features
  case 20:
    pPlayer.aimbot = bState;
    break;
  case 21:
    pPlayer.aimbotlock = bState;
    break;
  case 22:
    pPlayer.silen4a = bState;
    /* The root Daemon keeps its own copy of the aim flags. Without this the
     * classic menu switch changed the app side only, while the Daemon (the
     * component that actually moves the aim) never heard about it. */
    SyncWithNinLiteDaemon(Ninlite::MODE_SILENT, bState, 0);
    break;
  case 23:
    pPlayer.aimMagnet = bState;
    break;
  case 24:
    pPlayer.ignoreKnocked = bState;
    SyncWithNinLiteDaemon(Ninlite::MODE_IGNORE_KNOCK, bState, 0);
    break;
  case 25:
    pPlayer.ignoreBots = bState;
    break;
  case 26:
    pPlayer.silentAimHead = bState;
    break;
  case 27:
    showFOVCircle = bState;
    break;

  /* Reference engine options: the weapon name, the threat counter and the
   * info line at the bottom of the screen. */
  case 206:
    pEspPlayer.espWeapon = bState;
    break;
  case 207:
    pEspPlayer.espCount = bState;
    break;
  case 208:
    pEspPlayer.espInfo = bState;
    break;
  case 216:
    pPlayer.KillInChest = bState;
    break;

  // Combat / Memory Features
  case 40:
    pPlayer.NoRecoil = bState;
    break;
  case 41:
    pPlayer.SpeedHackV2 = bState;
    speedHackEnabled = bState;
    break;
  case 42:
    pPlayer.PlayerToMe = bState;
    break;
  case 43:
    pPlayer.undercam = bState;
    break;
  case 44:
    Actived.Bypass = bState;
    break;
  // NINLITE Checkbox & Switch Features
  // Ids 111..115: 101/102 belong to the "MAX TARGET DISTANCE" and
  // "GAME SPEED" sliders of the main overlay menu. Sharing the numeric ids
  // meant that a single NINLITE checkbox also overwrote an unrelated value.
  /* 111..115 write exactly ONE flag each. They used to force the master
   * switch on unconditionally, so switching a feature OFF switched the whole
   * ESP master ON and the master could never be turned off from this menu.
   * The master is now handled explicitly by the Java state store, which
   * persists it and mirrors it into the other menu. */
  case 111:
    pEspPlayer.espCaixa = bState;
    break;
  case 112:
    pEspPlayer.espLinha = bState;
    break;
  case 113:
    pEspPlayer.espHealth = bState;
    break;
  case 114:
    pEspPlayer.espDistance = bState;
    break;
  case 115:
    pEspPlayer.espName = bState;
    break;
  case 301:
    /* "Silent Aim Enable" must not silently switch the separate
     * "AIMBOT AUTO LOCK" feature on or off - that used to leave the Java
     * switch showing a stale state while the native flag had changed. */
    pPlayer.silen4a = bState;
    SyncWithNinLiteDaemon(Ninlite::MODE_SILENT, bState, 0);
    break;
  case 302:
    pPlayer.ignoreKnocked = bState;
    SyncWithNinLiteDaemon(Ninlite::MODE_IGNORE_KNOCK, bState, 0);
    break;
  case 999:
    Actived.activar = bState;
    SyncWithNinLiteDaemon(Ninlite::MODE_MASTER, bState, 0);
    NIN_LOGI("NINLITE", "Root Engine state toggled: %d", bState ? 1 : 0);
    break;

  default:
    break;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_setFeatureState(JNIEnv *env,
                                                        jclass clazz,
                                                        jint featId,
                                                        jboolean state) {
  NinFeatureSetState((int)featId, (bool)state);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_setFeatureFloat(JNIEnv *env,
                                                        jclass clazz,
                                                        jint featId,
                                                        jfloat value) {
  NinFeatureSetValue((int)featId, (float)value);
}

void NinFeatureSetValue(int featId, float fVal) {
  switch (featId) {
  case 100:
    pPlayer.AimFov = fVal;
    SyncWithNinLiteDaemon(Ninlite::MODE_AIM_FOV, true, (int)fVal);
    break;
  case 101:
    pPlayer.AimDistance = fVal;
    SyncWithNinLiteDaemon(Ninlite::MODE_AIM_DISTANCE, true, (int)fVal);
    break;
  case 102:
    pPlayer.gameSpeed = fVal;
    break;

  // NINLITE Sliders & Dropdown Values
  case 201:
    pEspPlayer.boxType = (BoxType)((int)fVal);
    break;
  case 202:
    pEspPlayer.lineType = (LineType)((int)fVal);
    break;
  /* Reference engine sliders: the size of the ESP text and the head and
   * chest rate of the aim (how many of every ten shots hit the head). */
  case 209:
    pEspPlayer.textSize = fVal;
    break;
  case 214:
    pPlayer.HeadRate = (int)fVal;
    break;
  case 215:
    pPlayer.ChestRate = (int)fVal;
    break;
  case 303:
    pPlayer.AimFov = fVal;
    SyncWithNinLiteDaemon(Ninlite::MODE_AIM_FOV, true, (int)fVal);
    break;
  case 304:
    /* "Legit Delay" (0..10). It used to overwrite the FOV circle scale and
     * was additionally forwarded to the Daemon as "chest shot rate", so one
     * slider controlled three unrelated things. */
    pPlayer.legitDelayMs = fVal;
    SyncWithNinLiteDaemon(Ninlite::MODE_LEGIT_DELAY, true, (int)fVal);
    break;
  case 305:
    /* Silent Target: 0 = Legit, 1 = Head, 2 = Chest, 3 = Neck.
     * The old expression evaluated to true for BOTH "Legit" and "Head",
     * so choosing "Legit" silently enabled head-only aiming. */
    {
      int selection = (int)fVal;
      if (selection < 0) selection = 0;
      if (selection > 3) selection = 3;
      g_silentTargetSelection.store(selection);
      pPlayer.silentAimHead = (selection == 1);
      if (selection == 1) {
        SyncWithNinLiteDaemon(Ninlite::MODE_HEAD_RATE, true, 3);
        SyncWithNinLiteDaemon(Ninlite::MODE_CHEST_RATE, true, 0);
      } else {
        SyncWithNinLiteDaemon(Ninlite::MODE_HEAD_RATE, true, 0);
        SyncWithNinLiteDaemon(Ninlite::MODE_CHEST_RATE, true, 3);
      }
    }
    break;
  default:
    break;
  }
}

/* Reads the live value of a slider / dropdown back out of the engine.
 * Without this the menus could only WRITE the values, so they had to hard
 * code the initial slider position and silently overwrote the engine every
 * time the menu was opened. */
extern "C" JNIEXPORT jfloat JNICALL
Java_com_ninmods_main_core_NativeBridge_getFeatureFloat(JNIEnv *env,
                                                          jclass clazz,
                                                          jint featId) {
  return (jfloat)NinFeatureGetValue((int)featId);
}

float NinFeatureGetValue(int featId) {
  switch (featId) {
  case 100:
  case 303:
    return (jfloat)pPlayer.AimFov.load();
  case 101:
    return (jfloat)pPlayer.AimDistance.load();
  case 102:
    return (jfloat)pPlayer.gameSpeed.load();
  case 201:
    return (jfloat)(int)pEspPlayer.boxType.load();
  case 202:
    return (jfloat)(int)pEspPlayer.lineType.load();
  case 209:
    return (jfloat)pEspPlayer.textSize.load();
  case 214:
    return (jfloat)pPlayer.HeadRate.load();
  case 215:
    return (jfloat)pPlayer.ChestRate.load();
  case 304:
    return (jfloat)pPlayer.legitDelayMs.load();
  case 305:
    return (jfloat)g_silentTargetSelection.load();
default:
    return -1.0f;
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_getFeatureState(JNIEnv *env,
                                                          jclass clazz,
                                                          jint featId) {
  return NinFeatureGetState((int)featId) ? JNI_TRUE : JNI_FALSE;
}

bool NinFeatureGetState(int featId) {
  switch (featId) {
  case 1:
    return (jboolean)Actived.activar;
  case 2:
    return (jboolean)pEspPlayer.espCaixa;
  case 3:
    return (jboolean)pEspPlayer.espSkeleton;
  case 4:
    return (jboolean)pEspPlayer.espLinha;
  case 5:
    return (jboolean)pEspPlayer.espHealth;
  case 6:
    return (jboolean)pEspPlayer.espName;
  case 7:
    return (jboolean)pEspPlayer.espDistance;
  case 8:
    return (jboolean)pEspPlayer.espDrawCount;
  case 9:
    return (jboolean)pEspPlayer.espIden360;

  case 20:
    return (jboolean)pPlayer.aimbot;
  case 21:
    return (jboolean)pPlayer.aimbotlock;
  case 22:
    return (jboolean)pPlayer.silen4a;
  case 23:
    return (jboolean)pPlayer.aimMagnet;
  case 24:
    return (jboolean)pPlayer.ignoreKnocked;
  case 25:
    return (jboolean)pPlayer.ignoreBots;
  case 26:
    return (jboolean)pPlayer.silentAimHead;
  case 27:
    return (jboolean)showFOVCircle;
  case 206:
    return (jboolean)pEspPlayer.espWeapon;
  case 207:
    return (jboolean)pEspPlayer.espCount;
  case 208:
    return (jboolean)pEspPlayer.espInfo;
  case 216:
    return (jboolean)pPlayer.KillInChest;

  case 40:
    return (jboolean)pPlayer.NoRecoil;
  case 41:
    return (jboolean)pPlayer.SpeedHackV2;
  case 42:
    return (jboolean)pPlayer.PlayerToMe;
  case 43:
    return (jboolean)pPlayer.undercam;
  case 44:
    return (jboolean)Actived.Bypass;

  /* NINLITE alias ids. 111..115 and the legacy NINLITE ids 301/302 address
   * the very same engine flags as the classic menu ids, so a menu must be
   * able to READ the live state of every flag it is allowed to write.
   * Without these the getter answered JNI_FALSE for all of them, which is why
   * the menus had to fall back to hard coded check box states. */
  case 111:
    return (jboolean)pEspPlayer.espCaixa;
  case 112:
    return (jboolean)pEspPlayer.espLinha;
  case 113:
    return (jboolean)pEspPlayer.espHealth;
  case 114:
    return (jboolean)pEspPlayer.espDistance;
  case 115:
    return (jboolean)pEspPlayer.espName;
  case 301:
    return (jboolean)pPlayer.silen4a;
  case 302:
    return (jboolean)pPlayer.ignoreKnocked;
  case 999:
    return (jboolean)Actived.activar;
  default:
    return JNI_FALSE;
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isKernelDriverConnected(JNIEnv *env,
                                                                jclass clazz) {
  if (driver && driver->isConnected() && driver->getFd() >= 0)
    return JNI_TRUE;
  if (EnsureDriverConnectedThrottled())
    return JNI_TRUE;
  return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_NativeBridge_getEngineLiveStatus(JNIEnv *env,
                                                            jclass clazz) {
  char buf[512];
  bool connected = (driver && driver->isConnected() && driver->getFd() >= 0);
  if (!connected) {
    connected = EnsureDriverConnectedThrottled();
  }
  const char *node = (connected && strlen(driver->getNodeName()) > 0)
                         ? driver->getNodeName()
                         : "/dev/BYNb (DISCONNECTED)";
  const char *mode = connected ? driver->getDriverModeName() : "OFFLINE";
  int fd = connected ? driver->getFd() : -1;
  pid_t targetPid = (connected && driver) ? driver->getPid() : 0;

  /* The cached snapshot is published by the scene worker under a lock, so it
   * must be read through the lock protected accessor. */
  Response cached = GetCachedResponseSnapshot();
  int entities = connected ? cached.PlayerCount : 0;
  int visible = connected ? cached.VisibleEnemyCount : 0;
  bool espActive = Actived.activar;
  bool aimActive = pPlayer.aimbot;

  snprintf(buf, sizeof(buf),
           "KERNEL DRIVER : %s\n"
           "DEVICE NODE   : %s (fd: %d)\n"
           "CHANNEL MODE  : %s\n"
           "TARGET PID    : %s\n"
           "MEMORY IOCTL  : %s\n"
           "ENTITIES READ : %d TARGETS\n"
           "ENEMIES VIS   : %d VISIBLE\n"
           "HARDWARE ESP  : %s\n"
           "AIM ENGINE    : %s",
           connected ? "ONLINE" : "OFFLINE (NOT LOADED)", node, fd, mode,
           (targetPid > 0) ? std::to_string(targetPid).c_str()
                           : "NONE (NO TARGET)",
           connected ? "ACTIVE (32-BYTE RING 0)" : "STANDBY (LOADER REQUIRED)",
           entities, visible, espActive ? "ACTIVE" : "STANDBY",
           aimActive ? "ENABLED" : "OFF");
  return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ninmods_main_core_NativeBridge_getDetectedGamePid(JNIEnv *env, jclass clazz) {
  /*
   * The detection lives in the dedicated engine
   * (jni/src/Detection/GameDetection.cpp). This function only keeps the
   * pid of the engine in sync with the one the memory layer uses.
   */
  NinDetection::GameProcess process = NinDetection::Detect();
  if (process.isDetected && process.pid > 0) {
    target_pid.store(process.pid, std::memory_order_relaxed);
    return (jint)process.pid;
  }
  target_pid.store(0, std::memory_order_relaxed);
  return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isLiveGameDetected(JNIEnv *env, jclass clazz) {
  NinDetection::GameProcess process = NinDetection::Detect();
  if (process.isDetected && process.pid > 0) {
    target_pid.store(process.pid, std::memory_order_relaxed);
    return JNI_TRUE;
  }
  return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_NativeBridge_getNinliteStateJson(JNIEnv *env, jclass clazz) {
  std::string json = NinliteEngine::GetStateJson();
  return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_startNinliteEngineNative(JNIEnv *env, jclass clazz, jstring jNativeLibDir) {
  /* Cache the NativeBridge class on THIS JNI thread before the engine is
   * started. The engine runs the root script on a detached native thread,
   * and a thread attached through AttachCurrentThread() uses the system
   * class loader where FindClass() cannot see the app classes. */
  if (g_NativeBridgeClassRef == nullptr && clazz != nullptr) {
    g_NativeBridgeClassRef = (jclass)env->NewGlobalRef(clazz);
  }

  std::string nativeLibDir = "";
  if (jNativeLibDir) {
    const char *chars = env->GetStringUTFChars(jNativeLibDir, nullptr);
    if (chars) {
      nativeLibDir = chars;
      env->ReleaseStringUTFChars(jNativeLibDir, chars);
    }
  }
  NinliteEngine::StartEngine(nativeLibDir);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_stopNinliteEngineNative(JNIEnv *env, jclass clazz) {
  NinliteEngine::StopEngine();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isNinliteSetupDone(JNIEnv *env, jclass clazz) {
  return NinliteEngine::IsSetupDone() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_NativeBridge_getDetectedGameTitle(JNIEnv *env, jclass clazz) {
  /*
   * The title comes from the detection engine. The old implementation read
   * /proc/<pid>/cmdline here a second time - that read fails since Android 9
   * for a process of another user, which is why the panel reported the
   * wrong game (and sometimes none at all).
   */
  NinDetection::GameProcess process = NinDetection::Detect();
  if (process.isDetected && process.pid > 0) {
    target_pid.store(process.pid, std::memory_order_relaxed);
    return env->NewStringUTF(process.gameTitle.c_str());
  }
  return env->NewStringUTF("Waiting for Game...");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isEmulatorDetected(JNIEnv *env, jclass clazz) {
  return NinliteEngine::CheckEmulatorDetected() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_NativeBridge_getConnectionMode(JNIEnv *env, jclass clazz) {
  return env->NewStringUTF(NinliteEngine::GetConnectionModeStr());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_core_NativeBridge_isShellDaemonRunning(JNIEnv *env, jclass clazz) {
  return NinliteEngine::CheckShellDaemonRunning() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_Utils_getGamePackage(JNIEnv *env, jclass clazz) {
  return env->NewStringUTF(OBFUSCATE("com.dts.freefireth"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_Utils_getDaemonName(JNIEnv *env, jclass clazz) {
  return env->NewStringUTF("");
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_DashboardActivity_setModeNative(JNIEnv *env,
                                                           jclass clazz,
                                                           jint mode) {
  g_hackVersion = (mode == 2) ? 2 : 1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_ModeSelectActivity_setModeNative(JNIEnv *env,
                                                            jclass clazz,
                                                            jint mode) {
  g_hackVersion = (mode == 2) ? 2 : 1;
}

extern "C" JavaVM *g_JavaVM = nullptr;

/* Global reference to the Java NativeBridge class.
 * Resolved on the JNI thread (which has the app class loader) and reused by
 * the GL render thread, where FindClass() would always fail. */
jclass g_NativeBridgeClassRef = nullptr;

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_NativeBridge_getMemoryAccessMode(JNIEnv *env, jclass clazz) {
  /*
   * The real memory path of the engine. The telemetry panel used to show a
   * hardcoded "process_vm_readv / pread64" - that was wrong: both the app
   * engine and the daemon read and write through the kernel driver.
   */
  (void) clazz;
  const char *mode = "Not connected";
  if (driver != nullptr && driver->isConnected()) {
    mode = "Kernel Driver IOCTL (0x801/0x802)";
  } else {
    std::string connection = NinliteEngine::GetConnectionModeStr();
    if (connection.find("RT") != std::string::npos) {
      mode = "Root Daemon (Kernel Driver)";
    }
  }
  return env->NewStringUTF(mode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_core_NativeBridge_setEspWatermark(JNIEnv *env, jclass clazz, jstring text) {
  (void) clazz;
  if (text == nullptr) {
    g_espWatermark.clear();
    return;
  }
  const char *value = env->GetStringUTFChars(text, nullptr);
  if (value != nullptr) {
    g_espWatermark = value;
    env->ReleaseStringUTFChars(text, value);
  }
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  g_JavaVM = vm;
  signal(SIGPIPE, SIG_IGN);

  /*
   * The mod menu binds its native methods here. A failed binding is logged
   * but does not abort the load: the library also carries the ESP engine,
   * the driver bridge and the licence vault, and those must keep working
   * even if the menu classes are not reachable.
   */
  JNIEnv *env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK && env != nullptr) {
    if (RegisterNinMenu(env) != JNI_OK) {
      NIN_LOGI("MENU", "Native mod menu binding failed - the menu will not be available");
    }
    if (RegisterNinMenuPreferences(env) != JNI_OK) {
      NIN_LOGI("MENU", "Native mod menu preference binding failed - the menu will not be available");
    }
  }

  return JNI_VERSION_1_6;
}
