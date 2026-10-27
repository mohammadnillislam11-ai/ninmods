package com.ninmods.main.menu;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;

import com.ninmods.main.core.NativeBridge;

/**
 * MOD MENU OVERLAY SERVICE
 * ------------------------
 * The service that owns the floating mod menu window.
 *
 * This is the counterpart of the launcher service of the reference mod menu:
 * the service creates the menu, attaches it to the window manager and keeps
 * the menu visible only while the game it belongs to is running. The menu
 * itself is never destroyed and recreated - it is only hidden and shown.
 */
public class MenuOverlayService extends Service {

    private static final String TAG = "MenuOverlayService";

    /**
     * The live menu instance. It is kept so that the rest of the app (the
     * dashboard, the overlay manager) can expand or collapse the menu
     * without touching the window manager itself.
     */
    private static volatile ModMenu sMenu;

    ModMenu menu;
    private final Handler mMainHandler = new Handler(android.os.Looper.getMainLooper());
    private volatile boolean mDestroyed = false;

    @Override
    public void onCreate() {
        super.onCreate();

        menu = new ModMenu(this);
        sMenu = menu;
        menu.SetWindowManagerWindowService();
        menu.ShowMenu();

        new Thread(() -> {
            while (!mDestroyed) {
                final boolean running = isGameRunning();
                mMainHandler.post(() -> {
                    if (!mDestroyed && menu != null) {
                        menu.setVisibility(running ? View.VISIBLE : View.INVISIBLE);
                    }
                });
                try {
                    java.lang.Thread.sleep(1200);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }, "MenuOverlayGamePoller").start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * The reference menu lives INSIDE the game process, so it can ask the
     * system for its own process importance to find out whether the game is
     * in the foreground.
     *
     * NINMODS is a separate app, so the same question is answered by the
     * target game: while the game process is alive the menu stays visible,
     * when the game is gone the menu hides itself. (Since Android 5.1 the
     * list of the running processes of other apps is not available any
     * more, so the pid of the detected game is the only reliable signal.)
     */
    private boolean isGameRunning() {
        try {
            return NativeBridge.getDetectedGamePid() > 0;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to read the detected game pid", t);
            return false;
        }
    }


    /**
     * Expands the running menu (icon -> menu card).
     */
    public static void expandMenu() {
        ModMenu live = sMenu;
        if (live != null) {
            live.expandMenu();
        }
    }

    /**
     * Collapses the running menu (menu card -> icon).
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

    //Destroy our View
    @Override
    public void onDestroy() {
        mDestroyed = true;
        super.onDestroy();
        if (menu != null) {
            menu.onDestroy();
        }
        sMenu = null;
    }

    //Same as above so it wont crash in the background and therefore use alot of Battery life
    @Override
    public void onTaskRemoved(Intent intent) {
        super.onTaskRemoved(intent);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        stopSelf();
    }

    //Override our Start Command so the Service doesnt try to recreate itself when the App is closed
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }
}
