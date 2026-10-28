package com.ninmods.main.core;

import android.content.Context;
import android.content.Intent;
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
 * Connects the Main App with the Ring 0 kernel driver of the loader APK.
 * Provides a real time inquiry of the driver node, the device ids and the
 * state of the IOCTL interface.
 */
public class DriverManager {

    private static final String TAG = "DriverManager";

    public static final String MAIN_PACKAGE_NAME = "com.ninmods.main";
    public static final String LOADER_PACKAGE_NAME = "com.ghost.driver.loader";

    public static final String PRIMARY_DRIVER_NODE = "/dev/BYNb";
    public static final String[] CANDIDATE_DRIVER_NODES = {
            "/dev/BYNb",
            "/dev/mem_tool",
            "/dev/nh",
            "/dev/wanbai",
            "/proc/uevents_records"
    };

    static {
        try {
            System.loadLibrary("ninmods");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load native library in DriverManager: " + t.getMessage());
        }
    }

    /**
     * Holds the real state of the driver.
     */
    public static class DriverInfo {
        /** True when the driver answered an IOCTL successfully. */
        public boolean isActive = false;
        /** Character device that carries the IOCTL interface. */
        public String activeNode = "None";
        /** Device id of the active node. */
        public String deviceId = "Unknown";
        /** Major number of the active node. */
        public int major = 0;
        /** Minor number of the active node. */
        public int minor = 0;
        /** Permission bits of the active node. */
        public String permissions = "Inaccessible";
        /** Kernel module that carries the driver, if it was found. */
        public String kernelModule = "Not Detected";
        /** Loader mode of the build. */
        public String loaderMode = "RING_0_KERNEL";
        /** Human readable state of the driver. */
        public String statusText = "Driver Inactive";
        /** True when the loader APK is installed. */
        public boolean loaderInstalled = false;
    }

    /**
     * Checks if Driver Loader APK (com.ghost.driver.loader) is installed on the device.
     *
     * @param context context used for the package manager
     * @return true when the loader is installed
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
     * Checks whether the kernel driver is really usable.
     *
     * A node that only exists on the file system is NOT enough: the driver is
     * only active when it answered an IOCTL. The old implementation reported
     * every device node it could find as an active driver, so the dashboard
     * showed a green driver state on devices without a working driver.
     *
     * @return true when the driver answered an IOCTL
     */
    public static boolean isDriverActive() {
        try {
            DriverInfo info = inquireDriverInfo(null);
            return info != null && info.isActive;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Returns the character device that carries the IOCTL interface.
     *
     * @return the node name, or null when no driver answered
     */
    public static String getActiveDriverNode() {
        try {
            DriverInfo info = inquireDriverInfo(null);
            if (info != null && info.isActive && info.activeNode != null
                    && !info.activeNode.equals("None")) {
                return info.activeNode;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Performs a real time inquiry of the Ring 0 driver.
     *
     * The native probe answers with the state of the character device and of
     * the last IOCTL. The Java part only adds the information that the native
     * layer can not read: the loader installation and the kernel module that
     * is loaded. Neither of them can mark the driver as active.
     *
     * @param context context of the caller, may be null
     * @return the real state of the driver
     */
    public static DriverInfo inquireDriverInfo(Context context) {
        DriverInfo info = new DriverInfo();
        info.loaderInstalled = isLoaderInstalled(context);

        // 1. Native probe: the only source that can prove the driver works.
        try {
            String nativeJson = nativeProbeDriverInfo();
            if (nativeJson != null && !nativeJson.trim().isEmpty()) {
                JSONObject obj = new JSONObject(nativeJson);
                info.isActive = obj.optBoolean("is_active", false);
                info.activeNode = obj.optString("active_node", "None");
                info.major = obj.optInt("major", 0);
                info.minor = obj.optInt("minor", 0);
                info.deviceId = obj.optString("device_id",
                        "Major: " + info.major + ", Minor: " + info.minor);
                info.permissions = obj.optString("permissions", "Inaccessible");
                info.statusText = obj.optString("status_text",
                        info.isActive ? "Active (Ring 0 Kernel)" : "Inactive");
            }
        } catch (Throwable t) {
            Log.d(TAG, "Native driver probe failed: " + t.getMessage());
        }

        // 2. The character device of the loader, reported for information.
        if (!info.isActive) {
            for (String node : CANDIDATE_DRIVER_NODES) {
                File file = new File(node);
                if (!file.exists()) {
                    continue;
                }
                info.activeNode = node;
                boolean canRead = file.canRead();
                boolean canWrite = file.canWrite();
                info.permissions = (canRead ? "r" : "-") + (canWrite ? "w" : "-");
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        android.system.StructStat stat = android.system.Os.stat(node);
                        long rdev = stat.st_rdev;
                        info.major = (int) ((rdev >> 8) & 0xFFF);
                        info.minor = (int) ((rdev & 0xFF) | ((rdev >> 12) & 0xFFF00));
                        info.deviceId = "Major: " + info.major + ", Minor: " + info.minor
                                + " (0x" + Long.toHexString(rdev).toUpperCase() + ")";
                    }
                } catch (Throwable ignored) {
                    info.deviceId = "Node Present (" + node + ")";
                }
                break;
            }
        }

        // 3. The kernel module that carries the driver. It is reported for
        //    information only: a loaded module does not prove that the IOCTL
        //    interface works, so it never switches the driver to active.
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/modules"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length > 0) {
                    String modName = parts[0].toLowerCase();
                    if (modName.contains("5.10") || modName.contains("4.14") || modName.contains("4.19") ||
                        modName.contains("4.9") || modName.contains("5.15") || modName.contains("6.1") ||
                        modName.contains("nh") || modName.contains("mem_tool") || modName.contains("bynb")) {
                        info.kernelModule = parts[0] + " (Size: "
                                + (parts.length > 1 ? parts[1] + " B" : "Loaded") + ")";
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (!info.isActive && info.statusText.equals("Driver Inactive")) {
            info.statusText = "NOT LOADED / INACTIVE";
        }

        return info;
    }

    /**
     * Launches the kernel driver loader.
     *
     * @param context context of the caller
     * @return true when the loader was started
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
        }
        Toast.makeText(context, "Kernel Driver 2.0 is not installed!", Toast.LENGTH_LONG).show();
        AppLogger.logE("DRIVER_MGR", "Failed to launch Driver Loader: " + LOADER_PACKAGE_NAME, null);
        return false;
    }

    /**
     * Native JNI driver probing method.
     *
     * @return the state of the driver as a JSON document
     */
    public static native String nativeProbeDriverInfo();
}
