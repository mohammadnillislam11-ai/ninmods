package com.ninmods.main.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.drawerlayout.widget.DrawerLayout;

import com.ninmods.main.R;
import com.ninmods.main.auth.AuthManager;
import com.ninmods.main.core.AppColors;

public class SidebarView {

    public interface SidebarListener {
        void onDashboardClick();
        void onDeviceInfoClick();
        void onDriverLoaderClick();
        void onProfileClick();
        void onLogoutClick();
    }

    public static View create(Context context, final SidebarListener listener) {
        LinearLayout sidebar = new LinearLayout(context);
        DrawerLayout.LayoutParams sideLp = new DrawerLayout.LayoutParams(
                dp(context, 280),
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        sideLp.gravity = Gravity.START;
        sidebar.setLayoutParams(sideLp);
        sidebar.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.parseColor("#34123C")); // Dark Tactical Surface
        panelBg.setStroke(dp(context, 1), AppColors.MAGENTA_CARD_BORDER);
        sidebar.setBackground(panelBg);

        // 1. Sidebar Brand Header (Top Bar Title)
        LinearLayout sideHeader = new LinearLayout(context);
        sideHeader.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        sideHeader.setOrientation(LinearLayout.HORIZONTAL);
        sideHeader.setGravity(Gravity.CENTER_VERTICAL);
        sideHeader.setPadding(dp(context, 20), dp(context, 26), dp(context, 20), dp(context, 14));

        TextView sTitle = new TextView(context);
        sTitle.setText(context.getString(R.string.app_name));
        sTitle.setTextColor(AppColors.WHITE);
        sTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        sTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        sideHeader.addView(sTitle);

        sidebar.addView(sideHeader);

        // 2. User Profile Card Container (Exact layout as shown in user's reference screenshot)
        FrameLayout profileCardFrame = new FrameLayout(context);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(dp(context, 16), dp(context, 4), dp(context, 16), dp(context, 16));
        profileCardFrame.setLayoutParams(cardLp);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#481954")); // Rich Card Surface matching tactical palette
        cardBg.setCornerRadius(dp(context, 16));
        cardBg.setStroke(dp(context, 1), AppColors.MAGENTA_CARD_BORDER);
        profileCardFrame.setBackground(cardBg);

        // Card Content: Avatar, Username, Balance Pill
        LinearLayout profileCardContent = new LinearLayout(context);
        profileCardContent.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        profileCardContent.setOrientation(LinearLayout.VERTICAL);
        profileCardContent.setGravity(Gravity.CENTER_HORIZONTAL);
        profileCardContent.setPadding(dp(context, 16), dp(context, 18), dp(context, 16), dp(context, 18));

        // Circular Avatar Box
        FrameLayout avatarCircle = new FrameLayout(context);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(context, 66), dp(context, 66));
        avatarCircle.setLayoutParams(avLp);

        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(Color.parseColor("#260B2E"));
        avBg.setStroke(dp(context, 2), AppColors.MAGENTA_PRIMARY);
        avatarCircle.setBackground(avBg);

        TextView avatarIcon = new TextView(context);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        iconLp.gravity = Gravity.CENTER;
        avatarIcon.setLayoutParams(iconLp);
        avatarIcon.setText("👤");
        avatarIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        avatarIcon.setGravity(Gravity.CENTER);
        avatarCircle.addView(avatarIcon);

        profileCardContent.addView(avatarCircle);

        // Username
        String username = AuthManager.getActiveUsername(context);
        if (username.isEmpty()) username = "Tactical Operator";

        TextView tvUsername = new TextView(context);
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        uLp.topMargin = dp(context, 8);
        tvUsername.setLayoutParams(uLp);
        tvUsername.setText(username);
        tvUsername.setTextColor(AppColors.WHITE);
        tvUsername.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvUsername.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvUsername.setGravity(Gravity.CENTER);
        profileCardContent.addView(tvUsername);

        // Balance / Currency Pill (prominent gradient pill like in screenshot)
        float balance = AuthManager.getActiveBalance(context);
        String currency = AuthManager.getActiveCurrency(context);

        TextView tvBalancePill = new TextView(context);
        LinearLayout.LayoutParams balLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        balLp.topMargin = dp(context, 10);
        tvBalancePill.setLayoutParams(balLp);
        tvBalancePill.setText(String.format(java.util.Locale.US, "%.2f %s", balance, currency));
        tvBalancePill.setTextColor(AppColors.WHITE);
        tvBalancePill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvBalancePill.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvBalancePill.setGravity(Gravity.CENTER);
        tvBalancePill.setPadding(dp(context, 24), dp(context, 6), dp(context, 24), dp(context, 7));

        GradientDrawable balBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END}
        );
        balBg.setCornerRadius(dp(context, 16));
        tvBalancePill.setBackground(balBg);
        profileCardContent.addView(tvBalancePill);

        profileCardFrame.addView(profileCardContent);

        // Top-Right Corner Role / Status Badge (like "NEW" badge in reference screenshot)
        String role = AuthManager.getActiveRole(context);
        String roleText = !role.isEmpty() ? role.toUpperCase() : "MEMBER";

        TextView roleBadge = new TextView(context);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        badgeLp.gravity = Gravity.TOP | Gravity.END;
        badgeLp.setMargins(0, dp(context, 10), dp(context, 10), 0);
        roleBadge.setLayoutParams(badgeLp);
        roleBadge.setText(roleText);
        roleBadge.setTextColor(AppColors.WHITE);
        roleBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        roleBadge.setTypeface(Typeface.DEFAULT_BOLD);
        roleBadge.setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3));

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#9C27B0"));
        badgeBg.setCornerRadius(dp(context, 6));
        roleBadge.setBackground(badgeBg);
        profileCardFrame.addView(roleBadge);

        // Click on profile card also opens profile
        profileCardFrame.setClickable(true);
        profileCardFrame.setOnClickListener(v -> {
            if (listener != null) listener.onProfileClick();
        });

        sidebar.addView(profileCardFrame);

        // 3. Navigation Items (Middle list)
        LinearLayout navList = new LinearLayout(context);
        navList.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));
        navList.setOrientation(LinearLayout.VERTICAL);
        navList.setPadding(dp(context, 16), dp(context, 4), dp(context, 16), dp(context, 8));

        // 1. Dashboard - Highlighted Active Button (Like "New Order" with vibrant gradient button)
        navList.addView(createActiveNavItem(context, "⊞   Dashboard", v -> {
            if (listener != null) listener.onDashboardClick();
        }));

        // 2. User Profile - Flat Item (Like "Orders")
        navList.addView(createFlatNavItem(context, "👤   Account Profile", v -> {
            if (listener != null) listener.onProfileClick();
        }));

        // 3. Device Information - Flat Item (Like "Services")
        navList.addView(createFlatNavItem(context, "📱   Device Information", v -> {
            if (listener != null) listener.onDeviceInfoClick();
        }));

        // 4. Driver Loader Engine - Flat Item (Like "Tickets")
        navList.addView(createFlatNavItem(context, "⚡   Driver Loader Engine", v -> {
            if (listener != null) listener.onDriverLoaderClick();
        }));

        sidebar.addView(navList);

        // 4. Bottom Footer - Log Out Session (Pinned to the very bottom, NO background per user request)
        LinearLayout footerLayout = new LinearLayout(context);
        footerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        footerLayout.setOrientation(LinearLayout.VERTICAL);
        footerLayout.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 20));

        Button logoutBtn = new Button(context);
        LinearLayout.LayoutParams loLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 44)
        );
        logoutBtn.setLayoutParams(loLp);
        logoutBtn.setText("⏻  Log Out Session");
        logoutBtn.setTextColor(Color.parseColor("#FF5252"));
        logoutBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        logoutBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        logoutBtn.setGravity(Gravity.CENTER);
        logoutBtn.setBackgroundColor(Color.TRANSPARENT); // বাটনের কোন ব্যাকগ্রাউন্ড থাকবে না
        logoutBtn.setOnClickListener(v -> {
            if (listener != null) listener.onLogoutClick();
        });
        footerLayout.addView(logoutBtn);

        sidebar.addView(footerLayout);

        return sidebar;
    }

    // Active Highlighted Navigation Button (Vibrant Gradient rounded pill/card like "New Order")
    private static View createActiveNavItem(Context context, String label, View.OnClickListener onClickListener) {
        Button btn = new Button(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 46)
        );
        lp.setMargins(0, 0, 0, dp(context, 8));
        btn.setLayoutParams(lp);
        btn.setText(label);
        btn.setTextColor(AppColors.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        btn.setPadding(dp(context, 16), 0, dp(context, 16), 0);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END}
        );
        bg.setCornerRadius(dp(context, 10));
        btn.setBackground(bg);
        btn.setOnClickListener(onClickListener);
        return btn;
    }

    // Flat Clean Navigation Row (Transparent background with left icon + clean text like reference screenshot)
    private static View createFlatNavItem(Context context, String label, View.OnClickListener onClickListener) {
        Button btn = new Button(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 44)
        );
        lp.setMargins(0, 0, 0, dp(context, 4));
        btn.setLayoutParams(lp);
        btn.setText(label);
        btn.setTextColor(AppColors.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        btn.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setOnClickListener(onClickListener);
        return btn;
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
