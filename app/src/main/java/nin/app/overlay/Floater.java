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
        // Compact dimensions per user request
        int menuWidth = Math.min(FloaterStyles.dp(context, isLandscape ? 280 : 250), (int) (screenWidth * 0.85f));
        int menuHeight = Math.min(FloaterStyles.dp(context, isLandscape ? 230 : 270), (int) (screenHeight * 0.70f));

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
        logoBg.setColor(AppColors.CARD_BACKGROUND);
        logoBg.setStroke(FloaterStyles.dp(context, 2), AppColors.PRIMARY);
        logoImagem.setBackground(logoBg);

        if (base64Icon != null && !base64Icon.isEmpty()) {
            Bitmap iconBitmap = FloaterStyles.base64ToBitmap(base64Icon);
            if (iconBitmap != null) logoImagem.setImageBitmap(iconBitmap);
            else logoImagem.setImageBitmap(FloaterStyles.createDefaultIcon());
        } else {
            logoImagem.setImageBitmap(FloaterStyles.createDefaultIcon());
        }

        // Main Menu Layout (Compact & Clean)
        menuLayout = new LinearLayout(context);
        menuLayout.setLayoutParams(new LinearLayout.LayoutParams(menuWidth, menuHeight));
        menuLayout.setVisibility(View.GONE);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setBackground(FloaterStyles.createCardBackground(context));
        menuLayout.setPadding(FloaterStyles.dp(context, 6), FloaterStyles.dp(context, 6), FloaterStyles.dp(context, 6), FloaterStyles.dp(context, 6));

        // 1. Header Bar (Draggable, tap logo or header to close, ZERO minimize/close buttons)
        LinearLayout headerBlock = new LinearLayout(context);
        headerBlock.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        headerBlock.setOrientation(LinearLayout.HORIZONTAL);
        headerBlock.setGravity(Gravity.CENTER_VERTICAL);
        headerBlock.setPadding(FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 6), FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 6));

        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setShape(GradientDrawable.RECTANGLE);
        headerBg.setCornerRadius(FloaterStyles.dp(context, 8));
        headerBg.setColor(AppColors.PANEL_HEADER_START);
        headerBlock.setBackground(headerBg);

        ImageView menuLogo = new ImageView(context);
        menuLogo.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 22), FloaterStyles.dp(context, 22)));
        menuLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (base64Icon != null && !base64Icon.isEmpty()) {
            Bitmap b = FloaterStyles.base64ToBitmap(base64Icon);
            if (b != null) menuLogo.setImageBitmap(b);
            else menuLogo.setImageBitmap(FloaterStyles.createDefaultIcon());
        } else {
            menuLogo.setImageBitmap(FloaterStyles.createDefaultIcon());
        }
        menuLogo.setOnClickListener(v -> closeMenu());
        headerBlock.addView(menuLogo);

        TextView brand = new TextView(context);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        brandLp.leftMargin = FloaterStyles.dp(context, 8);
        brand.setLayoutParams(brandLp);
        brand.setText("NINMODS");
        brand.setTextColor(AppColors.PRIMARY);
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        headerBlock.addView(brand);

        headerBlock.setOnTouchListener(createDragListener(false));
        menuLayout.addView(headerBlock);

        // 2. Category Tab Bar (Horizontal Scroll)
        HorizontalScrollView tabScroller = new HorizontalScrollView(context);
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 34));
        tabLp.topMargin = FloaterStyles.dp(context, 4);
        tabScroller.setLayoutParams(tabLp);
        tabScroller.setHorizontalScrollBarEnabled(false);
        tabScroller.setOverScrollMode(View.OVER_SCROLL_NEVER);

        firstSectionMenuContainer = new LinearLayout(context);
        firstSectionMenuContainer.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        firstSectionMenuContainer.setOrientation(LinearLayout.HORIZONTAL);
        firstSectionMenuContainer.setGravity(Gravity.CENTER_VERTICAL);
        tabScroller.addView(firstSectionMenuContainer);
        menuLayout.addView(tabScroller);

        // 3. Main Body ScrollView (Options directly inside the menu body, NO nested card)
        mainScroll = new ScrollView(context);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollParams.topMargin = FloaterStyles.dp(context, 4);
        mainScroll.setLayoutParams(scrollParams);
        mainScroll.setVerticalScrollBarEnabled(false);

        funcsListLayout = new LinearLayout(context);
        funcsListLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        funcsListLayout.setOrientation(LinearLayout.VERTICAL);
        funcsListLayout.setPadding(FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 4));
        mainScroll.addView(funcsListLayout);

        menuLayout.addView(mainScroll);

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
        tv.setTextColor(AppColors.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
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

    public void onNativeAddSeekBar(final String name, final int value, final int max, final String type, final int id) {
        if (!categoryViews.containsKey(currentCategory)) {
            categoryViews.put(currentCategory, new ArrayList<>());
        }

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int pad = FloaterStyles.dp(context, 3);
        row.setPadding(0, pad, 0, pad);

        final TextView tv = new TextView(context);
        tv.setText(name + ": " + value);
        tv.setTextColor(AppColors.TEXT_SUBTITLE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTypeface(Typeface.DEFAULT);
        row.addView(tv);

        SeekBar sb = new SeekBar(context);
        sb.setMax(max);
        sb.setProgress(value);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tv.setText(name + ": " + progress);
                ChangesID(id, progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        row.addView(sb);

        categoryViews.get(currentCategory).add(row);
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
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    FloaterStyles.dp(context, 30)
            );
            lp.setMargins(0, 0, FloaterStyles.dp(context, 6), 0);
            tab.setLayoutParams(lp);
            tab.setText(catName);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(FloaterStyles.dp(context, 10), 0, FloaterStyles.dp(context, 10), 0);
            tab.setTag(catName);
            tab.setOnClickListener(v -> selectCategory(catName));

            firstSectionMenuContainer.addView(tab);
            categoryTabViews.add(tab);
        }

        if (selectedCategoryName == null && !categoryViews.isEmpty()) {
            selectedCategoryName = categoryViews.keySet().iterator().next();
        }
        highlightSelectedCategory(selectedCategoryName);
        displayCategory(selectedCategoryName);
    }

    private void highlightSelectedCategory(String selected) {
        for (TextView tab : categoryTabViews) {
            boolean isSel = selected != null && selected.equals(tab.getTag());
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(FloaterStyles.dp(context, 6));
            if (isSel) {
                bg.setColor(AppColors.TAB_SELECTED_BG);
                bg.setStroke(FloaterStyles.dp(context, 1), AppColors.PRIMARY);
                tab.setTextColor(AppColors.TEXT_WHITE);
            } else {
                bg.setColor(AppColors.TAB_UNSELECTED_BG);
                tab.setTextColor(AppColors.TEXT_DIM);
            }
            tab.setBackground(bg);
        }
    }

    public void selectCategory(String categoryName) {
        selectedCategoryName = categoryName;
        currentCategory = categoryName;
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
