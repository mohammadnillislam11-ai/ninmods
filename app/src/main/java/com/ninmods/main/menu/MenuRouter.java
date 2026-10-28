package com.ninmods.main.menu;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.ninmods.main.auth.AuthManager;
import com.ninmods.main.core.Prefs;
import com.ninmods.main.menu.nin.NinMenuProject;
import com.ninmods.main.menu.ninlite.NinLiteMenuProject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central Dynamic Routing Engine for Tactical Menus.
 * Exclusively registers and routes between NINMODS (Kernel Driver Mode)
 * and NINLITE (Internal Daemon Mode). All obsolete brands removed.
 */
public class MenuRouter {

    private static final String TAG = "MenuRouter";
    private static final String PREF_ACTIVE_MENU_ID = "pref_active_menu_id";

    private static final Map<String, IMenuProject> REGISTRY = new LinkedHashMap<>();

    static {
        // 1. Primary NINMODS Tactical Menu (Kernel Mode / /dev/BYNb Ring-0)
        register(new NinMenuProject());

        // 2. NINLITE Cyberpunk Menu (Internal Root Daemon / process_vm_readv)
        register(new NinLiteMenuProject());
    }

    /**
     * Registers a menu project into the central registry.
     *
     * @param menu Menu project implementation.
     */
    private static synchronized void register(IMenuProject menu) {
        if (menu != null) {
            REGISTRY.put(menu.getId(), menu);
        }
    }

    /**
     * Retrieves the immutable list of registered menu projects (strictly NINMODS and NINLITE).
     *
     * @return List of active menu projects.
     */
    public static List<IMenuProject> getRegisteredMenus() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
    }

    /**
     * Looks up a specific menu project by its unique identifier.
     *
     * @param menuId Unique project ID ("nin" or "ninlite").
     * @return Matching IMenuProject instance, defaulting to NINMODS.
     */
    public static IMenuProject getMenuById(String menuId) {
        if (menuId == null) {
            return REGISTRY.get(NinMenuProject.ID);
        }
        IMenuProject project = REGISTRY.get(menuId);
        return project != null ? project : REGISTRY.get(NinMenuProject.ID);
    }

    /**
     * Retrieves the currently active menu project from user preferences.
     *
     * @param context Android context.
     * @return Active IMenuProject instance.
     */
    public static IMenuProject getActiveMenu(Context context) {
        String activeId = Prefs.with(context).readString(PREF_ACTIVE_MENU_ID, NinMenuProject.ID);
        return getMenuById(activeId);
    }

    /**
     * Sets the active menu project ID.
     *
     * @param context Android context.
     * @param menuId Unique menu identifier.
     */
    public static void setActiveMenu(Context context, String menuId) {
        if (menuId != null && REGISTRY.containsKey(menuId)) {
            Prefs.with(context).writeString(PREF_ACTIVE_MENU_ID, menuId);
        }
    }

    /**
     * Launches the currently active menu project configured in preferences.
     *
     * @param context Android context.
     */
    public static synchronized void launchActiveMenu(Context context) {
        if (context == null) return;
        IMenuProject active = getActiveMenu(context);
        String targetId = active != null ? active.getId() : NinMenuProject.ID;
        launchMenu(context, targetId);
    }

    /**
     * Launches a specific menu project by ID.
     * Enforces strict channel isolation: ensures other running overlays are stopped first.
     *
     * @param context Android context.
     * @param menuId Menu identifier to launch.
     */
    public static synchronized void launchMenu(Context context, String menuId) {
        if (context == null) return;

        // Verify active online license session before launching floating overlay
        String activeKey = AuthManager.getActiveLicenseKey(context);
        if (activeKey == null || activeKey.trim().isEmpty()) {
            Log.e(TAG, "Cannot launch floating menu: Active online license key is required!");
            Toast.makeText(context, "Active license key required! Please verify key online in Dashboard.", Toast.LENGTH_LONG).show();
            return;
        }

        String targetId = (menuId != null && REGISTRY.containsKey(menuId)) ? menuId : NinMenuProject.ID;
        setActiveMenu(context, targetId);

        // Stop all other running overlays before launching to preserve channel isolation
        for (IMenuProject project : REGISTRY.values()) {
            if (!project.getId().equals(targetId) && project.isRunning()) {
                Log.d(TAG, "Stopping conflicting running overlay: " + project.getId());
                project.stop(context);
            }
        }

        // Launch target menu
        IMenuProject target = getMenuById(targetId);
        if (target != null) {
            Log.d(TAG, "Launching tactical menu project: " + target.getId());
            target.launch(context);
        }
    }

    /**
     * Stops all currently running floating overlays.
     *
     * @param context Android context.
     */
    public static synchronized void stopAll(Context context) {
        if (context == null) return;
        for (IMenuProject project : REGISTRY.values()) {
            try {
                if (project.isRunning()) {
                    project.stop(context);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error stopping menu: " + project.getId(), t);
            }
        }
    }
}
