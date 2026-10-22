package nin.app.core;

import nin.app.overlay.Floater;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Initialize {

    public String daemonEXE;
    public static String socket;
    private static final String TAG = "DAEMON_INIT";

    public Initialize(final Context context) {
        // Load the native library
        System.loadLibrary("ninmods");
        daemonEXE = Utils.getDaemonName();
        final String gamePackage = Utils.getGamePackage();

        if (daemonEXE == null || daemonEXE.equals("a") || gamePackage == null || gamePackage.equals("a")) {
            daemonEXE = "Titoo";
        }

        // 1. Launch the Game
        try {
            PackageManager packageManager = context.getPackageManager();
            Intent launchIntent = packageManager.getLaunchIntentForPackage(gamePackage);
            if (launchIntent != null) {
                context.startActivity(launchIntent);
                //applyIptablesRules();
                
                // 2. Start daemon setup after 20 seconds delay
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Run Setup in Background Thread to prevent "Signal 3" (ANR) crash
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {

                                    String outPath = context.getFilesDir().getPath();
                                    String daemonPath = outPath + "/" + daemonEXE;

                                    // A. KILL existing daemon process to fix "Text file busy" (ETXTBSY)
                                    Runtime.getRuntime().exec("su -c killall -9 " + daemonEXE);
                                    Thread.sleep(300); // Wait for process to release file handle

                                    // B. Copy Binary from Assets
                                    boolean success = CopyFromAssets(context, outPath, daemonEXE);
                                    
                                    if (success) {
                                        // C. Set executable permissions
                                        Runtime.getRuntime().exec("su -c chmod 777 " + daemonPath).waitFor();
                                        File daemonFile = new File(daemonPath);
                                        if (!daemonFile.exists() || !daemonFile.canExecute()) {
                                            Log.e(TAG, "Daemon integrity/permission check failed");
                                            return;
                                        }

                                        // D. Prepare Shell Command
                                        socket = "su -c ./ " + daemonPath;

                                        // E. Start Daemon in background

                                        Runtime.getRuntime().exec("su -c " + daemonPath);

                                        // F. Return to Main Thread to show the Floater/Menu
                                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(context, "Tactical Menu Initialized!", Toast.LENGTH_SHORT).show();
                                                nin.app.menu.MenuRouter.launchActiveMenu(context);
                                            }
                                        });
                                    } else {
                                        Log.e(TAG, "Failed to copy daemon from assets");
                                    }

                                } catch (Exception e) {
                                    Log.e(TAG, "Error in background init: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            }
                        }).start();
                    }
                }, 3000);
                
            } else {
                Log.e(TAG, "Game package not found!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Safely copies the binary from the APK assets to the internal data folder.
     */
    public static boolean CopyFromAssets(Context ctx, String outPath, String fileName) {
        File file = new File(outPath);
        if (!file.exists()) {
            file.mkdirs();
        }
        
        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        
        try {
            inputStream = ctx.getAssets().open(fileName);
            File outFile = new File(file, fileName);
            fileOutputStream = new FileOutputStream(outFile);
            
            byte[] buffer = new byte[4096];
            int byteRead;
            while ((byteRead = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, byteRead);
            }
            
            fileOutputStream.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Copy Error: " + e.getMessage());
            return false;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (fileOutputStream != null) fileOutputStream.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
/*private void applyIptablesRules() {
    new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                String[] iptablesCommands = {
                    // Original INPUT chain rules
                    "iptables -A INPUT -s 192.168.1.1 -j DROP",
                    "iptables -A OUTPUT -d 192.168.1.1 -j DROP",
                    "iptables -A INPUT -s 192.168.31.1 -j DROP",
                    "iptables -A OUTPUT -d 192.168.31.1 -j DROP",
                    "iptables -A INPUT -s 192.168.0.1 -j DROP",
                    "iptables -A OUTPUT -d 192.168.0.1 -j DROP",
                    "iptables -A INPUT -s 10.0.0.1 -j DROP",
                    "iptables -A OUTPUT -d 10.0.0.1 -j DROP",
                    "iptables -A INPUT -s 1.1.1.1 -j DROP",
                    "iptables -A OUTPUT -d 1.1.1.1 -j DROP",
                    "iptables -A INPUT -s 8.8.8.8 -j DROP",
                    "iptables -A OUTPUT -d 8.8.8.8 -j DROP",
                    "iptables -A INPUT -s 8.8.4.4 -j DROP",
                    "iptables -A OUTPUT -d 8.8.4.4 -j DROP",
                    "iptables -A INPUT -s 114.114.114.114 -j DROP",
                    "iptables -A OUTPUT -d 114.114.114.114 -j DROP",
                    "iptables -A INPUT -s 203.116.180.99 -j DROP",
                    "iptables -A OUTPUT -d 203.116.180.99 -j DROP",
                    "iptables -A INPUT -s 202.81.117.88 -j DROP",
                    "iptables -A OUTPUT -d 202.81.117.88 -j DROP",
                    "iptables -A INPUT -s 202.81.118.4 -j DROP",
                    "iptables -A OUTPUT -d 202.81.118.4 -j DROP",
                    "iptables -A INPUT -s 49.51.133.79 -j DROP",
                    "iptables -A OUTPUT -d 49.51.133.79 -j DROP",
                    "iptables -A INPUT -s 164.52.102.0/24 -j DROP",
                    "iptables -A OUTPUT -d 164.52.102.0/24 -j DROP",
                    "iptables -A INPUT -s 199.0.0.0/8 -j DROP",
                    "iptables -A OUTPUT -d 199.0.0.0/8 -j DROP",
                    "iptables -A INPUT -s 164.0.0.0/8 -j DROP",
                    "iptables -A OUTPUT -d 164.0.0.0/8 -j DROP",
                    "iptables -A INPUT -s 60.0.0.0/8 -j DROP",
                    "iptables -A OUTPUT -d 60.0.0.0/8 -j DROP",
                    "iptables -A INPUT -s 63.0.0.0/8 -j DROP",
                    "iptables -A OUTPUT -d 63.0.0.0/8 -j DROP",
                    "iptables -A INPUT -s 56.0.0.0/8 -j DROP",
                    "iptables -A OUTPUT -d 56.0.0.0/8 -j DROP",
                    "iptables -A INPUT -s 49.51.42.152 -j DROP",
                    "iptables -A OUTPUT -d 49.51.42.152 -j DROP",
                    "iptables -A INPUT -s 49.51.185.106 -j DROP",
                    "iptables -A OUTPUT -d 49.51.185.106 -j DROP",
                    "iptables -A INPUT -s 150.109.185.81 -j DROP",
                    "iptables -A OUTPUT -d 150.109.185.81 -j DROP",
                    "iptables -A INPUT -s 103.16.33.161 -j DROP",
                    "iptables -A OUTPUT -d 103.16.33.161 -j DROP",
                    "iptables -A INPUT -s 103.16.33.171 -j DROP",
                    "iptables -A OUTPUT -d 103.16.33.171 -j DROP",
                    "iptables -A OUTPUT -p tcp -d 103.16.33.161 --dport 80 -j DROP",
                    "iptables -A OUTPUT -p tcp -d 103.16.33.161 --dport 443 -j DROP",
                    "iptables -A INPUT -p tcp -s 103.16.33.161 --sport 80 -j DROP",
                    "iptables -A INPUT -p tcp -s 103.16.33.161 --sport 443 -j DROP",
                    "iptables -A OUTPUT -p tcp -d 103.16.33.0/24 --dport 80 -j DROP",
                    "iptables -A OUTPUT -p tcp -d 103.16.33.0/24 --dport 443 -j DROP",
                    "iptables -A INPUT -p tcp -s 103.16.33.0/24 --sport 80 -j DROP",
                    "iptables -A INPUT -p tcp -s 103.16.33.0/24 --sport 443 -j DROP",                    
                    "iptables -A INPUT -s 192.168.1.1 -j DROP",
                    "iptables -A INPUT -s 192.168.31.1 -j DROP",
                    "iptables -A INPUT -s 192.168.0.1 -j DROP",
                    "iptables -A INPUT -s 10.0.0.1 -j DROP",
                    "iptables -A INPUT -s one.one.one.one -j DROP",
                    "iptables -A INPUT -s dns.google -j DROP",
                    "iptables -A INPUT -s dns.google -j DROP",
                    "iptables -A INPUT -s public1.114dns.com -j DROP",
                    "iptables -A INPUT -s 203.116.180-99.unknown.starhub.net.sg -j DROP",
                    "iptables -A INPUT -s 202.81.117.88 -j DROP",
                    "iptables -A INPUT -s 202.81.118.4 -j DROP",
                    "iptables -A INPUT -s 49.51.133.79 -j DROP",
                    "iptables -A INPUT -s 164.52.102.0/24 -j DROP",
                    "iptables -A INPUT -s 103.16.33.0/24 -j DROP",
                    "iptables -A INPUT -s 199.0.0.0/8 -j DROP",
                    "iptables -A INPUT -s 164-0-0-0.kcell.kz/8 -j DROP",
                    "iptables -A INPUT -s 60.0.0.0/8 -j DROP",
                    "iptables -A INPUT -s 63.0.0.0/8 -j DROP",
                    "iptables -A INPUT -s 56.0.0.0/8 -j DROP",
                    "iptables -A INPUT -s 49.51.42.152 -j DROP",
                    "iptables -A INPUT -s 49.51.185.106 -j DROP",
                    "iptables -A INPUT -s 150.109.185.81 -j DROP",
                    "iptables -A INPUT -s 103.16.33.161 -j DROP",
                    "iptables -A INPUT -s 103.16.33.171 -j DROP",
                    "iptables -A INPUT -p tcp -s 103.16.33.161 --sport http -j DROP",
                    "iptables -A INPUT -p tcp -s 103.16.33.161 --sport https -j DROP",
                    "iptables -A INPUT -p tcp -s 103.16.33.0/24 --sport http -j DROP",
                    "iptables -A INPUT -p tcp -s 103.16.33.0/24 --sport https -j DROP",
                    
                    // Original OUTPUT chain rules
                    "iptables -A OUTPUT -p tcp --dport http -j DROP",
                    "iptables -A OUTPUT -d 0.0.0.34.bc.googleusercontent.com/8 -j DROP",
                    "iptables -A OUTPUT -d v-guest-dhcp-dns-cool.umnet.umich.edu/8 -j DROP",

                    // Rules from C++ enableBypass()
                    // Block entire subnets
                    "iptables -A OUTPUT -d 148.222.0.0/16 -p udp -m udp --dport 443 -j DROP",
                    "iptables -A OUTPUT -d 148.222.0.0/16 -p tcp -m tcp --dport 443 -j DROP",
                    "iptables -A OUTPUT -d 34.0.0.0/8 -j DROP",
                    "iptables -A OUTPUT -d 35.0.0.0/8 -j DROP",
                    
                    // Block common ports (all destinations)
                    "iptables -A OUTPUT -p tcp -m tcp --dport 80 -j DROP",
                    "iptables -A OUTPUT -p udp -m udp --dport 80 -j DROP",
                    "iptables -A OUTPUT -p tcp -m tcp --dport 8001 -j DROP",
                    "iptables -A OUTPUT -p udp -m udp --dport 8001 -j DROP",
                    "iptables -A OUTPUT -p tcp -m tcp --dport 8012 -j DROP",
                    "iptables -A OUTPUT -p udp -m udp --dport 8012 -j DROP",
                    "iptables -A OUTPUT -p tcp -m tcp --dport 5574 -j DROP",
                    "iptables -A OUTPUT -p udp -m udp --dport 5574 -j DROP",
                    "iptables -A OUTPUT -p tcp -m tcp --dport 6674 -j DROP",
                    "iptables -A OUTPUT -p udp -m udp --dport 6674 -j DROP",
                    "iptables -A OUTPUT -p tcp -m tcp --dport 9006 -j DROP",
                    "iptables -A OUTPUT -p udp -m udp --dport 9006 -j DROP",
                    "iptables -A OUTPUT -p tcp -m tcp --dport 10880 -j DROP",
                    "iptables -A OUTPUT -p udp -m udp --dport 10880 -j DROP",
                    
                    // Block specific IPs
                    "iptables -A OUTPUT -d 103.78.86.116/32 -j DROP",
                    "iptables -A OUTPUT -d 103.78.86.117/32 -j DROP",
                    "iptables -A OUTPUT -d 124.158.134.3/32 -j DROP",
                    "iptables -A OUTPUT -d 124.158.134.11/32 -j DROP",
                    "iptables -A OUTPUT -d 143.92.112.32/32 -j DROP",
                    "iptables -A OUTPUT -d 143.92.120.186/32 -j DROP",
                    "iptables -A OUTPUT -d 143.92.120.201/32 -j DROP",
                    "iptables -A OUTPUT -d 143.92.126.103/32 -j DROP",
                    "iptables -A OUTPUT -d 143.92.126.139/32 -j DROP",
                    "iptables -A OUTPUT -d 202.81.117.206/32 -j DROP",
                    "iptables -A OUTPUT -d 202.181.73.175/32 -j DROP",
                    "iptables -A OUTPUT -d 202.181.74.24/32 -j DROP"
                };
                
                // Execute iptables commands in batches of 10 with 2-second wait
                int batchSize = 10;
                int totalCommands = iptablesCommands.length;
                
                for (int i = 0; i < totalCommands; i += batchSize) {
                    int end = Math.min(i + batchSize, totalCommands);
                    
                    // Process current batch of commands (up to 10)
                    for (int j = i; j < end; j++) {
                        try {
                            String cmd = iptablesCommands[j];
                            Process process = Runtime.getRuntime().exec("su -c " + cmd);
                            process.waitFor();
                        } catch (Exception e) {
                            // Silent execution
                        }
                    }
                    
                    // Log progress
                    int rulesApplied = Math.min(i + batchSize, totalCommands);
                
                    
                    // Wait 2 seconds before next batch, unless this was the last batch
                    if (end < totalCommands) {
                        try {
                            Thread.sleep(4000); // 2 second wait
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                
                
                
            } catch (Exception e) {
                
            }
        }
    }).start();
}*/
}
