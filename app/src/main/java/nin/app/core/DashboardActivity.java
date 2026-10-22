package nin.app.core;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.drawerlayout.widget.DrawerLayout;

import nin.app.overlay.Floater;
import nin.app.ui.DashboardContentView;
import nin.app.ui.HeaderView;
import nin.app.ui.SidebarView;

public class DashboardActivity extends Activity {

    static {
        System.loadLibrary("ninmods");
    }

    public static final int MODE_SAFE = 1;
    public static final int MODE_EXTREME = 2;
    public static final String PREF_KEY_MODE = "config_mode";

    private DrawerLayout drawerLayout;
    private LinearLayout mainContainer;
    private View currentBodyView;
    private int currentMode = MODE_SAFE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currentMode = Prefs.with(this).readInt(PREF_KEY_MODE, MODE_SAFE);
        setModeNative(currentMode);

        buildInterface();
    }

    private void buildInterface() {
        drawerLayout = new DrawerLayout(this);
        drawerLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Main Layout Container
        mainContainer = new LinearLayout(this);
        mainContainer.setLayoutParams(new DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setBackgroundColor(AppColors.BLACK);

        // 1. Separate Header File Import (HeaderView)
        View headerView = HeaderView.create(this, () -> drawerLayout.openDrawer(Gravity.START));
        mainContainer.addView(headerView);

        // 2. Separate Dashboard Body File Import (DashboardContentView)
        refreshBody();

        drawerLayout.addView(mainContainer);

        // 3. Separate Sidebar File Import (SidebarView)
        View sidebarView = SidebarView.create(this, new SidebarView.SidebarListener() {
            @Override
            public void onDashboardClick() {
                drawerLayout.closeDrawer(Gravity.START);
            }

            @Override
            public void onDeviceInfoClick() {
                drawerLayout.closeDrawer(Gravity.START);
                startActivity(new Intent(DashboardActivity.this, DeviceInfoActivity.class));
            }

            @Override
            public void onModeSelectClick() {
                drawerLayout.closeDrawer(Gravity.START);
                startActivity(new Intent(DashboardActivity.this, ModeSelectActivity.class));
            }
        });
        drawerLayout.addView(sidebarView);

        setContentView(drawerLayout);
    }

    private void refreshBody() {
        if (currentBodyView != null) {
            mainContainer.removeView(currentBodyView);
        }
        currentBodyView = DashboardContentView.create(this, currentMode, new DashboardContentView.DashboardListener() {
            @Override
            public void onStartMenu() {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(DashboardActivity.this)) {
                        android.widget.Toast.makeText(DashboardActivity.this, "Please grant Overlay Permission", android.widget.Toast.LENGTH_SHORT).show();
                        android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                        return;
                    }
                    Floater.start(DashboardActivity.this);
                    android.widget.Toast.makeText(DashboardActivity.this, "Floating Menu Started", android.widget.Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    android.widget.Toast.makeText(DashboardActivity.this, "Error starting menu: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onStopMenu() {
                try {
                    Floater.stop();
                    android.widget.Toast.makeText(DashboardActivity.this, "Floating Menu Stopped", android.widget.Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onModeSelected(int mode) {
                currentMode = mode;
                Prefs.with(DashboardActivity.this).writeInt(PREF_KEY_MODE, mode);
                setModeNative(mode);
                refreshBody();
            }

            @Override
            public void onLaunchGame(String packageName) {
                try {
                    Floater.start(DashboardActivity.this);
                } catch (Exception ignored) {}

                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                }
            }
        });
        mainContainer.addView(currentBodyView);
    }

    public static native boolean isLoggedInNative();
    public static native void setModeNative(int mode);
}
