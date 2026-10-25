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
 * Dynamically switches between:
 *  1. Compact 76dp Floating Logo (draggable, non-blocking, zero screen freeze)
 *  2. Centered Modal Dialog (for License Verification & Driver Status Alerts)
 *  3. Responsive Tactical Brand Menu (touchable, scalable DPI, top header controls)
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
    private boolean mIsMenuOpen = false;
    private boolean mIsModalOpen = false;
    private String mActiveBrandId = "nin";

    // Logo coordinates and dimensions
    private int mLogoX = 60;
    private int mLogoY = 240;
    private int mLogoSizePx = 180;
    private float mDensity = 2.4f;

    private final Runnable mStateSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsAttached && mOverlayView != null) {
                boolean modalActive = NativeBridge.isModalPopupActive();
                boolean nativeOpen = NativeBridge.isMenuOpen();

                if (modalActive) {
                    if (!mIsModalOpen) {
                        openModalDialog();
                    }
                } else if (nativeOpen) {
                    if (!mIsMenuOpen) {
                        openMenu();
                    }
                } else {
                    if (mIsModalOpen || mIsMenuOpen) {
                        collapseToLogo();
                    }
                }
                mMainHandler.postDelayed(this, 80);
            }
        }
    };

    private NativeOverlayManager(Context context) {
        mContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        try {
            mDensity = mContext.getResources().getDisplayMetrics().density;
            if (mDensity < 1.5f) mDensity = 2.0f;
            mLogoSizePx = (int) (76 * mDensity);
        } catch (Throwable t) {
            mLogoSizePx = 180;
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

                if (mIsAttached && mOverlayView != null) {
                    AppLogger.log(TAG, "Re-routing running native overlay to brand: " + mActiveBrandId);
                    NativeBridge.launchBrandOverlay(mActiveBrandId, hwid, hasActiveLicense, mDensity, activeKey, serverUrl);
                    return;
                }

                int layoutFlag;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                } else {
                    layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
                }

                DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
                int screenW = dm.widthPixels;
                int screenH = dm.heightPixels;
                boolean isLandscape = screenW > screenH;

                int initialW = mLogoSizePx;
                int initialH = mLogoSizePx;
                int initialGravity = Gravity.TOP | Gravity.START;

                if (!hasActiveLicense) {
                    mIsModalOpen = true;
                    int dialogW = isLandscape ? (int) (screenW * 0.48f) : (int) (screenW * 0.92f);
                    int dialogH = isLandscape ? (int) (screenH * 0.70f) : (int) (screenH * 0.55f);
                    int minW = (int) (360 * mDensity);
                    int maxW = (int) (480 * mDensity);
                    if (dialogW < minW) dialogW = minW;
                    if (dialogW > maxW) dialogW = maxW;
                    if (dialogW > screenW) dialogW = (int) (screenW * 0.95f);

                    int maxH = (int) (440 * mDensity);
                    if (dialogH > maxH) dialogH = maxH;
                    if (dialogH > screenH) dialogH = (int) (screenH * 0.90f);

                    initialW = dialogW;
                    initialH = dialogH;
                    initialGravity = Gravity.CENTER;
                }

                int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
                if (hasActiveLicense) {
                    flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                }

                mParams = new WindowManager.LayoutParams(
                        initialW,
                        initialH,
                        layoutFlag,
                        flags,
                        PixelFormat.TRANSLUCENT
                );
                if (!hasActiveLicense) {
                    mParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
                }
                mParams.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
                mParams.gravity = initialGravity;
                mParams.x = hasActiveLicense ? mLogoX : 0;
                mParams.y = hasActiveLicense ? mLogoY : 0;

                // Launch C++ ImGui overlay state machine with credentials
                NativeBridge.launchBrandOverlay(mActiveBrandId, hwid, hasActiveLicense, mDensity, activeKey, serverUrl);

                mOverlayView = new GLES3JNIView(mContext);
                setupTouchListener();

                mWindowManager.addView(mOverlayView, mParams);
                mIsAttached = true;
                mIsMenuOpen = false;
                mIsModalOpen = !hasActiveLicense;

                mMainHandler.removeCallbacks(mStateSyncRunnable);
                mMainHandler.postDelayed(mStateSyncRunnable, 200);

                AppLogger.log(TAG, "Native C++ Dear ImGui Overlay attached successfully (License active: " + hasActiveLicense + ")");
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to start native overlay", t);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchListener() {
        if (mOverlayView == null) return;

        mOverlayView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;
            private static final int CLICK_THRESHOLD = 15; // px

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event == null) return false;

                if (!mIsMenuOpen && !mIsModalOpen) {
                    // ─────────────────────────────────────────────────────────────
                    // LOGO MODE: Smooth dragging across screen & tap to open menu
                    // ─────────────────────────────────────────────────────────────
                    int action = event.getActionMasked();
                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = mParams.x;
                            initialY = mParams.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            isDragging = false;
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - initialTouchX;
                            float dy = event.getRawY() - initialTouchY;
                            if (Math.abs(dx) > CLICK_THRESHOLD || Math.abs(dy) > CLICK_THRESHOLD) {
                                isDragging = true;
                                mParams.x = initialX + (int) dx;
                                mParams.y = initialY + (int) dy;
                                mLogoX = mParams.x;
                                mLogoY = mParams.y;
                                try {
                                    mWindowManager.updateViewLayout(mOverlayView, mParams);
                                } catch (Throwable ignored) {}
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                            if (!isDragging) {
                                // Tap detected on floating logo: Expand menu!
                                openMenu();
                            }
                            return true;
                    }
                    return false;
                } else {
                    // ─────────────────────────────────────────────────────────────
                    // MODAL / MENU MODE: Forward touches directly to Dear ImGui
                    // ─────────────────────────────────────────────────────────────
                    int action = event.getActionMasked();
                    float x = event.getX();
                    float y = event.getY();
                    NativeBridge.handleTouch(action, x, y);

                    // Check state changes
                    if (!NativeBridge.isModalPopupActive() && !NativeBridge.isMenuOpen()) {
                        collapseToLogo();
                    }
                    return true;
                }
            }
        });
    }

    public void openModalDialog() {
        if (!mIsAttached || mOverlayView == null) return;

        mMainHandler.post(() -> {
            try {
                mIsModalOpen = true;
                mIsMenuOpen = false;

                DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
                int screenW = dm.widthPixels;
                int screenH = dm.heightPixels;
                boolean isLandscape = screenW > screenH;

                int dialogW = isLandscape ? (int) (screenW * 0.48f) : (int) (screenW * 0.92f);
                int dialogH = isLandscape ? (int) (screenH * 0.70f) : (int) (screenH * 0.55f);

                int minW = (int) (360 * dm.density);
                int maxW = (int) (480 * dm.density);
                if (dialogW < minW) dialogW = minW;
                if (dialogW > maxW) dialogW = maxW;
                if (dialogW > screenW) dialogW = (int) (screenW * 0.95f);

                int maxH = (int) (440 * dm.density);
                if (dialogH > maxH) dialogH = maxH;
                if (dialogH > screenH) dialogH = (int) (screenH * 0.90f);

                mParams.width = dialogW;
                mParams.height = dialogH;
                mParams.gravity = Gravity.CENTER;
                mParams.x = 0;
                mParams.y = 0;
                mParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                mParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

                mWindowManager.updateViewLayout(mOverlayView, mParams);
                AppLogger.log(TAG, "Overlay resized for Modal Dialog (" + dialogW + "x" + dialogH + ")");
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to resize modal dialog", t);
            }
        });
    }

    public void openMenu() {
        if (!mIsAttached || mOverlayView == null || mIsMenuOpen) return;

        mMainHandler.post(() -> {
            try {
                NativeBridge.setMenuOpen(true);
                mIsMenuOpen = true;
                mIsModalOpen = false;

                DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
                int screenW = dm.widthPixels;
                int screenH = dm.heightPixels;
                boolean isLandscape = screenW > screenH;

                // Authentic In-Game Golden Ratio Sizing
                int menuW = isLandscape ? (int) (screenW * 0.44f) : (int) (screenW * 0.90f);
                int menuH = isLandscape ? (int) (screenH * 0.78f) : (int) (screenH * 0.65f);

                int minW = (int) (380 * dm.density);
                int maxW = (int) (540 * dm.density);
                if (menuW < minW) menuW = minW;
                if (menuW > maxW) menuW = maxW;
                if (menuW > screenW) menuW = (int) (screenW * 0.95f);

                int maxH = (int) (650 * dm.density);
                if (menuH > maxH) menuH = maxH;
                if (menuH > screenH) menuH = (int) (screenH * 0.92f);

                mParams.width = menuW;
                mParams.height = menuH;
                mParams.gravity = Gravity.CENTER;
                mParams.x = 0;
                mParams.y = 0;
                mParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

                mWindowManager.updateViewLayout(mOverlayView, mParams);
                AppLogger.log(TAG, "Overlay expanded to tactical menu (" + menuW + "x" + menuH + ")");
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to expand menu", t);
            }
        });
    }

    public void collapseToLogo() {
        if (!mIsAttached || mOverlayView == null || (!mIsMenuOpen && !mIsModalOpen)) return;

        mMainHandler.post(() -> {
            try {
                NativeBridge.setMenuOpen(false);
                mIsMenuOpen = false;
                mIsModalOpen = false;

                mParams.width = mLogoSizePx;
                mParams.height = mLogoSizePx;
                mParams.gravity = Gravity.TOP | Gravity.START;
                mParams.x = mLogoX;
                mParams.y = mLogoY;
                mParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

                mWindowManager.updateViewLayout(mOverlayView, mParams);
                AppLogger.log(TAG, "Overlay collapsed back to 76dp floating logo");
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to collapse menu", t);
            }
        });
    }

    private void stopInternal() {
        mMainHandler.post(() -> {
            try {
                mMainHandler.removeCallbacks(mStateSyncRunnable);
                if (mIsAttached && mOverlayView != null) {
                    NativeBridge.closeOverlay();
                    if (mOverlayView.isAttachedToWindow()) {
                        mWindowManager.removeView(mOverlayView);
                    }
                    mOverlayView = null;
                    mIsAttached = false;
                    mIsMenuOpen = false;
                    mIsModalOpen = false;
                    AppLogger.log(TAG, "Native C++ Dear ImGui Overlay stopped cleanly");
                }
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to stop native overlay", t);
            }
        });
    }
}
