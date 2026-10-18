package com.ninmods.menu.memory

import android.os.SystemClock

/**
 * Continuous Background Supervisor for Game Process & Driver Attachment.
 * Runs independently of UI visibility to ensure ESP, Aimbot, and Combat
 * maintain valid game PID and memory handle at all times.
 */
object ProcessSupervisor : Runnable {
    @Volatile private var isRunning = false
    private var worker: Thread? = null
    private var lastPidCheck = 0L

    fun start() {
        if (isRunning) return
        isRunning = true
        worker = Thread(this, "NIN_ProcessSupervisor").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning = false
        worker?.interrupt()
        worker = null
    }

    override fun run() {
        while (isRunning) {
            try {
                val now = SystemClock.uptimeMillis()
                val curPid = GameProcess.getCachedPidFast()
                val needsPid = curPid <= 0 || (now - lastPidCheck > 3000L)

                if (needsPid) {
                    lastPidCheck = now
                    GameProcess.getGamePid()
                }

                // Continuous RAM attachment: As long as game is alive, keep memory & libil2cpp base hot in RAM
                val pid = GameProcess.getCachedPidFast()
                if (pid > 0) {
                    MemoryReader.ensureAttached(pid)
                    if (MemoryReader.cachedBase == 0L) {
                        MemoryReader.getModuleBase(GameConfig.LIBIL2CPP)
                    }
                }
                Thread.sleep(1000L)
            } catch (_: InterruptedException) {
                break
            } catch (_: Exception) {}
        }
    }
}
