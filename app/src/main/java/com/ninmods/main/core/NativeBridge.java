package com.ninmods.main.core;

import android.content.Context;
import android.content.Intent;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;
import android.app.AlertDialog;

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
        AuthApiController.verifyLicenseKey(context, key, brand, new AuthApiController.ApiCallback() {
            @Override
            public void onSuccess(String message) {
                Log.d(TAG, "License successfully verified via native C++ bridge: " + message);
                // Save session in SharedPreferences
                try {
                    AuthManager.updateActiveLicense(context, key, "30_days", "", 30 * 86400);
                    AuthManager.setActiveLicenseKey(context, key);
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

    /**
     * Updates key directly in C++ LicensePopup from Java (clipboard or dialog).
     */
    public static native void setKeyFromJava(String key);

    /**
     * Reads primary clip text from Android ClipboardManager.
     */
    public static String getClipboardText() {
        Context context = NativeOverlayManager.getContext();
        if (context == null) return "";
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text != null) return text.toString().trim();
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to read clipboard", t);
        }
        return "";
    }

    /**
     * Shows a 100% reliable native Android AlertDialog with virtual keyboard for typing license keys.
     */
    public static void showKeyInputDialog(String currentKey) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Context context = NativeOverlayManager.getContext();
            if (context == null) return;

            final EditText input = new EditText(context);
            input.setTextColor(Color.WHITE);
            input.setHintTextColor(Color.GRAY);
            input.setHint("Paste or enter license key...");
            if (currentKey != null && !currentKey.isEmpty()) {
                input.setText(currentKey);
                input.setSelection(input.getText().length());
            }
            input.setSingleLine(true);
            input.setPadding(40, 30, 40, 30);

            AlertDialog dialog = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("NINMODS VIP LICENSE")
                .setMessage("Enter or paste your subscription key:")
                .setView(input)
                .setPositiveButton("VERIFY", (d, which) -> {
                    String key = input.getText().toString().trim();
                    if (!key.isEmpty()) {
                        setKeyFromJava(key);
                        verifyLicenseFromNative(key, NativeOverlayManager.getActiveBrandId());
                    }
                })
                .setNeutralButton("PASTE", (d, which) -> {
                    String clip = getClipboardText();
                    if (!clip.isEmpty()) {
                        input.setText(clip);
                        input.setSelection(input.getText().length());
                        setKeyFromJava(clip);
                    }
                })
                .setNegativeButton("CANCEL", null)
                .create();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            } else {
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
            }
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialog.show();
        });
    }
}
