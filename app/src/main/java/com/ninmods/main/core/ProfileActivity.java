package com.ninmods.main.core;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.ninmods.main.R;
import com.ninmods.main.auth.AuthApiController;
import com.ninmods.main.auth.AuthManager;
import com.ninmods.main.auth.LoginActivity;
import com.ninmods.main.ui.PasswordEyeHelper;

public class ProfileActivity extends BaseActivity {

    @Override
    protected boolean enableSecureScreen() {
        return false;
    }


    public static final String PREF_ACTIVE_USERNAME = "active_username";
    public static final String PREF_ACTIVE_EMAIL = "active_email";
    public static final String PREF_ACTIVE_PHONE = "active_phone";
    /** Stores a salted SHA-256 hash of the password, never the raw password. */
    public static final String PREF_ACTIVE_PASSWORD_HASH = "active_password_hash";
    public static final String PREF_ACTIVE_ROLE = "active_role";
    public static final String PREF_AUTH_BALANCE = "auth_balance";
    public static final String PREF_AUTH_CURRENCY = "auth_currency";

    private EditText etCurrentPassword;
    private EditText etNewPassword;
    private EditText etConfirmNewPassword;
    private TextView tvError;
    private Button updatePassBtn;
    private ProgressBar progressBar;

    private TextView tvLiveCountdown;
    private TextView tvBoxDaysVal;
    private TextView tvBoxHoursVal;
    private TextView tvBoxMinsVal;
    private TextView tvBoxSecsVal;
    private TextView tvClockStatusText;

    private TextView tvPlanValue;
    private TextView tvKeyValue;
    private TextView tvExpiryDateValue;

    private String currentUsername = "";
    private String currentEmail = "";
    private String currentPhone = "";
    private String currentRole = "";
    private float currentBalance = 0.0f;
    private String currentCurrency = "BDT";

    private TextView tvHeroName;
    private TextView tvHeroMail;
    private TextView tvHeroPhone;
    private TextView tvHeroRoleBadge;
    private TextView tvUsernameRowVal;
    private TextView tvEmailRowVal;
    private TextView tvPhoneRowVal;
    private TextView tvBalanceRowVal;
    private TextView tvRoleRowVal;

    private final Handler tickerHandler = new Handler(Looper.getMainLooper());
    private final Runnable tickerRunnable = new Runnable() {
        @Override
        public void run() {
            updateLiveCountdown();
            tickerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadUserData();
        buildView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserData();
        updateUIValues();
        tickerHandler.removeCallbacks(tickerRunnable);
        tickerHandler.post(tickerRunnable);

        // Fetch fresh genuine profile data from backend server
        AuthApiController.fetchProfile(this, new AuthApiController.ApiCallback() {
            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> {
                    loadUserData();
                    updateUIValues();
                });
            }

            @Override
            public void onError(String errorMessage) {
                // Keep local values
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        tickerHandler.removeCallbacks(tickerRunnable);
    }

    private void loadUserData() {
        currentUsername = AuthManager.getActiveUsername(this);
        currentEmail = AuthManager.getActiveEmail(this);
        currentPhone = AuthManager.getActivePhone(this);
        currentRole = AuthManager.getActiveRole(this);
        currentBalance = AuthManager.getActiveBalance(this);
        currentCurrency = AuthManager.getActiveCurrency(this);
    }

    private void updateUIValues() {
        if (tvHeroName != null) {
            tvHeroName.setText(currentUsername.isEmpty() ? "Tactical Operator" : currentUsername);
        }
        if (tvHeroMail != null) {
            tvHeroMail.setText(currentEmail.isEmpty() ? "No Email Linked" : currentEmail);
        }
        if (tvHeroPhone != null) {
            tvHeroPhone.setText(currentPhone.isEmpty() ? "Phone: Not Provided" : "Phone: " + currentPhone);
        }
        if (tvUsernameRowVal != null) {
            tvUsernameRowVal.setText(currentUsername.isEmpty() ? "Not Set" : currentUsername);
        }
        if (tvEmailRowVal != null) {
            tvEmailRowVal.setText(currentEmail.isEmpty() ? "Not Linked" : currentEmail);
        }
        if (tvPhoneRowVal != null) {
            tvPhoneRowVal.setText(currentPhone.isEmpty() ? "Not Provided" : currentPhone);
        }
        if (tvBalanceRowVal != null) {
            tvBalanceRowVal.setText(String.format(java.util.Locale.US, "%.2f %s", currentBalance, currentCurrency));
        }
        if (tvRoleRowVal != null) {
            tvRoleRowVal.setText(currentRole.isEmpty() ? "---" : currentRole.toUpperCase());
        }
        if (tvHeroRoleBadge != null) {
            tvHeroRoleBadge.setText(currentRole.isEmpty() ? "MEMBER" : currentRole.toUpperCase());
        }
        boolean hasPlan = AuthManager.hasActiveLicense(this);
        if (tvPlanValue != null) {
            tvPlanValue.setText(hasPlan ? AuthManager.getActivePlanName(this) : "No Active Plan");
            tvPlanValue.setTextColor(hasPlan ? 0xFF00E676 : 0xFFFF5252);
        }
        if (tvKeyValue != null) {
            String key = AuthManager.getActiveLicenseKey(this);
            tvKeyValue.setText(key.isEmpty() ? "No License Key Bound" : key);
        }
        if (tvExpiryDateValue != null) {
            tvExpiryDateValue.setText(hasPlan ? AuthManager.getExpiryDate(this) : "Not Subscribed");
        }
        updateLiveCountdown();
    }

    private void updateLiveCountdown() {
        long remaining = AuthManager.getRemainingSeconds(this);
        long[] c = AuthManager.getRemainingTimeComponents(remaining);

        if (tvBoxDaysVal != null) tvBoxDaysVal.setText(String.format(java.util.Locale.US, "%02d", c[0]));
        if (tvBoxHoursVal != null) tvBoxHoursVal.setText(String.format(java.util.Locale.US, "%02d", c[1]));
        if (tvBoxMinsVal != null) tvBoxMinsVal.setText(String.format(java.util.Locale.US, "%02d", c[2]));
        if (tvBoxSecsVal != null) tvBoxSecsVal.setText(String.format(java.util.Locale.US, "%02d", c[3]));

        if (tvLiveCountdown != null) {
            if (remaining > 0) {
                tvLiveCountdown.setText(AuthManager.formatRemainingTime(remaining));
                tvLiveCountdown.setTextColor(0xFF00E676);
            } else {
                tvLiveCountdown.setText("No Active License / Expired");
                tvLiveCountdown.setTextColor(0xFFFF5252);
            }
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

        // 1. Header (Clean, borderless)
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
        title.setText("USER PROFILE & SUBSCRIPTION");
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
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

        // 3. Hero User Profile Banner Card
        content.addView(createHeroProfileCard());

        // 4. Genuine User Credentials Card (Username, Gmail, Phone, Balance, Role)
        content.addView(createSectionTitle("REGISTERED ACCOUNT CREDENTIALS"));
        content.addView(createAccountCredentialsCard());

        // 5. Active Membership & Live Expiration Countdown Card
        content.addView(createSectionTitle("SUBSCRIPTION & EXPIRATION"));
        content.addView(createSubscriptionCountdownCard());

        // 6. Security & System Integrity Card
        content.addView(createSectionTitle("SECURITY & HARDWARE INTEGRITY"));
        content.addView(createSecurityDiagnosticsCard());

        // 7. Change Password Card
        content.addView(createSectionTitle("CHANGE ACCESS PASSWORD"));
        content.addView(createChangePasswordCard());

        scroll.addView(content);
        layout.addView(scroll);
        root.addView(layout);

        setContentView(root);
    }

    // Hero User Profile Banner Card
    private View createHeroProfileCard() {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(18), dp(16), dp(18));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppColors.MAGENTA_CARD);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        card.setBackground(bg);

        // Avatar Row
        LinearLayout avatarRow = new LinearLayout(this);
        avatarRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        avatarRow.setOrientation(LinearLayout.HORIZONTAL);
        avatarRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout avatarBox = new FrameLayout(this);
        avatarBox.setLayoutParams(new LinearLayout.LayoutParams(dp(62), dp(62)));
        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(AppColors.MAGENTA_CHIP);
        avBg.setStroke(dp(2), Color.parseColor("#FFD54F")); // Gold VIP border ring
        avatarBox.setBackground(avBg);

        ImageView avIcon = new ImageView(this);
        FrameLayout.LayoutParams avIconLp = new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER);
        avIcon.setLayoutParams(avIconLp);
        avIcon.setImageResource(R.drawable.ic_user_profile);
        avatarBox.addView(avIcon);
        avatarRow.addView(avatarBox);

        LinearLayout nameCol = new LinearLayout(this);
        LinearLayout.LayoutParams nameColLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        nameColLp.leftMargin = dp(14);
        nameCol.setLayoutParams(nameColLp);
        nameCol.setOrientation(LinearLayout.VERTICAL);

        tvHeroName = new TextView(this);
        tvHeroName.setText(currentUsername.isEmpty() ? "Tactical Operator" : currentUsername);
        tvHeroName.setTextColor(AppColors.WHITE);
        tvHeroName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvHeroName.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        nameCol.addView(tvHeroName);

        tvHeroMail = new TextView(this);
        tvHeroMail.setText(currentEmail.isEmpty() ? "No Email Linked" : currentEmail);
        tvHeroMail.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        tvHeroMail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvHeroMail.setPadding(0, dp(2), 0, 0);
        nameCol.addView(tvHeroMail);

        tvHeroPhone = new TextView(this);
        tvHeroPhone.setText(currentPhone.isEmpty() ? "Phone: Not Provided" : "Phone: " + currentPhone);
        tvHeroPhone.setTextColor(0xFF00E676);
        tvHeroPhone.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvHeroPhone.setPadding(0, dp(2), 0, 0);
        nameCol.addView(tvHeroPhone);

        avatarRow.addView(nameCol);
        card.addView(avatarRow);

        // Badge Pill Row below avatar
        LinearLayout badgeRow = new LinearLayout(this);
        badgeRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        badgeRow.setPadding(0, dp(14), 0, 0);

        tvHeroRoleBadge = createStatusChip(currentRole.isEmpty() ? "MEMBER" : currentRole.toUpperCase(), 0xFFF76BF7, 0x22F76BF7);
        badgeRow.addView(tvHeroRoleBadge);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
        badgeRow.addView(spacer);

        badgeRow.addView(createStatusChip("PROTECTED SESSION", 0xFF00E676, 0x2200E676));

        card.addView(badgeRow);

        return card;
    }

    // Genuine User Credentials Card (Real Username, Real Gmail, Real Phone Number, Balance)
    private View createAccountCredentialsCard() {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppColors.MAGENTA_CARD);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        card.setBackground(bg);

        tvUsernameRowVal = new TextView(this);
        card.addView(createRow("Account Username", tvUsernameRowVal, currentUsername.isEmpty() ? "Not Set" : currentUsername, AppColors.WHITE));

        tvEmailRowVal = new TextView(this);
        card.addView(createRow("Gmail / Email", tvEmailRowVal, currentEmail.isEmpty() ? "Not Linked" : currentEmail, AppColors.WHITE));

        tvPhoneRowVal = new TextView(this);
        card.addView(createRow("Phone / Mobile", tvPhoneRowVal, currentPhone.isEmpty() ? "Not Provided" : currentPhone, 0xFF00E676));

        tvBalanceRowVal = new TextView(this);
        String balanceStr = String.format(java.util.Locale.US, "%.2f %s", currentBalance, currentCurrency);
        card.addView(createRow("Account Balance", tvBalanceRowVal, balanceStr, 0xFF00E676));

        tvRoleRowVal = new TextView(this);
        card.addView(createRow("Account Role", tvRoleRowVal, currentRole.toUpperCase(), AppColors.MAGENTA_PRIMARY));

        return card;
    }

    // Active Membership & Live Digital Clock Countdown Card
    private View createSubscriptionCountdownCard() {
        boolean hasActive = AuthManager.hasActiveLicense(this);

        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppColors.MAGENTA_CARD);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        card.setBackground(bg);

        // Shows digital clock ONLY if active plan is verified (no nested card!)
        if (hasActive) {
            TextView label = new TextView(this);
            label.setText("LIVE DIGITAL CLOCK EXPIRATION");
            label.setTextColor(AppColors.MAGENTA_TEXT_DIM);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(label);

            // Digital Clock Boxes Row (DAYS : HOURS : MINS : SECS) - Flat inside card
            LinearLayout digitRow = new LinearLayout(this);
            LinearLayout.LayoutParams drLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            drLp.topMargin = dp(8);
            drLp.bottomMargin = dp(6);
            digitRow.setLayoutParams(drLp);
            digitRow.setOrientation(LinearLayout.HORIZONTAL);
            digitRow.setGravity(Gravity.CENTER_VERTICAL);
            digitRow.setWeightSum(4.0f);

            tvBoxDaysVal = new TextView(this);
            tvBoxDaysVal.setText("00");
            digitRow.addView(createClockDigitBox(tvBoxDaysVal, "DAYS"));

            digitRow.addView(createClockColon());

            tvBoxHoursVal = new TextView(this);
            tvBoxHoursVal.setText("00");
            digitRow.addView(createClockDigitBox(tvBoxHoursVal, "HOURS"));

            digitRow.addView(createClockColon());

            tvBoxMinsVal = new TextView(this);
            tvBoxMinsVal.setText("00");
            digitRow.addView(createClockDigitBox(tvBoxMinsVal, "MINS"));

            digitRow.addView(createClockColon());

            tvBoxSecsVal = new TextView(this);
            tvBoxSecsVal.setText("00");
            digitRow.addView(createClockDigitBox(tvBoxSecsVal, "SECS"));

            card.addView(digitRow);

            // Divider
            View div = new View(this);
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            divLp.setMargins(0, dp(14), 0, dp(12));
            div.setLayoutParams(divLp);
            div.setBackgroundColor(AppColors.MAGENTA_CARD_BORDER);
            card.addView(div);
        }

        // Detail Rows
        tvPlanValue = new TextView(this);
        card.addView(createRow("Subscription Plan", tvPlanValue, hasActive ? AuthManager.getActivePlanName(this) : "No Active Plan", hasActive ? 0xFF00E676 : 0xFFFF5252));

        tvKeyValue = new TextView(this);
        String activeKey = AuthManager.getActiveLicenseKey(this);
        card.addView(createCopyableRow("License Key", tvKeyValue, activeKey.isEmpty() ? "No License Key" : activeKey, AppColors.WHITE));

        tvExpiryDateValue = new TextView(this);
        card.addView(createRow("Valid Until", tvExpiryDateValue, hasActive ? AuthManager.getExpiryDate(this) : "Not Subscribed", AppColors.WHITE));

        card.addView(createStaticRow("Driver Channel", "Ring 0 Real-Time IOCTL", 0xFF00E676));
        card.addView(createStaticRow("License Seat Policy", "Multi-Device Linked", AppColors.MAGENTA_TEXT_DIM));

        return card;
    }

    private View createClockDigitBox(TextView tvValue, String labelText) {
        LinearLayout box = new LinearLayout(this);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(0, dp(58), 1.0f);
        box.setLayoutParams(boxLp);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4), dp(4), dp(4), dp(4));

        GradientDrawable bBg = new GradientDrawable();
        bBg.setColor(Color.parseColor("#1B0B24"));
        bBg.setCornerRadius(dp(8));
        bBg.setStroke(dp(1), Color.parseColor("#4A1D4F"));
        box.setBackground(bBg);

        tvValue.setTextColor(0xFF00E676);
        tvValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        tvValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvValue.setGravity(Gravity.CENTER);
        box.addView(tvValue);

        TextView lbl = new TextView(this);
        lbl.setText(labelText);
        lbl.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        lbl.setGravity(Gravity.CENTER);
        lbl.setPadding(0, dp(1), 0, 0);
        box.addView(lbl);

        return box;
    }

    private View createClockColon() {
        TextView colon = new TextView(this);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(dp(10), dp(58));
        colon.setLayoutParams(cLp);
        colon.setText(":");
        colon.setTextColor(AppColors.WHITE);
        colon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        colon.setTypeface(Typeface.DEFAULT_BOLD);
        colon.setGravity(Gravity.CENTER);
        return colon;
    }

    // Security & Hardware Integrity Card
    private View createSecurityDiagnosticsCard() {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppColors.MAGENTA_CARD);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        card.setBackground(bg);

        card.addView(createStaticRow("Anti-Tamper Core", "Active (AES-256 + SHA-256)", 0xFF00E676));
        card.addView(createStaticRow("Memory Protection", "Hardware Enforced (Kernel)", 0xFF00E676));
        card.addView(createStaticRow("Native Library", "libninmods.so (ARM64-v8a)", AppColors.WHITE));
        card.addView(createStaticRow("Client Version", getString(R.string.app_name) + " v" + getString(R.string.app_version), AppColors.MAGENTA_PRIMARY));

        return card;
    }

    // Change Password Card
    private View createChangePasswordCard() {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppColors.MAGENTA_CARD);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        card.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("CHANGE ACCESS PASSWORD");
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setPadding(0, 0, 0, dp(4));
        card.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Update your tactical credentials directly on the secure server.");
        subtitle.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        subtitle.setPadding(0, 0, 0, dp(10));
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

        PasswordEyeHelper.PasswordFieldBundle currentPassBundle = PasswordEyeHelper.createPasswordField(this, "Current Password");
        etCurrentPassword = currentPassBundle.editText;
        card.addView(currentPassBundle.container);

        PasswordEyeHelper.PasswordFieldBundle newPassBundle = PasswordEyeHelper.createPasswordField(this, "New Password");
        etNewPassword = newPassBundle.editText;
        LinearLayout.LayoutParams npLp = (LinearLayout.LayoutParams) newPassBundle.container.getLayoutParams();
        npLp.topMargin = dp(10);
        newPassBundle.container.setLayoutParams(npLp);
        card.addView(newPassBundle.container);

        PasswordEyeHelper.PasswordFieldBundle confirmPassBundle = PasswordEyeHelper.createPasswordField(this, "Confirm New Password");
        etConfirmNewPassword = confirmPassBundle.editText;
        LinearLayout.LayoutParams cnpLp = (LinearLayout.LayoutParams) confirmPassBundle.container.getLayoutParams();
        cnpLp.topMargin = dp(10);
        confirmPassBundle.container.setLayoutParams(cnpLp);
        card.addView(confirmPassBundle.container);

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

    private TextView createStatusChip(String text, int textColor, int bgColor) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(textColor);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setPadding(dp(10), dp(4), dp(10), dp(4));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(6));
        bg.setStroke(dp(1), textColor);
        chip.setBackground(bg);

        return chip;
    }

    private View createSectionTitle(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(AppColors.MAGENTA_PRIMARY);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(6), 0, dp(6));
        return tv;
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

    private View createRow(String label, TextView tvVal, String initialValue, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvLabel = new TextView(this);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.45f));
        tvLabel.setText(label);
        tvLabel.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvLabel.setMaxLines(2);
        tvLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(tvLabel);

        tvVal.setText(initialValue);
        tvVal.setTextColor(valueColor);
        tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvVal.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvVal.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.55f));
        tvVal.setGravity(Gravity.END);
        tvVal.setMaxLines(2);
        tvVal.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(tvVal);

        return row;
    }

    private View createCopyableRow(String label, TextView tvVal, String initialValue, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvLabel = new TextView(this);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.45f));
        tvLabel.setText(label);
        tvLabel.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvLabel.setMaxLines(2);
        tvLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(tvLabel);

        tvVal.setText(initialValue);
        tvVal.setTextColor(valueColor);
        tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvVal.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvVal.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.55f));
        tvVal.setGravity(Gravity.END);
        tvVal.setMaxLines(2);
        tvVal.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(tvVal);

        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            String text = tvVal.getText().toString();
            if (!text.isEmpty() && !text.contains("No License")) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("License Key", text);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(ProfileActivity.this, "License Key copied to clipboard!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        return row;
    }

    private View createStaticRow(String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvLabel = new TextView(this);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.45f));
        tvLabel.setText(label);
        tvLabel.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvLabel.setMaxLines(2);
        tvLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(tvLabel);

        TextView tvVal = new TextView(this);
        tvVal.setText(value);
        tvVal.setTextColor(valueColor);
        tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvVal.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvVal.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.55f));
        tvVal.setGravity(Gravity.END);
        tvVal.setMaxLines(2);
        tvVal.setEllipsize(android.text.TextUtils.TruncateAt.END);
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

        // Fail-closed current-password check: compare against the locally stored salted hash.
        // If no hash is on record (e.g. storage was cleared outside of a normal login), do NOT
        // silently skip verification — require the user to re-authenticate instead of allowing
        // an unverified password change.
        String savedHash = AuthManager.getActivePasswordHash(this);
        if (savedHash.isEmpty()) {
            showError("Session credentials not found. Please log out and log back in before changing your password.");
            return;
        }
        if (!savedHash.equals(AuthManager.hashPassword(currentPass))) {
            showError("Current password is incorrect.");
            return;
        }

        tvError.setVisibility(View.GONE);
        updatePassBtn.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        etCurrentPassword.setEnabled(false);
        etNewPassword.setEnabled(false);
        etConfirmNewPassword.setEnabled(false);

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

                AuthManager.setActivePasswordHash(ProfileActivity.this, AuthManager.hashPassword(newPass));

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



    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
