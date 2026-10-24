package com.ninmods.main.core;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

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
     * @param brandId "nin", "bull", "cross", "delta"
     * @param hwid Device hardware identifier for license check
     * @param isLicenseActive true if license is already confirmed active
     */
    public static native void launchBrandOverlay(String brandId, String hwid, boolean isLicenseActive);

    /**
     * Dispatches Android touch events directly to Dear ImGui io.MousePos / io.MouseDown.
     * @return true if ImGui captured the touch event, false if it passed through to the background game
     */
    public static native boolean handleTouch(int action, float x, float y);

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
