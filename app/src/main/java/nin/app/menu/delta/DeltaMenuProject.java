package nin.app.menu.delta;

import android.content.Context;

import nin.app.R;
import nin.app.core.Prefs;
import nin.app.menu.IMenuProject;
import nin.app.overlay.Floater;

/**
 * Delta Tactical Menu Project Implementation.
 * Isolated in package 'nin.app.menu.delta'.
 * Strictly zero cross-contamination with other menu brand code.
 * Configures Delta-specific stealth/radar switches, seekbars, and sliders.
 */
public class DeltaMenuProject implements IMenuProject {

    public static final String ID = "delta";
    private static final String PREF_BRAND_NAME = "brand_title_delta";
    private static final int THEME_COLOR = 0xFF00E676; // Tactical Neon Emerald

    private boolean isRunning = false;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName(Context context) {
        String customName = Prefs.with(context).readString(PREF_BRAND_NAME, "");
        if (customName != null && !customName.trim().isEmpty()) {
            return customName;
        }
        return context.getString(R.string.menu_tactical_4_name);
    }

    @Override
    public String getSubtitle(Context context) {
        return context.getString(R.string.menu_tactical_4_sub);
    }

    @Override
    public int getIconResId() {
        return R.drawable.ic_menu_delta;
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
        // Delta Custom Category 1: Stealth Movement
        floater.onNativeAddCategory("Aim");
        floater.onNativeAddSwitch("Silent Step", 301);
        floater.onNativeAddSwitch("Ghost Anti-Report", 302);

        floater.onNativeAddCategory("Aim Setting");
        floater.onNativeAddSeekBar("Speed Multiplier", 1, 5, "Range", 310);

        // Delta Custom Category 2: Radar Telemetry
        floater.onNativeAddCategory("Esp");
        floater.onNativeAddSwitch("Mini-Map Sweep", 320);
        floater.onNativeAddSwitch("Vehicle Detector", 321);

        floater.onNativeAddCategory("Esp Setting");
        floater.onNativeAddSeekBar("Detection Radius", 200, 500, "Distance", 330);
    }
}
