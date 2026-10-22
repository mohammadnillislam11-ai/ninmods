package nin.app.menu.nin;

import android.content.Context;
import nin.app.overlay.Floater;

/**
 * NIN Tactical Menu Floater Implementation.
 * Isolated in 'nin.app.menu.nin'.
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
