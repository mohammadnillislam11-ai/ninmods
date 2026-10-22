package nin.app.auth;

import android.app.Activity;
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
import nin.app.ui.TacticalGraphicBackground;

public class ResetPasswordActivity extends Activity {

    private EditText etUsername;
    private EditText etNewPassword;
    private TextView tvError;
    private Button resetBtn;
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

        // Rich Cyber Matrix Tactical Graphic Body Background (Zero Plain Black)
        root.setBackground(new TacticalGraphicBackground(this));

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

        // Main Board Column (Stretches to fill screen width cleanly)
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

        // 5. New Password Field
        etNewPassword = createInputField("New Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams passLp = (LinearLayout.LayoutParams) etNewPassword.getLayoutParams();
        passLp.topMargin = dp(12);
        etNewPassword.setLayoutParams(passLp);
        board.addView(etNewPassword);

        // 6. Reset Password Button
        resetBtn = new Button(this);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        btnLp.topMargin = dp(16);
        resetBtn.setLayoutParams(btnLp);
        resetBtn.setText("RESET PASSWORD");
        resetBtn.setTextColor(AppColors.WHITE);
        resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        resetBtn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END}
        );
        btnBg.setCornerRadius(dp(8));
        resetBtn.setBackground(btnBg);
        resetBtn.setOnClickListener(v -> performReset());
        board.addView(resetBtn);

        // 7. Official Android Native Circular Loading Component
        progressBar = new ProgressBar(this);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        pbLp.gravity = Gravity.CENTER_HORIZONTAL;
        pbLp.topMargin = dp(16);
        pbLp.bottomMargin = dp(4);
        progressBar.setLayoutParams(pbLp);
        progressBar.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBar.setIndeterminateTintList(ColorStateList.valueOf(AppColors.MAGENTA_PRIMARY));
        }
        progressBar.setVisibility(View.GONE);
        board.addView(progressBar);

        // 8. Navigation Link (Back to Login)
        LinearLayout linksRow = new LinearLayout(this);
        LinearLayout.LayoutParams linksLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linksLp.topMargin = dp(16);
        linksRow.setLayoutParams(linksLp);
        linksRow.setOrientation(LinearLayout.VERTICAL);
        linksRow.setGravity(Gravity.CENTER);

        TextView backToLoginLink = new TextView(this);
        backToLoginLink.setText("Remember your password? Login");
        backToLoginLink.setTextColor(AppColors.MAGENTA_LIGHT);
        backToLoginLink.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        backToLoginLink.setGravity(Gravity.CENTER);
        backToLoginLink.setPadding(0, dp(6), 0, dp(6));
        backToLoginLink.setOnClickListener(v -> finish());
        linksRow.addView(backToLoginLink);

        board.addView(linksRow);

        // 9. Official Version Footer
        TextView footerVersion = new TextView(this);
        LinearLayout.LayoutParams fvLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fvLp.topMargin = dp(32);
        fvLp.bottomMargin = dp(8);
        footerVersion.setLayoutParams(fvLp);
        footerVersion.setText("V" + getString(R.string.app_version));
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

    private void performReset() {
        final String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        final String newPassword = etNewPassword.getText() != null ? etNewPassword.getText().toString().trim() : "";

        if (username.isEmpty() || newPassword.isEmpty()) {
            showError("Please enter both username and new password.");
            return;
        }

        if (newPassword.length() < 4) {
            showError("Password must be at least 4 characters.");
            return;
        }

        // Hide errors and trigger Android default loading component
        tvError.setVisibility(View.GONE);
        resetBtn.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        etUsername.setEnabled(false);
        etNewPassword.setEnabled(false);

        // Forward password reset to Node.js /api/v2/auth/reset-password endpoint
        AuthApiController.resetPassword(this, username, newPassword, new AuthApiController.ApiCallback() {
            @Override
            public void onSuccess(String message) {
                if (isFinishing()) return;
                progressBar.setVisibility(View.GONE);
                resetBtn.setVisibility(View.VISIBLE);
                etUsername.setEnabled(true);
                etNewPassword.setEnabled(true);

                Toast.makeText(ResetPasswordActivity.this, message, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                if (isFinishing()) return;
                progressBar.setVisibility(View.GONE);
                resetBtn.setVisibility(View.VISIBLE);
                etUsername.setEnabled(true);
                etNewPassword.setEnabled(true);
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
