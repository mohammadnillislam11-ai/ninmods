package com.ninmods.menu.aimbot

import com.ninmods.menu.data.FeatureState
import com.ninmods.menu.ipc.DriverBridge
import com.ninmods.menu.memory.GameProcess
import com.ninmods.menu.memory.MemoryReader
import com.ninmods.menu.memory.GameConfig

/**
 * Aimbot Worker Thread.
 * ALL entity scanning, WorldToScreen, and aim vector calculations reside exclusively in C++ (gameoffsets.h / native_esp_aim.cpp).
 * This thread relays target angles directly to nativeWriteAim in volatile RAM.
 */
object AimbotThread : Runnable {
    @Volatile private var isRunning = false
    private var workerThread: Thread? = null
    private val aimDummyBuf = java.nio.ByteBuffer.allocateDirect(64 * 10 * 4).order(java.nio.ByteOrder.nativeOrder())
    private val diagBuffer = LongArray(10)

    fun start() {
        if (isRunning) return
        isRunning = true
        workerThread = Thread(this, "NIN_Aimbot_Worker").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning = false
        workerThread?.interrupt()
        workerThread = null
        resetTarget()
    }

    fun resetTarget() {
        AimState.targetPitch = 0f
        AimState.targetYaw = 0f
        AimState.hasTarget = false
        AimState.localPlayer = 0L
        AimState.lastUpdate = 0L
    }

    override fun run() {
        while (isRunning) {
            if (!FeatureState.aimbot) {
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }
            try {
                executeAimLoop()
                Thread.sleep(8)
            } catch (_: InterruptedException) {
                break
            } catch (_: Exception) {}
        }
    }

    private fun executeAimLoop() {
        val pid = GameProcess.getCachedPidFast()
        val h = MemoryReader.currentHandle
        if (pid <= 0 || h == 0L || !DriverBridge.isAvailable()) return

        val now = android.os.SystemClock.uptimeMillis()

        // If ESP overlay is not running, compute aim target natively via C++
        if (!FeatureState.espAny && now - AimState.lastUpdate > 25L) {
            aimDummyBuf.clear()
            DriverBridge.nativeScanEsp(
                pid,
                h,
                GameConfig.isMax,
                1920f,
                1080f,
                aimDummyBuf,
                diagBuffer,
                FeatureState.aimFov * 2.2f
            )
            if (diagBuffer[9] == 1L) {
                AimState.targetPitch = diagBuffer[7] / 1000.0f
                AimState.targetYaw = diagBuffer[8] / 1000.0f
                AimState.hasTarget = true
                AimState.localPlayer = diagBuffer[3]
                AimState.lastUpdate = now
            } else {
                AimState.hasTarget = false
            }
        }

        // High-speed C++ Native Aim Path (direct RT kernel ioctl write to memory)
        if (AimState.hasTarget && (now - AimState.lastUpdate < 350L) && AimState.localPlayer != 0L) {
            val ok = DriverBridge.nativeWriteAim(
                pid,
                h,
                AimState.localPlayer,
                AimState.targetPitch,
                AimState.targetYaw,
                5.0f,
                GameConfig.isMax
            )
            if (!ok) {
                AimState.hasTarget = false
            }
        }
    }
}
