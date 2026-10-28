package com.ninmods.main.menu;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ninmods.main.core.Prefs;
import com.ninmods.main.menu.bull.BullMenuProject;
import com.ninmods.main.menu.cross.CrossMenuProject;
import com.ninmods.main.menu.delta.DeltaMenuProject;
import com.ninmods.main.menu.nin.NinMenuProject;
import com.ninmods.main.menu.ninlite.NinLiteMenuProject;

/**
 * Central Dynamic Routing Engine for Tactical Menus.
 * Coordinates multi-brand menu projects, enforcing strict folder isolation
 * and zero automatic cross-channel interference.
 */
public class MenuRouter {

    private static final String TAG = "MenuRouter";
    private static final String PREF_ACTIVE_MENU_ID = "pref_active_menu_id";

    private static final Map<String, IMenuProject> REGISTRY = new LinkedHashMap<>();

    static {
        // 1. Primary NINMODS Tactical Menu (Kernel Mode)
        register(new NinMenuProject());
        // 2. NINLITE Cyberpunk Menu (Pure Root Daemon Mode - Sidebar UI)
        register(new NinLiteMenuProject());
        // 3. Bull Tactical Brand Menu
        register(new BullMenuProject());
        // 4. Cross Tactical Brand Menu
        register(new CrossMenuProject());
        // 5. Delta Tactical Brand Menu
        register(new DeltaMenuProject());
    }

    private static synchronized void register(IMenuProject menu) {
        if (menu != null) {
            REGISTRY.put(menu.getId(), menu);
        }
    }

    /**
     * Get the immutable list of all registered tactical menus (up to 4 in a single series/row).
     */
    public static List<IMenuProject> getRegisteredMenus() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
    }

    /**
     * Look up a specific menu project by its unique ID.
     */
    public static IMenuProject getMenuById(String menuId) {
        if (menuId == null) {
            return REGISTRY.get(NinMenuProject.ID);
        }
        IMenuProject project = REGISTRY.get(menuId);
        return project != null ? project : REGISTRY.get(NinMenuProject.ID);
    }

    /**
     * Get the currently active/selected menu project.
     */
    public static IMenuProject getActiveMenu(Context context) {
        String activeId = Prefs.with(context).readString(PREF_ACTIVE_MENU_ID, NinMenuProject.ID);
        return getMenuById(activeId);
    }

    /**
     * Set the active/selected menu ID.
     */
    public static void setActiveMenu(Context context, String menuId) {
        if (menuId != null && REGISTRY.containsKey(menuId)) {
            Prefs.with(context).writeString(PREF_ACTIVE_MENU_ID, menuId);
        }
    }

    /**
     * Launch a specific menu project by ID.
     * Enforces strict channel isolation: ensures all running overlays are stopped first.
     */
    public static synchronized void launchMenu(Context context, String menuId) {
        if (context == null) return;

        // Verify active online license session before launching floating overlay
        String activeKey = com.ninmods.main.auth.AuthManager.getActiveLicenseKey(context);
        if (activeKey == null || activeKey.trim().isEmpty()) {
            Log.e(TAG, "Cannot launch floating menu: Active online license key is required!");
            android.widget.Toast.makeText(context, "Active license key required! Please verify key online in Dashboard.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        String sessionToken = com.ninmods.main.auth.AuthManager.getSessionToken(context);
        String hwid = com.ninmods.main.auth.AuthApiController.getHwid(context);
        long remSec = com.ninmods.main.auth.AuthManager.getRemainingSeconds(context);
        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            Log.e(TAG, "Cannot launch floating menu: no server-issued session token.");
            android.widget.Toast.makeText(context, "Session expired. Please verify your license key again.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        com.ninmods.main.auth.SecurityVault.setNativeSession(sessionToken, hwid, remSec);

        // 1. Strict Channel Isolation: Stop any currently running menu overlay first
        stopAllMenus(context);

        // 2. Resolve requested project
        IMenuProject targetMenu = getMenuById(menuId);
        if (targetMenu != null) {
            setActiveMenu(context, targetMenu.getId());
            Log.d(TAG, "Routing to tactical menu: " + targetMenu.getId());
            targetMenu.launch(context);
        } else {
            Log.e(TAG, "Unknown menu id: " + menuId);
        }
    }

    /**
     * Launch whichever menu is currently active.
     */
    public static void launchActiveMenu(Context context) {
        if (context == null) return;
        IMenuProject active = getActiveMenu(context);
        launchMenu(context, active != null ? active.getId() : NinMenuProject.ID);
    }

    /**
     * Stop all tactical menu overlays across all projects.
     */
    public static synchronized void stopAllMenus(Context context) {
        com.ninmods.main.overlay.NativeOverlayManager.stop();
        for (IMenuProject menu : REGISTRY.values()) {
            try {
                if (menu.isRunning()) {
                    menu.stop(context);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping menu: " + menu.getId(), e);
            }
        }
    }

    /**
     * Check if any menu project is currently running on screen.
     */
    public static boolean isAnyMenuRunning() {
        if (com.ninmods.main.overlay.NativeOverlayManager.isRunning()) {
            return true;
        }
        for (IMenuProject menu : REGISTRY.values()) {
            if (menu.isRunning()) {
                return true;
            }
        }
        return false;
    }
}
