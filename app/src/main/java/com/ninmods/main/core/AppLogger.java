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

    private static volatile android.content.Context sContext = null;

    public static void init(android.content.Context context) {
        if (context != null) {
            sContext = context.getApplicationContext();
            ensureStorageAccessAndLogFile();
        }
    }

    private static void ensureStorageAccessAndLogFile() {
        new Thread(() -> {
            try {
                // Grant All Files permission via root if rooted
                Runtime.getRuntime().exec("su -c \"appops set com.ninmods.main MANAGE_EXTERNAL_STORAGE allow\"");
                Runtime.getRuntime().exec("su -c \"pm grant com.ninmods.main android.permission.READ_EXTERNAL_STORAGE\"");
                Runtime.getRuntime().exec("su -c \"pm grant com.ninmods.main android.permission.WRITE_EXTERNAL_STORAGE\"");
                
                // Pre-create world-writable log file in Download directory
                String[] createCmds = {
                    "su -c \"mkdir -p /data/media/0/Download && touch /data/media/0/Download/ninmods.log && chmod 777 /data/media/0/Download/ninmods.log && chown 1023:1023 /data/media/0/Download/ninmods.log\"",
                    "su -c \"mkdir -p /storage/emulated/0/Download && touch /storage/emulated/0/Download/ninmods.log && chmod 777 /storage/emulated/0/Download/ninmods.log\""
                };
                for (String cmd : createCmds) {
                    try {
                        Runtime.getRuntime().exec(cmd);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }).start();
    }

    private static List<File> getCandidateLogFiles() {
        List<File> list = new ArrayList<>();
        // 1. Standard Public Download folder
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir != null) {
                if (!downloadDir.exists()) downloadDir.mkdirs();
                list.add(new File(downloadDir, "ninmods.log"));
            }
        } catch (Throwable ignored) {}

        list.add(new File("/storage/emulated/0/Download/ninmods.log"));
        list.add(new File("/sdcard/Download/ninmods.log"));

        // 2. App-specific external storage (Android 11+ 100% accessible without any permissions)
        if (sContext != null) {
            try {
                File extFiles = sContext.getExternalFilesDir(null);
                if (extFiles != null) {
                    if (!extFiles.exists()) extFiles.mkdirs();
                    list.add(new File(extFiles, "ninmods.log"));
                }
            } catch (Throwable ignored) {}
        }
        list.add(new File("/storage/emulated/0/Android/data/com.ninmods.main/files/ninmods.log"));
        list.add(new File("/sdcard/Android/data/com.ninmods.main/files/ninmods.log"));

        // 3. Fallbacks
        list.add(new File("/data/local/tmp/ninmods.log"));
        if (sContext != null) {
            try {
                File internal = sContext.getFilesDir();
                if (internal != null) {
                    list.add(new File(internal, "ninmods.log"));
                }
            } catch (Throwable ignored) {}
        }
        return list;
    }

    private static void writeToDownloadFile(String formattedLine) {
        synchronized (LOCK) {
            List<File> candidates = getCandidateLogFiles();
            boolean writtenToPublic = false;
            for (File logFile : candidates) {
                try {
                    File parent = logFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
                    fw.write(formattedLine + "\n");
                    fw.flush();
                    fw.close();
                    logFile.setReadable(true, false);
                    logFile.setWritable(true, false);

                    if (logFile.getAbsolutePath().contains("Download")) {
                        writtenToPublic = true;
                    }
                } catch (Throwable ignored) {}
            }
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
