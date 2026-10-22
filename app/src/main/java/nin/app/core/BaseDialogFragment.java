package nin.app.core;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.fragment.app.DialogFragment;

import nin.app.R;
import nin.app.ui.ParticleNetworkView;

/**
 * Optional BaseDialogFragment for modal dialogs and bottom sheets.
 * Injects the blurred particle network background behind dialog content.
 */
public abstract class BaseDialogFragment extends DialogFragment {

    private ParticleNetworkView particleBg;
    private FrameLayout contentContainer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View baseView = inflater.inflate(R.layout.activity_base, container, false);
        particleBg = baseView.findViewById(R.id.particleBg);
        contentContainer = baseView.findViewById(R.id.contentContainer);

        View contentView = onCreateDialogContentView(inflater, contentContainer, savedInstanceState);
        if (contentView != null) {
            contentContainer.addView(contentView);
        }

        if (!enableParticleBackground() && particleBg != null) {
            particleBg.setVisibility(View.GONE);
            particleBg.stop();
        }

        return baseView;
    }

    /**
     * Subclasses must provide the content view that appears on top of the particle background.
     */
    protected abstract View onCreateDialogContentView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState);

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
    public void onResume() {
        super.onResume();
        if (particleBg != null && enableParticleBackground()) {
            particleBg.start();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (particleBg != null) {
            particleBg.stop();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (particleBg != null) {
            particleBg.stop();
            particleBg = null;
        }
    }
}
