package nin.app.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.drawerlayout.widget.DrawerLayout;

import nin.app.auth.AuthConstants;
import nin.app.auth.AuthManager;
import nin.app.menu.IMenuProject;
import nin.app.menu.MenuRouter;
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
            public void onBrandMenuClick(IMenuProject menu) {
                showKeyVerificationDialog(menu);
            }

            @Override
            public void onModeSelected(int mode) {
                currentMode = mode;
                Prefs.with(DashboardActivity.this).writeInt(PREF_KEY_MODE, mode);
                setModeNative(mode);
                refreshBody();
            }

            @Override
            public void onGameClick(final String packageName, final String gameName, boolean isInstalled) {
                if (!isInstalled) {
                    Toast.makeText(DashboardActivity.this, gameName + " is not installed on this device.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Official Android native AlertDialog Component
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
        });
        mainContainer.addView(currentBodyView);
    }

    private void showKeyVerificationDialog(final IMenuProject menu) {
        if (menu == null) return;
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout dialogRoot = new FrameLayout(this);
        dialogRoot.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout card = new LinearLayout(this);
        card.setLayoutParams(new FrameLayout.LayoutParams(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(24), dp(22), dp(20));

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

        // 2. Title
        TextView title = new TextView(this);
        title.setText(menu.getDisplayName(this) + " ACCESS KEY");
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        // 3. Subtitle
        TextView subtitle = new TextView(this);
        subtitle.setText("Enter tactical key to launch " + menu.getDisplayName(this));
        subtitle.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        card.addView(subtitle);

        // 4. Key Input Field
        final EditText etKey = new EditText(this);
        etKey.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        etKey.setHint("Enter Key (e.g. " + AuthConstants.DEV_FREE_LICENSE_KEY + ")");
        etKey.setHintTextColor(AppColors.MAGENTA_TEXT_DIM);
        etKey.setTextColor(AppColors.WHITE);
        etKey.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        etKey.setSingleLine(true);
        etKey.setPadding(dp(12), 0, dp(12), 0);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(AppColors.MAGENTA_CHIP);
        inputBg.setCornerRadius(dp(8));
        inputBg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        etKey.setBackground(inputBg);
        card.addView(etKey);

        // 5. Error text (initially hidden)
        final TextView tvError = new TextView(this);
        LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errLp.topMargin = dp(6);
        tvError.setLayoutParams(errLp);
        tvError.setTextColor(Color.parseColor("#FF5252"));
        tvError.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvError.setVisibility(View.GONE);
        card.addView(tvError);

        // 6. Dev Free Key Hint
        TextView tvDevHint = new TextView(this);
        tvDevHint.setText("Free Test Key: " + AuthConstants.DEV_FREE_LICENSE_KEY);
        tvDevHint.setTextColor(AppColors.MAGENTA_LIGHT);
        tvDevHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tvDevHint.setGravity(Gravity.CENTER);
        tvDevHint.setPadding(0, dp(8), 0, dp(12));
        card.addView(tvDevHint);

        // 7. Official Android Native Circular Loading Component
        final ProgressBar progressBar = new ProgressBar(this);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        pbLp.gravity = Gravity.CENTER_HORIZONTAL;
        pbLp.topMargin = dp(8);
        pbLp.bottomMargin = dp(4);
        progressBar.setLayoutParams(pbLp);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        card.addView(progressBar);

        // 8. Verify & Launch Button
        final Button btnVerify = new Button(this);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        btnVerify.setLayoutParams(btnLp);
        btnVerify.setText("VERIFY & LAUNCH");
        btnVerify.setTextColor(AppColors.WHITE);
        btnVerify.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnVerify.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(menu.getThemeColor());
        btnBg.setCornerRadius(dp(8));
        btnVerify.setBackground(btnBg);

        btnVerify.setOnClickListener(v -> {
            String inputKey = etKey.getText() != null ? etKey.getText().toString().trim() : "";
            if (inputKey.isEmpty()) {
                tvError.setText("Please enter your license key.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            // Start Official Android Native Loading Component
            tvError.setVisibility(View.GONE);
            btnVerify.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);

            // Simulate authentic network handshake (1.2s)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!dialog.isShowing()) return;

                if (AuthManager.verifyLicenseKey(inputKey)) {
                    dialog.dismiss();

                    // Check overlay permission
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(DashboardActivity.this)) {
                        Toast.makeText(DashboardActivity.this, "Please grant Overlay Permission to display menu", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                        return;
                    }

                    Toast.makeText(DashboardActivity.this, menu.getDisplayName(DashboardActivity.this) + " Launched!", Toast.LENGTH_SHORT).show();
                    MenuRouter.launchMenu(DashboardActivity.this, menu.getId());
                } else {
                    progressBar.setVisibility(View.GONE);
                    btnVerify.setVisibility(View.VISIBLE);
                    tvError.setText("Invalid Key! Please enter a valid test key (" + AuthConstants.DEV_FREE_LICENSE_KEY + ").");
                    tvError.setVisibility(View.VISIBLE);
                }
            }, 1200);
        });
        card.addView(btnVerify);

        // 9. Cancel / Close Button
        Button btnCancel = new Button(this);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        cancelLp.topMargin = dp(8);
        btnCancel.setLayoutParams(cancelLp);
        btnCancel.setText("CANCEL");
        btnCancel.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        btnCancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnCancel.setBackgroundColor(Color.TRANSPARENT);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        card.addView(btnCancel);

        dialogRoot.addView(card);
        dialog.setContentView(dialogRoot);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
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
