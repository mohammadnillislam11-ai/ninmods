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
     * Channel identifier of the Ring-0 kernel driver used by the in-app engine.
     */
    public static final int CHANNEL_RT = 0;

    /**
     * Channel identifier of the root daemon that streams the scene over IPC.
     */
    public static final int CHANNEL_QX = 1;

    /**
     * Selects the memory channel the engine has to use.
     *
     * The two channels are completely independent: the RT channel reads the
     * game memory through the kernel driver, the QX channel receives the
     * scene from the root daemon. The engine never switches the channel on
     * its own, so a failing channel can not activate the other one.
     *
     * @param channel CHANNEL_RT or CHANNEL_QX
     */
    public static native void setActiveChannel(int channel);

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
     * Runs a shell script as root through Magisk' su and returns its exit code.
     *
     * The app process must NOT fork(). The Android app sandbox answers a
     * plain fork() with SIGSYS and kills the whole process, which is why
     * system() and popen() are unusable in this app. Runtime.exec() on the
     * Java side uses the allowed posix_spawn() path, so the spawn has to be
     * done here and the native side calls this method through the JNI.
     *
     * @param script the shell script to run as root
     * @return the exit code of the script, or -1 when it could not run
     */
    public static int runAsRoot(String script) {
        if (script == null || script.isEmpty()) {
            return -1;
        }
        try {
            Process process = new ProcessBuilder("su")
                    .redirectErrorStream(true)
                    .start();

            // Feed script directly into root shell standard input
            try (java.io.OutputStream os = process.getOutputStream()) {
                os.write(script.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.write("\nexit\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
            }

            final java.util.concurrent.atomic.AtomicInteger exitCode =
                    new java.util.concurrent.atomic.AtomicInteger(-1);

            // Drain the output so the pipe can never fill up and deadlock.
            Thread drainer = new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        AppLogger.log(TAG, "[SU] " + line);
                    }
                    exitCode.set(process.waitFor());
                } catch (Throwable ignored) {}
            }, "runAsRootDrain");
            drainer.start();

            /* A broken or half installed root binary can sit forever without
             * printing a single byte - the old waitFor() then blocked this
             * thread (and, on the UI path, the whole app) until Android
             * killed it. The wait is now bounded. */
            drainer.join(25000);
            if (drainer.isAlive()) {
                process.destroyForcibly();
                drainer.interrupt();
                Log.e(TAG, "runAsRoot timed out after 25s - root shell unresponsive");
                return -1;
            }
            return exitCode.get();
        } catch (Throwable t) {
            Log.e(TAG, "runAsRoot failed", t);
            AppLogger.logE(TAG, "runAsRoot failed: " + t.getMessage(), t);
            return -1;
        }
    }

    /**
     * Direct Semi-JNI switch & hack control for Android Floating Window.
     */
    public static native void setFeatureState(int featId, boolean state);
    public static native void setFeatureFloat(int featId, float value);
    /**
     * Boolean helper. It only writes the STATE channel.
     * It used to write BOTH the float and the state channel, which meant
     * that a checkbox with id 101 also overwrote "MAX TARGET DISTANCE"
     * (a float id) with 0 or 1 metres.
     */
    public static void setFeatureEnabled(int featId, int value) {
        setFeatureState(featId, value != 0);
    }
    public static native boolean getFeatureState(int featId);
    /**
     * Reads the live value of a slider / dropdown out of the C++ engine.
     *
     * The menus need this to show the real configuration instead of a hard
     * coded one: without a reader every menu had to invent its initial
     * slider position and silently overwrote the engine when it was opened.
     *
     * @return the live value, or -1 when the id is not a slider / dropdown.
     */
    public static native float getFeatureFloat(int featId);
    public static native boolean isEmulatorDetected();
    public static native String getConnectionMode();
    public static native boolean isShellDaemonRunning();
    public static native boolean isKernelDriverConnected();
    public static native int getDetectedGamePid();
    public static native String getDetectedGameTitle();

    /**
     * Hands the text of the ESP info line over to the native renderer.
     * The native side draws it at the bottom of the screen. The value is the
     * name of the white label build, so no brand is hardcoded in the native
     * code.
     */
    public static native void setEspWatermark(String text);

    /**
     * The real memory path of the engine, for the telemetry panel.
     * The panel used to show a hardcoded string that did not match the
     * engine - the engine reads and writes through the kernel driver.
     */
    public static native String getMemoryAccessMode();
    public static native boolean isLiveGameDetected();
    public static native void drawEspNative(com.ninmods.main.esp.ESPView espView, android.graphics.Canvas canvas, int width, int height);
    public static native String getNinliteStateJson();
    public static native void startNinliteEngineNative(String nativeLibDir);
    public static void startNinliteEngineNative() {
        startNinliteEngineNative("");
    }
    public static native void stopNinliteEngineNative();
    public static native String getEngineLiveStatus();
    public static native boolean isNinliteSetupDone();


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
            DriverManager.launchDriverLoader(context);
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
