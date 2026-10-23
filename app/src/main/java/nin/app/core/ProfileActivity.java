package nin.app.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import nin.app.R;
import nin.app.auth.AuthApiController;
import nin.app.auth.AuthManager;
import nin.app.auth.LoginActivity;

public class ProfileActivity extends BaseActivity {

    public static final String PREF_ACTIVE_USERNAME = "active_username";
    public static final String PREF_ACTIVE_EMAIL = "active_email";
    public static final String PREF_ACTIVE_PLAN = "active_plan";
    public static final String PREF_ACTIVE_PASSWORD = "active_password";

    private EditText etCurrentPassword;
    private EditText etNewPassword;
    private EditText etConfirmNewPassword;
    private TextView tvError;
    private Button updatePassBtn;
    private ProgressBar progressBar;

    private String currentUsername;
    private String currentEmail;
    private String currentPlan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadUserData();
        buildView();
    }

    private void loadUserData() {
        currentUsername = Prefs.with(this).readString(PREF_ACTIVE_USERNAME, "");
        if (currentUsername == null || currentUsername.trim().isEmpty()) {
            currentUsername = "Operator";
            Prefs.with(this).writeString(PREF_ACTIVE_USERNAME, currentUsername);
        }

        currentEmail = Prefs.with(this).readString(PREF_ACTIVE_EMAIL, "");
        if (currentEmail == null || currentEmail.trim().isEmpty()) {
            currentEmail = currentUsername;
        }

        String expiresAt = Prefs.with(this).readString("license_expires_at", "");
        int remainingSec = Prefs.with(this).readInt("license_remaining_sec", 0);
        if (remainingSec > 0 || !expiresAt.isEmpty()) {
            currentPlan = "Active (" + (remainingSec > 0 ? (remainingSec / 3600) + "h remaining" : expiresAt) + ")";
        } else {
            currentPlan = "No Active License";
        }
    }

    private void buildView() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout layout = new LinearLayout(this);
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        layout.setOrientation(LinearLayout.VERTICAL);

        // 1. Header
        LinearLayout header = new LinearLayout(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), 0, dp(16), 0);
        header.setBackgroundColor(AppColors.MAGENTA_SURFACE);

        Button backBtn = new Button(this);
        backBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        backBtn.setText("<");
        backBtn.setTextColor(AppColors.WHITE);
        backBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        backBtn.setTypeface(Typeface.DEFAULT_BOLD);
        backBtn.setBackgroundColor(Color.TRANSPARENT);
        backBtn.setOnClickListener(v -> finish());
        header.addView(backBtn);

        TextView title = new TextView(this);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleLp.leftMargin = dp(8);
        title.setLayoutParams(titleLp);
        title.setText("ACCOUNT PROFILE");
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        header.addView(title);

        layout.addView(header);

        // 2. Scrollable Body
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(this);
        content.setLayoutParams(new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(24));

        // 3. User Info Card
        content.addView(createProfileInfoCard());

        // 4. Change Password Card
        content.addView(createChangePasswordCard());

        // 5. Log Out Button
        Button logoutBtn = new Button(this);
        LinearLayout.LayoutParams loLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        loLp.topMargin = dp(16);
        logoutBtn.setLayoutParams(loLp);
        logoutBtn.setText("LOG OUT");
        logoutBtn.setTextColor(AppColors.WHITE);
        logoutBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        logoutBtn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable loBg = new GradientDrawable();
        loBg.setColor(Color.parseColor("#441010"));
        loBg.setCornerRadius(dp(8));
        loBg.setStroke(dp(1), Color.parseColor("#882222"));
        logoutBtn.setBackground(loBg);
        logoutBtn.setOnClickListener(v -> confirmLogout());
        content.addView(logoutBtn);

        scroll.addView(content);
        layout.addView(scroll);
        root.addView(layout);

        setContentView(root);
    }

    private View createProfileInfoCard() {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(16);
        card.setLayoutParams(lp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(20), dp(16), dp(20));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppColors.MAGENTA_CARD);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        card.setBackground(bg);

        // Avatar Row
        LinearLayout avatarRow = new LinearLayout(this);
        avatarRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        avatarRow.setOrientation(LinearLayout.HORIZONTAL);
        avatarRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout avatarBox = new FrameLayout(this);
        avatarBox.setLayoutParams(new LinearLayout.LayoutParams(dp(60), dp(60)));
        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(AppColors.MAGENTA_CHIP);
        avatarBox.setBackground(avBg);

        ImageView avIcon = new ImageView(this);
        FrameLayout.LayoutParams avIconLp = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER);
        avIcon.setLayoutParams(avIconLp);
        avIcon.setImageResource(R.drawable.ic_user_profile);
        avatarBox.addView(avIcon);
        avatarRow.addView(avatarBox);

        LinearLayout nameCol = new LinearLayout(this);
        LinearLayout.LayoutParams nameColLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameColLp.leftMargin = dp(14);
        nameCol.setLayoutParams(nameColLp);
        nameCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvName = new TextView(this);
        tvName.setText(currentUsername);
        tvName.setTextColor(AppColors.WHITE);
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tvName.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        nameCol.addView(tvName);

        TextView tvMail = new TextView(this);
        tvMail.setText(currentEmail);
        tvMail.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        tvMail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvMail.setPadding(0, dp(2), 0, 0);
        nameCol.addView(tvMail);

        avatarRow.addView(nameCol);
        card.addView(avatarRow);

        // Divider
        View div = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divLp.setMargins(0, dp(16), 0, dp(14));
        div.setLayoutParams(divLp);
        div.setBackgroundColor(AppColors.MAGENTA_CARD_BORDER);
        card.addView(div);

        // Details Key-Value Rows
        card.addView(createDetailRow("Subscription Plan", currentPlan, AppColors.ONLINE_GREEN));
        card.addView(createDetailRow("Account Status", "Protected & Active", AppColors.ONLINE_GREEN));
        card.addView(createDetailRow("Hardware ID", getShortHwid(), AppColors.MAGENTA_TEXT_DIM));

        return card;
    }

    private View createChangePasswordCard() {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(18), dp(16), dp(18));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppColors.MAGENTA_CARD);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        card.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("CHANGE PASSWORD");
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setPadding(0, 0, 0, dp(8));
        card.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Update your tactical access credentials.");
        subtitle.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        subtitle.setPadding(0, 0, 0, dp(12));
        card.addView(subtitle);

        // Error message positioned strictly above input fields
        tvError = new TextView(this);
        LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errLp.bottomMargin = dp(10);
        tvError.setLayoutParams(errLp);
        tvError.setTextColor(Color.parseColor("#FF5252"));
        tvError.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvError.setVisibility(View.GONE);
        card.addView(tvError);

        etCurrentPassword = createInputField("Current Password");
        card.addView(etCurrentPassword);

        etNewPassword = createInputField("New Password");
        LinearLayout.LayoutParams npLp = (LinearLayout.LayoutParams) etNewPassword.getLayoutParams();
        npLp.topMargin = dp(10);
        etNewPassword.setLayoutParams(npLp);
        card.addView(etNewPassword);

        etConfirmNewPassword = createInputField("Confirm New Password");
        LinearLayout.LayoutParams cnpLp = (LinearLayout.LayoutParams) etConfirmNewPassword.getLayoutParams();
        cnpLp.topMargin = dp(10);
        etConfirmNewPassword.setLayoutParams(cnpLp);
        card.addView(etConfirmNewPassword);

        updatePassBtn = new Button(this);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        btnLp.topMargin = dp(14);
        updatePassBtn.setLayoutParams(btnLp);
        updatePassBtn.setText("UPDATE PASSWORD");
        updatePassBtn.setTextColor(AppColors.WHITE);
        updatePassBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        updatePassBtn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END}
        );
        btnBg.setCornerRadius(dp(8));
        updatePassBtn.setBackground(btnBg);
        updatePassBtn.setOnClickListener(v -> performChangePassword());
        card.addView(updatePassBtn);

        progressBar = new ProgressBar(this);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        pbLp.gravity = Gravity.CENTER_HORIZONTAL;
        pbLp.topMargin = dp(12);
        progressBar.setLayoutParams(pbLp);
        progressBar.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBar.setIndeterminateTintList(ColorStateList.valueOf(AppColors.MAGENTA_PRIMARY));
        }
        progressBar.setVisibility(View.GONE);
        card.addView(progressBar);

        return card;
    }

    private EditText createInputField(String hint) {
        EditText et = new EditText(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        et.setLayoutParams(lp);
        et.setHint(hint);
        et.setHintTextColor(AppColors.MAGENTA_TEXT_DIM);
        et.setTextColor(AppColors.WHITE);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setSingleLine(true);
        et.setPadding(dp(14), 0, dp(14), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppColors.MAGENTA_CHIP);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        et.setBackground(bg);

        return et;
    }

    private View createDetailRow(String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvLabel = new TextView(this);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        tvLabel.setText(label);
        tvLabel.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        row.addView(tvLabel);

        TextView tvVal = new TextView(this);
        tvVal.setText(value);
        tvVal.setTextColor(valueColor);
        tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvVal.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        row.addView(tvVal);

        return row;
    }

    private void performChangePassword() {
        final String currentPass = etCurrentPassword.getText() != null ? etCurrentPassword.getText().toString().trim() : "";
        final String newPass = etNewPassword.getText() != null ? etNewPassword.getText().toString().trim() : "";
        final String confirmPass = etConfirmNewPassword.getText() != null ? etConfirmNewPassword.getText().toString().trim() : "";

        if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            showError("Please fill in all password fields.");
            return;
        }

        if (newPass.length() < 4) {
            showError("New password must be at least 4 characters.");
            return;
        }

        if (!newPass.equals(confirmPass)) {
            showError("New passwords do not match.");
            return;
        }

        String savedPass = Prefs.with(this).readString(PREF_ACTIVE_PASSWORD, "");
        if (!savedPass.isEmpty() && !savedPass.equals(currentPass)) {
            showError("Current password is incorrect.");
            return;
        }

        tvError.setVisibility(View.GONE);
        updatePassBtn.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        etCurrentPassword.setEnabled(false);
        etNewPassword.setEnabled(false);
        etConfirmNewPassword.setEnabled(false);

        // Update password through API controller and persist locally
        AuthApiController.resetPassword(this, currentUsername, newPass, new AuthApiController.ApiCallback() {
            @Override
            public void onSuccess(String message) {
                if (isFinishing()) return;
                progressBar.setVisibility(View.GONE);
                updatePassBtn.setVisibility(View.VISIBLE);
                etCurrentPassword.setEnabled(true);
                etNewPassword.setEnabled(true);
                etConfirmNewPassword.setEnabled(true);

                etCurrentPassword.setText("");
                etNewPassword.setText("");
                etConfirmNewPassword.setText("");

                Prefs.with(ProfileActivity.this).writeString(PREF_ACTIVE_PASSWORD, newPass);

                AlertDialog.Builder b = new AlertDialog.Builder(ProfileActivity.this);
                b.setTitle("Password Updated");
                b.setMessage("Your password has been successfully updated.");
                b.setPositiveButton("OK", null);
                b.show();
            }

            @Override
            public void onError(String errorMessage) {
                if (isFinishing()) return;
                progressBar.setVisibility(View.GONE);
                updatePassBtn.setVisibility(View.VISIBLE);
                etCurrentPassword.setEnabled(true);
                etNewPassword.setEnabled(true);
                etConfirmNewPassword.setEnabled(true);
                showError(errorMessage);
            }
        });
    }

    private void confirmLogout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Log Out");
        builder.setMessage("Are you sure you want to log out of your session?");
        builder.setPositiveButton("Log Out", (dialog, which) -> {
            AuthManager.setLoggedIn(ProfileActivity.this, false);
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private String getShortHwid() {
        try {
            String hwid = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            if (hwid != null && hwid.length() > 8) {
                return hwid.substring(0, 4) + "..." + hwid.substring(hwid.length() - 4);
            }
            return hwid != null ? hwid : "PROTECTED";
        } catch (Exception e) {
            return "DEV-ARM64";
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
