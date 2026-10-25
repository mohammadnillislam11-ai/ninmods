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

/**
 * Standard Diagnostics & Telemetry Logger for NINMODS.
 * 
 * Strict Download Folder Policy (Rule 15 of GEMINI.md):
 * - Absolutely NEVER saves loose files to root SDCard (/sdcard/) or app internal data.
 * - Automatically creates a dedicated 'ninmods' directory inside the default Download folder:
 *   /storage/emulated/0/Download/ninmods/ninmods.log
 * - All runtime errors, kernel driver status, memory inquiries, and system events
 *   are comprehensively captured here for effortless inspection via Android default Files app.
 */
public final class AppLogger {
    private static final String TAG = "NINMODS_LOG";
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
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
            ensureLogFolderAndFile();
            logSystemHeader();
        }
    }

    /**
     * Resolves the dedicated log file located strictly inside Download/ninmods/ directory.
     */
    private static File getDedicatedLogFile() {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir != null) {
                File ninmodsFolder = new File(downloadDir, "ninmods");
                if (!ninmodsFolder.exists()) {
                    ninmodsFolder.mkdirs();
                }
                return new File(ninmodsFolder, "ninmods.log");
            }
        } catch (Throwable ignored) {}

        // Direct standard fallback strictly inside the official Download folder
        File fallbackDir = new File("/storage/emulated/0/Download/ninmods");
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs();
        }
        return new File(fallbackDir, "ninmods.log");
    }

    /**
     * Automatically ensures the 'ninmods' subfolder and log file exist inside the Download folder.
     */
    private static void ensureLogFolderAndFile() {
        try {
            File logFile = getDedicatedLogFile();
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
            Log.w(TAG, "Notice: Download/ninmods folder creation deferral: " + t.getMessage());
        }
    }

    /**
     * Writes comprehensive initial environment and hardware details to the log file.
     */
    private static void logSystemHeader() {
        log("SYSTEM", "==================================================");
        log("SYSTEM", "=== NINMODS GENUINE SYSTEM DIAGNOSTIC LOG STARTED ===");
        log("SYSTEM", "==================================================");
        log("SYSTEM", "Timestamp: " + SDF.format(new Date()));
        log("SYSTEM", "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.PRODUCT + ")");
        log("SYSTEM", "Hardware: " + Build.HARDWARE + " | Board: " + Build.BOARD);
        log("SYSTEM", "Primary ABI: " + (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "arm64-v8a"));
        log("SYSTEM", "Android OS: Android " + Build.VERSION.RELEASE + " (API Level " + Build.VERSION.SDK_INT + ")");
        log("SYSTEM", "Log Directory: /storage/emulated/0/Download/ninmods/ninmods.log");
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

    public static void log(String subTag, String message) {
        Log.i(TAG, "[" + subTag + "] " + message);
        String time = SDF.format(new Date());
        String line = "[" + time + "] [" + subTag + "] " + message;
        recordInMemory(line);
        writeToDownloadFile(line);
    }

    public static void logE(String subTag, String message, Throwable t) {
        Log.e(TAG, "[" + subTag + "] " + message, t);
        String time = SDF.format(new Date());
        String fullMsg = message + (t != null ? " : " + t.getMessage() : "");
        String line = "[" + time + "] [ERROR][" + subTag + "] " + fullMsg;
        recordInMemory(line);
        writeToDownloadFile(line);
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
                result.add("No live memory logs recorded yet. Telemetry streaming to Download/ninmods/ninmods.log.");
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
