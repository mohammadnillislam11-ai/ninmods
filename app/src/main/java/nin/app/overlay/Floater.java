package nin.app.overlay;

import nin.app.core.AppColors;
import nin.app.core.Prefs;
import nin.app.core.Utils;
import nin.app.core.RootServices;
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
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Floater {

    private static final String PREF_KEY_MODE_VERSION = "config_mode";
    private static final int VERSION_SAFE = 1;
    private static final int VERSION_BUTRUL = 2;

    private static MSGConnection conn;
    private static Messenger remoteMessenger;
    private static final Messenger myMessenger = new Messenger(new Handler(Looper.getMainLooper()));

    public static native void Functions();
    public static native void ChangesID(int ID, int Value);
    public static native void ftKZXivSr(Context context);
    public static native String getBase64Icon();
    public static native void PxbftKZXivSr(ESPView espView, Canvas canvas, int width, int height);
    public static boolean isLoggedIn() { return true; }
    public static native String getPrefKeyModeVersion();
    public static native String getUiString(int id);
    public static native String getSellerName();

    public static boolean serviceTestQueued;
    public static int SCREEN_WIDTH = 0;
    public static int SCREEN_HEIGHT = 0;

    public static int Width() { return SCREEN_WIDTH; }
    public static int Height() { return SCREEN_HEIGHT; }

    public IBinder onBind(Intent intent) { return null; }

    public static int PrimaryColor = FloaterStyles.PrimaryColor;
    public static int AccentColor = FloaterStyles.AccentColor;
    public static int DarkBackground = FloaterStyles.DarkBackground;
    public static int CardBackground = FloaterStyles.CardBackground;
    public static int TextColor = AppColors.TEXT_PRIMARY;

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
    private LinearLayout secondSectionContainer;
    private TextView secondSectionTitle;
    private final List<TextView> menuCategoryViews = new ArrayList<>();
    final Map<String, List<NativeControlItem>> nativeControlsByCategory = new LinkedHashMap<>();
    final Map<String, List<View>> categoryWidgetsByName = new LinkedHashMap<>();
    String selectedCategoryName = "Aim";

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
        activeFloater = new Floater(targetCtx);
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

    private String str(int resId, String defaultVal) {
        if (context != null) {
            try {
                return context.getString(resId);
            } catch (Exception ignored) { }
        }
        return defaultVal;
    }

    private void initNativeControls() {
        nativeControlsByCategory.clear();
        categoryWidgetsByName.clear();

        int selectedVersion = Prefs.with(context).readInt(PREF_KEY_MODE_VERSION, VERSION_SAFE);
        String nativePrefKey = getPrefKeyModeVersion();
        if (nativePrefKey != null && !nativePrefKey.equals("x")) {
            selectedVersion = Prefs.with(context).readInt(nativePrefKey, selectedVersion);
        }

        List<NativeControlItem> memoryMenu = new ArrayList<>();
        memoryMenu.add(new NativeControlItem(NativeControlItem.ControlType.SWITCH, str(nin.app.R.string.control_enable_functions, "Enable Functions"), 1, 0, 1));
        memoryMenu.add(new NativeControlItem(NativeControlItem.ControlType.SWITCH, str(nin.app.R.string.control_aim_external, "Aim External"), 6, 0, 1));
        if (selectedVersion == VERSION_BUTRUL) {
            memoryMenu.add(new NativeControlItem(NativeControlItem.ControlType.SWITCH, str(nin.app.R.string.control_aim_silent, "Aim Silent"), 8, 0, 1));
        }
        String catAim = str(nin.app.R.string.cat_aim, "Aim");
        nativeControlsByCategory.put(catAim, memoryMenu);
        categoryWidgetsByName.put(catAim, new ArrayList<>());

        if (selectedVersion == VERSION_BUTRUL) {
            List<NativeControlItem> memorySettingMenu = new ArrayList<>();
            memorySettingMenu.add(new NativeControlItem(NativeControlItem.ControlType.SEEKBAR, str(nin.app.R.string.control_silent_aim_target, "Silent Aim Target"), 61, 0, 1));
            memorySettingMenu.add(new NativeControlItem(NativeControlItem.ControlType.SEEKBAR, str(nin.app.R.string.control_target_priority, "Target Priority"), 62, 0, 1));
            memorySettingMenu.add(new NativeControlItem(NativeControlItem.ControlType.SEEKBAR, str(nin.app.R.string.control_aim_fov_radius, "Aim FOV Radius"), 9, 10, 180));
            memorySettingMenu.add(new NativeControlItem(NativeControlItem.ControlType.SEEKBAR, str(nin.app.R.string.control_aim_distance, "Aim Distance"), 10, 10, 130));
            String catAimSetting = str(nin.app.R.string.cat_aim_setting, "Aim Setting");
            nativeControlsByCategory.put(catAimSetting, memorySettingMenu);
            categoryWidgetsByName.put(catAimSetting, new ArrayList<>());
        }

        List<NativeControlItem> espMenu = new ArrayList<>();
        espMenu.add(new NativeControlItem(NativeControlItem.ControlType.SWITCH, str(nin.app.R.string.control_draw_line, "Draw Line"), 11, 0, 1));
        espMenu.add(new NativeControlItem(NativeControlItem.ControlType.SWITCH, str(nin.app.R.string.control_draw_distance, "Draw Distance"), 13, 0, 1));
        espMenu.add(new NativeControlItem(NativeControlItem.ControlType.SWITCH, str(nin.app.R.string.control_draw_aim_fov, "Draw Aim FOV"), 27, 0, 1));
        espMenu.add(new NativeControlItem(NativeControlItem.ControlType.SWITCH, str(nin.app.R.string.control_draw_box, "Draw Box"), 12, 0, 1));
        espMenu.add(new NativeControlItem(NativeControlItem.ControlType.SWITCH, str(nin.app.R.string.control_draw_name, "Draw Name"), 21, 0, 1));
        espMenu.add(new NativeControlItem(NativeControlItem.ControlType.SWITCH, str(nin.app.R.string.control_draw_health, "Draw Health"), 19, 0, 1));
        espMenu.add(new NativeControlItem(NativeControlItem.ControlType.SWITCH, str(nin.app.R.string.control_draw_count, "Draw Count"), 23, 0, 1));
        String catEsp = str(nin.app.R.string.cat_esp, "Esp");
        nativeControlsByCategory.put(catEsp, espMenu);
        categoryWidgetsByName.put(catEsp, new ArrayList<>());

        List<NativeControlItem> espConfig = new ArrayList<>();
        espConfig.add(new NativeControlItem(NativeControlItem.ControlType.SEEKBAR, str(nin.app.R.string.control_esp_color, "ESP Color"), 16, 0, 9));
        espConfig.add(new NativeControlItem(NativeControlItem.ControlType.SEEKBAR, str(nin.app.R.string.control_box_type, "Box Type"), 17, 0, 2));
        espConfig.add(new NativeControlItem(NativeControlItem.ControlType.SEEKBAR, str(nin.app.R.string.control_line_type, "Line Type"), 18, 0, 2));
        espConfig.add(new NativeControlItem(NativeControlItem.ControlType.SEEKBAR, str(nin.app.R.string.control_text_size, "Text Size"), 20, 10, 15));
        espConfig.add(new NativeControlItem(NativeControlItem.ControlType.SEEKBAR, str(nin.app.R.string.control_thickness, "Thickness"), 22, 1, 10));
        String catEspSetting = str(nin.app.R.string.cat_esp_setting, "Esp Setting");
        nativeControlsByCategory.put(catEspSetting, espConfig);
        categoryWidgetsByName.put(catEspSetting, new ArrayList<>());

        List<NativeControlItem> espPositionFix = new ArrayList<>();
        espPositionFix.add(new NativeControlItem(NativeControlItem.ControlType.SEEKBAR, str(nin.app.R.string.control_esp_left_adjust, "ESP Left Adjust"), 30, 0, 100));
        espPositionFix.add(new NativeControlItem(NativeControlItem.ControlType.SEEKBAR, str(nin.app.R.string.control_esp_right_adjust, "ESP Right Adjust"), 31, 0, 100));
        espPositionFix.add(new NativeControlItem(NativeControlItem.ControlType.SWITCH, str(nin.app.R.string.control_reset_adjust, "Reset Adjust"), 32, 0, 1));
        String catPositionFix = str(nin.app.R.string.cat_position_fix, "Position Fix");
        nativeControlsByCategory.put(catPositionFix, espPositionFix);
        categoryWidgetsByName.put(catPositionFix, new ArrayList<>());

        if (!nativeControlsByCategory.containsKey(selectedCategoryName) && !nativeControlsByCategory.isEmpty()) {
            selectedCategoryName = nativeControlsByCategory.keySet().iterator().next();
        }
    }

    public Floater(Context globContext) {
        if (activeFloater != null && activeFloater != this) {
            activeFloater.destroyOverlay();
        }
        activeFloater = this;
        context = globContext.getApplicationContext() != null ? globContext.getApplicationContext() : globContext;
        utils = new Utils(context);
        initNativeControls();
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
        int menuWidth = Math.min(FloaterStyles.dp(context, isLandscape ? 380 : 340), (int) (screenWidth * 0.92f));
        int menuHeight = Math.min(FloaterStyles.dp(context, isLandscape ? 380 : 420), (int) (screenHeight * 0.82f));

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
        logoImagem.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 50), FloaterStyles.dp(context, 50)));
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

        // Main Menu Layout (Standard Clean Margins & Padding)
        menuLayout = new LinearLayout(context);
        menuLayout.setLayoutParams(new LinearLayout.LayoutParams(menuWidth, menuHeight));
        menuLayout.setVisibility(View.GONE);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setBackground(FloaterStyles.createCardBackground(context));
        menuLayout.setPadding(FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 8));

        // 1. Header Block
        LinearLayout headerBlock = new LinearLayout(context);
        headerBlock.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        headerBlock.setOrientation(LinearLayout.VERTICAL);
        headerBlock.setPadding(FloaterStyles.dp(context, 10), FloaterStyles.dp(context, 6), FloaterStyles.dp(context, 10), FloaterStyles.dp(context, 6));
        
        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setShape(GradientDrawable.RECTANGLE);
        headerBg.setCornerRadius(FloaterStyles.dp(context, 12));
        headerBg.setColor(AppColors.PANEL_HEADER_START);
        headerBg.setStroke(FloaterStyles.dp(context, 1), AppColors.CYAN_STROKE);
        headerBlock.setBackground(headerBg);

        // Header Top Row (Logo, Title, Minimize & Close)
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 38)));
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView menuLogo = new ImageView(context);
        menuLogo.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 28), FloaterStyles.dp(context, 28)));
        menuLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (base64Icon != null && !base64Icon.isEmpty()) {
            Bitmap b = FloaterStyles.base64ToBitmap(base64Icon);
            if (b != null) menuLogo.setImageBitmap(b);
            else menuLogo.setImageBitmap(FloaterStyles.createDefaultIcon());
        } else {
            menuLogo.setImageBitmap(FloaterStyles.createDefaultIcon());
        }
        headerRow.addView(menuLogo);

        TextView brand = new TextView(context);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        brandLp.leftMargin = FloaterStyles.dp(context, 8);
        brand.setLayoutParams(brandLp);
        brand.setText(str(nin.app.R.string.app_name, "NINMODS"));
        brand.setTextColor(AppColors.PRIMARY);
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        headerRow.addView(brand);

        // Minimize Button
        TextView minBtn = new TextView(context);
        LinearLayout.LayoutParams minLp = new LinearLayout.LayoutParams(FloaterStyles.dp(context, 28), FloaterStyles.dp(context, 28));
        minLp.rightMargin = FloaterStyles.dp(context, 6);
        minBtn.setLayoutParams(minLp);
        minBtn.setText("—");
        minBtn.setTextColor(AppColors.TEXT_WHITE);
        minBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        minBtn.setTypeface(Typeface.DEFAULT_BOLD);
        minBtn.setGravity(Gravity.CENTER);
        GradientDrawable minBg = new GradientDrawable();
        minBg.setColor(AppColors.BTN_HIDE_BG);
        minBg.setCornerRadius(FloaterStyles.dp(context, 8));
        minBg.setStroke(FloaterStyles.dp(context, 1), AppColors.CYAN_STROKE);
        minBtn.setBackground(minBg);
        minBtn.setOnClickListener(v -> closeMenu());
        headerRow.addView(minBtn);

        // Close Button
        TextView closeBtn = new TextView(context);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(FloaterStyles.dp(context, 28), FloaterStyles.dp(context, 28));
        closeBtn.setLayoutParams(closeLp);
        closeBtn.setText("✕");
        closeBtn.setTextColor(AppColors.TEXT_WHITE);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(AppColors.BTN_HIDE_BG);
        closeBg.setCornerRadius(FloaterStyles.dp(context, 8));
        closeBg.setStroke(FloaterStyles.dp(context, 1), AppColors.CYAN_STROKE);
        closeBtn.setBackground(closeBg);
        closeBtn.setOnClickListener(v -> destroyOverlay());
        headerRow.addView(closeBtn);

        headerBlock.addView(headerRow);

        // Status Sub-Row
        LinearLayout statusRow = new LinearLayout(context);
        statusRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setPadding(0, FloaterStyles.dp(context, 4), 0, 0);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView engineStatus = new TextView(context);
        engineStatus.setText("• ENGINE: ONLINE (RT / IPC)");
        engineStatus.setTextColor(AppColors.ONLINE_GREEN);
        engineStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        engineStatus.setTypeface(Typeface.DEFAULT_BOLD);
        statusRow.addView(engineStatus);
        headerBlock.addView(statusRow);

        menuLayout.addView(headerBlock);

        // 2. Category Tab Bar (Horizontal Scroll)
        HorizontalScrollView tabScroller = new HorizontalScrollView(context);
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 42));
        tabLp.topMargin = FloaterStyles.dp(context, 6);
        tabScroller.setLayoutParams(tabLp);
        tabScroller.setHorizontalScrollBarEnabled(false);
        tabScroller.setOverScrollMode(View.OVER_SCROLL_NEVER);

        firstSectionMenuContainer = new LinearLayout(context);
        firstSectionMenuContainer.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        firstSectionMenuContainer.setOrientation(LinearLayout.HORIZONTAL);
        firstSectionMenuContainer.setGravity(Gravity.CENTER_VERTICAL);
        tabScroller.addView(firstSectionMenuContainer);
        menuLayout.addView(tabScroller);

        rebuildFirstSectionMenus();

        // 3. Main Content Area (Feature List inside Body)
        LinearLayout mainPanel = new LinearLayout(context);
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        mainParams.topMargin = FloaterStyles.dp(context, 6);
        mainPanel.setLayoutParams(mainParams);
        mainPanel.setOrientation(LinearLayout.VERTICAL);
        mainPanel.setBackground(FloaterStyles.createPanelBackground(context, 14));
        mainPanel.setPadding(FloaterStyles.dp(context, 10), FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 10), FloaterStyles.dp(context, 8));

        secondSectionTitle = new TextView(context);
        secondSectionTitle.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 26)));
        secondSectionTitle.setText("AIM CONTROLS");
        secondSectionTitle.setTextColor(AppColors.CYAN_LIGHT);
        secondSectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        secondSectionTitle.setGravity(Gravity.CENTER_VERTICAL);
        secondSectionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        mainPanel.addView(secondSectionTitle);

        mainScroll = new ScrollView(context);
        mainScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        mainScroll.setVerticalScrollBarEnabled(false);

        funcsListLayout = new LinearLayout(context);
        funcsListLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        funcsListLayout.setOrientation(LinearLayout.VERTICAL);
        secondSectionContainer = funcsListLayout;
        mainScroll.addView(funcsListLayout);
        mainPanel.addView(mainScroll);

        // 4. Footer Action Buttons
        LinearLayout footerButtons = new LinearLayout(context);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 40));
        footerParams.topMargin = FloaterStyles.dp(context, 6);
        footerButtons.setLayoutParams(footerParams);
        footerButtons.setOrientation(LinearLayout.HORIZONTAL);
        footerButtons.setGravity(Gravity.CENTER_VERTICAL);

        Button hideBtn = new Button(context);
        LinearLayout.LayoutParams hideParams = new LinearLayout.LayoutParams(0, FloaterStyles.dp(context, 38), 1.0f);
        hideParams.rightMargin = FloaterStyles.dp(context, 6);
        hideBtn.setLayoutParams(hideParams);
        hideBtn.setText(str(nin.app.R.string.btn_hide, "MINIMIZE"));
        hideBtn.setAllCaps(false);
        hideBtn.setTextColor(AppColors.TEXT_WHITE);
        hideBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hideBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable hideBg = new GradientDrawable();
        hideBg.setColor(AppColors.BTN_HIDE_BG);
        hideBg.setCornerRadius(FloaterStyles.dp(context, 8));
        hideBg.setStroke(FloaterStyles.dp(context, 1), AppColors.CYAN_STROKE);
        hideBtn.setBackground(hideBg);
        hideBtn.setOnClickListener(v -> closeMenu());
        footerButtons.addView(hideBtn);

        Button closeBottomBtn = new Button(context);
        LinearLayout.LayoutParams closeBottomParams = new LinearLayout.LayoutParams(0, FloaterStyles.dp(context, 38), 1.0f);
        closeBottomBtn.setLayoutParams(closeBottomParams);
        closeBottomBtn.setText(str(nin.app.R.string.btn_close, "CLOSE"));
        closeBottomBtn.setAllCaps(false);
        closeBottomBtn.setTextColor(AppColors.TEXT_WHITE);
        closeBottomBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        closeBottomBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable closeBottomBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{AppColors.BTN_CLOSE_START, AppColors.BTN_CLOSE_END});
        closeBottomBg.setCornerRadius(FloaterStyles.dp(context, 8));
        closeBottomBg.setStroke(FloaterStyles.dp(context, 1), AppColors.CYAN_STROKE);
        closeBottomBtn.setBackground(closeBottomBg);
        closeBottomBtn.setOnClickListener(v -> destroyOverlay());
        footerButtons.addView(closeBottomBtn);

        mainPanel.addView(footerButtons);
        menuLayout.addView(mainPanel);

        populateSecondSection(selectedCategoryName);

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

        rootFrame.setOnTouchListener(createOnTouchListener());
        logoImagem.setOnTouchListener(createOnTouchListener());
        logoImagem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openMenu();
            }
        });

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Functions();
                    ftKZXivSr(context);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }, 3000);
    }

    public void addWidgetToCurrentCategory(View widget) {
        if (widget == null) return;
        List<View> widgets = categoryWidgetsByName.get(selectedCategoryName);
        if (widgets == null) {
            widgets = new ArrayList<>();
            categoryWidgetsByName.put(selectedCategoryName, widgets);
        }
        widgets.add(widget);
        refreshSelectedCategoryWidgets(selectedCategoryName);
    }

    private void refreshSelectedCategoryWidgets(String categoryName) {
        if (secondSectionContainer == null || categoryName == null || !categoryName.equals(selectedCategoryName)) {
            return;
        }
        populateSecondSection(categoryName);
    }

    private View createCategoryMenuItem(final String categoryName) {
        final TextView item = new TextView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                FloaterStyles.dp(context, 32)
        );
        params.setMargins(0, 0, FloaterStyles.dp(context, 6), 0);
        item.setLayoutParams(params);
        item.setPadding(FloaterStyles.dp(context, 12), 0, FloaterStyles.dp(context, 12), 0);
        item.setGravity(Gravity.CENTER);
        item.setText(categoryName);
        item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        item.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        item.setTag(categoryName);
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectCategory(categoryName);
            }
        });
        return item;
    }

    private void populateSecondSection(String categoryName) {
        if (secondSectionContainer == null) return;
        secondSectionContainer.removeAllViews();
        if (secondSectionTitle != null) {
            secondSectionTitle.setText(categoryName != null ? categoryName.toUpperCase(Locale.ROOT) : "MENU");
        }

        List<NativeControlItem> nativeControls = nativeControlsByCategory.get(categoryName);
        if (nativeControls != null) {
            for (NativeControlItem item : nativeControls) {
                if (item.type == NativeControlItem.ControlType.SEEKBAR) {
                    secondSectionContainer.addView(WidgetFactory.createDetailSeekBar(
                            context, item.label, item.id, item.min, item.max,
                            (id, value) -> ChangesID(id, value)
                    ));
                } else {
                    secondSectionContainer.addView(WidgetFactory.createDetailSwitch(
                            context, item.label, item.id,
                            (id, value) -> ChangesID(id, value)
                    ));
                }
            }
        }

        List<View> customWidgets = categoryWidgetsByName.get(categoryName);
        if (customWidgets != null) {
            for (View view : customWidgets) {
                if (view.getParent() instanceof ViewGroup) {
                    ((ViewGroup) view.getParent()).removeView(view);
                }
                secondSectionContainer.addView(view);
            }
        }
    }

    public void rebuildFirstSectionMenus() {
        if (firstSectionMenuContainer == null) return;
        firstSectionMenuContainer.removeAllViews();
        menuCategoryViews.clear();

        for (String categoryName : nativeControlsByCategory.keySet()) {
            View itemView = createCategoryMenuItem(categoryName);
            firstSectionMenuContainer.addView(itemView);
            if (itemView instanceof TextView) {
                menuCategoryViews.add((TextView) itemView);
            }
        }
        highlightSelectedCategory(selectedCategoryName);
    }

    private void highlightSelectedCategory(String selectedCategory) {
        for (TextView tab : menuCategoryViews) {
            Object tag = tab.getTag();
            boolean isSelected = tag != null && tag.toString().equals(selectedCategory);
            GradientDrawable tabBg = new GradientDrawable();
            tabBg.setCornerRadius(FloaterStyles.dp(context, 8));
            if (isSelected) {
                tabBg.setColor(AppColors.TAB_SELECTED_BG);
                tabBg.setStroke(FloaterStyles.dp(context, 1), AppColors.PRIMARY);
                tab.setTextColor(AppColors.TEXT_WHITE);
            } else {
                tabBg.setColor(AppColors.TAB_UNSELECTED_BG);
                tabBg.setStroke(FloaterStyles.dp(context, 1), AppColors.withAlpha(AppColors.CYAN_STROKE, 0x33));
                tab.setTextColor(AppColors.TEXT_DIM);
            }
            tab.setBackground(tabBg);
        }
    }

    void selectCategory(String categoryName) {
        selectedCategoryName = categoryName;
        highlightSelectedCategory(categoryName);
        populateSecondSection(categoryName);
    }

    private String normalizeCategoryName(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    String findExistingCategoryKeyIgnoreCase(String rawName) {
        String normalizedRaw = normalizeCategoryName(rawName);
        for (String key : nativeControlsByCategory.keySet()) {
            if (key != null && normalizeCategoryName(key).equals(normalizedRaw)) {
                return key;
            }
        }
        return null;
    }

    private View.OnTouchListener createOnTouchListener() {
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
                        windowManager.updateViewLayout(rootFrame, windowManagerParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                        int xDiff = (int) (event.getRawX() - initialTouchX);
                        int yDiff = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(xDiff) < 10 && Math.abs(yDiff) < 10) {
                            if (v == logoImagem) {
                                openMenu();
                            }
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

    // Static bridge methods called by JNI
    public static void addCategory(String name) {
        FloaterBridge.addCategory(activeFloater, name, 32);
    }

    public static void addCategory(String name, int categoryHeightDp) {
        FloaterBridge.addCategory(activeFloater, name, categoryHeightDp);
    }

    public static void addSwitch(final String name, final int ID) {
        FloaterBridge.addSwitch(activeFloater, context, name, ID, 40);
    }

    public static void addSwitch(final String name, final int ID, int switchHeightDp) {
        FloaterBridge.addSwitch(activeFloater, context, name, ID, switchHeightDp);
    }

    public static void addCheckBox(final String name, final int ID) {
        FloaterBridge.addCheckBox(activeFloater, context, name, ID, 40);
    }

    public static void addCheckBox(final String name, final int ID, int heightDp) {
        FloaterBridge.addCheckBox(activeFloater, context, name, ID, heightDp);
    }

    public static void addRadioGroup(final String name, final String[] options, final int defaultIndex, final int ID) {
        FloaterBridge.addRadioGroup(activeFloater, context, name, options, defaultIndex, ID);
    }

    public static void addSeekBar(final String name, int value, int max, final String type, final int ID) {
        FloaterBridge.addSeekBar(activeFloater, context, name, value, max, type, ID);
    }

    public static void Funçoes(String pkg, String lib, String offset, String hex) {
        FloaterBridge.sendFunctionMessage(context, remoteMessenger, myMessenger, conn, pkg, lib, offset, hex);
    }

    public static class MSGConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName name, IBinder service) {
            remoteMessenger = new Messenger(service);
            if (serviceTestQueued) serviceTestQueued = false;
        }

        public void onServiceDisconnected(ComponentName name) {
            remoteMessenger = null;
        }
    }

    public static final void setRemoteMessenger(Messenger messenger) {
        remoteMessenger = messenger;
    }
}
