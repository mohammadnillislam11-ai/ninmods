package com.ninmods.main.menu.nin;

import android.content.Context;

import com.ninmods.main.R;
import com.ninmods.main.core.Prefs;
import com.ninmods.main.menu.IMenuProject;

/**
 * NIN Tactical Menu Project Implementation.
 * Isolated in package 'com.ninmods.main.menu.nin'.
 * Strictly zero cross-contamination with other menu brand code.
 */
public class NinMenuProject implements IMenuProject {

    public static final String ID = "nin";
    private static final String PREF_BRAND_NAME = "brand_title_nin";
    private static final int THEME_COLOR = 0xFFE637E6; // Tactical Magenta

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
        return context.getString(R.string.menu_tactical_1_name);
    }

    @Override
    public String getSubtitle(Context context) {
        return context.getString(R.string.menu_tactical_1_sub);
    }

    @Override
    public int getIconResId() {
        return R.drawable.nin_logo;
    }

    @Override
    public int getThemeColor() {
        return THEME_COLOR;
    }

    @Override
    public void launch(Context context) {
        NinMenuFloater.start(context);
    }

    @Override
    public void stop(Context context) {
        NinMenuFloater.stop();
    }

    @Override
    public boolean isRunning() {
        return NinMenuFloater.isRunning();
    }
}
