package nin.app.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.util.Base64;
import android.util.TypedValue;

import nin.app.core.AppColors;

public final class FloaterStyles {

    public static final int PrimaryColor = AppColors.PRIMARY;
    public static final int AccentColor = AppColors.ACCENT;
    public static final int DarkBackground = AppColors.DARK_BACKGROUND;
    public static final int CardBackground = AppColors.CARD_BACKGROUND;

    public static final int ColorDarkBg = 0xFF11121C;
    public static final int ColorSidebarBg = 0xFF0D0E17;
    public static final int ColorBorder = 0xFF1E2030;
    public static final int ColorAccentBlue = 0xFF2962FF;
    public static final int ColorSectionTitle = 0xFF3D5AFE;
    public static final int ColorTextMuted = 0xFF8C8FA1;
    public static final int ColorButtonDark = 0xFF1E2030;
    public static final int ColorButtonStroke = 0xFF2C2F46;

    private FloaterStyles() {}

    public static int dp(Context context, int value) {
        if (context == null) return value;
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    public static Bitmap base64ToBitmap(String base64Str) {
        try {
            byte[] decodedBytes = Base64.decode(base64Str, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Bitmap createDefaultIcon() {
        Bitmap bitmap = Bitmap.createBitmap(55, 45, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xFF2962FF);
        return bitmap;
    }

    public static GradientDrawable createCardBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 10));
        drawable.setColor(ColorDarkBg);
        drawable.setStroke(dp(context, 1), ColorBorder);
        return drawable;
    }

    public static GradientDrawable createLeftRailBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        int r = dp(context, 10);
        drawable.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r}); // top-left, top-right, bottom-right, bottom-left
        drawable.setColor(ColorSidebarBg);
        return drawable;
    }

    public static GradientDrawable createActiveTabDrawable(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 6));
        drawable.setColor(ColorAccentBlue);
        return drawable;
    }

    public static GradientDrawable createInactiveTabDrawable(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 6));
        drawable.setColor(0x00000000);
        return drawable;
    }

    public static GradientDrawable createSelectorBtnDrawable(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 4));
        drawable.setColor(ColorButtonDark);
        drawable.setStroke(dp(context, 1), ColorButtonStroke);
        return drawable;
    }

    public static GradientDrawable createBadgeDrawable(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 4));
        drawable.setColor(ColorButtonDark);
        drawable.setStroke(dp(context, 1), ColorAccentBlue);
        return drawable;
    }

    public static GradientDrawable createActiveIconRailDrawable(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 8));
        drawable.setColor(ColorAccentBlue);
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

