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

        // STAGE 2: Engine Selection Stage Container (Appears with smooth native animation on key success)
        final LinearLayout stageEngineSelect = new LinearLayout(this);
        stageEngineSelect.setOrientation(LinearLayout.VERTICAL);
        stageEngineSelect.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        stageEngineSelect.setVisibility(View.GONE);

        // Success Indicator
        TextView tvSuccess = new TextView(this);
        tvSuccess.setText("Key Verified Successfully!");
        tvSuccess.setTextColor(Color.parseColor("#00E676"));
        tvSuccess.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvSuccess.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvSuccess.setGravity(Gravity.CENTER);
        tvSuccess.setPadding(0, 0, 0, dp(6));
        stageEngineSelect.addView(tvSuccess);

        // Prompt Subtitle
        TextView tvPrompt = new TextView(this);
        tvPrompt.setText("SELECT OPERATIONAL ENGINE");
        tvPrompt.setTextColor(AppColors.WHITE);
        tvPrompt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvPrompt.setTypeface(Typeface.DEFAULT_BOLD);
        tvPrompt.setGravity(Gravity.CENTER);
        tvPrompt.setPadding(0, 0, 0, dp(12));
        stageEngineSelect.addView(tvPrompt);

        // 1. Safe Engine Option Card
        View cardSafe = createEngineOptionItem(this, "Safe Engine", "Anti-Detection & Memory Protection", "#00E676", v -> {
            currentMode = MODE_SAFE;
            Prefs.with(DashboardActivity.this).writeInt(PREF_KEY_MODE, MODE_SAFE);
            setModeNative(MODE_SAFE);
            refreshBody();
            dialog.dismiss();
            launchMenuWithOverlayCheck(menu, "Safe Engine");
        });
        stageEngineSelect.addView(cardSafe);

        // 2. External Engine Option Card
        String themeHex = String.format("#%06X", (0xFFFFFF & menu.getThemeColor()));
        View cardExternal = createEngineOptionItem(this, "External Engine", "Tactical Combat & External Hook", themeHex, v -> {
            currentMode = MODE_EXTREME;
            Prefs.with(DashboardActivity.this).writeInt(PREF_KEY_MODE, MODE_EXTREME);
            setModeNative(MODE_EXTREME);
            refreshBody();
            dialog.dismiss();
            launchMenuWithOverlayCheck(menu, "External Engine");
        });
        stageEngineSelect.addView(cardExternal);

        // Cancel in Stage 2
        Button btnCancelStage2 = new Button(this);
        btnCancelStage2.setLayoutParams(cancelLp);
        btnCancelStage2.setText("CANCEL");
        btnCancelStage2.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        btnCancelStage2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnCancelStage2.setBackgroundColor(Color.TRANSPARENT);
        btnCancelStage2.setOnClickListener(v -> dialog.dismiss());
        stageEngineSelect.addView(btnCancelStage2);

        card.addView(stageEngineSelect);

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

                    // Native Android Default Component Transition Animation
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        android.transition.TransitionManager.beginDelayedTransition(card);
                    }
                    stageKeyInput.setVisibility(View.GONE);
                    stageEngineSelect.setVisibility(View.VISIBLE);
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
        if (dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);

            // Official Android Native Background Blur and Dim Component
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.70f);

            if (Build.VERSION.SDK_INT >= 31) { // Android 12+ (Build.VERSION_CODES.S)
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                window.getAttributes().setBlurBehindRadius(dp(20));
            }
        }
        dialog.show();
    }

    private View createEngineOptionItem(Context context, String title, String subtitle, String accentHex, View.OnClickListener onClickListener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        lp.bottomMargin = dp(10);
        row.setLayoutParams(lp);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));

        int accentColor = Color.parseColor(accentHex);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppColors.MAGENTA_CHIP);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), accentColor);
        row.setBackground(bg);

        // Indicator Dot
        View dot = new View(context);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotLp.rightMargin = dp(12);
        dot.setLayoutParams(dotLp);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(accentColor);
        dot.setBackground(dotBg);
        row.addView(dot);

        // Text Column
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textCol.setLayoutParams(textLp);

        TextView tvTitle = new TextView(context);
        tvTitle.setText(title);
        tvTitle.setTextColor(AppColors.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(context);
        tvSub.setText(subtitle);
        tvSub.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        textCol.addView(tvSub);

        row.addView(textCol);

        // Action Indicator
        TextView tvSelect = new TextView(context);
        tvSelect.setText("START");
        tvSelect.setTextColor(accentColor);
        tvSelect.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvSelect.setTypeface(Typeface.DEFAULT_BOLD);
        tvSelect.setPadding(dp(8), dp(4), dp(4), dp(4));
        row.addView(tvSelect);

        row.setOnClickListener(onClickListener);
        return row;
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

        Toast.makeText(this, menu.getDisplayName(this) + " (" + engineName + ") Launched!", Toast.LENGTH_SHORT).show();
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
