package com.ninmods.main.core;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.ninmods.main.auth.AuthApiController;
import com.ninmods.main.auth.AuthManager;
import com.ninmods.main.auth.DeviceHardwareIdentifier;

/**
 * Enterprise Silent Telemetry & Live Telegram Cloud Log Dispatcher.
 * 
 * Streams application, kernel driver, JNI, and memory telemetry directly to
 * the official administrative Telegram Bot (@ninmods_live_log_bot, Chat: 6326853456)
 * without writing any log files to external physical device storage:
 * 
 * 1. Zero Local Storage Footprint (zero /sdcard/ exposure against MT Manager inspection)
 * 2. 100% Silent Background Operation (zero UI notifications, toasts, or frame drop)
 * 3. Dual-Channel Transmission:
 *    - Cloud Relay: POST /api/v2/app/telemetry/log (Hides Telegram Bot Token on server)
 *    - Direct Telegram Bot API fallback (sendMessage / sendDocument)
 * 4. Periodic & Event-Triggered Batch Flushes (Driver Load, Floater Inject, Memory Errors)
 */
public final class TelegramLogUploader {

    private static final String TAG = "NIN_TELEMETRY";
    public static final String TELEGRAM_CHAT_ID = "6326853456";
    public static final String TELEGRAM_BOT_USERNAME = "@ninmods_live_log_bot";
    public static final String TELEGRAM_BOT_ID = "8479669762";

    // Direct Telegram Bot token fallback (if set dynamically or via backend)
    private static volatile String sTelegramBotToken = "";

    private static final ConcurrentLinkedQueue<String> sLogQueue = new ConcurrentLinkedQueue<>();
    private static final ScheduledExecutorService sScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final SimpleDateFormat sTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private static Context sContext = null;
    private static boolean sScheduled = false;

    private TelegramLogUploader() {}

    /**
     * Initializes the telemetry engine with application context.
     */
    public static synchronized void init(Context context) {
        if (context != null) {
            sContext = context.getApplicationContext();
        }

        if (!sScheduled) {
            sScheduled = true;
            // Schedule automated batch upload every 30 seconds
            sScheduler.scheduleWithFixedDelay(TelegramLogUploader::flushLogs, 15, 30, TimeUnit.SECONDS);
        }
    }

    /**
     * Sets or updates the Telegram Bot token for direct fallback transmission.
     */
    public static void setBotToken(String token) {
        if (token != null) {
            sTelegramBotToken = token.trim();
        }
    }

    /**
     * Enqueues a formatted log entry for silent background transmission.
     */
    public static void enqueueLog(String subTag, String message) {
        String time = sTimeFormat.format(new Date());
        String entry = "[" + time + "] [" + subTag + "] " + message;
        sLogQueue.offer(entry);

        // Immediate flush on critical milestone events
        if (isMilestoneEvent(subTag, message)) {
            triggerImmediateFlush();
        }
    }

    /**
     * Determines whether an event requires instantaneous cloud notification.
     */
    private static boolean isMilestoneEvent(String subTag, String message) {
        if (subTag == null || message == null) return false;
        String lower = (subTag + " " + message).toLowerCase(Locale.US);
        return lower.contains("driver loaded") ||
               lower.contains("driver error") ||
               lower.contains("ioctl") ||
               lower.contains("verified") ||
               lower.contains("injection") ||
               lower.contains("crash") ||
               lower.contains("fatal") ||
               lower.contains("tamper");
    }

    /**
     * Triggers an asynchronous, non-blocking log transmission immediately.
     */
    public static void triggerImmediateFlush() {
        sScheduler.execute(TelegramLogUploader::flushLogs);
    }

    /**
     * Collects and dispatches enqueued log lines silently to Telegram.
     */
    private static void flushLogs() {
        if (sLogQueue.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        int lineCount = 0;
        String line;
        while ((line = sLogQueue.poll()) != null && lineCount < 200) {
            sb.append(line).append("\n");
            lineCount++;
        }

        if (sb.length() == 0) return;

        final String logPayload = sb.toString();
        final Context ctx = sContext;
        final String hwid = ctx != null ? DeviceHardwareIdentifier.getHardwareId(ctx) : "UNKNOWN_HWID";
        final String activeKey = ctx != null ? AuthManager.getActiveLicenseKey(ctx) : "NO_KEY";
        final String deviceModel = Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT + ")";

        // Channel 1: Primary Server Relay (Keeps Bot Token completely secret)
        boolean relayed = uploadViaBackendRelay(ctx, hwid, activeKey, deviceModel, logPayload);

        // Channel 2: Direct Telegram Bot API fallback if direct token is present
        if (!relayed && sTelegramBotToken != null && !sTelegramBotToken.isEmpty()) {
            uploadDirectToTelegram(sTelegramBotToken, TELEGRAM_CHAT_ID, hwid, activeKey, deviceModel, logPayload);
        }
    }

    /**
     * Uploads telemetry silently through the backend API relay.
     * The backend server holds the Telegram Bot Token in .env and forwards to the bot.
     */
    private static boolean uploadViaBackendRelay(Context context, String hwid, String key, String deviceModel, String logText) {
        try {
            String baseUrl = context != null ? AuthApiController.getServerBaseUrl(context) : AuthApiController.DEFAULT_SERVER_URL;
            URL url = new URL(baseUrl + "/api/v2/app/telemetry/log");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "NINMODS-Kernel-Telemetry/90.3");

            JSONObject json = new JSONObject();
            json.put("chat_id", TELEGRAM_CHAT_ID);
            json.put("bot_id", TELEGRAM_BOT_ID);
            json.put("hwid", hwid);
            json.put("license_key", key);
            json.put("device_model", deviceModel);
            json.put("logs", logText);
            json.put("timestamp", System.currentTimeMillis());

            byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
                os.flush();
            }

            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Directly communicates with the official Telegram Bot API (sendMessage / sendDocument).
     */
    private static boolean uploadDirectToTelegram(String token, String chatId, String hwid, String key, String deviceModel, String logText) {
        try {
            String header = "--- NINMODS LIVE TELEMETRY ---\n" +
                            "------------------------------\n" +
                            "Key: " + (key.isEmpty() ? "UNLICENSED" : key) + "\n" +
                            "HWID: " + (hwid.length() > 20 ? hwid.substring(0, 20) + "..." : hwid) + "\n" +
                            "Device: " + deviceModel + "\n" +
                            "------------------------------\n\n";

            String fullMessage = header + "```\n" + logText + "\n```";

            // If message is under Telegram's 4096 character limit, use sendMessage
            if (fullMessage.length() < 4000) {
                URL url = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

                JSONObject json = new JSONObject();
                json.put("chat_id", chatId);
                json.put("text", fullMessage);
                json.put("parse_mode", "Markdown");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int code = conn.getResponseCode();
                return code == 200;
            } else {
                // Large logs: send as document via multipart/form-data
                return uploadDocumentMultipart(token, chatId, "ninmods_" + System.currentTimeMillis() + ".log", logText);
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Sends larger log buffers as a file document via Telegram multipart/form-data.
     */
    private static boolean uploadDocumentMultipart(String token, String chatId, String filename, String content) {
        try {
            String boundary = "===NINMODS" + System.currentTimeMillis() + "===";
            URL url = new URL("https://api.telegram.org/bot" + token + "/sendDocument");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            OutputStream os = conn.getOutputStream();
            // Chat ID field
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write(("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + chatId + "\r\n").getBytes());

            // Caption field
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write(("Content-Disposition: form-data; name=\"caption\"\r\n\r\nNINMODS Full Execution Logs\r\n").getBytes());

            // Document file
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write(("Content-Disposition: form-data; name=\"document\"; filename=\"" + filename + "\"\r\n").getBytes());
            os.write(("Content-Type: text/plain\r\n\r\n").getBytes());
            os.write(content.getBytes(StandardCharsets.UTF_8));
            os.write(("\r\n--" + boundary + "--\r\n").getBytes());
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            return code == 200;
        } catch (Throwable t) {
            return false;
        }
    }
}
