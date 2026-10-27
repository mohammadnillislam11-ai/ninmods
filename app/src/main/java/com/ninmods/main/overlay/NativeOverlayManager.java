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
 *
 * The hardware ESP lives on a dedicated transparent OpenGL ES surface that is
 * shared by every overlay flavour (main menu and NINLITE menu) - there must
 * only ever be ONE GL context, otherwise the two surfaces fight over the EGL
 * context and the ESP flickers or disappears.
 */
public class NativeOverlayManager {
    private static final String TAG = "NativeOverlayManager";
    private static volatile NativeOverlayManager sInstance;

    /* Shared ESP surface (static: used by the NINLITE overlay as well). */
    private static volatile GLES3JNIView sEspOverlayView;
    private static volatile WindowManager.LayoutParams sEspParams;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private FloatingMenuView mFloatingMenuView;
    private WindowManager.LayoutParams mEspParams;

    private volatile boolean mIsAttached = false;
    private volatile String mActiveBrandId = "nin";
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

    /* ------------------------------------------------------------------
     * SHARED HARDWARE ESP SURFACE
     * ------------------------------------------------------------------
     * The GLES3JNIView drives nativeStep() -> RenderHardwareESP() ->
     * getData() -> CreateDataList(), which is the complete ESP pipeline.
     * Before this surface existed the field was declared but never
     * assigned, so the render thread never started and NOT A SINGLE ESP
     * FRAME was ever drawn.
     */
    public static synchronized void attachEspSurface(Context context, WindowManager wm) {
        if (sEspOverlayView != null) {
            return;
        }
        if (wm == null) {
            AppLogger.logE(TAG, "Cannot attach the hardware ESP surface: WindowManager is null", null);
            return;
        }
        GLES3JNIView view = null;
        try {
            view = new GLES3JNIView(context);
            int overlayType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            wm.addView(view, params);
            sEspOverlayView = view;
            sEspParams = params;
            AppLogger.log(TAG, "Hardware ESP surface attached (transparent GLES3 overlay)");
        } catch (Throwable t) {
            /* A failed attach means NO ESP AT ALL, so the failure must be
             * visible in the log instead of being swallowed. */
            AppLogger.logE(TAG, "Failed to attach the hardware ESP surface - the ESP will not render", t);
            if (view != null) {
                try {
                    if (view.isAttachedToWindow()) {
                        wm.removeView(view);
                    }
                } catch (Throwable ignored) {
                    // The view was never attached - nothing to clean up.
                }
            }
            sEspOverlayView = null;
            sEspParams = null;
        }
    }

    public static synchronized void detachEspSurface(WindowManager wm) {
        if (sEspOverlayView == null) {
            return;
        }
        try {
            if (sEspOverlayView.isAttachedToWindow()) {
                wm.removeView(sEspOverlayView);
            }
        } catch (Throwable t) {
            AppLogger.logE(TAG, "Failed to detach the hardware ESP surface", t);
        }
        sEspOverlayView = null;
        sEspParams = null;
    }

    public static boolean isEspSurfaceAttached() {
        return sEspOverlayView != null;
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

                // Ensure kernel driver device nodes and permissions are initialized
                new Thread(() -> {
                    com.ninmods.main.core.MainActivity.requestSuperUser();
                }).start();

                // 1. Initialize C++ Native Bridge Engine & Driver link
                NativeBridge.launchBrandOverlay(mActiveBrandId, hwid, hasActiveLicense, mDensity, activeKey, serverUrl);

                // 2. Attach the transparent hardware ESP surface (shared).
                attachEspSurface(mContext, mWindowManager);

                // 3. Attach Android Native Floating Menu Window (Exact Semi-JNI Window Architecture)
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
                /* Tear the GL surface down first so the native render thread
                 * stops touching the ImGui context before the brand overlay is
                 * released. It is only released when NO other overlay (the
                 * NINLITE menu) still uses the shared surface - otherwise
                 * stopping the main menu would kill the ESP of the NINLITE
                 * menu that is still open. */
                if (!com.ninmods.main.menu.ninlite.NinLiteOverlayManager.isRunning()) {
                    detachEspSurface(mWindowManager);
                }
                NativeBridge.closeOverlay();
                mIsAttached = false;
                AppLogger.log(TAG, "Native Floating Overlay and ESP surface stopped cleanly");
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to stop native overlay", t);
            }
        });
    }
}
