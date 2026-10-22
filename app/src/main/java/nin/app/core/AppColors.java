package nin.app.core;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;

import nin.app.R;

public final class AppColors {

    private AppColors() {}

    // Theme & Brand (#E637E6 Magenta Theme)
    public static final int PRIMARY = Color.parseColor("#E637E6");
    public static final int ACCENT = Color.parseColor("#F76BF7");
    public static final int PRIMARY_VARIANT = Color.parseColor("#A81FA8");
    public static final int CYAN_BRIGHT = Color.parseColor("#E637E6");
    public static final int CYAN_STROKE = Color.parseColor("#D936D9");
    public static final int CYAN_GLOW = Color.parseColor("#FF55FF");
    public static final int CYAN_LIGHT = Color.parseColor("#F76BF7");
    public static final int ONLINE_GREEN = Color.parseColor("#6CF6A8");

    // Backgrounds
    public static final int DARK_BACKGROUND = Color.parseColor("#E60B0312");
    public static final int CARD_BACKGROUND = Color.parseColor("#F21C0A29");
    public static final int PANEL_BACKGROUND = Color.parseColor("#EE12051C");
    public static final int PANEL_HEADER_START = Color.parseColor("#2B0E38");
    public static final int PANEL_HEADER_END = Color.parseColor("#1B0824");
    public static final int TAB_SELECTED_BG = Color.parseColor("#521A6B");
    public static final int TAB_UNSELECTED_BG = Color.parseColor("#CC1B0824");
    public static final int TITLE_BAR_START = Color.parseColor("#3B134D");
    public static final int TITLE_BAR_END = Color.parseColor("#260B33");
    public static final int BTN_SAFE_START = Color.parseColor("#361147");
    public static final int BTN_SAFE_END = Color.parseColor("#4D1866");
    public static final int BTN_CLOSE_START = Color.parseColor("#E637E6");
    public static final int BTN_CLOSE_END = Color.parseColor("#9E1B9E");
    public static final int BTN_HIDE_BG = Color.parseColor("#1E0729");
    public static final int GEAR_BG = Color.parseColor("#2E0D3D");
    public static final int SIDEBAR_GLOW_START = Color.parseColor("#1B0626");
    public static final int SIDEBAR_GLOW_END = Color.parseColor("#380F4F");

    // Text Colors
    public static final int TEXT_WHITE = Color.parseColor("#FFFFFF");
    public static final int TEXT_PRIMARY = Color.parseColor("#FCEEFC");
    public static final int TEXT_TITLE = Color.parseColor("#FBEBFB");
    public static final int TEXT_SUBTITLE = Color.parseColor("#F5D6F5");
    public static final int TEXT_SAFE_MODE = Color.parseColor("#F2B6F2");
    public static final int TEXT_SELLER = Color.parseColor("#F5C2F5");
    public static final int TEXT_DIM = Color.parseColor("#D99ED9");
    public static final int TEXT_CLOSE = Color.parseColor("#FCE8FC");

    // Snowfall and Particles
    public static final int SNOW_PARTICLE = Color.parseColor("#C7D1FF");
    public static final int PARTICLE_GLOW_1 = Color.parseColor("#00F2FF");
    public static final int PARTICLE_GLOW_2 = Color.parseColor("#7000FF");

    // Mode Selector
    public static final int MODE_SCREEN_BG = Color.parseColor("#10172A");
    public static final int MODE_BTN_START = Color.parseColor("#5468F5");
    public static final int MODE_BTN_END = Color.parseColor("#5A6CF1");
    public static final int MODE_TAG_TEXT = Color.parseColor("#E9EEFF");
    public static final int MODE_SUBTITLE_TEXT = Color.parseColor("#DCE3FF");
    public static final int MODE_SECURITY_HINT = Color.parseColor("#1F2A44");
    public static final int MODE_CARD_BORDER = Color.parseColor("#1A5A6CF1");

    // Switch & Slider Controls
    public static final int BLACK = Color.parseColor("#000000");
    public static final int WHITE = Color.parseColor("#FFFFFF");
    public static final int SWITCH_BG_DARK = Color.parseColor("#1A1D2E");
    public static final int SWITCH_INACTIVE_TRACK = Color.parseColor("#444A66");
    public static final int TEXT_HIDE_BTN = Color.parseColor("#BEEEFF");
    public static final int SWITCH_TRACK_ON = Color.parseColor("#16D8FF");
    public static final int SWITCH_TRACK_OFF = Color.parseColor("#2C3A4A");
    public static final int SWITCH_THUMB_ON = Color.parseColor("#FFFFFF");
    public static final int SWITCH_THUMB_OFF = Color.parseColor("#F4FBFF");

    // Cyberpunk #E637E6 Magenta Theme
    public static final int MAGENTA_PRIMARY = Color.parseColor("#E637E6");
    public static final int MAGENTA_LIGHT = Color.parseColor("#F76BF7");
    public static final int MAGENTA_DARK = Color.parseColor("#A81FA8");
    public static final int MAGENTA_GLOW = Color.parseColor("#FF55FF");
    public static final int MAGENTA_SCREEN_BG = Color.parseColor("#0B0411");
    public static final int MAGENTA_SURFACE = Color.parseColor("#2BF76BF7"); // Same Translucent Pink Glass as Cards
    public static final int MAGENTA_CARD = Color.parseColor("#2BF76BF7"); // Pure Translucent Pink Glass (~17% #F76BF7)
    public static final int MAGENTA_CARD_BORDER = Color.parseColor("#44F76BF7"); // Delicate Pink Glass Rim (~27% #F76BF7)
    public static final int MAGENTA_CHIP = Color.parseColor("#32F76BF7"); // Translucent Pink Glass Input / Chip (~20% #F76BF7)
    public static final int MAGENTA_TEXT_DIM = Color.parseColor("#E2BDE8");
    public static final int MAGENTA_BTN_START = Color.parseColor("#E637E6");
    public static final int MAGENTA_BTN_END = Color.parseColor("#961A96");
    public static final int MAGENTA_SIDEBAR_HEADER = Color.parseColor("#1B092B");
    public static final int MAGENTA_ITEM_ACTIVE = Color.parseColor("#3D134F");
    public static final int STATUS_BADGE_GREEN = Color.parseColor("#22C55E");
    public static final int STATUS_BADGE_BG = Color.parseColor("#2022C55E");

    public static int withAlpha(int color, int alpha255) {
        return (color & 0x00FFFFFF) | ((alpha255 & 0xFF) << 24);
    }

    public static int get(Context context, int colorResId, int defaultColor) {
        if (context == null) return defaultColor;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return context.getColor(colorResId);
            } else {
                return context.getResources().getColor(colorResId);
            }
        } catch (Exception ignored) {
            return defaultColor;
        }
    }
}
