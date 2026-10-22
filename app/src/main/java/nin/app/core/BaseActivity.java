package nin.app.core;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import nin.app.R;
import nin.app.ui.FooterView;
import nin.app.ui.ParticleNetworkView;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureBaseWrapper();
    }

    private void ensureBaseWrapper() {
        if (baseRoot == null) {
            LayoutInflater inflater = LayoutInflater.from(this);
            baseRoot = (FrameLayout) inflater.inflate(R.layout.activity_base, null);
            particleBg = baseRoot.findViewById(R.id.particleBg);
            contentContainer = baseRoot.findViewById(R.id.contentContainer);
            universalFooter = baseRoot.findViewById(R.id.universalFooter);

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

    private android.graphics.RenderEffect cachedModalBlur = null;

    /**
     * Applies full-screen hardware-accelerated blur across the entire activity layout
     * (background, content, and footer) when modal popups/dialogs are displayed.
     * Clears instantly when dismissed.
     */
    public void setScreenBlur(boolean enable) {
        if (baseRoot == null) return;
        if (Build.VERSION.SDK_INT >= 31) { // Android 12+ (Build.VERSION_CODES.S)
            if (enable) {
                if (cachedModalBlur == null) {
                    cachedModalBlur = android.graphics.RenderEffect.createBlurEffect(
                            18.0f, 18.0f, android.graphics.Shader.TileMode.CLAMP
                    );
                }
                baseRoot.setRenderEffect(cachedModalBlur);
            } else {
                baseRoot.setRenderEffect(null);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
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
