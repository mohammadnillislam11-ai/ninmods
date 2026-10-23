package nin.app.core;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppLogger {
    private static final String TAG = "NINMODS_JAVA";
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    public static void log(String subTag, String message) {
        Log.i(TAG, "[" + subTag + "] " + message);
        write("[" + subTag + "] " + message);
    }

    public static void logE(String subTag, String message, Throwable t) {
        Log.e(TAG, "[" + subTag + "] " + message, t);
        write("[ERROR] [" + subTag + "] " + message + (t != null ? " : " + t.getMessage() : ""));
    }

    private static void write(String text) {
        synchronized (LOCK) {
            String time = SDF.format(new Date());
            String line = "[" + time + "] " + text + "\n";
            File[] targets = new File[]{
                new File("/sdcard/ninmods.log"),
                new File(Environment.getExternalStorageDirectory(), "ninmods.log"),
                new File("/data/data/com.ninmods.main/files/ninmods.log")
            };
            for (File target : targets) {
                try {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    FileWriter fw = new FileWriter(target, true);
                    fw.write(line);
                    fw.flush();
                    fw.close();
                    break;
                } catch (Throwable ignored) {}
            }
        }
    }

    public static List<String> getRecentLogs(int maxLines) {
        List<String> result = new ArrayList<>();
        File logFile = new File("/sdcard/ninmods.log");
        if (!logFile.exists()) {
            logFile = new File("/data/data/com.ninmods.main/files/ninmods.log");
        }
        if (!logFile.exists()) {
            result.add("No log file found yet (/sdcard/ninmods.log)");
            return result;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            List<String> allLines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                allLines.add(line);
            }
            int start = Math.max(0, allLines.size() - maxLines);
            for (int i = start; i < allLines.size(); i++) {
                result.add(allLines.get(i));
            }
        } catch (Throwable t) {
            result.add("Error reading logs: " + t.getMessage());
        }
        return result;
    }

    public static void clearLogs() {
        synchronized (LOCK) {
            File[] targets = new File[]{
                new File("/sdcard/ninmods.log"),
                new File(Environment.getExternalStorageDirectory(), "ninmods.log"),
                new File("/data/data/nin.app/files/ninmods.log")
            };
            for (File target : targets) {
                try {
                    if (target.exists()) {
                        target.delete();
                    }
                } catch (Throwable ignored) {}
            }
        }
    }
}
