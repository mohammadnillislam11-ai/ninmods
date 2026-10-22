package nin.app.core;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import nin.app.overlay.Floater;

public class ModeSelectActivity extends Activity {
    static {
        System.loadLibrary("ninmods");
    }

    public static final int MODE_SAFE = 1;
    public static final int MODE_EXTREME = 2;
    public static final String PREF_KEY_MODE = "config_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, DashboardActivity.class);
        startActivity(intent);
        finish();
    }

    private void showSelectorScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        root.setBackground(createScreenBackground());

        LinearLayout cardLayout = new LinearLayout(this);
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        cardLayout.setBackground(createCardBackground());
        cardLayout.setPadding(dp(0), dp(0), dp(0), dp(20));
        cardLayout.setAlpha(0.96f);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                dp(320),
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.gravity = Gravity.CENTER;
        cardLayout.setLayoutParams(cardParams);
        cardLayout.setElevation(dp(12));
        root.addView(cardLayout);

        LinearLayout topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.VERTICAL);
        topPanel.setPadding(dp(20), dp(20), dp(20), dp(18));
        topPanel.setBackground(createTopPanelBackground());
        cardLayout.addView(topPanel);

        TextView logoTag = new TextView(this);
        logoTag.setText(getString(nin.app.R.string.app_name));
        logoTag.setTextColor(nin.app.core.AppColors.MODE_TAG_TEXT);
        logoTag.setAlpha(0.92f);
        logoTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        topPanel.addView(logoTag);

        TextView heading = new TextView(this);
        heading.setText(getString(nin.app.R.string.mode_select_title));
        heading.setTextColor(nin.app.core.AppColors.TEXT_WHITE);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        heading.setPadding(0, dp(8), 0, dp(6));
        topPanel.addView(heading);

        TextView subtitle = new TextView(this);
        subtitle.setText(getString(nin.app.R.string.mode_select_subtitle));
        subtitle.setTextColor(nin.app.core.AppColors.MODE_SUBTITLE_TEXT);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        topPanel.addView(subtitle);

        TextView securityHint = new TextView(this);
        securityHint.setText("Tactical Profiles");
        securityHint.setTextColor(nin.app.core.AppColors.MODE_SECURITY_HINT);
        securityHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        securityHint.setPadding(dp(20), dp(18), dp(20), dp(14));
        cardLayout.addView(securityHint);

        Button safeButton = createOptionButton(getString(nin.app.R.string.mode_safe_title), getString(nin.app.R.string.mode_safe_desc));
        safeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSelectedMode(MODE_SAFE);
            }
        });
        LinearLayout.LayoutParams safeLp = (LinearLayout.LayoutParams) safeButton.getLayoutParams();
        safeLp.setMargins(dp(20), 0, dp(20), dp(10));
        safeButton.setLayoutParams(safeLp);
        cardLayout.addView(safeButton);

        Button brutalButton = createOptionButton(getString(nin.app.R.string.mode_extreme_title), getString(nin.app.R.string.mode_extreme_desc));
        brutalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSelectedMode(MODE_EXTREME);
            }
        });
        LinearLayout.LayoutParams brutalLp = (LinearLayout.LayoutParams) brutalButton.getLayoutParams();
        brutalLp.setMargins(dp(20), 0, dp(20), 0);
        brutalButton.setLayoutParams(brutalLp);
        cardLayout.addView(brutalButton);

        setContentView(root);
    }

    private Button createOptionButton(String title, String detail) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), dp(12), dp(14), dp(12));
        button.setText(title + "\n" + detail);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        button.setTextColor(Color.WHITE);
        button.setBackground(createPrimaryButtonBackground());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        button.setLayoutParams(params);
        return button;
    }

    private GradientDrawable createTopPanelBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.BLACK, Color.BLACK, Color.BLACK}
        );
        drawable.setCornerRadii(new float[]{dp(18), dp(18), dp(18), dp(18), 0, 0, 0, 0});
        return drawable;
    }

    private GradientDrawable createCardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(nin.app.core.AppColors.get(this, nin.app.R.color.text_close, nin.app.core.AppColors.TEXT_CLOSE));
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), nin.app.core.AppColors.MODE_CARD_BORDER);
        return drawable;
    }

    private GradientDrawable createScreenBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{nin.app.core.AppColors.MODE_SCREEN_BG, nin.app.core.AppColors.MODE_SCREEN_BG, Color.BLACK}
        );
    }

    private GradientDrawable createPrimaryButtonBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{nin.app.core.AppColors.MODE_BTN_START, nin.app.core.AppColors.MODE_BTN_END}
        );
        drawable.setCornerRadius(dp(12));
        return drawable;
    }

    private void openSelectedMode(int mode) {
        Prefs.with(this).writeInt(PREF_KEY_MODE, mode);
        setModeNative(mode);

        try {
            Floater.start(this);

            Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.dts.freefireth");
            if (launchIntent != null) {
                startActivity(launchIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
            new Initialize(this);
        }

        finish();
    }

    public static native void setModeNative(int mode);

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
