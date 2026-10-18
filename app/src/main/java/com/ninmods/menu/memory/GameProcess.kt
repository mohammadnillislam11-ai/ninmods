package com.ninmods.menu.memory

import com.ninmods.menu.ipc.DriverBridge

object GameProcess {
    @Volatile
    var detectedPackage: String = ""
        private set

    @Volatile
    private var lastPid: Int = -1

    fun getGamePackage(): String {
        getGamePid()
        return detectedPackage
    }

    fun getCachedPidFast(): Int = if (lastPid > 0) lastPid else getGamePid()

    fun getGamePid(): Int {
        // Fast-path 1: Direct /proc/<pid>/cmdline verification
        if (lastPid > 0) {
            try {
                val cmdline = java.io.File("/proc/$lastPid/cmdline").readText()
                if (cmdline.contains("freefire")) {
                    return lastPid
                }
            } catch (_: Exception) {}

            // Fast-path 2: Kernel-level validation via module base IOCTL (Ring 0 bypasses Android file sandbox)
            if (DriverBridge.isAvailable() && MemoryReader.currentHandle != 0L) {
                if (DriverBridge.nativeGetModuleBase(lastPid, GameConfig.LIBIL2CPP) != 0L) {
                    return lastPid
                }
            }
        }

        // Fast one-line root command checking normal Free Fire first, then MAX
        val out = RootHelper.execSu(
            "if p=$(pidof com.dts.freefireth 2>/dev/null); then echo \"TH:\$p\"; elif p=$(pidof com.dts.freefiremax 2>/dev/null); then echo \"MAX:\$p\"; fi"
        ).trim()

        if (out.startsWith("TH:")) {
            val p = out.removePrefix("TH:").trim().split("\\s+".toRegex()).firstOrNull()?.toIntOrNull()
            if (p != null && p > 0) {
                lastPid = p
                detectedPackage = GameConfig.GAME_PACKAGE_FF
                GameConfig.isMax = false
                return p
            }
        } else if (out.startsWith("MAX:")) {
            val p = out.removePrefix("MAX:").trim().split("\\s+".toRegex()).firstOrNull()?.toIntOrNull()
            if (p != null && p > 0) {
                lastPid = p
                detectedPackage = GameConfig.GAME_PACKAGE_MAX
                GameConfig.isMax = true
                return p
            }
        }

        lastPid = -1
        detectedPackage = ""
        return -1
    }

    fun isGameRunning(): Boolean = getGamePid() > 0
}
