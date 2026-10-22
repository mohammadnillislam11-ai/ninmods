package nin.app.core;

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

import nin.app.R;

public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_ALL_FILES_PERMISSION = 1002;
    private static final int REQUEST_LEGACY_STORAGE_PERMISSION = 1003;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1004;
    private static final int REQUEST_LOCATION_PERMISSION = 1005;

    // Sequential Permission States
    private static final int STEP_OVERLAY = 1;
    private static final int STEP_STORAGE = 2;
    private static final int STEP_NOTIFICATION = 3;
    private static final int STEP_LOCATION = 4;
    private static final int STEP_DONE = 5;

    private int currentStep = STEP_OVERLAY;
    private boolean isNavigatingAway = false;
    private boolean isAppStarted = false;

    private TextView tvStatus;
    private Button btnAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Always show branded loading view immediately (eliminates blank / black screens)
        buildSplashView();

        // Copy daemon in background thread to avoid freezing UI
        new Thread(() -> {
            copyDaemon(MainActivity.this);
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
            // Check if current step was granted upon returning
            if (currentStep == STEP_OVERLAY) {
                if (isOverlayGranted()) {
                    currentStep = STEP_STORAGE;
                }
            } else if (currentStep == STEP_STORAGE) {
                if (isStorageGranted()) {
                    currentStep = STEP_NOTIFICATION;
                }
            }
            executeCurrentStep();
        }
    }

    private void buildSplashView() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{AppColors.MAGENTA_SCREEN_BG, AppColors.BLACK}
        );
        root.setBackground(bg);

        LinearLayout layout = new LinearLayout(this);
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(dp(24), dp(24), dp(24), dp(24));

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
            case STEP_NOTIFICATION:
                handleNotificationStep();
                break;
            case STEP_LOCATION:
                handleLocationStep();
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
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        // Auto prompt first time
        if (!isNavigatingAway) {
            isNavigatingAway = true;
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private boolean isOverlayGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    // Step 2: Storage Permission (All Files / Super Storage)
    private void handleStorageStep() {
        if (isStorageGranted()) {
            currentStep = STEP_NOTIFICATION;
            executeCurrentStep();
            return;
        }

        tvStatus.setText("Storage access required for game assets");
        btnAction.setText("GRANT STORAGE ACCESS");
        btnAction.setVisibility(View.VISIBLE);
        btnAction.setOnClickListener(v -> requestStoragePermission());

        // Auto prompt first time
        requestStoragePermission();
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

    // Step 3: Notification Permission
    private void handleNotificationStep() {
        if (Build.VERSION.SDK_INT >= 33 && getApplicationInfo().targetSdkVersion >= 33) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                tvStatus.setText("Requesting Notification Access...");
                btnAction.setVisibility(View.GONE);
                ActivityCompat.requestPermissions(this,
                        new String[]{"android.permission.POST_NOTIFICATIONS"},
                        REQUEST_NOTIFICATION_PERMISSION);
                return;
            }
        }
        currentStep = STEP_LOCATION;
        executeCurrentStep();
    }

    // Step 4: Location Permission
    private void handleLocationStep() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            tvStatus.setText("Requesting Location Access...");
            btnAction.setVisibility(View.GONE);
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
            return;
        }
        currentStep = STEP_DONE;
        executeCurrentStep();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LEGACY_STORAGE_PERMISSION) {
            currentStep = STEP_NOTIFICATION;
            executeCurrentStep();
        } else if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            currentStep = STEP_LOCATION;
            executeCurrentStep();
        } else if (requestCode == REQUEST_LOCATION_PERMISSION) {
            currentStep = STEP_DONE;
            executeCurrentStep();
        }
    }

    private void startApp() {
        if (isAppStarted || isFinishing()) return;
        isAppStarted = true;

        tvStatus.setText("Initializing Login...");
        btnAction.setVisibility(View.GONE);

        Intent intent = new Intent(this, nin.app.auth.LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    public static void copyDaemon(Activity context) {
        String abi = android.os.Build.SUPPORTED_ABIS[0];
        String assetName = abi.contains("x86") ? "Titoo1" : "Titoo";
        String outPath = context.getCacheDir().getAbsolutePath() + "/" + assetName;

        try {
            java.io.InputStream in = context.getAssets().open(assetName);
            java.io.File outFile = new java.io.File(outPath);
            java.io.FileOutputStream out = new java.io.FileOutputStream(outFile);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.flush();
            out.close();
            Runtime.getRuntime().exec("chmod 777 " + outPath);
        } catch (Exception e) {
            e.printStackTrace();
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
