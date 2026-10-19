package com.ninmods.menu.memory

import android.util.Log
import com.topjohnwu.superuser.Shell

object RootHelper {
    private const val TAG = "NINMODS_ROOT"

    val isRooted: Boolean
        get() {
            return try {
                Shell.getShell().isRoot
            } catch (_: Exception) {
                false
            }
        }

    fun requestRoot(): Boolean {
        return try {
            Shell.getShell().isRoot
        } catch (e: Exception) {
            Log.e(TAG, "requestRoot error: ${e.message}")
            false
        }
    }

    fun execSu(command: String): String {
        return try {
            val result = Shell.cmd(command).exec()
            result.out.joinToString("\n").trim()
        } catch (e: Exception) {
            Log.e(TAG, "execSu error: ${e.message}")
            ""
        }
    }

    fun fixDriverPermissions(): String {
        return try {
            val script = """
                setenforce 0 2>/dev/null
                MAJOR=""
                for l in ${'$'}(cat /proc/devices 2>/dev/null | grep -E 'health_storage_service_alloc|health_storage_service|BYNb|mem_tool|sched_debug|uevents_records'); do
                    case "${'$'}l" in
                        [0-9]*)
                            MAJOR="${'$'}l"
                            break
                            ;;
                    esac
                done
                DEVNAME=${'$'}(ls /sys/class/health_storage_service_alloc /sys/class/mem_tool_class 2>/dev/null | head -n 1 | tr -d '\r\n')
                if [ -n "${'$'}MAJOR" ]; then
                    rm -f /dev/health_storage_service_alloc /dev/health_storage_service /dev/BYNb 2>/dev/null
                    mknod /dev/health_storage_service_alloc c "${'$'}MAJOR" 0 2>/dev/null
                    mknod /dev/health_storage_service c "${'$'}MAJOR" 1 2>/dev/null
                    mknod /dev/BYNb c "${'$'}MAJOR" 0 2>/dev/null
                fi
                chmod 666 /dev/health_storage_service_alloc 2>/dev/null
                chmod 666 /dev/health_storage_service 2>/dev/null
                chmod 666 /dev/BYNb* 2>/dev/null
                chmod 666 /dev/uevents_records 2>/dev/null
                chmod 666 /dev/wanbai 2>/dev/null
                chmod 666 /dev/entryi 2>/dev/null
                chmod 666 /dev/sched_debug 2>/dev/null
                chmod 666 /dev/cdev 2>/dev/null
                chmod 777 /data/local/tmp 2>/dev/null
                chmod 666 /data/local/tmp/.nin_driver_state 2>/dev/null
                echo "FIXED: MAJOR=${'$'}MAJOR DEVNAME=${'$'}DEVNAME"
                ls -l /dev/health_storage_service_alloc /dev/BYNb /data/local/tmp/.nin_driver_state 2>/dev/null
            """.trimIndent()
            val result = Shell.cmd(script).exec()
            val out = result.out.joinToString("\n").trim()
            Log.i(TAG, "fixDriverPermissions output:\n$out")
            out
        } catch (e: Exception) {
            Log.e(TAG, "fixDriverPermissions error: ${e.message}")
            ""
        }
    }
}
