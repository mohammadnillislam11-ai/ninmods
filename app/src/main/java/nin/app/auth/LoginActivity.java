package nin.app.auth;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import nin.app.R;
import nin.app.core.AppColors;
import nin.app.core.DashboardActivity;

public class LoginActivity extends Activity {

    private EditText etUsername;
    private EditText etPassword;
    private TextView tvError;

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

        // Dark gradient background
        GradientDrawable screenBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{AppColors.MAGENTA_SCREEN_BG, AppColors.BLACK}
        );
        root.setBackground(screenBg);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout centerContainer = new LinearLayout(this);
        centerContainer.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        centerContainer.setOrientation(LinearLayout.VERTICAL);
        centerContainer.setGravity(Gravity.CENTER);
        centerContainer.setPadding(dp(24), dp(36), dp(24), dp(36));

        // Main Board Column (Direct on board - No Card box)
        LinearLayout board = new LinearLayout(this);
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(
                dp(320),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        board.setLayoutParams(boardLp);
        board.setOrientation(LinearLayout.VERTICAL);
        board.setGravity(Gravity.CENTER_HORIZONTAL);

        // 1. Logo
        ImageView logo = new ImageView(this);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(56), dp(56));
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
        titleLp.bottomMargin = dp(24);
        title.setLayoutParams(titleLp);
        board.addView(title);

        // 3. Username Field
        etUsername = createInputField("Username", InputType.TYPE_CLASS_TEXT);
        board.addView(etUsername);

        // 4. Password Field
        etPassword = createInputField("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams passLp = (LinearLayout.LayoutParams) etPassword.getLayoutParams();
        passLp.topMargin = dp(12);
        etPassword.setLayoutParams(passLp);
        board.addView(etPassword);

        // 5. Error Label
        tvError = new TextView(this);
        LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errLp.topMargin = dp(8);
        tvError.setLayoutParams(errLp);
        tvError.setTextColor(Color.parseColor("#FF5252"));
        tvError.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvError.setGravity(Gravity.CENTER);
        tvError.setVisibility(View.GONE);
        board.addView(tvError);

        // 6. Login Button
        Button loginBtn = new Button(this);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        btnLp.topMargin = dp(18);
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

        // 7. Navigation Links (Signup & Reset Password)
        LinearLayout linksRow = new LinearLayout(this);
        LinearLayout.LayoutParams linksLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linksLp.topMargin = dp(16);
        linksRow.setLayoutParams(linksLp);
        linksRow.setOrientation(LinearLayout.VERTICAL);
        linksRow.setGravity(Gravity.CENTER);

        TextView signUpLink = new TextView(this);
        signUpLink.setText("Don't have an account? Sign Up");
        signUpLink.setTextColor(AppColors.MAGENTA_LIGHT);
        signUpLink.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        signUpLink.setGravity(Gravity.CENTER);
        signUpLink.setPadding(0, dp(6), 0, dp(6));
        signUpLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
        });
        linksRow.addView(signUpLink);

        TextView resetPassLink = new TextView(this);
        resetPassLink.setText("Forgot Password? Reset");
        resetPassLink.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        resetPassLink.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        resetPassLink.setGravity(Gravity.CENTER);
        resetPassLink.setPadding(0, dp(6), 0, dp(6));
        resetPassLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ResetPasswordActivity.class);
            startActivity(intent);
        });
        linksRow.addView(resetPassLink);

        board.addView(linksRow);

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
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }

        if (AuthManager.verifyLogin(username, password)) {
            tvError.setVisibility(View.GONE);
            AuthManager.setLoggedIn(this, true);
            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(this, DashboardActivity.class);
            startActivity(intent);
            finish();
        } else {
            showError("Invalid credentials! Please try again.");
        }
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
