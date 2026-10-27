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
 */
public class NinLiteFloatingMenuView {

    private static final String TAG = "NinLiteFloatingMenuView";

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final float mDensity;

    private WindowManager.LayoutParams mParams;
    private FrameLayout mRootFrame;
    private RelativeLayout mCollapsedView;
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
        initViews();
        FeatureSettings.addListener(mFeatureListener);
    }

    /**
     * Detaches the view from the shared feature store. Called when the
     * overlay is stopped so a dismissed menu is not refreshed any more.
     */
    public void release() {
        FeatureSettings.removeListener(mFeatureListener);
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

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;

        mParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                flags,
                PixelFormat.TRANSLUCENT
        );
        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = dp(20);
        mParams.y = dp(140);

        mCollapsedView = createCollapsedLogoView();
        mExpandedView = createExpandedMenuView();

        mRootFrame.addView(mCollapsedView);
        mRootFrame.addView(mExpandedView);

        View.OnTouchListener logoTouch = createLogoTouchListener();
        mCollapsedView.setOnTouchListener(logoTouch);
        if (mStartImage != null) {
            mStartImage.setOnTouchListener(logoTouch);
        }

        mCollapsedView.setVisibility(View.VISIBLE);
        mExpandedView.setVisibility(View.GONE);
    }

    private RelativeLayout createCollapsedLogoView() {
        RelativeLayout collapsed = new RelativeLayout(mContext);
        int logoSize = dp(52);
        collapsed.setLayoutParams(new RelativeLayout.LayoutParams(logoSize, logoSize));
        collapsed.setBackgroundColor(Color.TRANSPARENT);

        mStartImage = new ImageView(mContext);
        mStartImage.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mStartImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mStartImage.setImageResource(R.drawable.ic_menu_ninlite);
        collapsed.addView(mStartImage);

        return collapsed;
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

        header.setOnTouchListener(createHeaderDragListener());
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
                if (sSettingsDismissed) return;

                // Query C++ Real Engine for unified state
                String stateJsonStr = NativeBridge.getNinliteStateJson();
                try {
                    org.json.JSONObject state = new org.json.JSONObject(stateJsonStr);
                    boolean isStarting = state.optBoolean("isStarting", false);
                    boolean shellActive = state.optBoolean("shellActive", false);
                    boolean gameDetected = state.optBoolean("gameDetected", false);
                    int pid = state.optInt("gamePid", 0);
                    String gameTitle = state.optString("gameTitle", "Waiting for Game...");
                    boolean isEmu = state.optBoolean("isEmulator", false);
                    String connMode = state.optString("connectionMode", "Phantom");
                    String statusText = state.optString("statusText", "");
                    String buttonText = state.optString("buttonText", "Start");
                    boolean readyToDismiss = state.optBoolean("readyToDismiss", false);

                    if (mEmulatorValView != null) {
                        mEmulatorValView.setText(isEmu ? "true" : "false");
                        mEmulatorValView.setTextColor(isEmu ? COLOR_CYAN : COLOR_GREEN);
                    }
                    if (mConnectionValView != null) {
                        mConnectionValView.setText(connMode);
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

                    if (mStatusValView != null) {
                        mStatusValView.setText(statusText);
                        if (readyToDismiss) {
                            mStatusValView.setTextColor(COLOR_GREEN);
                        } else if (shellActive) {
                            mStatusValView.setTextColor(COLOR_CYAN);
                        } else if (isStarting) {
                            mStatusValView.setTextColor(COLOR_CYAN_LIGHT);
                        } else {
                            mStatusValView.setTextColor(COLOR_TEXT_MUTED);
                        }
                    }

                    if (mStartBtnView != null) {
                        mStartBtnView.setText(buttonText);
                        if (readyToDismiss) {
                            mStartBtnView.setTextColor(Color.WHITE);
                            GradientDrawable greenBg = new GradientDrawable();
                            greenBg.setColor(COLOR_GREEN);
                            greenBg.setCornerRadius(dp(10));
                            mStartBtnView.setBackground(greenBg);
                            mStartBtnView.setClickable(false);
                        } else if (shellActive) {
                            mStartBtnView.setTextColor(COLOR_CYAN);
                            GradientDrawable cyanBg = new GradientDrawable();
                            cyanBg.setColor(Color.parseColor("#102525"));
                            cyanBg.setStroke(dp(1), COLOR_CYAN);
                            cyanBg.setCornerRadius(dp(10));
                            mStartBtnView.setBackground(cyanBg);
                            mStartBtnView.setClickable(false);
                        } else if (isStarting) {
                            mStartBtnView.setClickable(false);
                        } else {
                            mStartBtnView.setClickable(true);
                        }
                    }

                    /* The engine switches the ESP master on while it boots,
                     * so the saved configuration is restored once the root
                     * shell is really up. */
                    if (shellActive && !mEngineStateRestored) {
                        mEngineStateRestored = true;
                        FeatureSettings.applyAllToEngine();
                    }

                    /* The global feature switches of the backend belong to
                     * the running game, so they are read again when the game
                     * changes. */
                    if (!gameTitle.equals(mLastGateGameTitle)) {
                        mLastGateGameTitle = gameTitle;
                        String gateGameType = FeatureSettings.gameTypeForTitle(gameTitle);
                        if (!gateGameType.isEmpty()) {
                            FeatureSettings.refreshServerGates(mContext, gateGameType);
                        }
                    }

                    if (readyToDismiss) {
                        mIsEngineStarted = true;
                        mIsEngineStarting = false;
                        dismissSettingsTabPermanently();
                        return; // Stop poller
                    }
                } catch (Throwable t) {
                    AppLogger.logE(TAG, "Error parsing native engine state: " + t.getMessage(), t);
                }

                if (mIsAttached && mExpandedView != null && mExpandedView.getVisibility() == View.VISIBLE && !sSettingsDismissed) {
                    mMainHandler.postDelayed(this, 500);
                }
            }
        }, 500);
    }

    private void dismissSettingsTabPermanently() {
        sSettingsDismissed = true;
        if (mSidebarButtons != null && mSidebarButtons.length > 0 && mSidebarButtons[0] != null) {
            mSidebarButtons[0].setVisibility(View.GONE);
        }
        if (mContentSections != null && mContentSections.length > 0 && mContentSections[0] != null) {
            mContentSections[0].setVisibility(View.GONE);
        }
        selectTab(1); // Switch directly to Silent Aim tab
        AppLogger.log(TAG, "Settings setup layer dismissed permanently after successful shell start & game detection");
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
        /* The spinner is positioned on the LIVE value, never on the first
         * entry of the list. */
        spinner.setSelection(FeatureSettings.getIntValue(FeatureCatalog.AIM_SILENT_TARGET), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0) return;
                /* A Spinner fires this callback once while it is built. Only
                 * a REAL change is forwarded, otherwise opening the menu
                 * silently overwrote the engine with "Legit". */
                if (position == FeatureSettings.getIntValue(FeatureCatalog.AIM_SILENT_TARGET)) return;
                FeatureSettings.setIntValue(FeatureCatalog.AIM_SILENT_TARGET, position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        mRefreshers.put(FeatureCatalog.AIM_SILENT_TARGET, () -> {
            int selection = FeatureSettings.getIntValue(FeatureCatalog.AIM_SILENT_TARGET);
            if (spinner.getSelectedItemPosition() != selection) {
                spinner.setSelection(selection, false);
            }
        });

        layout.addView(spinner);
        sv.addView(layout);
        return sv;
    }

    // =========================================================================
    // ESP Section (Matching STORM_MODS features with clean UI)
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
        layout.addView(createKeyValueRow("Engine Mode: ", conn, COLOR_GREEN));
        layout.addView(createKeyValueRow("Target Game: ", pid > 0 ? (game + " [" + pid + "]") : "Waiting for Game", pid > 0 ? COLOR_GREEN : COLOR_TEXT_MUTED));
        layout.addView(createKeyValueRow("Daemon Stream: ", daemonRunning ? "Online (Connected)" : "Offline", daemonRunning ? COLOR_GREEN : COLOR_RED));
        layout.addView(createKeyValueRow("Memory Access: ", "process_vm_readv / pread64", COLOR_CYAN));
        layout.addView(createKeyValueRow("Central Offsets: ", "gameoffsets.h (Unified C++)", COLOR_CYAN));

        sv.addView(layout);
        return sv;
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

        /* The initial state is the LIVE engine state. It used to be a literal
         * ("true" / "false") written into the layout, which is exactly why
         * the menu showed features as enabled that the engine never turned
         * on. */
        final boolean[] state = { FeatureSettings.isEnabled(feature) };

        // Circle indicator matching screenshot (White thick circle border)
        final FrameLayout circleBox = new FrameLayout(mContext);
        int circleSize = dp(22);
        LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(circleSize, circleSize);
        circleLp.rightMargin = dp(10);
        circleBox.setLayoutParams(circleLp);

        final GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(state[0] ? Color.WHITE : Color.TRANSPARENT);
        circleBg.setStroke(dp(2.2f), Color.WHITE);
        circleBox.setBackground(circleBg);

        // Center check dot
        final View innerDot = new View(mContext);
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dp(10), dp(10));
        dotLp.gravity = Gravity.CENTER;
        innerDot.setLayoutParams(dotLp);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(COLOR_CARD_BG);
        innerDot.setBackground(dotBg);
        innerDot.setVisibility(state[0] ? View.VISIBLE : View.GONE);
        circleBox.addView(innerDot);

        TextView tv = new TextView(mContext);
        tv.setText(label);
        tv.setTextColor(COLOR_TEXT_WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);

        row.addView(circleBox);
        row.addView(tv);

        row.setOnClickListener(v -> {
            FeatureSettings.setEnabled(feature, !state[0]);
            /* The store can refuse the change (the server may have switched
             * the whole group off), so the shown state is read back from it
             * instead of assuming the toggle worked. */
            state[0] = FeatureSettings.isEnabled(feature);
            circleBg.setColor(state[0] ? Color.WHITE : Color.TRANSPARENT);
            innerDot.setVisibility(state[0] ? View.VISIBLE : View.GONE);
            AppLogger.log(TAG, "Feature toggled: " + feature.key() + " -> " + state[0]);
        });

        mRefreshers.put(feature, () -> {
            boolean live = FeatureSettings.isEnabled(feature);
            state[0] = live;
            circleBg.setColor(live ? Color.WHITE : Color.TRANSPARENT);
            innerDot.setVisibility(live ? View.VISIBLE : View.GONE);
        });

        return row;
    }

    // Exact Screenshot 1 Slider Row ("Silent Fov: 120" with cyan value)
    private View createAccentSliderRow(String label, final FeatureCatalog feature) {
        final int min = feature.min();
        final int max = feature.max();
        /* The live value of the engine, never a hard coded default. */
        final int liveValue = FeatureSettings.getIntValue(feature);

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
        valTv.setText(String.valueOf(liveValue));
        valTv.setTextColor(COLOR_CYAN_LIGHT);
        valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        valTv.setTypeface(Typeface.DEFAULT_BOLD);

        textRow.addView(labelTv);
        textRow.addView(valTv);
        container.addView(textRow);

        // Thin Sleek SeekBar with white thumb & cyan progress
        SeekBar sb = new SeekBar(mContext);
        sb.setMax(max - min);
        sb.setProgress(liveValue - min);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sb.setProgressTintList(android.content.res.ColorStateList.valueOf(COLOR_CYAN_LIGHT));
            sb.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        }

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int actual = min + progress;
                valTv.setText(String.valueOf(actual));
                /* setProgress() during the construction fires this callback
                 * with fromUser == false. Only a real drag is forwarded, so
                 * opening the menu can no longer overwrite the engine. */
                if (!fromUser) return;
                FeatureSettings.setIntValue(feature, actual);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        container.addView(sb);

        mRefreshers.put(feature, () -> {
            int value = FeatureSettings.getIntValue(feature);
            valTv.setText(String.valueOf(value));
            if (sb.getProgress() != value - min) {
                sb.setProgress(value - min);
            }
        });

        return container;
    }

    public void expandMenu() {
        if (mCollapsedView != null) mCollapsedView.setVisibility(View.GONE);
        if (mExpandedView != null) mExpandedView.setVisibility(View.VISIBLE);
        try {
            mWindowManager.updateViewLayout(mRootFrame, mParams);
        } catch (Throwable ignored) {}
    }

    public void collapseToLogo() {
        if (mExpandedView != null) mExpandedView.setVisibility(View.GONE);
        if (mCollapsedView != null) mCollapsedView.setVisibility(View.VISIBLE);
        try {
            mWindowManager.updateViewLayout(mRootFrame, mParams);
        } catch (Throwable ignored) {}
    }

    private View.OnTouchListener createLogoTouchListener() {
        return new View.OnTouchListener() {
            private float initialTouchX, initialTouchY;
            private int initialX, initialY;

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = mParams.x;
                        initialY = mParams.y;
                        initialTouchX = motionEvent.getRawX();
                        initialTouchY = motionEvent.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        int rawX = (int) (motionEvent.getRawX() - initialTouchX);
                        int rawY = (int) (motionEvent.getRawY() - initialTouchY);
                        if (Math.abs(rawX) < 10 && Math.abs(rawY) < 10) {
                            expandMenu();
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        mParams.x = initialX + ((int) (motionEvent.getRawX() - initialTouchX));
                        mParams.y = initialY + ((int) (motionEvent.getRawY() - initialTouchY));
                        try {
                            mWindowManager.updateViewLayout(mRootFrame, mParams);
                        } catch (Throwable ignored) {}
                        return true;

                    default:
                        return false;
                }
            }
        };
    }

    private View.OnTouchListener createHeaderDragListener() {
        return new View.OnTouchListener() {
            private float initialTouchX, initialTouchY;
            private int initialX, initialY;

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = mParams.x;
                        initialY = mParams.y;
                        initialTouchX = motionEvent.getRawX();
                        initialTouchY = motionEvent.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        mParams.x = initialX + ((int) (motionEvent.getRawX() - initialTouchX));
                        mParams.y = initialY + ((int) (motionEvent.getRawY() - initialTouchY));
                        try {
                            mWindowManager.updateViewLayout(mRootFrame, mParams);
                        } catch (Throwable ignored) {}
                        return true;

                    default:
                        return false;
                }
            }
        };
    }
}
