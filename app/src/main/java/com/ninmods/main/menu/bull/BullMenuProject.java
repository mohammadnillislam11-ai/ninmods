package com.ninmods.main.menu.bull;

import android.content.Context;

import com.ninmods.main.R;
import com.ninmods.main.core.Prefs;
import com.ninmods.main.menu.IMenuProject;

/**
 * Bull Tactical Menu Project Implementation.
 * Isolated in package 'com.ninmods.main.menu.bull'.
 * Strictly zero cross-contamination with other menu brand code.
 */
public class BullMenuProject implements IMenuProject {

    public static final String ID = "bull";
    private static final String PREF_BRAND_NAME = "brand_title_bull";
    private static final int THEME_COLOR = BullMenuFloater.THEME_ORANGE;

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
        BullMenuFloater.start(context);
    }

    @Override
    public void stop(Context context) {
        BullMenuFloater.stop();
    }

    @Override
    public boolean isRunning() {
        return BullMenuFloater.isRunning();
    }
}
