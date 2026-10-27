package com.ninmods.main.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
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

        android.graphics.drawable.GradientDrawable headerBg = new android.graphics.drawable.GradientDrawable();
        headerBg.setColor(AppColors.MAGENTA_CARD);
        headerBg.setStroke(dp(context, 1), AppColors.MAGENTA_CARD_BORDER);
        header.setBackground(headerBg);

        // 3-Line Hamburger Menu Button (opens sidebar drawer)
        HamburgerView menuBtn = new HamburgerView(context);
        menuBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
        menuBtn.setContentDescription("Open navigation menu");
        menuBtn.setFocusable(true);
        menuBtn.setClickable(true);
        menuBtn.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.setAlpha(0.65f);
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1.0f);
            }
            return false;
        });
        menuBtn.setOnClickListener(v -> {
            if (headerListener != null) {
                headerListener.onMenuClick();
            }
        });
        header.addView(menuBtn);

        LinearLayout brandBox = new LinearLayout(context);
        brandBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        brandBox.setOrientation(LinearLayout.VERTICAL);
        brandBox.setPadding(dp(context, 8), 0, 0, 0);

        TextView title = new TextView(context);
        title.setText(context.getString(R.string.app_name));
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        brandBox.addView(title);

        header.addView(brandBox);

        // Profile Avatar Button (Right Edge - Clean icon, zero border)
        android.widget.FrameLayout profileBtn = new android.widget.FrameLayout(context);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48));
        profileBtn.setLayoutParams(pLp);
        profileBtn.setBackgroundColor(Color.TRANSPARENT);
        profileBtn.setContentDescription("Open profile");
        profileBtn.setFocusable(true);
        profileBtn.setClickable(true);
        profileBtn.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.setAlpha(0.65f);
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1.0f);
            }
            return false;
        });

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

    public static class HamburgerView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public HamburgerView(Context context) {
            super(context);
            paint.setColor(AppColors.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float stroke = dp(getContext(), 2.4f);
            paint.setStrokeWidth(stroke);

            float padX = dp(getContext(), 10f);
            float startX = padX;
            float endX = w - padX;

            float midY = h / 2.0f;
            float gap = dp(getContext(), 6.5f);

            // 3 horizontal white lines (crisp, rounded caps)
            canvas.drawLine(startX, midY - gap, endX, midY - gap, paint);
            canvas.drawLine(startX, midY, endX, midY, paint);
            canvas.drawLine(startX, midY + gap, endX, midY + gap, paint);
        }

        private static float dp(Context ctx, float value) {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    value,
                    ctx.getResources().getDisplayMetrics()
            );
        }
    }
}
