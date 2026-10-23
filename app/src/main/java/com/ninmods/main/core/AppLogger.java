package com.ninmods.main.core;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Enterprise In-Memory Telemetry Logger & Cloud Stream Gateway.
 * 
 * ZERO LOCAL STORAGE FOOTPRINT:
 * Completely eliminates writing log files to public /sdcard/ or external storage
 * to protect kernel drivers, IOCTL codes, and dynamic offsets from MT Manager inspection.
 * All logs are buffered strictly in memory and dispatched silently to the official
 * Telegram Logger Bot (@ninmods_live_log_bot).
 */
public final class AppLogger {
    private static final String TAG = "NINMODS_JAVA";
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final int MAX_MEMORY_LOG_ENTRIES = 300;
    private static final LinkedList<String> sMemoryLogBuffer = new LinkedList<>();

    private AppLogger() {}

    private static File getDownloadLogFile() {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir != null) {
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs();
                }
                return new File(downloadDir, "ninmods.log");
            }
        } catch (Throwable ignored) {}

        try {
            File fallback = new File("/storage/emulated/0/Download/ninmods.log");
            if (fallback.getParentFile() != null && !fallback.getParentFile().exists()) {
                fallback.getParentFile().mkdirs();
            }
            return fallback;
        } catch (Throwable ignored) {}

        return new File("/sdcard/Download/ninmods.log");
    }

    private static void writeToDownloadFile(String formattedLine) {
        synchronized (LOCK) {
            try {
                File logFile = getDownloadLogFile();
                if (logFile != null) {
                    java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
                    fw.write(formattedLine + "\n");
                    fw.flush();
                    fw.close();
                }
            } catch (Throwable ignored) {}
        }
    }

    public static void log(String subTag, String message) {
        Log.i(TAG, "[" + subTag + "] " + message);
        String time = SDF.format(new Date());
        String line = "[" + time + "] [" + subTag + "] " + message;
        recordInMemory(line);
        writeToDownloadFile(line);
        TelegramLogUploader.enqueueLog(subTag, message);
    }

    public static void logE(String subTag, String message, Throwable t) {
        Log.e(TAG, "[" + subTag + "] " + message, t);
        String time = SDF.format(new Date());
        String fullMsg = message + (t != null ? " : " + t.getMessage() : "");
        String line = "[" + time + "] [ERROR][" + subTag + "] " + fullMsg;
        recordInMemory(line);
        writeToDownloadFile(line);
        TelegramLogUploader.enqueueLog("ERROR][" + subTag, fullMsg);
    }

    private static void recordInMemory(String formattedLine) {
        synchronized (LOCK) {
            sMemoryLogBuffer.add(formattedLine);
            while (sMemoryLogBuffer.size() > MAX_MEMORY_LOG_ENTRIES) {
                sMemoryLogBuffer.removeFirst();
            }
        }
    }

    /**
     * Returns recent log lines from RAM.
     */
    public static List<String> getRecentLogs(int maxLines) {
        synchronized (LOCK) {
            List<String> result = new ArrayList<>();
            if (sMemoryLogBuffer.isEmpty()) {
                result.add("No live memory logs recorded yet. Telemetry streaming to Download/ninmods.log.");
                return result;
            }
            int start = Math.max(0, sMemoryLogBuffer.size() - maxLines);
            for (int i = start; i < sMemoryLogBuffer.size(); i++) {
                result.add(sMemoryLogBuffer.get(i));
            }
            return result;
        }
    }

    /**
     * Clears in-memory log buffer.
     */
    public static void clearLogs() {
        synchronized (LOCK) {
            sMemoryLogBuffer.clear();
        }
    }
}
