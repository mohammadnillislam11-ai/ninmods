package com.ninmods.menu.memory

import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG = "NINMODS_LOG"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val logFile: File by lazy {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) downloadDir.mkdirs()
        File(downloadDir, "ninmods_driver.log")
    }

    @Synchronized
    fun log(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun getLogPath(): String = ""

    fun runDiagnostics() {
        Thread {
            log("DIAG", "=================== NINMODS SYSTEM DIAGNOSTICS ===================")
            
            // 1. Root & SELinux status
            val rootRes = RootHelper.execSu("id")
            log("DIAG", "Root Identity (id): $rootRes")
            val selinux = RootHelper.execSu("getenforce")
            log("DIAG", "SELinux status: $selinux")

            // 2. Kernel release
            val uname = RootHelper.execSu("uname -a")
            log("DIAG", "Kernel release (uname -a): $uname")

            // 3. Loaded kernel modules in /proc/modules
            val modules = RootHelper.execSu("cat /proc/modules")
            log("DIAG", "Loaded kernel modules in /proc/modules:")
            if (modules.isBlank()) {
                log("DIAG", "  --> /proc/modules is EMPTY or unreadable via su!")
            } else {
                var foundDriverModule = false
                modules.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        val mName = line.split("\\s+".toRegex()).firstOrNull() ?: ""
                        if (mName.contains("nh") || mName.contains("4.14") || mName.contains("4.19") ||
                            mName.contains("5.10") || mName.contains("BYNb") || mName.contains("driver") ||
                            mName.contains("test") || mName.contains("wanbai") || mName.contains("entryi")) {
                            log("DIAG", "  [MATCHED DRIVER] $line")
                            foundDriverModule = true
                        } else {
                            log("DIAG", "  module: $line")
                        }
                    }
                }
                if (!foundDriverModule) {
                    log("DIAG", "  [ALERT] No obvious driver module found loaded in /proc/modules!")
                }
            }

            // 4. Driver major and sysfs class diagnostics
            val procDevices = RootHelper.execSu("cat /proc/devices 2>/dev/null | grep -E 'BYNb|mem_tool'").trim()
            val sysClass = RootHelper.execSu("ls -la /sys/class/mem_tool_class 2>/dev/null").trim()
            log("DIAG", "Kernel Driver Registration in /proc/devices: ${if (procDevices.isNotEmpty()) procDevices else "[NONE]"}")
            log("DIAG", "Kernel Driver sysfs /sys/class/mem_tool_class: ${if (sysClass.isNotEmpty()) sysClass else "[NONE]"}")

            // 5. Checking character device nodes
            val checkNodes = listOf(
                "/dev/health_storage_service_alloc",
                "/dev/BYNb",
                "/dev/uevents_records",
                "/dev/sched_debug",
                "/dev/cdev",
                "/dev/wanbai",
                "/dev/entryi",
                "/proc/uevents_records"
            )
            log("DIAG", "Driver Candidate Device Nodes status:")
            for (node in checkNodes) {
                val ls = RootHelper.execSu("ls -l $node 2>/dev/null").trim()
                if (ls.isNotBlank()) {
                    log("DIAG", "  [FOUND] $node -> $ls")
                } else {
                    log("DIAG", "  [NOT FOUND] $node")
                }
            }

            // 5. Native driver connection check
            val activeNode = MemoryReader.getActiveDriverNode()
            val isOnline = MemoryReader.isKernelActive()
            log("DIAG", "Native Driver Connection: isOnline=$isOnline activeNode=$activeNode")

            // 6. Game process check
            val pid = GameProcess.getGamePid()
            val pkg = GameProcess.detectedPackage
            log("DIAG", "Target Game Check: detectedPkg=$pkg pid=$pid")
            if (pid > 0) {
                val cmdline = RootHelper.execSu("cat /proc/$pid/cmdline 2>/dev/null")
                val libBase = MemoryReader.getModuleBase(GameConfig.LIBIL2CPP)
                log("DIAG", "Game /proc/$pid/cmdline: $cmdline")
                log("DIAG", "Game libil2cpp.so Base (IOCTL 0x803): 0x%x".format(libBase))
            } else {
                log("DIAG", "Game is not currently running.")
            }
            log("DIAG", "==================================================================")
            RootHelper.execSu("chmod 666 /sdcard/Download/ninmods_driver.log 2>/dev/null")
        }.start()
    }
}
