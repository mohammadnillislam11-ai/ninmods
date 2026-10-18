package com.ninmods.menu.data

import android.content.Context
import android.content.SharedPreferences
import com.ninmods.menu.ipc.DriverMode

object PrefsHelper {
    private const val PREF_NAME = "ninmods_prefs"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    @Synchronized
    fun saveAll(context: Context? = null) {
        if (context != null) init(context)
    }

    @Synchronized
    fun loadAll(context: Context? = null) {
        if (context != null) init(context)
        prefs?.edit()?.apply {
            remove("driver_mode"); remove("aimbot"); remove("aimbotLegit"); remove("silentAim"); remove("autofire")
            remove("espBox"); remove("espSkeleton"); remove("espHealth"); remove("espName")
            remove("espDistance"); remove("flyHit"); remove("guestReset")
            apply()
        }
        resetFeatures()
        setDriverMode(DriverMode.RT)
    }

    @Synchronized
    fun setDriverMode(mode: DriverMode) {
        FeatureState.activeMode = mode.name
        FeatureState.activeModeCode = mode.code
    }

    @Synchronized
    fun getDriverMode(): DriverMode {
        return DriverMode.fromCode(FeatureState.activeModeCode)
    }

    fun resetFeatures() {
        FeatureState.aimbot = false
        FeatureState.aimbotLegit = false
        FeatureState.silentAim = false
        FeatureState.ignoreKnock = false
        FeatureState.smartKill = false
        FeatureState.autofire = false
        FeatureState.aimFov = 120.0f
        FeatureState.aimMode = 0
        FeatureState.espLine = false
        FeatureState.espBox = false
        FeatureState.espSkeleton = false
        FeatureState.espHealth = false
        FeatureState.espName = false
        FeatureState.espDistance = false
        FeatureState.espCount = false
        FeatureState.drawAimFov = false
        FeatureState.flyHack = false
        FeatureState.speedHack = false
        FeatureState.pcMovement = false
        FeatureState.ghostHack = false
        FeatureState.tpMark = false
        FeatureState.spinBot = false
        FeatureState.wallBug = false
        FeatureState.scaleDown = false
        FeatureState.flyHit = false
        FeatureState.flySpeed = 10.0f
        FeatureState.flyHeight = 5.0f
        FeatureState.guestReset = false
    }

    fun saveFloatingPos(x: Int, y: Int) {
        prefs?.edit()?.putInt("icon_x", x)?.putInt("icon_y", y)?.apply()
    }

    fun getFloatingPosX(defaultX: Int = 100): Int = prefs?.getInt("icon_x", defaultX) ?: defaultX
    fun getFloatingPosY(defaultY: Int = 300): Int = prefs?.getInt("icon_y", defaultY) ?: defaultY

    fun saveMenuPos(x: Int, y: Int) {
        prefs?.edit()?.putInt("menu_x", x)?.putInt("menu_y", y)?.apply()
    }

    fun getMenuPosX(defaultX: Int = -1): Int = prefs?.getInt("menu_x", defaultX) ?: defaultX
    fun getMenuPosY(defaultY: Int = -1): Int = prefs?.getInt("menu_y", defaultY) ?: defaultY

    fun setLoggedIn(loggedIn: Boolean) {
        prefs?.edit()?.putBoolean("is_logged_in", loggedIn)?.apply()
    }

    fun isLoggedIn(): Boolean = prefs?.getBoolean("is_logged_in", false) ?: false
}
