package nin.app.menu.bull;

import android.content.Context;

import nin.app.R;
import nin.app.core.Prefs;
import nin.app.menu.IMenuProject;
import nin.app.overlay.Floater;

/**
 * Bull Tactical Menu Project Implementation.
 * Isolated in package 'nin.app.menu.bull'.
 * Strictly zero cross-contamination with other menu brand code.
 * Configures Bull-specific combat switches, seekbars, and sliders.
 */
public class BullMenuProject implements IMenuProject {

    public static final String ID = "bull";
    private static final String PREF_BRAND_NAME = "brand_title_bull";
    private static final int THEME_COLOR = 0xFFFF5722; // Tactical Deep Orange

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
        return context.getString(R.string.menu_tactical_2_name);
    }

    @Override
    public String getSubtitle(Context context) {
        return context.getString(R.string.menu_tactical_2_sub);
    }

    @Override
    public int getIconResId() {
        return R.drawable.ic_menu_bull;
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
        // Bull Custom Category 1: Heavy Combat
        floater.onNativeAddCategory("Aim");
        floater.onNativeAddSwitch("Bull Penetration", 101);
        floater.onNativeAddSwitch("Rapid Fire Mod", 102);

        floater.onNativeAddCategory("Aim Setting");
        floater.onNativeAddSeekBar("Recoil Reducer", 75, 100, "Percentage", 110);
        floater.onNativeAddSeekBar("Trigger Speed", 10, 20, "Range", 111);

        // Bull Custom Category 2: Bull ESP
        floater.onNativeAddCategory("Esp");
        floater.onNativeAddSwitch("Bull 3D Box", 120);
        floater.onNativeAddSwitch("Bull Skeleton", 121);

        floater.onNativeAddCategory("Esp Setting");
        floater.onNativeAddSeekBar("ESP Opacity", 80, 100, "Percentage", 130);
    }
}
