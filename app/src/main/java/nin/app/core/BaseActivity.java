package nin.app.core;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import nin.app.R;
import nin.app.ui.ParticleNetworkView;

/**
 * Universal BaseActivity for all screens.
 * Wraps any layout with an isolated, blurred particle network animation
 * running in the background while keeping all foreground cards, text,
 * buttons, and inputs 100% sharp and unaffected.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private FrameLayout baseRoot;
    private ParticleNetworkView particleBg;
    private FrameLayout contentContainer;

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

            if (!enableParticleBackground() && particleBg != null) {
                particleBg.setVisibility(View.GONE);
                particleBg.stop();
            }
        }
    }

    @Override
    public void setContentView(int layoutResID) {
        ensureBaseWrapper();
        contentContainer.removeAllViews();
        LayoutInflater.from(this).inflate(layoutResID, contentContainer, true);
        super.setContentView(baseRoot);
    }

    @Override
    public void setContentView(View view) {
        ensureBaseWrapper();
        contentContainer.removeAllViews();
        if (view != null) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp == null) {
                lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
            }
            contentContainer.addView(view, lp);
        }
        super.setContentView(baseRoot);
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        ensureBaseWrapper();
        contentContainer.removeAllViews();
        if (view != null) {
            contentContainer.addView(view, params);
        }
        super.setContentView(baseRoot);
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        ensureBaseWrapper();
        if (view != null) {
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
