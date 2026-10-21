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
        bitmap.eraseColor(PrimaryColor);
        return bitmap;
    }

    public static GradientDrawable createButtonBg(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 22));
        drawable.setColor(color);
        drawable.setStroke(dp(context, 1), AppColors.withAlpha(AppColors.TEXT_WHITE, 0x33));
        return drawable;
    }

    public static GradientDrawable createCardBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 26));
        drawable.setColor(CardBackground);
        drawable.setStroke(dp(context, 1), AppColors.withAlpha(AppColors.CYAN_BRIGHT, 0x66));
        return drawable;
    }

    public static GradientDrawable createPanelBackground(Context context, int cornerDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, cornerDp));
        drawable.setColor(AppColors.PANEL_BACKGROUND);
        drawable.setStroke(dp(context, 1), AppColors.withAlpha(AppColors.CYAN_STROKE, 0x55));
        return drawable;
    }

    public static GradientDrawable createSolidBg(Context context, int color, int cornerDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, cornerDp));
        drawable.setColor(color);
        drawable.setStroke(dp(context, 1), AppColors.MODE_CARD_BORDER);
        return drawable;
    }
}
