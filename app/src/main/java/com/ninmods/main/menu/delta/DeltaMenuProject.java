package com.ninmods.main.menu.delta;

import android.content.Context;

import com.ninmods.main.R;
import com.ninmods.main.core.Prefs;
import com.ninmods.main.menu.IMenuProject;

/**
 * Delta Tactical Menu Project Implementation.
 * Isolated in package 'com.ninmods.main.menu.delta'.
 * Strictly zero cross-contamination with other menu brand code.
 */
public class DeltaMenuProject implements IMenuProject {

    public static final String ID = "delta";
    private static final String PREF_BRAND_NAME = "brand_title_delta";
    private static final int THEME_COLOR = DeltaMenuFloater.THEME_EMERALD;

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
        DeltaMenuFloater.start(context);
    }

    @Override
    public void stop(Context context) {
        DeltaMenuFloater.stop();
    }

    @Override
    public boolean isRunning() {
        return DeltaMenuFloater.isRunning();
    }
}
