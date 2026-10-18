package com.ninmods.menu.logging

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * NINMODS Standalone Dual-Path Log File Module.
 * Path 1: Guaranteed local save into Android's public Download folder (/sdcard/Download/ninmods_runtime.log).
 * Path 2: Instant upload to Telegram Bot as a Document (.log attachment).
 */
object TelegramLogger {
    private const val TAG = "NIN_TelegramLogger"
    private const val PREFS_NAME = "nin_telegram_logger_prefs"
    private const val KEY_BOT_TOKEN = "bot_token"
    private const val KEY_CHAT_ID = "chat_id"
    private const val KEY_ENABLED = "logger_enabled"

    // Default credentials provided by user
    private const val DEFAULT_BOT_TOKEN = "8479669762:AAEdrFlN-MQdn76Psk65ibLi1pLrAlIG8bQ"
    private const val DEFAULT_CHAT_ID = "6326853456"

    @Volatile private var botToken: String = DEFAULT_BOT_TOKEN
    @Volatile private var chatId: String = DEFAULT_CHAT_ID
    @Volatile private var isEnabled: Boolean = true

    private var appContext: Context? = null
    private var internalLogFile: File? = null
    private val logLock = Any()
    private val inMemoryLog = StringBuilder()

    private val isUploading = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            botToken = prefs.getString(KEY_BOT_TOKEN, DEFAULT_BOT_TOKEN) ?: DEFAULT_BOT_TOKEN
            chatId = prefs.getString(KEY_CHAT_ID, DEFAULT_CHAT_ID) ?: DEFAULT_CHAT_ID
            isEnabled = prefs.getBoolean(KEY_ENABLED, true)
        } catch (_: Exception) {}

        synchronized(logLock) {
            try {
                val dir = context.filesDir.resolve("logs")
                if (!dir.exists()) dir.mkdirs()
                internalLogFile = dir.resolve("ninmods_runtime.log")

                // Rotate if file is larger than 2MB
                if (internalLogFile != null && internalLogFile!!.exists() && internalLogFile!!.length() > 2 * 1024 * 1024) {
                    internalLogFile!!.delete()
                }

                val header = buildString {
                    append("==================================================\n")
                    append("NINMODS RUNTIME LOG STARTED: ${logDateFormat.format(Date())}\n")
                    append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})\n")
                    append("==================================================\n")
                }
                appendRaw(header)
            } catch (e: Exception) {
                Log.e(TAG, "Init log file error: ${e.message}")
            }
        }

        // Initial copy to public Download folder
        syncToDownloadFolder()
    }

    private fun appendRaw(text: String) {
        inMemoryLog.append(text)
        internalLogFile?.let { file ->
            try {
                FileWriter(file, true).use { it.write(text) }
            } catch (_: Exception) {}
        }
    }

    fun appendLog(tag: String, message: String) {
        synchronized(logLock) {
            val ts = logDateFormat.format(Date())
            val line = "[$ts] [$tag] $message\n"
            appendRaw(line)
        }
        // Auto sync to Download folder
        syncToDownloadFolder()
    }

    fun logSystemStartup(engine: String, pid: Int, baseAddr: Long, pkgName: String) {
        val baseHex = if (baseAddr > 0) "0x" + java.lang.Long.toHexString(baseAddr) else "FAILED"
        val msg = "SYSTEM STARTUP: Engine=$engine | PID=$pid ($pkgName) | libil2cpp Base=$baseHex"
        appendLog("STARTUP", msg)
    }

    fun logToggle(featureName: String, isEnabled: Boolean) {
        val state = if (isEnabled) "ON (Enabled)" else "OFF (Disabled)"
        appendLog("USER_ACTION", "TOGGLE: $featureName -> $state")
    }

    fun logMemoryDiagnostics(
        eventTag: String,
        pid: Int,
        baseAddr: Long,
        facade: Long,
        match: Long,
        localPlayer: Long,
        dictCount: Long,
        renderedCount: Int,
        matrixValid: Boolean
    ) {
        val baseHex = "0x" + java.lang.Long.toHexString(baseAddr)
        val facadeHex = if (facade > 0) "0x" + java.lang.Long.toHexString(facade) else "NULL(0x0)"
        val matchHex = if (match > 0) "0x" + java.lang.Long.toHexString(match) else "NULL(NOT_IN_MATCH)"
        val lpHex = if (localPlayer > 0) "0x" + java.lang.Long.toHexString(localPlayer) else "NULL"
        val matrixStr = if (matrixValid) "VALID" else "INVALID"

        val msg = "DIAGNOSTICS [$eventTag] | PID=$pid | Base=$baseHex | Facade=$facadeHex | Match=$matchHex | LocalPlayer=$lpHex | DictCount=$dictCount | Rendered=$renderedCount | CamMatrix=$matrixStr"
        appendLog("MEM_DIAG", msg)
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val sb = StringBuilder("ERROR in $tag: $message")
        if (throwable != null) {
            sb.append("\nStacktrace:\n").append(throwable.stackTraceToString())
        }
        appendLog("ERROR", sb.toString())
    }

    /**
     * Guarantees that the latest log is written to /sdcard/Download/ninmods_runtime.log
     */
    fun syncToDownloadFolder(): File? {
        val content = synchronized(logLock) {
            if (inMemoryLog.isNotEmpty()) inMemoryLog.toString()
            else internalLogFile?.takeIf { it.exists() }?.readText() ?: "NINMODS Log Initialized\n"
        }

        val targetDirs = listOf(
            File("/sdcard/Download"),
            File("/storage/emulated/0/Download"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        for (dir in targetDirs) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val target = File(dir, "ninmods_runtime.log")
                target.writeText(content)
                return target
            } catch (_: Exception) {}
        }

        // Root fallback: copy via su directly to /sdcard/Download/
        try {
            val cacheFile = File(appContext?.cacheDir ?: File("/data/local/tmp"), "ninmods_tmp.log")
            cacheFile.writeText(content)
            val suCmd = "mkdir -p /sdcard/Download && cp '${cacheFile.absolutePath}' /sdcard/Download/ninmods_runtime.log && chmod 666 /sdcard/Download/ninmods_runtime.log"
            com.ninmods.menu.memory.RootHelper.execSu(suCmd)
            return File("/sdcard/Download/ninmods_runtime.log")
        } catch (_: Exception) {}

        return null
    }

    /**
     * User Button Action: Save to Download folder AND upload to Telegram bot
     */
    fun exportAndSend(context: Context, reason: String = "User Manual Tap") {
        appendLog("EXPORT", "User requested log export: $reason")

        // 1. Immediate save to Download folder
        val savedFile = syncToDownloadFolder()
        val downloadMsg = if (savedFile != null) {
            "✅ Saved to: /sdcard/Download/ninmods_runtime.log"
        } else {
            "⚠️ Saved in internal app storage"
        }

        Toast.makeText(context, downloadMsg, Toast.LENGTH_SHORT).show()

        // 2. Upload to Telegram in background
        if (isUploading.compareAndSet(false, true)) {
            Thread({
                var statusText = ""
                try {
                    val fileToUpload = savedFile ?: internalLogFile
                    if (fileToUpload != null && fileToUpload.exists() && fileToUpload.length() > 0) {
                        val ts = fileTimestampFormat.format(Date())
                        val caption = "📄 <b>NINMODS Runtime Log File</b>\n" +
                                "⏰ <i>${logDateFormat.format(Date())}</i>\n" +
                                "📌 <b>Reason:</b> <code>$reason</code>\n" +
                                "📱 <b>Device:</b> ${Build.MANUFACTURER} ${Build.MODEL}"

                        val result = uploadDocumentViaHttp(fileToUpload, "ninmods_log_$ts.log", caption)
                        statusText = if (result.success) {
                            "✅ Also sent to Telegram Bot!"
                        } else {
                            "⚠️ Telegram error: ${result.error}"
                        }
                    } else {
                        statusText = "⚠️ Log file is empty"
                    }
                } catch (e: Exception) {
                    statusText = "⚠️ Telegram exception: ${e.message}"
                } finally {
                    isUploading.set(false)
                    mainHandler.post {
                        try {
                            Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                        } catch (_: Exception) {}
                    }
                }
            }, "NIN_Telegram_Uploader").start()
        }
    }

    private data class UploadResult(val success: Boolean, val error: String = "")

    private fun uploadDocumentViaHttp(file: File, uploadName: String, caption: String): UploadResult {
        var conn: HttpURLConnection? = null
        val boundary = "===NINMODS_BOUNDARY_" + System.currentTimeMillis() + "==="
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        try {
            val url = URL("https://api.telegram.org/bot$botToken/sendDocument")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                useCaches = false
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            DataOutputStream(conn.outputStream).use { dos ->
                // chat_id
                dos.writeBytes(twoHyphens + boundary + lineEnd)
                dos.writeBytes("Content-Disposition: form-data; name=\"chat_id\"$lineEnd$lineEnd")
                dos.writeBytes(chatId + lineEnd)

                // caption
                dos.writeBytes(twoHyphens + boundary + lineEnd)
                dos.writeBytes("Content-Disposition: form-data; name=\"caption\"$lineEnd$lineEnd")
                dos.write(caption.toByteArray(Charsets.UTF_8))
                dos.writeBytes(lineEnd)

                // parse_mode
                dos.writeBytes(twoHyphens + boundary + lineEnd)
                dos.writeBytes("Content-Disposition: form-data; name=\"parse_mode\"$lineEnd$lineEnd")
                dos.writeBytes("HTML" + lineEnd)

                // document file
                dos.writeBytes(twoHyphens + boundary + lineEnd)
                dos.writeBytes("Content-Disposition: form-data; name=\"document\"; filename=\"$uploadName\"$lineEnd")
                dos.writeBytes("Content-Type: text/plain; charset=UTF-8$lineEnd$lineEnd")

                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        dos.write(buffer, 0, bytesRead)
                    }
                }
                dos.writeBytes(lineEnd)
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                dos.flush()
            }

            val responseCode = conn.responseCode
            return if (responseCode == 200) {
                UploadResult(true)
            } else {
                val errorStream = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Log.w(TAG, "Telegram error: $errorStream")
                UploadResult(false, "HTTP $responseCode: $errorStream")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload exception: ${e.message}")
            return UploadResult(false, e.message ?: "Network error")
        } finally {
            conn?.disconnect()
        }
    }
}
