package com.ninmods.menu.memory

import android.util.Log
import com.ninmods.menu.ipc.DriverBridge
import com.ninmods.menu.ipc.DriverMode
import com.ninmods.menu.ipc.DaemonManager
import com.ninmods.menu.ipc.DriverStateReceiver
import com.ninmods.menu.data.PrefsHelper

object MemoryReader {
    private const val TAG = "NIN_MEM"
    private var driverFd: Int = -1

    @Volatile
    var currentHandle: Long = 0L
        private set
    @Volatile
    var currentPid: Int = -1
        private set
    @Volatile
    var cachedBase: Long = 0L

    @Synchronized
    fun isKernelActive(): Boolean {
        if (!DriverBridge.isAvailable()) return false

        if (driverFd > 2) return true
        var fd = DriverBridge.nativeOpen(DriverMode.RT.code)
        if (fd <= 2) {
            RootHelper.fixDriverPermissions()
            fd = DriverBridge.nativeOpen(DriverMode.RT.code)
        }
        if (fd > 2) {
            driverFd = fd
            return true
        }

        return DriverStateReceiver.isKernelActive()
    }

    @Synchronized
    fun ensureAttached(pid: Int): Boolean {
        if (pid <= 0 || !DriverBridge.isAvailable()) return false
        if (currentHandle != 0L && currentPid == pid && cachedBase > 0L) {
            return true
        }

        if (currentPid != pid) {
            clearAttachment()
        }

        currentPid = pid

        // 1. If Kernel mode is active, attach via direct kernel driver first
        if (DriverStateReceiver.isKernelActive() || driverFd > 2) {
            if (driverFd <= 2) {
                driverFd = DriverBridge.nativeOpen(DriverMode.RT.code)
                if (driverFd <= 2) {
                    RootHelper.fixDriverPermissions()
                    driverFd = DriverBridge.nativeOpen(DriverMode.RT.code)
                }
            }
            if (driverFd > 2) {
                val handle = DriverBridge.nativeAttach(driverFd, pid)
                currentHandle = if (handle != 0L) handle else pid.toLong()
                val base = getModuleBase(GameConfig.LIBIL2CPP)
                if (base > 0L) cachedBase = base
                Log.i(TAG, "Kernel Driver attached for pid=$pid handle=0x${java.lang.Long.toHexString(currentHandle)} base=0x${java.lang.Long.toHexString(cachedBase)}")
                return true
            }
        }

        // 2. If Master Daemon is active, attach via daemon socket
        if (DriverStateReceiver.isDaemonActive() || DaemonManager.isRunning()) {
            currentHandle = 1L
            val base = getModuleBase(GameConfig.LIBIL2CPP)
            if (base > 0L) cachedBase = base
            Log.i(TAG, "Master Daemon attached for pid=$pid, base=0x${java.lang.Long.toHexString(cachedBase)}")
            return true
        }

        // 3. Fallback driver attach
        if (driverFd <= 2) {
            driverFd = DriverBridge.nativeOpen(DriverMode.RT.code)
        }
        val handle = DriverBridge.nativeAttach(driverFd, pid)
        currentHandle = if (handle != 0L) handle else pid.toLong()
        val base = getModuleBase(GameConfig.LIBIL2CPP)
        if (base > 0L) cachedBase = base
        Log.i(TAG, "Driver attached for pid=$pid handle=0x${java.lang.Long.toHexString(currentHandle)} base=0x${java.lang.Long.toHexString(cachedBase)}")
        return true
    }

    fun readBytes(addr: Long, size: Int): ByteArray? {
        val h = currentHandle
        if (h == 0L || addr == 0L || size <= 0) return null
        return DriverBridge.nativeReadBytes(h, addr, size)
    }

    fun getModuleBase(moduleName: String): Long {
        if (cachedBase > 0L) return cachedBase

        val pid = if (currentPid > 0) currentPid else GameProcess.getGamePid()
        if (pid <= 0) return cachedBase

        // 1. Native bridge query (Daemon socket or Kernel IOCTL)
        if (DriverBridge.isAvailable()) {
            val b = DriverBridge.nativeGetModuleBase(pid, moduleName)
            if (b > 0L) {
                cachedBase = b
                return b
            }
        }

        // 2. Root Maps Shell Fallback (using su to read /proc/<pid>/maps directly)
        try {
            val out = RootHelper.execSu("cat /proc/$pid/maps | grep \"$moduleName\" | head -n 3").trim()
            if (out.isNotEmpty()) {
                val lines = out.lines()
                var fallbackAddr = 0L
                for (line in lines) {
                    val part = line.substringBefore('-').trim()
                    val addr = java.lang.Long.parseUnsignedLong(part, 16)
                    if (fallbackAddr == 0L) fallbackAddr = addr
                    if (addr > 0x10000000L) {
                        cachedBase = addr
                        Log.i(TAG, "Resolved $moduleName via root maps fallback: 0x${java.lang.Long.toHexString(addr)}")
                        return addr
                    }
                }
                if (fallbackAddr > 0L) {
                    cachedBase = fallbackAddr
                    return fallbackAddr
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Root maps fallback error: ${e.message}")
        }

        return cachedBase
    }

    fun getActiveDriverNode(): String {
        if (!DriverBridge.isAvailable()) return ""
        return DriverBridge.nativeGetActiveNode()
    }

    @Synchronized
    fun selectMode(mode: DriverMode) {
        PrefsHelper.setDriverMode(mode)
        close()
    }

    @Synchronized
    fun close() {
        clearAttachment()
        if (driverFd >= 0 && DriverBridge.isAvailable()) {
            DriverBridge.nativeClose(driverFd)
        }
        driverFd = -1
    }

    private fun clearAttachment() {
        if (currentHandle != 0L && DriverBridge.isAvailable()) {
            DriverBridge.nativeDetach(currentHandle)
        }
        currentHandle = 0L
        currentPid = -1
    }
}
