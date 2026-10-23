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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.drawerlayout.widget.DrawerLayout;

import com.ninmods.main.R;
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
        panelBg.setColor(Color.parseColor("#4A1D4F")); // Exact same color as Dashboard Cards (74, 29, 79)
        panelBg.setStroke(dp(context, 1), AppColors.MAGENTA_CARD_BORDER);
        sidebar.setBackground(panelBg);

        // Sidebar Header
        LinearLayout sideHeader = new LinearLayout(context);
        sideHeader.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sideHeader.setOrientation(LinearLayout.VERTICAL);
        sideHeader.setPadding(dp(context, 20), dp(context, 28), dp(context, 20), dp(context, 20));
        
        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(Color.parseColor("#401846")); // Exact same color as Header (64, 24, 70)
        headerBg.setStroke(dp(context, 1), AppColors.MAGENTA_CARD_BORDER);
        sideHeader.setBackground(headerBg);

        TextView sTitle = new TextView(context);
        sTitle.setText(context.getString(R.string.app_name));
        sTitle.setTextColor(AppColors.WHITE);
        sTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        sTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        sideHeader.addView(sTitle);

        TextView sSub = new TextView(context);
        sSub.setText("Tactical Suite v" + context.getString(R.string.app_version));
        sSub.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        sSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sSub.setPadding(0, dp(context, 2), 0, 0);
        sideHeader.addView(sSub);

        sidebar.addView(sideHeader);

        // Sidebar Navigation Items
        LinearLayout navList = new LinearLayout(context);
        navList.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        navList.setOrientation(LinearLayout.VERTICAL);
        navList.setPadding(dp(context, 12), dp(context, 16), dp(context, 12), dp(context, 16));

        // 1. Dashboard (Home)
        navList.addView(createNavItem(context, "Dashboard", v -> {
            if (listener != null) listener.onDashboardClick();
        }));

        // 2. User Profile (Opens ProfileActivity)
        navList.addView(createNavItem(context, "Account Profile", v -> {
            if (listener != null) listener.onProfileClick();
        }));

        // 3. Device Information (Opens DeviceInfoActivity)
        navList.addView(createNavItem(context, "Device Information", v -> {
            if (listener != null) listener.onDeviceInfoClick();
        }));

        // 4. Ring 0 Driver Loader (Opens com.ninmods.loader)
        navList.addView(createNavItem(context, "Driver Loader Engine", v -> {
            if (listener != null) listener.onDriverLoaderClick();
        }));

        // 5. Log Out Session
        navList.addView(createLogoutNavItem(context, "Log Out Session", v -> {
            if (listener != null) listener.onLogoutClick();
        }));

        sidebar.addView(navList);

        return sidebar;
    }

    private static View createLogoutNavItem(Context context, String label, View.OnClickListener onClickListener) {
        Button btn = new Button(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 44));
        lp.setMargins(0, dp(context, 10), 0, 0);
        btn.setLayoutParams(lp);
        btn.setText(label);
        btn.setTextColor(AppColors.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btn.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#441010"));
        bg.setCornerRadius(dp(context, 8));
        bg.setStroke(dp(context, 1), Color.parseColor("#882222"));
        btn.setBackground(bg);
        btn.setOnClickListener(onClickListener);
        return btn;
    }

    private static View createNavItem(Context context, String label, View.OnClickListener onClickListener) {
        Button btn = new Button(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 46));
        lp.setMargins(0, 0, 0, dp(context, 6));
        btn.setLayoutParams(lp);
        btn.setText(label);
        btn.setTextColor(AppColors.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        btn.setPadding(dp(context, 16), 0, dp(context, 16), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#5A2260")); // Vibrant card matching button color
        bg.setCornerRadius(dp(context, 8));
        bg.setStroke(dp(context, 1), AppColors.MAGENTA_CARD_BORDER);
        btn.setBackground(bg);
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
