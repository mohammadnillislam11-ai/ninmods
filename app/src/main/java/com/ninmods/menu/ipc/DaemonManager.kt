package com.ninmods.menu.ipc

import android.content.Context
import android.os.Build
import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.FileOutputStream

object DaemonManager {
    private const val TAG = "NinDaemonMgr"
    private const val DAEMON_TMP = "/data/local/tmp/nin_daemon"

    @Volatile
    private var isStarting = false

    fun isRunning(): Boolean {
        if (!DriverBridge.isAvailable()) return false
        return DriverBridge.nativePingDaemon()
    }

    @Synchronized
    fun ensureDaemonRunning(context: Context): Boolean {
        if (isRunning()) return true
        if (isStarting) return false

        isStarting = true
        try {
            val abi = if (Build.SUPPORTED_ABIS.isNotEmpty() && Build.SUPPORTED_ABIS[0].contains("v8a")) {
                "arm64-v8a"
            } else {
                "armeabi-v7a"
            }

            val assetName = "nin_daemon_$abi"
            val internalFile = File(context.filesDir, "nin_daemon")

            var extracted = false
            try {
                context.assets.open(assetName).use { input ->
                    FileOutputStream(internalFile).use { output ->
                        input.copyTo(output)
                    }
                }
                internalFile.setExecutable(true, false)
                extracted = true
            } catch (e: Exception) {
                Log.w(TAG, "Asset $assetName not found, checking fallback: ${e.message}")
            }

            if (!extracted) {
                try {
                    context.assets.open("nin_daemon").use { input ->
                        FileOutputStream(internalFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    internalFile.setExecutable(true, false)
                    extracted = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to extract daemon from assets", e)
                }
            }

            val internalPath = internalFile.absolutePath

            // 1. Terminate any previous dead instances
            Shell.cmd("killall -9 nin_daemon 2>/dev/null").exec()
            Thread.sleep(150)

            // 2. Set SELinux permissive & relax driver node permissions
            Shell.cmd(
                "setenforce 0 2>/dev/null",
                "chmod 666 /dev/health_storage_service_alloc /dev/BYNb* /dev/sched_debug /dev/uevents_records /dev/wanbai /dev/entryi /dev/cdev 2>/dev/null"
            ).exec()

            // 3. Make internal executable and launch directly (treasure / Bull_Team pattern)
            if (extracted) {
                Shell.cmd(
                    "chmod 777 $internalPath",
                    "$internalPath </dev/null >/dev/null 2>&1 &"
                ).exec()

                // Wait up to 800ms for internal binary
                for (i in 0 until 8) {
                    Thread.sleep(100)
                    if (isRunning()) {
                        Log.i(TAG, "Master Daemon confirmed running from internal storage")
                        return true
                    }
                }

                // Fallback: Copy to /data/local/tmp and launch if internal is restricted
                Log.w(TAG, "Internal launch did not start, falling back to $DAEMON_TMP...")
                Shell.cmd(
                    "cp $internalPath $DAEMON_TMP 2>/dev/null",
                    "chmod 777 $DAEMON_TMP 2>/dev/null",
                    "$DAEMON_TMP </dev/null >/dev/null 2>&1 &"
                ).exec()

                for (i in 0 until 12) {
                    Thread.sleep(100)
                    if (isRunning()) {
                        Log.i(TAG, "Master Daemon confirmed running from /data/local/tmp")
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Master Daemon with libsu", e)
        } finally {
            isStarting = false
        }

        return isRunning()
    }

    fun stopDaemon() {
        try {
            Shell.cmd(
                "killall -9 nin_daemon 2>/dev/null",
                "rm -f $DAEMON_TMP 2>/dev/null"
            ).exec()
        } catch (_: Exception) {}
    }
}
