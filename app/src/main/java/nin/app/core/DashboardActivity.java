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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import nin.app.R;
import nin.app.overlay.Floater;

public class DashboardActivity extends Activity {

    static {
        System.loadLibrary("ninmods");
    }

    public static final int MODE_SAFE = 1;
    public static final int MODE_EXTREME = 2;
    public static final String PREF_KEY_MODE = "config_mode";

    private FrameLayout rootFrame;
    private LinearLayout mainContentContainer;
    private int currentMode = MODE_SAFE;

    private View heroCardView;
    private View safeEngineCardView;
    private View extremeEngineCardView;

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

        // 1. Standard Clean Header Bar
        baseLayout.addView(createHeaderBar());

        // 2. Main Scrollable Body (All sections contained directly in the body)
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
        mainContentContainer.setPadding(dp(16), dp(16), dp(16), dp(24));

        populateMainBody();

        contentScrollView.addView(mainContentContainer);
        baseLayout.addView(contentScrollView);

        rootFrame.addView(baseLayout);
        setContentView(rootFrame);
    }

    private View createHeaderBar() {
        LinearLayout header = new LinearLayout(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(60)
        ));
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(8), dp(16), dp(8));

        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(AppColors.MAGENTA_SURFACE);
        headerBg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        header.setBackground(headerBg);

        LinearLayout brandBox = new LinearLayout(this);
        brandBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        brandBox.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
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
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusRow.addView(statusText);

        brandBox.addView(statusRow);
        header.addView(brandBox);

        // Header Action Button: MOD MENU
        Button floaterBtn = new Button(this);
        floaterBtn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        floaterBtn.setText("MOD MENU");
        floaterBtn.setTextColor(AppColors.WHITE);
        floaterBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        floaterBtn.setTypeface(Typeface.DEFAULT_BOLD);
        floaterBtn.setAllCaps(false);
        GradientDrawable fBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END}
        );
        fBg.setCornerRadius(dp(10));
        fBg.setStroke(dp(1), AppColors.MAGENTA_GLOW);
        floaterBtn.setBackground(fBg);
        floaterBtn.setPadding(dp(16), 0, dp(16), 0);
        floaterBtn.setOnClickListener(v -> launchFloater());
        header.addView(floaterBtn);

        return header;
    }

    private void populateMainBody() {
        mainContentContainer.removeAllViews();

        // 1. Hero Card (Master Engine Status)
        heroCardView = createHeroCard();
        mainContentContainer.addView(heroCardView);

        // 2. Tactical Engine Profiles Section
        TextView enginesHeader = createSectionHeader("TACTICAL ENGINE PROFILES");
        mainContentContainer.addView(enginesHeader);

        safeEngineCardView = createEngineCard(
                MODE_SAFE,
                getString(R.string.mode_safe_title),
                getString(R.string.mode_safe_desc),
                "Maximum Stealth • Safe Anti-Detection Hook"
        );
        mainContentContainer.addView(safeEngineCardView);

        extremeEngineCardView = createEngineCard(
                MODE_EXTREME,
                getString(R.string.mode_extreme_title),
                getString(R.string.mode_extreme_desc),
                "High Speed Boost • Enhanced Aim & ESP Hook"
        );
        mainContentContainer.addView(extremeEngineCardView);

        // 3. Direct Game Launcher Section
        TextView launchHeader = createSectionHeader("DIRECT GAME LAUNCHER");
        mainContentContainer.addView(launchHeader);
        mainContentContainer.addView(createFullGameLaunchCard(
                "Free Fire Global",
                "com.dts.freefireth",
                "Official Global Package"
        ));

        // 4. Floating Overlay Controller Section
        TextView floaterHeader = createSectionHeader("FLOATING OVERLAY CONTROLLER");
        mainContentContainer.addView(floaterHeader);
        mainContentContainer.addView(createFloaterControlCard());

        // 5. System & Hardware Telemetry Section
        TextView teleHeader = createSectionHeader("DEVICE & SYSTEM TELEMETRY");
        mainContentContainer.addView(teleHeader);
        mainContentContainer.addView(createTelemetryCard());
    }

    private View createHeroCard() {
        LinearLayout hero = new LinearLayout(this);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        heroLp.setMargins(0, 0, 0, dp(16));
        hero.setLayoutParams(heroLp);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));

        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{AppColors.MAGENTA_CARD, AppColors.MAGENTA_SURFACE}
        );
        heroBg.setCornerRadius(dp(14));
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
        desc.setText(getString(R.string.status_daemon_running) + " • Native IPC Active (chmod 777)");
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
            populateMainBody();
        });

        card.addView(switchBtn);
        return card;
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

    private View createFloaterControlCard() {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(lp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(createCardBg());

        TextView t = new TextView(this);
        t.setText("NINMODS Floating Menu");
        t.setTextColor(AppColors.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t);

        TextView info = new TextView(this);
        info.setText("Launch or stop the in-game floating overlay menu window anytime. Controls Aim, ESP, and Visuals.");
        info.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        info.setPadding(0, dp(4), 0, dp(14));
        card.addView(info);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button startBtn = new Button(this);
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        startLp.rightMargin = dp(8);
        startBtn.setLayoutParams(startLp);
        startBtn.setText("START MENU");
        startBtn.setTextColor(AppColors.WHITE);
        startBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        startBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable sBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END});
        sBg.setCornerRadius(dp(10));
        sBg.setStroke(dp(1), AppColors.MAGENTA_GLOW);
        startBtn.setBackground(sBg);
        startBtn.setOnClickListener(v -> launchFloater());
        btnRow.addView(startBtn);

        Button stopBtn = new Button(this);
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        stopBtn.setLayoutParams(stopLp);
        stopBtn.setText("STOP MENU");
        stopBtn.setTextColor(AppColors.WHITE);
        stopBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        stopBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable stopBg = new GradientDrawable();
        stopBg.setColor(AppColors.MAGENTA_CHIP);
        stopBg.setCornerRadius(dp(10));
        stopBg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        stopBtn.setBackground(stopBg);
        stopBtn.setOnClickListener(v -> {
            Floater.stop();
            Toast.makeText(DashboardActivity.this, "Floating Menu Stopped", Toast.LENGTH_SHORT).show();
        });
        btnRow.addView(stopBtn);

        card.addView(btnRow);
        return card;
    }

    private View createTelemetryCard() {
        LinearLayout card = new LinearLayout(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(createCardBg());

        card.addView(createTelemetryRow("Device Model", Build.MANUFACTURER + " " + Build.MODEL));
        card.addView(createTelemetryRow("CPU ABI", Build.SUPPORTED_ABIS[0]));
        card.addView(createTelemetryRow("Android Version", "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"));
        card.addView(createTelemetryRow("Hardware Board", Build.BOARD));
        card.addView(createTelemetryRow("Daemon Engine", "Titoo Active (chmod 777)"));
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

    private void applyEngineMode(int mode) {
        currentMode = mode;
        Prefs.with(this).writeInt(PREF_KEY_MODE, mode);
        setModeNative(mode);
        Toast.makeText(this, "Profile Activated: " + (mode == MODE_SAFE ? "Standard Safe" : "Advanced Extreme"), Toast.LENGTH_SHORT).show();
    }

    private void launchFloater() {
        try {
            Floater.start(this);
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
