package nin.app.core;

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

import nin.app.auth.AuthApiController;
import nin.app.auth.AuthManager;
import nin.app.menu.IMenuProject;
import nin.app.menu.MenuRouter;
import nin.app.ui.DashboardContentView;
import nin.app.ui.HeaderView;
import nin.app.ui.SidebarView;

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
        if (menu == null) return;
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout dialogRoot = new FrameLayout(this);
        dialogRoot.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        dialogRoot.setPadding(dp(16), dp(16), dp(16), dp(16));

        final LinearLayout card = new LinearLayout(this);
        card.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(22), dp(20), dp(18));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(AppColors.MAGENTA_CARD);
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke(dp(1), menu.getThemeColor());
        card.setBackground(cardBg);

        // 1. Brand Icon
        ImageView icon = new ImageView(this);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(54), dp(54));
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        iconLp.bottomMargin = dp(10);
        icon.setLayoutParams(iconLp);
        icon.setImageResource(menu.getIconResId());
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(icon);

        // 2. Title (Brand Name + KEY)
        TextView title = new TextView(this);
        title.setText(menu.getDisplayName(this) + " KEY");
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = dp(14);
        title.setLayoutParams(titleLp);
        card.addView(title);

        // STAGE 1: Key Input Stage Container
        final LinearLayout stageKeyInput = new LinearLayout(this);
        stageKeyInput.setOrientation(LinearLayout.VERTICAL);
        stageKeyInput.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Error text (Positioned ABOVE Key input field)
        final TextView tvError = new TextView(this);
        LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errLp.bottomMargin = dp(8);
        tvError.setLayoutParams(errLp);
        tvError.setTextColor(Color.parseColor("#FF5252"));
        tvError.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvError.setGravity(Gravity.CENTER);
        tvError.setVisibility(View.GONE);
        stageKeyInput.addView(tvError);

        // Key Input Field with clean hint
        final EditText etKey = new EditText(this);
        etKey.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        etKey.setHint("Enter your key");
        etKey.setHintTextColor(AppColors.MAGENTA_TEXT_DIM);
        etKey.setTextColor(AppColors.WHITE);
        etKey.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        etKey.setSingleLine(true);
        etKey.setPadding(dp(14), 0, dp(14), 0);

        String activeKey = AuthManager.getActiveLicenseKey(this);
        if (activeKey != null && !activeKey.trim().isEmpty()) {
            etKey.setText(activeKey.trim());
        }

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(AppColors.MAGENTA_CHIP);
        inputBg.setCornerRadius(dp(8));
        inputBg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        etKey.setBackground(inputBg);
        stageKeyInput.addView(etKey);

        // Official Android Native Circular Loading Component
        final ProgressBar progressBar = new ProgressBar(this);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        pbLp.gravity = Gravity.CENTER_HORIZONTAL;
        pbLp.topMargin = dp(12);
        pbLp.bottomMargin = dp(4);
        progressBar.setLayoutParams(pbLp);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        stageKeyInput.addView(progressBar);

        // Verify Button
        final Button btnVerify = new Button(this);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        btnLp.topMargin = dp(12);
        btnVerify.setLayoutParams(btnLp);
        btnVerify.setText("VERIFY KEY");
        btnVerify.setTextColor(AppColors.WHITE);
        btnVerify.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnVerify.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(menu.getThemeColor());
        btnBg.setCornerRadius(dp(8));
        btnVerify.setBackground(btnBg);
        stageKeyInput.addView(btnVerify);

        // Cancel Button
        Button btnCancel = new Button(this);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        cancelLp.topMargin = dp(6);
        btnCancel.setLayoutParams(cancelLp);
        btnCancel.setText("CANCEL");
        btnCancel.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        btnCancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnCancel.setBackgroundColor(Color.TRANSPARENT);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        stageKeyInput.addView(btnCancel);

        card.addView(stageKeyInput);

        // Key verification execution
        btnVerify.setOnClickListener(v -> {
            String inputKey = etKey.getText() != null ? etKey.getText().toString().trim() : "";
            if (inputKey.isEmpty()) {
                tvError.setText("Please enter your key.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            // Start Android Native Circular Loading Component
            tvError.setVisibility(View.GONE);
            btnVerify.setVisibility(View.GONE);
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
                    btnVerify.setVisibility(View.VISIBLE);
                    etKey.setEnabled(true);
                    tvError.setText(errorMessage);
                    tvError.setVisibility(View.VISIBLE);
                }
            });
        });

        dialogRoot.addView(card);
        dialog.setContentView(dialogRoot);
        dialog.setOnShowListener(d -> setScreenBlur(true));
        dialog.setOnDismissListener(d -> setScreenBlur(false));

        if (dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);

            // Official Android Native Background Blur and Dim Component
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.65f);

            if (Build.VERSION.SDK_INT >= 31) { // Android 12+ (Build.VERSION_CODES.S)
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.setBlurBehindRadius(dp(25));
                window.setAttributes(lp);
            }
        }
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
