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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NINMODS MODERN FLOATING MOD MENU
 * ---------------------------------
 * Sleek, high-performance, dark glassmorphism floating menu.
 *
 * All feature activations, sliding rules, persistence, and JNI engine
 * communication are driven by the central MenuController.
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

    // --- Styling Constants ---
    private static final int ICON_SIZE_DP = 52;
    private static final int MENU_WIDTH_DP = 310;
    private static final int MENU_HEIGHT_DP = 430;
    private static final int CORNER_RADIUS_DP = 16;
    private static final int ITEM_CORNER_DP = 10;

    // Palette
    private static final int COLOR_CARD_BG = 0xF20F121C;
    private static final int COLOR_CARD_BORDER = 0xFF242A3B;
    private static final int COLOR_ITEM_BG = 0xFF181C28;
    private static final int COLOR_ITEM_BORDER = 0xFF232838;
    private static final int COLOR_HEADER_BG = 0x80131722;
    private static final int COLOR_ACCENT_CYAN = 0xFF00E5FF;
    private static final int COLOR_ACCENT_BLUE = 0xFF3B82F6;
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int COLOR_TEXT_MUTED = 0xFF94A3B8;
    private static final int COLOR_TEXT_DIM = 0xFF64748B;
    private static final int COLOR_STATUS_GREEN = 0xFF10B981;
    private static final int COLOR_STATUS_AMBER = 0xFFF59E0B;
    private static final int COLOR_SWITCH_OFF = 0xFF2D3748;

    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final MenuController mController;

    private WindowManager mWindowManager;
    private WindowManager.LayoutParams vmParams;

    private FrameLayout rootFrame;
    private View mCollapsedView;
    private LinearLayout mExpandedView;
    private ImageView mFloatingBubble;

    private LinearLayout mTabBar;
    private final List<TextView> mTabButtons = new ArrayList<>();
    private final Map<String, LinearLayout> mTabContainers = new LinkedHashMap<>();
    private String mActiveCategory = "VISUALS";
    private TextView mStatusChip;
    private TextView mFooterText;

    private int mPosX = 30;
    private int mPosY = 180;
    private boolean mIsAttached = false;

    public ModMenu(Context context) {
        this.mContext = context;
        this.mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.mController = MenuController.getInstance();
        this.mController.init(context);
        this.vmParams = mController.createWindowLayoutParams(mPosX, mPosY);

        initViews();
    }

    private void initViews() {
        rootFrame = new FrameLayout(mContext);

        buildCollapsedView();
        buildExpandedView();

        rootFrame.addView(mCollapsedView);
        rootFrame.addView(mExpandedView);

        mCollapsedView.setVisibility(View.VISIBLE);
        mExpandedView.setVisibility(View.GONE);
    }

    /**
     * Floating Draggable Bubble with Integrated Corner Close Button (Collapsed State)
     */
    @SuppressLint("ClickableViewAccessibility")
    private void buildCollapsedView() {
        mCollapsedView = mController.buildFloatingLogoWithCloseBadge(
                mContext,
                R.drawable.nin_logo,
                COLOR_ACCENT_CYAN,
                createDragListener(true),
                () -> mController.killOverlay(mContext, "nin", this::onDestroy)
        );
    }

    /**
     * Modern Card (Expanded State)
     */
    @SuppressLint("ClickableViewAccessibility")
    private void buildExpandedView() {
        mExpandedView = new LinearLayout(mContext);
        mExpandedView.setOrientation(LinearLayout.VERTICAL);

        int widthPx = dp(MENU_WIDTH_DP);
        int heightPx = dp(MENU_HEIGHT_DP);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(widthPx, heightPx);
        mExpandedView.setLayoutParams(cardParams);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(CORNER_RADIUS_DP));
        cardBg.setColor(COLOR_CARD_BG);
        cardBg.setStroke(dp(1), COLOR_CARD_BORDER);
        mExpandedView.setBackground(cardBg);

        // Header
        mExpandedView.addView(buildHeaderBar());

        // Horizontal Segmented Pill Tabs
        mExpandedView.addView(buildTabBar());

        // Content Area
        ScrollView contentScroll = new ScrollView(mContext);
        contentScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        contentScroll.setVerticalScrollBarEnabled(false);
        contentScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        FrameLayout contentHolder = new FrameLayout(mContext);
        contentHolder.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        createCategoryContainer("VISUALS", contentHolder);
        createCategoryContainer("AIMBOT", contentHolder);
        createCategoryContainer("COMBAT", contentHolder);
        createCategoryContainer("SYSTEM", contentHolder);

        contentScroll.addView(contentHolder);
        mExpandedView.addView(contentScroll);

        // Footer
        mExpandedView.addView(buildFooterBar());
    }

    @SuppressLint("ClickableViewAccessibility")
    private View buildHeaderBar() {
        LinearLayout header = new LinearLayout(mContext);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(12), dp(10));
        header.setBackgroundColor(COLOR_HEADER_BG);
        header.setOnTouchListener(createDragListener(false));

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
        subtitleText.setText(mController.getEngineMode().toUpperCase());
        subtitleText.setTextColor(COLOR_TEXT_DIM);
        subtitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        subtitleText.setLetterSpacing(0.08f);
        titleCol.addView(subtitleText);

        header.addView(titleCol);

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

        TextView minBtn = createHeaderButton("—", v -> collapseToLogo());
        header.addView(minBtn);

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

    private View buildFooterBar() {
        LinearLayout footer = new LinearLayout(mContext);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(14), dp(8), dp(14), dp(8));
        footer.setBackgroundColor(0x550C0E17);

        mFooterText = new TextView(mContext);
        String vStr = mContext.getString(R.string.app_version);
        mFooterText.setText(mContext.getString(R.string.app_name).toUpperCase() + " V" + vStr + " • " + mController.getEngineMode().toUpperCase());
        mFooterText.setTextColor(COLOR_TEXT_DIM);
        mFooterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        mFooterText.setLetterSpacing(0.05f);

        LinearLayout.LayoutParams ftParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        mFooterText.setLayoutParams(ftParams);
        footer.addView(mFooterText);

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

    // --- Dynamic Population via MenuController & JNI ---

    public void ShowMenu() {
        if (mCollapsedView != null && mCollapsedView.getParent() == null) {
            rootFrame.addView(mCollapsedView);
        }
        if (mExpandedView != null && mExpandedView.getParent() == null) {
            rootFrame.addView(mExpandedView);
        }
        if (mCollapsedView != null) {
            mCollapsedView.setVisibility(View.VISIBLE);
        }
        if (mExpandedView != null) {
            mExpandedView.setVisibility(View.GONE);
        }

        mMainHandler.postDelayed(() -> {
            populateAllFeatures();
            startTelemetryPoller();
        }, 200);
    }

    private void populateAllFeatures() {
        for (LinearLayout container : mTabContainers.values()) {
            /* The observers of the old rows have to be released before the
             * rows are thrown away, otherwise the central list grows with
             * every rebuild and the discarded views keep being updated. */
            for (int i = 0; i < container.getChildCount(); i++) {
                try {
                    MenuController.getInstance().unregisterViewListeners(container.getChildAt(i));
                } catch (Throwable ignored) {}
            }
            container.removeAllViews();
        }

        // Fetch dynamic JNI DSL features without hardcoding
        String[] rawDsl = sNativeReady ? GetFeatureList() : new String[0];
        List<MenuController.MenuFeatureItem> items = MenuController.parseDsl(rawDsl);

        if (items.isEmpty()) {
            /* The native DSL is the single source of truth. Falling back to a
             * second Java catalog silently desynchronised labels, ids and
             * defaults from the C++ engine. Show an explicit unavailable state
             * instead of rendering controls that do not belong to the engine. */
            AppLogger.logE(TAG, "Native feature catalog is empty; no feature controls rendered", null);
            TextView unavailable = new TextView(mContext);
            unavailable.setText("Native feature catalog unavailable");
            unavailable.setTextColor(0xFFFF5252);
            unavailable.setPadding(dp(12), dp(12), dp(12), dp(12));
            LinearLayout visuals = mTabContainers.get("VISUALS");
            if (visuals != null) visuals.addView(unavailable);
        }

        for (MenuController.MenuFeatureItem item : items) {
            LinearLayout container = mTabContainers.get(item.category);
            if (container == null) {
                container = mTabContainers.get("VISUALS");
            }
            if (container == null) continue;

            if ("Toggle".equalsIgnoreCase(item.type)) {
                addToggleItem(container, item.id, item.name, item.defaultOn);
            } else if ("SeekBar".equalsIgnoreCase(item.type)) {
                addSliderItem(container, item.id, item.name, item.minVal, item.maxVal);
            } else if ("Spinner".equalsIgnoreCase(item.type)) {
                addOptionsItem(container, item.id, item.name, item.options);
            }
        }

        populateSystemTab();
    }

    private void populateSystemTab() {
        LinearLayout sys = mTabContainers.get("SYSTEM");
        if (sys == null) return;

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
        addTelemetryRow(infoCard, "Target Game PID", String.valueOf(mController.getTargetPid()));

        sys.addView(infoCard);

        addToggleItem(sys, -1, "Save Feature Preferences", MenuPreferences.loadPref);

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
            FeatureSettings.resetAllToDefaults();
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

    private LinearLayout.LayoutParams createItemLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(6);
        return params;
    }

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
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        item.addView(title);

        Switch sw = new Switch(mContext);
        mController.bindSwitch(sw, featId, label, defaultVal);
        applySwitchStyling(sw);

        mController.addListener((fId, state, val) -> {
            if (fId == featId) {
                applySwitchStyling(sw);
            }
        });

        sw.setOnCheckedChangeListener((btn, isChecked) -> {
            if (btn.isPressed()) {
                mController.toggleFeature(mContext, featId, label, isChecked);
            }
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

        LinearLayout topRow = new LinearLayout(mContext);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(mContext);
        title.setText(label);
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        topRow.addView(title);

        TextView valBadge = new TextView(mContext);
        valBadge.setTextColor(COLOR_ACCENT_CYAN);
        valBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        valBadge.setTypeface(Typeface.DEFAULT_BOLD);
        topRow.addView(valBadge);

        item.addView(topRow);

        SeekBar seekBar = new SeekBar(mContext);
        seekBar.setPadding(dp(4), dp(6), dp(4), dp(2));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT_BLUE));
            seekBar.setThumbTintList(ColorStateList.valueOf(COLOR_ACCENT_CYAN));
        }

        // Bind seekbar to central MenuController
        mController.bindSeekBar(seekBar, valBadge, featId, label, min, max, "");

        item.addView(seekBar);
        container.addView(item);
    }

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

        int savedIndex = mController.getOptionIndex(mContext, featId, label, options.length, 0);
        final List<TextView> chipViews = new ArrayList<>();

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView chip = new TextView(mContext);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(26), 1.0f);
            p.setMargins(dp(2), 0, dp(2), 0);
            chip.setLayoutParams(p);
            chip.setText(options[i]);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);

            chip.setOnClickListener(v -> {
                mController.setOptionValue(mContext, featId, label, index);
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
        int pid = mController.getTargetPid();
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

    /**
     * Creates the drag and tap motion listener backed by MenuController.
     *
     * @param isBubble True if attaching to the floating bubble icon, false for the header.
     * @return Universal touch listener with screen clamping and slide animation.
     */
    private View.OnTouchListener createDragListener(final boolean isBubble) {
        return mController.createDragTouchListener(
                rootFrame, vmParams, mWindowManager, mCollapsedView, mExpandedView,
                this::expandMenu, this::collapseToLogo);
    }

    /**
     * Configures WindowManager parameters and attaches the root overlay to the WindowService.
     */
    @SuppressLint("WrongConstant")
    public void SetWindowManagerWindowService() {
        if (vmParams == null) {
            vmParams = mController.createWindowLayoutParams(mPosX, mPosY);
        }
        if (mWindowManager == null) {
            mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        }
        if (mWindowManager != null && rootFrame != null && !mIsAttached) {
            try {
                mWindowManager.addView(rootFrame, vmParams);
                mIsAttached = true;
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to attach mod menu to WindowManager", t);
            }
        }
    }

    /**
     * Attaches the menu overlay in an Activity context.
     */
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
            if (mWindowManager != null && rootFrame != null && !mIsAttached) {
                try {
                    mWindowManager.addView(rootFrame, vmParams);
                    mIsAttached = true;
                } catch (Throwable t) {
                    AppLogger.logE(TAG, "Failed to attach activity menu", t);
                }
            }
        }
    }

    /**
     * Toggles visibility of the root frame container.
     *
     * @param visibility View visibility integer (VISIBLE, GONE, INVISIBLE).
     */
    public void setVisibility(int visibility) {
        if (rootFrame != null) {
            rootFrame.setVisibility(visibility);
        }
    }

    /**
     * Expands the menu from floating bubble to full tactical card with smooth slide animation.
     */
    public void expandMenu() {
        if (mCollapsedView != null && mExpandedView != null) {
            mController.animateExpand(mCollapsedView, mExpandedView, vmParams, mWindowManager, this::updateLiveTelemetry);
        }
    }

    /**
     * Collapses the menu back to floating bubble with smooth spring animation.
     */
    public void collapseToLogo() {
        if (mCollapsedView != null && mExpandedView != null) {
            mController.animateCollapse(mCollapsedView, mExpandedView, vmParams, mWindowManager, null);
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
