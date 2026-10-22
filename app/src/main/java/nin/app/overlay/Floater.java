package nin.app.overlay;

import nin.app.core.AppColors;
import nin.app.core.Prefs;
import nin.app.core.Utils;
import nin.app.graphics.ESPView;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Messenger;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Floater {

    public static native void Functions();
    public static native void ChangesID(int ID, int Value);
    public static native void ftKZXivSr(Context context);
    public static native String getBase64Icon();
    public static native void PxbftKZXivSr(ESPView espView, Canvas canvas, int width, int height);
    public static boolean isLoggedIn() { return true; }
    public static native String getPrefKeyModeVersion();
    public static native String getUiString(int id);
    public static native String getSellerName();

    public static int SCREEN_WIDTH = 0;
    public static int SCREEN_HEIGHT = 0;

    public static int Width() { return SCREEN_WIDTH; }
    public static int Height() { return SCREEN_HEIGHT; }

    public IBinder onBind(Intent intent) { return null; }

    static Context context;
    private static Utils utils;
    public static Floater activeFloater = null;

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowManagerParams;
    ESPView drawView;
    WindowManager.LayoutParams windowManagerDrawViewParams;

    private FrameLayout rootFrame;
    private RelativeLayout rootRelative;
    private RelativeLayout collapsedRelative;
    private LinearLayout menuLayout;
    private ImageView logoImagem;

    private ScrollView mainScroll;
    public static LinearLayout funcsListLayout;
    private LinearLayout firstSectionMenuContainer;

    private final Map<String, List<View>> categoryViews = new LinkedHashMap<>();
    private final List<TextView> categoryTabViews = new ArrayList<>();
    private String currentCategory = "Aim";
    private String selectedCategoryName = "Aim";
    private TextView currentSectionTitle;

    // Strict Singleton & Lifecycle Control
    public static synchronized void start(Context ctx) {
        if (activeFloater != null && activeFloater.isAttached()) {
            activeFloater.openMenu();
            return;
        }
        if (activeFloater != null) {
            activeFloater.destroyOverlay();
        }
        Context targetCtx = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
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
        try {
            if (windowManager != null) {
                if (rootFrame != null) {
                    try {
                        windowManager.removeView(rootFrame);
                    } catch (Exception ignored) {}
                }
                if (drawView != null) {
                    drawView.stop();
                    try {
                        windowManager.removeView(drawView);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        activeFloater = null;
    }

    public void openMenu() {
        if (collapsedRelative != null) collapsedRelative.setVisibility(View.GONE);
        if (menuLayout != null) menuLayout.setVisibility(View.VISIBLE);
    }

    public void closeMenu() {
        if (menuLayout != null) menuLayout.setVisibility(View.GONE);
        if (collapsedRelative != null) collapsedRelative.setVisibility(View.VISIBLE);
    }

    public Floater(Context globContext) {
        if (activeFloater != null && activeFloater != this) {
            activeFloater.destroyOverlay();
        }
        activeFloater = this;
        context = globContext.getApplicationContext() != null ? globContext.getApplicationContext() : globContext;
        utils = new Utils(context);
        onCreate();
    }

    public void onCreate() {
        if (!isLoggedIn()) return;
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
        if (!isLoggedIn()) return;
        int layoutFlag = getLayoutType();

        windowManagerParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        windowManagerParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowManagerParams.x = 0;
        windowManagerParams.y = 100;

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
        // Tactical Split Layout dimensions matching landscape game HUD
        int menuWidth = Math.min(FloaterStyles.dp(context, isLandscape ? 340 : 310), (int) (screenWidth * 0.90f));
        int menuHeight = Math.min(FloaterStyles.dp(context, isLandscape ? 210 : 250), (int) (screenHeight * 0.70f));

        rootFrame = new FrameLayout(context);
        rootFrame.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        rootRelative = new RelativeLayout(context);
        rootRelative.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        collapsedRelative = new RelativeLayout(context);
        collapsedRelative.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        collapsedRelative.setAlpha(0.96f);
        collapsedRelative.setVisibility(View.VISIBLE);

        String base64Icon = getBase64Icon();
        logoImagem = new ImageView(context);
        logoImagem.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 46), FloaterStyles.dp(context, 46)));
        logoImagem.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logoImagem.setPadding(FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 4));

        GradientDrawable logoBg = new GradientDrawable();
        logoBg.setShape(GradientDrawable.OVAL);
        logoBg.setColor(FloaterStyles.ColorDarkBg);
        logoBg.setStroke(FloaterStyles.dp(context, 2), FloaterStyles.ColorAccentBlue);
        logoImagem.setBackground(logoBg);

        if (base64Icon != null && !base64Icon.isEmpty()) {
            Bitmap iconBitmap = FloaterStyles.base64ToBitmap(base64Icon);
            if (iconBitmap != null) logoImagem.setImageBitmap(iconBitmap);
            else logoImagem.setImageBitmap(FloaterStyles.createDefaultIcon());
        } else {
            logoImagem.setImageBitmap(FloaterStyles.createDefaultIcon());
        }

        // Main Menu Layout (Tactical Split Layout: Left Sidebar + Right Content Area)
        menuLayout = new LinearLayout(context);
        menuLayout.setLayoutParams(new LinearLayout.LayoutParams(menuWidth, menuHeight));
        menuLayout.setVisibility(View.GONE);
        menuLayout.setOrientation(LinearLayout.HORIZONTAL);
        menuLayout.setBackground(FloaterStyles.createCardBackground(context));
        menuLayout.setPadding(0, 0, 0, 0);

        // 1. LEFT SIDEBAR (Width: 105dp, contains Brand Title + Vertical Tabs)
        LinearLayout sidebar = new LinearLayout(context);
        int sidebarWidth = FloaterStyles.dp(context, 105);
        sidebar.setLayoutParams(new LinearLayout.LayoutParams(sidebarWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setBackgroundColor(FloaterStyles.ColorSidebarBg);
        sidebar.setPadding(FloaterStyles.dp(context, 6), FloaterStyles.dp(context, 6), FloaterStyles.dp(context, 6), FloaterStyles.dp(context, 6));
        sidebar.setOnTouchListener(createDragListener(false));

        // Brand Title
        TextView brandTitle = new TextView(context);
        brandTitle.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        brandTitle.setText("NINMODS");
        brandTitle.setTextColor(FloaterStyles.ColorSectionTitle);
        brandTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        brandTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        brandTitle.setGravity(Gravity.CENTER_VERTICAL);
        brandTitle.setPadding(FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 2), FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 2));
        sidebar.addView(brandTitle);

        // Thin divider under title
        View brandDivider = new View(context);
        LinearLayout.LayoutParams bdLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 1));
        bdLp.topMargin = FloaterStyles.dp(context, 4);
        bdLp.bottomMargin = FloaterStyles.dp(context, 4);
        brandDivider.setLayoutParams(bdLp);
        brandDivider.setBackgroundColor(FloaterStyles.ColorBorder);
        sidebar.addView(brandDivider);

        // Vertical ScrollView for Categories / Tabs
        ScrollView tabScroll = new ScrollView(context);
        tabScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        tabScroll.setVerticalScrollBarEnabled(false);
        tabScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        firstSectionMenuContainer = new LinearLayout(context);
        firstSectionMenuContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        firstSectionMenuContainer.setOrientation(LinearLayout.VERTICAL);
        tabScroll.addView(firstSectionMenuContainer);
        sidebar.addView(tabScroll);
        menuLayout.addView(sidebar);

        // 2. VERTICAL DIVIDER LINE
        View vDivider = new View(context);
        vDivider.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 1), ViewGroup.LayoutParams.MATCH_PARENT));
        vDivider.setBackgroundColor(FloaterStyles.ColorBorder);
        menuLayout.addView(vDivider);

        // 3. RIGHT CONTENT AREA (Section Title + Close Button + Options Scroll)
        LinearLayout contentArea = new LinearLayout(context);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        contentArea.setLayoutParams(contentLp);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setPadding(FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 6), FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 6));

        // Right Header
        LinearLayout rightHeader = new LinearLayout(context);
        rightHeader.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rightHeader.setOrientation(LinearLayout.HORIZONTAL);
        rightHeader.setGravity(Gravity.CENTER_VERTICAL);
        rightHeader.setPadding(FloaterStyles.dp(context, 2), 0, FloaterStyles.dp(context, 2), 0);
        rightHeader.setOnTouchListener(createDragListener(false));

        currentSectionTitle = new TextView(context);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        currentSectionTitle.setLayoutParams(titleLp);
        currentSectionTitle.setText("AIM ASSIST");
        currentSectionTitle.setTextColor(FloaterStyles.ColorSectionTitle);
        currentSectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        currentSectionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        rightHeader.addView(currentSectionTitle);

        TextView closeBtn = new TextView(context);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 24), FloaterStyles.dp(context, 24)));
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setText("✕");
        closeBtn.setTextColor(AppColors.WHITE);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setOnClickListener(v -> closeMenu());
        rightHeader.addView(closeBtn);
        contentArea.addView(rightHeader);

        // Divider under right header
        View rightDivider = new View(context);
        LinearLayout.LayoutParams rdLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 1));
        rdLp.topMargin = FloaterStyles.dp(context, 4);
        rdLp.bottomMargin = FloaterStyles.dp(context, 4);
        rightDivider.setLayoutParams(rdLp);
        rightDivider.setBackgroundColor(FloaterStyles.ColorBorder);
        contentArea.addView(rightDivider);

        // Main Body ScrollView
        mainScroll = new ScrollView(context);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        mainScroll.setLayoutParams(scrollParams);
        mainScroll.setVerticalScrollBarEnabled(false);

        funcsListLayout = new LinearLayout(context);
        funcsListLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        funcsListLayout.setOrientation(LinearLayout.VERTICAL);
        funcsListLayout.setPadding(FloaterStyles.dp(context, 2), FloaterStyles.dp(context, 2), FloaterStyles.dp(context, 2), FloaterStyles.dp(context, 2));
        mainScroll.addView(funcsListLayout);

        contentArea.addView(mainScroll);
        menuLayout.addView(contentArea);

        collapsedRelative.addView(logoImagem);
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

        logoImagem.setOnTouchListener(createDragListener(true));

        // Call genuine native C++ Functions immediately to populate all real features
        try {
            Functions();
            ftKZXivSr(context);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private String getCategoryIcon(String catName) {
        if (catName == null) return "•";
        String lower = catName.toLowerCase();
        if (lower.contains("aim")) return "🎯";
        if (lower.contains("esp") || lower.contains("visual")) return "👁";
        if (lower.contains("fix") || lower.contains("pos")) return "📐";
        if (lower.contains("set") || lower.contains("cfg") || lower.contains("config")) return "⚙";
        return "•";
    }

    private String formatSectionHeader(String catName) {
        if (catName == null) return "TACTICAL";
        String lower = catName.toLowerCase();
        if (lower.equals("aim")) return "AIM ASSIST";
        if (lower.contains("aim") && lower.contains("set")) return "AIM SETTINGS";
        if (lower.equals("esp")) return "ESP VISUALS";
        if (lower.contains("esp") && lower.contains("set")) return "ESP SETTINGS";
        if (lower.contains("pos") || lower.contains("fix")) return "POSITION FIX";
        return catName.toUpperCase();
    }

    // Dynamic genuine registration from Native C++ JNI
    public void onNativeAddCategory(final String name) {
        if (name == null || name.trim().isEmpty()) return;
        final String catName = name.trim();
        if (!categoryViews.containsKey(catName)) {
            categoryViews.put(catName, new ArrayList<>());
        }
        currentCategory = catName;
        rebuildCategoryTabs();
    }

    public void onNativeAddSwitch(final String name, final int id) {
        if (!categoryViews.containsKey(currentCategory)) {
            categoryViews.put(currentCategory, new ArrayList<>());
        }

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int pad = FloaterStyles.dp(context, 3);
        row.setPadding(0, pad, 0, pad);

        TextView tv = new TextView(context);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        tv.setText(name);
        tv.setTextColor(0xFFF0F2F8);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTypeface(Typeface.DEFAULT);
        row.addView(tv);

        final Switch sw = new Switch(context);
        sw.setChecked(false);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ChangesID(id, isChecked ? 1 : 0);
        });
        row.addView(sw);

        categoryViews.get(currentCategory).add(row);
        if (currentCategory.equals(selectedCategoryName)) {
            displayCategory(currentCategory);
        }
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

        if (isSelectorOption(type, id, max)) {
            // Dropdown / Selector Button matching photo (e.g. Aimbot Bone Target: Head ▼)
            final String[] options = getOptionLabels(type, id, max);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            int pad = FloaterStyles.dp(context, 3);
            row.setPadding(0, pad, 0, pad);

            TextView tv = new TextView(context);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            tv.setText(name);
            tv.setTextColor(0xFFF0F2F8);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTypeface(Typeface.DEFAULT);
            row.addView(tv);

            final TextView btn = new TextView(context);
            btn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, FloaterStyles.dp(context, 26)));
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(FloaterStyles.dp(context, 10), 0, FloaterStyles.dp(context, 10), 0);
            btn.setBackground(FloaterStyles.createSelectorBtnDrawable(context));

            final int[] currentVal = new int[]{Math.max(0, Math.min(value, options.length - 1))};
            btn.setText(options[currentVal[0]] + "  ▼");
            btn.setTextColor(FloaterStyles.ColorAccentBlue);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

            btn.setOnClickListener(v -> {
                currentVal[0] = (currentVal[0] + 1) % options.length;
                btn.setText(options[currentVal[0]] + "  ▼");
                ChangesID(id, currentVal[0]);
            });
            row.addView(btn);

            categoryViews.get(currentCategory).add(row);
        } else {
            // Range Slider with Value Badge (e.g. Aim FOV Range Size: 100.0 px)
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            int pad = FloaterStyles.dp(context, 3);
            row.setPadding(0, pad, 0, pad);

            LinearLayout topRow = new LinearLayout(context);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            topRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView tv = new TextView(context);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            tv.setText(name);
            tv.setTextColor(0xFFF0F2F8);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTypeface(Typeface.DEFAULT);
            topRow.addView(tv);

            final String unit = getSliderUnit(name);
            final TextView badge = new TextView(context);
            badge.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, FloaterStyles.dp(context, 22)));
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(FloaterStyles.dp(context, 8), 0, FloaterStyles.dp(context, 8), 0);
            badge.setBackground(FloaterStyles.createBadgeDrawable(context));
            badge.setText(value + unit);
            badge.setTextColor(FloaterStyles.ColorAccentBlue);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            topRow.addView(badge);
            row.addView(topRow);

            SeekBar sb = new SeekBar(context);
            sb.setMax(max);
            sb.setProgress(value);
            sb.setPadding(0, FloaterStyles.dp(context, 2), 0, FloaterStyles.dp(context, 2));
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    badge.setText(progress + unit);
                    ChangesID(id, progress);
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            row.addView(sb);

            categoryViews.get(currentCategory).add(row);
        }

        if (currentCategory.equals(selectedCategoryName)) {
            displayCategory(currentCategory);
        }
    }

    public void rebuildCategoryTabs() {
        if (firstSectionMenuContainer == null) return;
        firstSectionMenuContainer.removeAllViews();
        categoryTabViews.clear();

        for (final String catName : categoryViews.keySet()) {
            TextView tab = new TextView(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    FloaterStyles.dp(context, 30)
            );
            lp.setMargins(0, 0, 0, FloaterStyles.dp(context, 3));
            tab.setLayoutParams(lp);
            tab.setText(getCategoryIcon(catName) + " " + catName);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(FloaterStyles.dp(context, 8), 0, FloaterStyles.dp(context, 4), 0);
            tab.setTag(catName);
            tab.setOnClickListener(v -> selectCategory(catName));

            firstSectionMenuContainer.addView(tab);
            categoryTabViews.add(tab);
        }

        if (selectedCategoryName == null && !categoryViews.isEmpty()) {
            selectedCategoryName = categoryViews.keySet().iterator().next();
        }
        if (selectedCategoryName != null) {
            selectCategory(selectedCategoryName);
        }
    }

    private void highlightSelectedCategory(String selected) {
        for (TextView tab : categoryTabViews) {
            boolean isSel = selected != null && selected.equals(tab.getTag());
            if (isSel) {
                tab.setBackground(FloaterStyles.createActiveTabDrawable(context));
                tab.setTextColor(0xFFFFFFFF);
                tab.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            } else {
                tab.setBackground(FloaterStyles.createInactiveTabDrawable(context));
                tab.setTextColor(FloaterStyles.ColorTextMuted);
                tab.setTypeface(Typeface.DEFAULT);
            }
        }
    }

    public void selectCategory(String categoryName) {
        selectedCategoryName = categoryName;
        currentCategory = categoryName;
        if (currentSectionTitle != null) {
            currentSectionTitle.setText(formatSectionHeader(categoryName));
        }
        highlightSelectedCategory(categoryName);
        displayCategory(categoryName);
    }

    public void displayCategory(String categoryName) {
        if (funcsListLayout == null) return;
        funcsListLayout.removeAllViews();
        List<View> views = categoryViews.get(categoryName);
        if (views != null) {
            for (View v : views) {
                if (v.getParent() instanceof ViewGroup) {
                    ((ViewGroup) v.getParent()).removeView(v);
                }
                funcsListLayout.addView(v);
            }
        }
    }

    // Official Android WindowManager drag listener (smooth dragging, touch slop aware)
    private View.OnTouchListener createDragListener(final boolean isLogo) {
        final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        return new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = windowManagerParams.x;
                        initialY = windowManagerParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                            isDragging = true;
                            windowManagerParams.x = initialX + dx;
                            windowManagerParams.y = initialY + dy;
                            try {
                                if (rootFrame != null && rootFrame.isAttachedToWindow()) {
                                    windowManager.updateViewLayout(rootFrame, windowManagerParams);
                                }
                            } catch (Exception ignored) {}
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging && isLogo) {
                            openMenu();
                        }
                        return true;
                }
                return false;
            }
        };
    }

    public void DrawCanvas() {
        if (drawView != null && drawView.isAttachedToWindow()) return;
        int layoutFlag = getLayoutType();
        drawView = new ESPView(context);
        windowManagerDrawViewParams = new WindowManager.LayoutParams(-1, -1, layoutFlag, 56, -3);
        windowManagerDrawViewParams.gravity = Gravity.TOP | Gravity.START;
        windowManagerDrawViewParams.x = 0;
        windowManagerDrawViewParams.y = 0;
        windowManagerDrawViewParams.alpha = 0.8f;
        try {
            windowManager.addView(drawView, windowManagerDrawViewParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Static bridge methods called directly by Native C++ JNI
    public static void addCategory(final String name) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (activeFloater != null) activeFloater.onNativeAddCategory(name);
        });
    }

    public static void addCategory(final String name, int categoryHeightDp) {
        addCategory(name);
    }

    public static void addSwitch(final String name, final int ID) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (activeFloater != null) activeFloater.onNativeAddSwitch(name, ID);
        });
    }

    public static void addSwitch(final String name, final int ID, int switchHeightDp) {
        addSwitch(name, ID);
    }

    public static void addSeekBar(final String name, final int value, final int max, final String type, final int ID) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (activeFloater != null) activeFloater.onNativeAddSeekBar(name, value, max, type, ID);
        });
    }

    public static void addCheckBox(final String name, final int ID) {
        addSwitch(name, ID);
    }

    public static void addCheckBox(final String name, final int ID, int heightDp) {
        addSwitch(name, ID);
    }

    public static void addRadioGroup(final String name, final String[] options, final int defaultIndex, final int ID) {
    }

    public static void Funçoes(String pkg, String lib, String offset, String hex) {
    }
}
