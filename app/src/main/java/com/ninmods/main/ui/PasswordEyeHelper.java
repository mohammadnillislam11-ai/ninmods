package com.ninmods.main.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.ninmods.main.core.AppColors;

/**
 * Enterprise Password Input Field & Vector Eye Toggle Builder.
 * Standardizes password masking (*) and revealing with smooth vector eye graphics
 * across Login, Signup, Reset Password, Profile, and Key Dialogs.
 */
public final class PasswordEyeHelper {

    private PasswordEyeHelper() {}

    public static class PasswordFieldBundle {
        public final LinearLayout container;
        public final EditText editText;
        public final ImageView eyeButton;
        public boolean isVisible;

        public PasswordFieldBundle(LinearLayout container, EditText editText, ImageView eyeButton) {
            this.container = container;
            this.editText = editText;
            this.eyeButton = eyeButton;
            this.isVisible = false;
        }
    }

    /**
     * Creates an input container with an embedded password field and eye toggle button.
     */
    public static PasswordFieldBundle createPasswordField(Context context, String hint) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 44));
        container.setLayoutParams(icLp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppColors.MAGENTA_CHIP);
        bg.setCornerRadius(dp(context, 8));
        bg.setStroke(dp(context, 1), AppColors.MAGENTA_CARD_BORDER);
        container.setBackground(bg);

        EditText et = new EditText(context);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        et.setLayoutParams(etLp);
        et.setBackgroundColor(Color.TRANSPARENT);
        et.setPadding(dp(context, 14), 0, dp(context, 8), 0);
        et.setHint(hint);
        et.setHintTextColor(AppColors.MAGENTA_TEXT_DIM);
        et.setTextColor(AppColors.WHITE);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setTransformationMethod(PasswordTransformationMethod.getInstance());
        container.addView(et);

        ImageView eyeBtn = new ImageView(context);
        LinearLayout.LayoutParams eyeLp = new LinearLayout.LayoutParams(dp(context, 40), dp(context, 40));
        eyeLp.rightMargin = dp(context, 4);
        eyeBtn.setLayoutParams(eyeLp);
        eyeBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        eyeBtn.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        eyeBtn.setImageDrawable(createEyeIcon(context, false));
        container.addView(eyeBtn);

        final PasswordFieldBundle bundle = new PasswordFieldBundle(container, et, eyeBtn);

        eyeBtn.setOnClickListener(v -> {
            bundle.isVisible = !bundle.isVisible;
            if (bundle.isVisible) {
                et.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                eyeBtn.setImageDrawable(createEyeIcon(context, true));
            } else {
                et.setTransformationMethod(PasswordTransformationMethod.getInstance());
                eyeBtn.setImageDrawable(createEyeIcon(context, false));
            }
            if (et.getText() != null) {
                et.setSelection(et.getText().length());
            }
        });

        return bundle;
    }

    /**
     * Vector Eye Toggle Drawable with anti-aliased geometry.
     */
    public static Drawable createEyeIcon(Context context, final boolean isOpen) {
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                float cx = bounds.exactCenterX();
                float cy = bounds.exactCenterY();
                float r = bounds.width() * 0.35f;

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dpF(context, 1.8f));
                paint.setColor(isOpen ? AppColors.MAGENTA_PRIMARY : AppColors.MAGENTA_TEXT_DIM);

                // Eye contour
                Path path = new Path();
                path.moveTo(cx - r, cy);
                path.quadTo(cx, cy - r * 0.85f, cx + r, cy);
                path.quadTo(cx, cy + r * 0.85f, cx - r, cy);
                canvas.drawPath(path, paint);

                // Pupil
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, r * 0.35f, paint);

                // Diagonal slash if closed/masked
                if (!isOpen) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dpF(context, 1.8f));
                    canvas.drawLine(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f, paint);
                }
            }

            @Override
            public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(ColorFilter colorFilter) {
                paint.setColorFilter(colorFilter);
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    private static float dpF(Context context, float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
