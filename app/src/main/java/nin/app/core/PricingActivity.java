package nin.app.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import nin.app.R;

public class PricingActivity extends Activity {

    public static final String TELEGRAM_SUPPORT_URL = "https://t.me/ninmods";

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
        root.setBackgroundColor(AppColors.BLACK);

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
        title.setText("PRICING PLANS");
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

        // Subtitle banner
        TextView subHeader = new TextView(this);
        subHeader.setText("Choose your tactical subscription tier. All plans provide full access to all 4 modular software cheats.");
        subHeader.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        subHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subHeader.setPadding(0, 0, 0, dp(14));
        content.addView(subHeader);

        // Plan 1: 3-Day Plan ($2.50)
        content.addView(createPlanCard(
                "3-DAY TACTICAL PASS",
                "3 Days Access",
                "$2.50",
                null,
                new String[]{
                        "Full Access to All 4 Modular Cheats",
                        "Real Kernel RT & QX IPC Modes",
                        "Anti-Detection Scanning Engine",
                        "Instant Digital Key Activation"
                },
                "BUY 3-DAY KEY ($2.50)",
                false
        ));

        // Plan 2: 7-Day Plan ($4.00)
        content.addView(createPlanCard(
                "7-DAY ELITE PASS",
                "7 Days Access",
                "$4.00",
                "MOST POPULAR",
                new String[]{
                        "Full Access to All 4 Modular Cheats",
                        "Full ESP + Smart Aim Target Matrix",
                        "Dual-Engine Safe & External Pipeline",
                        "Priority 24/7 Updates & Fast Support"
                },
                "BUY 7-DAY KEY ($4.00)",
                false
        ));

        // Plan 3: 16-Day Plan ($7.50)
        content.addView(createPlanCard(
                "16-DAY VIP MASTER",
                "16 Days Access",
                "$7.50",
                "BEST VALUE - 50% OFF",
                new String[]{
                        "Unlimited Unrestricted Access",
                        "Safe Anti-Detection & Ring 0 Kernel Mode",
                        "Direct VIP Assistance & Custom Offsets",
                        "Maximum Cost Efficiency & Longest Term"
                },
                "BUY 16-DAY KEY ($7.50)",
                true
        ));

        scroll.addView(content);
        layout.addView(scroll);
        root.addView(layout);

        setContentView(root);
    }

    private View createPlanCard(
            final String planTitle,
            final String duration,
            final String price,
            final String badgeText,
            final String[] features,
            final String actionText,
            final boolean isFeatured
    ) {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.bottomMargin = dp(16);
        card.setLayoutParams(cardLp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(AppColors.MAGENTA_CARD);
        cardBg.setCornerRadius(dp(10));
        if (isFeatured) {
            cardBg.setStroke(dp(1), AppColors.MAGENTA_PRIMARY);
        } else {
            cardBg.setStroke(dp(1), AppColors.MAGENTA_CARD_BORDER);
        }
        card.setBackground(cardBg);

        // Header Row: Title and Price Badge
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        titleCol.setOrientation(LinearLayout.VERTICAL);

        if (badgeText != null) {
            TextView badge = new TextView(this);
            badge.setText(badgeText);
            badge.setTextColor(AppColors.MAGENTA_LIGHT);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setPadding(0, 0, 0, dp(2));
            titleCol.addView(badge);
        }

        TextView tvTitle = new TextView(this);
        tvTitle.setText(planTitle);
        tvTitle.setTextColor(AppColors.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleCol.addView(tvTitle);

        TextView tvDuration = new TextView(this);
        tvDuration.setText(duration);
        tvDuration.setTextColor(AppColors.MAGENTA_TEXT_DIM);
        tvDuration.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        titleCol.addView(tvDuration);

        headerRow.addView(titleCol);

        // Price Badge
        TextView tvPrice = new TextView(this);
        tvPrice.setText(price);
        tvPrice.setTextColor(AppColors.WHITE);
        tvPrice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvPrice.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvPrice.setPadding(dp(12), dp(6), dp(12), dp(6));

        GradientDrawable priceBg = new GradientDrawable();
        priceBg.setColor(AppColors.MAGENTA_CHIP);
        priceBg.setCornerRadius(dp(6));
        tvPrice.setBackground(priceBg);
        headerRow.addView(tvPrice);

        card.addView(headerRow);

        // Feature list
        LinearLayout featureList = new LinearLayout(this);
        featureList.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        featureList.setOrientation(LinearLayout.VERTICAL);
        featureList.setPadding(0, dp(12), 0, dp(14));

        for (String feat : features) {
            TextView tvFeat = new TextView(this);
            tvFeat.setText("•  " + feat);
            tvFeat.setTextColor(AppColors.TEXT_PRIMARY);
            tvFeat.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvFeat.setPadding(0, dp(2), 0, dp(2));
            featureList.addView(tvFeat);
        }
        card.addView(featureList);

        // Action Button
        Button buyBtn = new Button(this);
        buyBtn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        buyBtn.setText(actionText);
        buyBtn.setTextColor(AppColors.WHITE);
        buyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        buyBtn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppColors.MAGENTA_BTN_START, AppColors.MAGENTA_BTN_END}
        );
        btnBg.setCornerRadius(dp(6));
        buyBtn.setBackground(btnBg);

        buyBtn.setOnClickListener(v -> showPurchaseDialog(planTitle, price));
        card.addView(buyBtn);

        return card;
    }

    private void showPurchaseDialog(final String planTitle, final String price) {
        final String orderDetails = "Order Request: " + planTitle + " (" + price + ")\nClient: NINMODS Android Suite\nTimestamp: " + System.currentTimeMillis();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Purchase " + planTitle);
        builder.setMessage("Selected Plan: " + planTitle + "\nPrice: " + price + "\n\nAvailable Payment Methods:\n- Crypto (USDT, BTC, LTC)\n- Mobile Banking (bKash, Nagad)\n- Cards & International\n\nContact our official Telegram support to complete your order and receive your instant VIP activation key.");

        builder.setPositiveButton("Contact Telegram", (dialog, which) -> {
            try {
                Intent telegramIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_SUPPORT_URL));
                startActivity(telegramIntent);
            } catch (Exception e) {
                Toast.makeText(PricingActivity.this, "Opening Telegram: " + TELEGRAM_SUPPORT_URL, Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNeutralButton("Copy Details", (dialog, which) -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("NINMODS Order", orderDetails);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(PricingActivity.this, "Order details copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
