package nin.app.menu;

import android.content.Context;
import nin.app.overlay.Floater;

/**
 * Universal contract for modular tactical menu projects.
 * Enforces strict isolation: each brand or menu implementation resides
 * in its own dedicated package folder.
 */
public interface IMenuProject {

    /**
     * Unique alphanumeric identifier for this menu project (e.g., "nin", "bull", "cross", "delta").
     */
    String getId();

    /**
     * Human-readable display title, loaded dynamically for white-label compatibility.
     */
    String getDisplayName(Context context);

    /**
     * Edition or badge subtitle.
     */
    String getSubtitle(Context context);

    /**
     * Brand icon resource identifier.
     */
    int getIconResId();

    /**
     * Primary theme color for brand buttons, strokes, and dialog highlights.
     */
    int getThemeColor();

    /**
     * Launch the tactical floating menu overlay on screen.
     */
    void launch(Context context);

    /**
     * Stop and dismiss the tactical floating menu overlay.
     */
    void stop(Context context);

    /**
     * Check if this menu overlay is currently active and attached.
     */
    boolean isRunning();

    /**
     * Populate brand-specific controls, switches, seekbars, and buttons for this brand's menu.
     */
    void configureBrandOptions(Floater floater, Context context);
}
