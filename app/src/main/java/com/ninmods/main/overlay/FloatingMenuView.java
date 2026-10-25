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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.ninmods.main.R;
import com.ninmods.main.auth.AuthApiController;
import com.ninmods.main.core.AppLogger;
import com.ninmods.main.core.NativeBridge;

/**
 * 100% Exact System Architecture of Android-ModMenu-SemiJni-main.
 * Features:
 *   1. Identical Floating WindowManager frame & onTouchListener with alpha dragging feedback.
 *   2. Identical Logo sliding and Menu header sliding using the exact LGL engine.
 *   3. Custom NINMODS Cyber Dark UI design with 4 Navigation Tabs (VISUALS, AIMBOT, COMBAT, SYSTEM).
 *   4. Direct JNI synchronization with libninmods.so & Linux Kernel Module Driver (/dev/BYNb).
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
    private TextView mDriverStatusText;

    private boolean mIsAttached = false;

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
     * Exact initFloating structure from Android-ModMenu-SemiJni-main (Menu.java)
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
        mParams.x = dp(40);
        mParams.y = dp(120);

        // 1. Create Collapsed Logo Layer (mCollapsed)
        mCollapsedView = createCollapsedLogoView();

        // 2. Create Expanded Menu Layer (mExpanded)
        mExpandedView = createExpandedMenuView();

        mRootFrame.addView(mCollapsedView);
        mRootFrame.addView(mExpandedView);

        // 3. Attach Exact onTouchListener to RootFrame, StartImage, and Header
        View.OnTouchListener touchListener = onTouchListener();
        mRootFrame.setOnTouchListener(touchListener);
        mCollapsedView.setOnTouchListener(touchListener);
        if (mStartImage != null) {
            mStartImage.setOnTouchListener(touchListener);
        }

        // Initially show collapsed logo, hide expanded menu
        mCollapsedView.setVisibility(View.VISIBLE);
        mExpandedView.setVisibility(View.GONE);
    }

    /**
     * Exact onTouchListener code from Android-ModMenu-SemiJni-main (Menu.java:136-177)
     */
    private View.OnTouchListener onTouchListener() {
        return new View.OnTouchListener() {
            final View collapsedView = mCollapsedView;
            final View expandedView = mExpandedView;
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
                        if (mExpandedView != null) mExpandedView.setAlpha(1f);
                        if (mCollapsedView != null) mCollapsedView.setAlpha(1f);
                        if (Math.abs(rawX) < 10 && Math.abs(rawY) < 10 && isViewCollapsed()) {
                            try {
                                collapsedView.setVisibility(View.GONE);
                                expandedView.setVisibility(View.VISIBLE);
                                updateDriverStatusBadge();
                            } catch (NullPointerException ignored) {}
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (mExpandedView != null) mExpandedView.setAlpha(0.5f);
                        if (mCollapsedView != null) mCollapsedView.setAlpha(0.5f);
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

    /**
     * Collapsed Floating Logo (RelativeLayout containing ImageView startimage)
     */
    private RelativeLayout createCollapsedLogoView() {
        RelativeLayout collapsed = new RelativeLayout(mContext);
        int logoSize = dp(64);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(logoSize, logoSize);
        collapsed.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#EE0B0E17"));
        bg.setStroke(dp(2.0f), Color.parseColor("#00E5FF"));
        collapsed.setBackground(bg);
        collapsed.setPadding(dp(8), dp(8), dp(8), dp(8));

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
     * Expanded Tactical Menu Card (Header + Tabs + ScrollView)
     */
    private LinearLayout createExpandedMenuView() {
        LinearLayout expanded = new LinearLayout(mContext);
        expanded.setOrientation(LinearLayout.VERTICAL);

        int menuWidth = Math.min(dp(330), (int) (mContext.getResources().getDisplayMetrics().widthPixels * 0.90f));
        int menuHeight = Math.min(dp(410), (int) (mContext.getResources().getDisplayMetrics().heightPixels * 0.85f));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(menuWidth, menuHeight);
        expanded.setLayoutParams(cardLp);

        // Cyber Dark Translucent Background with Neon Cyan Border
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(dp(12));
        cardBg.setColor(Color.parseColor("#F2090C14"));
        cardBg.setStroke(dp(1.5f), Color.parseColor("#00E5FF"));
        expanded.setBackground(cardBg);

        // 1. Draggable Header Bar (using same onTouchListener for sliding)
        View headerBar = createHeaderBar();
        expanded.addView(headerBar);

        // 2. Navigation TabBar (VISUALS, AIMBOT, COMBAT, SYSTEM)
        View navTabBar = createNavTabBar();
        expanded.addView(navTabBar);

        // 3. Scrollable Content Body (patches LinearLayout)
        ScrollView scrollView = new ScrollView(mContext);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout contentContainer = new LinearLayout(mContext);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(dp(12), dp(8), dp(12), dp(14));

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
        expanded.addView(scrollView);

        return expanded;
    }

    /**
     * Header Bar: Pinned Logo, Live Driver Badge, and Close [X] Button.
     * Attached with onTouchListener so dragging header slides the whole menu window!
     */
    private View createHeaderBar() {
        LinearLayout header = new LinearLayout(mContext);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(8), dp(8));
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setShape(GradientDrawable.RECTANGLE);
        headerBg.setColor(Color.parseColor("#FF101522"));
        headerBg.setCornerRadii(new float[]{dp(12), dp(12), dp(12), dp(12), 0, 0, 0, 0});
        header.setBackground(headerBg);

        // Logo
        ImageView iconView = new ImageView(mContext);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        try {
            iconView.setImageResource(R.drawable.nin_logo);
        } catch (Throwable ignored) {}
        header.addView(iconView);

        // Title
        TextView titleText = new TextView(mContext);
        titleText.setText(" NINMODS");
        titleText.setTextColor(Color.parseColor("#00E5FF"));
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(titleText);

        // Live Driver Status Badge
        mDriverStatusText = new TextView(mContext);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        mDriverStatusText.setLayoutParams(statusLp);
        mDriverStatusText.setGravity(Gravity.CENTER);
        mDriverStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mDriverStatusText.setTypeface(Typeface.DEFAULT_BOLD);
        updateDriverStatusBadge();
        header.addView(mDriverStatusText);

        // Close / Minimize Button
        TextView closeBtn = new TextView(mContext);
        closeBtn.setText("X");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(Gravity.CENTER);

        int btnSize = dp(28);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(btnSize, btnSize);
        closeBtn.setLayoutParams(closeLp);

        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.RECTANGLE);
        closeBg.setCornerRadius(dp(6));
        closeBg.setColor(Color.parseColor("#D32F2F"));
        closeBtn.setBackground(closeBg);

        closeBtn.setOnClickListener(v -> collapseToLogo());
        header.addView(closeBtn);

        // Attach exact onTouchListener to header so menu slides smoothly!
        header.setOnTouchListener(onTouchListener());
        return header;
    }

    /**
     * 4 Tactical Navigation Tabs: VISUALS, AIMBOT, COMBAT, SYSTEM
     */
    private View createNavTabBar() {
        LinearLayout tabBar = new LinearLayout(mContext);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(38)
        ));
        tabBar.setBackgroundColor(Color.parseColor("#FF0B0F19"));

        mTabButtons = new TextView[4];
        String[] titles = new String[]{"VISUALS", "AIMBOT", "COMBAT", "SYSTEM"};

        for (int i = 0; i < 4; i++) {
            final int index = i;
            TextView tab = new TextView(mContext);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            tab.setLayoutParams(lp);
            tab.setText(titles[i]);
            tab.setGravity(Gravity.CENTER);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
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
                activeBg.setStroke(dp(1.2f), Color.parseColor("#00E5FF"));
                mTabButtons[i].setBackground(activeBg);
            } else {
                mTabButtons[i].setTextColor(Color.parseColor("#8A98A8"));
                mTabButtons[i].setBackgroundColor(Color.TRANSLUCENT);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAB SECTIONS BUILDERS
    // ─────────────────────────────────────────────────────────────────────────

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

    private LinearLayout createSystemSection() {
        LinearLayout section = new LinearLayout(mContext);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout infoCard = new LinearLayout(mContext);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(dp(10), dp(10), dp(10), dp(10));
        infoCard.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(dp(8));
        cardBg.setColor(Color.parseColor("#FF101622"));
        cardBg.setStroke(dp(1.0f), Color.parseColor("#1E2D44"));
        infoCard.setBackground(cardBg);

        TextView hwidText = new TextView(mContext);
        hwidText.setText("DEVICE HWID: " + AuthApiController.getHwid(mContext));
        hwidText.setTextColor(Color.parseColor("#8A98A8"));
        hwidText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        infoCard.addView(hwidText);

        TextView driverInfo = new TextView(mContext);
        driverInfo.setText("DRIVER NODE: /dev/BYNb (Kernel Ring 0)");
        driverInfo.setTextColor(Color.parseColor("#00E5FF"));
        driverInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        driverInfo.setTypeface(Typeface.DEFAULT_BOLD);
        driverInfo.setPadding(0, dp(4), 0, dp(4));
        infoCard.addView(driverInfo);

        section.addView(infoCard);

        Button openLoaderBtn = new Button(mContext);
        openLoaderBtn.setText("OPEN DRIVER LOADER");
        openLoaderBtn.setTextColor(Color.WHITE);
        openLoaderBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        openLoaderBtn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setCornerRadius(dp(8));
        btnBg.setColor(Color.parseColor("#FF0088CC"));
        openLoaderBtn.setBackground(btnBg);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
        );
        btnLp.setMargins(0, dp(12), 0, 0);
        openLoaderBtn.setLayoutParams(btnLp);
        openLoaderBtn.setOnClickListener(v -> NativeBridge.openLoader(mContext));
        section.addView(openLoaderBtn);

        return section;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXACT SWITCH & SEEKBAR CREATION (Views.java style from Android-ModMenu-SemiJni-main)
    // ─────────────────────────────────────────────────────────────────────────

    private View createSwitch(final String featName, final int featId) {
        Switch switchView = new Switch(mContext);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(3), 0, dp(3));
        switchView.setLayoutParams(lp);

        switchView.setText(featName);
        switchView.setTextColor(Color.WHITE);
        switchView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        switchView.setTypeface(Typeface.DEFAULT_BOLD);
        switchView.setPadding(dp(4), dp(6), dp(4), dp(6));

        // High-contrast Cyber Neon Cyan and Slate Grey ColorStateList
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
        rowLp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(rowLp);

        final TextView label = new TextView(mContext);
        label.setText(featName + ": " + defaultVal + " " + unit);
        label.setTextColor(Color.parseColor("#B0BEC5"));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(dp(4), dp(2), dp(4), 0);
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
            mCollapsedView.setVisibility(View.GONE);
            mExpandedView.setVisibility(View.VISIBLE);
            updateDriverStatusBadge();
            AppLogger.log(TAG, "Floating tactical menu card expanded");
        });
    }

    public void collapseToLogo() {
        mMainHandler.post(() -> {
            mExpandedView.setVisibility(View.GONE);
            mCollapsedView.setVisibility(View.VISIBLE);
            AppLogger.log(TAG, "Floating tactical menu card collapsed back to logo");
        });
    }

    public void updateDriverStatusBadge() {
        if (mDriverStatusText == null) return;
        boolean connected = NativeBridge.isKernelDriverConnected();
        if (connected) {
            mDriverStatusText.setText("[ONLINE] KERNEL (/dev/BYNb)");
            mDriverStatusText.setTextColor(Color.parseColor("#00E676")); // Neon Green
        } else {
            mDriverStatusText.setText("[OFFLINE] KERNEL");
            mDriverStatusText.setTextColor(Color.parseColor("#FF5252")); // Soft Red
        }
    }
}
