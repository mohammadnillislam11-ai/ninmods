package com.ninmods.main.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
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

import com.ninmods.main.R;
import com.ninmods.main.core.AppLogger;
import com.ninmods.main.core.NativeBridge;

/**
 * Authentic Floating Window & Mod Menu Architecture (Android-ModMenu-SemiJni-main + OLD).
 * Features:
 *   1. Zero Border around main menu card (Pure dark translucent background).
 *   2. Compact Sleek Dimensions (275dp width x 315dp height).
 *   3. OLD Authentic Eagle Banner Logo centered in Header Bar (Zero driver badges in header).
 *   4. OLD Authentic NINMODS Watermark Logo centered behind Scrollable Body.
 *   5. Pure Transparent Floating Logo (Zero circle background, exact OLD design).
 *   6. Live Real-time Engine Telemetry in SYSTEM tab (Unified monospace slate styling, zero rainbow colors).
 *   7. Zero Screen Freeze: Independent logo/header dragging, non-blocking asynchronous telemetry bus.
 */
public class FloatingMenuView {

    private static final String TAG = "FloatingMenuView";

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final float mDensity;

    private WindowManager.LayoutParams mParams;
    private FrameLayout mRootFrame;
    private RelativeLayout mCollapsedView;
    private LinearLayout mExpandedView;
    private ImageView mStartImage;

    // Navigation Tabs and Section Containers
    private TextView[] mTabButtons;
    private LinearLayout[] mTabSections;
    private TextView mEngineStatusView;

    private boolean mIsAttached = false;

    private final Runnable mLiveTelemetryRunnable = new Runnable() {
        @Override
        public void run() {
            if (mEngineStatusView != null && isSystemSectionVisible()) {
                new Thread(() -> {
                    String liveData = NativeBridge.getEngineLiveStatus();
                    mMainHandler.post(() -> {
                        if (mEngineStatusView != null) {
                            mEngineStatusView.setText(liveData != null ? liveData : "ENGINE INITIALIZING...");
                        }
                    });
                }).start();
            }
            mMainHandler.postDelayed(this, 1000);
        }
    };

    public FloatingMenuView(Context context, WindowManager windowManager) {
        this.mContext = context;
        this.mWindowManager = windowManager;
        this.mDensity = context.getResources().getDisplayMetrics().density;
        initViews();
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

    /**
     * Exact WindowManager setup (Android-ModMenu-SemiJni-main)
     */
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
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        mParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                flags,
                android.graphics.PixelFormat.TRANSLUCENT
        );
        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = dp(30);
        mParams.y = dp(100);

        // 1. Create Collapsed Logo Layer (Pure Transparent Background)
        mCollapsedView = createCollapsedLogoView();

        // 2. Create Compact Expanded Menu Layer (Header Banner + Tabs + Body Watermark)
        mExpandedView = createExpandedMenuView();

        mRootFrame.addView(mCollapsedView);
        mRootFrame.addView(mExpandedView);

        // Logo touch listener for dragging and tap-to-expand
        View.OnTouchListener logoTouch = createLogoTouchListener();
        mCollapsedView.setOnTouchListener(logoTouch);
        if (mStartImage != null) {
            mStartImage.setOnTouchListener(logoTouch);
        }

        // Initially show collapsed logo, hide expanded menu
        mCollapsedView.setVisibility(View.VISIBLE);
        mExpandedView.setVisibility(View.GONE);
    }

    /**
     * Floating Logo Touch Engine: Moves logo across screen, taps expand menu.
     */
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
                        if (mCollapsedView != null) mCollapsedView.setAlpha(1.0f);
                        if (Math.abs(rawX) < 10 && Math.abs(rawY) < 10 && isViewCollapsed()) {
                            expandMenu();
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (mCollapsedView != null) mCollapsedView.setAlpha(0.55f);
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

    /**
     * Header Bar Drag Listener: Allows sliding the menu window smoothly without interfering with body controls.
     */
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

                    case MotionEvent.ACTION_UP:
                        if (mExpandedView != null) mExpandedView.setAlpha(1.0f);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (mExpandedView != null) mExpandedView.setAlpha(0.70f);
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

    private boolean isViewCollapsed() {
        return mCollapsedView == null || mCollapsedView.getVisibility() == View.VISIBLE;
    }

    private boolean isSystemSectionVisible() {
        return mTabSections != null && mTabSections.length > 3 &&
                mTabSections[3] != null && mTabSections[3].getVisibility() == View.VISIBLE &&
                mExpandedView != null && mExpandedView.getVisibility() == View.VISIBLE;
    }

    /**
     * Collapsed Floating Logo (Pure Transparent Background as in OLD project, zero circle)
     */
    private RelativeLayout createCollapsedLogoView() {
        RelativeLayout collapsed = new RelativeLayout(mContext);
        int logoSize = dp(56);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(logoSize, logoSize);
        collapsed.setLayoutParams(lp);

        // ZERO CIRCULAR BACKGROUND: Transparent background, ONLY the logo itself is shown
        collapsed.setBackgroundColor(Color.TRANSPARENT);
        collapsed.setPadding(0, 0, 0, 0);

        mStartImage = new ImageView(mContext);
        mStartImage.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mStartImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            mStartImage.setImageResource(R.drawable.nin_logo);
        } catch (Throwable t) {
            mStartImage.setImageResource(android.R.drawable.ic_dialog_info);
        }
        collapsed.addView(mStartImage);

        return collapsed;
    }

    /**
     * Compact Expanded Menu (Zero Border, Eagle Banner Header, Body Watermark)
     */
    private LinearLayout createExpandedMenuView() {
        LinearLayout expanded = new LinearLayout(mContext);
        expanded.setOrientation(LinearLayout.VERTICAL);

        // Compact proportions: 275dp width x 315dp height
        int menuWidth = Math.min(dp(275), (int) (mContext.getResources().getDisplayMetrics().widthPixels * 0.88f));
        int menuHeight = Math.min(dp(315), (int) (mContext.getResources().getDisplayMetrics().heightPixels * 0.75f));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(menuWidth, menuHeight);
        expanded.setLayoutParams(cardLp);

        // Zero Border: Pure deep dark translucent background with smooth rounded corners
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(dp(10));
        cardBg.setColor(Color.parseColor("#F20A0E18"));
        expanded.setBackground(cardBg);

        // 1. Draggable Header Bar with Eagle Banner from OLD
        View headerBar = createHeaderBar();
        expanded.addView(headerBar);

        // 2. Compact Navigation TabBar (VISUALS, AIMBOT, COMBAT, SYSTEM)
        View navTabBar = createNavTabBar();
        expanded.addView(navTabBar);

        // 3. Body Container with Authentic NINMODS Watermark Logo in Background
        FrameLayout bodyFrame = new FrameLayout(mContext);
        bodyFrame.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        // Background Watermark Logo (like in OLD MenuRender.h)
        ImageView watermarkView = new ImageView(mContext);
        FrameLayout.LayoutParams wmParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        wmParams.gravity = Gravity.CENTER;
        watermarkView.setLayoutParams(wmParams);
        watermarkView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        watermarkView.setPadding(dp(18), dp(12), dp(18), dp(12));
        watermarkView.setAlpha(0.12f); // Subtle authentic watermark transparency
        try {
            watermarkView.setImageResource(R.drawable.nin_logo);
        } catch (Throwable ignored) {}
        bodyFrame.addView(watermarkView);

        // Foreground Scrollable Content
        ScrollView scrollView = new ScrollView(mContext);
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout contentContainer = new LinearLayout(mContext);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(dp(10), dp(4), dp(10), dp(10));

        mTabSections = new LinearLayout[4];
        mTabSections[0] = createVisualsSection();
        mTabSections[1] = createAimbotSection();
        mTabSections[2] = createCombatSection();
        mTabSections[3] = createSystemSection();

        for (int i = 0; i < 4; i++) {
            contentContainer.addView(mTabSections[i]);
            mTabSections[i].setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        }

        scrollView.addView(contentContainer);
        bodyFrame.addView(scrollView);
        expanded.addView(bodyFrame);

        return expanded;
    }

    /**
     * Header Bar: OLD Eagle Banner Logo and Close [X] Button (Zero fake driver badges).
     * Attached with createHeaderDragListener() so dragging the header slides the menu window.
     */
    private View createHeaderBar() {
        RelativeLayout header = new RelativeLayout(mContext);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
        ));
        header.setPadding(dp(8), dp(4), dp(8), dp(4));

        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setShape(GradientDrawable.RECTANGLE);
        headerBg.setColor(Color.parseColor("#FF0E131F"));
        headerBg.setCornerRadii(new float[]{dp(10), dp(10), dp(10), dp(10), 0, 0, 0, 0});
        header.setBackground(headerBg);

        // Centered Eagle Banner Logo from OLD
        ImageView bannerView = new ImageView(mContext);
        RelativeLayout.LayoutParams bannerLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(34)
        );
        bannerLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        bannerLp.setMargins(dp(10), 0, dp(34), 0);
        bannerView.setLayoutParams(bannerLp);
        bannerView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            bannerView.setImageResource(R.drawable.nin_banner);
        } catch (Throwable t) {
            bannerView.setImageResource(R.drawable.nin_logo);
        }
        header.addView(bannerView);

        // Pinned Close [X] Button on Top-Right Corner
        TextView closeBtn = new TextView(mContext);
        closeBtn.setText("X");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(Gravity.CENTER);

        int btnSize = dp(24);
        RelativeLayout.LayoutParams closeLp = new RelativeLayout.LayoutParams(btnSize, btnSize);
        closeLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        closeLp.addRule(RelativeLayout.CENTER_VERTICAL);
        closeBtn.setLayoutParams(closeLp);

        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.RECTANGLE);
        closeBg.setCornerRadius(dp(5));
        closeBg.setColor(Color.parseColor("#C62828"));
        closeBtn.setBackground(closeBg);

        closeBtn.setOnClickListener(v -> collapseToLogo());
        header.addView(closeBtn);

        // Attach drag listener to header bar for smooth window positioning
        header.setOnTouchListener(createHeaderDragListener());
        return header;
    }

    /**
     * Compact Navigation Tabs: VISUALS, AIMBOT, COMBAT, SYSTEM
     */
    private View createNavTabBar() {
        LinearLayout tabBar = new LinearLayout(mContext);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(30)
        ));
        tabBar.setBackgroundColor(Color.parseColor("#FF0B0F18"));

        mTabButtons = new TextView[4];
        String[] titles = new String[]{"VISUALS", "AIMBOT", "COMBAT", "SYSTEM"};

        for (int i = 0; i < 4; i++) {
            final int index = i;
            TextView tab = new TextView(mContext);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            tab.setLayoutParams(lp);
            tab.setText(titles[i]);
            tab.setGravity(Gravity.CENTER);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            tab.setTypeface(Typeface.DEFAULT_BOLD);

            tab.setOnClickListener(v -> selectTab(index));
            mTabButtons[i] = tab;
            tabBar.addView(tab);
        }

        updateTabStyles(0);
        return tabBar;
    }

    private void selectTab(int index) {
        for (int i = 0; i < 4; i++) {
            mTabSections[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        updateTabStyles(index);
    }

    private void updateTabStyles(int activeIndex) {
        for (int i = 0; i < 4; i++) {
            if (i == activeIndex) {
                mTabButtons[i].setTextColor(Color.parseColor("#00E5FF"));
                GradientDrawable activeBg = new GradientDrawable();
                activeBg.setShape(GradientDrawable.RECTANGLE);
                activeBg.setColor(Color.parseColor("#FF131C2D"));
                mTabButtons[i].setBackground(activeBg);
            } else {
                mTabButtons[i].setTextColor(Color.parseColor("#8A98A8"));
                mTabButtons[i].setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    // =========================================================================
    // TAB SECTIONS BUILDERS
    // =========================================================================

    private LinearLayout createVisualsSection() {
        LinearLayout section = new LinearLayout(mContext);
        section.setOrientation(LinearLayout.VERTICAL);

        section.addView(createSwitch("ESP MASTER SWITCH", 1));
        section.addView(createSwitch("ESP BOX (2D)", 2));
        section.addView(createSwitch("ESP SKELETON (BONES)", 3));
        section.addView(createSwitch("ESP LINE (TRACER)", 4));
        section.addView(createSwitch("ESP HEALTH BAR", 5));
        section.addView(createSwitch("ESP PLAYER NAME", 6));
        section.addView(createSwitch("ESP DISTANCE (METERS)", 7));
        section.addView(createSwitch("360 THREAT RADAR", 9));
        section.addView(createSwitch("LIVE THREAT COUNTER HUD", 8));

        return section;
    }

    private LinearLayout createAimbotSection() {
        LinearLayout section = new LinearLayout(mContext);
        section.setOrientation(LinearLayout.VERTICAL);

        section.addView(createSwitch("AIMBOT AUTO LOCK", 20));
        section.addView(createSwitch("AIMBOT STICKY TARGET", 21));
        section.addView(createSwitch("SILENT AIM (MAGIC BULLET)", 22));
        section.addView(createSwitch("AIM MAGNET V2", 23));
        section.addView(createSwitch("IGNORE KNOCKED PLAYERS", 24));
        section.addView(createSwitch("IGNORE BOTS / AI", 25));
        section.addView(createSwitch("HEADSHOT PRIORITY", 26));

        section.addView(createSeekBar("AIM FOV ANGLE", 100, 10, 180, 60, "deg"));
        section.addView(createSeekBar("MAX TARGET DISTANCE", 101, 10, 350, 150, "M"));

        return section;
    }

    private LinearLayout createCombatSection() {
        LinearLayout section = new LinearLayout(mContext);
        section.setOrientation(LinearLayout.VERTICAL);

        section.addView(createSwitch("NO RECOIL", 40));
        section.addView(createSwitch("SPEED HACK V2", 41));
        section.addView(createSwitch("PLAYER TO ME (TELEPORT)", 42));
        section.addView(createSwitch("UNDER CAMERA", 43));
        section.addView(createSeekBar("GAME SPEED MULTIPLIER", 102, 1, 5, 1, "x"));

        return section;
    }

    /**
     * SYSTEM Tab: Live Engine Telemetry Monitor (Clean, unified typography, zero distracting rainbow colors)
     */
    private LinearLayout createSystemSection() {
        LinearLayout section = new LinearLayout(mContext);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout telemetryCard = new LinearLayout(mContext);
        telemetryCard.setOrientation(LinearLayout.VERTICAL);
        telemetryCard.setPadding(dp(8), dp(8), dp(8), dp(8));
        telemetryCard.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Dark clean card background matching overall theme
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(dp(6));
        cardBg.setColor(Color.parseColor("#FF0E121B"));
        telemetryCard.setBackground(cardBg);

        // Unified monospace telemetry view with single clean slate color (#A0B0C0)
        mEngineStatusView = new TextView(mContext);
        mEngineStatusView.setTextColor(Color.parseColor("#A0B0C0"));
        mEngineStatusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        mEngineStatusView.setTypeface(Typeface.MONOSPACE);
        mEngineStatusView.setLineSpacing(dp(2), 1.0f);
        mEngineStatusView.setText("CONNECTING TELEMETRY BUS...");
        telemetryCard.addView(mEngineStatusView);

        section.addView(telemetryCard);

        // Start live telemetry polling loop
        mMainHandler.post(mLiveTelemetryRunnable);

        return section;
    }

    // =========================================================================
    // SWITCH & SEEKBAR CREATION (Compact & Responsive)
    // =========================================================================

    private View createSwitch(final String featName, final int featId) {
        Switch switchView = new Switch(mContext);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(1), 0, dp(1));
        switchView.setLayoutParams(lp);

        switchView.setText(featName);
        switchView.setTextColor(Color.WHITE);
        switchView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        switchView.setTypeface(Typeface.DEFAULT_BOLD);
        switchView.setPadding(dp(2), dp(4), dp(2), dp(4));

        ColorStateList buttonStates = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{
                        Color.parseColor("#00E5FF"), // ON: Neon Cyan
                        Color.parseColor("#78909C")  // OFF: Slate Grey
                }
        );

        ColorStateList trackStates = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{
                        Color.parseColor("#4D00E5FF"), // ON track: Translucent Cyan
                        Color.parseColor("#263238")    // OFF track: Dark Slate
                }
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            switchView.setThumbTintList(buttonStates);
            switchView.setTrackTintList(trackStates);
        }

        boolean initialState = NativeBridge.getFeatureState(featId);
        switchView.setChecked(initialState);

        switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            NativeBridge.setFeatureState(featId, isChecked);
            AppLogger.log(TAG, "Feature toggled: " + featName + " (ID: " + featId + ") -> " + isChecked);
        });

        return switchView;
    }

    private View createSeekBar(final String featName, final int featId, final int min, final int max, int defaultVal, final String unit) {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowLp.setMargins(0, dp(2), 0, dp(2));
        row.setLayoutParams(rowLp);

        final TextView label = new TextView(mContext);
        label.setText(featName + ": " + defaultVal + " " + unit);
        label.setTextColor(Color.parseColor("#B0BEC5"));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(dp(2), dp(2), dp(2), 0);
        row.addView(label);

        SeekBar seekBar = new SeekBar(mContext);
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        seekBar.setMax(max - min);
        seekBar.setProgress(defaultVal - min);

        ColorStateList cyanTint = ColorStateList.valueOf(Color.parseColor("#00E5FF"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.setProgressTintList(cyanTint);
            seekBar.setThumbTintList(cyanTint);
        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int actualVal = min + progress;
                label.setText(featName + ": " + actualVal + " " + unit);
                if (fromUser) {
                    NativeBridge.setFeatureFloat(featId, (float) actualVal);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        row.addView(seekBar);
        return row;
    }

    public void expandMenu() {
        mMainHandler.post(() -> {
            if (mCollapsedView != null) mCollapsedView.setVisibility(View.GONE);
            if (mExpandedView != null) mExpandedView.setVisibility(View.VISIBLE);
            try {
                mWindowManager.updateViewLayout(mRootFrame, mParams);
            } catch (Throwable ignored) {}
            AppLogger.log(TAG, "Floating tactical menu card expanded");
        });
    }

    public void collapseToLogo() {
        mMainHandler.post(() -> {
            if (mExpandedView != null) mExpandedView.setVisibility(View.GONE);
            if (mCollapsedView != null) mCollapsedView.setVisibility(View.VISIBLE);
            try {
                mWindowManager.updateViewLayout(mRootFrame, mParams);
            } catch (Throwable ignored) {}
            AppLogger.log(TAG, "Floating tactical menu card collapsed back to logo");
        });
    }
}
