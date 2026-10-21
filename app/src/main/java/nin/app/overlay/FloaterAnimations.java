package nin.app.overlay;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import nin.app.graphics.ParticleView;
import nin.app.graphics.SnowfallView;

public final class FloaterAnimations {

    private FloaterAnimations() {}

    public static void animateMenuOpen(final View collapsedView, final View menuView, final SnowfallView snowView) {
        if (collapsedView != null) collapsedView.setVisibility(View.GONE);
        if (menuView == null) return;

        menuView.setVisibility(View.VISIBLE);
        if (snowView != null) {
            snowView.setVisibility(View.VISIBLE);
            snowView.start();
        }

        menuView.setAlpha(0f);
        menuView.setScaleX(0.95f);
        menuView.setScaleY(0.95f);
        menuView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(320)
                .setInterpolator(new OvershootInterpolator(0.8f))
                .start();
    }

    public static void animateMenuClose(final View menuView, final View collapsedView,
                                        final ParticleView particleView, final SnowfallView snowView) {
        if (menuView == null) return;

        menuView.animate()
                .alpha(0f)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(190)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (particleView != null) {
                            particleView.stopAnimation();
                            particleView.setVisibility(View.GONE);
                        }
                        menuView.setVisibility(View.GONE);
                        menuView.setAlpha(1f);
                        menuView.setScaleX(1f);
                        menuView.setScaleY(1f);

                        if (snowView != null) {
                            snowView.stop();
                            snowView.setVisibility(View.GONE);
                        }
                        if (collapsedView != null) {
                            collapsedView.setVisibility(View.VISIBLE);
                        }
                    }
                })
                .start();
    }

    public static void startLogoBreathing(final View logoView, Context context) {
        if (logoView == null) return;
        int shift = FloaterStyles.dp(context, 4);

        ObjectAnimator floatAnim = ObjectAnimator.ofFloat(logoView, "translationY", 0f, -shift, 0f);
        floatAnim.setDuration(1500);
        floatAnim.setRepeatCount(ValueAnimator.INFINITE);
        floatAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        floatAnim.start();

        ObjectAnimator glowAnim = ObjectAnimator.ofFloat(logoView, "alpha", 1f, 0.75f, 1f);
        glowAnim.setDuration(1500);
        glowAnim.setRepeatCount(ValueAnimator.INFINITE);
        glowAnim.setInterpolator(new LinearInterpolator());
        glowAnim.start();

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(logoView, "scaleX", 1f, 1.05f, 1f);
        scaleX.setDuration(1500);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleX.start();

        ObjectAnimator scaleY = ObjectAnimator.ofFloat(logoView, "scaleY", 1f, 1.05f, 1f);
        scaleY.setDuration(1500);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleY.start();
    }

    public static void startMenuLiveAnimations(final View sideBar, final View mainPanel, final View closeButton) {
        if (sideBar != null) {
            ObjectAnimator panelPulse = ObjectAnimator.ofFloat(sideBar, "alpha", 1f, 0.88f, 1f);
            panelPulse.setDuration(2400);
            panelPulse.setRepeatCount(ValueAnimator.INFINITE);
            panelPulse.setInterpolator(new LinearInterpolator());
            panelPulse.start();
        }

        if (mainPanel != null) {
            ObjectAnimator cardBreath = ObjectAnimator.ofFloat(mainPanel, "scaleX", 1f, 1.01f, 1f);
            cardBreath.setDuration(2400);
            cardBreath.setRepeatCount(ValueAnimator.INFINITE);
            cardBreath.setInterpolator(new AccelerateDecelerateInterpolator());
            cardBreath.start();

            ObjectAnimator cardBreathY = ObjectAnimator.ofFloat(mainPanel, "scaleY", 1f, 1.01f, 1f);
            cardBreathY.setDuration(2400);
            cardBreathY.setRepeatCount(ValueAnimator.INFINITE);
            cardBreathY.setInterpolator(new AccelerateDecelerateInterpolator());
            cardBreathY.start();
        }

        if (closeButton != null) {
            ObjectAnimator buttonGlow = ObjectAnimator.ofFloat(closeButton, "alpha", 1f, 0.8f, 1f);
            buttonGlow.setDuration(1700);
            buttonGlow.setRepeatCount(ValueAnimator.INFINITE);
            buttonGlow.setInterpolator(new LinearInterpolator());
            buttonGlow.start();
        }
    }

    public static ObjectAnimator startCollapsedPulse(final View logoView) {
        if (logoView == null) return null;
        ObjectAnimator animator = ObjectAnimator.ofFloat(logoView, "alpha", 1f, 0.78f, 1f);
        animator.setDuration(1700);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.start();
        return animator;
    }

    public static ValueAnimator startSidebarGlow(final View sidebar) {
        if (sidebar == null || !(sidebar.getBackground() instanceof GradientDrawable)) {
            return null;
        }
        final GradientDrawable sidebarBg = (GradientDrawable) sidebar.getBackground();
        ValueAnimator animator = ValueAnimator.ofArgb(
                nin.app.core.AppColors.SIDEBAR_GLOW_START,
                nin.app.core.AppColors.SIDEBAR_GLOW_END,
                nin.app.core.AppColors.SIDEBAR_GLOW_START
        );
        animator.setDuration(2600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                sidebarBg.setColor((int) animation.getAnimatedValue());
            }
        });
        animator.start();
        return animator;
    }
}
