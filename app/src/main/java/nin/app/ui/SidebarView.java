package nin.app.ui;

import android.content.Context;
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

import nin.app.R;
import nin.app.core.AppColors;

public class SidebarView {

    public interface SidebarListener {
        void onDashboardClick();
        void onDeviceInfoClick();
        void onModeSelectClick();
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
        sidebar.setBackgroundColor(AppColors.MAGENTA_SURFACE);

        // Sidebar Header
        LinearLayout sideHeader = new LinearLayout(context);
        sideHeader.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sideHeader.setOrientation(LinearLayout.VERTICAL);
        sideHeader.setPadding(dp(context, 20), dp(context, 28), dp(context, 20), dp(context, 20));
        sideHeader.setBackgroundColor(AppColors.MAGENTA_CARD);

        TextView sTitle = new TextView(context);
        sTitle.setText(context.getString(R.string.app_name));
        sTitle.setTextColor(AppColors.WHITE);
        sTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        sTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        sideHeader.addView(sTitle);

        TextView sSub = new TextView(context);
        sSub.setText("Tactical Suite v64.0");
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

        // 2. Device Information (Opens DeviceInfoActivity)
        navList.addView(createNavItem(context, "Device Information", v -> {
            if (listener != null) listener.onDeviceInfoClick();
        }));

        // 3. Tactical Mode Selection (Opens ModeSelectActivity)
        navList.addView(createNavItem(context, "Tactical Mode Selection", v -> {
            if (listener != null) listener.onModeSelectClick();
        }));

        sidebar.addView(navList);

        // Sidebar Footer
        TextView footer = new TextView(context);
        footer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        footer.setText("Version 64.0 (Official Build)");
        footer.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 16));
        sidebar.addView(footer);

        return sidebar;
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
        bg.setColor(AppColors.MAGENTA_CHIP);
        bg.setCornerRadius(dp(context, 8));
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
