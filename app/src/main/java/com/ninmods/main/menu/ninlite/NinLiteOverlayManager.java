package com.ninmods.main.menu.ninlite;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import com.ninmods.main.auth.AuthApiController;
import com.ninmods.main.auth.AuthManager;
import com.ninmods.main.core.AppLogger;
import com.ninmods.main.core.NativeBridge;

/**
 * NinLiteOverlayManager coordinates the NINLITE floating menu lifecycle
 * and connects with the Pure Root Daemon & ESP pipeline.
 */
public class NinLiteOverlayManager {
    private static final String TAG = "NinLiteOverlayManager";
    private static volatile NinLiteOverlayManager sInstance;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private NinLiteFloatingMenuView mFloatingMenuView;
    private boolean mIsAttached = false;

    private NinLiteOverlayManager(Context context) {
        mContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public static synchronized NinLiteOverlayManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new NinLiteOverlayManager(context);
        }
        return sInstance;
    }

    public static void start(Context context) {
        getInstance(context).startInternal();
    }

    public static void stop() {
        if (sInstance != null) {
            sInstance.stopInternal();
        }
    }

    public static boolean isRunning() {
        return sInstance != null && sInstance.mIsAttached;
    }

    private void startInternal() {
        mMainHandler.post(() -> {
            try {
                if (mIsAttached && mFloatingMenuView != null && mFloatingMenuView.isAttached()) {
                    AppLogger.log(TAG, "NINLITE overlay already running.");
                    return;
                }

                String hwid = AuthApiController.getHwid(mContext);
                boolean hasActiveLicense = AuthManager.hasActiveLicense(mContext);
                String activeKey = AuthManager.getActiveLicenseKey(mContext);
                String serverUrl = AuthApiController.getServerBaseUrl(mContext);
                float density = mContext.getResources().getDisplayMetrics().density;

                // Inform NativeBridge
                NativeBridge.launchBrandOverlay("ninlite", hwid, hasActiveLicense, density, activeKey, serverUrl);

                /* Attach the shared transparent hardware ESP surface. There
                 * must only be ONE GL context: the main overlay manager owns
                 * it, so this call is a no-op when it is already running. */
                com.ninmods.main.overlay.NativeOverlayManager.attachEspSurface(mContext, mWindowManager);

                if (mFloatingMenuView == null) {
                    mFloatingMenuView = new NinLiteFloatingMenuView(mContext, mWindowManager);
                    mWindowManager.addView(mFloatingMenuView.getRootFrame(), mFloatingMenuView.getLayoutParams());
                    mFloatingMenuView.setAttached(true);
                    AppLogger.log(TAG, "NINLITE Floating Sidebar Menu Window attached successfully");
                }

                mIsAttached = true;
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to start NINLITE overlay", t);
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
                /* Release the shared ESP surface only when the main overlay
                 * manager is not running any more. */
                if (!com.ninmods.main.overlay.NativeOverlayManager.isRunning()) {
                    com.ninmods.main.overlay.NativeOverlayManager.detachEspSurface(mWindowManager);
                }
                mIsAttached = false;
                AppLogger.log(TAG, "NINLITE overlay stopped cleanly");
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to stop NINLITE overlay", t);
            }
        });
    }
}
