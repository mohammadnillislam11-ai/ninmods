package com.ninmods.menu.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject
import java.io.File

class DriverStateReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DriverStateReceiver"
        const val ACTION_DRIVER_STATE = "com.ninmods.action.DRIVER_STATE"
        const val LOADER_PACKAGE = "com.ninmods.loader"
        const val STATE_FILE_PATH = "/data/local/tmp/.nin_driver_state"

        @Volatile
        var currentMode: String = "NONE"
            private set

        @Volatile
        var currentStatus: String = "STOPPED"
            private set

        var onStateChangeListener: ((mode: String, status: String) -> Unit)? = null

        fun isKernelActive(): Boolean {
            if (currentMode == "NONE") syncStateFromFile()
            return currentMode == "KERNEL_RT" && currentStatus == "ACTIVE"
        }

        fun isDaemonActive(): Boolean {
            if (currentMode == "NONE") syncStateFromFile()
            return currentMode == "ROOT_RAM" && currentStatus == "ACTIVE"
        }

        fun syncStateFromFile() {
            try {
                var text = ""
                val file = File(STATE_FILE_PATH)
                if (file.exists() && file.canRead()) {
                    text = file.readText().trim()
                }
                if (text.isEmpty()) {
                    text = com.ninmods.menu.memory.RootHelper.execSu("cat $STATE_FILE_PATH 2>/dev/null").trim()
                }
                if (text.isNotEmpty() && text.startsWith("{")) {
                    val json = JSONObject(text)
                    val mode = json.optString("mode", "NONE")
                    val status = json.optString("status", "STOPPED")
                    if (mode.isNotEmpty() && status.isNotEmpty() && (mode != currentMode || status != currentStatus)) {
                        updateState(mode, status)
                    }
                }
                if (currentMode == "NONE" || currentStatus != "ACTIVE") {
                    val hasDev = com.ninmods.menu.memory.RootHelper.execSu("test -e /dev/health_storage_service_alloc && echo 'YES' || test -e /dev/BYNb && echo 'YES' || echo 'NO'").trim()
                    if (hasDev == "YES") {
                        updateState("KERNEL_RT", "ACTIVE")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "syncStateFromFile error: ${e.message}")
            }
        }

        fun updateState(mode: String, status: String) {
            currentMode = mode
            currentStatus = status
            Log.i(TAG, "Driver state updated: mode=$mode, status=$status")
            onStateChangeListener?.invoke(mode, status)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || intent.action != ACTION_DRIVER_STATE) return
        val mode = intent.getStringExtra("mode") ?: "NONE"
        val status = intent.getStringExtra("status") ?: "STOPPED"
        Log.i(TAG, "Broadcast received: mode=$mode, status=$status from action=${intent.action}")
        updateState(mode, status)
    }
}

