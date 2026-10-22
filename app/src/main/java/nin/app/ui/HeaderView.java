package nin.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import nin.app.R;
import nin.app.core.AppColors;

public class HeaderView {

    public interface OnMenuClickListener {
        void onMenuClick();
    }

    public interface HeaderListener {
        void onMenuClick();
        void onProfileClick();
    }

    public static View create(Context context, final OnMenuClickListener menuClickListener) {
        return create(context, new HeaderListener() {
            @Override
            public void onMenuClick() {
                if (menuClickListener != null) menuClickListener.onMenuClick();
            }

            @Override
            public void onProfileClick() {}
        });
    }

    public static View create(Context context, final HeaderListener headerListener) {
        LinearLayout header = new LinearLayout(context);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 56)
        ));
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(context, 12), 0, dp(context, 14), 0);
        header.setBackgroundColor(AppColors.MAGENTA_SURFACE); // Clean solid background, zero borders

        // 3-Line Hamburger Menu Button (opens sidebar drawer)
        Button menuBtn = new Button(context);
        menuBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));
        menuBtn.setText("☰");
        menuBtn.setTextColor(AppColors.WHITE);
        menuBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        menuBtn.setTypeface(Typeface.DEFAULT_BOLD);
        menuBtn.setBackgroundColor(Color.TRANSPARENT);
        menuBtn.setOnClickListener(v -> {
            if (headerListener != null) {
                headerListener.onMenuClick();
            }
        });
        header.addView(menuBtn);

        LinearLayout brandBox = new LinearLayout(context);
        brandBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        brandBox.setOrientation(LinearLayout.VERTICAL);
        brandBox.setPadding(dp(context, 6), 0, 0, 0);

        TextView title = new TextView(context);
        title.setText(context.getString(R.string.app_name));
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        brandBox.addView(title);

        header.addView(brandBox);

        // Circular Profile Avatar Button (Right Edge)
        android.widget.FrameLayout profileBtn = new android.widget.FrameLayout(context);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(dp(context, 38), dp(context, 38));
        profileBtn.setLayoutParams(pLp);

        android.graphics.drawable.GradientDrawable pBg = new android.graphics.drawable.GradientDrawable();
        pBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        pBg.setColor(AppColors.MAGENTA_CHIP);
        profileBtn.setBackground(pBg);

        android.widget.ImageView pIcon = new android.widget.ImageView(context);
        android.widget.FrameLayout.LayoutParams pIconLp = new android.widget.FrameLayout.LayoutParams(
                dp(context, 22),
                dp(context, 22),
                Gravity.CENTER
        );
        pIcon.setLayoutParams(pIconLp);
        pIcon.setImageResource(R.drawable.ic_user_profile);
        profileBtn.addView(pIcon);

        profileBtn.setOnClickListener(v -> {
            if (headerListener != null) {
                headerListener.onProfileClick();
            }
        });
        header.addView(profileBtn);

        return header;
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
