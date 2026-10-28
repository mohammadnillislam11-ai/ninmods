package com.ninmods.main.menu.ninlite;

import android.annotation.SuppressLint;
import android.content.Context;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.ninmods.main.R;
import com.ninmods.main.core.AppLogger;
import com.ninmods.main.core.FeatureCatalog;
import com.ninmods.main.core.FeatureSettings;
import com.ninmods.main.core.NativeBridge;
import com.ninmods.main.menu.MenuController;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * NinLiteFloatingMenuView:
 * 100% Matching Screenshot UI Design:
 * - Title: "NINLITE Mods" (Electric Cyan highlight)
 * - Sidebar: Circular bordered icons on the left
 * - Initial Page (Settings/Setup):
 *     Emulator: false
 *     Connection: Phantom
 *     Mode: External (Root)
 *     Status: Disconnected / Connected
 *     Game: Free Fire Max
 *     Action: [ Start ] Button (Large rounded Cyan button)
 * - Feature Page (Silent Aim):
 *     Radio/Circle Checkboxes (Silent Aim Enable, Ignore Knocked)
 *     Sliders (Silent Fov, Legit Delay) with cyan accent values
 *     Dropdown (Silent Target: Legit / Head / Neck)
 *
 * All interaction behavior, drag physics, and slider rules are powered
 * by the central MenuController.
 */
public class NinLiteFloatingMenuView {

    private static final String TAG = "NinLiteFloatingMenuView";

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final float mDensity;
    private final MenuController mController = MenuController.getInstance();

    private WindowManager.LayoutParams mParams;
    private FrameLayout mRootFrame;
    private View mCollapsedView;
    private LinearLayout mExpandedView;
    private ImageView mStartImage;

    // Sidebar & Pages
    private final int[] TAB_ICONS = {
            R.drawable.ic_nav_driver,   // Inject/Settings tab (Screenshot 2)
            R.drawable.ic_rail_aim,      // Aim / Crosshair (Screenshot 1)
            R.drawable.ic_rail_esp,      // Combat / Sword
            R.drawable.ic_user_profile   // User / Account
    };
    private LinearLayout[] mSidebarButtons;
    private ImageView[] mSidebarIcons;
    private ScrollView[] mContentSections;

    // Persistent state across menu reopenings during this session
    private static boolean sSettingsDismissed = false;

    /* Live refresh registry: when the OTHER menu changes a feature, this
     * menu must show the new value instead of a stale one. Both menus write
     * into the same FeatureSettings store, which notifies them. */
    private final Map<FeatureCatalog, Runnable> mRefreshers = new HashMap<>();

    private final FeatureSettings.Listener mFeatureListener =
            (feature, switchValue, sliderValue) ->
                    mMainHandler.post(() -> refreshFeature(feature));

    private int mSelectedTabIndex = 0;
    private boolean mIsAttached = false;
    private boolean mIsEngineStarting = false;
    private boolean mIsEngineStarted = false;
    /* The saved configuration is restored once the root shell is really up. */
    private boolean mEngineStateRestored = false;
    /* The game the backend feature switches were read for. */
    private String mLastGateGameTitle = "";

    // References for dynamic UI updates (All driven by C++ Real Engine)
    private TextView mEmulatorValView;
    private TextView mConnectionValView;
    private TextView mStatusValView;
    private TextView mGameValView;
    private TextView mStartBtnView;
    private TextView mTeleTargetGameVal;
    private TextView mTeleEngineModeVal;
    private TextView mTeleDaemonVal;

    // Authentic Screenshot Color Palette
    private static final int COLOR_CARD_BG = 0xF50B0F19;      // Deep Blackish Navy (#0B0F19)
    private static final int COLOR_SIDEBAR_BG = 0xFF080C14;   // Dark Charcoal Sidebar (#080C14)
    private static final int COLOR_CYAN = 0xFF00E5FF;         // Electric Neon Cyan (#00E5FF)
    private static final int COLOR_CYAN_LIGHT = 0xFF1DE9B6;   // Vibrant Mint Cyan (#1DE9B6)
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_MUTED = 0xFF8896A6;
    private static final int COLOR_ACTIVE_TAB_BG = 0x2200E5FF;
    private static final int COLOR_GREEN = 0xFF00E676;
    private static final int COLOR_RED = 0xFFFF1744;

    public NinLiteFloatingMenuView(Context context, WindowManager windowManager) {
        this.mContext = context;
        this.mWindowManager = windowManager;
        this.mDensity = context.getResources().getDisplayMetrics().density;
        this.mController.init(context);
        initViews();
        FeatureSettings.addListener(mFeatureListener);
    }

    /**
     * Detaches the view from the shared feature store. Called when the
     * overlay is stopped so a dismissed menu is not refreshed any more.
     */
    public void release() {
        FeatureSettings.removeListener(mFeatureListener);
        /* Release the observers that the central controller registered for the
         * bound rows of this menu, otherwise they keep a reference to the
         * discarded views. */
        try {
            if (mController != null && mRootFrame != null) {
                mController.unregisterViewListeners(mRootFrame);
            }
        } catch (Throwable ignored) {}
        if (mWindowManager != null && mRootFrame != null && mIsAttached) {
            try {
                if (mRootFrame.isAttachedToWindow()) {
                    mWindowManager.removeView(mRootFrame);
                }
            } catch (Throwable ignored) {}
            mIsAttached = false;
        }
    }

    /** Re-reads one feature from the store and updates its control. */
    private void refreshFeature(FeatureCatalog feature) {
        Runnable refresher = mRefreshers.get(feature);
        if (refresher == null) return;
        try {
            refresher.run();
        } catch (Throwable t) {
            AppLogger.logE(TAG, "Could not refresh the control of " + feature.key(), t);
        }
    }

    public FrameLayout getRootFrame() {
        return mRootFrame;
    }

    public WindowManager.LayoutParams getLayoutParams() {
        return mParams;
    }

    public boolean isAttached() {
        return mIsAttached;
    }

    public void setAttached(boolean attached) {
        this.mIsAttached = attached;
    }

    private int dp(float dpVal) {
        return (int) (dpVal * mDensity + 0.5f);
    }

    private void initViews() {
        mRootFrame = new FrameLayout(mContext);
        mRootFrame.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        mParams = mController.createWindowLayoutParams(dp(20), dp(140));

        mExpandedView = createExpandedMenuView();
        mCollapsedView = createCollapsedLogoView();

        mRootFrame.addView(mCollapsedView);
        mRootFrame.addView(mExpandedView);

        setupDragTouchListeners();

        mCollapsedView.setVisibility(View.VISIBLE);
        mExpandedView.setVisibility(View.GONE);
    }

    private void setupDragTouchListeners() {
        View.OnTouchListener bubbleListener = mController.createDragTouchListener(
                mRootFrame, mParams, mWindowManager, mCollapsedView, mExpandedView,
                this::expandMenu, this::collapseToLogo);

        if (mCollapsedView instanceof ViewGroup && ((ViewGroup) mCollapsedView).getChildCount() > 0) {
            View bubble = ((ViewGroup) mCollapsedView).getChildAt(0);
            if (bubble != null) {
                bubble.setOnTouchListener(bubbleListener);
            }
        } else if (mCollapsedView != null) {
            mCollapsedView.setOnTouchListener(bubbleListener);
        }

        if (mExpandedView != null && mExpandedView.getChildCount() > 0) {
            View header = mExpandedView.getChildAt(0);
            if (header != null) {
                header.setOnTouchListener(mController.createDragTouchListener(
                        mRootFrame, mParams, mWindowManager, mCollapsedView, mExpandedView,
                        this::expandMenu, this::collapseToLogo));
            }
        }
    }

    private View createCollapsedLogoView() {
        View.OnTouchListener logoTouch = mController.createDragTouchListener(
                mRootFrame, mParams, mWindowManager, null, mExpandedView,
                this::expandMenu, this::collapseToLogo);

        return mController.buildFloatingLogoWithCloseBadge(
                mContext,
                R.drawable.ic_menu_ninlite,
                COLOR_CYAN,
                logoTouch,
                () -> mController.killOverlay(mContext, "ninlite", this::release)
        );
    }

    private LinearLayout createExpandedMenuView() {
        LinearLayout outerCard = new LinearLayout(mContext);
        outerCard.setOrientation(LinearLayout.VERTICAL);

        int menuWidth = Math.min(dp(290), (int) (mContext.getResources().getDisplayMetrics().widthPixels * 0.90f));
        int menuHeight = Math.min(dp(325), (int) (mContext.getResources().getDisplayMetrics().heightPixels * 0.78f));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(menuWidth, menuHeight);
        outerCard.setLayoutParams(cardLp);

        // Dark Rounded Floating Container with subtle border
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(COLOR_CARD_BG);
        cardBg.setCornerRadius(dp(18));
        cardBg.setStroke(dp(1.2f), 0xFF182233);
        outerCard.setBackground(cardBg);

        // Header Title Bar ("NINLITE Mods" [X])
        LinearLayout headerBar = createHeaderBar();
        outerCard.addView(headerBar);

        // Body: Left Vertical Sidebar + Right Content Area
        LinearLayout bodyContainer = new LinearLayout(mContext);
        bodyContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        bodyContainer.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout sidebar = createSidebar();
        FrameLayout contentArea = createContentArea();

        bodyContainer.addView(sidebar);
        bodyContainer.addView(contentArea);

        outerCard.addView(bodyContainer);
        return outerCard;
    }

    private LinearLayout createHeaderBar() {
        LinearLayout header = new LinearLayout(mContext);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
        ));
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), 0, dp(14), 0);

        // Centered Title: "NINLITE Mods"
        TextView title = new TextView(mContext);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        title.setGravity(Gravity.CENTER);

        // Styled text with two colors: "NINLITE " + "Mods"
        android.text.SpannableString span = new android.text.SpannableString("NINLITE Mods");
        span.setSpan(new android.text.style.ForegroundColorSpan(COLOR_TEXT_WHITE), 0, 7, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new android.text.style.ForegroundColorSpan(COLOR_CYAN), 8, 12, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(span);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title);

        // Close/Minimize icon (✕) matching screenshot top right
        TextView closeBtn = new TextView(mContext);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setText("✕");
        closeBtn.setTextColor(COLOR_CYAN);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setOnClickListener(v -> collapseToLogo());
        header.addView(closeBtn);

        header.setOnTouchListener(mController.createDragTouchListener(
                mRootFrame, mParams, mWindowManager, mCollapsedView, mExpandedView,
                this::expandMenu, this::collapseToLogo));
        return header;
    }

    private LinearLayout createSidebar() {
        LinearLayout sidebar = new LinearLayout(mContext);
        sidebar.setLayoutParams(new LinearLayout.LayoutParams(
                dp(62),
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        sidebar.setPadding(dp(6), dp(8), dp(6), dp(8));

        GradientDrawable sideBg = new GradientDrawable();
        sideBg.setColor(COLOR_SIDEBAR_BG);
        sideBg.setCornerRadii(new float[]{0, 0, 0, 0, 0, 0, dp(18), dp(18)});
        sidebar.setBackground(sideBg);

        mSidebarButtons = new LinearLayout[TAB_ICONS.length];
        mSidebarIcons = new ImageView[TAB_ICONS.length];

        for (int i = 0; i < TAB_ICONS.length; i++) {
            final int index = i;
            LinearLayout btn = new LinearLayout(mContext);
            int circleSize = dp(42);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(circleSize, circleSize);
            btnLp.bottomMargin = dp(10);
            btn.setLayoutParams(btnLp);
            btn.setGravity(Gravity.CENTER);
            btn.setClickable(true);
            btn.setFocusable(true);

            ImageView iv = new ImageView(mContext);
            iv.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
            iv.setImageResource(TAB_ICONS[i]);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);

            btn.addView(iv);
            btn.setOnClickListener(v -> selectTab(index));

            mSidebarButtons[i] = btn;
            mSidebarIcons[i] = iv;
            if (sSettingsDismissed && i == 0) {
                btn.setVisibility(View.GONE);
            }
            sidebar.addView(btn);
        }

        return sidebar;
    }

    private FrameLayout createContentArea() {
        FrameLayout contentArea = new FrameLayout(mContext);
        contentArea.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        contentArea.setPadding(dp(12), dp(4), dp(12), dp(8));

        mContentSections = new ScrollView[TAB_ICONS.length];
        mContentSections[0] = createSettingsSetupSection(); // Screenshot 2 (Settings / Start)
        mContentSections[1] = createSilentAimSection();      // Screenshot 1 (Silent Aim)
        mContentSections[2] = createEspSection();            // ESP section
        mContentSections[3] = createUserSection();           // Profile / Telemetry

        for (int i = 0; i < mContentSections.length; i++) {
            contentArea.addView(mContentSections[i]);
            if (sSettingsDismissed && i == 0) {
                mContentSections[i].setVisibility(View.GONE);
            }
        }

        selectTab(sSettingsDismissed ? 1 : 0);
        return contentArea;
    }

    private void selectTab(int index) {
        if (sSettingsDismissed && index == 0) {
            index = 1;
        }
        mSelectedTabIndex = index;
        for (int i = 0; i < mContentSections.length; i++) {
            if (mContentSections[i] != null) {
                boolean show = (i == index) && (!sSettingsDismissed || i != 0);
                mContentSections[i].setVisibility(show ? View.VISIBLE : View.GONE);
            }
        }
        updateTabStyles();
    }

    private void updateTabStyles() {
        for (int i = 0; i < mSidebarButtons.length; i++) {
            if (mSidebarButtons[i] == null) continue;
            if (sSettingsDismissed && i == 0) {
                mSidebarButtons[i].setVisibility(View.GONE);
                continue;
            }
            boolean active = (i == mSelectedTabIndex);
            GradientDrawable circleBg = new GradientDrawable();
            circleBg.setShape(GradientDrawable.OVAL);
            if (active) {
                circleBg.setColor(COLOR_ACTIVE_TAB_BG);
                circleBg.setStroke(dp(1.8f), COLOR_CYAN);
                mSidebarIcons[i].setColorFilter(COLOR_CYAN);
            } else {
                circleBg.setColor(Color.TRANSPARENT);
                circleBg.setStroke(0, Color.TRANSPARENT);
                mSidebarIcons[i].setColorFilter(COLOR_TEXT_MUTED);
            }
            mSidebarButtons[i].setBackground(circleBg);
        }
    }

    // =========================================================================
    // Screenshot 2: Initial Setup & Info Page (100% C++ Real Engine Driven)
    // =========================================================================
    private ScrollView createSettingsSetupSection() {
        ScrollView sv = new ScrollView(mContext);
        sv.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sv.setVerticalScrollBarEnabled(false);

        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        // Section Title: "Settings"
        layout.addView(createSectionHeaderTitle("Settings"));

        // Live Dynamic Detection Rows queried directly from C++ engine
        boolean isEmu = NativeBridge.isEmulatorDetected();
        mEmulatorValView = createValueTextView(isEmu ? "true" : "false", isEmu ? COLOR_CYAN : COLOR_GREEN);
        layout.addView(createDynamicKeyValueRow("Emulator: ", mEmulatorValView));

        String connMode = NativeBridge.getConnectionMode();
        mConnectionValView = createValueTextView(connMode, COLOR_GREEN);
        layout.addView(createDynamicKeyValueRow("Connection: ", mConnectionValView));

        // Subheader Badge: "External (Root)"
        TextView rootBadge = new TextView(mContext);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.setMargins(0, dp(6), 0, dp(6));
        rootBadge.setLayoutParams(badgeLp);
        rootBadge.setText("External (Root)");
        rootBadge.setTextColor(COLOR_CYAN);
        rootBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        rootBadge.setTypeface(Typeface.DEFAULT_BOLD);
        layout.addView(rootBadge);

        // Status Row: "Status: Standby" -> "Starting..." -> "Connected" (Driven by C++ Engine)
        LinearLayout statusRow = new LinearLayout(mContext);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setPadding(0, dp(2), 0, dp(2));
        TextView statusLbl = new TextView(mContext);
        statusLbl.setText("Status: ");
        statusLbl.setTextColor(COLOR_TEXT_WHITE);
        statusLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        statusLbl.setTypeface(Typeface.DEFAULT_BOLD);

        mStatusValView = new TextView(mContext);
        mStatusValView.setText("Standby (Ready to Start)");
        mStatusValView.setTextColor(COLOR_RED);
        mStatusValView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mStatusValView.setTypeface(Typeface.DEFAULT_BOLD);
        statusRow.addView(statusLbl);
        statusRow.addView(mStatusValView);
        layout.addView(statusRow);

        // Dynamic Running Game Row: Queried from C++ engine (FindGamePid())
        LinearLayout gameRow = new LinearLayout(mContext);
        gameRow.setOrientation(LinearLayout.HORIZONTAL);
        gameRow.setPadding(0, dp(2), 0, dp(2));
        TextView gameLbl = new TextView(mContext);
        gameLbl.setText("Game: ");
        gameLbl.setTextColor(COLOR_TEXT_WHITE);
        gameLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        gameLbl.setTypeface(Typeface.DEFAULT_BOLD);

        int initialPid = NativeBridge.getDetectedGamePid();
        String initialTitle = NativeBridge.getDetectedGameTitle();
        mGameValView = new TextView(mContext);
        if (initialPid > 0) {
            mGameValView.setText(initialTitle + " (PID: " + initialPid + ")");
            mGameValView.setTextColor(COLOR_GREEN);
        } else {
            mGameValView.setText("Waiting for Game...");
            mGameValView.setTextColor(COLOR_TEXT_MUTED);
        }
        mGameValView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mGameValView.setTypeface(Typeface.DEFAULT_BOLD);
        gameRow.addView(gameLbl);
        gameRow.addView(mGameValView);
        layout.addView(gameRow);

        // Big Action Button: [ Start ] (Matches Screenshot 2 rounded Cyan Button)
        LinearLayout btnWrapper = new LinearLayout(mContext);
        btnWrapper.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnWrapper.setPadding(0, dp(14), 0, 0);

        mStartBtnView = new TextView(mContext);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        mStartBtnView.setLayoutParams(btnLp);
        mStartBtnView.setGravity(Gravity.CENTER);
        mStartBtnView.setText("Start");
        mStartBtnView.setTextColor(Color.BLACK);
        mStartBtnView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mStartBtnView.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(COLOR_CYAN);
        btnBg.setCornerRadius(dp(10));
        mStartBtnView.setBackground(btnBg);
        mStartBtnView.setClickable(true);
        mStartBtnView.setFocusable(true);

        mStartBtnView.setOnClickListener(v -> {
            startRealShellAndEngine();
        });

        btnWrapper.addView(mStartBtnView);
        layout.addView(btnWrapper);

        // Periodic Live Poller for Running Game Detection & Engine State from C++
        startLiveEnginePoller();

        sv.addView(layout);
        return sv;
    }

    private TextView createValueTextView(String text, int color) {
        TextView tv = new TextView(mContext);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private View createDynamicKeyValueRow(String key, TextView valTv) {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(3));

        TextView keyTv = new TextView(mContext);
        keyTv.setText(key);
        keyTv.setTextColor(COLOR_TEXT_WHITE);
        keyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        keyTv.setTypeface(Typeface.DEFAULT_BOLD);

        row.addView(keyTv);
        row.addView(valTv);
        return row;
    }

    private void startLiveEnginePoller() {
        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (sSettingsDismissed || !mIsAttached) return;

                new Thread(() -> {
                    try {
                        final String stateJsonStr = NativeBridge.getNinliteStateJson();
                        final boolean daemonRunning = NativeBridge.isShellDaemonRunning();
                        mMainHandler.post(() -> {
                            if (sSettingsDismissed || !mIsAttached) return;
                            applyEngineStateUpdate(stateJsonStr, daemonRunning);
                        });
                    } catch (Throwable t) {
                        AppLogger.logE(TAG, "Error querying native engine state: " + t.getMessage(), t);
                    }
                }, "NinLiteEnginePoller").start();

                if (mIsAttached) {
                    mMainHandler.postDelayed(this, 1000);
                }
            }
        }, 500);
    }

    private void applyEngineStateUpdate(String stateJsonStr, boolean daemonRunning) {
        try {
            org.json.JSONObject state = new org.json.JSONObject(stateJsonStr);
            boolean isStarting = state.optBoolean("isStarting", false);
            boolean isEngineStarted = state.optBoolean("isEngineStarted", false);
            mIsEngineStarting = isStarting;
            mIsEngineStarted = isEngineStarted;
            boolean canStart = state.optBoolean("canStart", false);
            boolean gameDetected = state.optBoolean("gameDetected", false);
            int pid = state.optInt("gamePid", 0);
            String gameTitle = state.optString("gameTitle", "Waiting for Game...");
            boolean isEmu = state.optBoolean("isEmulator", false);
            String connMode = state.optString("connectionMode", "Phantom");
            String statusText = state.optString("statusText", "");
            String buttonText = state.optString("buttonText", "Start");

            if (mEmulatorValView != null) {
                mEmulatorValView.setText(isEmu ? "true" : "false");
                mEmulatorValView.setTextColor(isEmu ? COLOR_CYAN : COLOR_GREEN);
            }
            if (mConnectionValView != null) {
                mConnectionValView.setText(connMode);
            }
            if (mTeleEngineModeVal != null) {
                mTeleEngineModeVal.setText(connMode);
            }
            if (mTeleDaemonVal != null) {
                mTeleDaemonVal.setText(daemonRunning ? "Online (Connected)" : "Offline");
                mTeleDaemonVal.setTextColor(daemonRunning ? COLOR_GREEN : COLOR_RED);
            }
            if (mGameValView != null) {
                if (pid > 0) {
                    mGameValView.setText(gameTitle + " (PID: " + pid + ")");
                    mGameValView.setTextColor(COLOR_GREEN);
                } else {
                    mGameValView.setText("Waiting for Game...");
                    mGameValView.setTextColor(COLOR_TEXT_MUTED);
                }
            }
            if (mTeleTargetGameVal != null) {
                if (pid > 0) {
                    mTeleTargetGameVal.setText(gameTitle + " [" + pid + "]");
                    mTeleTargetGameVal.setTextColor(COLOR_GREEN);
                } else {
                    mTeleTargetGameVal.setText("Waiting for Game");
                    mTeleTargetGameVal.setTextColor(COLOR_TEXT_MUTED);
                }
            }

            if (mStatusValView != null) {
                mStatusValView.setText(statusText);
                if (isEngineStarted) {
                    mStatusValView.setTextColor(COLOR_GREEN);
                } else if (isStarting) {
                    mStatusValView.setTextColor(COLOR_CYAN_LIGHT);
                } else if (gameDetected) {
                    mStatusValView.setTextColor(COLOR_CYAN);
                } else {
                    mStatusValView.setTextColor(COLOR_TEXT_MUTED);
                }
            }

            if (mStartBtnView != null) {
                mStartBtnView.setText(buttonText);
                if (isEngineStarted) {
                    mStartBtnView.setTextColor(Color.WHITE);
                    GradientDrawable greenBg = new GradientDrawable();
                    greenBg.setColor(COLOR_GREEN);
                    greenBg.setCornerRadius(dp(10));
                    mStartBtnView.setBackground(greenBg);
                    mStartBtnView.setClickable(false);
                } else if (isStarting) {
                    mStartBtnView.setTextColor(COLOR_CYAN_LIGHT);
                    GradientDrawable startingBg = new GradientDrawable();
                    startingBg.setColor(Color.parseColor("#152836"));
                    startingBg.setCornerRadius(dp(10));
                    mStartBtnView.setBackground(startingBg);
                    mStartBtnView.setClickable(false);
                } else if (canStart) {
                    mStartBtnView.setTextColor(Color.BLACK);
                    GradientDrawable cyanBg = new GradientDrawable();
                    cyanBg.setColor(COLOR_CYAN);
                    cyanBg.setCornerRadius(dp(10));
                    mStartBtnView.setBackground(cyanBg);
                    mStartBtnView.setClickable(true);
                } else {
                    mStartBtnView.setTextColor(Color.parseColor("#667788"));
                    GradientDrawable disabledBg = new GradientDrawable();
                    disabledBg.setColor(Color.parseColor("#161F2C"));
                    disabledBg.setCornerRadius(dp(10));
                    mStartBtnView.setBackground(disabledBg);
                    mStartBtnView.setClickable(false);
                }
            }

            if (!gameTitle.equals(mLastGateGameTitle)) {
                mLastGateGameTitle = gameTitle;
                String gateGameType = FeatureSettings.gameTypeForTitle(gameTitle);
                if (!gateGameType.isEmpty()) {
                    FeatureSettings.refreshServerGates(mContext, gateGameType);
                }
            }
        } catch (Throwable t) {
            AppLogger.logE(TAG, "Error parsing native engine state: " + t.getMessage(), t);
        }
    }

    private void startRealShellAndEngine() {
        if (mIsEngineStarted || mIsEngineStarting) return;
        mIsEngineStarting = true;

        if (mStatusValView != null) {
            mStatusValView.setText("Injecting Root Shell & Daemon...");
            mStatusValView.setTextColor(COLOR_CYAN_LIGHT);
        }
        if (mStartBtnView != null) {
            mStartBtnView.setText("Starting...");
            mStartBtnView.setClickable(false);
        }

        // Delegate 100% of root shell, SELinux policy, and daemon execution to C++
        String nativeLibDir = mContext.getApplicationInfo().nativeLibraryDir;
        NativeBridge.startNinliteEngineNative(nativeLibDir);

        // Attach ESP surface when engine is started
        com.ninmods.main.overlay.NativeOverlayManager.attachEspSurface(mContext, mWindowManager);
    }


    // =========================================================================
    // Screenshot 1: Silent Aim Page (Radio Checkboxes, Fov & Delay Sliders, Dropdown)
    // =========================================================================
    private ScrollView createSilentAimSection() {
        ScrollView sv = new ScrollView(mContext);
        sv.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sv.setVerticalScrollBarEnabled(false);

        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);

        // Section Title: "Silent Aim"
        layout.addView(createSectionHeaderTitle("Silent Aim"));

        // Circle/Radio Checkboxes (Screenshot 1).
        // The initial state is read from the engine through FeatureSettings.
        // It used to be a hard coded literal, which is why the menu showed
        // options as ENABLED while the engine had them switched off.
        layout.addView(createCircleCheckboxRow("Silent Aim Enable", FeatureCatalog.AIM_SILENT));
        layout.addView(createCircleCheckboxRow("Ignore Knocked", FeatureCatalog.AIM_IGNORE_KNOCKED));

        // Sliders with Cyan Value indicator: "Silent Fov", "Legit Delay"
        layout.addView(createAccentSliderRow("Silent Fov", FeatureCatalog.AIM_FOV));
        layout.addView(createAccentSliderRow("Legit Delay", FeatureCatalog.AIM_LEGIT_DELAY));

        // Dropdown Header: "Silent Target"
        TextView targetHeader = new TextView(mContext);
        targetHeader.setText("Silent Target");
        targetHeader.setTextColor(COLOR_TEXT_WHITE);
        targetHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        targetHeader.setTypeface(Typeface.DEFAULT_BOLD);
        targetHeader.setPadding(0, dp(10), 0, dp(4));
        layout.addView(targetHeader);

        // Styled Dropdown Spinner ("Legit", "Head", "Chest", "Neck")
        Spinner spinner = new Spinner(mContext);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        spinner.setLayoutParams(spinLp);

        String[] targets = {"Legit", "Head", "Chest", "Neck"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(mContext, android.R.layout.simple_spinner_dropdown_item, targets) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(COLOR_TEXT_WHITE);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                return tv;
            }
        };
        spinner.setAdapter(adapter);

        // Unified dropdown binding via MenuController
        mController.bindDropdown(spinner, FeatureCatalog.AIM_SILENT_TARGET.nativeId(),
                FeatureCatalog.AIM_SILENT_TARGET.key(), targets, 0);

        layout.addView(spinner);
        sv.addView(layout);
        return sv;
    }

    // =========================================================================
    // ESP section (the native feature set with a clean UI)
    // =========================================================================
    private ScrollView createEspSection() {
        ScrollView sv = new ScrollView(mContext);
        sv.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sv.setVerticalScrollBarEnabled(false);

        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);

        layout.addView(createSectionHeaderTitle("ESP Visuals"));
        /* The classic overlay menu drives the very same engine flags under
         * different numeric ids (2/4/5/7/6 instead of 111..115). Both menus
         * now use the shared FeatureCatalog, so one flag can no longer be
         * changed through two different ids. */
        layout.addView(createCircleCheckboxRow("ESP Box", FeatureCatalog.ESP_BOX));
        layout.addView(createCircleCheckboxRow("ESP Line", FeatureCatalog.ESP_LINE));
        layout.addView(createCircleCheckboxRow("ESP Health Bar", FeatureCatalog.ESP_HEALTH));
        layout.addView(createCircleCheckboxRow("ESP Distance", FeatureCatalog.ESP_DISTANCE));
        layout.addView(createCircleCheckboxRow("ESP Name", FeatureCatalog.ESP_NAME));

        layout.addView(createAccentSliderRow("Box Style (0-3)", FeatureCatalog.ESP_BOX_STYLE));
        layout.addView(createAccentSliderRow("Line Snap Mode", FeatureCatalog.ESP_LINE_SNAP));

        sv.addView(layout);
        return sv;
    }

    // =========================================================================
    // Profile / Telemetry Section
    // =========================================================================
    private ScrollView createUserSection() {
        ScrollView sv = new ScrollView(mContext);
        sv.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sv.setVerticalScrollBarEnabled(false);

        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);

        layout.addView(createSectionHeaderTitle("NINLITE Telemetry"));

        int pid = NativeBridge.getDetectedGamePid();
        String game = NativeBridge.getDetectedGameTitle();
        String conn = NativeBridge.getConnectionMode();
        boolean daemonRunning = NativeBridge.isShellDaemonRunning();

        layout.addView(createKeyValueRow("Architecture: ", "AArch64 (ARM64)", COLOR_CYAN));

        mTeleEngineModeVal = createValueTextView(conn, COLOR_GREEN);
        layout.addView(createDynamicKeyValueRow("Engine Mode: ", mTeleEngineModeVal));

        mTeleTargetGameVal = createValueTextView(pid > 0 ? (game + " [" + pid + "]") : "Waiting for Game", pid > 0 ? COLOR_GREEN : COLOR_TEXT_MUTED);
        layout.addView(createDynamicKeyValueRow("Target Game: ", mTeleTargetGameVal));

        mTeleDaemonVal = createValueTextView(daemonRunning ? "Online (Connected)" : "Offline", daemonRunning ? COLOR_GREEN : COLOR_RED);
        layout.addView(createDynamicKeyValueRow("Daemon Stream: ", mTeleDaemonVal));

        layout.addView(createKeyValueRow("Memory Access: ", memoryAccessMode(), COLOR_CYAN));

        layout.addView(createKeyValueRow("Central Offsets: ", "gameoffsets.h (Unified C++)", COLOR_CYAN));

        sv.addView(layout);
        return sv;
    }

    /**
     * The real memory path of the engine.
     *
     * The panel used to show a hardcoded "process_vm_readv / pread64", but
     * both the app engine and the root daemon read and write through the
     * kernel driver - so the native side reports the truth.
     */
    private String memoryAccessMode() {
        try {
            String mode = NativeBridge.getMemoryAccessMode();
            return (mode == null || mode.isEmpty()) ? "Not connected" : mode;
        } catch (Throwable t) {
            return "Not connected";
        }
    }

    // =========================================================================
    // UI Helpers & Screenshot Component Clones
    // =========================================================================

    private TextView createSectionHeaderTitle(String text) {
        TextView title = new TextView(mContext);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(10));
        title.setLayoutParams(lp);
        title.setGravity(Gravity.CENTER);
        title.setText(text);
        title.setTextColor(COLOR_CYAN);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        return title;
    }

    private View createKeyValueRow(String key, String val, int valColor) {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(3));

        TextView keyTv = new TextView(mContext);
        keyTv.setText(key);
        keyTv.setTextColor(COLOR_TEXT_WHITE);
        keyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        keyTv.setTypeface(Typeface.DEFAULT_BOLD);

        TextView valTv = new TextView(mContext);
        valTv.setText(val);
        valTv.setTextColor(valColor);
        valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        valTv.setTypeface(Typeface.DEFAULT_BOLD);

        row.addView(keyTv);
        row.addView(valTv);
        return row;
    }

    // Exact Screenshot 1 Circular Checkbox (Circle outline with white fill when checked)
    private View createCircleCheckboxRow(String label, final FeatureCatalog feature) {
        LinearLayout row = new LinearLayout(mContext);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);

        // Circle indicator matching screenshot (White thick circle border)
        final FrameLayout circleBox = new FrameLayout(mContext);
        int circleSize = dp(22);
        LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(circleSize, circleSize);
        circleLp.rightMargin = dp(10);
        circleBox.setLayoutParams(circleLp);

        // Center check dot
        final View innerDot = new View(mContext);
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dp(10), dp(10));
        dotLp.gravity = Gravity.CENTER;
        innerDot.setLayoutParams(dotLp);
        circleBox.addView(innerDot);

        TextView tv = new TextView(mContext);
        tv.setText(label);
        tv.setTextColor(COLOR_TEXT_WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);

        row.addView(circleBox);
        row.addView(tv);

        // Unified circle checkbox binding via MenuController
        mController.bindCircleCheckbox(row, circleBox, innerDot, feature.nativeId(), feature.key(),
                feature.defaultValue() > 0.5f, Color.WHITE, Color.WHITE, COLOR_CARD_BG);

        return row;
    }

    // Exact Screenshot 1 Slider Row ("Silent Fov: 120" with cyan value)
    private View createAccentSliderRow(String label, final FeatureCatalog feature) {
        final int min = feature.min();
        final int max = feature.max();

        LinearLayout container = new LinearLayout(mContext);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(4), 0, dp(6));

        // Header: "Label: " + Value (Cyan)
        LinearLayout textRow = new LinearLayout(mContext);
        textRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView labelTv = new TextView(mContext);
        labelTv.setText(label + ": ");
        labelTv.setTextColor(COLOR_TEXT_WHITE);
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        labelTv.setTypeface(Typeface.DEFAULT_BOLD);

        final TextView valTv = new TextView(mContext);
        valTv.setTextColor(COLOR_CYAN_LIGHT);
        valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        valTv.setTypeface(Typeface.DEFAULT_BOLD);

        textRow.addView(labelTv);
        textRow.addView(valTv);
        container.addView(textRow);

        // Thin Sleek SeekBar with white thumb & cyan progress
        SeekBar sb = new SeekBar(mContext);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sb.setProgressTintList(android.content.res.ColorStateList.valueOf(COLOR_CYAN_LIGHT));
            sb.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        }

        // Unified accent slider binding via central MenuController
        mController.bindAccentSlider(sb, valTv, feature.nativeId(), feature.key(), min, max, "");

        container.addView(sb);
        return container;
    }

    /**
     * Expands the NINLITE menu from floating icon to full HUD card with smooth slide animation.
     */
    public void expandMenu() {
        if (mCollapsedView != null && mExpandedView != null) {
            mController.animateExpand(mCollapsedView, mExpandedView, mParams, mWindowManager, null);
        }
    }

    /**
     * Collapses the NINLITE menu back to floating icon with smooth spring animation.
     */
    public void collapseToLogo() {
        if (mCollapsedView != null && mExpandedView != null) {
            mController.animateCollapse(mCollapsedView, mExpandedView, mParams, mWindowManager, null);
        }
    }
}
