package com.ninmods.main.overlay;

import com.ninmods.main.graphics.ESPView;
import com.ninmods.main.graphics.GLES3JNIView;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
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
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ninmods.main.core.AppLogger;

public class Floater {

    public static native void Functions();
    public static native void ChangesID(int ID, int Value);
    public static native void ftKZXivSr(Context context);
    public static native void ftKwCvSr();
    public static native void PxbftKZXivSr(ESPView espView, Canvas canvas, int width, int height);

    public static int SCREEN_WIDTH = 0;
    public static int SCREEN_HEIGHT = 0;

    public static int Width() { return SCREEN_WIDTH; }
    public static int Height() { return SCREEN_HEIGHT; }

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    public static volatile Floater activeFloater = null;

    // Periodic inquiry disabled: once floating menu is activated with verified license, it remains active without periodic 1-minute cutoffs
    private static final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            // No recurring cutoff inquiry
        }
    };

    private Context context;

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowManagerParams;
    GLES3JNIView drawView;
    WindowManager.LayoutParams windowManagerDrawViewParams;

    private FrameLayout rootFrame;
    private RelativeLayout rootRelative;
    private FrameLayout collapsedRelative;
    private FrameLayout menuLayout;
    private ImageView logoImagem;
    private ImageView bodyWatermark;
    private int menuWidth;
    private int menuHeight;

    private final Map<String, List<View>> categoryViews = new LinkedHashMap<>();
    private final List<ImageView> railTabButtons = new ArrayList<>();
    private int activeRailIndex = 0;
    private String currentCategory = "Aim";

    private LinearLayout iconTabsContainer;
    private TextView page1Title;
    private TextView page2Title;
    private LinearLayout page1Content;
    private LinearLayout page2Content;

    private int savedMenuX = -1;
    private int savedMenuY = -1;
    private boolean isLogoDragged = false;

    private void updateScreenDimensions() {
        try {
            if (windowManager != null) {
                DisplayMetrics dm = new DisplayMetrics();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    windowManager.getDefaultDisplay().getRealMetrics(dm);
                } else {
                    windowManager.getDefaultDisplay().getMetrics(dm);
                }
                if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                    SCREEN_WIDTH = dm.widthPixels;
                    SCREEN_HEIGHT = dm.heightPixels;
                }
            }
        } catch (Exception ignored) {}
    }

    // Strict Singleton & Lifecycle Control
    public static synchronized void start(Context ctx) {
        Context targetCtx = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;

        // Check active online verified license session
        String activeKey = com.ninmods.main.auth.AuthManager.getActiveLicenseKey(targetCtx);
        String sessionToken = com.ninmods.main.auth.AuthManager.getSessionToken(targetCtx);
        if (activeKey == null || activeKey.trim().isEmpty() || sessionToken == null || sessionToken.trim().isEmpty()) {
            android.widget.Toast.makeText(targetCtx, "Active license key required! Please activate key online.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        if (!com.ninmods.main.auth.SecurityVault.isSessionValid()) {
            String hwid = com.ninmods.main.auth.AuthApiController.getHwid(targetCtx);
            long remSec = com.ninmods.main.auth.AuthManager.getRemainingSeconds(targetCtx);
            com.ninmods.main.auth.SecurityVault.setNativeSession(sessionToken, hwid, remSec);
        }

        if (!com.ninmods.main.auth.SecurityVault.isSessionValid()) {
            android.widget.Toast.makeText(targetCtx, "Tamper detected / Invalid server session. Please activate key online.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        if (activeFloater != null && activeFloater.isAttached()) {
            activeFloater.closeMenu();
            return;
        }
        if (activeFloater != null) {
            activeFloater.destroyOverlay();
        }
        new Floater(targetCtx);
    }

    public static synchronized void stop() {
        if (activeFloater != null) {
            activeFloater.destroyOverlay();
            activeFloater = null;
        }
    }

    public static boolean isRunning() {
        return activeFloater != null && activeFloater.isAttached();
    }

    public boolean isAttached() {
        return rootFrame != null && rootFrame.isAttachedToWindow();
    }

    public synchronized void destroyOverlay() {
        mainHandler.removeCallbacks(heartbeatRunnable);
        try {
            if (windowManager != null) {
                if (rootFrame != null) {
                    try {
                        windowManager.removeView(rootFrame);
                    } catch (Exception ignored) {}
                    rootFrame.removeAllViews();
                }
                if (drawView != null) {
                    try {
                        if (drawView.isAttachedToWindow()) {
                            windowManager.removeView(drawView);
                        }
                    } catch (Exception ignored) {}
                    drawView = null;
                }
            }
        } catch (Exception ignored) {}
        categoryViews.clear();
        railTabButtons.clear();
        activeFloater = null;
    }

    public void openMenu() {
        if (collapsedRelative != null) collapsedRelative.setVisibility(View.GONE);
        if (menuLayout != null) menuLayout.setVisibility(View.VISIBLE);
        updateScreenDimensions();

        boolean isLandscape = SCREEN_WIDTH > SCREEN_HEIGHT;
        // Dynamically compute expansive width so sliders have ample space, while strictly preserving original compact height
        int targetWidth = isLandscape ? FloaterStyles.dp(context, 940) : FloaterStyles.dp(context, 740);
        menuWidth = Math.min(targetWidth, (int) (SCREEN_WIDTH * 0.96f));
        menuHeight = Math.min(FloaterStyles.dp(context, isLandscape ? 195 : 240), (int) (SCREEN_HEIGHT * 0.70f));

        if (menuLayout != null && menuLayout.getLayoutParams() != null) {
            menuLayout.getLayoutParams().width = menuWidth;
            menuLayout.getLayoutParams().height = menuHeight;
            menuLayout.requestLayout();
        }

        if (bodyWatermark != null && bodyWatermark.getLayoutParams() != null) {
            int watermarkSize = Math.min(FloaterStyles.dp(context, 180), (int) (menuHeight * 0.90f));
            bodyWatermark.getLayoutParams().width = watermarkSize;
            bodyWatermark.getLayoutParams().height = watermarkSize;
        }

        if (rootFrame != null) {
            rootFrame.requestLayout();
            try {
                if (windowManager != null && rootFrame.isAttachedToWindow()) {
                    if (savedMenuX >= 0 && savedMenuY >= 0 && !isLogoDragged) {
                        windowManagerParams.x = savedMenuX;
                        windowManagerParams.y = savedMenuY;
                    } else {
                        if (SCREEN_WIDTH > 0 && SCREEN_HEIGHT > 0 && menuWidth > 0 && menuHeight > 0) {
                            int maxX = Math.max(0, SCREEN_WIDTH - menuWidth);
                            int maxY = Math.max(0, SCREEN_HEIGHT - menuHeight);
                            if (windowManagerParams.x > maxX) windowManagerParams.x = maxX;
                            if (windowManagerParams.y > maxY) windowManagerParams.y = maxY;
                            if (windowManagerParams.x < 0) windowManagerParams.x = 0;
                            if (windowManagerParams.y < 0) windowManagerParams.y = 0;
                        }
                    }
                    windowManager.updateViewLayout(rootFrame, windowManagerParams);
                }
            } catch (Exception ignored) {}
        }
    }

    public void closeMenu() {
        if (windowManagerParams != null) {
            savedMenuX = windowManagerParams.x;
            savedMenuY = windowManagerParams.y;
        }
        isLogoDragged = false;
        if (menuLayout != null) menuLayout.setVisibility(View.GONE);
        if (collapsedRelative != null) collapsedRelative.setVisibility(View.VISIBLE);
        if (rootFrame != null) {
            rootFrame.requestLayout();
            try {
                if (windowManager != null && rootFrame.isAttachedToWindow()) {
                    windowManager.updateViewLayout(rootFrame, windowManagerParams);
                }
            } catch (Exception ignored) {}
        }
    }

    public Floater(Context globContext) {
        if (activeFloater != null && activeFloater != this) {
            activeFloater.destroyOverlay();
        }
        activeFloater = this;
        context = globContext.getApplicationContext() != null ? globContext.getApplicationContext() : globContext;
        ensureDaemonExtracted(context);
        AppLogger.log("FLOATER", "Floater overlay initialized for package: " + context.getPackageName());
        onCreate();
    }

    public static void ensureDaemonExtracted(Context ctx) {
        String abi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";
        String assetName = abi.contains("x86") ? "Titoo1" : "Titoo";
        File filesDest = new File(ctx.getFilesDir(), assetName);
        File cacheDest = new File(ctx.getCacheDir(), assetName);

        if (!filesDest.exists() || filesDest.length() < 10000) {
            copyAsset(ctx, assetName, filesDest);
        }
        if (!cacheDest.exists() || cacheDest.length() < 10000) {
            copyAsset(ctx, assetName, cacheDest);
        }
        try {
            Runtime.getRuntime().exec("su -c setenforce 0");
        } catch (Throwable ignored) {}
    }

    private static void copyAsset(Context ctx, String assetName, File destFile) {
        try (java.io.InputStream in = ctx.getAssets().open(assetName);
             java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            out.flush();
            destFile.setExecutable(true, false);
            destFile.setReadable(true, false);
            destFile.setWritable(true, false);
            Runtime.getRuntime().exec("chmod 777 " + destFile.getAbsolutePath());
            AppLogger.log("DAEMON_EXTRACT", "Extracted " + assetName + " to " + destFile.getAbsolutePath() + " (" + destFile.length() + " bytes)");
        } catch (Throwable t) {
            AppLogger.logE("DAEMON_EXTRACT", "Failed to copy asset " + assetName, t);
        }
    }

    public void onCreate() {
        onCreateSystemWindow();
        onCreateTemplate();
    }

    private int getLayoutType() {
        if (Build.VERSION.SDK_INT >= 26) return 2038;
        if (Build.VERSION.SDK_INT >= 24) return 2002;
        if (Build.VERSION.SDK_INT >= 23) return 2005;
        return 2003;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void onCreateSystemWindow() {
        int layoutFlag = getLayoutType();

        windowManagerParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        windowManagerParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowManagerParams.x = 40;
        windowManagerParams.y = 120;

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DrawCanvas();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void onCreateTemplate() {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
        SCREEN_WIDTH = screenWidth;
        SCREEN_HEIGHT = screenHeight;

        boolean isLandscape = screenWidth > screenHeight;
        // Tactical 3-Layer Compact Layout dimensions (Expansive width per user request, compact height preserved)
        int targetWidth = isLandscape ? FloaterStyles.dp(context, 940) : FloaterStyles.dp(context, 740);
        menuWidth = Math.min(targetWidth, (int) (screenWidth * 0.96f));
        menuHeight = Math.min(FloaterStyles.dp(context, isLandscape ? 195 : 240), (int) (screenHeight * 0.70f));

        rootFrame = new FrameLayout(context);
        rootFrame.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        rootRelative = new RelativeLayout(context);
        rootRelative.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        collapsedRelative = new FrameLayout(context);
        int totalLogoBox = FloaterStyles.dp(context, 76);
        collapsedRelative.setLayoutParams(new ViewGroup.LayoutParams(totalLogoBox, totalLogoBox));
        collapsedRelative.setVisibility(View.VISIBLE);

        int brandIcon = com.ninmods.main.R.drawable.nin_logo;
        try {
            brandIcon = com.ninmods.main.menu.MenuRouter.getActiveMenu(context).getIconResId();
        } catch (Throwable ignored) {}

        int logoSize = FloaterStyles.dp(context, 60);
        logoImagem = new ImageView(context);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(logoSize, logoSize);
        logoLp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        logoImagem.setLayoutParams(logoLp);
        logoImagem.setImageResource(brandIcon);
        logoImagem.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logoImagem.setBackground(null); // Pure PNG without circular background
        collapsedRelative.addView(logoImagem);

        // Corner Close Badge (X) with Transparent Circle background & Pink circular ring border per user explicit request
        TextView logoCloseBadge = new TextView(context);
        int badgeSize = FloaterStyles.dp(context, 20);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(badgeSize, badgeSize);
        badgeLp.gravity = Gravity.TOP | Gravity.RIGHT;
        badgeLp.topMargin = FloaterStyles.dp(context, 2);
        badgeLp.rightMargin = FloaterStyles.dp(context, 2);
        logoCloseBadge.setLayoutParams(badgeLp);
        logoCloseBadge.setGravity(Gravity.CENTER);
        logoCloseBadge.setText("X");
        logoCloseBadge.setTextColor(FloaterStyles.getColorAccentPink(context));
        logoCloseBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        logoCloseBadge.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(Color.TRANSPARENT); // Strictly transparent background per user requirement
        badgeBg.setStroke(FloaterStyles.dp(context, 1.5f), FloaterStyles.getColorAccentPink(context)); // Circular pink border ring
        logoCloseBadge.setBackground(badgeBg);

        logoCloseBadge.setOnClickListener(v -> {
            destroyOverlay();
        });
        collapsedRelative.addView(logoCloseBadge);

        // Main Menu Layout (Translucent Glass Container with Body Watermark Logo)
        menuLayout = new FrameLayout(context);
        menuLayout.setLayoutParams(new LinearLayout.LayoutParams(menuWidth, menuHeight));
        menuLayout.setVisibility(View.GONE);
        menuLayout.setBackground(FloaterStyles.createCardBackground(context));
        menuLayout.setPadding(0, 0, 0, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            menuLayout.setClipToOutline(true);
        }

        // Large Brand Logo Watermark in Menu Body (Enlarged per user request)
        bodyWatermark = new ImageView(context);
        int watermarkSize = Math.min(FloaterStyles.dp(context, 180), (int) (menuHeight * 0.90f));
        FrameLayout.LayoutParams wmLp = new FrameLayout.LayoutParams(watermarkSize, watermarkSize);
        wmLp.gravity = Gravity.CENTER;
        wmLp.leftMargin = FloaterStyles.dp(context, 20);
        bodyWatermark.setLayoutParams(wmLp);
        bodyWatermark.setImageResource(brandIcon);
        bodyWatermark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bodyWatermark.setAlpha(0.20f);
        bodyWatermark.setClickable(false);
        bodyWatermark.setFocusable(false);
        menuLayout.addView(bodyWatermark);

        // Content Horizontal Row: Icon Rail + Page 1 + Page 2
        LinearLayout menuContent = new LinearLayout(context);
        menuContent.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        menuContent.setOrientation(LinearLayout.HORIZONTAL);
        menuContent.setPadding(0, 0, 0, 0);

        // LAYER 1: Icon-Only Rail (Width: 38dp, Smooth Left Rounded Corners)
        LinearLayout iconRail = new LinearLayout(context);
        int railWidth = FloaterStyles.dp(context, 38);
        iconRail.setLayoutParams(new LinearLayout.LayoutParams(railWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        iconRail.setOrientation(LinearLayout.VERTICAL);
        iconRail.setBackground(FloaterStyles.createLeftRailBackground(context));
        iconRail.setGravity(Gravity.CENTER_HORIZONTAL);
        iconRail.setPadding(0, FloaterStyles.dp(context, 6), 0, FloaterStyles.dp(context, 6));

        // Top mini logo (Brand display only, strictly non-clickable per user requirement)
        ImageView railLogo = new ImageView(context);
        railLogo.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 24), FloaterStyles.dp(context, 24)));
        railLogo.setImageResource(brandIcon);
        railLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        railLogo.setClickable(false);
        railLogo.setFocusable(false);
        iconRail.addView(railLogo);

        View railSep = new View(context);
        LinearLayout.LayoutParams rslp = new LinearLayout.LayoutParams(FloaterStyles.dp(context, 22), FloaterStyles.dp(context, 1));
        rslp.topMargin = FloaterStyles.dp(context, 4);
        rslp.bottomMargin = FloaterStyles.dp(context, 4);
        railSep.setLayoutParams(rslp);
        railSep.setBackgroundColor(FloaterStyles.getColorBorder(context));
        iconRail.addView(railSep);

        // Vector Icon Buttons: Tab 0 (Aim), Tab 1 (ESP), Tab 2 (Position Fix), Tab 3 (Settings)
        iconTabsContainer = new LinearLayout(context);
        iconTabsContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        iconTabsContainer.setOrientation(LinearLayout.VERTICAL);
        iconTabsContainer.setGravity(Gravity.CENTER_HORIZONTAL);

        railTabButtons.clear();
        int[] railDrawables = new int[]{
                com.ninmods.main.R.drawable.ic_rail_aim,
                com.ninmods.main.R.drawable.ic_rail_esp,
                com.ninmods.main.R.drawable.ic_rail_position
        };
        for (int i = 0; i < railDrawables.length; i++) {
            final int tabIndex = i;
            ImageView btn = new ImageView(context);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(FloaterStyles.dp(context, 28), FloaterStyles.dp(context, 28));
            blp.topMargin = FloaterStyles.dp(context, 3);
            blp.bottomMargin = FloaterStyles.dp(context, 3);
            btn.setLayoutParams(blp);
            btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            btn.setPadding(FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 4));
            btn.setImageResource(railDrawables[i]);
            btn.setOnClickListener(v -> selectRailTab(tabIndex));
            iconTabsContainer.addView(btn);
            railTabButtons.add(btn);
        }
        iconRail.addView(iconTabsContainer);
        menuContent.addView(iconRail);

        // Divider 1
        View div1 = new View(context);
        div1.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 1), ViewGroup.LayoutParams.MATCH_PARENT));
        div1.setBackgroundColor(FloaterStyles.getColorBorder(context));
        menuContent.addView(div1);

        // LAYER 2: Page 1 (Sub-Panel 1, e.g. AIM ASSIST)
        LinearLayout page1 = new LinearLayout(context);
        LinearLayout.LayoutParams p1Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        page1.setLayoutParams(p1Lp);
        page1.setOrientation(LinearLayout.VERTICAL);
        page1.setPadding(FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 4));

        LinearLayout p1Header = new LinearLayout(context);
        p1Header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 26)));
        p1Header.setOrientation(LinearLayout.HORIZONTAL);
        p1Header.setGravity(Gravity.CENTER_VERTICAL);
        p1Header.setPadding(FloaterStyles.dp(context, 4), 0, FloaterStyles.dp(context, 4), 0);

        page1Title = new TextView(context);
        LinearLayout.LayoutParams p1tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        page1Title.setLayoutParams(p1tLp);
        page1Title.setText("AIM ASSIST");
        page1Title.setTextColor(FloaterStyles.getColorSectionTitle(context));
        page1Title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        page1Title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        p1Header.addView(page1Title);
        page1.addView(p1Header);

        View p1Div = new View(context);
        LinearLayout.LayoutParams p1dLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 1));
        p1dLp.topMargin = FloaterStyles.dp(context, 2);
        p1dLp.bottomMargin = FloaterStyles.dp(context, 3);
        p1Div.setLayoutParams(p1dLp);
        p1Div.setBackgroundColor(FloaterStyles.getColorBorder(context));
        page1.addView(p1Div);

        ScrollView scroll1 = new ScrollView(context);
        scroll1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        scroll1.setVerticalScrollBarEnabled(false);

        page1Content = new LinearLayout(context);
        page1Content.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page1Content.setOrientation(LinearLayout.VERTICAL);
        page1Content.setPadding(FloaterStyles.dp(context, 2), FloaterStyles.dp(context, 1), FloaterStyles.dp(context, 2), FloaterStyles.dp(context, 1));
        scroll1.addView(page1Content);
        page1.addView(scroll1);
        menuContent.addView(page1);

        // Divider 2
        View div2 = new View(context);
        div2.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 1), ViewGroup.LayoutParams.MATCH_PARENT));
        div2.setBackgroundColor(FloaterStyles.getColorBorder(context));
        menuContent.addView(div2);

        // LAYER 3: Page 2 (Sub-Panel 2, e.g. AIM SETTINGS + Close Button)
        LinearLayout page2 = new LinearLayout(context);
        LinearLayout.LayoutParams p2Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        page2.setLayoutParams(p2Lp);
        page2.setOrientation(LinearLayout.VERTICAL);
        page2.setPadding(FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 4));

        LinearLayout p2Header = new LinearLayout(context);
        p2Header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 26)));
        p2Header.setOrientation(LinearLayout.HORIZONTAL);
        p2Header.setGravity(Gravity.CENTER_VERTICAL);
        p2Header.setPadding(FloaterStyles.dp(context, 4), 0, FloaterStyles.dp(context, 4), 0);

        page2Title = new TextView(context);
        LinearLayout.LayoutParams p2tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        page2Title.setLayoutParams(p2tLp);
        page2Title.setText("AIM SETTINGS");
        page2Title.setTextColor(FloaterStyles.getColorSectionTitle(context));
        page2Title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        page2Title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        p2Header.addView(page2Title);

        TextView closeBtn = new TextView(context);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 22), FloaterStyles.dp(context, 22)));
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setText("X");
        closeBtn.setTextColor(FloaterStyles.getColorAccentPink(context));
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setOnClickListener(v -> closeMenu());
        p2Header.addView(closeBtn);
        page2.addView(p2Header);

        View p2Div = new View(context);
        LinearLayout.LayoutParams p2dLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 1));
        p2dLp.topMargin = FloaterStyles.dp(context, 2);
        p2dLp.bottomMargin = FloaterStyles.dp(context, 3);
        p2Div.setLayoutParams(p2dLp);
        p2Div.setBackgroundColor(FloaterStyles.getColorBorder(context));
        page2.addView(p2Div);

        ScrollView scroll2 = new ScrollView(context);
        scroll2.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        scroll2.setVerticalScrollBarEnabled(false);

        page2Content = new LinearLayout(context);
        page2Content.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page2Content.setOrientation(LinearLayout.VERTICAL);
        page2Content.setPadding(FloaterStyles.dp(context, 2), FloaterStyles.dp(context, 1), FloaterStyles.dp(context, 2), FloaterStyles.dp(context, 1));
        scroll2.addView(page2Content);
        page2.addView(scroll2);

        menuContent.addView(page2);
        menuLayout.addView(menuContent);

        rootRelative.addView(collapsedRelative);
        rootRelative.addView(menuLayout);
        rootFrame.addView(rootRelative);

        // Add to WindowManager safely
        try {
            if (rootFrame.getParent() == null) {
                windowManager.addView(rootFrame, windowManagerParams);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        logoImagem.setOnTouchListener(createLogoTouchListener());
        iconRail.setOnTouchListener(createMenuDragListener());
        p1Header.setOnTouchListener(createMenuDragListener());
        p2Header.setOnTouchListener(createMenuDragListener());

        highlightActiveRailTab(0);

        // Call genuine native C++ Functions immediately to populate all real features
        try {
            Functions();
            ftKZXivSr(context);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        refreshActivePages();

        // Periodic inquiry disabled: once floating menu is running, it remains active without cutoff
        mainHandler.removeCallbacks(heartbeatRunnable);
    }

    public void selectRailTab(int index) {
        activeRailIndex = index;
        highlightActiveRailTab(index);
        refreshActivePages();
    }

    private void highlightActiveRailTab(int index) {
        for (int i = 0; i < railTabButtons.size(); i++) {
            ImageView btn = railTabButtons.get(i);
            if (i == index) {
                btn.setBackground(FloaterStyles.createActiveIconRailDrawable(context));
                btn.setColorFilter(FloaterStyles.getColorIconActive(context));
            } else {
                btn.setBackground(FloaterStyles.createInactiveIconRailDrawable(context));
                btn.setColorFilter(FloaterStyles.getColorIconInactive(context));
            }
        }
    }

    public void refreshActivePages() {
        if (page1Content == null || page2Content == null) return;
        page1Content.removeAllViews();
        page2Content.removeAllViews();

        String p1Cat = "Aim";
        String p2Cat = "Aim Setting";
        String p1Header = "AIM ASSIST";
        String p2Header = "AIM SETTINGS";

        if (activeRailIndex == 0) {
            p1Cat = "Aim";
            p2Cat = "Aim Setting";
            p1Header = "AIM ASSIST";
            p2Header = "AIM SETTINGS";
        } else if (activeRailIndex == 1) {
            p1Cat = "Esp";
            p2Cat = "Esp Setting";
            p1Header = "ESP VISUALS";
            p2Header = "ESP SETTINGS";
        } else if (activeRailIndex == 2) {
            p1Cat = "Position Fix";
            p2Cat = "Position Fix Extra";
            p1Header = "POSITION FIX";
            p2Header = "CALIBRATION";
        }

        if (page1Title != null) page1Title.setText(p1Header);
        if (page2Title != null) page2Title.setText(p2Header);

        List<View> p1Views = categoryViews.get(p1Cat);
        if (p1Views != null) {
            for (View v : p1Views) {
                if (v.getParent() instanceof ViewGroup) {
                    ((ViewGroup) v.getParent()).removeView(v);
                }
                page1Content.addView(v);
            }
        }

        List<View> p2Views = categoryViews.get(p2Cat);
        if (p2Views != null) {
            for (View v : p2Views) {
                if (v.getParent() instanceof ViewGroup) {
                    ((ViewGroup) v.getParent()).removeView(v);
                }
                page2Content.addView(v);
            }
        }
    }



    // Dynamic genuine registration from Native C++ JNI
    public void onNativeAddCategory(final String name) {
        if (name == null || name.trim().isEmpty()) return;
        final String catName = name.trim();
        if (!categoryViews.containsKey(catName)) {
            categoryViews.put(catName, new ArrayList<>());
        }
        currentCategory = catName;
        refreshActivePages();
    }

    public void onNativeAddSwitch(final String name, final int id) {
        if (!categoryViews.containsKey(currentCategory)) {
            categoryViews.put(currentCategory, new ArrayList<>());
        }

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int pad = FloaterStyles.dp(context, 2);
        row.setPadding(0, pad, 0, pad);

        TextView tv = new TextView(context);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        tv.setText(name);
        tv.setSingleLine(true);
        tv.setMaxLines(1);
        tv.setTextColor(FloaterStyles.getColorTextPrimary(context));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tv.setTypeface(Typeface.DEFAULT);
        row.addView(tv);

        final Switch sw = new Switch(context);
        sw.setChecked(false);
        sw.setScaleX(0.80f);
        sw.setScaleY(0.80f);

        // Apply pure Pink tinting for checked state, clean muted tint for unchecked state per user requirement
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int accentPink = FloaterStyles.getColorAccentPink(context);
            ColorStateList thumbStates = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    },
                    new int[]{
                            accentPink,
                            0xFF8A7A90
                    }
            );
            ColorStateList trackStates = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    },
                    new int[]{
                            0x80E637E6,
                            0x33FFFFFF
                    }
            );
            sw.setThumbTintList(thumbStates);
            sw.setTrackTintList(trackStates);
        }

        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ChangesID(id, isChecked ? 1 : 0);
        });
        row.addView(sw);

        categoryViews.get(currentCategory).add(row);
        schedulePageRefresh();
    }

    private boolean isSelectorOption(String type, int id, int max) {
        return "Color".equals(type) || "BoxType".equals(type) || "LineType".equals(type) || id == 61 || id == 62 || (max > 0 && max <= 3);
    }

    private String[] getOptionLabels(String type, int id, int max) {
        if ("Color".equals(type)) {
            return new String[]{"White", "Green", "Blue", "Red", "Black", "Yellow", "Cyan", "Magenta", "Gray", "Purple"};
        } else if ("BoxType".equals(type)) {
            return new String[]{"Stroke", "Filled", "Corner"};
        } else if ("LineType".equals(type)) {
            return new String[]{"Top", "Center", "Bottom"};
        } else if (id == 61) {
            return new String[]{"Head", "Body"};
        } else if (id == 62) {
            return new String[]{"AimFov", "Distance"};
        } else {
            String[] arr = new String[max + 1];
            for (int i = 0; i <= max; i++) arr[i] = String.valueOf(i);
            return arr;
        }
    }

    private String getSliderUnit(String name) {
        if (name == null) return "";
        String lower = name.toLowerCase();
        if (lower.contains("fov") || lower.contains("distance") || lower.contains("size")) return " px";
        if (lower.contains("%") || lower.contains("headshot")) return " %";
        if (lower.contains("speed")) return "x";
        return "";
    }

    public void onNativeAddSeekBar(final String name, final int value, final int max, final String type, final int id) {
        if (!categoryViews.containsKey(currentCategory)) {
            categoryViews.put(currentCategory, new ArrayList<>());
        }

        final boolean isOption = isSelectorOption(type, id, max);
        final String[] options = isOption ? getOptionLabels(type, id, max) : null;
        final int seekMax = isOption ? (options.length - 1) : max;

        // Slide Bar (SeekBar) for both ranges and discrete options per user explicit rule
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = FloaterStyles.dp(context, 3);
        rowLp.bottomMargin = FloaterStyles.dp(context, 4);
        row.setLayoutParams(rowLp);
        int pad = FloaterStyles.dp(context, 2);
        row.setPadding(pad, pad, pad, pad);

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tv = new TextView(context);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        tv.setText(name);
        tv.setSingleLine(true);
        tv.setMaxLines(1);
        tv.setTextColor(FloaterStyles.getColorTextPrimary(context));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        tv.setTypeface(Typeface.DEFAULT);
        tv.setPadding(0, 0, FloaterStyles.dp(context, 8), 0);
        topRow.addView(tv);

        final TextView valLabel = new TextView(context);
        valLabel.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        valLabel.setTextColor(FloaterStyles.getColorSectionTitle(context));
        valLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        valLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        valLabel.setPadding(FloaterStyles.dp(context, 4), 0, FloaterStyles.dp(context, 4), 0);

        int initVal = isOption ? Math.max(0, Math.min(value, options.length - 1)) : value;
        valLabel.setText(isOption ? options[initVal] : (initVal + getSliderUnit(name)));
        topRow.addView(valLabel);
        row.addView(topRow);

        SeekBar sb = new SeekBar(context);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.topMargin = FloaterStyles.dp(context, 2);
        sb.setLayoutParams(sbLp);
        sb.setMax(seekMax);
        sb.setProgress(initVal);
        sb.setPadding(FloaterStyles.dp(context, 6), FloaterStyles.dp(context, 2), FloaterStyles.dp(context, 6), FloaterStyles.dp(context, 2));

        // Apply pure Pink tinting for thumb and progress bar per user explicit request
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int accentPink = FloaterStyles.getColorAccentPink(context);
            sb.setThumbTintList(ColorStateList.valueOf(accentPink));
            sb.setProgressTintList(ColorStateList.valueOf(accentPink));
            sb.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        }
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isOption && options != null && progress >= 0 && progress < options.length) {
                    valLabel.setText(options[progress]);
                } else {
                    valLabel.setText(progress + getSliderUnit(name));
                }
                ChangesID(id, progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        row.addView(sb);

        categoryViews.get(currentCategory).add(row);
        schedulePageRefresh();
    }

    private final Runnable debouncedRefresh = this::refreshActivePages;
    private void schedulePageRefresh() {
        mainHandler.removeCallbacks(debouncedRefresh);
        mainHandler.postDelayed(debouncedRefresh, 50);
    }

    // Dedicated smooth 1:1 touch listener for floating logo
    private View.OnTouchListener createLogoTouchListener() {
        return new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean hasMoved = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = windowManagerParams.x;
                        initialY = windowManagerParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        hasMoved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            hasMoved = true;
                            isLogoDragged = true;
                        }
                        windowManagerParams.x = initialX + dx;
                        windowManagerParams.y = initialY + dy;
                        try {
                            if (rootFrame != null && rootFrame.isAttachedToWindow()) {
                                windowManager.updateViewLayout(rootFrame, windowManagerParams);
                            }
                        } catch (Exception ignored) {}
                        return true;

                    case MotionEvent.ACTION_UP:
                        int xDiff = (int) (event.getRawX() - initialTouchX);
                        int yDiff = (int) (event.getRawY() - initialTouchY);
                        if (!hasMoved && Math.abs(xDiff) < 12 && Math.abs(yDiff) < 12) {
                            openMenu();
                        }
                        return true;
                }
                return false;
            }
        };
    }

    // Dedicated smooth 1:1 touch listener for menu headers
    private View.OnTouchListener createMenuDragListener() {
        return new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = windowManagerParams.x;
                        initialY = windowManagerParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        windowManagerParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        windowManagerParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        savedMenuX = windowManagerParams.x;
                        savedMenuY = windowManagerParams.y;
                        try {
                            if (rootFrame != null && rootFrame.isAttachedToWindow()) {
                                windowManager.updateViewLayout(rootFrame, windowManagerParams);
                            }
                        } catch (Exception ignored) {}
                        return true;

                    case MotionEvent.ACTION_UP:
                        savedMenuX = windowManagerParams.x;
                        savedMenuY = windowManagerParams.y;
                        return true;
                }
                return false;
            }
        };
    }

    public void DrawCanvas() {
        if (drawView != null && drawView.isAttachedToWindow()) return;
        int layoutFlag = getLayoutType();
        drawView = new GLES3JNIView(context);
        windowManagerDrawViewParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        windowManagerDrawViewParams.gravity = Gravity.TOP | Gravity.START;
        windowManagerDrawViewParams.x = 0;
        windowManagerDrawViewParams.y = 0;
        try {
            windowManager.addView(drawView, windowManagerDrawViewParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Static bridge methods called directly by Native C++ JNI
    public static void addCategory(final String name) {
        mainHandler.post(() -> {
            Floater instance = activeFloater;
            if (instance != null && instance.isAttached()) {
                instance.onNativeAddCategory(name);
            }
        });
    }

    public static void addSwitch(final String name, final int ID) {
        mainHandler.post(() -> {
            Floater instance = activeFloater;
            if (instance != null && instance.isAttached()) {
                instance.onNativeAddSwitch(name, ID);
            }
        });
    }

    public static void addSeekBar(final String name, final int value, final int max, final String type, final int ID) {
        mainHandler.post(() -> {
            Floater instance = activeFloater;
            if (instance != null && instance.isAttached()) {
                instance.onNativeAddSeekBar(name, value, max, type, ID);
            }
        });
    }
}
