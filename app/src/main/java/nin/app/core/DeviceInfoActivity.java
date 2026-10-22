package nin.app.core;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import nin.app.R;

public class DeviceInfoActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildView();
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
        title.setText("DEVICE INFORMATION");
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
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

        // Device Hardware Card
        content.addView(createSectionHeader("HARDWARE & SYSTEM"));
        LinearLayout hwCard = createCardContainer();
        hwCard.addView(createRow("Manufacturer", Build.MANUFACTURER.toUpperCase()));
        hwCard.addView(createRow("Model", Build.MODEL));
        hwCard.addView(createRow("Product / Board", Build.PRODUCT + " (" + Build.BOARD + ")"));
        hwCard.addView(createRow("Hardware", Build.HARDWARE));
        hwCard.addView(createRow("CPU ABI", Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "aarch64"));
        hwCard.addView(createRow("OS Architecture", System.getProperty("os.arch")));
        content.addView(hwCard);

        // Android OS Card
        content.addView(createSectionHeader("ANDROID PLATFORM"));
        LinearLayout osCard = createCardContainer();
        osCard.addView(createRow("Android Version", "Android " + Build.VERSION.RELEASE));
        osCard.addView(createRow("API Level", String.valueOf(Build.VERSION.SDK_INT)));
        osCard.addView(createRow("Security Patch", Build.VERSION.SECURITY_PATCH != null ? Build.VERSION.SECURITY_PATCH : "N/A"));
        osCard.addView(createRow("Kernel Version", getKernelVersion()));
        content.addView(osCard);

        // Display & Memory Card
        content.addView(createSectionHeader("DISPLAY & MEMORY"));
        LinearLayout dispCard = createCardContainer();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        dispCard.addView(createRow("Resolution", dm.widthPixels + " x " + dm.heightPixels));
        dispCard.addView(createRow("Density", dm.densityDpi + " dpi"));
        dispCard.addView(createRow("RAM Details", getRamInfo()));
        dispCard.addView(createRow("Root Access", isRootAvailable() ? "Granted (su active)" : "Not Detected"));
        content.addView(dispCard);

        // Official Version Footer inside scrollable content
        TextView devFooter = new TextView(this);
        LinearLayout.LayoutParams dfLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dfLp.topMargin = dp(16);
        dfLp.bottomMargin = dp(12);
        devFooter.setLayoutParams(dfLp);
        devFooter.setGravity(Gravity.CENTER);
        devFooter.setText("Version: " + getString(R.string.app_version));
        devFooter.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        devFooter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        devFooter.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(devFooter);

        scroll.addView(content);
        layout.addView(scroll);

        root.addView(layout);
        setContentView(root);
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
        l.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        l.setText(label);
        l.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(AppColors.WHITE);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setTypeface(Typeface.DEFAULT_BOLD);

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
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
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
