package com.ninmods.main.core;

import android.app.Activity;
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
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
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
import com.ninmods.main.auth.LoginActivity;
import com.ninmods.main.auth.SecurityVault;
import com.ninmods.main.menu.IMenuProject;
import com.ninmods.main.menu.MenuRouter;
import com.ninmods.main.ui.DashboardContentView;
import com.ninmods.main.ui.HeaderView;
import com.ninmods.main.ui.SidebarView;

public class DashboardActivity extends BaseActivity {

    static {
        try {
            System.loadLibrary("ninmods");
        } catch (Throwable t) {
            android.util.Log.e("DashboardActivity", "Failed to load ninmods: " + t.getMessage());
        }
    }

    private DrawerLayout drawerLayout;
    private LinearLayout mainContainer;
    private View currentBodyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Persistent Login Check
        if (!AuthManager.isLoggedIn(this)) {
            Intent intent = new Intent(this, com.ninmods.main.auth.LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            return;
        }

        try {
            // Restore native cryptographic session from saved token if active
            if (AuthManager.hasActiveLicense(this)) {
                String sessionToken = AuthManager.getSessionToken(this);
                String hwid = AuthApiController.getHwid(this);
                long remSec = AuthManager.getRemainingSeconds(this);
                if (sessionToken != null && !sessionToken.isEmpty()) {
                    SecurityVault.setNativeSession(sessionToken, hwid, remSec);
                }
            }

            buildInterface();
        } catch (Throwable t) {
            AppLogger.logE("DASHBOARD", "Failed to build dashboard interface: " + t.getMessage(), t);
            showFatalError("Dashboard Error", "Could not load dashboard components: " + t.getMessage());
            return;
        }

        // Asynchronously synchronize active plan and license directly from server database
        AuthApiController.fetchProfile(this, new AuthApiController.ApiCallback() {
            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> {
                    DashboardContentView.updateLicenseCard(currentBodyView, DashboardActivity.this);
                    refreshBody();
                });
            }
            @Override
            public void onError(String errorMessage) {}
        });
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
        refreshBody();
    }

    private void buildInterface() {
        drawerLayout = new DrawerLayout(this);
        drawerLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        // Clean high-performance 55% dark dim scrim behind open drawer (Zero-lag hardware acceleration)
        drawerLayout.setScrimColor(Color.parseColor("#990B0411"));

        // Smooth Drawer Listener without heavy per-frame blur calculations
        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                // Per-frame blur removed: Continuous shader recreation on slide caused noticeable frame drops and lag.
                // Standard hardware scrim provides instant, fluid 60/120 FPS drawer animation.
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                // Subtle static blur applied only once after drawer has fully opened
                com.ninmods.main.ui.BlurHelper.applyViewBlur(mainContainer, 12f);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                com.ninmods.main.ui.BlurHelper.clearViewBlur(mainContainer);
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

            @Override
            public void onLogoutClick() {
                drawerLayout.closeDrawer(Gravity.START);
                confirmLogout();
            }
        });
        drawerLayout.addView(sidebarView);

        setContentView(drawerLayout);
    }

    private void confirmLogout() {
        showCustomConfirmDialog(
                "Log Out",
                "Are you sure you want to log out of your session?",
                "LOG OUT",
                () -> {
                    AuthManager.clearSession(DashboardActivity.this);
                    Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                },
                null,
                null,
                "CANCEL",
                null
        );
    }

    private void refreshBody() {
        if (mainContainer == null || isFinishing()) return;
        final int scrollY = currentBodyView instanceof android.widget.ScrollView
                ? currentBodyView.getScrollY()
                : 0;
        if (currentBodyView != null) {
            mainContainer.removeView(currentBodyView);
        }
        currentBodyView = DashboardContentView.create(this, new DashboardContentView.DashboardListener() {
            @Override
            public void onBrandMenuClick(IMenuProject menu) {
                if (menu == null) return;
                if (!MainActivity.hasOverlayPermission(DashboardActivity.this)) {
                    Toast.makeText(DashboardActivity.this, "Please grant Overlay Permission to display menu", Toast.LENGTH_SHORT).show();
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception ignored) {}
                    return;
                }

                // 100% Pure Native C++ Dear ImGui Pipeline:
                // Step 1: C++ Modal Key Verification Popup (if license not active)
                // Step 2: C++ Kernel Driver Checker Alert (if /dev/BYNb offline)
                // Step 3: High-FPS Draggable Floating Logo & Brand Tactical Mod Menu
                if ("ninlite".equals(menu.getId())) {
                    com.ninmods.main.menu.ninlite.NinLiteOverlayManager.start(DashboardActivity.this);
                } else {
                    com.ninmods.main.overlay.NativeOverlayManager.start(DashboardActivity.this, menu.getId());
                }
            }

            @Override
            public void onGameClick(final String packageName, final String gameName, boolean isInstalled) {
                if (!isInstalled) {
                    Toast.makeText(DashboardActivity.this, gameName + " is not installed on this device.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!DriverManager.isDriverActive()) {
                    showCustomConfirmDialog(
                            "Driver Loader Required",
                            "Ring 0 Kernel Driver is not active. Would you like to open the Driver Loader (" + DriverManager.LOADER_PACKAGE_NAME + ") first?",
                            "OPEN LOADER",
                            () -> DriverManager.launchDriverLoader(DashboardActivity.this),
                            "LAUNCH ANYWAY",
                            () -> launchGame(packageName, gameName),
                            "CANCEL",
                            null
                    );
                    return;
                }

                launchGame(packageName, gameName);
            }
        });
        mainContainer.addView(currentBodyView);
        final View refreshedBody = currentBodyView;
        if (scrollY > 0) {
            refreshedBody.getViewTreeObserver().addOnGlobalLayoutListener(
                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            refreshedBody.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            refreshedBody.scrollTo(0, scrollY);
                        }
                    }
            );
        }
    }

    private void launchGame(final String packageName, final String gameName) {
        showCustomConfirmDialog(
                "Launch " + gameName,
                "Do you want to launch " + gameName + "?",
                "LAUNCH",
                () -> {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    } else {
                        Toast.makeText(DashboardActivity.this, "Unable to launch " + gameName, Toast.LENGTH_SHORT).show();
                    }
                },
                null,
                null,
                "CANCEL",
                null
        );
    }

    private void showKeyVerificationDialog(final IMenuProject menu) {
        if (menu == null || isFinishing()) return;
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
        cardBg.setColor(AppColors.DIALOG_BACKGROUND);
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke((int) dp(1.5f), menu.getThemeColor());
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

        // Key Input Stage Container
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

        // Input Container with border & rounded background
        final LinearLayout inputContainer = new LinearLayout(this);
        inputContainer.setOrientation(LinearLayout.HORIZONTAL);
        inputContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        inputContainer.setLayoutParams(icLp);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(AppColors.MAGENTA_CHIP);
        inputBg.setCornerRadius(dp(8));
        inputBg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        inputContainer.setBackground(inputBg);

        // Key Input Field with password asterisks mask
        final EditText etKey = new EditText(this);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        etKey.setLayoutParams(etLp);
        etKey.setBackgroundColor(Color.TRANSPARENT);
        etKey.setPadding(dp(14), 0, dp(8), 0);
        etKey.setHint("Enter license key");
        etKey.setHintTextColor(AppColors.MAGENTA_TEXT_DIM);
        etKey.setTextColor(AppColors.WHITE);
        etKey.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        etKey.setSingleLine(true);
        etKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etKey.setTransformationMethod(PasswordTransformationMethod.getInstance());

        // Eye toggle button
        final ImageView btnToggleEye = new ImageView(this);
        LinearLayout.LayoutParams eyeLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        eyeLp.rightMargin = dp(4);
        btnToggleEye.setLayoutParams(eyeLp);
        btnToggleEye.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btnToggleEye.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnToggleEye.setImageDrawable(createEyeIcon(false));

        final boolean[] isPasswordVisible = {false};
        btnToggleEye.setOnClickListener(v -> {
            isPasswordVisible[0] = !isPasswordVisible[0];
            if (isPasswordVisible[0]) {
                etKey.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                btnToggleEye.setImageDrawable(createEyeIcon(true));
            } else {
                etKey.setTransformationMethod(PasswordTransformationMethod.getInstance());
                btnToggleEye.setImageDrawable(createEyeIcon(false));
            }
            etKey.setSelection(etKey.getText() != null ? etKey.getText().length() : 0);
        });

        inputContainer.addView(etKey);
        inputContainer.addView(btnToggleEye);
        stageKeyInput.addView(inputContainer);

        // Remember Key CheckBox
        final CheckBox cbRemember = new CheckBox(this);
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbLp.topMargin = dp(6);
        cbRemember.setLayoutParams(cbLp);
        cbRemember.setText(" Remember Key on this device");
        cbRemember.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        cbRemember.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cbRemember.setButtonTintList(ColorStateList.valueOf(AppColors.MAGENTA_PRIMARY));
        }

        // Restore saved key & remember state scoped strictly to the current logged-in user session
        String savedKey = AuthManager.getRememberedKey(this);
        boolean rememberKey = AuthManager.isKeyRemembered(this);
        if (savedKey.isEmpty()) {
            savedKey = AuthManager.getActiveLicenseKey(this);
        }
        if (savedKey != null && !savedKey.trim().isEmpty()) {
            etKey.setText(savedKey.trim());
            cbRemember.setChecked(true);
        } else {
            cbRemember.setChecked(rememberKey);
        }
        stageKeyInput.addView(cbRemember);

        // Circular Indeterminate Loading Component
        final ProgressBar progressBar = new ProgressBar(this);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        pbLp.gravity = Gravity.CENTER_HORIZONTAL;
        pbLp.topMargin = dp(12);
        pbLp.bottomMargin = dp(4);
        progressBar.setLayoutParams(pbLp);
        progressBar.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBar.setIndeterminateTintList(ColorStateList.valueOf(menu.getThemeColor()));
        }
        progressBar.setVisibility(View.GONE);
        stageKeyInput.addView(progressBar);

        // Verify Button
        final Button btnVerify = new Button(this);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        btnLp.topMargin = dp(12);
        btnVerify.setLayoutParams(btnLp);
        btnVerify.setText("ACTIVATE KEY");
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

            tvError.setVisibility(View.GONE);
            btnVerify.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
            etKey.setEnabled(false);
            cbRemember.setEnabled(false);
            btnToggleEye.setEnabled(false);

            AuthApiController.verifyLicenseKey(DashboardActivity.this, inputKey, menu.getId(), new AuthApiController.ApiCallback() {
                @Override
                public void onSuccess(String message) {
                    AuthManager.setRememberedKey(DashboardActivity.this, inputKey, true);

                    // Maintain continuous screen blur without flickers into the next dialog
                    dialog.setOnDismissListener(null);
                    dialog.dismiss();
                    DashboardContentView.updateLicenseCard(currentBodyView, DashboardActivity.this);
                    refreshBody();
                    Toast.makeText(DashboardActivity.this, "Key Verified! Connecting Ring 0 Driver...", Toast.LENGTH_SHORT).show();
                    launchMenuWithOverlayCheck(menu, "Ring 0 Driver");
                }

                @Override
                public void onError(String errorMessage) {
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setVisibility(View.VISIBLE);
                    etKey.setEnabled(true);
                    cbRemember.setEnabled(true);
                    btnToggleEye.setEnabled(true);
                    tvError.setText(errorMessage);
                    tvError.setVisibility(View.VISIBLE);
                }
            });
        });

        dialogRoot.addView(card);
        dialog.setContentView(dialogRoot);

        if (dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);

            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.50f);
        }

        dialog.show();
    }

    private void showCustomConfirmDialog(String titleText, String messageText,
                                         String positiveText, final Runnable onPositive,
                                         String neutralText, final Runnable onNeutral,
                                         String negativeText, final Runnable onNegative) {
        if (isFinishing()) return;
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout dialogRoot = new FrameLayout(this);
        dialogRoot.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        dialogRoot.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout card = new LinearLayout(this);
        card.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(22), dp(20), dp(18));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(AppColors.DIALOG_BACKGROUND);
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke((int) dp(1.5f), Color.parseColor("#4A1D4F"));
        card.setBackground(cardBg);

        // Header Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(titleText);
        tvTitle.setTextColor(AppColors.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.bottomMargin = dp(10);
        tvTitle.setLayoutParams(tLp);
        card.addView(tvTitle);

        // Message
        TextView tvMsg = new TextView(this);
        tvMsg.setText(messageText);
        tvMsg.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        tvMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvMsg.setGravity(Gravity.CENTER);
        tvMsg.setLineSpacing(dp(2), 1.15f);
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mLp.bottomMargin = dp(18);
        tvMsg.setLayoutParams(mLp);
        card.addView(tvMsg);

        // Buttons Container
        LinearLayout btnContainer = new LinearLayout(this);
        btnContainer.setOrientation(LinearLayout.VERTICAL);
        btnContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (positiveText != null && !positiveText.isEmpty()) {
            Button btnPos = new Button(this);
            LinearLayout.LayoutParams posLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
            btnPos.setLayoutParams(posLp);
            btnPos.setText(positiveText);
            btnPos.setTextColor(AppColors.WHITE);
            btnPos.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnPos.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable posBg = new GradientDrawable();
            posBg.setColor(AppColors.MAGENTA_PRIMARY);
            posBg.setCornerRadius(dp(8));
            btnPos.setBackground(posBg);
            btnPos.setOnClickListener(v -> {
                dialog.dismiss();
                if (onPositive != null) onPositive.run();
            });
            btnContainer.addView(btnPos);
        }

        if (neutralText != null && !neutralText.isEmpty()) {
            Button btnNeu = new Button(this);
            LinearLayout.LayoutParams neuLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            neuLp.topMargin = dp(8);
            btnNeu.setLayoutParams(neuLp);
            btnNeu.setText(neutralText);
            btnNeu.setTextColor(AppColors.WHITE);
            btnNeu.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnNeu.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable neuBg = new GradientDrawable();
            neuBg.setColor(AppColors.MAGENTA_CHIP);
            neuBg.setCornerRadius(dp(8));
            neuBg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
            btnNeu.setBackground(neuBg);
            btnNeu.setOnClickListener(v -> {
                dialog.dismiss();
                if (onNeutral != null) onNeutral.run();
            });
            btnContainer.addView(btnNeu);
        }

        if (negativeText != null && !negativeText.isEmpty()) {
            Button btnNeg = new Button(this);
            LinearLayout.LayoutParams negLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
            negLp.topMargin = dp(6);
            btnNeg.setLayoutParams(negLp);
            btnNeg.setText(negativeText);
            btnNeg.setTextColor(AppColors.MAGENTA_TEXT_DIM);
            btnNeg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btnNeg.setBackgroundColor(Color.TRANSPARENT);
            btnNeg.setOnClickListener(v -> {
                dialog.dismiss();
                if (onNegative != null) onNegative.run();
            });
            btnContainer.addView(btnNeg);
        }

        card.addView(btnContainer);
        dialogRoot.addView(card);
        dialog.setContentView(dialogRoot);

        if (dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.50f);
        }

        dialog.show();
    }

    private void launchMenuWithOverlayCheck(IMenuProject menu, String engineName) {
        if (menu == null) return;

        // Check active license
        if (!AuthManager.hasActiveLicense(this)) {
            showKeyVerificationDialog(menu);
            return;
        }

        // Synchronize verified native session
        String sessionToken = AuthManager.getSessionToken(this);
        String hwid = AuthApiController.getHwid(this);
        long remSec = AuthManager.getRemainingSeconds(this);
        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            Toast.makeText(this, "Session expired. Please verify your license key again.", Toast.LENGTH_LONG).show();
            return;
        }
        SecurityVault.setNativeSession(sessionToken, hwid, remSec);

        if (!MainActivity.hasOverlayPermission(this)) {
            Toast.makeText(this, "Please grant Overlay Permission to display menu", Toast.LENGTH_SHORT).show();
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
            return;
        }

        if (!DriverManager.isDriverActive()) {
            showCustomConfirmDialog(
                    "Kernel Driver Required",
                    "Ring 0 Kernel Driver is currently offline. You can open Driver Loader (" + DriverManager.LOADER_PACKAGE_NAME + ") to activate it, or launch the Floating Menu directly.",
                    "OPEN LOADER",
                    () -> DriverManager.launchDriverLoader(DashboardActivity.this),
                    "LAUNCH MENU",
                    () -> MenuRouter.launchMenu(DashboardActivity.this, menu.getId()),
                    "CANCEL",
                    null
            );
            return;
        }

        Toast.makeText(this, menu.getDisplayName(this) + " (Ring 0 Driver) Launched!", Toast.LENGTH_SHORT).show();
        MenuRouter.launchMenu(this, menu.getId());
    }

    private Drawable createEyeIcon(final boolean isOpen) {
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                float cx = bounds.exactCenterX();
                float cy = bounds.exactCenterY();
                float r = bounds.width() * 0.35f;

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.8f));
                paint.setColor(isOpen ? AppColors.MAGENTA_PRIMARY : AppColors.MAGENTA_TEXT_DIM);

                // Eye contour
                Path path = new Path();
                path.moveTo(cx - r, cy);
                path.quadTo(cx, cy - r * 0.85f, cx + r, cy);
                path.quadTo(cx, cy + r * 0.85f, cx - r, cy);
                canvas.drawPath(path, paint);

                // Pupil
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, r * 0.35f, paint);

                // Diagonal slash if closed/masked
                if (!isOpen) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(1.8f));
                    canvas.drawLine(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f, paint);
                }
            }

            @Override
            public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(ColorFilter colorFilter) {
                paint.setColorFilter(colorFilter);
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    public static native void setModeNative(int mode);
}
