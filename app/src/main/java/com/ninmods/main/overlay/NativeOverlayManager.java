package com.ninmods.main.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.ninmods.main.auth.AuthApiController;
import com.ninmods.main.auth.AuthManager;
import com.ninmods.main.core.AppLogger;
import com.ninmods.main.core.NativeBridge;
import com.ninmods.main.graphics.GLES3JNIView;

/**
 * NativeOverlayManager coordinates the 100% C++ Dear ImGui Overlay Engine.
 * Replaces legacy Java Floater overlays with direct hardware accelerated
 * EGL / OpenGL ES 3.0 surfaces and native event routing.
 */
public class NativeOverlayManager {
    private static final String TAG = "NativeOverlayManager";
    private static volatile NativeOverlayManager sInstance;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private GLES3JNIView mOverlayView;
    private WindowManager.LayoutParams mParams;
    private boolean mIsAttached = false;
    private String mActiveBrandId = "nin";

    private NativeOverlayManager(Context context) {
        mContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public static synchronized NativeOverlayManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new NativeOverlayManager(context);
        }
        return sInstance;
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
                if (mIsAttached && mOverlayView != null) {
                    AppLogger.log(TAG, "Re-routing running native overlay to brand: " + mActiveBrandId);
                    String hwid = AuthApiController.getHwid(mContext);
                    boolean hasActiveLicense = AuthManager.hasActiveLicense(mContext);
                    NativeBridge.launchBrandOverlay(mActiveBrandId, hwid, hasActiveLicense);
                    return;
                }

                int layoutFlag;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                } else {
                    layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
                }

                mParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        layoutFlag,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        PixelFormat.TRANSLUCENT
                );
                mParams.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
                mParams.gravity = Gravity.TOP | Gravity.START;
                mParams.x = 0;
                mParams.y = 0;

                // Launch C++ ImGui overlay state machine
                String hwid = AuthApiController.getHwid(mContext);
                boolean hasActiveLicense = AuthManager.hasActiveLicense(mContext);
                NativeBridge.launchBrandOverlay(mActiveBrandId, hwid, hasActiveLicense);

                mOverlayView = new GLES3JNIView(mContext);
                mOverlayView.setOnTouchListener((v, event) -> {
                    if (event == null) return false;
                    int action = event.getActionMasked();
                    float x = event.getX();
                    float y = event.getY();
                    return NativeBridge.handleTouch(action, x, y);
                });

                mWindowManager.addView(mOverlayView, mParams);
                mIsAttached = true;
                AppLogger.log(TAG, "Native C++ Dear ImGui Overlay attached successfully for brand: " + mActiveBrandId);
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to start native overlay", t);
            }
        });
    }

    private void stopInternal() {
        mMainHandler.post(() -> {
            try {
                if (mIsAttached && mOverlayView != null) {
                    NativeBridge.closeOverlay();
                    if (mOverlayView.isAttachedToWindow()) {
                        mWindowManager.removeView(mOverlayView);
                    }
                    mOverlayView = null;
                    mIsAttached = false;
                    AppLogger.log(TAG, "Native C++ Dear ImGui Overlay stopped cleanly");
                }
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to stop native overlay", t);
            }
        });
    }
}
