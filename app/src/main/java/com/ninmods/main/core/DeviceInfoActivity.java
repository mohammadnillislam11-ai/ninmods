package com.ninmods.main.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ninmods.main.R;

public class DeviceInfoActivity extends BaseActivity {

    private final ExecutorService driverStatusExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int driverStatusRequest;

    private LinearLayout driverCard;
    private TextView tvDriverStatusValue;
    private TextView tvDriverNodeValue;
    private TextView tvDriverIdValue;
    private TextView tvDriverPermValue;
    private TextView tvKernelModuleValue;
    private TextView tvLoaderStatusValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Automatically re-inquire driver whenever user returns from Loader or background
        refreshDriverStatus();
    }

    @Override
    protected void onDestroy() {
        driverStatusExecutor.shutdownNow();
        super.onDestroy();
    }

    private void buildView() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout layout = new LinearLayout(this);
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        layout.setOrientation(LinearLayout.VERTICAL);

        // 1. Header (Clean, borderless)
        LinearLayout header = new LinearLayout(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), 0, dp(16), 0);
        header.setBackgroundColor(AppColors.MAGENTA_SURFACE);

        Button backBtn = new Button(this);
        backBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        backBtn.setText("<");
        backBtn.setTextColor(AppColors.WHITE);
        backBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        backBtn.setTypeface(Typeface.DEFAULT_BOLD);
        backBtn.setBackgroundColor(Color.TRANSPARENT);
        backBtn.setOnClickListener(v -> finish());
        header.addView(backBtn);

        TextView title = new TextView(this);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleLp.leftMargin = dp(8);
        title.setLayoutParams(titleLp);
        title.setText("DEVICE INFORMATION & TELEMETRY");
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        header.addView(title);

        layout.addView(header);

        // 2. Scrollable Body
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(this);
        content.setLayoutParams(new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(24));

        // =====================================================================
        // 0. RING 0 KERNEL DRIVER STATUS CARD (PROMINENT AT TOP)
        // =====================================================================
        content.addView(createSectionHeader("RING 0 KERNEL DRIVER STATUS"));
        driverCard = createCardContainer();

        // Row 1: Status Badge
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setPadding(0, dp(6), 0, dp(6));

        TextView sl = new TextView(this);
        sl.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        sl.setText("Driver State");
        sl.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        sl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusRow.addView(sl);

        tvDriverStatusValue = new TextView(this);
        tvDriverStatusValue.setText("Checking Driver...");
        tvDriverStatusValue.setTextColor(Color.parseColor("#FFD54F"));
        tvDriverStatusValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvDriverStatusValue.setTypeface(Typeface.DEFAULT_BOLD);
        statusRow.addView(tvDriverStatusValue);
        driverCard.addView(statusRow);

        // Row 2: Active Node
        View rNode = createDynamicRow("Active Device Node", out -> tvDriverNodeValue = out);
        driverCard.addView(rNode);

        // Row 3: Driver Device ID
        View rId = createDynamicRow("Driver Device ID", out -> tvDriverIdValue = out);
        driverCard.addView(rId);

        // Row 4: Channel & IOCTL
        driverCard.addView(createRow("IOCTL Protocol", "0x801 (Read), 0x802 (Write), 0x803 (Base)"));

        // Row 5: Permissions
        View rPerm = createDynamicRow("Node Access", out -> tvDriverPermValue = out);
        driverCard.addView(rPerm);

        // Row 6: Kernel Module
        View rMod = createDynamicRow("Kernel Module", out -> tvKernelModuleValue = out);
        driverCard.addView(rMod);

        // Row 7: Loader App
        View rLoader = createDynamicRow("Driver Loader App", out -> tvLoaderStatusValue = out);
        driverCard.addView(rLoader);

        // Action Buttons Row inside Driver Card
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(10), 0, dp(4));

        Button btnRefresh = new Button(this);
        LinearLayout.LayoutParams refLp = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
        refLp.rightMargin = dp(6);
        btnRefresh.setLayoutParams(refLp);
        btnRefresh.setText("RE-CHECK DRIVER");
        btnRefresh.setTextColor(AppColors.WHITE);
        btnRefresh.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnRefresh.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable refBg = new GradientDrawable();
        refBg.setColor(Color.parseColor("#6A1B9A"));
        refBg.setCornerRadius(dp(6));
        btnRefresh.setBackground(refBg);
        btnRefresh.setOnClickListener(v -> {
            refreshDriverStatus();
            Toast.makeText(this, "Checking driver status...", Toast.LENGTH_SHORT).show();
        });
        btnRow.addView(btnRefresh);

        Button btnOpenLoader = new Button(this);
        LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
        loadLp.leftMargin = dp(6);
        btnOpenLoader.setLayoutParams(loadLp);
        btnOpenLoader.setText("OPEN DRIVER LOADER");
        btnOpenLoader.setTextColor(AppColors.WHITE);
        btnOpenLoader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnOpenLoader.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable loadBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END}
        );
        loadBg.setCornerRadius(dp(6));
        btnOpenLoader.setBackground(loadBg);
        btnOpenLoader.setOnClickListener(v -> DriverManager.launchDriverLoader(this));
        btnRow.addView(btnOpenLoader);

        driverCard.addView(btnRow);
        content.addView(driverCard);

        // =====================================================================
        // 1. Device Hardware Card
        // =====================================================================
        content.addView(createSectionHeader("HARDWARE & SYSTEM"));
        LinearLayout hwCard = createCardContainer();
        hwCard.addView(createRow("Manufacturer", Build.MANUFACTURER.toUpperCase()));
        hwCard.addView(createRow("Model", Build.MODEL));
        hwCard.addView(createRow("Product / Board", Build.PRODUCT + " (" + Build.BOARD + ")"));
        hwCard.addView(createRow("Hardware", Build.HARDWARE));
        hwCard.addView(createRow("CPU ABI", Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "aarch64"));
        hwCard.addView(createRow("OS Architecture", System.getProperty("os.arch")));
        content.addView(hwCard);

        // =====================================================================
        // 2. Android OS Card
        // =====================================================================
        content.addView(createSectionHeader("ANDROID PLATFORM"));
        LinearLayout osCard = createCardContainer();
        osCard.addView(createRow("Android Version", "Android " + Build.VERSION.RELEASE));
        osCard.addView(createRow("API Level", String.valueOf(Build.VERSION.SDK_INT)));
        osCard.addView(createRow("Security Patch", Build.VERSION.SECURITY_PATCH != null ? Build.VERSION.SECURITY_PATCH : "N/A"));
        osCard.addView(createRow("Kernel Version", getKernelVersion()));
        content.addView(osCard);

        // =====================================================================
        // 3. Display & Memory Card
        // =====================================================================
        content.addView(createSectionHeader("DISPLAY & MEMORY"));
        LinearLayout dispCard = createCardContainer();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        dispCard.addView(createRow("Resolution", dm.widthPixels + " x " + dm.heightPixels));
        dispCard.addView(createRow("Density", dm.densityDpi + " dpi"));
        dispCard.addView(createRow("RAM Details", getRamInfo()));
        dispCard.addView(createRow("Root Access", isRootAvailable() ? "su binary detected" : "su binary not found"));
        content.addView(dispCard);

        scroll.addView(content);
        layout.addView(scroll);

        root.addView(layout);
        setContentView(root);

    }

    private void refreshDriverStatus() {
        if (tvDriverStatusValue == null) return;

        final int requestId = ++driverStatusRequest;
        tvDriverStatusValue.setText("Checking...");
        tvDriverNodeValue.setText("Checking...");
        tvDriverIdValue.setText("Checking...");
        tvDriverPermValue.setText("Checking...");
        tvKernelModuleValue.setText("Checking...");
        tvLoaderStatusValue.setText("Checking...");
        driverStatusExecutor.execute(() -> {
            final DriverManager.DriverInfo info;
            try {
                info = DriverManager.inquireDriverInfo(getApplicationContext());
            } catch (RuntimeException e) {
                Log.e("DeviceInfoActivity", "Unable to refresh driver status", e);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed() || requestId != driverStatusRequest) return;
                    tvDriverStatusValue.setText("Unable to check");
                    tvDriverStatusValue.setTextColor(Color.parseColor("#FFD54F"));
                    tvDriverNodeValue.setText("Unavailable");
                    tvDriverIdValue.setText("Unavailable");
                    tvDriverPermValue.setText("Unavailable");
                    tvKernelModuleValue.setText("Unavailable");
                    tvLoaderStatusValue.setText("Unavailable");
                });
                return;
            }

            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed() || requestId != driverStatusRequest) return;
                if (info.isActive) {
                    tvDriverStatusValue.setText("[ACTIVE] Ring 0 Kernel Driver");
                    tvDriverStatusValue.setTextColor(Color.parseColor("#00E676"));
                } else {
                    tvDriverStatusValue.setText("[INACTIVE] Not Loaded");
                    tvDriverStatusValue.setTextColor(Color.parseColor("#FF5252"));
                }
                tvDriverNodeValue.setText(info.activeNode != null ? info.activeNode : "None Detected");
                tvDriverIdValue.setText(info.deviceId != null ? info.deviceId : "Unknown");
                tvDriverPermValue.setText(info.isActive ? "Accessible" : "Inaccessible");
                tvKernelModuleValue.setText(info.kernelModule != null ? info.kernelModule : "Not Detected in /proc/modules");
                boolean installed = DriverManager.isLoaderInstalled(DeviceInfoActivity.this);
                tvLoaderStatusValue.setText(installed ? "Kernel-Driver 2.0 (Installed)" : "Kernel-Driver 2.0 (Not Installed)");
            });
        });
    }

    private interface RowBinder {
        void bind(TextView textView);
    }

    private View createDynamicRow(String label, RowBinder binder) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView l = new TextView(this);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.45f));
        l.setText(label);
        l.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        l.setMaxLines(2);
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView v = new TextView(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.55f));
        v.setText("—");
        v.setTextColor(AppColors.WHITE);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.END);
        v.setMaxLines(2);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);

        if (binder != null) {
            binder.bind(v);
        }

        row.addView(l);
        row.addView(v);
        return row;
    }

    private LinearLayout createCardContainer() {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(lp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppColors.MAGENTA_CARD);
        bg.setCornerRadius(dp(10));
        card.setBackground(bg);
        return card;
    }

    private View createRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView l = new TextView(this);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.45f));
        l.setText(label);
        l.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        l.setMaxLines(2);
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView v = new TextView(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.55f));
        v.setText(value);
        v.setTextColor(AppColors.WHITE);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.END);
        v.setMaxLines(2);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);

        row.addView(l);
        row.addView(v);
        return row;
    }

    private TextView createSectionHeader(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(AppColors.MAGENTA_PRIMARY);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(6), 0, dp(6));
        return tv;
    }

    private String getKernelVersion() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/version"))) {
            String line = br.readLine();
            if (line != null) {
                String[] parts = line.split(" ");
                if (parts.length >= 3) return parts[2];
                return line.substring(0, Math.min(line.length(), 35));
            }
        } catch (Exception ignored) {}
        return System.getProperty("os.version");
    }

    private String getRamInfo() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(mi);
                long totalMb = mi.totalMem / (1024 * 1024);
                long availMb = mi.availMem / (1024 * 1024);
                return availMb + " MB free / " + totalMb + " MB total";
            }
        } catch (Exception ignored) {}
        return "N/A";
    }

    private boolean isRootAvailable() {
        return new File("/system/bin/su").exists() ||
               new File("/system/xbin/su").exists() ||
               new File("/sbin/su").exists() ||
               new File("/system/app/Superuser.apk").exists();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
