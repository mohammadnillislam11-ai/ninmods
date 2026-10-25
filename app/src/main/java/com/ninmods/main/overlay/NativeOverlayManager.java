package com.ninmods.main.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import com.ninmods.main.auth.AuthApiController;
import com.ninmods.main.auth.AuthManager;
import com.ninmods.main.core.AppLogger;
import com.ninmods.main.core.NativeBridge;
import com.ninmods.main.graphics.GLES3JNIView;

/**
 * NativeOverlayManager coordinates the Android Native Floating Menu Window
 * (Semi-JNI Architecture as in Android-ModMenu-SemiJni-main) coupled with
 * 100% C++ Linux Kernel Module Driver (/dev/BYNb) and Hardware ESP rendering.
 */
public class NativeOverlayManager {
    private static final String TAG = "NativeOverlayManager";
    private static volatile NativeOverlayManager sInstance;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private FloatingMenuView mFloatingMenuView;
    private GLES3JNIView mEspOverlayView;
    private WindowManager.LayoutParams mEspParams;

    private boolean mIsAttached = false;
    private String mActiveBrandId = "nin";
    private float mDensity = 2.4f;

    private NativeOverlayManager(Context context) {
        mContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        try {
            mDensity = mContext.getResources().getDisplayMetrics().density;
            if (mDensity < 1.5f) mDensity = 2.0f;
        } catch (Throwable t) {
            mDensity = 2.4f;
        }
    }

    public static synchronized NativeOverlayManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new NativeOverlayManager(context);
        }
        return sInstance;
    }

    public static Context getContext() {
        return sInstance != null ? sInstance.mContext : null;
    }

    public static void start(Context context, String brandId) {
        getInstance(context).startInternal(brandId);
    }

    public static void stop() {
        if (sInstance != null) {
            sInstance.stopInternal();
        }
    }

    public static boolean isRunning() {
        return sInstance != null && sInstance.mIsAttached;
    }

    public static String getActiveBrandId() {
        return sInstance != null ? sInstance.mActiveBrandId : "nin";
    }

    @SuppressLint("ClickableViewAccessibility")
    private void startInternal(String brandId) {
        mActiveBrandId = (brandId != null && !brandId.isEmpty()) ? brandId : "nin";
        mMainHandler.post(() -> {
            try {
                String hwid = AuthApiController.getHwid(mContext);
                boolean hasActiveLicense = AuthManager.hasActiveLicense(mContext);
                String activeKey = AuthManager.getActiveLicenseKey(mContext);
                String serverUrl = AuthApiController.getServerBaseUrl(mContext);

                if (mIsAttached && mFloatingMenuView != null && mFloatingMenuView.isAttached()) {
                    AppLogger.log(TAG, "Floating overlay already running. Active brand: " + mActiveBrandId);
                    return;
                }

                // Ensure kernel nodes are linked and nin_engine daemon is active
                new Thread(() -> {
                    com.ninmods.main.core.MainActivity.requestSuperUser();
                    com.ninmods.main.core.MainActivity.deployEngineDaemon(mContext);
                }).start();

                // 1. Initialize C++ Native Bridge Engine & Driver link
                NativeBridge.launchBrandOverlay(mActiveBrandId, hwid, hasActiveLicense, mDensity, activeKey, serverUrl);

                // Attach Android Native Floating Menu Window (Exact Semi-JNI Window Architecture)
                if (mFloatingMenuView == null) {
                    mFloatingMenuView = new FloatingMenuView(mContext, mWindowManager);
                    mWindowManager.addView(mFloatingMenuView.getRootFrame(), mFloatingMenuView.getLayoutParams());
                    mFloatingMenuView.setAttached(true);
                    AppLogger.log(TAG, "Android Native Floating Menu Window attached (Pure floating view, zero screen block)");
                }

                mIsAttached = true;
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to start native overlay", t);
            }
        });
    }

    public void openMenu() {
        mMainHandler.post(() -> {
            if (mFloatingMenuView != null) {
                mFloatingMenuView.expandMenu();
            }
        });
    }

    public void collapseToLogo() {
        mMainHandler.post(() -> {
            if (mFloatingMenuView != null) {
                mFloatingMenuView.collapseToLogo();
            }
        });
    }

    private void stopInternal() {
        mMainHandler.post(() -> {
            try {
                if (mFloatingMenuView != null) {
                    if (mFloatingMenuView.getRootFrame() != null && mFloatingMenuView.getRootFrame().isAttachedToWindow()) {
                        mWindowManager.removeView(mFloatingMenuView.getRootFrame());
                    }
                    mFloatingMenuView.setAttached(false);
                    mFloatingMenuView = null;
                }
                if (mEspOverlayView != null) {
                    NativeBridge.closeOverlay();
                    if (mEspOverlayView.isAttachedToWindow()) {
                        mWindowManager.removeView(mEspOverlayView);
                    }
                    mEspOverlayView = null;
                }
                mIsAttached = false;
                AppLogger.log(TAG, "Native Floating Overlay and ESP surface stopped cleanly");
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to stop native overlay", t);
            }
        });
    }
}
