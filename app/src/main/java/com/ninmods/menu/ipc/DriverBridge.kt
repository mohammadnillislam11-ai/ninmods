package com.ninmods.menu.ipc

import android.util.Log

enum class DriverMode(val code: Int) {
    NONE(0),
    RT(1),
    QX(2);

    companion object {
        fun fromCode(code: Int): DriverMode =
            entries.firstOrNull { it.code == code } ?: NONE
    }
}

object DriverBridge {
    private const val TAG = "NINMODS_MEM"
    private val isLoaded = try {
        System.loadLibrary("ninmods_mem")
        true
    } catch (error: UnsatisfiedLinkError) {
        Log.e(TAG, "Unable to load native driver bridge", error)
        false
    }

    fun isAvailable(): Boolean = isLoaded

    external fun nativeOpen(mode: Int): Int
    external fun nativeClose(fd: Int)
    external fun nativePingDaemon(): Boolean
    external fun nativeAttach(fd: Int, pid: Int): Long
    external fun nativeDetach(handle: Long)
    external fun nativeReadBytes(handle: Long, addr: Long, size: Int): ByteArray?
    external fun nativeGetModuleBase(pid: Int, moduleName: String): Long
    external fun nativeGetActiveNode(): String
    external fun nativeScanEsp(
        pid: Int,
        handle: Long,
        isMax: Boolean,
        screenW: Float,
        screenH: Float,
        directBuffer: java.nio.ByteBuffer,
        outDiag: LongArray?,
        aimFov: Float
    ): Int
    external fun nativeWriteAim(
        pid: Int,
        handle: Long,
        localPlayer: Long,
        targetPitch: Float,
        targetYaw: Float,
        smoothFactor: Float,
        isMax: Boolean
    ): Boolean
    external fun nativeUpdateCombat(
        pid: Int,
        handle: Long,
        isMax: Boolean,
        flyHack: Boolean,
        speedHack: Boolean,
        pcMovement: Boolean,
        ghostHack: Boolean,
        tpMark: Boolean,
        spinBot: Boolean,
        wallBug: Boolean,
        scaleDown: Boolean
    )
    external fun nativeResetCombat()
}
