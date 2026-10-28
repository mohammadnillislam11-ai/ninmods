package com.ninmods.main.menu.ninlite;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import com.ninmods.main.auth.AuthApiController;
import com.ninmods.main.auth.AuthManager;
import com.ninmods.main.core.AppLogger;
import com.ninmods.main.core.FeatureSettings;
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

    /**
     * Returns the shared instance of the manager.
     *
     * @param context context used to build the instance
     * @return the shared instance
     */
    public static synchronized NinLiteOverlayManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new NinLiteOverlayManager(context);
        }
        return sInstance;
    }

    /**
     * Starts the NINLITE floating menu.
     *
     * @param context context of the caller
     */
    public static void start(Context context) {
        getInstance(context).startInternal();
    }

    /**
     * Stops the NINLITE floating menu.
     */
    public static void stop() {
        if (sInstance != null) {
            sInstance.stopInternal();
        }
    }

    /**
     * Reports whether the NINLITE floating menu is running.
     *
     * @return true when the menu is attached
     */
    public static boolean isRunning() {
        return sInstance != null && sInstance.mIsAttached;
    }

    /**
     * Starts the NINLITE overlay of the QX channel.
     *
     * The QX channel is the root daemon over IPC, so the channel is selected
     * explicitly before the native overlay is created. The in-app engine then
     * never reads the game memory itself in this mode.
     */
    private void startInternal() {
        mMainHandler.post(() -> {
            try {
                if (mIsAttached && mFloatingMenuView != null && mFloatingMenuView.isAttached()) {
                    AppLogger.log(TAG, "NINLITE overlay already running.");
                    return;
                }

                // Stop conflicting RT overlay if active to enforce strict single-channel isolation
                if (com.ninmods.main.overlay.NativeOverlayManager.isRunning()) {
                    com.ninmods.main.overlay.NativeOverlayManager.stop();
                }

                String hwid = AuthApiController.getHwid(mContext);
                boolean hasActiveLicense = AuthManager.hasActiveLicense(mContext);
                String activeKey = AuthManager.getActiveLicenseKey(mContext);
                String serverUrl = AuthApiController.getServerBaseUrl(mContext);
                float density = mContext.getResources().getDisplayMetrics().density;

                // Select the QX channel (root daemon over IPC) and inform the
                // native bridge.
                NativeBridge.setActiveChannel(NativeBridge.CHANNEL_QX);
                NativeBridge.launchBrandOverlay("ninlite", hwid, hasActiveLicense, density, activeKey, serverUrl);

                /* The persistent feature store has to exist before the menu
                 * reads a single value. */
                FeatureSettings.init(mContext);

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

    /**
     * Stops the NINLITE overlay and releases the shared ESP surface.
     */
    private void stopInternal() {
        mMainHandler.post(() -> {
            try {
                if (mFloatingMenuView != null) {
                    mFloatingMenuView.release();
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
