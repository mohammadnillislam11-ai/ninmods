package com.ninmods.menu.aimbot

object AimState {
    @Volatile var targetPitch: Float = 0f
    @Volatile var targetYaw: Float = 0f
    @Volatile var hasTarget: Boolean = false
    @Volatile var localPlayer: Long = 0L
    @Volatile var lastUpdate: Long = 0L
}
