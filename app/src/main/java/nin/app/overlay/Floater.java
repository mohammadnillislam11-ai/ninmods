package nin.app.overlay;

import nin.app.core.AppColors;
import nin.app.core.Prefs;
import nin.app.core.Utils;
import nin.app.core.RootServices;
import nin.app.graphics.ESPView;
import nin.app.graphics.ParticleView;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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
    static Floater activeFloater;

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
    String selectedCategoryName = "Memory";
    private ParticleView snowParticleView;

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
        context = globContext;
        utils = new Utils(context);
        activeFloater = this;
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
        int maxMenuWidth = isLandscape ? (int) (screenWidth * 0.55f) : (int) (screenWidth * 0.92f);
        int maxMenuHeight = isLandscape ? (int) (screenHeight * 0.8f) : (int) (screenHeight * 0.7f);

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
        collapsedRelative.setAlpha(0.95f);
        collapsedRelative.setVisibility(View.VISIBLE);

        String base64Icon = getBase64Icon();
        logoImagem = new ImageView(context);
        logoImagem.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 55), FloaterStyles.dp(context, 45)));
        if (base64Icon != null && !base64Icon.isEmpty()) {
            Bitmap iconBitmap = FloaterStyles.base64ToBitmap(base64Icon);
            if (iconBitmap != null) logoImagem.setImageBitmap(iconBitmap);
            else logoImagem.setImageBitmap(FloaterStyles.createDefaultIcon());
        } else {
            logoImagem.setImageBitmap(FloaterStyles.createDefaultIcon());
        }

        menuLayout = new LinearLayout(context);
        menuLayout.setLayoutParams(new LinearLayout.LayoutParams(
                Math.min(FloaterStyles.dp(context, isLandscape ? 420 : 360), maxMenuWidth),
                maxMenuHeight));
        menuLayout.setVisibility(View.GONE);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setBackground(FloaterStyles.createCardBackground(context));
        menuLayout.setPadding(FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 8));

        snowParticleView = new ParticleView(context);
        RelativeLayout.LayoutParams snowParams = new RelativeLayout.LayoutParams(
                Math.min(FloaterStyles.dp(context, isLandscape ? 560 : 430), maxMenuWidth),
                maxMenuHeight
        );
        snowParticleView.setLayoutParams(snowParams);
        snowParticleView.setVisibility(View.GONE);
        snowParticleView.setAlpha(0.72f);
        snowParticleView.setClickable(false);
        snowParticleView.setFocusable(false);

        // Header block
        LinearLayout headerBlock = new LinearLayout(context);
        headerBlock.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        headerBlock.setOrientation(LinearLayout.VERTICAL);
        headerBlock.setPadding(FloaterStyles.dp(context, 10), FloaterStyles.dp(context, 8), FloaterStyles.dp(context, 10), FloaterStyles.dp(context, 8));
        GradientDrawable headerBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.PANEL_HEADER_START, AppColors.PANEL_HEADER_END}
        );
        headerBg.setCornerRadius(FloaterStyles.dp(context, 20));
        headerBg.setStroke(FloaterStyles.dp(context, 1), AppColors.withAlpha(AppColors.CYAN_STROKE, 0x66));
        headerBlock.setBackground(headerBg);

        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 44)));
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView menuLogo = new ImageView(context);
        menuLogo.setLayoutParams(new LinearLayout.LayoutParams(FloaterStyles.dp(context, 36), FloaterStyles.dp(context, 36)));
        menuLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        String b64 = getBase64Icon();
        if (b64 != null && !b64.isEmpty()) {
            Bitmap b = FloaterStyles.base64ToBitmap(b64);
            if (b != null) menuLogo.setImageBitmap(b);
            else menuLogo.setImageBitmap(FloaterStyles.createDefaultIcon());
        } else {
            menuLogo.setImageBitmap(FloaterStyles.createDefaultIcon());
        }
        headerRow.addView(menuLogo);

        LinearLayout brandContainer = new LinearLayout(context);
        brandContainer.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        brandContainer.setOrientation(LinearLayout.VERTICAL);
        brandContainer.setPadding(FloaterStyles.dp(context, 8), 0, 0, 0);

        TextView brand = new TextView(context);
        brand.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        brand.setText(str(nin.app.R.string.app_name, "NINMODS"));
        brand.setTextColor(AppColors.TEXT_WHITE);
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        brandContainer.addView(brand);
        headerRow.addView(brandContainer);

        Button closeBtn = new Button(context);
        LinearLayout.LayoutParams safeParams = new LinearLayout.LayoutParams(FloaterStyles.dp(context, 62), FloaterStyles.dp(context, 26));
        safeParams.setMargins(0, 0, FloaterStyles.dp(context, 6), 0);
        closeBtn.setLayoutParams(safeParams);
        closeBtn.setText(str(nin.app.R.string.btn_safe, "SAFE"));
        closeBtn.setTextColor(AppColors.TEXT_WHITE);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
        closeBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        GradientDrawable cBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{AppColors.BTN_SAFE_START, AppColors.BTN_SAFE_END});
        cBg.setCornerRadius(FloaterStyles.dp(context, 13));
        cBg.setStroke(FloaterStyles.dp(context, 1), AppColors.withAlpha(AppColors.CYAN_STROKE, 0x88));
        closeBtn.setBackground(cBg);
        headerRow.addView(closeBtn);

        TextView closeHeaderIcon = new TextView(context);
        LinearLayout.LayoutParams closeIconParams = new LinearLayout.LayoutParams(
                FloaterStyles.dp(context, 26),
                FloaterStyles.dp(context, 26)
        );
        closeHeaderIcon.setLayoutParams(closeIconParams);
        closeHeaderIcon.setText("✕");
        closeHeaderIcon.setTextColor(AppColors.TEXT_WHITE);
        closeHeaderIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        closeHeaderIcon.setTypeface(Typeface.DEFAULT_BOLD);
        closeHeaderIcon.setGravity(Gravity.CENTER);
        GradientDrawable closeIconBg = new GradientDrawable();
        closeIconBg.setShape(GradientDrawable.OVAL);
        closeIconBg.setColor(AppColors.BTN_HIDE_BG);
        closeIconBg.setStroke(FloaterStyles.dp(context, 1), AppColors.CYAN_STROKE);
        closeHeaderIcon.setBackground(closeIconBg);
        closeHeaderIcon.setOnClickListener(v -> FloaterAnimations.animateMenuClose(menuLayout, collapsedRelative, null, null));
        headerRow.addView(closeHeaderIcon);

        headerBlock.addView(headerRow);

        LinearLayout statusRow = new LinearLayout(context);
        statusRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setPadding(0, FloaterStyles.dp(context, 6), 0, 0);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView safeMode = new TextView(context);
        safeMode.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        safeMode.setText(str(nin.app.R.string.status_safe_mode, "SAFE MODE"));
        safeMode.setTextColor(AppColors.TEXT_SAFE_MODE);
        safeMode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        safeMode.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        statusRow.addView(safeMode);

        TextView online = new TextView(context);
        online.setText("ONLINE");
        online.setTextColor(AppColors.ONLINE_GREEN);
        online.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        online.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        statusRow.addView(online);
        headerBlock.addView(statusRow);
        menuLayout.addView(headerBlock);

        // Category Tab Bar
        HorizontalScrollView tabScroller = new HorizontalScrollView(context);
        tabScroller.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 46)));
        tabScroller.setHorizontalScrollBarEnabled(false);
        tabScroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        tabScroller.setPadding(0, FloaterStyles.dp(context, 4), 0, FloaterStyles.dp(context, 4));

        firstSectionMenuContainer = new LinearLayout(context);
        firstSectionMenuContainer.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        firstSectionMenuContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabScroller.addView(firstSectionMenuContainer);
        menuLayout.addView(tabScroller);
        rebuildFirstSectionMenus();

        // Main Content Area
        LinearLayout mainPanel = new LinearLayout(context);
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        mainParams.topMargin = FloaterStyles.dp(context, 6);
        mainPanel.setLayoutParams(mainParams);
        mainPanel.setOrientation(LinearLayout.VERTICAL);
        mainPanel.setBackground(FloaterStyles.createPanelBackground(context, 20));
        mainPanel.setPadding(FloaterStyles.dp(context, 12), FloaterStyles.dp(context, 10), FloaterStyles.dp(context, 12), FloaterStyles.dp(context, 10));

        secondSectionTitle = new TextView(context);
        secondSectionTitle.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, FloaterStyles.dp(context, 30)));
        secondSectionTitle.setText("SAFE MENU");
        GradientDrawable titleBarBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{AppColors.TITLE_BAR_START, AppColors.TITLE_BAR_END});
        titleBarBg.setCornerRadius(FloaterStyles.dp(context, 15));
        titleBarBg.setStroke(FloaterStyles.dp(context, 1), AppColors.withAlpha(AppColors.CYAN_STROKE, 0x77));
        secondSectionTitle.setBackground(titleBarBg);
        secondSectionTitle.setTextColor(AppColors.CYAN_LIGHT);
        secondSectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        secondSectionTitle.setGravity(Gravity.CENTER);
        secondSectionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        mainPanel.addView(secondSectionTitle);

        mainScroll = new ScrollView(context);
        mainScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        mainScroll.setVerticalScrollBarEnabled(false);

        funcsListLayout = new LinearLayout(context);
        funcsListLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        funcsListLayout.setOrientation(LinearLayout.VERTICAL);
        secondSectionContainer = funcsListLayout;
        mainScroll.addView(funcsListLayout);
        mainPanel.addView(mainScroll);
        menuLayout.addView(mainPanel);

        populateSecondSection(selectedCategoryName);

        LinearLayout collapsedRow = new LinearLayout(context);
        collapsedRow.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        collapsedRow.setOrientation(LinearLayout.HORIZONTAL);
        collapsedRow.setGravity(Gravity.CENTER_VERTICAL);
        collapsedRow.addView(logoImagem);

        TextView hideFloaterBtn = new TextView(context);
        LinearLayout.LayoutParams hideLp = new LinearLayout.LayoutParams(
                FloaterStyles.dp(context, 24),
                FloaterStyles.dp(context, 24)
        );
        hideLp.setMargins(FloaterStyles.dp(context, 6), 0, 0, 0);
        hideFloaterBtn.setLayoutParams(hideLp);
        hideFloaterBtn.setText("✕");
        hideFloaterBtn.setTextColor(AppColors.TEXT_WHITE);
        hideFloaterBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hideFloaterBtn.setTypeface(Typeface.DEFAULT_BOLD);
        hideFloaterBtn.setGravity(Gravity.CENTER);
        GradientDrawable hBg = new GradientDrawable();
        hBg.setShape(GradientDrawable.OVAL);
        hBg.setColor(AppColors.BTN_HIDE_BG);
        hBg.setStroke(FloaterStyles.dp(context, 1), AppColors.CYAN_STROKE);
        hideFloaterBtn.setBackground(hBg);
        hideFloaterBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collapsedRelative.setVisibility(View.GONE);
                menuLayout.setVisibility(View.GONE);
                if (rootFrame != null) {
                    rootFrame.setVisibility(View.GONE);
                }
            }
        });
        collapsedRow.addView(hideFloaterBtn);

        collapsedRelative.addView(collapsedRow);
        rootRelative.addView(collapsedRelative);
        rootRelative.addView(menuLayout);
        rootRelative.addView(snowParticleView);
        rootFrame.addView(rootRelative);
        windowManager.addView(rootFrame, windowManagerParams);

        rootFrame.setOnTouchListener(createOnTouchListener());
        logoImagem.setOnTouchListener(createOnTouchListener());
        logoImagem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FloaterAnimations.animateMenuOpen(collapsedRelative, menuLayout, null);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    view.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100)
                            .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(100));
                }
            }
        });

        FloaterAnimations.startCollapsedPulse(logoImagem);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Functions();
                ftKZXivSr(context);
            }
        }, 5000);
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
                FloaterStyles.dp(context, 34)
        );
        params.setMargins(0, 0, FloaterStyles.dp(context, 6), 0);
        item.setLayoutParams(params);
        item.setPadding(FloaterStyles.dp(context, 16), 0, FloaterStyles.dp(context, 16), 0);
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

    void rebuildFirstSectionMenus() {
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
            tabBg.setCornerRadius(FloaterStyles.dp(context, 17));
            if (isSelected) {
                tabBg.setColor(AppColors.TAB_SELECTED_BG);
                tabBg.setStroke(FloaterStyles.dp(context, 1), AppColors.CYAN_GLOW);
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
                                FloaterAnimations.animateMenuOpen(collapsedRelative, menuLayout, null);
                            }
                        }
                        return true;
                }
                return false;
            }
        };
    }

    public void DrawCanvas() {
        int layoutFlag = getLayoutType();
        drawView = new ESPView(context);
        windowManagerDrawViewParams = new WindowManager.LayoutParams(-1, -1, layoutFlag, 56, -3);
        windowManagerDrawViewParams.gravity = Gravity.TOP | Gravity.START;
        windowManagerDrawViewParams.x = 0;
        windowManagerDrawViewParams.y = 0;
        windowManagerDrawViewParams.alpha = 0.8f;
        windowManager.addView(drawView, windowManagerDrawViewParams);
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
