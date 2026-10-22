package nin.app.menu.cross;

import android.content.Context;

import nin.app.R;
import nin.app.core.Prefs;
import nin.app.menu.IMenuProject;
import nin.app.overlay.Floater;

/**
 * Cross Tactical Menu Project Implementation.
 * Isolated in package 'nin.app.menu.cross'.
 * Strictly zero cross-contamination with other menu brand code.
 * Configures Cross-specific sniper/precision switches, seekbars, and sliders.
 */
public class CrossMenuProject implements IMenuProject {

    public static final String ID = "cross";
    private static final String PREF_BRAND_NAME = "brand_title_cross";
    private static final int THEME_COLOR = 0xFF00E5FF; // Tactical Electric Cyan

    private boolean isRunning = false;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName(Context context) {
        String customName = Prefs.with(context).readString(PREF_BRAND_NAME, null);
        if (customName != null && !customName.trim().isEmpty()) {
            return customName;
        }
        return context.getString(R.string.menu_tactical_3_name);
    }

    @Override
    public String getSubtitle(Context context) {
        return context.getString(R.string.menu_tactical_3_sub);
    }

    @Override
    public int getIconResId() {
        return R.drawable.ic_menu_cross;
    }

    @Override
    public int getThemeColor() {
        return THEME_COLOR;
    }

    @Override
    public void launch(Context context) {
        isRunning = true;
        Floater.start(context);
    }

    @Override
    public void stop(Context context) {
        isRunning = false;
        Floater.stop();
    }

    @Override
    public boolean isRunning() {
        return isRunning && Floater.isRunning();
    }

    @Override
    public void configureBrandOptions(Floater floater, Context context) {
        if (floater == null) return;
        // Cross Custom Category 1: Sniper Focus
        floater.onNativeAddCategory("Aim");
        floater.onNativeAddSwitch("Crosshair Dot", 201);
        floater.onNativeAddSwitch("Scope Auto-Lock", 202);

        floater.onNativeAddCategory("Aim Setting");
        floater.onNativeAddSeekBar("Scope Sensitivity", 100, 200, "Range", 210);
        floater.onNativeAddSeekBar("Drop Compensation", 25, 50, "Range", 211);

        // Cross Custom Category 2: Precision Radar
        floater.onNativeAddCategory("Esp");
        floater.onNativeAddSwitch("Cross Radar Dot", 220);
        floater.onNativeAddSwitch("Distance Tag", 221);

        floater.onNativeAddCategory("Esp Setting");
        floater.onNativeAddSeekBar("Radar Zoom", 2, 5, "Range", 230);
    }
}
