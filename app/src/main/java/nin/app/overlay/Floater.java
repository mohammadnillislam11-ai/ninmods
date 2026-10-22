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
    private final List<TextView> railTabButtons = new ArrayList<>();
    private int activeRailIndex = 0;
    private String currentCategory = "Aim";
    private String selectedCategoryName = "Aim";

    private LinearLayout iconTabsContainer;
    private TextView page1Title;
    private TextView page2Title;
    private LinearLayout page1Content;
    private LinearLayout page2Content;

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
        // Tactical 3-Layer Compact Layout dimensions (low-profile height, wide horizontal)
        int menuWidth = Math.min(FloaterStyles.dp(context, isLandscape ? 470 : 385), (int) (screenWidth * 0.96f));
        int menuHeight = Math.min(FloaterStyles.dp(context, isLandscape ? 180 : 230), (int) (screenHeight * 0.65f));

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

        int logoSize = FloaterStyles.dp(context, 48);
        logoImagem = new ImageView(context);
        RelativeLayout.LayoutParams logoLp = new RelativeLayout.LayoutParams(logoSize, logoSize);
        logoLp.setMargins(FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 6), FloaterStyles.dp(context, 4));
        logoImagem.setLayoutParams(logoLp);
        logoImagem.setImageResource(nin.app.R.drawable.nin_logo);
        logoImagem.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logoImagem.setPadding(FloaterStyles.dp(context, 3), FloaterStyles.dp(context, 3), FloaterStyles.dp(context, 3), FloaterStyles.dp(context, 3));

        GradientDrawable logoBg = new GradientDrawable();
        logoBg.setShape(GradientDrawable.OVAL);
        logoBg.setColor(FloaterStyles.ColorDarkBg);
        logoBg.setStroke(FloaterStyles.dp(context, 2), FloaterStyles.ColorAccentBlue);
        logoImagem.setBackground(logoBg);
        collapsedRelative.addView(logoImagem);

        // Corner Close Badge (✕) on the floating logo
        TextView logoCloseBadge = new TextView(context);
        int badgeSize = FloaterStyles.dp(context, 19);
        RelativeLayout.LayoutParams badgeLp = new RelativeLayout.LayoutParams(badgeSize, badgeSize);
        badgeLp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        badgeLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        badgeLp.topMargin = 0;
        badgeLp.rightMargin = 0;
        logoCloseBadge.setLayoutParams(badgeLp);
        logoCloseBadge.setGravity(Gravity.CENTER);
        logoCloseBadge.setText("✕");
        logoCloseBadge.setTextColor(Color.WHITE);
        logoCloseBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        logoCloseBadge.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(0xFFE53935); // Crimson red
        badgeBg.setStroke(FloaterStyles.dp(context, 1.5f), 0xFFFFFFFF);
        logoCloseBadge.setBackground(badgeBg);

        logoCloseBadge.setOnClickListener(v -> {
            destroyOverlay();
        });
        collapsedRelative.addView(logoCloseBadge);

        // Main Menu Layout (3-Layer Layout: Icon Rail + Page 1 + Page 2)
        menuLayout = new LinearLayout(context);
        menuLayout.setLayoutParams(new LinearLayout.LayoutParams(menuWidth, menuHeight));
        menuLayout.setVisibility(View.GONE);
        menuLayout.setOrientation(LinearLayout.HORIZONTAL);
        menuLayout.setBackground(FloaterStyles.createCardBackground(context));
        menuLayout.setPadding(0, 0, 0, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            menuLayout.setClipToOutline(true);
        }

        // LAYER 1: Icon-Only Rail (Width: 38dp, Smooth Left Rounded Corners)
        LinearLayout iconRail = new LinearLayout(context);
        int railWidth = FloaterStyles.dp(context, 38);
        iconRail.setLayoutParams(new LinearLayout.LayoutParams(railWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        iconRail.setOrientation(LinearLayout.VERTICAL);
        iconRail.setBackground(FloaterStyles.createLeftRailBackground(context));
        iconRail.setGravity(Gravity.CENTER_HORIZONTAL);
        iconRail.setPadding(0, FloaterStyles.dp(context, 6), 0, FloaterStyles.dp(context, 6));
        iconRail.setOnTouchListener(createDragListener(false));

        // Top mini logo
        ImageView railLogo = new ImageView(context);
        railLogo.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 24), FloaterStyles.dp(context, 24)));
        railLogo.setImageResource(nin.app.R.drawable.nin_logo);
        railLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        railLogo.setOnClickListener(v -> closeMenu());
        iconRail.addView(railLogo);

        View railSep = new View(context);
        LinearLayout.LayoutParams rslp = new LinearLayout.LayoutParams(FloaterStyles.dp(context, 22), FloaterStyles.dp(context, 1));
        rslp.topMargin = FloaterStyles.dp(context, 4);
        rslp.bottomMargin = FloaterStyles.dp(context, 4);
        railSep.setLayoutParams(rslp);
        railSep.setBackgroundColor(FloaterStyles.ColorBorder);
        iconRail.addView(railSep);

        // Icon Buttons: Tab 0 (Aim 🎯), Tab 1 (ESP 👁), Tab 2 (Position Fix 📐), Tab 3 (Settings ⚙)
        iconTabsContainer = new LinearLayout(context);
        iconTabsContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        iconTabsContainer.setOrientation(LinearLayout.VERTICAL);
        iconTabsContainer.setGravity(Gravity.CENTER_HORIZONTAL);

        railTabButtons.clear();
        String[] railIcons = new String[]{"🎯", "👁", "📐", "⚙"};
        for (int i = 0; i < railIcons.length; i++) {
            final int tabIndex = i;
            TextView btn = new TextView(context);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(FloaterStyles.dp(context, 30), FloaterStyles.dp(context, 30));
            blp.topMargin = FloaterStyles.dp(context, 3);
            blp.bottomMargin = FloaterStyles.dp(context, 3);
            btn.setLayoutParams(blp);
            btn.setGravity(Gravity.CENTER);
            btn.setText(railIcons[i]);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            btn.setOnClickListener(v -> selectRailTab(tabIndex));
            iconTabsContainer.addView(btn);
            railTabButtons.add(btn);
        }
        iconRail.addView(iconTabsContainer);
        menuLayout.addView(iconRail);

        // Divider 1
        View div1 = new View(context);
        div1.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 1), ViewGroup.LayoutParams.MATCH_PARENT));
        div1.setBackgroundColor(FloaterStyles.ColorBorder);
        menuLayout.addView(div1);

        // LAYER 2: Page 1 (Sub-Panel 1, e.g. AIM ASSIST)
        LinearLayout page1 = new LinearLayout(context);
        LinearLayout.LayoutParams p1Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.08f);
        page1.setLayoutParams(p1Lp);
        page1.setOrientation(LinearLayout.VERTICAL);
        page1.setPadding(FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 6), FloaterStyles.dp(context, 4));

        LinearLayout p1Header = new LinearLayout(context);
        p1Header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        p1Header.setOrientation(LinearLayout.HORIZONTAL);
        p1Header.setGravity(Gravity.CENTER_VERTICAL);
        p1Header.setPadding(FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 2), FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 2));
        p1Header.setOnTouchListener(createDragListener(false));

        page1Title = new TextView(context);
        page1Title.setText("AIM ASSIST");
        page1Title.setTextColor(FloaterStyles.ColorSectionTitle);
        page1Title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        page1Title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        p1Header.addView(page1Title);
        page1.addView(p1Header);

        View p1Div = new View(context);
        LinearLayout.LayoutParams p1dLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 1));
        p1dLp.topMargin = FloaterStyles.dp(context, 2);
        p1dLp.bottomMargin = FloaterStyles.dp(context, 3);
        p1Div.setLayoutParams(p1dLp);
        p1Div.setBackgroundColor(FloaterStyles.ColorBorder);
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
        menuLayout.addView(page1);

        // Divider 2
        View div2 = new View(context);
        div2.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 1), ViewGroup.LayoutParams.MATCH_PARENT));
        div2.setBackgroundColor(FloaterStyles.ColorBorder);
        menuLayout.addView(div2);

        // LAYER 3: Page 2 (Sub-Panel 2, e.g. AIM SETTINGS + Close Button)
        LinearLayout page2 = new LinearLayout(context);
        LinearLayout.LayoutParams p2Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        page2.setLayoutParams(p2Lp);
        page2.setOrientation(LinearLayout.VERTICAL);
        page2.setPadding(FloaterStyles.dp(context, 6), FloaterStyles.dp(context, 4), FloaterStyles.dp(context, 10), FloaterStyles.dp(context, 4));

        LinearLayout p2Header = new LinearLayout(context);
        p2Header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        p2Header.setOrientation(LinearLayout.HORIZONTAL);
        p2Header.setGravity(Gravity.CENTER_VERTICAL);
        p2Header.setPadding(FloaterStyles.dp(context, 4), 0, FloaterStyles.dp(context, 4), 0);
        p2Header.setOnTouchListener(createDragListener(false));

        page2Title = new TextView(context);
        LinearLayout.LayoutParams p2tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        page2Title.setLayoutParams(p2tLp);
        page2Title.setText("AIM SETTINGS");
        page2Title.setTextColor(FloaterStyles.ColorSectionTitle);
        page2Title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        page2Title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        p2Header.addView(page2Title);

        TextView closeBtn = new TextView(context);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 22), FloaterStyles.dp(context, 22)));
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setText("✕");
        closeBtn.setTextColor(AppColors.WHITE);
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
        p2Div.setBackgroundColor(FloaterStyles.ColorBorder);
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
        menuLayout.addView(page2);

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
        highlightActiveRailTab(0);

        // Call genuine native C++ Functions immediately to populate all real features
        try {
            Functions();
            ftKZXivSr(context);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void selectRailTab(int index) {
        activeRailIndex = index;
        highlightActiveRailTab(index);
        refreshActivePages();
    }

    private void highlightActiveRailTab(int index) {
        for (int i = 0; i < railTabButtons.size(); i++) {
            TextView btn = railTabButtons.get(i);
            if (i == index) {
                btn.setBackground(FloaterStyles.createActiveIconRailDrawable(context));
                btn.setTextColor(0xFFFFFFFF);
            } else {
                btn.setBackground(FloaterStyles.createInactiveIconRailDrawable(context));
                btn.setTextColor(0xFF70758F);
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
        } else if (activeRailIndex == 3) {
            p1Cat = "Preferences";
            p2Cat = "System";
            p1Header = "TACTICAL PREFS";
            p2Header = "ENGINE STATUS";
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
        tv.setTextColor(0xFFF0F2F8);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tv.setTypeface(Typeface.DEFAULT);
        row.addView(tv);

        final Switch sw = new Switch(context);
        sw.setChecked(false);
        sw.setScaleX(0.80f);
        sw.setScaleY(0.80f);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ChangesID(id, isChecked ? 1 : 0);
        });
        row.addView(sw);

        categoryViews.get(currentCategory).add(row);
        refreshActivePages();
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
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int pad = FloaterStyles.dp(context, 2);
        row.setPadding(0, pad, 0, pad);

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tv = new TextView(context);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        tv.setText(name);
        tv.setSingleLine(true);
        tv.setMaxLines(1);
        tv.setTextColor(0xFFF0F2F8);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tv.setTypeface(Typeface.DEFAULT);
        topRow.addView(tv);

        final TextView valLabel = new TextView(context);
        valLabel.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        valLabel.setTextColor(FloaterStyles.ColorSectionTitle);
        valLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        valLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        valLabel.setPadding(0, 0, FloaterStyles.dp(context, 4), 0);

        int initVal = isOption ? Math.max(0, Math.min(value, options.length - 1)) : value;
        valLabel.setText(isOption ? options[initVal] : (initVal + getSliderUnit(name)));
        topRow.addView(valLabel);
        row.addView(topRow);

        SeekBar sb = new SeekBar(context);
        sb.setMax(seekMax);
        sb.setProgress(initVal);
        sb.setPadding(FloaterStyles.dp(context, 10), FloaterStyles.dp(context, 2), FloaterStyles.dp(context, 10), FloaterStyles.dp(context, 2));
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
        refreshActivePages();
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
