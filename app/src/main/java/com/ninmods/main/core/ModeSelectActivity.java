package com.ninmods.main.core;

import android.content.Intent;
import android.os.Bundle;

/**
 * ModeSelectActivity is the launcher activity of the app.
 *
 * The activity loads the native library and hands the control to the
 * dashboard, which owns the menu, the license flow and the overlay. The
 * separate mode selection screen that used to live here was never reachable:
 * onCreate has always gone straight to the dashboard, so the whole selector
 * (about 150 lines of view building) was dead code and has been removed.
 */
public class ModeSelectActivity extends BaseActivity {

    static {
        try {
            System.loadLibrary("ninmods");
        } catch (Throwable t) {
            android.util.Log.e("ModeSelectActivity", "Failed to load ninmods: " + t.getMessage());
        }
    }

    /**
     * Key of the selected tactical profile in the preferences.
     */
    public static final String PREF_KEY_MODE = "config_mode";

    /**
     * Opens the dashboard and closes the launcher activity.
     *
     * @param savedInstanceState saved state of the activity, not used
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, DashboardActivity.class);
        startActivity(intent);
        finish();
    }
}
