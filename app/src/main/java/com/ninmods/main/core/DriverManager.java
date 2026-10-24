package com.ninmods.main.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * NINMODS Driver Integration Manager
 * Permanently links the Main App (com.ninmods.main) with the Driver Loader (com.ninmods.loader).
 * Provides deep real-time inquiry of Ring 0 Kernel Driver nodes, device IDs, and module statuses.
 */
public class DriverManager {

    private static final String TAG = "DriverManager";

    public static final String MAIN_PACKAGE_NAME = "com.ninmods.main";
    public static final String LOADER_PACKAGE_NAME = "com.ninmods.loader";
    public static final String LOADER_ACTIVITY_NAME = "com.ninmods.loader.MainActivity";
    public static final String ACTION_DRIVER_STATE = "com.ninmods.action.DRIVER_STATE";

    public static final String PRIMARY_DRIVER_NODE = "/dev/health_storage_service_alloc";
    public static final String[] CANDIDATE_DRIVER_NODES = {
            "/dev/health_storage_service_alloc",
            "/dev/BYNb",
            "/dev/uevents_records"
    };

    static {
        try {
            System.loadLibrary("ninmods");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load native library in DriverManager: " + t.getMessage());
        }
    }

    public static class DriverInfo {
        public boolean isActive = false;
        public String activeNode = "None";
        public String deviceId = "Unknown";
        public int major = 0;
        public int minor = 0;
        public String permissions = "Inaccessible";
        public String kernelModule = "Not Detected";
        public String loaderMode = "NONE";
        public String statusText = "Driver Inactive";
        public boolean loaderInstalled = false;
    }

    public interface DriverStateListener {
        void onDriverStateChanged(boolean isLoaded, String mode, String activeNode);
    }

    /**
     * Checks if Driver Loader APK (com.ninmods.loader) is installed on the device.
     */
    public static boolean isLoaderInstalled(Context context) {
        if (context == null) return false;
        try {
            context.getPackageManager().getPackageInfo(LOADER_PACKAGE_NAME, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if any of the Ring 0 Driver character device nodes are open and accessible.
     */
    public static boolean isDriverActive() {
        for (String node : CANDIDATE_DRIVER_NODES) {
            File f = new File(node);
            if (f.exists() && f.canRead()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the path of the currently active driver node.
     */
    public static String getActiveDriverNode() {
        for (String node : CANDIDATE_DRIVER_NODES) {
            File f = new File(node);
            if (f.exists()) {
                return node;
            }
        }
        return null;
    }

    /**
     * Performs a comprehensive, automated real-time inquiry of the Ring 0 Driver.
     * Evaluates character device nodes, major/minor device IDs, kernel modules,
     * file permissions, and persisted driver states.
     */
    public static DriverInfo inquireDriverInfo(Context context) {
        DriverInfo info = new DriverInfo();
        info.loaderInstalled = isLoaderInstalled(context);

        // 1. Try Native JNI Driver Probe first
        try {
            String nativeJson = nativeProbeDriverInfo();
            if (nativeJson != null && !nativeJson.trim().isEmpty()) {
                JSONObject obj = new JSONObject(nativeJson);
                info.isActive = obj.optBoolean("is_active", false);
                info.activeNode = obj.optString("active_node", "None");
                info.major = obj.optInt("major", 0);
                info.minor = obj.optInt("minor", 0);
                info.deviceId = obj.optString("device_id", "Major: " + info.major + ", Minor: " + info.minor);
                info.permissions = obj.optString("permissions", "rw-rw-rw-");
                info.statusText = obj.optString("status_text", info.isActive ? "Active (Ring 0)" : "Inactive");
            }
        } catch (Throwable t) {
            Log.d(TAG, "Native driver probe fallback to Java: " + t.getMessage());
        }

        // 2. Java Level Node Verification (if native didn't find active node)
        if (!info.isActive) {
            for (String node : CANDIDATE_DRIVER_NODES) {
                File f = new File(node);
                if (f.exists()) {
                    info.activeNode = node;
                    boolean canR = f.canRead();
                    boolean canW = f.canWrite();
                    info.permissions = (canR ? "r" : "-") + (canW ? "w" : "-");
                    info.isActive = canR;

                    // Query device ID using android.system.Os.stat if available
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            android.system.StructStat stat = android.system.Os.stat(node);
                            long rdev = stat.st_rdev;
                            info.major = (int) ((rdev >> 8) & 0xFFF);
                            info.minor = (int) ((rdev & 0xFF) | ((rdev >> 12) & 0xFFF00));
                            info.deviceId = "Major: " + info.major + ", Minor: " + info.minor + " (0x" + Long.toHexString(rdev).toUpperCase() + ")";
                        }
                    } catch (Throwable ignored) {
                        info.deviceId = "Node Present (" + node + ")";
                    }
                    break;
                }
            }
        }

        // 3. Inspect /proc/modules for genuine loaded kernel module
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/modules"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length > 0) {
                    String modName = parts[0].toLowerCase();
                    if (modName.contains("5.10") || modName.contains("4.14") || modName.contains("4.19") ||
                        modName.contains("nh") || modName.contains("mem_tool") || modName.contains("bynb") ||
                        modName.contains("health_storage") || modName.contains("driver")) {
                        info.kernelModule = parts[0] + " (Size: " + (parts.length > 1 ? parts[1] + " B" : "Loaded") + ")";
                        info.isActive = true;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 4. Check persisted state from /data/local/tmp/.nin_driver_state
        File stateFile = new File("/data/local/tmp/.nin_driver_state");
        if (stateFile.exists() && stateFile.canRead()) {
            try (BufferedReader br = new BufferedReader(new FileReader(stateFile))) {
                String stateJson = br.readLine();
                if (stateJson != null && !stateJson.trim().isEmpty()) {
                    JSONObject obj = new JSONObject(stateJson);
                    info.loaderMode = obj.optString("mode", "NONE");
                    String status = obj.optString("status", "");
                    if ("ACTIVE".equalsIgnoreCase(status)) {
                        info.isActive = true;
                        if ("ROOT_RAM".equalsIgnoreCase(info.loaderMode)) {
                            info.statusText = "ACTIVE & RESPONDING (RAM Daemon)";
                        } else {
                            info.statusText = "ACTIVE & RESPONDING (Ring 0 Kernel RT)";
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (!info.isActive) {
            info.statusText = "NOT LOADED / INACTIVE";
        }

        return info;
    }

    /**
     * Seamlessly launches the Driver Loader (com.ninmods.loader).
     */
    public static boolean launchDriverLoader(Context context) {
        if (context == null) return false;
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(LOADER_PACKAGE_NAME);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            AppLogger.log("DRIVER_MGR", "Launched Driver Loader: " + LOADER_PACKAGE_NAME);
            return true;
        } else {
            Toast.makeText(context, "Driver Loader (" + LOADER_PACKAGE_NAME + ") is not installed!", Toast.LENGTH_LONG).show();
            AppLogger.logE("DRIVER_MGR", "Failed to launch Driver Loader - package not installed: " + LOADER_PACKAGE_NAME, null);
            return false;
        }
    }

    /**
     * Registers a BroadcastReceiver to listen for real-time driver state updates sent by com.ninmods.loader.
     */
    public static BroadcastReceiver registerDriverStateReceiver(Context context, final DriverStateListener listener) {
        if (context == null || listener == null) return null;

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent != null && ACTION_DRIVER_STATE.equals(intent.getAction())) {
                    String mode = intent.getStringExtra("mode");
                    String status = intent.getStringExtra("status");
                    boolean isActive = "ACTIVE".equalsIgnoreCase(status);
                    String node = getActiveDriverNode();
                    AppLogger.log("DRIVER_MGR", "Received driver broadcast: mode=" + mode + ", status=" + status + ", node=" + node);
                    listener.onDriverStateChanged(isActive, mode != null ? mode : "NONE", node);
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_DRIVER_STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
        return receiver;
    }

    // Native JNI driver probing method
    public static native String nativeProbeDriverInfo();
}
