package nin.app.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import nin.app.R;
import nin.app.overlay.Floater;
import nin.app.overlay.FloaterStyles;

public class DashboardActivity extends Activity {

    static {
        System.loadLibrary("ninmods");
    }

    public static final int MODE_SAFE = 1;
    public static final int MODE_EXTREME = 2;
    public static final String PREF_KEY_MODE = "config_mode";

    private FrameLayout rootFrame;
    private LinearLayout mainContentContainer;
    private FrameLayout drawerOverlay;
    private LinearLayout sidebarDrawer;
    private boolean isDrawerOpen = false;

    private int activePage = 0; // 0: Dashboard, 1: Profiles, 2: Launcher, 3: Floater, 4: Telemetry
    private int currentMode = MODE_SAFE;

    private TextView navItemDashboard;
    private TextView navItemProfiles;
    private TextView navItemLauncher;
    private TextView navItemFloater;
    private TextView navItemTelemetry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currentMode = Prefs.with(this).readInt(PREF_KEY_MODE, MODE_SAFE);
        setModeNative(currentMode);

        buildDashboardInterface();
    }

    private void buildDashboardInterface() {
        rootFrame = new FrameLayout(this);
        rootFrame.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        rootFrame.setBackground(createScreenGradient());

        LinearLayout baseLayout = new LinearLayout(this);
        baseLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        baseLayout.setOrientation(LinearLayout.VERTICAL);

        baseLayout.addView(createHeaderBar());

        ScrollView contentScrollView = new ScrollView(this);
        contentScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));
        contentScrollView.setVerticalScrollBarEnabled(false);

        mainContentContainer = new LinearLayout(this);
        mainContentContainer.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        mainContentContainer.setOrientation(LinearLayout.VERTICAL);
        mainContentContainer.setPadding(dp(16), dp(14), dp(16), dp(24));

        contentScrollView.addView(mainContentContainer);
        baseLayout.addView(contentScrollView);

        rootFrame.addView(baseLayout);

        createSidebarDrawer();

        setContentView(rootFrame);

        renderActivePage();
    }

    private View createHeaderBar() {
        LinearLayout header = new LinearLayout(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
        ));
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(8), dp(16), dp(8));

        GradientDrawable headerBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_SURFACE, AppColors.MAGENTA_CARD}
        );
        headerBg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        header.setBackground(headerBg);

        TextView hamburgerBtn = new TextView(this);
        hamburgerBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        hamburgerBtn.setText("☰");
        hamburgerBtn.setTextColor(AppColors.MAGENTA_PRIMARY);
        hamburgerBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        hamburgerBtn.setGravity(Gravity.CENTER);
        hamburgerBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(AppColors.MAGENTA_CHIP);
        btnBg.setCornerRadius(dp(12));
        btnBg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        hamburgerBtn.setBackground(btnBg);
        hamburgerBtn.setOnClickListener(v -> toggleDrawer());
        header.addView(hamburgerBtn);

        LinearLayout brandBox = new LinearLayout(this);
        brandBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        brandBox.setOrientation(LinearLayout.VERTICAL);
        brandBox.setPadding(dp(12), 0, dp(12), 0);

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        brandBox.addView(title);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(2), 0, 0);

        View dot = new View(this);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(7), dp(7)));
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(AppColors.STATUS_BADGE_GREEN);
        dot.setBackground(dotBg);
        statusRow.addView(dot);

        TextView statusText = new TextView(this);
        statusText.setText(" " + getString(R.string.status_online_root));
        statusText.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusRow.addView(statusText);

        brandBox.addView(statusRow);
        header.addView(brandBox);

        Button floaterBtn = new Button(this);
        floaterBtn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        floaterBtn.setText("MOD MENU");
        floaterBtn.setTextColor(AppColors.WHITE);
        floaterBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        floaterBtn.setTypeface(Typeface.DEFAULT_BOLD);
        floaterBtn.setAllCaps(false);
        GradientDrawable fBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END}
        );
        fBg.setCornerRadius(dp(10));
        fBg.setStroke(dp(1), AppColors.MAGENTA_GLOW);
        floaterBtn.setBackground(fBg);
        floaterBtn.setPadding(dp(14), 0, dp(14), 0);
        floaterBtn.setOnClickListener(v -> launchFloater());
        header.addView(floaterBtn);

        return header;
    }

    private void createSidebarDrawer() {
        drawerOverlay = new FrameLayout(this);
        drawerOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        drawerOverlay.setBackgroundColor(AppColors.withAlpha(AppColors.BLACK, 175));
        drawerOverlay.setVisibility(View.GONE);
        drawerOverlay.setOnClickListener(v -> closeDrawer());

        sidebarDrawer = new LinearLayout(this);
        FrameLayout.LayoutParams drawerLp = new FrameLayout.LayoutParams(
                dp(280),
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        drawerLp.gravity = Gravity.START;
        sidebarDrawer.setLayoutParams(drawerLp);
        sidebarDrawer.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable drawerBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{AppColors.MAGENTA_SURFACE, AppColors.MAGENTA_SCREEN_BG}
        );
        drawerBg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        sidebarDrawer.setBackground(drawerBg);

        LinearLayout sideHeader = new LinearLayout(this);
        sideHeader.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        sideHeader.setOrientation(LinearLayout.VERTICAL);
        sideHeader.setPadding(dp(20), dp(36), dp(20), dp(20));

        GradientDrawable sideHeaderBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_SIDEBAR_HEADER, AppColors.MAGENTA_CARD}
        );
        sideHeaderBg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        sideHeader.setBackground(sideHeaderBg);

        TextView drawerLogo = new TextView(this);
        drawerLogo.setText(getString(R.string.app_name));
        drawerLogo.setTextColor(AppColors.MAGENTA_PRIMARY);
        drawerLogo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        drawerLogo.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        sideHeader.addView(drawerLogo);

        TextView drawerSub = new TextView(this);
        drawerSub.setText(getString(R.string.dashboard_subtitle));
        drawerSub.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        drawerSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        drawerSub.setPadding(0, dp(4), 0, 0);
        sideHeader.addView(drawerSub);

        sidebarDrawer.addView(sideHeader);

        LinearLayout navList = new LinearLayout(this);
        navList.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));
        navList.setOrientation(LinearLayout.VERTICAL);
        navList.setPadding(dp(12), dp(16), dp(12), dp(16));

        navItemDashboard = createNavItem(getString(R.string.nav_dashboard), 0);
        navItemProfiles = createNavItem(getString(R.string.nav_engines), 1);
        navItemLauncher = createNavItem(getString(R.string.nav_game_launcher), 2);
        navItemFloater = createNavItem(getString(R.string.nav_floating_menu), 3);
        navItemTelemetry = createNavItem(getString(R.string.nav_device_info), 4);

        navList.addView(navItemDashboard);
        navList.addView(navItemProfiles);
        navList.addView(navItemLauncher);
        navList.addView(navItemFloater);
        navList.addView(navItemTelemetry);

        sidebarDrawer.addView(navList);

        LinearLayout drawerFooter = new LinearLayout(this);
        drawerFooter.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        drawerFooter.setOrientation(LinearLayout.VERTICAL);
        drawerFooter.setPadding(dp(16), dp(12), dp(16), dp(20));

        TextView closeDrawerBtn = new TextView(this);
        closeDrawerBtn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
        ));
        closeDrawerBtn.setText("←  " + getString(R.string.btn_close));
        closeDrawerBtn.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        closeDrawerBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        closeDrawerBtn.setGravity(Gravity.CENTER);
        closeDrawerBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(AppColors.MAGENTA_CHIP);
        closeBg.setCornerRadius(dp(10));
        closeDrawerBtn.setBackground(closeBg);
        closeDrawerBtn.setOnClickListener(v -> closeDrawer());
        drawerFooter.addView(closeDrawerBtn);

        sidebarDrawer.addView(drawerFooter);

        drawerOverlay.addView(sidebarDrawer);
        rootFrame.addView(drawerOverlay);
    }

    private TextView createNavItem(String title, final int pageIndex) {
        TextView item = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        lp.setMargins(0, 0, 0, dp(6));
        item.setLayoutParams(lp);
        item.setText(title);
        item.setTextColor(AppColors.WHITE);
        item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(16), 0, dp(16), 0);
        item.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        item.setOnClickListener(v -> {
            activePage = pageIndex;
            closeDrawer();
            renderActivePage();
        });

        return item;
    }

    private void updateNavItemStyles() {
        TextView[] items = new TextView[]{navItemDashboard, navItemProfiles, navItemLauncher, navItemFloater, navItemTelemetry};
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) continue;
            if (i == activePage) {
                GradientDrawable activeBg = new GradientDrawable();
                activeBg.setColor(AppColors.MAGENTA_ITEM_ACTIVE);
                activeBg.setCornerRadius(dp(10));
                activeBg.setStroke(dp(1), AppColors.MAGENTA_PRIMARY);
                items[i].setBackground(activeBg);
                items[i].setTextColor(AppColors.MAGENTA_LIGHT);
                items[i].setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                items[i].setBackground(null);
                items[i].setTextColor(AppColors.WHITE);
                items[i].setTypeface(Typeface.NORMAL);
            }
        }
    }

    private void renderActivePage() {
        updateNavItemStyles();
        mainContentContainer.removeAllViews();

        switch (activePage) {
            case 0:
                renderDashboardPage();
                break;
            case 1:
                renderProfilesPage();
                break;
            case 2:
                renderGameLauncherPage();
                break;
            case 3:
                renderFloatingControlPage();
                break;
            case 4:
                renderTelemetryPage();
                break;
            default:
                renderDashboardPage();
                break;
        }
    }

    private void renderDashboardPage() {
        mainContentContainer.addView(createHeroCard());

        TextView sectionTitle = createSectionHeader(getString(R.string.mode_select_title));
        mainContentContainer.addView(sectionTitle);

        mainContentContainer.addView(createEngineCard(
                MODE_SAFE,
                getString(R.string.mode_safe_title),
                getString(R.string.mode_safe_desc),
                "Maximum Stealth • Safe Anti-Detection"
        ));

        mainContentContainer.addView(createEngineCard(
                MODE_EXTREME,
                getString(R.string.mode_extreme_title),
                getString(R.string.mode_extreme_desc),
                "High Speed Boost • Enhanced Aim & ESP"
        ));

        TextView launchTitle = createSectionHeader(getString(R.string.nav_game_launcher));
        mainContentContainer.addView(launchTitle);

        LinearLayout gameRow = new LinearLayout(this);
        gameRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        gameRow.setOrientation(LinearLayout.HORIZONTAL);

        Button launchFf = createGameButton(getString(R.string.btn_launch_ff), "com.dts.freefireth");
        launchFf.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        gameRow.addView(launchFf);
        mainContentContainer.addView(gameRow);

        TextView teleTitle = createSectionHeader(getString(R.string.nav_device_info));
        mainContentContainer.addView(teleTitle);
        mainContentContainer.addView(createQuickTelemetryCard());
    }

    private void renderProfilesPage() {
        mainContentContainer.addView(createSectionHeader("TACTICAL ENGINE PROFILES"));

        TextView desc = new TextView(this);
        desc.setText("Select your preferred native memory hook & anti-detection profile. Engine will dynamically calibrate memory offsets.");
        desc.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        desc.setPadding(0, 0, 0, dp(14));
        mainContentContainer.addView(desc);

        mainContentContainer.addView(createEngineCard(
                MODE_SAFE,
                getString(R.string.mode_safe_title),
                getString(R.string.mode_safe_desc),
                "Active Hooks: Position Fix, ESP Box, Clean Aim (Stealth Priority)"
        ));

        mainContentContainer.addView(createEngineCard(
                MODE_EXTREME,
                getString(R.string.mode_extreme_title),
                getString(R.string.mode_extreme_desc),
                "Active Hooks: Max Silent Aim, ESP Visuals, Speed Boost (Aggressive Priority)"
        ));
    }

    private void renderGameLauncherPage() {
        mainContentContainer.addView(createSectionHeader("DIRECT GAME LAUNCHER & BOOSTER"));

        TextView desc = new TextView(this);
        desc.setText("Launches game process directly through high-speed native environment with daemon hooks pre-initialized.");
        desc.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        desc.setPadding(0, 0, 0, dp(16));
        mainContentContainer.addView(desc);

        mainContentContainer.addView(createFullGameLaunchCard(
                "Free Fire Global",
                "com.dts.freefireth",
                "Official Global Package"
        ));
    }

    private void renderFloatingControlPage() {
        mainContentContainer.addView(createSectionHeader("FLOATING OVERLAY CONTROLLER"));

        LinearLayout card = new LinearLayout(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(createCardBg());

        TextView t = new TextView(this);
        t.setText("NINMODS Floating Menu");
        t.setTextColor(AppColors.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t);

        TextView info = new TextView(this);
        info.setText("Launch the in-game floating menu window on top of any game screen. Controls Aim, ESP, Visuals, and Snowfall particle background.");
        info.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        info.setPadding(0, dp(6), 0, dp(16));
        card.addView(info);

        Button startBtn = new Button(this);
        startBtn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        startBtn.setText("START FLOATING MOD MENU");
        startBtn.setTextColor(AppColors.WHITE);
        startBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        startBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable sBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{AppColors.MAGENTA_PRIMARY, AppColors.MAGENTA_DARK});
        sBg.setCornerRadius(dp(12));
        sBg.setStroke(dp(1), AppColors.MAGENTA_GLOW);
        startBtn.setBackground(sBg);
        startBtn.setOnClickListener(v -> launchFloater());
        card.addView(startBtn);

        mainContentContainer.addView(card);
    }

    private void renderTelemetryPage() {
        mainContentContainer.addView(createSectionHeader("SYSTEM & HARDWARE TELEMETRY"));
        mainContentContainer.addView(createDetailedTelemetryCard());
    }

    private View createHeroCard() {
        LinearLayout hero = new LinearLayout(this);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        heroLp.setMargins(0, 0, 0, dp(18));
        hero.setLayoutParams(heroLp);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));

        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{AppColors.MAGENTA_CARD, AppColors.MAGENTA_SURFACE}
        );
        heroBg.setCornerRadius(dp(16));
        heroBg.setStroke(dp(1), AppColors.MAGENTA_PRIMARY);
        hero.setBackground(heroBg);

        TextView tag = new TextView(this);
        tag.setText("ACTIVE CORE ENGINE");
        tag.setTextColor(AppColors.MAGENTA_PRIMARY);
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tag.setTypeface(Typeface.DEFAULT_BOLD);
        hero.addView(tag);

        TextView title = new TextView(this);
        title.setText(currentMode == MODE_SAFE ? getString(R.string.mode_safe_title) : getString(R.string.mode_extreme_title));
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setPadding(0, dp(4), 0, dp(6));
        hero.addView(title);

        TextView desc = new TextView(this);
        desc.setText(getString(R.string.status_daemon_running) + " • Native IPC Active");
        desc.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hero.addView(desc);

        return hero;
    }

    private View createEngineCard(final int mode, String title, String subtitle, String feature) {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardLp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        boolean isActive = (currentMode == mode);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_CARD, AppColors.MAGENTA_SURFACE}
        );
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), isActive ? AppColors.MAGENTA_PRIMARY : AppColors.MAGENTA_CARD_BORDER);
        card.setBackground(bg);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tView = new TextView(this);
        tView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        tView.setText(title);
        tView.setTextColor(AppColors.WHITE);
        tView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        topRow.addView(tView);

        if (isActive) {
            TextView activeBadge = new TextView(this);
            activeBadge.setText("ACTIVE");
            activeBadge.setTextColor(AppColors.MAGENTA_LIGHT);
            activeBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            activeBadge.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable bBg = new GradientDrawable();
            bBg.setColor(AppColors.MAGENTA_CHIP);
            bBg.setCornerRadius(dp(6));
            bBg.setStroke(dp(1), AppColors.MAGENTA_PRIMARY);
            activeBadge.setBackground(bBg);
            activeBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
            topRow.addView(activeBadge);
        }

        card.addView(topRow);

        TextView sView = new TextView(this);
        sView.setText(subtitle);
        sView.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        sView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sView.setPadding(0, dp(4), 0, dp(4));
        card.addView(sView);

        TextView fView = new TextView(this);
        fView.setText(feature);
        fView.setTextColor(AppColors.MAGENTA_LIGHT);
        fView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        fView.setPadding(0, 0, 0, dp(12));
        card.addView(fView);

        Button switchBtn = new Button(this);
        switchBtn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        switchBtn.setText(isActive ? getString(R.string.btn_activated) : getString(R.string.btn_activate));
        switchBtn.setTextColor(AppColors.WHITE);
        switchBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        switchBtn.setTypeface(Typeface.DEFAULT_BOLD);
        switchBtn.setAllCaps(false);

        GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                isActive ? new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END} : new int[]{AppColors.MAGENTA_CHIP, AppColors.MAGENTA_CARD}
        );
        btnBg.setCornerRadius(dp(10));
        btnBg.setStroke(dp(1), isActive ? AppColors.MAGENTA_GLOW : AppColors.MAGENTA_CARD_BORDER);
        switchBtn.setBackground(btnBg);

        switchBtn.setOnClickListener(v -> {
            applyEngineMode(mode);
            renderActivePage();
        });

        card.addView(switchBtn);
        return card;
    }

    private Button createGameButton(String label, final String packageName) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextColor(AppColors.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setAllCaps(false);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_CARD, AppColors.MAGENTA_SURFACE}
        );
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), AppColors.MAGENTA_PRIMARY);
        btn.setBackground(bg);

        btn.setOnClickListener(v -> launchGame(packageName));
        return btn;
    }

    private View createFullGameLaunchCard(String name, final String pkg, String desc) {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(lp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(createCardBg());

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        TextView pName = new TextView(this);
        pName.setText(pkg + " • " + desc);
        pName.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        pName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        pName.setPadding(0, dp(4), 0, dp(14));
        card.addView(pName);

        Button launchBtn = new Button(this);
        launchBtn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        launchBtn.setText("BOOST & LAUNCH GAME");
        launchBtn.setTextColor(AppColors.WHITE);
        launchBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        launchBtn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable lBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END});
        lBg.setCornerRadius(dp(10));
        lBg.setStroke(dp(1), AppColors.MAGENTA_GLOW);
        launchBtn.setBackground(lBg);
        launchBtn.setOnClickListener(v -> launchGame(pkg));
        card.addView(launchBtn);

        return card;
    }

    private View createQuickTelemetryCard() {
        LinearLayout card = new LinearLayout(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(createCardBg());

        card.addView(createTelemetryRow("CPU ABI", Build.SUPPORTED_ABIS[0]));
        card.addView(createTelemetryRow("Android Version", "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"));
        card.addView(createTelemetryRow("Daemon Engine", "Titoo Active (chmod 777)"));
        return card;
    }

    private View createDetailedTelemetryCard() {
        LinearLayout card = new LinearLayout(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(createCardBg());

        card.addView(createTelemetryRow("Manufacturer", Build.MANUFACTURER));
        card.addView(createTelemetryRow("Model", Build.MODEL));
        card.addView(createTelemetryRow("Hardware Board", Build.BOARD));
        card.addView(createTelemetryRow("Supported ABI", Build.SUPPORTED_ABIS[0]));
        card.addView(createTelemetryRow("Android OS", Build.VERSION.RELEASE));
        card.addView(createTelemetryRow("SDK Level", String.valueOf(Build.VERSION.SDK_INT)));
        card.addView(createTelemetryRow("Target Engine", "NINMODS v2.0 Native Core"));
        card.addView(createTelemetryRow("IPC Security", "RootService Validated"));
        return card;
    }

    private View createTelemetryRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView l = new TextView(this);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        l.setText(label);
        l.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(AppColors.WHITE);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setTypeface(Typeface.DEFAULT_BOLD);

        row.addView(l);
        row.addView(v);
        return row;
    }

    private TextView createSectionHeader(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(AppColors.MAGENTA_PRIMARY);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(14), 0, dp(8));
        return tv;
    }

    private GradientDrawable createScreenGradient() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{AppColors.MAGENTA_SCREEN_BG, AppColors.MAGENTA_SURFACE, AppColors.BLACK}
        );
    }

    private GradientDrawable createCardBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(AppColors.MAGENTA_CARD);
        d.setCornerRadius(dp(14));
        d.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        return d;
    }

    private void toggleDrawer() {
        if (isDrawerOpen) closeDrawer();
        else openDrawer();
    }

    private void openDrawer() {
        isDrawerOpen = true;
        drawerOverlay.setVisibility(View.VISIBLE);
        sidebarDrawer.setTranslationX(-sidebarDrawer.getWidth());
        sidebarDrawer.animate()
                .translationX(0)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void closeDrawer() {
        isDrawerOpen = false;
        sidebarDrawer.animate()
                .translationX(-sidebarDrawer.getWidth())
                .setDuration(180)
                .withEndAction(() -> drawerOverlay.setVisibility(View.GONE))
                .start();
    }

    private void applyEngineMode(int mode) {
        currentMode = mode;
        Prefs.with(this).writeInt(PREF_KEY_MODE, mode);
        setModeNative(mode);
        Toast.makeText(this, "Profile Activated: " + (mode == MODE_SAFE ? "Standard Safe" : "Advanced Extreme"), Toast.LENGTH_SHORT).show();
    }

    private void launchFloater() {
        try {
            new Floater(this);
            Toast.makeText(this, "Floating Mod Menu Started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            new Initialize(this);
        }
    }

    private void launchGame(String packageName) {
        launchFloater();
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            startActivity(launchIntent);
        } else {
            Toast.makeText(this, "Package not found: " + packageName, Toast.LENGTH_LONG).show();
        }
    }

    public static native boolean isLoggedInNative();
    public static native void setModeNative(int mode);

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
