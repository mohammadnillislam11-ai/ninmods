package com.ninmods.main.menu.ninlite;

import android.content.Context;

import com.ninmods.main.R;
import com.ninmods.main.core.Prefs;
import com.ninmods.main.menu.IMenuProject;

/**
 * NINLITE Tactical Menu Project Implementation (root daemon engine).
 * Isolated in package 'com.ninmods.main.menu.ninlite'.
 * Features: Pure Root Daemon Engine + Cyberpunk Vertical Sidebar Floating Window.
 */
public class NinLiteMenuProject implements IMenuProject {

    public static final String ID = "ninlite";
    private static final String PREF_BRAND_NAME = "brand_title_ninlite";
    private static final int THEME_COLOR = 0xFF00E5FF; // Electric Cyan / Neon Blue

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
        return R.drawable.ic_menu_ninlite;
    }

    @Override
    public int getThemeColor() {
        return THEME_COLOR;
    }

    @Override
    public void launch(Context context) {
        NinLiteOverlayManager.start(context);
    }

    @Override
    public void stop(Context context) {
        NinLiteOverlayManager.stop();
    }

    @Override
    public boolean isRunning() {
        return NinLiteOverlayManager.isRunning();
    }
}
