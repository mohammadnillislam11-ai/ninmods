package com.ninmods.main.core;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.ninmods.main.auth.AuthApiController;
import com.ninmods.main.auth.AuthManager;
import com.ninmods.main.overlay.NativeOverlayManager;

/**
 * Universal Native Bridge for C++ Dear ImGui Multi-Brand Engine.
 * Direct zero-copy communication with libninmods.so.
 */
public final class NativeBridge {
    private static final String TAG = "NativeBridge";

    static {
        try {
            System.loadLibrary("ninmods");
            Log.d(TAG, "libninmods.so successfully loaded");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load libninmods.so", e);
        }
    }

    private NativeBridge() {}

    /**
     * Initializes and launches the native Dear ImGui multi-brand overlay.
     */
    public static native void launchBrandOverlay(String brandId, String hwid, boolean isLicenseActive, float density, String prefillKey, String serverUrl);

    /**
     * Dispatches Android touch events directly to Dear ImGui io.MousePos / io.MouseDown.
     */
    public static native boolean handleTouch(int action, float x, float y);

    /**
     * Checks if any modal popup (License or Driver Alert) is active.
     */
    public static native boolean isModalPopupActive();

    /**
     * Relays asynchronous license verification result back to C++ LicensePopup.
     */
    public static native void onLicenseResult(boolean success, String message);

    /**
     * Checks if the active brand tactical menu is currently open/expanded.
     */
    public static native boolean isMenuOpen();

    /**
     * Sets the expanded/collapsed state of the tactical menu.
     */
    public static native void setMenuOpen(boolean open);

    /**
     * Checks if the native overlay controller is active.
     */
    public static native boolean isOverlayActive();

    /**
     * Cleanly shuts down the native overlay.
     */
    public static native void closeOverlay();

    /**
     * Invoked directly from C++ LicensePopup modal when user clicks "VERIFY LICENSE".
     * Routes verification through Android's secure SSL networking (AuthApiController).
     */
    public static void verifyLicenseFromNative(String key, String brand) {
        Context context = NativeOverlayManager.getContext();
        if (context == null) {
            Log.e(TAG, "verifyLicenseFromNative: context is null");
            onLicenseResult(false, "App context unavailable");
            return;
        }

        Log.d(TAG, "Verifying license key from native C++ popup: " + key + " (brand: " + brand + ")");
        AuthApiController.verifyLicenseKey(context, key, new AuthApiController.ApiCallback() {
            @Override
            public void onSuccess(String message) {
                Log.d(TAG, "License successfully verified via native C++ bridge: " + message);
                // Save session in SharedPreferences
                try {
                    AuthManager.saveUserLicenseSession(context, key, System.currentTimeMillis() + (30L * 86400000L), 30);
                } catch (Throwable ignored) {}
                onLicenseResult(true, message);
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Native license verification failed: " + errorMessage);
                onLicenseResult(false, errorMessage);
            }
        });
    }

    /**
     * Helper to open Driver Loader for Root users from the C++ Driver Alert popup.
     */
    public static void openLoader(Context context) {
        if (context == null) return;
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage("com.ninmods.loader");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to launch Driver Loader", t);
        }
    }
}
