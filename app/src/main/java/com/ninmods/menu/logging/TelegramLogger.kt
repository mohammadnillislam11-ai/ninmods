package com.ninmods.menu.logging

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * NINMODS Standalone Telegram Bot File Logger Module.
 * Features:
 * - Appends all events (toggles, memory reads, facade/match diagnostics, errors) into a local file.
 * - Dispatches log files directly to Telegram Bot as a Document (.log attachment) via multipart/form-data.
 * - Automatic debounce & flush mechanism (prevents spamming while ensuring no log is lost).
 * - Zero third-party dependencies (pure standard Android/Java network API).
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
    private var logFile: File? = null
    private val logLock = Any()

    private val isUploading = AtomicBoolean(false)
    private var lastUploadTime = 0L
    private val uploadHandler = android.os.Handler(android.os.Looper.getMainLooper())

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
                logFile = dir.resolve("ninmods_runtime.log")

                // Rotate if file is larger than 2MB
                if (logFile != null && logFile!!.exists() && logFile!!.length() > 2 * 1024 * 1024) {
                    logFile!!.delete()
                }

                appendInternal("==================================================")
                appendInternal("NINMODS RUNTIME LOG INITIALIZED: ${logDateFormat.format(Date())}")
                appendInternal("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT}, Android ${Build.VERSION.RELEASE})")
                appendInternal("==================================================")
            } catch (e: Exception) {
                Log.e(TAG, "Init log file error: ${e.message}")
            }
        }
    }

    fun updateConfig(context: Context, token: String, chat: String, enabled: Boolean) {
        botToken = token.trim()
        chatId = chat.trim()
        isEnabled = enabled
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_BOT_TOKEN, botToken)
                .putString(KEY_CHAT_ID, chatId)
                .putBoolean(KEY_ENABLED, isEnabled)
                .apply()
        } catch (_: Exception) {}
    }

    private fun appendInternal(line: String) {
        val target = logFile ?: return
        try {
            val ts = logDateFormat.format(Date())
            val entry = "[$ts] $line\n"
            FileWriter(target, true).use { it.write(entry) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log: ${e.message}")
        }
    }

    /**
     * Log a line into local file and schedule a file upload to Telegram
     */
    fun appendAndScheduleUpload(tag: String, message: String, uploadDelayMs: Long = 4000L) {
        synchronized(logLock) {
            appendInternal("[$tag] $message")
        }
        scheduleFileUpload(uploadDelayMs, "Event: $tag")
    }

    /**
     * Log system startup, engine status, and driver state
     */
    fun logSystemStartup(engine: String, pid: Int, baseAddr: Long, pkgName: String) {
        val baseHex = if (baseAddr > 0) "0x" + java.lang.Long.toHexString(baseAddr) else "FAILED"
        val msg = "SYSTEM STARTUP: Engine=$engine | PID=$pid ($pkgName) | libil2cpp Base=$baseHex"
        appendAndScheduleUpload("STARTUP", msg, 2000L)
    }

    /**
     * Log every user toggle action in the floating menu
     */
    fun logToggle(featureName: String, isEnabled: Boolean) {
        val state = if (isEnabled) "ON (Active)" else "OFF (Disabled)"
        val msg = "TOGGLE: $featureName -> $state"
        appendAndScheduleUpload("USER_ACTION", msg, 4000L)
    }

    /**
     * Log real-time in-game memory diagnostics (Facade, Match, Player, Enemies)
     */
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
        appendAndScheduleUpload("MEM_DIAG", msg, 5000L)
    }

    /**
     * Log runtime errors, memory read failures, or exceptions
     */
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        synchronized(logLock) {
            appendInternal("[ERROR] [$tag] $message")
            if (throwable != null) {
                appendInternal("Stacktrace:\n" + throwable.stackTraceToString())
            }
        }
        // Send error log file immediately (short 1s delay)
        scheduleFileUpload(1000L, "ERROR DETECTED: $tag - $message")
    }

    /**
     * Debounced scheduler for sending file to Telegram
     */
    private fun scheduleFileUpload(delayMs: Long, reason: String) {
        if (!isEnabled || botToken.isEmpty() || chatId.isEmpty()) return
        uploadHandler.removeCallbacksAndMessages(null)
        uploadHandler.postDelayed({
            sendFileToTelegramAsync(reason)
        }, delayMs)
    }

    /**
     * Trigger immediate file upload (e.g. from user button tap)
     */
    fun sendFileNow(reason: String = "Manual User Export") {
        sendFileToTelegramAsync(reason)
    }

    private fun sendFileToTelegramAsync(reason: String) {
        if (!isEnabled || botToken.isEmpty() || chatId.isEmpty()) return
        if (isUploading.compareAndSet(false, true)) {
            Thread({
                try {
                    val current = logFile
                    if (current != null && current.exists() && current.length() > 0) {
                        // Create a snapshot file for uploading
                        val timestamp = fileTimestampFormat.format(Date())
                        val snapFile = current.parentFile?.resolve("ninmods_log_$timestamp.log")
                        if (snapFile != null) {
                            synchronized(logLock) {
                                current.copyTo(snapFile, overwrite = true)
                            }
                            val caption = "📄 <b>NINMODS Runtime Log File</b>\n" +
                                    "⏰ <i>${logDateFormat.format(Date())}</i>\n" +
                                    "📌 <b>Trigger:</b> <code>$reason</code>\n" +
                                    "📱 <b>Device:</b> ${Build.MANUFACTURER} ${Build.MODEL}"

                            val ok = uploadDocumentViaHttp(snapFile, caption)
                            try { snapFile.delete() } catch (_: Exception) {}

                            if (ok) {
                                lastUploadTime = System.currentTimeMillis()
                                Log.i(TAG, "Log file uploaded to Telegram successfully.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed uploading log file: ${e.message}")
                } finally {
                    isUploading.set(false)
                }
            }, "NIN_Telegram_File_Uploader").start()
        }
    }

    /**
     * Pure Java multipart/form-data POST implementation for Telegram /sendDocument
     */
    private fun uploadDocumentViaHttp(file: File, caption: String): Boolean {
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
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            DataOutputStream(conn.outputStream).use { dos ->
                // 1. chat_id field
                dos.writeBytes(twoHyphens + boundary + lineEnd)
                dos.writeBytes("Content-Disposition: form-data; name=\"chat_id\"$lineEnd$lineEnd")
                dos.writeBytes(chatId + lineEnd)

                // 2. caption field
                dos.writeBytes(twoHyphens + boundary + lineEnd)
                dos.writeBytes("Content-Disposition: form-data; name=\"caption\"$lineEnd$lineEnd")
                dos.write(caption.toByteArray(Charsets.UTF_8))
                dos.writeBytes(lineEnd)

                // 3. parse_mode field
                dos.writeBytes(twoHyphens + boundary + lineEnd)
                dos.writeBytes("Content-Disposition: form-data; name=\"parse_mode\"$lineEnd$lineEnd")
                dos.writeBytes("HTML" + lineEnd)

                // 4. document file field
                dos.writeBytes(twoHyphens + boundary + lineEnd)
                dos.writeBytes("Content-Disposition: form-data; name=\"document\"; filename=\"${file.name}\"$lineEnd")
                dos.writeBytes("Content-Type: text/plain; charset=UTF-8$lineEnd$lineEnd")

                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        dos.write(buffer, 0, bytesRead)
                    }
                }
                dos.writeBytes(lineEnd)

                // Final boundary
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                dos.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                return true
            } else {
                val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "Telegram sendDocument returned HTTP $responseCode: $errorMsg")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadDocumentViaHttp error: ${e.message}")
            return false
        } finally {
            conn?.disconnect()
        }
    }
}
