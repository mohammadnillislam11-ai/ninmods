package com.ninmods.main.menu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.view.View;

import androidx.core.app.NotificationCompat;

import com.ninmods.main.R;

/**
 * MOD MENU OVERLAY SERVICE
 * ------------------------
 * The service that owns and manages the floating mod menu window.
 *
 * The menu is created, attached to the WindowManager, and displayed as a
 * draggable floating icon / tactical card. The user has full manual control
 * over opening, collapsing, and closing the menu overlay.
 *
 * The service runs in the foreground: the game is a different application, so
 * the menu app is in the background while the menu is on screen. A plain
 * background service is stopped by the system in that situation, which is why
 * the menu used to disappear as soon as the game was started.
 */
public class MenuOverlayService extends Service {

    private static final String TAG = "MenuOverlayService";
    private static final String CHANNEL_ID = "menu_overlay";
    private static final int NOTIFICATION_ID = 0x4E49;

    /**
     * The live menu instance. Kept static so the dashboard or overlay manager
     * can expand, collapse, or query the menu without touching the WindowManager directly.
     */
    private static volatile ModMenu sMenu;

    private ModMenu menu;

    @Override
    public void onCreate() {
        super.onCreate();

        /* The foreground notification has to exist before the service is
         * allowed to keep running in the background. */
        startInForeground();

        menu = new ModMenu(this);
        sMenu = menu;
        menu.SetWindowManagerWindowService();
        menu.ShowMenu();
        menu.setVisibility(View.VISIBLE);
    }

    /**
     * Moves the service into the foreground with a minimal notification.
     *
     * The notification only tells the user that the floating menu is running.
     * It carries no feature state and can not start anything by itself.
     */
    private void startInForeground() {
        try {
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.app_name),
                        NotificationManager.IMPORTANCE_MIN);
                channel.setDescription("Keeps the floating menu alive while the game is running");
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }

            PendingIntent pendingIntent = null;
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launchIntent != null) {
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, flags);
            }

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText("Floating menu is running")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setOngoing(true)
                    .setContentIntent(pendingIntent)
                    .build();

            startForeground(NOTIFICATION_ID, notification);
        } catch (Throwable ignored) {
            /* If the platform refuses the foreground state the menu still
             * works as long as the app stays in the foreground. */
        }
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

    /**
     * Reports whether the menu service is running.
     *
     * @return true when a menu instance exists
     */
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
