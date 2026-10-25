package com.ninmods.main.core;

import android.Manifest;
import android.app.Activity;
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

public class MainActivity extends BaseActivity {

    private static final int REQUEST_LEGACY_STORAGE_PERMISSION = 1003;

    // Sequential Permission States
    private static final int STEP_OVERLAY = 1;
    private static final int STEP_STORAGE = 2;
    private static final int STEP_DONE = 3;

    private int currentStep = STEP_OVERLAY;
    private boolean isNavigatingAway = false;
    private boolean hasAutoPromptedOverlay = false;
    private boolean isAppStarted = false;

    private TextView tvStatus;
    private Button btnAction;
    private Button btnContinueWithoutStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize logging & telemetry subsystem with application context
        AppLogger.init(this);
        TelegramLogUploader.init(this);
        AppLogger.log("SYSTEM", "=== NINMODS Engine Initialized (Version: " + getString(R.string.app_version) + ") ===");

        // Always show branded loading view immediately (eliminates blank / black screens)
        buildSplashView();

        // Request SuperUser privileges & set permissive mode in background thread
        new Thread(() -> {
            boolean hasRoot = requestSuperUser();
            AppLogger.log("SYSTEM", "SuperUser (Root) Access Verified: " + (hasRoot ? "GRANTED" : "DENIED"));
            try {
                System.loadLibrary("ninmods");
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }).start();

        // Start step-by-step permission sequence
        currentStep = STEP_OVERLAY;
        executeCurrentStep();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isNavigatingAway) {
            isNavigatingAway = false;
            // Delay check by 250ms to allow Android OS AppOps service to commit permission state
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (isFinishing() || isAppStarted) return;
                if (currentStep == STEP_OVERLAY) {
                    if (isOverlayGranted()) {
                        currentStep = STEP_STORAGE;
                    }
                } else if (currentStep == STEP_STORAGE) {
                    if (isStorageGranted()) {
                        currentStep = STEP_DONE;
                    }
                }
                executeCurrentStep();
            }, 250);
        } else {
            // Check in case user returned without isNavigatingAway trigger or state changed
            if (currentStep == STEP_OVERLAY && isOverlayGranted()) {
                currentStep = STEP_STORAGE;
                executeCurrentStep();
            } else if (currentStep == STEP_STORAGE && isStorageGranted()) {
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

        // Transparent root allows BaseActivity's blurred particle animation to shine through
        root.setBackgroundColor(Color.TRANSPARENT);

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

        // Auto prompt only on initial step entrance, never in an onResume loop
        if (!hasAutoPromptedOverlay) {
            hasAutoPromptedOverlay = true;
            isNavigatingAway = true;
            launchOverlaySettings();
        }
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
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
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
        try {
            process = Runtime.getRuntime().exec("su");
            java.io.DataOutputStream os = new java.io.DataOutputStream(process.getOutputStream());
            os.writeBytes("id\n");
            os.writeBytes("setenforce 0 2>/dev/null\n");
            
            // Universal Character Device Auto-Node Discovery & Symlink to /dev/BYNb
            os.writeBytes("for s in /sys/dev/char/*/uevent; do if [ -e \"$s\" ]; then " +
                    "dname=\"\"; maj=\"\"; min=\"\"; " +
                    "eval \"$(grep -E '^(MAJOR|MINOR|DEVNAME)=' \"$s\")\"; " +
                    "if [ -n \"$DEVNAME\" ] && [ -n \"$MAJOR\" ] && [ -n \"$MINOR\" ]; then " +
                    "[ ! -e \"/dev/$DEVNAME\" ] && mknod \"/dev/$DEVNAME\" c \"$MAJOR\" \"$MINOR\" 2>/dev/null; " +
                    "chmod 666 \"/dev/$DEVNAME\" 2>/dev/null; " +
                    "if [ \"$MAJOR\" -ge 200 ] && [ ! -e \"/dev/BYNb\" ]; then " +
                    "ln -sf \"/dev/$DEVNAME\" /dev/BYNb 2>/dev/null; chmod 666 /dev/BYNb 2>/dev/null; " +
                    "fi; fi; fi; done\n");

            os.writeBytes("for s in /sys/class/*/*/dev; do if [ -e \"$s\" ]; then " +
                    "dname=$(echo \"$s\" | awk -F'/' '{print $(NF-1)}'); " +
                    "mm=$(cat \"$s\" 2>/dev/null); maj=$(echo \"$mm\" | cut -d: -f1); min=$(echo \"$mm\" | cut -d: -f2); " +
                    "if [ -n \"$maj\" ] && [ -n \"$min\" ]; then " +
                    "[ ! -e \"/dev/$dname\" ] && mknod \"/dev/$dname\" c \"$maj\" \"$min\" 2>/dev/null; " +
                    "chmod 666 \"/dev/$dname\" 2>/dev/null; " +
                    "if [ ! -e \"/dev/BYNb\" ]; then " +
                    "ln -sf \"/dev/$dname\" /dev/BYNb 2>/dev/null; chmod 666 /dev/BYNb 2>/dev/null; " +
                    "fi; fi; fi; done\n");

            os.writeBytes("chmod 666 /dev/BYNb* /dev/mem_tool* /proc/uevents_records 2>/dev/null\n");
            os.writeBytes("exit\n");
            os.flush();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Throwable ignored) {}
            }
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
