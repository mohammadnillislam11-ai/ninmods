package com.ninmods.main.menu.cross;

import android.content.Context;

import com.ninmods.main.R;
import com.ninmods.main.core.Prefs;
import com.ninmods.main.menu.IMenuProject;

/**
 * Cross Tactical Menu Project Implementation.
 * Isolated in package 'com.ninmods.main.menu.cross'.
 * Strictly zero cross-contamination with other menu brand code.
 */
public class CrossMenuProject implements IMenuProject {

    public static final String ID = "cross";
    private static final String PREF_BRAND_NAME = "brand_title_cross";
    private static final int THEME_COLOR = CrossMenuFloater.THEME_CYAN;

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
        CrossMenuFloater.start(context);
    }

    @Override
    public void stop(Context context) {
        CrossMenuFloater.stop();
    }

    @Override
    public boolean isRunning() {
        return CrossMenuFloater.isRunning();
    }
}
