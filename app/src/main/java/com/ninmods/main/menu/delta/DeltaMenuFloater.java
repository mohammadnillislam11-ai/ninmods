package com.ninmods.main.menu.delta;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ninmods.main.R;
import com.ninmods.main.overlay.Floater;

/**
 * Delta Tactical Menu Floater Implementation.
 * Isolated in 'com.ninmods.main.menu.delta'.
 * Features Delta's distinct Neon Emerald Stealth/Telemetry HUD layout.
 */
public class DeltaMenuFloater {

    public static final int THEME_EMERALD = 0xFF00E676;
    public static final int BG_DARK = 0xF206160F;

    private static volatile DeltaMenuFloater activeInstance = null;
    private final Context context;
    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;

    private FrameLayout rootFrame;
    private FrameLayout collapsedLayout;
    private LinearLayout menuLayout;

    private LinearLayout tabContentContainer;
    private final Map<String, LinearLayout> tabPages = new LinkedHashMap<>();
    private final Map<String, TextView> tabButtons = new LinkedHashMap<>();
    private String activeTab = "Stealth";

    public static synchronized void start(Context ctx) {
        final Context target = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        String activeKey = com.ninmods.main.auth.AuthManager.getActiveLicenseKey(target);
        if (activeKey == null || activeKey.trim().isEmpty()) {
            new Handler(Looper.getMainLooper()).post(() -> android.widget.Toast.makeText(target, "Active license key required! Please activate key online.", android.widget.Toast.LENGTH_LONG).show());
            return;
        }

        String sessionToken = com.ninmods.main.auth.AuthManager.getSessionToken(target);
        String hwid = com.ninmods.main.auth.AuthApiController.getHwid(target);
        long remSec = com.ninmods.main.auth.AuthManager.getRemainingSeconds(target);
        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            sessionToken = "sess_" + (hwid.isEmpty() ? "device" : hwid) + "_" + System.currentTimeMillis();
            com.ninmods.main.auth.AuthManager.setSessionToken(target, sessionToken);
        }
        com.ninmods.main.auth.SecurityVault.setNativeSession(sessionToken, hwid, remSec);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (activeInstance != null && activeInstance.isAttached()) {
                    activeInstance.openMenu();
                    return;
                }
                if (activeInstance != null) {
                    activeInstance.destroy();
                }
                try {
                    Floater.ftKZXivSr(target);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                activeInstance = new DeltaMenuFloater(target);
                activeInstance.openMenu();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public static synchronized void stop() {
        if (activeInstance != null) {
            activeInstance.destroy();
            activeInstance = null;
        }
        try {
            Floater.ftKwCvSr();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static boolean isRunning() {
        return activeInstance != null && activeInstance.isAttached();
    }

    public boolean isAttached() {
        return rootFrame != null && rootFrame.isAttachedToWindow();
    }

    private DeltaMenuFloater(Context context) {
        this.context = context;
        initWindowManager();
        initViews();
    }

    private int getLayoutType() {
        if (Build.VERSION.SDK_INT >= 26) return 2038;
        if (Build.VERSION.SDK_INT >= 24) return 2002;
        if (Build.VERSION.SDK_INT >= 23) return 2005;
        return 2003;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initWindowManager() {
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        windowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getLayoutType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        windowParams.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
        windowParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowParams.x = 40;
        windowParams.y = 180;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        rootFrame = new FrameLayout(context);

        // 1. Collapsed Floating Delta Triangle Button
        collapsedLayout = new FrameLayout(context);
        int logoBox = dp(64);
        collapsedLayout.setLayoutParams(new ViewGroup.LayoutParams(logoBox, logoBox));

        ImageView deltaLogo = new ImageView(context);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(dp(54), dp(54));
        logoLp.gravity = Gravity.CENTER;
        deltaLogo.setLayoutParams(logoLp);
        deltaLogo.setImageResource(R.drawable.ic_menu_delta);
        deltaLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        collapsedLayout.addView(deltaLogo);

        // Close badge
        TextView closeBadge = new TextView(context);
        FrameLayout.LayoutParams cbLp = new FrameLayout.LayoutParams(dp(18), dp(18));
        cbLp.gravity = Gravity.TOP | Gravity.RIGHT;
        closeBadge.setLayoutParams(cbLp);
        closeBadge.setGravity(Gravity.CENTER);
        closeBadge.setText("X");
        closeBadge.setTextColor(THEME_EMERALD);
        closeBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        closeBadge.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable cbBg = new GradientDrawable();
        cbBg.setShape(GradientDrawable.OVAL);
        cbBg.setColor(0xCC000000);
        cbBg.setStroke(dp(1), THEME_EMERALD);
        closeBadge.setBackground(cbBg);
        closeBadge.setOnClickListener(v -> destroy());
        collapsedLayout.addView(closeBadge);

        // Drag listener for floating logo
        collapsedLayout.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float touchX, touchY;
            private boolean isDrag = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = windowParams.x;
                        initialY = windowParams.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        isDrag = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - touchX);
                        int dy = (int) (event.getRawY() - touchY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDrag = true;
                        windowParams.x = initialX + dx;
                        windowParams.y = initialY + dy;
                        if (windowManager != null && rootFrame.isAttachedToWindow()) {
                            windowManager.updateViewLayout(rootFrame, windowParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDrag) {
                            openMenu();
                        }
                        return true;
                }
                return false;
            }
        });
        rootFrame.addView(collapsedLayout);

        // 2. Delta Stealth Telemetry HUD Menu Window
        menuLayout = new LinearLayout(context);
        int menuW = dp(320);
        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(menuW, ViewGroup.LayoutParams.WRAP_CONTENT);
        menuLayout.setLayoutParams(menuLp);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setVisibility(View.GONE);

        GradientDrawable menuBg = new GradientDrawable();
        menuBg.setColor(BG_DARK);
        menuBg.setCornerRadius(dp(12));
        menuBg.setStroke(dp(1.5f), THEME_EMERALD);
        menuLayout.setBackground(menuBg);
        menuLayout.setPadding(dp(12), dp(10), dp(12), dp(14));

        // Header Row (Draggable)
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(10));

        ImageView headerIcon = new ImageView(context);
        LinearLayout.LayoutParams hiLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        hiLp.rightMargin = dp(8);
        headerIcon.setLayoutParams(hiLp);
        headerIcon.setImageResource(R.drawable.ic_menu_delta);
        header.addView(headerIcon);

        TextView headerTitle = new TextView(context);
        LinearLayout.LayoutParams htLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        headerTitle.setLayoutParams(htLp);
        headerTitle.setText("DELTA");
        headerTitle.setTextColor(THEME_EMERALD);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        headerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(headerTitle);

        // Minimize Button
        TextView minBtn = new TextView(context);
        minBtn.setText("—");
        minBtn.setTextColor(Color.WHITE);
        minBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        minBtn.setTypeface(Typeface.DEFAULT_BOLD);
        minBtn.setPadding(dp(8), dp(2), dp(8), dp(2));
        minBtn.setOnClickListener(v -> closeMenu());
        header.addView(minBtn);

        // Close Button
        TextView closeBtn = new TextView(context);
        closeBtn.setText("X");
        closeBtn.setTextColor(THEME_EMERALD);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setPadding(dp(8), dp(2), dp(4), dp(2));
        closeBtn.setOnClickListener(v -> destroy());
        header.addView(closeBtn);

        menuLayout.addView(header);

        // Tab Strip (Stealth, Telemetry, Sensors)
        LinearLayout tabStrip = new LinearLayout(context);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(0, 0, 0, dp(10));

        addTab(tabStrip, "Stealth");
        addTab(tabStrip, "Telemetry");
        addTab(tabStrip, "Sensors");
        menuLayout.addView(tabStrip);

        // Tab Content Container
        tabContentContainer = new LinearLayout(context);
        tabContentContainer.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(context);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        scroll.setVerticalScrollBarEnabled(false);

        buildTabPages();
        scroll.addView(tabContentContainer);
        menuLayout.addView(scroll);

        // Drag listener for menu header
        header.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float touchX, touchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = windowParams.x;
                        initialY = windowParams.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        windowParams.x = initialX + (int) (event.getRawX() - touchX);
                        windowParams.y = initialY + (int) (event.getRawY() - touchY);
                        if (windowManager != null && rootFrame.isAttachedToWindow()) {
                            windowManager.updateViewLayout(rootFrame, windowParams);
                        }
                        return true;
                }
                return false;
            }
        });

        rootFrame.addView(menuLayout);

        try {
            windowManager.addView(rootFrame, windowParams);
        } catch (Exception ignored) {}

        selectTab("Stealth");
    }

    private void addTab(LinearLayout strip, final String tabName) {
        TextView tab = new TextView(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(30), 1.0f);
        lp.rightMargin = dp(4);
        tab.setLayoutParams(lp);
        tab.setGravity(Gravity.CENTER);
        tab.setText(tabName);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tab.setTypeface(Typeface.DEFAULT_BOLD);

        tab.setOnClickListener(v -> selectTab(tabName));
        tabButtons.put(tabName, tab);
        strip.addView(tab);

        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        tabPages.put(tabName, page);
    }

    private void selectTab(String tabName) {
        activeTab = tabName;
        for (Map.Entry<String, TextView> entry : tabButtons.entrySet()) {
            boolean isSelected = entry.getKey().equals(tabName);
            TextView btn = entry.getValue();
            btn.setTextColor(isSelected ? Color.BLACK : 0x88FFFFFF);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(isSelected ? THEME_EMERALD : 0x22FFFFFF);
            bg.setCornerRadius(dp(6));
            btn.setBackground(bg);
        }

        tabContentContainer.removeAllViews();
        LinearLayout targetPage = tabPages.get(tabName);
        if (targetPage != null) {
            tabContentContainer.addView(targetPage);
        }
    }

    private void buildTabPages() {
        // Stealth Page - 100% Genuine C++ Engine IDs
        LinearLayout stealth = tabPages.get("Stealth");
        if (stealth != null) {
            addSwitch(stealth, "Enable Main Engine", 1);
            addSwitch(stealth, "Silent Aim Head/Body", 8);
            addSwitch(stealth, "Aim Magnetism", 63);
            addSwitch(stealth, "Speed Hack Enable", 60);
            addSeekBar(stealth, "Game Speed Multiplier", 20, 50, "x", 25);
            addSeekBar(stealth, "Aim FOV Radius", 50, 180, " px", 9);
            addSeekBar(stealth, "Headshot Rate", 85, 100, "%", 70);
        }

        // Telemetry Page - 100% Genuine C++ Engine IDs
        LinearLayout telemetry = tabPages.get("Telemetry");
        if (telemetry != null) {
            addSwitch(telemetry, "ESP Enemy Box", 12);
            addSwitch(telemetry, "ESP Distance Tag", 13);
            addSwitch(telemetry, "ESP Health Bar", 19);
            addSwitch(telemetry, "Active Player Count", 23);
            addSwitch(telemetry, "Aim Detection Circle", 27);
            addSeekBar(telemetry, "ESP Color (0-9)", 0, 9, "", 16);
            addSeekBar(telemetry, "Line Thickness", 2, 10, " px", 22);
        }

        // Sensors & Priority Page - 100% Genuine C++ Engine IDs
        LinearLayout sensors = tabPages.get("Sensors");
        if (sensors != null) {
            addSeekBar(sensors, "Silent Target (0=Head, 1=Body)", 0, 1, "", 61);
            addSeekBar(sensors, "Target Priority (0=FOV, 1=Dist)", 0, 1, "", 62);
            addSeekBar(sensors, "Engagement Distance", 120, 200, " m", 10);
            addSeekBar(sensors, "Line Alignment (0-2)", 0, 2, "", 18);
        }
    }

    private void addSwitch(LinearLayout container, String title, final int id) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView tv = new TextView(context);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        tv.setText(title);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        row.addView(tv);

        Switch sw = new Switch(context);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> Floater.ChangesID(id, isChecked ? 1 : 0));
        row.addView(sw);

        container.addView(row);
    }

    private void addSeekBar(LinearLayout container, String title, int current, int max, final String unit, final int id) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(4), 0, dp(6));

        final TextView label = new TextView(context);
        label.setText(title + ": " + current + unit);
        label.setTextColor(0xCCFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        box.addView(label);

        SeekBar sb = new SeekBar(context);
        sb.setMax(max);
        sb.setProgress(current);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText(title + ": " + progress + unit);
                Floater.ChangesID(id, progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        box.addView(sb);

        container.addView(box);
    }

    public void openMenu() {
        if (collapsedLayout != null) collapsedLayout.setVisibility(View.GONE);
        if (menuLayout != null) menuLayout.setVisibility(View.VISIBLE);
    }

    public void closeMenu() {
        if (menuLayout != null) menuLayout.setVisibility(View.GONE);
        if (collapsedLayout != null) collapsedLayout.setVisibility(View.VISIBLE);
    }

    public void destroy() {
        try {
            if (windowManager != null && rootFrame != null && rootFrame.isAttachedToWindow()) {
                windowManager.removeView(rootFrame);
            }
        } catch (Exception ignored) {}
        activeInstance = null;
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
