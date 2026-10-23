package nin.app.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import java.io.File;

/**
 * NINMODS Driver Integration Manager
 * Permanently links the Main App (nin.app) with the Driver Loader (com.ninmods.loader).
 */
public class DriverManager {

    public static final String MAIN_PACKAGE_NAME = "com.ninmods.main";
    public static final String LOADER_PACKAGE_NAME = "com.ninmods.loader";
    public static final String LOADER_ACTIVITY_NAME = "com.ninmods.loader.MainActivity";
    public static final String ACTION_DRIVER_STATE = "com.ninmods.action.DRIVER_STATE";

    public static final String PRIMARY_DRIVER_NODE = "/dev/health_storage_service_alloc";
    public static final String[] CANDIDATE_DRIVER_NODES = {
            "/dev/health_storage_service_alloc",
            "/dev/BYNb",
            "/dev/sched_debug",
            "/dev/uevents_records"
    };

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
}
