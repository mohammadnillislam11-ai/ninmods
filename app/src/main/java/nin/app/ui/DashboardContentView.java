package nin.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import nin.app.R;
import nin.app.core.AppColors;

public class DashboardContentView {

    public interface DashboardListener {
        void onStartMenu();
        void onStopMenu();
        void onModeSelected(int mode);
        void onLaunchGame(String packageName);
    }

    public static View create(Context context, int currentMode, final DashboardListener listener) {
        ScrollView contentScrollView = new ScrollView(context);
        contentScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));
        contentScrollView.setVerticalScrollBarEnabled(false);

        LinearLayout container = new LinearLayout(context);
        container.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 24));

        // 1. FIRST: START and STOP Buttons Side-by-Side (Horizontally Adjacent)
        container.addView(createStartStopRow(context, listener));

        // 2. Tactical Engine Profiles: Paired Cards Side-by-Side
        TextView enginesHeader = createSectionHeader(context, "TACTICAL ENGINE PROFILES");
        container.addView(enginesHeader);
        container.addView(createSideBySideEngineCards(context, currentMode, listener));

        // 3. Direct Game Launcher Section
        TextView launchHeader = createSectionHeader(context, "DIRECT GAME LAUNCHER");
        container.addView(launchHeader);
        container.addView(createGameLaunchCard(
                context,
                "Free Fire Global",
                "com.dts.freefireth",
                "Official Global Package",
                listener
        ));

        contentScrollView.addView(container);
        return contentScrollView;
    }

    // Start & Stop Buttons Side-by-Side
    private static View createStartStopRow(Context context, final DashboardListener listener) {
        LinearLayout row = new LinearLayout(context);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 48)
        );
        rowLp.setMargins(0, 0, 0, dp(context, 16));
        row.setLayoutParams(rowLp);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button startBtn = new Button(context);
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        startLp.rightMargin = dp(context, 6);
        startBtn.setLayoutParams(startLp);
        startBtn.setText("START MENU");
        startBtn.setTextColor(AppColors.WHITE);
        startBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        startBtn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable startBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END}
        );
        startBg.setCornerRadius(dp(context, 8));
        startBtn.setBackground(startBg);
        startBtn.setOnClickListener(v -> {
            if (listener != null) listener.onStartMenu();
        });
        row.addView(startBtn);

        Button stopBtn = new Button(context);
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        stopLp.leftMargin = dp(context, 6);
        stopBtn.setLayoutParams(stopLp);
        stopBtn.setText("STOP MENU");
        stopBtn.setTextColor(AppColors.WHITE);
        stopBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        stopBtn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable stopBg = new GradientDrawable();
        stopBg.setColor(AppColors.MAGENTA_CHIP);
        stopBg.setCornerRadius(dp(context, 8));
        stopBtn.setBackground(stopBg);
        stopBtn.setOnClickListener(v -> {
            if (listener != null) listener.onStopMenu();
        });
        row.addView(stopBtn);

        return row;
    }

    // Side-by-Side Paired Cards for Safe and Extreme Engine Profiles
    private static View createSideBySideEngineCards(Context context, int currentMode, final DashboardListener listener) {
        LinearLayout row = new LinearLayout(context);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowLp.setMargins(0, 0, 0, dp(context, 14));
        row.setLayoutParams(rowLp);
        row.setOrientation(LinearLayout.HORIZONTAL);

        // Safe Engine (Column 1)
        row.addView(createSingleEngineCard(
                context,
                1,
                context.getString(R.string.mode_safe_title),
                "Safe Anti-Detection",
                true,
                currentMode,
                listener
        ));

        // Extreme Engine (Column 2)
        row.addView(createSingleEngineCard(
                context,
                2,
                context.getString(R.string.mode_extreme_title),
                "Speed & Aim Hook",
                false,
                currentMode,
                listener
        ));

        return row;
    }

    private static View createSingleEngineCard(Context context, final int mode, String title, String subtitle, boolean isLeft, int currentMode, final DashboardListener listener) {
        LinearLayout card = new LinearLayout(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        if (isLeft) {
            lp.rightMargin = dp(context, 6);
        } else {
            lp.leftMargin = dp(context, 6);
        }
        card.setLayoutParams(lp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14));

        boolean isActive = (currentMode == mode);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppColors.MAGENTA_CARD);
        bg.setCornerRadius(dp(context, 10));
        if (isActive) {
            bg.setStroke(dp(context, 1), AppColors.MAGENTA_PRIMARY);
        }
        card.setBackground(bg);

        TextView tView = new TextView(context);
        tView.setText(title);
        tView.setTextColor(AppColors.WHITE);
        tView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        card.addView(tView);

        TextView sView = new TextView(context);
        sView.setText(subtitle);
        sView.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        sView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        sView.setPadding(0, dp(context, 2), 0, dp(context, 10));
        card.addView(sView);

        Button switchBtn = new Button(context);
        switchBtn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 36)));
        switchBtn.setText(isActive ? "ACTIVE" : "ACTIVATE");
        switchBtn.setTextColor(AppColors.WHITE);
        switchBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        switchBtn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(isActive ? AppColors.MAGENTA_PRIMARY : AppColors.MAGENTA_CHIP);
        btnBg.setCornerRadius(dp(context, 6));
        switchBtn.setBackground(btnBg);

        switchBtn.setOnClickListener(v -> {
            if (listener != null) listener.onModeSelected(mode);
        });

        card.addView(switchBtn);
        return card;
    }

    private static View createGameLaunchCard(Context context, String name, final String pkg, String desc, final DashboardListener listener) {
        LinearLayout card = new LinearLayout(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, dp(context, 14));
        card.setLayoutParams(lp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppColors.MAGENTA_CARD);
        bg.setCornerRadius(dp(context, 10));
        card.setBackground(bg);

        TextView title = new TextView(context);
        title.setText(name);
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        TextView pName = new TextView(context);
        pName.setText(pkg + " • " + desc);
        pName.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        pName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        pName.setPadding(0, dp(context, 2), 0, dp(context, 12));
        card.addView(pName);

        Button launchBtn = new Button(context);
        launchBtn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 42)));
        launchBtn.setText("BOOST & LAUNCH GAME");
        launchBtn.setTextColor(AppColors.WHITE);
        launchBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        launchBtn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable lBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END}
        );
        lBg.setCornerRadius(dp(context, 8));
        launchBtn.setBackground(lBg);
        launchBtn.setOnClickListener(v -> {
            if (listener != null) listener.onLaunchGame(pkg);
        });
        card.addView(launchBtn);

        return card;
    }

    private static TextView createSectionHeader(Context context, String title) {
        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextColor(AppColors.MAGENTA_PRIMARY);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(context, 8), 0, dp(context, 6));
        return tv;
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
