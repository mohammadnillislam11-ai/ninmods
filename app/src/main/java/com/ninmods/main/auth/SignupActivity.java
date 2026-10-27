package com.ninmods.main.auth;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
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
import com.ninmods.main.core.AppColors;
import com.ninmods.main.core.BaseActivity;

public class SignupActivity extends BaseActivity {

    @Override
    protected boolean enableSecureScreen() {
        return true;
    }


    private EditText etUsername;
    private EditText etEmail;
    private EditText etTelephone;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private EditText etReferral;
    private TextView tvError;
    private Button signupBtn;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildView();
        } catch (Throwable t) {
            // Never leave the user with an empty (black) window.
            com.ninmods.main.core.AppLogger.logE("SYSTEM", "Signup screen failed to build: " + t.getMessage(), t);
            showFatalError("Could not open the signup screen",
                    "The interface could not be created on this device. "
                            + "Please reload the app or reinstall the latest release.");
        }
    }

    private void buildView() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Transparent root allows BaseActivity's blurred particle animation to shine through
        root.setBackgroundColor(Color.TRANSPARENT);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);

        // Center Container with tight horizontal margins to minimize unused side spaces
        LinearLayout centerContainer = new LinearLayout(this);
        centerContainer.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        centerContainer.setOrientation(LinearLayout.VERTICAL);
        centerContainer.setGravity(Gravity.CENTER);
        centerContainer.setPadding(dp(16), dp(24), dp(16), dp(40));

        // Main Board Column (Seamless on screen - flows naturally inside particle animation)
        LinearLayout board = new LinearLayout(this);
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        board.setLayoutParams(boardLp);
        board.setOrientation(LinearLayout.VERTICAL);
        board.setGravity(Gravity.CENTER_HORIZONTAL);

        // 1. Logo
        ImageView logo = new ImageView(this);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(84), dp(84));
        logoLp.bottomMargin = dp(10);
        logo.setLayoutParams(logoLp);
        logo.setImageResource(R.drawable.nin_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        board.addView(logo);

        // 2. Title
        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextColor(AppColors.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = dp(12);
        title.setLayoutParams(titleLp);
        board.addView(title);

        // 3. Error Label (Positioned ABOVE Username input field)
        tvError = new TextView(this);
        LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errLp.bottomMargin = dp(10);
        tvError.setLayoutParams(errLp);
        tvError.setTextColor(Color.parseColor("#FF5252"));
        tvError.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvError.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvError.setGravity(Gravity.CENTER);
        tvError.setVisibility(View.GONE);
        board.addView(tvError);

        // 4. Username Field (3-20 chars alphanumeric)
        etUsername = createInputField("Username (3-20 chars, letters/numbers)", InputType.TYPE_CLASS_TEXT);
        board.addView(etUsername);

        // 5. Email Field (Required by Backend Server)
        etEmail = createInputField("Email Address", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        LinearLayout.LayoutParams emailLp = (LinearLayout.LayoutParams) etEmail.getLayoutParams();
        emailLp.topMargin = dp(10);
        etEmail.setLayoutParams(emailLp);
        board.addView(etEmail);

        // 6. Phone / Mobile Number Field (Optional)
        etTelephone = createInputField("Phone / Mobile Number (Optional)", InputType.TYPE_CLASS_PHONE);
        LinearLayout.LayoutParams phoneLp = (LinearLayout.LayoutParams) etTelephone.getLayoutParams();
        phoneLp.topMargin = dp(10);
        etTelephone.setLayoutParams(phoneLp);
        board.addView(etTelephone);

        // 7. Password Field (Min 6 characters) with Eye Toggle
        com.ninmods.main.ui.PasswordEyeHelper.PasswordFieldBundle passBundle =
                com.ninmods.main.ui.PasswordEyeHelper.createPasswordField(this, "Password (Min 6 chars)");
        etPassword = passBundle.editText;
        LinearLayout.LayoutParams passLp = (LinearLayout.LayoutParams) passBundle.container.getLayoutParams();
        passLp.topMargin = dp(10);
        passBundle.container.setLayoutParams(passLp);
        board.addView(passBundle.container);

        // 8. Confirm Password Field with Eye Toggle
        com.ninmods.main.ui.PasswordEyeHelper.PasswordFieldBundle confPassBundle =
                com.ninmods.main.ui.PasswordEyeHelper.createPasswordField(this, "Confirm Password");
        etConfirmPassword = confPassBundle.editText;
        LinearLayout.LayoutParams confPassLp = (LinearLayout.LayoutParams) confPassBundle.container.getLayoutParams();
        confPassLp.topMargin = dp(10);
        confPassBundle.container.setLayoutParams(confPassLp);
        board.addView(confPassBundle.container);

        // 8. Referral Code (Optional)
        etReferral = createInputField("Referral Code (Optional)", InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams refLp = (LinearLayout.LayoutParams) etReferral.getLayoutParams();
        refLp.topMargin = dp(10);
        etReferral.setLayoutParams(refLp);
        board.addView(etReferral);

        // 9. Sign Up Button
        signupBtn = new Button(this);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        btnLp.topMargin = dp(14);
        signupBtn.setLayoutParams(btnLp);
        signupBtn.setText("SIGN UP");
        signupBtn.setTextColor(AppColors.WHITE);
        signupBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        signupBtn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END}
        );
        btnBg.setCornerRadius(dp(8));
        signupBtn.setBackground(btnBg);
        signupBtn.setOnClickListener(v -> performSignup());
        board.addView(signupBtn);

        // 10. Native Loading Indicator
        progressBar = new ProgressBar(this);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        pbLp.gravity = Gravity.CENTER_HORIZONTAL;
        pbLp.topMargin = dp(14);
        pbLp.bottomMargin = dp(4);
        progressBar.setLayoutParams(pbLp);
        progressBar.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBar.setIndeterminateTintList(ColorStateList.valueOf(AppColors.MAGENTA_PRIMARY));
        }
        progressBar.setVisibility(View.GONE);
        board.addView(progressBar);

        // 11. Navigation Link (Login)
        TextView loginLink = new TextView(this);
        LinearLayout.LayoutParams llLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llLp.topMargin = dp(14);
        llLp.gravity = Gravity.CENTER_HORIZONTAL;
        loginLink.setLayoutParams(llLp);
        loginLink.setText("Already have an account? Login");
        loginLink.setTextColor(AppColors.MAGENTA_LIGHT);
        loginLink.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        loginLink.setGravity(Gravity.CENTER);
        loginLink.setPadding(0, dp(6), 0, dp(6));
        loginLink.setOnClickListener(v -> finish());
        board.addView(loginLink);

        centerContainer.addView(board);
        scrollView.addView(centerContainer);
        root.addView(scrollView);

        setContentView(root);
    }

    private EditText createInputField(String hint, int inputType) {
        EditText et = new EditText(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        et.setLayoutParams(lp);
        et.setHint(hint);
        et.setHintTextColor(AppColors.MAGENTA_TEXT_DIM);
        et.setTextColor(AppColors.WHITE);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        et.setInputType(inputType);
        et.setSingleLine(true);
        et.setPadding(dp(14), 0, dp(14), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppColors.MAGENTA_CHIP);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        et.setBackground(bg);

        return et;
    }

    private void performSignup() {
        final String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        final String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        final String telephone = etTelephone.getText() != null ? etTelephone.getText().toString().trim() : "";
        final String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        final String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";
        final String refCode = etReferral.getText() != null ? etReferral.getText().toString().trim() : "";

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Please fill in username, email, and password fields.");
            return;
        }

        if (username.length() < 3 || username.length() > 30 || !username.matches("^[a-zA-Z0-9_.-]{3,30}$")) {
            showError("Username must be 3-30 characters (letters, numbers, underscores, dots, hyphens).");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address.");
            return;
        }

        if (password.length() < 6) {
            showError("Password must be at least 6 characters long.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }

        // Hide errors and trigger Android default loading component
        tvError.setVisibility(View.GONE);
        signupBtn.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        etUsername.setEnabled(false);
        etEmail.setEnabled(false);
        etTelephone.setEnabled(false);
        etPassword.setEnabled(false);
        etConfirmPassword.setEnabled(false);
        etReferral.setEnabled(false);

        // Forward full registration credentials to Node.js /api/v2/auth/register endpoint
        AuthApiController.register(this, username, email, password, telephone, refCode, new AuthApiController.ApiCallback() {
            @Override
            public void onSuccess(String message) {
                if (isFinishing()) return;
                progressBar.setVisibility(View.GONE);
                signupBtn.setVisibility(View.VISIBLE);
                etUsername.setEnabled(true);
                etEmail.setEnabled(true);
                etTelephone.setEnabled(true);
                etPassword.setEnabled(true);
                etConfirmPassword.setEnabled(true);
                etReferral.setEnabled(true);

                if (!telephone.isEmpty()) {
                    com.ninmods.main.core.Prefs.with(SignupActivity.this).writeString(com.ninmods.main.core.ProfileActivity.PREF_ACTIVE_PHONE, telephone);
                }

                Toast.makeText(SignupActivity.this, message, Toast.LENGTH_LONG).show();
                finish(); // Return to LoginActivity
            }

            @Override
            public void onError(String errorMessage) {
                if (isFinishing()) return;
                progressBar.setVisibility(View.GONE);
                signupBtn.setVisibility(View.VISIBLE);
                etUsername.setEnabled(true);
                etEmail.setEnabled(true);
                etTelephone.setEnabled(true);
                etPassword.setEnabled(true);
                etConfirmPassword.setEnabled(true);
                etReferral.setEnabled(true);
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
