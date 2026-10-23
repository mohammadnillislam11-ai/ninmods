package com.ninmods.main.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ninmods.main.core.AppColors;
import com.ninmods.main.core.TelegramLogUploader;

public final class FloaterFeedbackDialog {

    private static View activeDialogView = null;
    private static WindowManager activeWindowManager = null;

    private FloaterFeedbackDialog() {}

    public static void show(final Context context, final int themeAccentColor, final Runnable onExitConfirmed) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                if (wm == null) {
                    if (onExitConfirmed != null) onExitConfirmed.run();
                    return;
                }

                if (activeDialogView != null && activeWindowManager != null) {
                    try {
                        activeWindowManager.removeView(activeDialogView);
                    } catch (Exception ignored) {}
                    activeDialogView = null;
                }

                int layoutFlag;
                if (Build.VERSION.SDK_INT >= 26) {
                    layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                } else if (Build.VERSION.SDK_INT >= 24) {
                    layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
                } else {
                    layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
                }

                final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        layoutFlag,
                        WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT
                );
                params.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
                params.gravity = Gravity.CENTER;
                params.dimAmount = 0.70f;

                final FrameLayout rootOverlay = new FrameLayout(context);
                rootOverlay.setBackgroundColor(Color.parseColor("#4D000000"));

                // Centered Dialog Card
                final LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                int cardWidth = dp(context, 290);
                FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
                card.setLayoutParams(cardLp);
                card.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 16));

                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setColor(AppColors.MAGENTA_CARD);
                cardBg.setCornerRadius(dp(context, 16));
                cardBg.setStroke(dp(context, 1.5f), themeAccentColor != 0 ? themeAccentColor : AppColors.MAGENTA_CARD_BORDER);
                card.setBackground(cardBg);

                // Title
                TextView tvTitle = new TextView(context);
                tvTitle.setText("FEEDBACK & LOGS");
                tvTitle.setTextColor(AppColors.WHITE);
                tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                tvTitle.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                titleLp.bottomMargin = dp(context, 6);
                tvTitle.setLayoutParams(titleLp);
                card.addView(tvTitle);

                // Subtitle / Prompt
                TextView tvDesc = new TextView(context);
                tvDesc.setText("Leaving the session? Type your feedback below (optional). Diagnostic logs will be dispatched to the official bot.");
                tvDesc.setTextColor(AppColors.MAGENTA_TEXT_DIM);
                tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                tvDesc.setGravity(Gravity.CENTER);
                tvDesc.setLineSpacing(dp(context, 2), 1.15f);
                LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                descLp.bottomMargin = dp(context, 14);
                tvDesc.setLayoutParams(descLp);
                card.addView(tvDesc);

                // Input Field
                final EditText etFeedback = new EditText(context);
                LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 75));
                etLp.bottomMargin = dp(context, 14);
                etFeedback.setLayoutParams(etLp);
                etFeedback.setHint("Write feedback or issue report (optional)...");
                etFeedback.setHintTextColor(Color.parseColor("#66FFFFFF"));
                etFeedback.setTextColor(AppColors.WHITE);
                etFeedback.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                etFeedback.setGravity(Gravity.TOP | Gravity.START);
                etFeedback.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));

                GradientDrawable etBg = new GradientDrawable();
                etBg.setColor(Color.parseColor("#1B0B24"));
                etBg.setCornerRadius(dp(context, 8));
                etBg.setStroke(dp(context, 1), Color.parseColor("#4A1D4F"));
                etFeedback.setBackground(etBg);
                card.addView(etFeedback);

                final Runnable dismissDialog = () -> {
                    try {
                        if (rootOverlay.isAttachedToWindow()) {
                            wm.removeView(rootOverlay);
                        }
                    } catch (Exception ignored) {}
                    activeDialogView = null;
                };

                // 1. "SEND & EXIT" Button (Primary)
                Button btnSend = new Button(context);
                LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 40));
                btnSend.setLayoutParams(sendLp);
                btnSend.setText("SEND LOGS & EXIT");
                btnSend.setTextColor(AppColors.WHITE);
                btnSend.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                btnSend.setTypeface(Typeface.DEFAULT_BOLD);

                GradientDrawable sendBg = new GradientDrawable();
                sendBg.setColor(themeAccentColor != 0 ? themeAccentColor : AppColors.MAGENTA_PRIMARY);
                sendBg.setCornerRadius(dp(context, 8));
                btnSend.setBackground(sendBg);

                btnSend.setOnClickListener(v -> {
                    String feedbackText = etFeedback.getText() != null ? etFeedback.getText().toString().trim() : "";
                    Toast.makeText(context, "Sending diagnostic logs to bot...", Toast.LENGTH_SHORT).show();
                    TelegramLogUploader.sendFeedbackWithLogs(context, feedbackText, null);
                    dismissDialog.run();
                    if (onExitConfirmed != null) {
                        onExitConfirmed.run();
                    }
                });
                card.addView(btnSend);

                // 2. "JUST EXIT" Button
                Button btnJustExit = new Button(context);
                LinearLayout.LayoutParams exitLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 36));
                exitLp.topMargin = dp(context, 6);
                btnJustExit.setLayoutParams(exitLp);
                btnJustExit.setText("JUST EXIT");
                btnJustExit.setTextColor(AppColors.MAGENTA_TEXT_DIM);
                btnJustExit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                btnJustExit.setBackgroundColor(Color.TRANSPARENT);

                btnJustExit.setOnClickListener(v -> {
                    dismissDialog.run();
                    if (onExitConfirmed != null) {
                        onExitConfirmed.run();
                    }
                });
                card.addView(btnJustExit);

                // 3. "CANCEL" Button
                Button btnCancel = new Button(context);
                LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 32));
                btnCancel.setLayoutParams(cancelLp);
                btnCancel.setText("CANCEL");
                btnCancel.setTextColor(Color.parseColor("#88FFFFFF"));
                btnCancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                btnCancel.setBackgroundColor(Color.TRANSPARENT);

                btnCancel.setOnClickListener(v -> dismissDialog.run());
                card.addView(btnCancel);

                rootOverlay.addView(card);
                wm.addView(rootOverlay, params);
                activeDialogView = rootOverlay;
                activeWindowManager = wm;
            } catch (Throwable t) {
                t.printStackTrace();
                if (onExitConfirmed != null) {
                    onExitConfirmed.run();
                }
            }
        });
    }

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
