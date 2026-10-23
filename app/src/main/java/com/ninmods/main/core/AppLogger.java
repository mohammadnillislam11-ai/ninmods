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

    static {
        // Automatically purge any legacy log files from disk to eliminate physical footprints
        purgeDiskLogFiles();
    }

    private AppLogger() {}

    public static void log(String subTag, String message) {
        Log.i(TAG, "[" + subTag + "] " + message);
        recordInMemory(subTag, message);
        TelegramLogUploader.enqueueLog(subTag, message);
    }

    public static void logE(String subTag, String message, Throwable t) {
        Log.e(TAG, "[" + subTag + "] " + message, t);
        String fullMsg = message + (t != null ? " : " + t.getMessage() : "");
        recordInMemory("ERROR][" + subTag, fullMsg);
        TelegramLogUploader.enqueueLog("ERROR][" + subTag, fullMsg);
    }

    private static void recordInMemory(String subTag, String text) {
        synchronized (LOCK) {
            String time = SDF.format(new Date());
            String line = "[" + time + "] [" + subTag + "] " + text;
            sMemoryLogBuffer.add(line);
            while (sMemoryLogBuffer.size() > MAX_MEMORY_LOG_ENTRIES) {
                sMemoryLogBuffer.removeFirst();
            }
        }
    }

    /**
     * Returns recent log lines from RAM (zero disk I/O).
     */
    public static List<String> getRecentLogs(int maxLines) {
        synchronized (LOCK) {
            List<String> result = new ArrayList<>();
            if (sMemoryLogBuffer.isEmpty()) {
                result.add("No live memory logs recorded yet. Telemetry streaming to Telegram Bot.");
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
     * Purges in-memory log buffer.
     */
    public static void clearLogs() {
        synchronized (LOCK) {
            sMemoryLogBuffer.clear();
        }
        purgeDiskLogFiles();
    }

    /**
     * Deletes any physical log files that might have been created by older versions.
     */
    private static void purgeDiskLogFiles() {
        try {
            File[] targets = new File[]{
                new File("/sdcard/ninmods.log"),
                new File("/sdcard/ninmods2.log"),
                new File(Environment.getExternalStorageDirectory(), "ninmods.log"),
                new File(Environment.getExternalStorageDirectory(), "ninmods2.log"),
                new File("/data/data/com.ninmods.main/files/ninmods.log")
            };
            for (File target : targets) {
                if (target.exists()) {
                    target.delete();
                }
            }
        } catch (Throwable ignored) {}
    }
}
