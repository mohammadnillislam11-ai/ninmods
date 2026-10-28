package com.ninmods.main.menu;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.View;

/**
 * MOD MENU OVERLAY SERVICE
 * ------------------------
 * The service that owns and manages the floating mod menu window.
 *
 * The menu is created, attached to the WindowManager, and displayed as a
 * draggable floating icon / tactical card. The user has full manual control
 * over opening, collapsing, and closing the menu overlay.
 */
public class MenuOverlayService extends Service {

    private static final String TAG = "MenuOverlayService";

    /**
     * The live menu instance. Kept static so the dashboard or overlay manager
     * can expand, collapse, or query the menu without touching the WindowManager directly.
     */
    private static volatile ModMenu sMenu;

    private ModMenu menu;

    @Override
    public void onCreate() {
        super.onCreate();

        menu = new ModMenu(this);
        sMenu = menu;
        menu.SetWindowManagerWindowService();
        menu.ShowMenu();
        menu.setVisibility(View.VISIBLE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Expands the running menu (floating icon -> full mod menu card).
     */
    public static void expandMenu() {
        ModMenu live = sMenu;
        if (live != null) {
            live.expandMenu();
        }
    }

    /**
     * Collapses the running menu (full mod menu card -> floating icon).
     */
    public static void collapseToLogo() {
        ModMenu live = sMenu;
        if (live != null) {
            live.collapseToLogo();
        }
    }

    public static boolean isRunning() {
        return sMenu != null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (menu != null) {
            menu.onDestroy();
        }
        sMenu = null;
    }

    @Override
    public void onTaskRemoved(Intent intent) {
        super.onTaskRemoved(intent);
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }
}
