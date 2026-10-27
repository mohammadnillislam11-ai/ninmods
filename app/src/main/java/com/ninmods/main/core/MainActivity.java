package com.ninmods.main.core;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ninmods.main.R;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class MainActivity extends BaseActivity {

    @Override
    protected boolean enableParticleBackground() {
        return false;
    }

    private static final int REQUEST_LEGACY_STORAGE_PERMISSION = 1003;

    // Sequential Permission States: Superuser -> Overlay -> Storage -> Done
    private static final int STEP_ROOT = 0;
    private static final int STEP_OVERLAY = 1;
    private static final int STEP_STORAGE = 2;
    private static final int STEP_DONE = 3;

    private int currentStep = STEP_ROOT;
    private boolean isNavigatingAway = false;
    private boolean hasAutoPromptedOverlay = false;
    private boolean isAppStarted = false;
    private boolean userBypassedStorage = false;

    private TextView tvStatus;
    private Button btnAction;
    private Button btnContinueWithoutStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // Initialize logging & telemetry subsystem with application context
            AppLogger.init(this);
            AppLogger.log("SYSTEM", "=== NINMODS Engine Initialized (Version: " + getString(R.string.app_version) + ") ===");

            // Always show branded loading view immediately (eliminates blank / black screens)
            buildSplashView();

            // Start step-by-step permission sequence starting with official SuperUser request
            currentStep = STEP_ROOT;
            executeCurrentStep();
        } catch (Throwable t) {
            // The window background is a near black colour, so an exception
            // before buildSplashView() used to leave the user on a completely
            // black screen. Show the failure instead.
            AppLogger.logE("SYSTEM", "Startup failed: " + t.getMessage(), t);
            showFatalError("NINMODS could not start",
                    "Something went wrong while preparing the app on this device. "
                            + "Please reload the app or reinstall the latest release.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isAppStarted || isFinishing()) return;

        if (currentStep == STEP_ROOT) {
            // Background thread is currently verifying Superuser privileges, do nothing
            return;
        }

        if (isNavigatingAway) {
            isNavigatingAway = false;
            // Delay check by 250ms to allow Android OS AppOps service to commit permission state
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (isFinishing() || isAppStarted) return;
                if (currentStep == STEP_OVERLAY) {
                    if (isOverlayGranted()) {
                        currentStep = userBypassedStorage || isStorageGranted() ? STEP_DONE : STEP_STORAGE;
                    }
                } else if (currentStep == STEP_STORAGE) {
                    if (isStorageGranted() || userBypassedStorage) {
                        currentStep = STEP_DONE;
                    }
                }
                executeCurrentStep();
            }, 250);
        } else {
            // Check in case user returned without isNavigatingAway trigger or state changed
            if (currentStep == STEP_OVERLAY && isOverlayGranted()) {
                currentStep = userBypassedStorage || isStorageGranted() ? STEP_DONE : STEP_STORAGE;
                executeCurrentStep();
            } else if (currentStep == STEP_STORAGE && (isStorageGranted() || userBypassedStorage)) {
                currentStep = STEP_DONE;
                executeCurrentStep();
            }
        }
    }

    private void buildSplashView() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Solid dark background ensures 0ms black screen latency on all hardware
        root.setBackgroundColor(Color.parseColor("#0A0C12"));

        LinearLayout layout = new LinearLayout(this);
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(dp(28), dp(28), dp(28), dp(28));



        ImageView logo = new ImageView(this);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(76), dp(76));
        logoLp.bottomMargin = dp(14);
        logo.setLayoutParams(logoLp);
        logo.setImageResource(R.drawable.nin_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        layout.addView(logo);

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        tvStatus = new TextView(this);
        tvStatus.setText("Configuring Permissions...");
        tvStatus.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, dp(8), 0, dp(16));
        layout.addView(tvStatus);

        btnAction = new Button(this);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(dp(220), dp(42));
        btnAction.setLayoutParams(btnLp);
        btnAction.setText("GRANT PERMISSION");
        btnAction.setTextColor(AppColors.WHITE);
        btnAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnAction.setTypeface(Typeface.DEFAULT_BOLD);
        btnAction.setVisibility(View.GONE);

        GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END}
        );
        btnBg.setCornerRadius(dp(8));
        btnAction.setBackground(btnBg);
        layout.addView(btnAction);

        btnContinueWithoutStorage = new Button(this);
        LinearLayout.LayoutParams continueLp = new LinearLayout.LayoutParams(dp(220), dp(42));
        continueLp.topMargin = dp(6);
        btnContinueWithoutStorage.setLayoutParams(continueLp);
        btnContinueWithoutStorage.setText("CONTINUE WITHOUT STORAGE");
        btnContinueWithoutStorage.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        btnContinueWithoutStorage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnContinueWithoutStorage.setTypeface(Typeface.DEFAULT_BOLD);
        btnContinueWithoutStorage.setBackgroundColor(Color.TRANSPARENT);
        btnContinueWithoutStorage.setVisibility(View.GONE);
        btnContinueWithoutStorage.setOnClickListener(v -> {
            userBypassedStorage = true;
            currentStep = STEP_DONE;
            executeCurrentStep();
        });
        layout.addView(btnContinueWithoutStorage);

        root.addView(layout);
        setContentView(root);
    }

    private void executeCurrentStep() {
        if (isFinishing() || isAppStarted) return;

        switch (currentStep) {
            case STEP_ROOT:
                handleRootStep();
                break;
            case STEP_OVERLAY:
                handleOverlayStep();
                break;
            case STEP_STORAGE:
                handleStorageStep();
                break;
            case STEP_DONE:
            default:
                startApp();
                break;
        }
    }

    // Step 0: SuperUser Permission (Official System Dialog via native su)
    private void handleRootStep() {
        tvStatus.setText("Requesting Superuser Privileges...");
        btnAction.setVisibility(View.GONE);
        btnContinueWithoutStorage.setVisibility(View.GONE);

        new Thread(() -> {
            boolean hasRoot = requestSuperUser();
            AppLogger.log("SYSTEM", "SuperUser (Root) Access Verified: " + (hasRoot ? "GRANTED" : "DENIED"));
            try {
                System.loadLibrary("ninmods");
            } catch (Throwable t) {
                AppLogger.logE("SYSTEM", "Failed to load ninmods library: " + t.getMessage(), t);
            }
            runOnUiThread(() -> {
                if (isFinishing() || isAppStarted) return;
                currentStep = STEP_OVERLAY;
                executeCurrentStep();
            });
        }).start();
    }

    // Step 1: Overlay Permission
    private void handleOverlayStep() {
        if (isOverlayGranted()) {
            currentStep = STEP_STORAGE;
            executeCurrentStep();
            return;
        }

        tvStatus.setText("Overlay permission required for tactical HUD");
        btnAction.setText("GRANT OVERLAY ACCESS");
        btnAction.setVisibility(View.VISIBLE);
        btnAction.setOnClickListener(v -> {
            isNavigatingAway = true;
            launchOverlaySettings();
        });
    }

    private void launchOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        }
    }

    public static boolean hasOverlayPermission(android.content.Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        try {
            if (Settings.canDrawOverlays(context)) {
                return true;
            }
        } catch (Throwable ignored) {}

        try {
            android.app.AppOpsManager appOps = (android.app.AppOpsManager) context.getSystemService(android.content.Context.APP_OPS_SERVICE);
            if (appOps != null) {
                int mode = appOps.checkOpNoThrow(
                        "android:system_alert_window",
                        android.os.Process.myUid(),
                        context.getPackageName()
                );
                return mode == android.app.AppOpsManager.MODE_ALLOWED;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private boolean isOverlayGranted() {
        return hasOverlayPermission(this);
    }

    // Step 2: Storage Permission (All Files / Super Storage)
    private void handleStorageStep() {
        if (isStorageGranted()) {
            currentStep = STEP_DONE;
            executeCurrentStep();
            return;
        }

        tvStatus.setText("Storage access lets the app save diagnostic logs to Downloads.");
        btnAction.setText("ALLOW STORAGE ACCESS");
        btnAction.setVisibility(View.VISIBLE);
        btnContinueWithoutStorage.setVisibility(View.VISIBLE);
        btnAction.setOnClickListener(v -> {
            isNavigatingAway = true;
            requestStoragePermission();
        });
    }

    private boolean isStorageGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        isNavigatingAway = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
             } catch (Exception e) {
                 try {
                     Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                     startActivity(intent);
                 } catch (Exception ignored) {}
             }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_LEGACY_STORAGE_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LEGACY_STORAGE_PERMISSION) {
            if (isStorageGranted()) {
                currentStep = STEP_DONE;
                executeCurrentStep();
            } else {
                handleStorageStep();
            }
        }
    }

    private void startApp() {
        if (isAppStarted || isFinishing()) return;
        isAppStarted = true;

        btnAction.setVisibility(View.GONE);
        btnContinueWithoutStorage.setVisibility(View.GONE);

        // Persistent Session Check (like Google account / digital token session)
        if (com.ninmods.main.auth.AuthManager.isLoggedIn(this)) {
            tvStatus.setText("Session Restored. Opening Dashboard...");
            String sessionToken = com.ninmods.main.auth.AuthManager.getSessionToken(this);
            String hwid = com.ninmods.main.auth.AuthApiController.getHwid(this);
            long remSec = com.ninmods.main.auth.AuthManager.getRemainingSeconds(this);
            if (sessionToken != null && !sessionToken.isEmpty()) {
                com.ninmods.main.auth.SecurityVault.setNativeSession(sessionToken, hwid, remSec);
            }
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
            return;
        }

        tvStatus.setText("Initializing Login...");
        Intent intent = new Intent(this, com.ninmods.main.auth.LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    public static boolean requestSuperUser() {
        Process process = null;
        DataOutputStream os = null;
        try {
            // Official standard interactive su invocation (identical to loader software)
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());

            // Drain stdout & stderr in background to avoid Linux pipe buffer deadlocks
            drainStream(process.getInputStream());
            drainStream(process.getErrorStream());

            os.writeBytes("setenforce 0 2>/dev/null\n");
            os.writeBytes("chmod 666 /dev/BYNb* /dev/mem_tool* /dev/nh* /dev/wanbai* /proc/uevents_records 2>/dev/null\n");
            os.writeBytes("exit\n");
            os.flush();

            /* Cleanly wait for the official Magisk / KernelSU / APatch prompt
             * response. Give user a reasonable window (10s) without hanging indefinitely. */
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                AppLogger.log("SYSTEM", "requestSuperUser timed out after 10s - official prompt was not answered");
                process.destroy();
                return false;
            }
            int exitVal = process.exitValue();
            return exitVal == 0;
        } catch (Throwable t) {
            AppLogger.log("SYSTEM", "requestSuperUser exception: " + t.getMessage());
            if (process != null) {
                try { process.destroy(); } catch (Throwable ignored) {}
            }
            return false;
        } finally {
            if (os != null) {
                try { os.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void drainStream(final InputStream stream) {
        if (stream == null) return;
        new Thread(() -> {
            try {
                byte[] buf = new byte[512];
                while (stream.read(buf) != -1) {
                    // Drain and discard
                }
                stream.close();
            } catch (Throwable ignored) {}
        }).start();
    }


    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
