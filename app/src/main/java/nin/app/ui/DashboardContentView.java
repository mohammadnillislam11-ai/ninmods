package nin.app.ui;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import nin.app.R;
import nin.app.core.AppColors;
import nin.app.menu.IMenuProject;
import nin.app.menu.MenuRouter;

public class DashboardContentView {

    public static final String PKG_FF_GLOBAL = "com.dts.freefireth";
    public static final String PKG_FF_MAX = "com.dts.freefiremax";

    public interface DashboardListener {
        void onBrandMenuClick(IMenuProject menu);
        void onModeSelected(int mode);
        void onGameClick(String packageName, String gameName, boolean isInstalled);
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

        // 1. Available Tactical Menus (Responsive 4-item single row series)
        TextView menusHeader = createSectionHeader(context, "AVAILABLE TACTICAL MENUS");
        container.addView(menusHeader);
        container.addView(createBrandMenuGrid(context, listener));

        // 2. Tactical Engine Profiles: Paired Cards Side-by-Side
        TextView enginesHeader = createSectionHeader(context, "TACTICAL ENGINE PROFILES");
        container.addView(enginesHeader);
        container.addView(createSideBySideEngineCards(context, currentMode, listener));

        // 3. Installed Games Section (Auto-detected from device, Zero buttons, dialog on click)
        TextView gamesHeader = createSectionHeader(context, "INSTALLED GAMES");
        container.addView(gamesHeader);
        container.addView(createInstalledGamesSection(context, listener));

        contentScrollView.addView(container);
        return contentScrollView;
    }

    // Responsive Brand Menu Grid (exactly 4 software menu items in a single series/row)
    private static View createBrandMenuGrid(Context context, final DashboardListener listener) {
        LinearLayout card = new LinearLayout(context);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(0, 0, 0, dp(context, 14));
        card.setLayoutParams(cardLp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 10), dp(context, 14), dp(context, 10), dp(context, 14));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(AppColors.MAGENTA_CARD);
        cardBg.setCornerRadius(dp(context, 10));
        card.setBackground(cardBg);

        // Row of 4 Software Menu Items in a single series (এক সেরেলে চারটা সফটওয়্যার)
        LinearLayout gridRow = new LinearLayout(context);
        gridRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        gridRow.setOrientation(LinearLayout.HORIZONTAL);
        gridRow.setGravity(Gravity.CENTER_VERTICAL);
        gridRow.setWeightSum(4.0f);

        List<IMenuProject> menus = MenuRouter.getRegisteredMenus();
        for (final IMenuProject menu : menus) {
            gridRow.addView(createBrandLogoButton(context, menu, listener));
        }

        card.addView(gridRow);
        return card;
    }

    // App-Launcher Style Menu Button - ONLY brand name below icon (No VIP MENU text)
    private static View createBrandLogoButton(Context context, final IMenuProject menu, final DashboardListener listener) {
        LinearLayout itemLayout = new LinearLayout(context);
        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        itemLp.setMargins(dp(context, 3), 0, dp(context, 3), 0);
        itemLayout.setLayoutParams(itemLp);
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        itemLayout.setClickable(true);
        itemLayout.setFocusable(true);
        itemLayout.setPadding(0, dp(context, 4), 0, dp(context, 4));

        // App-launcher style icon container
        FrameLayout iconBox = new FrameLayout(context);
        FrameLayout.LayoutParams iconBoxLp = new FrameLayout.LayoutParams(dp(context, 54), dp(context, 54));
        iconBoxLp.gravity = Gravity.CENTER_HORIZONTAL;
        iconBox.setLayoutParams(iconBoxLp);

        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(AppColors.MAGENTA_CHIP);
        iconBg.setCornerRadius(dp(context, 14));
        iconBg.setStroke(dp(context, 1), menu.getThemeColor());
        iconBox.setBackground(iconBg);

        ImageView iv = new ImageView(context);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(dp(context, 36), dp(context, 36));
        ivLp.gravity = Gravity.CENTER;
        iv.setLayoutParams(ivLp);
        iv.setImageResource(menu.getIconResId());
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconBox.addView(iv);

        itemLayout.addView(iconBox);

        // Brand Label below icon - ONLY NAME, zero extra subtitle/vip text
        TextView label = new TextView(context);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(context, 6);
        label.setLayoutParams(labelLp);
        label.setText(menu.getDisplayName(context));
        label.setTextColor(AppColors.WHITE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        itemLayout.addView(label);

        itemLayout.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBrandMenuClick(menu);
            }
        });

        return itemLayout;
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

    // Dynamic Installed Games Section (Zero Buttons, Auto-Detected from Device)
    private static View createInstalledGamesSection(Context context, final DashboardListener listener) {
        LinearLayout container = new LinearLayout(context);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        containerLp.setMargins(0, 0, 0, dp(context, 14));
        container.setLayoutParams(containerLp);
        container.setOrientation(LinearLayout.VERTICAL);

        // Free Fire Global
        container.addView(createClickableGameItem(context, PKG_FF_GLOBAL, "Free Fire", listener));

        // Free Fire MAX
        container.addView(createClickableGameItem(context, PKG_FF_MAX, "Free Fire MAX", listener));

        return container;
    }

    private static View createClickableGameItem(Context context, final String packageName, String defaultName, final DashboardListener listener) {
        PackageManager pm = context.getPackageManager();
        boolean isInstalled = false;
        String gameTitle = defaultName;
        Drawable gameIcon = null;

        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            isInstalled = true;
            CharSequence label = pm.getApplicationLabel(appInfo);
            if (label != null && !TextUtils.isEmpty(label)) {
                gameTitle = label.toString();
            }
            gameIcon = pm.getApplicationIcon(appInfo);
        } catch (PackageManager.NameNotFoundException ignored) {
            isInstalled = false;
        }

        final boolean finalIsInstalled = isInstalled;
        final String finalGameTitle = gameTitle;

        LinearLayout card = new LinearLayout(context);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(0, 0, 0, dp(context, 10));
        card.setLayoutParams(cardLp);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(AppColors.MAGENTA_CARD);
        cardBg.setCornerRadius(dp(context, 10));
        if (finalIsInstalled) {
            cardBg.setStroke(dp(context, 1), AppColors.withAlpha(AppColors.MAGENTA_PRIMARY, 100));
        }
        card.setBackground(cardBg);

        // App Icon
        ImageView iconView = new ImageView(context);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44));
        iconLp.rightMargin = dp(context, 12);
        iconView.setLayoutParams(iconLp);
        if (gameIcon != null) {
            iconView.setImageDrawable(gameIcon);
        } else {
            iconView.setImageResource(R.drawable.nin_logo);
        }
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(iconView);

        // Title and Package Details
        LinearLayout textCol = new LinearLayout(context);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleTv = new TextView(context);
        titleTv.setText(finalGameTitle);
        titleTv.setTextColor(AppColors.WHITE);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(titleTv);

        TextView pkgTv = new TextView(context);
        pkgTv.setText(packageName);
        pkgTv.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        pkgTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        pkgTv.setPadding(0, dp(context, 2), 0, 0);
        textCol.addView(pkgTv);

        card.addView(textCol);

        // Installation Status Chip (Zero buttons, clean status pill)
        TextView statusPill = new TextView(context);
        statusPill.setText(finalIsInstalled ? "INSTALLED" : "NOT INSTALLED");
        statusPill.setTextColor(finalIsInstalled ? 0xFF00E676 : 0xFF888888);
        statusPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        statusPill.setTypeface(Typeface.DEFAULT_BOLD);
        statusPill.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(finalIsInstalled ? 0x2200E676 : 0x22888888);
        pillBg.setCornerRadius(dp(context, 6));
        pillBg.setStroke(dp(context, 1), finalIsInstalled ? 0x6600E676 : 0x44888888);
        statusPill.setBackground(pillBg);
        card.addView(statusPill);

        // Click directly opens confirmation dialog
        card.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGameClick(packageName, finalGameTitle, finalIsInstalled);
            }
        });

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
