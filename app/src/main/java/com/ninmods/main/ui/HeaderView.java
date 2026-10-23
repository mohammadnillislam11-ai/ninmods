package com.ninmods.main.ui;

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

import com.ninmods.main.R;
import com.ninmods.main.core.AppColors;

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
        header.setBackgroundColor(AppColors.MAGENTA_CARD); // Exactly matches card color

        // 3-Line Hamburger Menu Button (opens sidebar drawer)
        ImageView menuBtn = new ImageView(context);
        menuBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));
        menuBtn.setPadding(dp(context, 11), dp(context, 11), dp(context, 11), dp(context, 11));
        menuBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);

        android.graphics.drawable.ShapeDrawable hamburgerDrawable = new android.graphics.drawable.ShapeDrawable(new android.graphics.drawable.shapes.Shape() {
            @Override
            public void draw(android.graphics.Canvas canvas, android.graphics.Paint paint) {
                paint.setColor(AppColors.WHITE);
                paint.setStrokeWidth(dp(context, 2));
                paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                float w = getWidth();
                float h = getHeight();
                canvas.drawLine(0, h * 0.25f, w, h * 0.25f, paint);
                canvas.drawLine(0, h * 0.50f, w, h * 0.50f, paint);
                canvas.drawLine(0, h * 0.75f, w, h * 0.75f, paint);
            }
        });
        menuBtn.setImageDrawable(hamburgerDrawable);
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

        // Profile Avatar Button (Right Edge - Clean icon, zero border)
        android.widget.FrameLayout profileBtn = new android.widget.FrameLayout(context);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(dp(context, 38), dp(context, 38));
        profileBtn.setLayoutParams(pLp);
        profileBtn.setBackgroundColor(Color.TRANSPARENT);

        android.widget.ImageView pIcon = new android.widget.ImageView(context);
        android.widget.FrameLayout.LayoutParams pIconLp = new android.widget.FrameLayout.LayoutParams(
                dp(context, 24),
                dp(context, 24),
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
