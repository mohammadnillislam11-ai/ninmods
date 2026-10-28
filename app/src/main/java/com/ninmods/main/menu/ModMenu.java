package com.ninmods.main.menu;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.ninmods.main.R;
import com.ninmods.main.core.AppLogger;
import com.ninmods.main.core.NativeBridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NINMODS MODERN TACTICAL FLOATING MENU
 * -------------------------------------
 * Sleek, high-performance, dark glassmorphism floating menu engineered
 * specifically for NINMODS.
 *
 * Architecture:
 *  - ninmods: EXCLUSIVELY operates with the Linux Kernel Driver (/dev/BYNb)
 *    via Ring-0 IOCTL (0x801 Read, 0x802 Write, 0x803 Base) in compliance
 *    with Rule 16 & 17.
 *  - ninlite: Internal Mode (user-space daemon / IPC).
 *
 * Features segmented horizontal pill tabs, real-time telemetry, smooth
 * dragging, and zero LGL legacy code.
 */
public class ModMenu {

    private static final String TAG = "NinModMenu";
    private static volatile boolean sNativeReady = false;

    static {
        try {
            System.loadLibrary("ninmods");
            sNativeReady = true;
        } catch (Throwable t) {
            AppLogger.logE(TAG, "libninmods.so unavailable for mod menu", t);
        }
    }

    // --- Native JNI Bindings (Registered in Setup.cpp) ---
    native void Init(Context context, TextView title, TextView subTitle);
    native String Icon();
    native String IconWebViewData();
    native String[] GetFeatureList();
    native String[] SettingsList();
    native boolean IsGameLibLoaded();

    // --- Dimensions & Styling Constants ---
    private static final int ICON_SIZE_DP = 52;
    private static final int MENU_WIDTH_DP = 310;
    private static final int MENU_HEIGHT_DP = 430;
    private static final int CORNER_RADIUS_DP = 16;
    private static final int ITEM_CORNER_DP = 10;

    // --- Theme Palette (Modern Obsidian & Tactical Cyan/Indigo) ---
    private static final int COLOR_CARD_BG = 0xF20F121C;        // Deep obsidian glass
    private static final int COLOR_CARD_BORDER = 0xFF242A3B;    // Crisp border stroke
    private static final int COLOR_ITEM_BG = 0xFF181C28;        // Feature item card
    private static final int COLOR_ITEM_BORDER = 0xFF232838;    // Item border stroke
    private static final int COLOR_HEADER_BG = 0x80131722;      // Subtle header tint
    private static final int COLOR_ACCENT_CYAN = 0xFF00E5FF;    // Tactical Cyan
    private static final int COLOR_ACCENT_BLUE = 0xFF3B82F6;    // Electric Blue
    private static final int COLOR_ACCENT_INDIGO = 0xFF6366F1;  // Indigo highlight
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFFFF;   // Crisp white
    private static final int COLOR_TEXT_MUTED = 0xFF94A3B8;     // Slate secondary
    private static final int COLOR_TEXT_DIM = 0xFF64748B;       // Dim metadata
    private static final int COLOR_STATUS_GREEN = 0xFF10B981;   // Connected
    private static final int COLOR_STATUS_AMBER = 0xFFF59E0B;   // Standby
    private static final int COLOR_SWITCH_OFF = 0xFF2D3748;     // Inactive track

    // --- Context & Window Management ---
    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams vmParams;

    // --- View Hierarchy ---
    private FrameLayout rootFrame;
    private RelativeLayout mCollapsedView;
    private LinearLayout mExpandedView;
    private ImageView mFloatingBubble;

    // --- Tabs & Content ---
    private LinearLayout mTabBar;
    private final List<TextView> mTabButtons = new ArrayList<>();
    private final Map<String, LinearLayout> mTabContainers = new LinkedHashMap<>();
    private String mActiveCategory = "VISUALS";
    private TextView mStatusChip;
    private TextView mFooterText;

    // Position memory
    private int mPosX = 30;
    private int mPosY = 180;
    private boolean mIsAttached = false;

    public ModMenu(Context context) {
        this.mContext = context;
        MenuPreferences.context = context;

        initViews();
    }

    private void initViews() {
        rootFrame = new FrameLayout(mContext);

        // 1. Build Floating Bubble (Collapsed View)
        buildCollapsedView();

        // 2. Build Modern Tactical Card (Expanded View)
        buildExpandedView();

        rootFrame.addView(mCollapsedView);
        rootFrame.addView(mExpandedView);

        // Collapse to bubble by default
        mCollapsedView.setVisibility(View.VISIBLE);
        mExpandedView.setVisibility(View.GONE);
    }

    /**
     * Floating Draggable Bubble / Pill (Collapsed State)
     */
    @SuppressLint("ClickableViewAccessibility")
    private void buildCollapsedView() {
        mCollapsedView = new RelativeLayout(mContext);
        mCollapsedView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int bubblePx = dp(ICON_SIZE_DP);

        mFloatingBubble = new ImageView(mContext);
        RelativeLayout.LayoutParams iconParams = new RelativeLayout.LayoutParams(bubblePx, bubblePx);
        mFloatingBubble.setLayoutParams(iconParams);
        mFloatingBubble.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mFloatingBubble.setPadding(dp(10), dp(10), dp(10), dp(10));

        // Glassmorphism circular background with subtle glowing cyan border
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xE60F121C);
        bg.setStroke(dp(2), COLOR_ACCENT_CYAN);
        mFloatingBubble.setBackground(bg);

        // Dynamic brand logo
        try {
            mFloatingBubble.setImageResource(R.drawable.nin_logo);
        } catch (Throwable t) {
            try {
                mFloatingBubble.setImageResource(R.drawable.ic_launcher);
            } catch (Throwable ignored) {}
        }

        mFloatingBubble.setOnTouchListener(createDragListener(true));
        mCollapsedView.addView(mFloatingBubble);
    }

    /**
     * Modern Tactical Card (Expanded State)
     */
    @SuppressLint("ClickableViewAccessibility")
    private void buildExpandedView() {
        mExpandedView = new LinearLayout(mContext);
        mExpandedView.setOrientation(LinearLayout.VERTICAL);

        int widthPx = dp(MENU_WIDTH_DP);
        int heightPx = dp(MENU_HEIGHT_DP);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(widthPx, heightPx);
        mExpandedView.setLayoutParams(cardParams);

        // Deep obsidian glass background with rounded corners and crisp stroke
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(CORNER_RADIUS_DP));
        cardBg.setColor(COLOR_CARD_BG);
        cardBg.setStroke(dp(1), COLOR_CARD_BORDER);
        mExpandedView.setBackground(cardBg);

        // 1. Header Bar
        mExpandedView.addView(buildHeaderBar());

        // 2. Horizontal Segmented Pill Tabs
        mExpandedView.addView(buildTabBar());

        // 3. Tab Content Scrollable Area
        ScrollView contentScroll = new ScrollView(mContext);
        contentScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        contentScroll.setVerticalScrollBarEnabled(false);
        contentScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        FrameLayout contentHolder = new FrameLayout(mContext);
        contentHolder.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Create standard categories
        createCategoryContainer("VISUALS", contentHolder);
        createCategoryContainer("AIMBOT", contentHolder);
        createCategoryContainer("COMBAT", contentHolder);
        createCategoryContainer("SYSTEM", contentHolder);

        contentScroll.addView(contentHolder);
        mExpandedView.addView(contentScroll);

        // 4. Sleek Footer Bar
        mExpandedView.addView(buildFooterBar());
    }

    /**
     * Header Bar with Dynamic App Title, Live Status Chip, and Control Buttons
     */
    @SuppressLint("ClickableViewAccessibility")
    private View buildHeaderBar() {
        LinearLayout header = new LinearLayout(mContext);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(12), dp(10));
        header.setBackgroundColor(COLOR_HEADER_BG);

        // Header touch listener allows dragging the entire expanded card
        header.setOnTouchListener(createDragListener(false));

        // Left: Logo + Dynamic App Title
        ImageView logo = new ImageView(mContext);
        logo.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
        try {
            logo.setImageResource(R.drawable.nin_logo);
        } catch (Throwable t) {
            try {
                logo.setImageResource(R.drawable.ic_launcher);
            } catch (Throwable ignored) {}
        }
        header.addView(logo);

        LinearLayout titleCol = new LinearLayout(mContext);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tcParams.setMargins(dp(8), 0, dp(8), 0);
        titleCol.setLayoutParams(tcParams);

        TextView titleText = new TextView(mContext);
        titleText.setText(mContext.getString(R.string.app_name));
        titleText.setTextColor(COLOR_TEXT_PRIMARY);
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        titleText.setSingleLine(true);
        titleCol.addView(titleText);

        TextView subtitleText = new TextView(mContext);
        subtitleText.setText("KERNEL ENGINE");
        subtitleText.setTextColor(COLOR_TEXT_DIM);
        subtitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        subtitleText.setLetterSpacing(0.08f);
        titleCol.addView(subtitleText);

        header.addView(titleCol);

        // Center: Live Status Chip Pill
        mStatusChip = new TextView(mContext);
        mStatusChip.setText("● STANDBY");
        mStatusChip.setTextColor(COLOR_STATUS_AMBER);
        mStatusChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        mStatusChip.setTypeface(Typeface.DEFAULT_BOLD);
        mStatusChip.setPadding(dp(7), dp(3), dp(7), dp(3));

        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setCornerRadius(dp(10));
        chipBg.setColor(0x22F59E0B);
        mStatusChip.setBackground(chipBg);

        LinearLayout.LayoutParams scParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        scParams.gravity = Gravity.CENTER_VERTICAL;
        mStatusChip.setLayoutParams(scParams);
        mStatusChip.setGravity(Gravity.CENTER);
        header.addView(mStatusChip);

        // Right: Minimize Button [—]
        TextView minBtn = createHeaderButton("—", v -> collapseToLogo());
        header.addView(minBtn);

        // Right: Close Button [✕]
        TextView closeBtn = createHeaderButton("✕", v -> collapseToLogo());
        LinearLayout.LayoutParams cbParams = (LinearLayout.LayoutParams) closeBtn.getLayoutParams();
        cbParams.leftMargin = dp(6);
        header.addView(closeBtn);

        return header;
    }

    private TextView createHeaderButton(String text, View.OnClickListener onClick) {
        TextView btn = new TextView(mContext);
        int size = dp(24);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        btn.setLayoutParams(params);
        btn.setText(text);
        btn.setTextColor(COLOR_TEXT_MUTED);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x33232838);
        btn.setBackground(bg);

        btn.setOnClickListener(onClick);
        return btn;
    }

    /**
     * Horizontal Segmented Pill Tab Bar
     */
    private View buildTabBar() {
        mTabBar = new LinearLayout(mContext);
        mTabBar.setOrientation(LinearLayout.HORIZONTAL);
        mTabBar.setPadding(dp(10), dp(8), dp(10), dp(8));
        mTabBar.setBackgroundColor(0x3310131E);

        String[] tabs = {"VISUALS", "AIMBOT", "COMBAT", "SYSTEM"};
        for (String tabName : tabs) {
            TextView tabBtn = new TextView(mContext);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    0, dp(28), 1.0f);
            p.setMargins(dp(2), 0, dp(2), 0);
            tabBtn.setLayoutParams(p);
            tabBtn.setText(tabName);
            tabBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            tabBtn.setTypeface(Typeface.DEFAULT_BOLD);
            tabBtn.setGravity(Gravity.CENTER);
            tabBtn.setSingleLine(true);

            final String catKey = tabName;
            tabBtn.setOnClickListener(v -> selectTab(catKey));

            mTabButtons.add(tabBtn);
            mTabBar.addView(tabBtn);
        }

        updateTabStyles("VISUALS");
        return mTabBar;
    }

    private void createCategoryContainer(String categoryName, FrameLayout parent) {
        LinearLayout container = new LinearLayout(mContext);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(12), dp(8), dp(12), dp(12));
        container.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        container.setVisibility(categoryName.equals(mActiveCategory) ? View.VISIBLE : View.GONE);
        mTabContainers.put(categoryName, container);
        parent.addView(container);
    }

    private void selectTab(String categoryName) {
        mActiveCategory = categoryName;
        updateTabStyles(categoryName);

        for (Map.Entry<String, LinearLayout> entry : mTabContainers.entrySet()) {
            entry.getValue().setVisibility(entry.getKey().equalsIgnoreCase(categoryName) ? View.VISIBLE : View.GONE);
        }
    }

    private void updateTabStyles(String activeCategory) {
        for (TextView tabBtn : mTabButtons) {
            boolean isActive = tabBtn.getText().toString().equalsIgnoreCase(activeCategory);

            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setCornerRadius(dp(8));

            if (isActive) {
                pillBg.setColor(COLOR_ACCENT_BLUE);
                tabBtn.setTextColor(COLOR_TEXT_PRIMARY);
            } else {
                pillBg.setColor(0x22222838);
                tabBtn.setTextColor(COLOR_TEXT_MUTED);
            }
            tabBtn.setBackground(pillBg);
        }
    }

    /**
     * Sleek Footer Bar with Telemetry & Engine Badge
     */
    private View buildFooterBar() {
        LinearLayout footer = new LinearLayout(mContext);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(14), dp(8), dp(14), dp(8));
        footer.setBackgroundColor(0x550C0E17);

        mFooterText = new TextView(mContext);
        mFooterText.setText("NINMODS V90.86 • KERNEL DRIVER RING-0");
        mFooterText.setTextColor(COLOR_TEXT_DIM);
        mFooterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        mFooterText.setLetterSpacing(0.05f);

        LinearLayout.LayoutParams ftParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        mFooterText.setLayoutParams(ftParams);
        footer.addView(mFooterText);

        // Small indicator grip dot
        View grip = new View(mContext);
        LinearLayout.LayoutParams gParams = new LinearLayout.LayoutParams(dp(24), dp(3));
        grip.setLayoutParams(gParams);
        GradientDrawable gBg = new GradientDrawable();
        gBg.setCornerRadius(dp(2));
        gBg.setColor(0x4494A3B8);
        grip.setBackground(gBg);
        footer.addView(grip);

        return footer;
    }

    // --- Dynamic Feature Population ---

    public void ShowMenu() {
        if (rootFrame.getParent() == null) {
            rootFrame.addView(mCollapsedView);
            rootFrame.addView(mExpandedView);
        }

        mMainHandler.postDelayed(() -> {
            populateAllFeatures();
            startTelemetryPoller();
        }, 200);
    }

    private void populateAllFeatures() {
        // Clear old items from each tab container
        for (LinearLayout container : mTabContainers.values()) {
            container.removeAllViews();
        }

        String[] rawDsl = sNativeReady ? GetFeatureList() : new String[0];
        if (rawDsl == null || rawDsl.length == 0) {
            populateFallbackFeatures();
            return;
        }

        String currentCategory = "VISUALS";

        for (String row : rawDsl) {
            if (row == null || row.trim().isEmpty()) continue;

            if (row.startsWith("Category_")) {
                currentCategory = row.substring("Category_".length()).trim().toUpperCase();
                continue;
            }

            LinearLayout targetContainer = mTabContainers.get(currentCategory);
            if (targetContainer == null) {
                targetContainer = mTabContainers.get("VISUALS");
            }
            if (targetContainer == null) continue;

            parseAndAddFeatureRow(row, targetContainer);
        }

        // Add System diagnostics & controls to SYSTEM tab
        populateSystemTab();
    }

    private void parseAndAddFeatureRow(String row, LinearLayout container) {
        try {
            boolean trueByDefault = row.contains("_True");
            String cleaned = row.replaceFirst("_True", "");

            String[] tokens = cleaned.split("_");
            if (tokens.length < 2) return;

            int featId = 0;
            try {
                featId = Integer.parseInt(tokens[0]);
            } catch (Exception e) {
                return;
            }

            String type = tokens[1];

            if ("Toggle".equalsIgnoreCase(type) && tokens.length >= 3) {
                String label = tokens[2];
                addToggleItem(container, featId, label, trueByDefault);
            } else if ("SeekBar".equalsIgnoreCase(type) && tokens.length >= 5) {
                String label = tokens[2];
                int min = Integer.parseInt(tokens[3]);
                int max = Integer.parseInt(tokens[4]);
                addSliderItem(container, featId, label, min, max);
            } else if ("Spinner".equalsIgnoreCase(type) && tokens.length >= 4) {
                String label = tokens[2];
                String[] options = tokens[3].split(",");
                addOptionsItem(container, featId, label, options);
            }
        } catch (Throwable t) {
            AppLogger.logE(TAG, "Error parsing DSL row: " + row, t);
        }
    }

    private void populateFallbackFeatures() {
        // VISUALS
        LinearLayout vis = mTabContainers.get("VISUALS");
        if (vis != null) {
            addToggleItem(vis, 1, "ESP MASTER SWITCH", false);
            addToggleItem(vis, 2, "ESP BOX (2D)", false);
            addToggleItem(vis, 4, "ESP LINE (TRACER)", false);
            addToggleItem(vis, 5, "ESP HEALTH BAR", false);
            addToggleItem(vis, 6, "ESP PLAYER NAME", false);
            addToggleItem(vis, 7, "ESP DISTANCE IN METERS", false);
            addToggleItem(vis, 206, "ESP WEAPON NAME", false);
            addToggleItem(vis, 207, "THREAT COUNTER", false);
            addToggleItem(vis, 208, "ESP INFO LINE", true);
            addToggleItem(vis, 27, "DRAW FOV CIRCLE", true);
            addOptionsItem(vis, 201, "ESP BOX STYLE", new String[]{"STROKE", "FILLED", "CORNER", "ROUNDED"});
            addOptionsItem(vis, 202, "ESP LINE SNAP MODE", new String[]{"TOP", "CENTER", "BOTTOM"});
            addSliderItem(vis, 209, "ESP TEXT SIZE", 8, 24);
        }

        // AIMBOT
        LinearLayout aim = mTabContainers.get("AIMBOT");
        if (aim != null) {
            addToggleItem(aim, 20, "AIM HARD (REAL AIM)", false);
            addToggleItem(aim, 21, "AIM COLLIDER LOCK", false);
            addToggleItem(aim, 22, "SILENT AIM (MAGIC BULLET)", false);
            addToggleItem(aim, 23, "AIM MAGNET V2", false);
            addToggleItem(aim, 24, "IGNORE KNOCKED PLAYERS", true);
            addToggleItem(aim, 216, "KILL IN CHEST", false);
            addSliderItem(aim, 303, "AIM FOV ANGLE", 10, 180);
            addSliderItem(aim, 101, "MAX TARGET DISTANCE", 10, 350);
            addSliderItem(aim, 214, "HEAD RATE", 0, 3);
            addSliderItem(aim, 215, "CHEST RATE", 0, 6);
        }

        // COMBAT
        LinearLayout com = mTabContainers.get("COMBAT");
        if (com != null) {
            addToggleItem(com, 40, "NO RECOIL (100% STABLE)", false);
            addToggleItem(com, 41, "SPEED MOVEMENT (2X)", false);
            addToggleItem(com, 42, "PLAYER TO ME (TELEPORT)", false);
            addToggleItem(com, 43, "UNDERGROUND CAMERA", false);
            addToggleItem(com, 44, "ANTICHEAT REALTIME BYPASS", false);
            addSliderItem(com, 102, "GAME SPEED MULTIPLIER", 1, 5);
        }

        // SYSTEM
        populateSystemTab();
    }

    private void populateSystemTab() {
        LinearLayout sys = mTabContainers.get("SYSTEM");
        if (sys == null) return;

        // Telemetry Card
        LinearLayout infoCard = new LinearLayout(mContext);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        infoCard.setLayoutParams(createItemLayoutParams());

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(ITEM_CORNER_DP));
        bg.setColor(COLOR_ITEM_BG);
        bg.setStroke(dp(1), COLOR_ITEM_BORDER);
        infoCard.setBackground(bg);

        TextView head = new TextView(mContext);
        head.setText("SYSTEM TELEMETRY");
        head.setTextColor(COLOR_ACCENT_CYAN);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setLetterSpacing(0.05f);
        infoCard.addView(head);

        addTelemetryRow(infoCard, "Engine Mode", "Kernel Driver (/dev/BYNb Ring-0)");
        addTelemetryRow(infoCard, "Architecture", "ninmods: Kernel Driver | ninlite: Internal");
        addTelemetryRow(infoCard, "Memory IOCTL", "0x801 Read / 0x802 Write / 0x803 Base");
        addTelemetryRow(infoCard, "Detection", "POSIX /proc/[pid]/cmdline");
        addTelemetryRow(infoCard, "Target Game PID", String.valueOf(NativeBridge.getDetectedGamePid()));

        sys.addView(infoCard);

        // Save Preferences Toggle
        addToggleItem(sys, -1, "Save Feature Preferences", MenuPreferences.loadPref);

        // Reset Features Action Button
        TextView resetBtn = new TextView(mContext);
        LinearLayout.LayoutParams rbParams = createItemLayoutParams();
        rbParams.topMargin = dp(8);
        resetBtn.setLayoutParams(rbParams);
        resetBtn.setText("RESET ALL FEATURES");
        resetBtn.setTextColor(0xFFEF4444);
        resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        resetBtn.setTypeface(Typeface.DEFAULT_BOLD);
        resetBtn.setGravity(Gravity.CENTER);
        resetBtn.setPadding(0, dp(10), 0, dp(10));

        GradientDrawable rBg = new GradientDrawable();
        rBg.setCornerRadius(dp(ITEM_CORNER_DP));
        rBg.setColor(0x22EF4444);
        rBg.setStroke(dp(1), 0x55EF4444);
        resetBtn.setBackground(rBg);

        resetBtn.setOnClickListener(v -> {
            Toast.makeText(mContext, "Features Reset to Default", Toast.LENGTH_SHORT).show();
            populateAllFeatures();
        });

        sys.addView(resetBtn);
    }

    private void addTelemetryRow(LinearLayout parent, String key, String val) {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(3));

        TextView kText = new TextView(mContext);
        kText.setText(key);
        kText.setTextColor(COLOR_TEXT_MUTED);
        kText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        kText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        row.addView(kText);

        TextView vText = new TextView(mContext);
        vText.setText(val);
        vText.setTextColor(COLOR_TEXT_PRIMARY);
        vText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        vText.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(vText);

        parent.addView(row);
    }

    // --- Component Item Builders ---

    private LinearLayout.LayoutParams createItemLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(6);
        return params;
    }

    /**
     * Modern Switch Card Item
     */
    private void addToggleItem(LinearLayout container, int featId, String label, boolean defaultVal) {
        LinearLayout item = new LinearLayout(mContext);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12), dp(8), dp(10), dp(8));
        item.setLayoutParams(createItemLayoutParams());

        GradientDrawable itemBg = new GradientDrawable();
        itemBg.setCornerRadius(dp(ITEM_CORNER_DP));
        itemBg.setColor(COLOR_ITEM_BG);
        itemBg.setStroke(dp(1), COLOR_ITEM_BORDER);
        item.setBackground(itemBg);

        TextView title = new TextView(mContext);
        title.setText(label);
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        title.setLayoutParams(tParams);
        item.addView(title);

        Switch sw = new Switch(mContext);
        boolean initialVal = MenuPreferences.loadPrefBool(label, featId, defaultVal);
        sw.setChecked(initialVal);

        applySwitchStyling(sw);

        sw.setOnCheckedChangeListener((btn, isChecked) -> {
            MenuPreferences.changeFeatureBool(label, featId, isChecked);
            NativeBridge.setFeatureState(featId, isChecked);
            applySwitchStyling(sw);
        });

        item.addView(sw);
        container.addView(item);
    }

    private void applySwitchStyling(Switch sw) {
        int trackColor = sw.isChecked() ? COLOR_ACCENT_BLUE : COLOR_SWITCH_OFF;
        int thumbColor = sw.isChecked() ? COLOR_TEXT_PRIMARY : COLOR_TEXT_MUTED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sw.setTrackTintList(ColorStateList.valueOf(trackColor));
            sw.setThumbTintList(ColorStateList.valueOf(thumbColor));
        }
    }

    /**
     * Modern Slider (SeekBar) Card Item
     */
    private void addSliderItem(LinearLayout container, int featId, String label, int min, int max) {
        LinearLayout item = new LinearLayout(mContext);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(12), dp(8), dp(12), dp(8));
        item.setLayoutParams(createItemLayoutParams());

        GradientDrawable itemBg = new GradientDrawable();
        itemBg.setCornerRadius(dp(ITEM_CORNER_DP));
        itemBg.setColor(COLOR_ITEM_BG);
        itemBg.setStroke(dp(1), COLOR_ITEM_BORDER);
        item.setBackground(itemBg);

        // Header Row: Label on Left, Live Badge on Right
        LinearLayout topRow = new LinearLayout(mContext);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(mContext);
        title.setText(label);
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        title.setLayoutParams(tParams);
        topRow.addView(title);

        TextView valBadge = new TextView(mContext);
        valBadge.setTextColor(COLOR_ACCENT_CYAN);
        valBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        valBadge.setTypeface(Typeface.DEFAULT_BOLD);
        topRow.addView(valBadge);

        item.addView(topRow);

        // SeekBar Row
        SeekBar seekBar = new SeekBar(mContext);
        seekBar.setPadding(dp(4), dp(6), dp(4), dp(2));
        seekBar.setMax(max - min);

        int savedVal = MenuPreferences.loadPrefInt(label, featId);
        int currentVal = (savedVal >= min && savedVal <= max) ? savedVal : min;
        seekBar.setProgress(currentVal - min);
        valBadge.setText(String.valueOf(currentVal));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT_BLUE));
            seekBar.setThumbTintList(ColorStateList.valueOf(COLOR_ACCENT_CYAN));
        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int actual = min + progress;
                valBadge.setText(String.valueOf(actual));
                if (fromUser) {
                    MenuPreferences.changeFeatureInt(label, featId, actual);
                    NativeBridge.setFeatureFloat(featId, (float) actual);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });

        item.addView(seekBar);
        container.addView(item);
    }

    /**
     * Modern Option Selector (Segmented Chips)
     */
    private void addOptionsItem(LinearLayout container, int featId, String label, String[] options) {
        LinearLayout item = new LinearLayout(mContext);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(12), dp(8), dp(12), dp(8));
        item.setLayoutParams(createItemLayoutParams());

        GradientDrawable itemBg = new GradientDrawable();
        itemBg.setCornerRadius(dp(ITEM_CORNER_DP));
        itemBg.setColor(COLOR_ITEM_BG);
        itemBg.setStroke(dp(1), COLOR_ITEM_BORDER);
        item.setBackground(itemBg);

        TextView title = new TextView(mContext);
        title.setText(label);
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        item.addView(title);

        LinearLayout chipsRow = new LinearLayout(mContext);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(0, dp(6), 0, 0);

        int savedIndex = MenuPreferences.loadPrefInt(label, featId);
        final List<TextView> chipViews = new ArrayList<>();

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView chip = new TextView(mContext);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    0, dp(26), 1.0f);
            p.setMargins(dp(2), 0, dp(2), 0);
            chip.setLayoutParams(p);
            chip.setText(options[i]);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);

            chip.setOnClickListener(v -> {
                MenuPreferences.changeFeatureInt(label, featId, index);
                NativeBridge.setFeatureFloat(featId, (float) index);
                updateChipStates(chipViews, index);
            });

            chipViews.add(chip);
            chipsRow.addView(chip);
        }

        updateChipStates(chipViews, (savedIndex >= 0 && savedIndex < options.length) ? savedIndex : 0);
        item.addView(chipsRow);
        container.addView(item);
    }

    private void updateChipStates(List<TextView> chips, int selectedIndex) {
        for (int i = 0; i < chips.size(); i++) {
            TextView chip = chips.get(i);
            boolean isSel = (i == selectedIndex);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(6));
            if (isSel) {
                bg.setColor(COLOR_ACCENT_BLUE);
                chip.setTextColor(COLOR_TEXT_PRIMARY);
            } else {
                bg.setColor(0x33232838);
                chip.setTextColor(COLOR_TEXT_MUTED);
            }
            chip.setBackground(bg);
        }
    }

    // --- Telemetry Poller ---

    private void startTelemetryPoller() {
        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mExpandedView.getVisibility() == View.VISIBLE) {
                    updateLiveTelemetry();
                }
                mMainHandler.postDelayed(this, 2500);
            }
        }, 2500);
    }

    private void updateLiveTelemetry() {
        int pid = NativeBridge.getDetectedGamePid();
        if (pid > 0) {
            mStatusChip.setText("● CONNECTED (" + pid + ")");
            mStatusChip.setTextColor(COLOR_STATUS_GREEN);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(10));
            bg.setColor(0x2210B981);
            mStatusChip.setBackground(bg);
        } else {
            mStatusChip.setText("● STANDBY");
            mStatusChip.setTextColor(COLOR_STATUS_AMBER);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(10));
            bg.setColor(0x22F59E0B);
            mStatusChip.setBackground(bg);
        }
    }

    // --- Window Drag & Lifecycle Handlers ---

    private View.OnTouchListener createDragListener(final boolean isBubble) {
        return new View.OnTouchListener() {
            private float touchStartX, touchStartY;
            private int initialX, initialY;
            private boolean isMoving = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = event.getRawX();
                        touchStartY = event.getRawY();
                        initialX = vmParams != null ? vmParams.x : mPosX;
                        initialY = vmParams != null ? vmParams.y : mPosY;
                        isMoving = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - touchStartX;
                        float dy = event.getRawY() - touchStartY;
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6) || isMoving) {
                            isMoving = true;
                            if (vmParams != null && mWindowManager != null) {
                                vmParams.x = initialX + (int) dx;
                                vmParams.y = initialY + (int) dy;
                                mPosX = vmParams.x;
                                mPosY = vmParams.y;
                                try {
                                    mWindowManager.updateViewLayout(rootFrame, vmParams);
                                } catch (Throwable ignored) {}
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isMoving) {
                            if (isBubble) {
                                expandMenu();
                            }
                        }
                        return true;
                }
                return false;
            }
        };
    }

    // --- Public Service Interface ---

    @SuppressLint("WrongConstant")
    public void SetWindowManagerWindowService() {
        int overlayType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        vmParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                PixelFormat.TRANSLUCENT);

        vmParams.gravity = Gravity.TOP | Gravity.START;
        vmParams.x = mPosX;
        vmParams.y = mPosY;

        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        if (mWindowManager != null) {
            try {
                mWindowManager.addView(rootFrame, vmParams);
                mIsAttached = true;
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to attach mod menu to WindowManager", t);
            }
        }
    }

    public void SetWindowManagerActivity() {
        if (mContext instanceof Activity) {
            vmParams = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            vmParams.gravity = Gravity.TOP | Gravity.START;
            vmParams.x = mPosX;
            vmParams.y = mPosY;

            mWindowManager = ((Activity) mContext).getWindowManager();
            if (mWindowManager != null) {
                try {
                    mWindowManager.addView(rootFrame, vmParams);
                    mIsAttached = true;
                } catch (Throwable t) {
                    AppLogger.logE(TAG, "Failed to attach activity menu", t);
                }
            }
        }
    }

    public void setVisibility(int visibility) {
        if (rootFrame != null) {
            rootFrame.setVisibility(visibility);
        }
    }

    public void expandMenu() {
        if (mCollapsedView != null && mExpandedView != null) {
            mCollapsedView.setVisibility(View.GONE);
            mExpandedView.setVisibility(View.VISIBLE);
            updateLiveTelemetry();
        }
    }

    public void collapseToLogo() {
        if (mCollapsedView != null && mExpandedView != null) {
            mExpandedView.setVisibility(View.GONE);
            mCollapsedView.setVisibility(View.VISIBLE);
        }
    }

    public void onDestroy() {
        if (mWindowManager != null && rootFrame != null && mIsAttached) {
            try {
                mWindowManager.removeView(rootFrame);
            } catch (Throwable ignored) {}
            mIsAttached = false;
        }
    }

    private int dp(int dip) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dip, mContext.getResources().getDisplayMetrics());
    }
}
