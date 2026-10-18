package com.ninmods.menu.data

object FeatureState {
    // Mode and Capability States
    @Volatile var activeMode: String = "NONE"
    @Volatile var activeModeCode: Int = 0
    @Volatile var targetEspFps: Int = 60
    @Volatile var targetAimbotHz: Int = 120
    @Volatile var memoryReadAllowed: Boolean = false
    @Volatile var aimbotAllowed: Boolean = true
    @Volatile var skeletonAllowed: Boolean = true
    @Volatile var statusMessage: String = ""

    // Aimbot Features
    @Volatile var aimbot: Boolean = false
    @Volatile var aimbotLegit: Boolean = false
    @Volatile var silentAim: Boolean = false
    @Volatile var ignoreKnock: Boolean = false
    @Volatile var smartKill: Boolean = false
    @Volatile var autofire: Boolean = false
    @Volatile var aimFov: Float = 120.0f
    @Volatile var aimMode: Int = 0 // 0=Off, 1=Always

    // ESP Features
    @Volatile var espLine: Boolean = false
    @Volatile var espLineBottom: Boolean = true
    @Volatile var espBox: Boolean = false
    @Volatile var espCornerBox: Boolean = true
    @Volatile var espOffScreen: Boolean = true
    @Volatile var espSkeleton: Boolean = false
    @Volatile var espHealth: Boolean = false
    @Volatile var espName: Boolean = false
    @Volatile var espDistance: Boolean = false
    @Volatile var espCount: Boolean = false
    @Volatile var espMaxDist: Float = 200.0f
    @Volatile var drawAimFov: Boolean = false

    // Combat / Brutal RAM Features
    @Volatile var flyHack: Boolean = false
    @Volatile var speedHack: Boolean = false
    @Volatile var pcMovement: Boolean = false
    @Volatile var ghostHack: Boolean = false
    @Volatile var tpMark: Boolean = false
    @Volatile var spinBot: Boolean = false
    @Volatile var wallBug: Boolean = false
    @Volatile var scaleDown: Boolean = false

    // Legacy Fly Hit (compatible)
    @Volatile var flyHit: Boolean = false
    @Volatile var flySpeed: Float = 10.0f
    @Volatile var flyHeight: Float = 5.0f

    // Guest Reset & Match Status
    @Volatile var guestReset: Boolean = false
    @Volatile var isInMatch: Boolean = false

    val espAny: Boolean
        get() = espLine || espBox || (espSkeleton && skeletonAllowed) || espHealth || espDistance || espName || espCount || espOffScreen || drawAimFov

    val combatAny: Boolean
        get() = flyHack || speedHack || pcMovement || ghostHack || tpMark || spinBot || wallBug || scaleDown
}

