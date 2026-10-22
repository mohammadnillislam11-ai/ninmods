package nin.app.overlay;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;

import nin.app.R;

public final class FloaterStyles {

    // Fallback constants (in case context is null)
    public static final int ColorDarkBg = 0xFF14081C;
    public static final int ColorSidebarBg = 0xFF0D0412;
    public static final int ColorBorder = 0xFF3D164A;
    public static final int ColorAccentPink = 0xFFE637E6;
    public static final int ColorSectionTitle = 0xFFF76BF7;
    public static final int ColorTextMuted = 0xFFD99ED9;
    public static final int ColorButtonDark = 0xFF220A2E;
    public static final int ColorButtonStroke = 0xFF4D1C63;

    private FloaterStyles() {}

    /**
     * Resolves color resource directly from res/values/colors.xml
     */
    public static int getColor(Context context, int colorResId) {
        if (context == null) return 0xFF000000;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return context.getColor(colorResId);
            } else {
                return context.getResources().getColor(colorResId);
            }
        } catch (Exception e) {
            return 0xFF000000;
        }
    }

    // Dynamic color getters reading directly from res/values/colors.xml
    public static int getColorDarkBg(Context context) {
        return getColor(context, R.color.floater_bg_dark);
    }

    public static int getColorSidebarBg(Context context) {
        return getColor(context, R.color.floater_sidebar_bg);
    }

    public static int getColorBorder(Context context) {
        return getColor(context, R.color.floater_border);
    }

    public static int getColorAccentPink(Context context) {
        return getColor(context, R.color.floater_accent_pink);
    }

    public static int getColorSectionTitle(Context context) {
        return getColor(context, R.color.floater_section_title);
    }

    public static int getColorTextPrimary(Context context) {
        return getColor(context, R.color.floater_text_primary);
    }

    public static int getColorTextMuted(Context context) {
        return getColor(context, R.color.floater_text_muted);
    }

    public static int getColorButtonDark(Context context) {
        return getColor(context, R.color.floater_button_dark);
    }

    public static int getColorButtonStroke(Context context) {
        return getColor(context, R.color.floater_button_stroke);
    }

    public static int getColorBadgeClose(Context context) {
        return getColor(context, R.color.floater_badge_close);
    }

    public static int getColorIconActive(Context context) {
        return getColor(context, R.color.floater_icon_active);
    }

    public static int getColorIconInactive(Context context) {
        return getColor(context, R.color.floater_icon_inactive);
    }

    public static int dp(Context context, int value) {
        if (context == null) return value;
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    public static int dp(Context context, float value) {
        if (context == null) return (int) value;
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    public static GradientDrawable createCardBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 10));
        drawable.setColor(getColorDarkBg(context));
        drawable.setStroke(dp(context, 1), getColorBorder(context));
        return drawable;
    }

    public static GradientDrawable createLeftRailBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        int r = dp(context, 10);
        drawable.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r}); // top-left, top-right, bottom-right, bottom-left
        drawable.setColor(getColorSidebarBg(context));
        return drawable;
    }

    public static GradientDrawable createActiveIconRailDrawable(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 8));
        drawable.setColor(getColorAccentPink(context));
        return drawable;
    }

    public static GradientDrawable createInactiveIconRailDrawable(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 8));
        drawable.setColor(0x00000000);
        return drawable;
    }
}
