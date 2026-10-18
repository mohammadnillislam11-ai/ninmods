package com.ninmods.menu.combat

import com.ninmods.menu.data.FeatureState
import com.ninmods.menu.ipc.DriverBridge
import com.ninmods.menu.memory.GameProcess
import com.ninmods.menu.memory.MemoryReader
import com.ninmods.menu.memory.GameConfig

/**
 * Combat Worker Thread.
 * ALL memory offsets and manipulation logic reside exclusively in C++ (gameoffsets.h / native_combat.cpp).
 * This thread only relays toggle states to nativeUpdateCombat.
 */
object CombatThread : Runnable {
    @Volatile private var isRunning = false
    private var worker: Thread? = null
    @Volatile private var wasActive = false

    fun start() {
        if (isRunning) return
        isRunning = true
        worker = Thread(this, "NIN_Combat_Worker").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning = false
        cleanupSync()
        worker?.interrupt()
        worker = null
    }

    fun onDriverModeChanged() {
        cleanupSync()
    }

    fun cleanupSync() {
        val pid = GameProcess.getCachedPidFast()
        val h = MemoryReader.currentHandle
        if (DriverBridge.isAvailable()) {
            if (pid > 0 && h != 0L) {
                DriverBridge.nativeUpdateCombat(
                    pid,
                    h,
                    GameConfig.isMax,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false
                )
            }
            DriverBridge.nativeResetCombat()
        }
        wasActive = false
    }

    override fun run() {
        while (isRunning) {
            val anyActive = FeatureState.combatAny
            val pid = GameProcess.getCachedPidFast()
            val h = MemoryReader.currentHandle

            if ((anyActive || wasActive) && pid > 0 && h != 0L && DriverBridge.isAvailable()) {
                DriverBridge.nativeUpdateCombat(
                    pid,
                    h,
                    GameConfig.isMax,
                    FeatureState.flyHack,
                    FeatureState.speedHack,
                    FeatureState.pcMovement,
                    FeatureState.ghostHack,
                    FeatureState.tpMark,
                    FeatureState.spinBot,
                    FeatureState.wallBug,
                    FeatureState.scaleDown
                )
                wasActive = anyActive
                if (FeatureState.tpMark) {
                    FeatureState.tpMark = false
                }
            }

            try {
                Thread.sleep(if (anyActive) 8L else 50L)
            } catch (_: InterruptedException) {
                break
            } catch (_: Exception) {}
        }
    }
}
