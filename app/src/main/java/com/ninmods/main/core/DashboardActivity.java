package com.ninmods.main.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.drawerlayout.widget.DrawerLayout;

import com.ninmods.main.auth.AuthApiController;
import com.ninmods.main.auth.AuthManager;
import com.ninmods.main.menu.IMenuProject;
import com.ninmods.main.menu.MenuRouter;
import com.ninmods.main.ui.DashboardContentView;
import com.ninmods.main.ui.HeaderView;
import com.ninmods.main.ui.SidebarView;

public class DashboardActivity extends BaseActivity {

    static {
        System.loadLibrary("ninmods");
    }

    private DrawerLayout drawerLayout;
    private LinearLayout mainContainer;
    private View currentBodyView;
    private BroadcastReceiver driverStateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildInterface();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (drawerLayout != null && !drawerLayout.isDrawerOpen(Gravity.START)) {
            if (Build.VERSION.SDK_INT >= 31) {
                if (mainContainer != null) mainContainer.setRenderEffect(null);
                if (getFooterView() != null) getFooterView().setRenderEffect(null);
            }
        }
        if (driverStateReceiver == null) {
            driverStateReceiver = DriverManager.registerDriverStateReceiver(this, (isLoaded, mode, activeNode) -> {
                runOnUiThread(() -> {
                    Toast.makeText(DashboardActivity.this, "Driver: " + (isLoaded ? "ACTIVE (" + mode + ")" : "OFFLINE"), Toast.LENGTH_SHORT).show();
                    refreshBody();
                });
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (driverStateReceiver != null) {
            try {
                unregisterReceiver(driverStateReceiver);
            } catch (Exception ignored) {}
            driverStateReceiver = null;
        }
    }

    private void buildInterface() {
        drawerLayout = new DrawerLayout(this);
        drawerLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        drawerLayout.setScrimColor(Color.parseColor("#80000000")); // Clean 50% dark dim scrim behind open drawer

        // Hardware-accelerated full screen blur behind the sidebar when opened
        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            private RenderEffect cachedDrawerBlur = null;
            private boolean isBlurred = false;

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                if (Build.VERSION.SDK_INT >= 31) {
                    if (slideOffset > 0.05f && !isBlurred) {
                        if (cachedDrawerBlur == null) {
                            cachedDrawerBlur = RenderEffect.createBlurEffect(
                                    18.0f, 18.0f, Shader.TileMode.CLAMP
                            );
                        }
                        mainContainer.setRenderEffect(cachedDrawerBlur);
                        if (getFooterView() != null) {
                            getFooterView().setRenderEffect(cachedDrawerBlur);
                        }
                        isBlurred = true;
                    } else if (slideOffset <= 0.05f && isBlurred) {
                        mainContainer.setRenderEffect(null);
                        if (getFooterView() != null) {
                            getFooterView().setRenderEffect(null);
                        }
                        isBlurred = false;
                    }
                }
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                if (Build.VERSION.SDK_INT >= 31 && !isBlurred) {
                    if (cachedDrawerBlur == null) {
                        cachedDrawerBlur = RenderEffect.createBlurEffect(
                                18.0f, 18.0f, Shader.TileMode.CLAMP
                        );
                    }
                    mainContainer.setRenderEffect(cachedDrawerBlur);
                    if (getFooterView() != null) {
                        getFooterView().setRenderEffect(cachedDrawerBlur);
                    }
                    isBlurred = true;
                }
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                if (Build.VERSION.SDK_INT >= 31) {
                    mainContainer.setRenderEffect(null);
                    if (getFooterView() != null) {
                        getFooterView().setRenderEffect(null);
                    }
                    isBlurred = false;
                }
            }

            @Override
            public void onDrawerStateChanged(int newState) {}
        });

        // Main Layout Container (Transparent so blurred particle animation shines through)
        mainContainer = new LinearLayout(this);
        mainContainer.setLayoutParams(new DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setBackgroundColor(Color.TRANSPARENT);

        // 1. Separate Header File Import (HeaderView)
        View headerView = HeaderView.create(this, new HeaderView.HeaderListener() {
            @Override
            public void onMenuClick() {
                drawerLayout.openDrawer(Gravity.START);
            }

            @Override
            public void onProfileClick() {
                startActivity(new Intent(DashboardActivity.this, ProfileActivity.class));
            }
        });
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
            public void onDriverLoaderClick() {
                drawerLayout.closeDrawer(Gravity.START);
                DriverManager.launchDriverLoader(DashboardActivity.this);
            }


            @Override
            public void onProfileClick() {
                drawerLayout.closeDrawer(Gravity.START);
                startActivity(new Intent(DashboardActivity.this, ProfileActivity.class));
            }
        });
        drawerLayout.addView(sidebarView);

        setContentView(drawerLayout);
    }

    private void refreshBody() {
        if (currentBodyView != null) {
            mainContainer.removeView(currentBodyView);
        }
        currentBodyView = DashboardContentView.create(this, new DashboardContentView.DashboardListener() {
            @Override
            public void onBrandMenuClick(IMenuProject menu) {
                showKeyVerificationDialog(menu);
            }

            @Override
            public void onGameClick(final String packageName, final String gameName, boolean isInstalled) {
                if (!isInstalled) {
                    Toast.makeText(DashboardActivity.this, gameName + " is not installed on this device.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!DriverManager.isDriverActive()) {
                    new AlertDialog.Builder(DashboardActivity.this)
                            .setTitle("Driver Loader Required")
                            .setMessage("Ring 0 Kernel Driver is not active. Would you like to open the Driver Loader (" + DriverManager.LOADER_PACKAGE_NAME + ") first?")
                            .setPositiveButton("OPEN LOADER", (dialog, which) -> {
                                DriverManager.launchDriverLoader(DashboardActivity.this);
                            })
                            .setNeutralButton("LAUNCH ANYWAY", (dialog, which) -> {
                                launchGame(packageName, gameName);
                            })
                            .setNegativeButton("CANCEL", null)
                            .show();
                    return;
                }

                launchGame(packageName, gameName);
            }
        });
        mainContainer.addView(currentBodyView);
    }

    private void launchGame(final String packageName, final String gameName) {
        new AlertDialog.Builder(DashboardActivity.this)
                .setTitle("Launch " + gameName)
                .setMessage("Do you want to launch " + gameName + "?")
                .setPositiveButton("LAUNCH", (dialog, which) -> {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    } else {
                        Toast.makeText(DashboardActivity.this, "Unable to launch " + gameName, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void showKeyVerificationDialog(final IMenuProject menu) {
        if (menu == null || isFinishing()) return;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(16), dp(24), dp(8));

        final TextView tvError = new TextView(this);
        tvError.setTextColor(Color.parseColor("#FF5252"));
        tvError.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvError.setVisibility(View.GONE);
        LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errLp.bottomMargin = dp(8);
        tvError.setLayoutParams(errLp);
        container.addView(tvError);

        final EditText etKey = new EditText(this);
        etKey.setHint("Enter license key");
        etKey.setSingleLine(true);
        String activeKey = AuthManager.getActiveLicenseKey(this);
        if (activeKey != null && !activeKey.trim().isEmpty()) {
            etKey.setText(activeKey.trim());
        }
        container.addView(etKey);

        final ProgressBar progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        pbLp.gravity = Gravity.CENTER_HORIZONTAL;
        pbLp.topMargin = dp(8);
        progressBar.setLayoutParams(pbLp);
        container.addView(progressBar);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(menu.getDisplayName(this) + " - KEY ACTIVATION")
                .setMessage("Enter your paid license key to activate. The key will be permanently locked to this hardware device.")
                .setView(container)
                .setPositiveButton("ACTIVATE", null)
                .setNegativeButton("CANCEL", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btnActivate = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnActivate.setOnClickListener(v -> {
                String inputKey = etKey.getText() != null ? etKey.getText().toString().trim() : "";
                if (inputKey.isEmpty()) {
                    tvError.setText("Please enter your key.");
                    tvError.setVisibility(View.VISIBLE);
                    return;
                }

                tvError.setVisibility(View.GONE);
                btnActivate.setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);
                etKey.setEnabled(false);

                AuthApiController.verifyLicenseKey(DashboardActivity.this, inputKey, menu.getId(), new AuthApiController.ApiCallback() {
                    @Override
                    public void onSuccess(String message) {
                        if (!dialog.isShowing()) return;
                        dialog.dismiss();
                        Toast.makeText(DashboardActivity.this, "Key Verified! Connecting Ring 0 Driver...", Toast.LENGTH_SHORT).show();
                        launchMenuWithOverlayCheck(menu, "Ring 0 Driver");
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (!dialog.isShowing()) return;
                        progressBar.setVisibility(View.GONE);
                        btnActivate.setEnabled(true);
                        etKey.setEnabled(true);
                        tvError.setText(errorMessage);
                        tvError.setVisibility(View.VISIBLE);
                    }
                });
            });
        });

        dialog.show();
    }

    private void launchMenuWithOverlayCheck(IMenuProject menu, String engineName) {
        if (menu == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant Overlay Permission to display menu", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }

        if (!DriverManager.isDriverActive()) {
            new AlertDialog.Builder(this)
                    .setTitle("Kernel Driver Required")
                    .setMessage("Ring 0 Kernel Driver is currently offline. Open Driver Loader (" + DriverManager.LOADER_PACKAGE_NAME + ") now?")
                    .setPositiveButton("OPEN LOADER", (d, which) -> {
                        DriverManager.launchDriverLoader(DashboardActivity.this);
                    })
                    .setNeutralButton("LAUNCH ANYWAY", (d, which) -> {
                        MenuRouter.launchMenu(DashboardActivity.this, menu.getId());
                    })
                    .setNegativeButton("CANCEL", null)
                    .show();
            return;
        }

        Toast.makeText(this, menu.getDisplayName(this) + " (Ring 0 Driver) Launched!", Toast.LENGTH_SHORT).show();
        MenuRouter.launchMenu(this, menu.getId());
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    public static native void setModeNative(int mode);
}
