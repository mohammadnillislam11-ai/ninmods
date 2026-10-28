package com.ninmods.main.core;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Diagnostic and telemetry logger for NINMODS.
 * Writes diagnostic events to public Download/ninmods.log per Rule 16,
 * with fallback to app-private files if external storage is inaccessible.
 */
public final class AppLogger {
    private static final String TAG = "NINMODS_LOG";
    private static final Object LOCK = new Object();

    private static final ThreadLocal<SimpleDateFormat> SDF =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US));

    private static final int MAX_MEMORY_LOG_ENTRIES = 300;
    private static final LinkedList<String> sMemoryLogBuffer = new LinkedList<>();
    private static final ExecutorService sFileExecutor = Executors.newSingleThreadExecutor();

    private static volatile Context sContext = null;
    private static volatile boolean sInitialized = false;

    /**
     * Initializes the logger with the application context.
     * @param context Application context
     */
    public static void init(Context context) {
        if (context != null) {
            sContext = context.getApplicationContext();
        }
        if (!sInitialized) {
            sInitialized = true;
            installGlobalCrashHandler();
            sFileExecutor.execute(() -> {
                ensureLogFolderAndFile();
                logSystemHeader();
            });
        }
    }

    private static void installGlobalCrashHandler() {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logE("CRASH", "Fatal unhandled exception in thread [" + thread.getName() + "]: " + throwable.getMessage(), throwable);
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    /**
     * Resolves the log file location in the public Download directory per Rule 16,
     * falling back to app-private storage if Download is unwritable.
     * @return File handle for logging
     */
    private static File getDedicatedLogFile() {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir != null && (downloadDir.exists() || downloadDir.mkdirs())) {
                File pubLog = new File(downloadDir, "ninmods.log");
                return pubLog;
            }
        } catch (Throwable ignored) {}

        File base = (sContext != null) ? sContext.getFilesDir() : null;
        if (base == null) {
            return null;
        }
        File logDir = new File(base, "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        return new File(logDir, "ninmods.log");
    }

    private static void ensureLogFolderAndFile() {
        try {
            File logFile = getDedicatedLogFile();
            if (logFile == null) return;
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            logFile.setReadable(true, false);
            logFile.setWritable(true, false);
        } catch (Throwable t) {
            Log.w(TAG, "Log folder creation deferred: " + t.getMessage());
        }
    }

    private static void logSystemHeader() {
        log("SYSTEM", "NINMODS Diagnostic Log Initialized");
        log("SYSTEM", "Timestamp: " + formatTimestamp());
        log("SYSTEM", "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.PRODUCT + ")");
        log("SYSTEM", "Hardware: " + Build.HARDWARE + " | Board: " + Build.BOARD);
        log("SYSTEM", "Primary ABI: " + (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "arm64-v8a"));
        log("SYSTEM", "Android OS: Android " + Build.VERSION.RELEASE + " (API Level " + Build.VERSION.SDK_INT + ")");
        File logFile = getDedicatedLogFile();
        log("SYSTEM", "Log Target: " + (logFile != null ? logFile.getAbsolutePath() : "In-Memory Only"));
        log("SYSTEM", "SELinux State: " + getSelinuxStatus());
        log("SYSTEM", "SuperUser Binary: " + (new File("/system/bin/su").exists() || new File("/system/xbin/su").exists() ? "DETECTED" : "STANDARD"));
        log("SYSTEM", "Kernel Driver Node: /dev/BYNb (Ring 0 IOCTL 0x801/0x802/0x803)");
    }

    private static String getSelinuxStatus() {
        try {
            File enforceFile = new File("/sys/fs/selinux/enforce");
            if (enforceFile.exists() && enforceFile.canRead()) {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(enforceFile));
                String line = br.readLine();
                br.close();
                if ("0".equals(line != null ? line.trim() : "")) return "Permissive";
                if ("1".equals(line != null ? line.trim() : "")) return "Enforcing";
            }
        } catch (Throwable ignored) {}
        return "Standard";
    }

    private static void writeToDownloadFile(String formattedLine) {
        sFileExecutor.execute(() -> {
            synchronized (LOCK) {
                try {
                    File logFile = getDedicatedLogFile();
                    if (logFile == null) return;
                    File parentDir = logFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    FileWriter fw = new FileWriter(logFile, true);
                    fw.write(formattedLine + "\n");
                    fw.flush();
                    fw.close();
                    logFile.setReadable(true, false);
                    logFile.setWritable(true, false);
                } catch (Throwable ignored) {}
            }
        });
    }

    /**
     * Records an informational log entry.
     * @param subTag Subsystem tag identifier
     * @param message Informational log text
     */
    public static void log(String subTag, String message) {
        try {
            Log.i(TAG, "[" + subTag + "] " + message);
            String line = "[" + formatTimestamp() + "] [" + subTag + "] " + message;
            recordInMemory(line);
            writeToDownloadFile(line);
        } catch (Throwable ignored) {}
    }

    /**
     * Records an error log entry.
     * @param subTag Subsystem tag identifier
     * @param message Error description
     * @param t Optional throwable or null
     */
    public static void logE(String subTag, String message, Throwable t) {
        try {
            Log.e(TAG, "[" + subTag + "] " + message, t);
            String fullMsg = message + (t != null ? " : " + t.getMessage() : "");
            String line = "[" + formatTimestamp() + "] [ERROR][" + subTag + "] " + fullMsg;
            recordInMemory(line);
            writeToDownloadFile(line);
        } catch (Throwable ignored) {}
    }

    private static String formatTimestamp() {
        try {
            return SDF.get().format(new Date());
        } catch (Throwable t) {
            return "";
        }
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
     * Returns recent log entries from memory buffer.
     * @param maxLines Maximum count of log lines to retrieve
     * @return List of recent formatted log strings
     */
    public static List<String> getRecentLogs(int maxLines) {
        synchronized (LOCK) {
            List<String> result = new ArrayList<>();
            if (sMemoryLogBuffer.isEmpty()) {
                File logFile = getDedicatedLogFile();
                result.add("No live memory logs recorded yet. Telemetry target: " + (logFile != null ? logFile.getAbsolutePath() : "in-memory"));
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
