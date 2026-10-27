package com.ninmods.main.core;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Standard Diagnostics & Telemetry Logger for NINMODS.
 * 
 * Strict Private Storage Policy:
 * - Logs are written to the app's private files directory (files/logs/ninmods.log).
 * - Not in public Download, so other apps and users cannot read device, HWID, or session data.
 * - All runtime errors, kernel driver status, memory inquiries, and system events
 *   are captured here for inspection from within the app.
 */
public final class AppLogger {
    private static final String TAG = "NINMODS_LOG";
    private static final Object LOCK = new Object();

    /*
     * SimpleDateFormat is NOT thread safe. A single shared instance was
     * formatted from the UI thread, from the background thread that requests
     * root and from the log writer thread at the same time, which can make
     * format() throw. Because AppLogger.log() is called from onCreate()
     * BEFORE the first view is set, such an exception left the user on the
     * bare (black) window background. One formatter per thread removes the
     * race completely.
     */
    private static final ThreadLocal<SimpleDateFormat> SDF =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US));

    private static final int MAX_MEMORY_LOG_ENTRIES = 300;
    private static final LinkedList<String> sMemoryLogBuffer = new LinkedList<>();

    private static volatile Context sContext = null;
    private static volatile boolean sInitialized = false;

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
     * Resolves the log file inside the app's PRIVATE files directory.
     * Not under public Download, so other apps and users cannot read device/session data.
     */
    private static File getDedicatedLogFile() {
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

    /**
     * Ensures the private log directory and file exist, with owner-only permissions.
     */
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
            logFile.setReadable(true, true);
            logFile.setWritable(true, true);
        } catch (Throwable t) {
            Log.w(TAG, "Log folder creation deferred: " + t.getMessage());
        }
    }

    /**
     * Writes comprehensive initial environment and hardware details to the log file.
     */
    private static void logSystemHeader() {
        log("SYSTEM", "==================================================");
        log("SYSTEM", "=== NINMODS GENUINE SYSTEM DIAGNOSTIC LOG STARTED ===");
        log("SYSTEM", "==================================================");
        log("SYSTEM", "Timestamp: " + formatTimestamp());
        log("SYSTEM", "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.PRODUCT + ")");
        log("SYSTEM", "Hardware: " + Build.HARDWARE + " | Board: " + Build.BOARD);
        log("SYSTEM", "Primary ABI: " + (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "arm64-v8a"));
        log("SYSTEM", "Android OS: Android " + Build.VERSION.RELEASE + " (API Level " + Build.VERSION.SDK_INT + ")");
        log("SYSTEM", "Log Directory: app private storage (files/logs/ninmods.log)");
        log("SYSTEM", "SELinux State: " + getSelinuxStatus());
        log("SYSTEM", "SuperUser Binary: " + (new File("/system/bin/su").exists() || new File("/system/xbin/su").exists() ? "DETECTED" : "STANDARD"));
        log("SYSTEM", "Kernel Driver Node: /dev/BYNb (Ring 0 IOCTL 0x801/0x802/0x803)");
        log("SYSTEM", "==================================================");
    }

    private static final java.util.concurrent.ExecutorService sFileExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

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
                    logFile.setReadable(true, true);
                    logFile.setWritable(true, true);
                } catch (Throwable ignored) {}
            }
        });
    }

    public static void log(String subTag, String message) {
        try {
            Log.i(TAG, "[" + subTag + "] " + message);
            String line = "[" + formatTimestamp() + "] [" + subTag + "] " + message;
            recordInMemory(line);
            writeToDownloadFile(line);
        } catch (Throwable ignored) {
            // Logging must never take the app down.
        }
    }

    public static void logE(String subTag, String message, Throwable t) {
        try {
            Log.e(TAG, "[" + subTag + "] " + message, t);
            String fullMsg = message + (t != null ? " : " + t.getMessage() : "");
            String line = "[" + formatTimestamp() + "] [ERROR][" + subTag + "] " + fullMsg;
            recordInMemory(line);
            writeToDownloadFile(line);
        } catch (Throwable ignored) {
            // Logging must never take the app down.
        }
    }

    /** Formats the current timestamp without ever throwing. */
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
     * Returns recent log lines from memory buffer.
     */
    public static List<String> getRecentLogs(int maxLines) {
        synchronized (LOCK) {
            List<String> result = new ArrayList<>();
            if (sMemoryLogBuffer.isEmpty()) {
                result.add("No live memory logs recorded yet. Telemetry streaming to app private storage (files/logs/ninmods.log).");
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
