package com.ninmods.main.menu.nin;

import android.content.Context;
import com.ninmods.main.overlay.Floater;

/**
 * NIN Tactical Menu Floater Implementation.
 * Isolated in 'com.ninmods.main.menu.nin'.
 * Launches and manages NIN's distinct 3-layer HUD floating overlay.
 */
public class NinMenuFloater {

    public static synchronized void start(Context context) {
        Floater.start(context);
    }

    public static synchronized void stop() {
        Floater.stop();
    }

    public static boolean isRunning() {
        return Floater.isRunning();
    }
}
