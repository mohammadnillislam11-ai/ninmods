package com.ninmods.main.core;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ninmods.main.auth.AuthApiController;
import com.ninmods.main.auth.AuthManager;
import com.ninmods.main.auth.DeviceHardwareIdentifier;

/**
 * On-Demand Feedback & Diagnostic Telemetry Dispatcher.
 * 
 * ZERO AUTOMATIC BACKGROUND TIMERS:
 * Dispatches logs strictly when explicitly triggered by the user via the
 * Floating Logo close badge feedback dialog, completely eliminating automatic
 * background polling or bandwidth usage.
 * 
 * ZERO EMOJIS: All formatted headers and fields strictly use clean alphanumeric tags.
 */
public final class TelegramLogUploader {

    private static final String TAG = "NIN_TELEMETRY";
    public static final String TELEGRAM_CHAT_ID = "6326853456";
    public static final String TELEGRAM_BOT_USERNAME = "@ninmods_live_log_bot";
    public static final String TELEGRAM_BOT_ID = "8479669762";

    // Direct Telegram Bot token
    private static volatile String sTelegramBotToken = "8479669762:AAEdrFlN-MQdn76Psk65ibLi1pLrAlIG8bQ";

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static final SimpleDateFormat sTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private static Context sContext = null;

    private TelegramLogUploader() {}

    /**
     * Initializes context reference.
     */
    public static synchronized void init(Context context) {
        if (context != null) {
            sContext = context.getApplicationContext();
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
     * Enqueues a formatted log entry into AppLogger in-memory buffer only (Zero automatic network calls).
     */
    public static void enqueueLog(String subTag, String message) {
        // Only buffered in memory for on-demand dispatch
    }

    /**
     * Dispatches user feedback along with live runtime logs and system diagnostics to the Telegram Bot.
     * Strictly triggered on user demand. Zero emojis.
     */
    public static void sendFeedbackWithLogs(Context context, String userFeedback, final Runnable onComplete) {
        sExecutor.execute(() -> {
            try {
                Context ctx = context != null ? context.getApplicationContext() : sContext;
                String hwid = ctx != null ? DeviceHardwareIdentifier.getHardwareId(ctx) : "UNKNOWN_HWID";
                String activeKey = ctx != null ? AuthManager.getActiveLicenseKey(ctx) : "NO_KEY";
                long remSec = ctx != null ? AuthManager.getRemainingSeconds(ctx) : 0;
                String deviceModel = Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT + ")";
                String driverStatus = DriverManager.isDriverActive() ? "ACTIVE (" + DriverManager.getActiveDriverNode() + ")" : "OFFLINE";
                String appVersion = ctx != null ? ctx.getString(com.ninmods.main.R.string.app_version) : "90.11";

                StringBuilder sb = new StringBuilder();
                sb.append("=========================================\n");
                sb.append("[USER FEEDBACK AND DIAGNOSTIC REPORT]\n");
                sb.append("=========================================\n");
                sb.append("[MESSAGE]: ").append(userFeedback != null && !userFeedback.trim().isEmpty() ? userFeedback.trim() : "(No written comment)").append("\n");
                sb.append("[TIMESTAMP]: ").append(sTimeFormat.format(new Date())).append("\n");
                sb.append("[DEVICE]: ").append(deviceModel).append("\n");
                sb.append("[LICENSE KEY]: ").append(activeKey.isEmpty() ? "UNLICENSED" : activeKey).append("\n");
                sb.append("[REMAINING SECONDS]: ").append(remSec).append("\n");
                sb.append("[HWID]: ").append(hwid).append("\n");
                sb.append("[DRIVER STATUS]: ").append(driverStatus).append("\n");
                sb.append("[APP VERSION]: V").append(appVersion).append("\n");
                sb.append("-----------------------------------------\n");
                sb.append("[RECENT RUNTIME LOGS]:\n");

                List<String> recentLogs = AppLogger.getRecentLogs(60);
                for (String logLine : recentLogs) {
                    sb.append(logLine).append("\n");
                }
                sb.append("=========================================\n");

                String reportText = sb.toString();

                // 1. Send via Backend Relay
                boolean sent = uploadViaBackendRelay(ctx, hwid, activeKey, deviceModel, reportText);

                // 2. Direct fallback if token available
                if (!sent && sTelegramBotToken != null && !sTelegramBotToken.isEmpty()) {
                    uploadDirectToTelegram(sTelegramBotToken, TELEGRAM_CHAT_ID, hwid, activeKey, deviceModel, reportText);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to send feedback: " + t.getMessage());
            } finally {
                if (onComplete != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onComplete);
                }
            }
        });
    }

    /**
     * Uploads telemetry through the backend API relay.
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
            conn.setRequestProperty("User-Agent", "NINMODS-Kernel-Telemetry/90.12");

            String sToken = context != null ? AuthManager.getSessionToken(context) : "";
            if (!sToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + sToken);
                conn.setRequestProperty("x-api-key", sToken);
            }

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
     * Directly communicates with the official Telegram Bot API (sendMessage).
     */
    private static boolean uploadDirectToTelegram(String token, String chatId, String hwid, String key, String deviceModel, String logText) {
        try {
            if (logText.length() < 4000) {
                URL url = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

                JSONObject json = new JSONObject();
                json.put("chat_id", chatId);
                json.put("text", logText);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int code = conn.getResponseCode();
                return code == 200;
            } else {
                return uploadDocumentToTelegram(token, chatId, "ninmods_feedback.txt", logText);
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Sends larger logs as a document file to Telegram Bot.
     */
    private static boolean uploadDocumentToTelegram(String token, String chatId, String filename, String content) {
        try {
            String boundary = "===" + System.currentTimeMillis() + "===";
            URL url = new URL("https://api.telegram.org/bot" + token + "/sendDocument");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            OutputStream os = conn.getOutputStream();
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write(("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + chatId + "\r\n").getBytes());

            os.write(("--" + boundary + "\r\n").getBytes());
            os.write(("Content-Disposition: form-data; name=\"caption\"\r\n\r\nNINMODS Feedback and Execution Logs\r\n").getBytes());

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
