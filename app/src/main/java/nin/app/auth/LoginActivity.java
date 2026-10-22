package nin.app.auth;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import nin.app.R;
import nin.app.core.AppColors;
import nin.app.core.BaseActivity;
import nin.app.core.DashboardActivity;
import nin.app.core.Prefs;

public class LoginActivity extends BaseActivity {

    private static final String PREF_REMEMBER_ME = "pref_auth_remember_me";
    private static final String PREF_SAVED_USERNAME = "pref_auth_saved_username";
    private static final String PREF_SAVED_PASSWORD = "pref_auth_saved_password";

    private EditText etUsername;
    private EditText etPassword;
    private CheckBox cbRemember;
    private TextView tvError;
    private Button loginBtn;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildView();
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
        centerContainer.setPadding(dp(16), dp(28), dp(16), dp(20));

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
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(96), dp(96));
        logoLp.bottomMargin = dp(12);
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
        titleLp.bottomMargin = dp(16);
        title.setLayoutParams(titleLp);
        board.addView(title);

        // 3. Error Label (Strictly positioned ABOVE Username input field per user explicit instruction)
        tvError = new TextView(this);
        LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errLp.bottomMargin = dp(12);
        tvError.setLayoutParams(errLp);
        tvError.setTextColor(Color.parseColor("#FF5252"));
        tvError.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvError.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvError.setGravity(Gravity.CENTER);
        tvError.setVisibility(View.GONE);
        board.addView(tvError);

        // 4. Username Field
        etUsername = createInputField("Username", InputType.TYPE_CLASS_TEXT);
        board.addView(etUsername);

        // 5. Password Field
        etPassword = createInputField("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams passLp = (LinearLayout.LayoutParams) etPassword.getLayoutParams();
        passLp.topMargin = dp(12);
        etPassword.setLayoutParams(passLp);
        board.addView(etPassword);

        // 6. Remember Me & Forgot Password Row (Positioned side-by-side above Login button per user screenshot)
        LinearLayout rememberRow = new LinearLayout(this);
        rememberRow.setOrientation(LinearLayout.HORIZONTAL);
        rememberRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams remRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        remRowLp.topMargin = dp(8);
        remRowLp.bottomMargin = dp(2);
        rememberRow.setLayoutParams(remRowLp);

        // Left: Remember Me Checkbox
        cbRemember = new CheckBox(this);
        cbRemember.setText(" Remember Me");
        cbRemember.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        cbRemember.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cbRemember.setButtonTintList(ColorStateList.valueOf(AppColors.MAGENTA_PRIMARY));
        }

        // Restore saved Remember Me state & credentials
        boolean savedRemember = Prefs.with(this).readBoolean(PREF_REMEMBER_ME, false);
        cbRemember.setChecked(savedRemember);
        if (savedRemember) {
            String savedUser = Prefs.with(this).readString(PREF_SAVED_USERNAME, "");
            String savedPass = Prefs.with(this).readString(PREF_SAVED_PASSWORD, "");
            if (!savedUser.isEmpty()) etUsername.setText(savedUser);
            if (!savedPass.isEmpty()) etPassword.setText(savedPass);
        }
        rememberRow.addView(cbRemember);

        // Spacer to push Forgot Password to the far right
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1.0f));
        rememberRow.addView(spacer);

        // Right: Forgot Password Link
        TextView resetPassLink = new TextView(this);
        resetPassLink.setText("Forgot Password?");
        resetPassLink.setTextColor(AppColors.MAGENTA_LIGHT);
        resetPassLink.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        resetPassLink.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        resetPassLink.setPadding(dp(4), dp(4), dp(4), dp(4));
        resetPassLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ResetPasswordActivity.class);
            startActivity(intent);
        });
        rememberRow.addView(resetPassLink);

        board.addView(rememberRow);

        // 7. Login Button
        loginBtn = new Button(this);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        btnLp.topMargin = dp(14);
        loginBtn.setLayoutParams(btnLp);
        loginBtn.setText("LOGIN");
        loginBtn.setTextColor(AppColors.WHITE);
        loginBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        loginBtn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END}
        );
        btnBg.setCornerRadius(dp(8));
        loginBtn.setBackground(btnBg);
        loginBtn.setOnClickListener(v -> performLogin());
        board.addView(loginBtn);

        // 8. Official Android Native Circular Loading Component
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

        // 9. Navigation Link (Signup)
        TextView signUpLink = new TextView(this);
        LinearLayout.LayoutParams suLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        suLp.topMargin = dp(16);
        suLp.gravity = Gravity.CENTER_HORIZONTAL;
        signUpLink.setLayoutParams(suLp);
        signUpLink.setText("Don't have an account? Sign Up");
        signUpLink.setTextColor(AppColors.MAGENTA_LIGHT);
        signUpLink.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        signUpLink.setGravity(Gravity.CENTER);
        signUpLink.setPadding(0, dp(6), 0, dp(6));
        signUpLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
        });
        board.addView(signUpLink);

        // 10. Official Version Footer inside scrollable content (Never overlaps keyboard)
        TextView footerVersion = new TextView(this);
        LinearLayout.LayoutParams fvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        fvLp.gravity = Gravity.CENTER_HORIZONTAL;
        fvLp.topMargin = dp(24);
        fvLp.bottomMargin = dp(8);
        footerVersion.setLayoutParams(fvLp);
        footerVersion.setGravity(Gravity.CENTER);
        footerVersion.setText("Version: " + getString(R.string.app_version));
        footerVersion.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        footerVersion.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        footerVersion.setTypeface(Typeface.DEFAULT_BOLD);
        board.addView(footerVersion);

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

    private void performLogin() {
        final String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        final String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }

        // Hide errors and trigger Android default loading component
        tvError.setVisibility(View.GONE);
        loginBtn.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        etUsername.setEnabled(false);
        etPassword.setEnabled(false);

        // Forward credentials to Node.js /api/v2/auth/login endpoint
        AuthApiController.login(this, username, password, new AuthApiController.ApiCallback() {
            @Override
            public void onSuccess(String message) {
                if (isFinishing()) return;
                progressBar.setVisibility(View.GONE);
                loginBtn.setVisibility(View.VISIBLE);
                etUsername.setEnabled(true);
                etPassword.setEnabled(true);

                // Save or clear Remember Me state
                if (cbRemember != null && cbRemember.isChecked()) {
                    Prefs.with(LoginActivity.this).writeBoolean(PREF_REMEMBER_ME, true);
                    Prefs.with(LoginActivity.this).writeString(PREF_SAVED_USERNAME, username);
                    Prefs.with(LoginActivity.this).writeString(PREF_SAVED_PASSWORD, password);
                } else {
                    Prefs.with(LoginActivity.this).writeBoolean(PREF_REMEMBER_ME, false);
                    Prefs.with(LoginActivity.this).writeString(PREF_SAVED_USERNAME, "");
                    Prefs.with(LoginActivity.this).writeString(PREF_SAVED_PASSWORD, "");
                }

                Prefs.with(LoginActivity.this).writeString(nin.app.core.ProfileActivity.PREF_ACTIVE_USERNAME, username);
                if (Prefs.with(LoginActivity.this).readString(nin.app.core.ProfileActivity.PREF_ACTIVE_EMAIL, "").isEmpty()) {
                    Prefs.with(LoginActivity.this).writeString(nin.app.core.ProfileActivity.PREF_ACTIVE_EMAIL, username.toLowerCase() + "@ninmods.vip");
                }
                if (Prefs.with(LoginActivity.this).readString(nin.app.core.ProfileActivity.PREF_ACTIVE_PLAN, "").isEmpty()) {
                    Prefs.with(LoginActivity.this).writeString(nin.app.core.ProfileActivity.PREF_ACTIVE_PLAN, "16-Day VIP Access (Active)");
                }
                Prefs.with(LoginActivity.this).writeString(nin.app.core.ProfileActivity.PREF_ACTIVE_PASSWORD, password);

                AuthManager.setLoggedIn(LoginActivity.this, true);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                if (isFinishing()) return;
                progressBar.setVisibility(View.GONE);
                loginBtn.setVisibility(View.VISIBLE);
                etUsername.setEnabled(true);
                etPassword.setEnabled(true);
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
