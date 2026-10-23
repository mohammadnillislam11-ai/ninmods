package com.ninmods.main.core;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.ninmods.main.R;
import com.ninmods.main.ui.FooterView;
import com.ninmods.main.ui.ParticleNetworkView;

/**
 * Universal BaseActivity for all screens.
 * Extends standard Android Activity to remain 100% immune to theme conflicts.
 * Wraps any screen layout with an isolated, hardware-accelerated blurred
 * particle network animation running in the background while keeping all foreground
 * cards, text, buttons, and inputs 100% sharp and unaffected.
 * Automatically pins the universal FooterView permanently to the screen footer across all activities.
 */
public abstract class BaseActivity extends Activity {

    private FrameLayout baseRoot;
    private ParticleNetworkView particleBg;
    private FrameLayout contentContainer;
    private FooterView universalFooter;
    private android.widget.ImageView modalBlurOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getWindow() != null) {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
        TelegramLogUploader.init(this);
        ensureBaseWrapper();
    }

    private void ensureBaseWrapper() {
        if (baseRoot == null) {
            LayoutInflater inflater = LayoutInflater.from(this);
            baseRoot = (FrameLayout) inflater.inflate(R.layout.activity_base, null);
            particleBg = baseRoot.findViewById(R.id.particleBg);
            contentContainer = baseRoot.findViewById(R.id.contentContainer);
            universalFooter = baseRoot.findViewById(R.id.universalFooter);
            modalBlurOverlay = baseRoot.findViewById(R.id.modalBlurOverlay);

            if (!enableParticleBackground() && particleBg != null) {
                particleBg.setVisibility(View.GONE);
                particleBg.stop();
            }

            if (!enableUniversalFooter() && universalFooter != null) {
                universalFooter.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void setContentView(int layoutResID) {
        ensureBaseWrapper();
        contentContainer.removeAllViews();
        LayoutInflater.from(this).inflate(layoutResID, contentContainer, true);
        if (baseRoot.getParent() == null) {
            super.setContentView(baseRoot);
        }
    }

    @Override
    public void setContentView(View view) {
        ensureBaseWrapper();
        contentContainer.removeAllViews();
        if (view != null) {
            if (view.getParent() instanceof ViewGroup) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp == null) {
                lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
            }
            contentContainer.addView(view, lp);
        }
        if (baseRoot.getParent() == null) {
            super.setContentView(baseRoot);
        }
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        ensureBaseWrapper();
        contentContainer.removeAllViews();
        if (view != null) {
            if (view.getParent() instanceof ViewGroup) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            contentContainer.addView(view, params);
        }
        if (baseRoot.getParent() == null) {
            super.setContentView(baseRoot);
        }
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        ensureBaseWrapper();
        if (view != null) {
            if (view.getParent() instanceof ViewGroup) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            contentContainer.addView(view, params);
        }
    }

    /**
     * Subclasses can override this to disable the particle animation on specific screens.
     * Defaults to true (enabled across all screens).
     */
    protected boolean enableParticleBackground() {
        return true;
    }

    public ParticleNetworkView getParticleBackgroundView() {
        return particleBg;
    }

    public FrameLayout getContentContainer() {
        return contentContainer;
    }

    public FooterView getFooterView() {
        return universalFooter;
    }

    protected boolean enableUniversalFooter() {
        return true;
    }

    /**
     * Applies full-screen hardware-accelerated blur across the entire activity layout
     * (background, content, and footer) when modal popups/dialogs are displayed.
     * Works on ALL Android devices (API 21 to 35).
     * Clears instantly when dismissed.
     */
    public void setScreenBlur(final boolean enable) {
        runOnUiThread(() -> {
            if (enable) {
                // 1. Android 12+ (API 31+) Full-Screen Hardware RenderEffect Compositor Blur (0ms latency)
                if (contentContainer != null) {
                    com.ninmods.main.ui.BlurHelper.applyViewBlur(contentContainer, 25f);
                }
                if (particleBg != null) {
                    com.ninmods.main.ui.BlurHelper.applyViewBlur(particleBg, 28f);
                }
                if (universalFooter != null) {
                    com.ninmods.main.ui.BlurHelper.applyViewBlur(universalFooter, 25f);
                }

                // 2. Translucent Frosted Acrylic Scrim across ALL Android versions (API 21-35)
                // Retains beautiful glowing pink background without blacking out
                if (modalBlurOverlay != null) {
                    modalBlurOverlay.setImageDrawable(null);
                    modalBlurOverlay.setBackgroundColor(android.graphics.Color.parseColor("#40080014"));
                    modalBlurOverlay.setVisibility(View.VISIBLE);
                    modalBlurOverlay.setAlpha(0.0f);
                    modalBlurOverlay.animate().alpha(1.0f).setDuration(120).start();
                }
            } else {
                // 1. Clear Hardware RenderEffect from foreground UI
                if (contentContainer != null) {
                    com.ninmods.main.ui.BlurHelper.clearViewBlur(contentContainer);
                }
                if (universalFooter != null) {
                    com.ninmods.main.ui.BlurHelper.clearViewBlur(universalFooter);
                }

                // 2. Restore ParticleNetworkView's built-in glowing ambient blur
                if (particleBg != null) {
                    particleBg.restoreDefaultBlur();
                }

                // 3. Fade out and clear universal overlay
                if (modalBlurOverlay != null) {
                    modalBlurOverlay.animate().alpha(0.0f).setDuration(120).withEndAction(() -> {
                        modalBlurOverlay.setVisibility(View.GONE);
                        modalBlurOverlay.setImageDrawable(null);
                        modalBlurOverlay.setBackground(null);
                    }).start();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getWindow() != null) {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
        if (particleBg != null && enableParticleBackground()) {
            particleBg.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (particleBg != null) {
            particleBg.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (particleBg != null) {
            particleBg.stop();
            particleBg = null;
        }
    }
}
