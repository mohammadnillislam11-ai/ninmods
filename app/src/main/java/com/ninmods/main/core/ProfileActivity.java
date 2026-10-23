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

public class ProfileActivity extends BaseActivity {

    public static final String PREF_ACTIVE_USERNAME = "active_username";
    public static final String PREF_ACTIVE_EMAIL = "active_email";
    public static final String PREF_ACTIVE_PASSWORD = "active_password";

    private EditText etCurrentPassword;
    private EditText etNewPassword;
    private EditText etConfirmNewPassword;
    private TextView tvError;
    private Button updatePassBtn;
    private ProgressBar progressBar;

    private TextView tvLiveCountdown;
    private TextView tvPlanValue;
    private TextView tvKeyValue;
    private TextView tvExpiryDateValue;

    private String currentUsername;
    private String currentEmail;

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
    }

    @Override
    protected void onPause() {
        super.onPause();
        tickerHandler.removeCallbacks(tickerRunnable);
    }

    private void loadUserData() {
        currentUsername = Prefs.with(this).readString(PREF_ACTIVE_USERNAME, "");
        if (currentUsername == null || currentUsername.trim().isEmpty()) {
            currentUsername = "VIP Operator";
            Prefs.with(this).writeString(PREF_ACTIVE_USERNAME, currentUsername);
        }

        currentEmail = Prefs.with(this).readString(PREF_ACTIVE_EMAIL, "");
        if (currentEmail == null || currentEmail.trim().isEmpty()) {
            currentEmail = currentUsername.toLowerCase().replaceAll("\\s+", "") + "@ninmods.vip";
        }
    }

    private void updateUIValues() {
        if (tvPlanValue != null) {
            tvPlanValue.setText(AuthManager.getActivePlanName(this));
        }
        if (tvKeyValue != null) {
            String key = AuthManager.getActiveLicenseKey(this);
            tvKeyValue.setText(key.isEmpty() ? "No License Key Bound" : key);
        }
        if (tvExpiryDateValue != null) {
            tvExpiryDateValue.setText(AuthManager.getExpiryDate(this));
        }
        updateLiveCountdown();
    }

    private void updateLiveCountdown() {
        if (tvLiveCountdown == null) return;
        long remaining = AuthManager.getRemainingSeconds(this);
        if (remaining > 0) {
            tvLiveCountdown.setText(AuthManager.formatRemainingTime(remaining));
            tvLiveCountdown.setTextColor(0xFF00E676);
        } else {
            tvLiveCountdown.setText("No Active License / Expired");
            tvLiveCountdown.setTextColor(0xFFFF5252);
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

        // 4. Active Membership & Live Expiration Countdown Card
        content.addView(createSectionTitle("VIP MEMBERSHIP & EXPIRATION"));
        content.addView(createSubscriptionCountdownCard());

        // 5. Security & System Integrity Card
        content.addView(createSectionTitle("SECURITY & HARDWARE INTEGRITY"));
        content.addView(createSecurityDiagnosticsCard());

        // 6. Change Password Card
        content.addView(createSectionTitle("ACCOUNT CREDENTIALS"));
        content.addView(createChangePasswordCard());

        // 7. Official Telegram Support / Renewal Button
        Button supportBtn = new Button(this);
        LinearLayout.LayoutParams supLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        supLp.topMargin = dp(14);
        supportBtn.setLayoutParams(supLp);
        supportBtn.setText("OFFICIAL SUPPORT & RENEWAL");
        supportBtn.setTextColor(AppColors.WHITE);
        supportBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        supportBtn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable supBg = new GradientDrawable();
        supBg.setColor(Color.parseColor("#1B2A4A"));
        supBg.setCornerRadius(dp(8));
        supBg.setStroke(dp(1), Color.parseColor("#2E5B9A"));
        supportBtn.setBackground(supBg);
        supportBtn.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/ninmods_official"));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Opening support channel...", Toast.LENGTH_SHORT).show();
            }
        });
        content.addView(supportBtn);

        // 8. Log Out Button
        Button logoutBtn = new Button(this);
        LinearLayout.LayoutParams loLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        loLp.topMargin = dp(10);
        logoutBtn.setLayoutParams(loLp);
        logoutBtn.setText("LOG OUT SESSION");
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

        TextView tvName = new TextView(this);
        tvName.setText(currentUsername);
        tvName.setTextColor(AppColors.WHITE);
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
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

        // Badge Pill Row below avatar
        LinearLayout badgeRow = new LinearLayout(this);
        badgeRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        badgeRow.setPadding(0, dp(14), 0, 0);

        badgeRow.addView(createStatusChip("VIP SUBSCRIBER", 0xFFF76BF7, 0x22F76BF7));

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
        badgeRow.addView(spacer);

        badgeRow.addView(createStatusChip("PROTECTED SESSION", 0xFF00E676, 0x2200E676));

        card.addView(badgeRow);

        return card;
    }

    // Active Membership & Live Countdown Card
    private View createSubscriptionCountdownCard() {
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

        // Big Live Countdown Container
        LinearLayout countdownBox = new LinearLayout(this);
        countdownBox.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        countdownBox.setOrientation(LinearLayout.VERTICAL);
        countdownBox.setPadding(dp(14), dp(12), dp(14), dp(12));

        GradientDrawable cBg = new GradientDrawable();
        cBg.setColor(AppColors.MAGENTA_CHIP);
        cBg.setCornerRadius(dp(8));
        cBg.setStroke(dp(1), 0x4400E676);
        countdownBox.setBackground(cBg);

        TextView label = new TextView(this);
        label.setText("LIVE TIME REMAINING");
        label.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        countdownBox.addView(label);

        tvLiveCountdown = new TextView(this);
        tvLiveCountdown.setText("Calculating...");
        tvLiveCountdown.setTextColor(0xFF00E676);
        tvLiveCountdown.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tvLiveCountdown.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvLiveCountdown.setPadding(0, dp(4), 0, dp(2));
        countdownBox.addView(tvLiveCountdown);

        TextView sub = new TextView(this);
        sub.setText("Real-time dynamic subscription expiration countdown");
        sub.setTextColor(Color.parseColor("#99FFFFFF"));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        countdownBox.addView(sub);

        card.addView(countdownBox);

        // Divider
        View div = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divLp.setMargins(0, dp(14), 0, dp(12));
        div.setLayoutParams(divLp);
        div.setBackgroundColor(AppColors.MAGENTA_CARD_BORDER);
        card.addView(div);

        // Detail Rows
        tvPlanValue = new TextView(this);
        card.addView(createRow("Subscription Plan", tvPlanValue, AuthManager.getActivePlanName(this), 0xFF00E676));

        tvKeyValue = new TextView(this);
        String activeKey = AuthManager.getActiveLicenseKey(this);
        card.addView(createCopyableRow("License Key", tvKeyValue, activeKey.isEmpty() ? "No License Key" : activeKey, AppColors.WHITE));

        tvExpiryDateValue = new TextView(this);
        card.addView(createRow("Valid Until", tvExpiryDateValue, AuthManager.getExpiryDate(this), AppColors.WHITE));

        card.addView(createStaticRow("Driver Channel", "Ring 0 Real-Time IOCTL", 0xFF00E676));
        card.addView(createStaticRow("License Seat Policy", "Multi-Device Linked", AppColors.MAGENTA_TEXT_DIM));

        return card;
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
        card.addView(createStaticRow("Client Version", getString(R.string.app_name) + " V90.4", AppColors.MAGENTA_PRIMARY));

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

    private View createStatusChip(String text, int textColor, int bgColor) {
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
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        tvLabel.setText(label);
        tvLabel.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        row.addView(tvLabel);

        tvVal.setText(initialValue);
        tvVal.setTextColor(valueColor);
        tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvVal.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
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
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        tvLabel.setText(label);
        tvLabel.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        row.addView(tvLabel);

        tvVal.setText(initialValue);
        tvVal.setTextColor(valueColor);
        tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvVal.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
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
            AuthManager.clearSession(ProfileActivity.this);
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

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
