package nin.app.core;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_ALL_FILES_PERMISSION = 1002;
    private static final int REQUEST_LEGACY_STORAGE_PERMISSION = 1003;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1004;
    private static final int REQUEST_LOCATION_PERMISSION = 1005;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Copy daemon and load native library
        copyDaemon(this);
        System.loadLibrary("ninmods");

        // Start sequential official Android permission pipeline
        processNextPermission();
    }

    /**
     * Sequential permission handler using official Android native methods:
     * 1. Overlay Permission (Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
     * 2. Super Storage Permission (Environment.isExternalStorageManager / All Files Access)
     * 3. Notification Permission (POST_NOTIFICATIONS for Android 13+)
     * 4. Location Permission (ACCESS_FINE_LOCATION & ACCESS_COARSE_LOCATION)
     * 5. Proceed to LoginActivity
     */
    private void processNextPermission() {
        // Step 1: Overlay Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                return;
            }
        }

        // Step 2: Super Storage Permission (All Files Access)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_ALL_FILES_PERMISSION);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, REQUEST_ALL_FILES_PERMISSION);
                }
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_LEGACY_STORAGE_PERMISSION);
                return;
            }
        }

        // Step 3: Notification Permission (Android 13+ / API 33+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{"android.permission.POST_NOTIFICATIONS"},
                        REQUEST_NOTIFICATION_PERMISSION);
                return;
            }
        }

        // Step 4: Location Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
            return;
        }

        // Step 5: All permissions granted in order -> Start LoginActivity
        startApp();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION || requestCode == REQUEST_ALL_FILES_PERMISSION) {
            processNextPermission();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LEGACY_STORAGE_PERMISSION
                || requestCode == REQUEST_NOTIFICATION_PERMISSION
                || requestCode == REQUEST_LOCATION_PERMISSION) {
            processNextPermission();
        }
    }

    private void startApp() {
        Intent intent = new Intent(this, nin.app.auth.LoginActivity.class);
        startActivity(intent);
        finish();
    }

    public static void copyDaemon(Activity context) {
        String abi = android.os.Build.SUPPORTED_ABIS[0];
        String assetName = abi.contains("x86") ? "Titoo1" : "Titoo";
        String outPath = context.getCacheDir().getAbsolutePath() + "/" + assetName;

        try {
            java.io.InputStream in = context.getAssets().open(assetName);
            java.io.File outFile = new java.io.File(outPath);
            java.io.FileOutputStream out = new java.io.FileOutputStream(outFile);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.flush();
            out.close();
            Runtime.getRuntime().exec("chmod 777 " + outPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
