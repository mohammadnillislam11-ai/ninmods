package com.ninmods.main.menu;

import android.content.Context;

/**
 * Universal contract for modular tactical menu projects.
 * Enforces strict isolation: each brand menu implementation resides
 * in its own dedicated package folder with its own distinct floating UI.
 */
public interface IMenuProject {

    /**
     * Unique alphanumeric identifier for this menu project ("nin" or "ninlite").
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
     * Whether this menu overlay is actively running on screen.
     */
    boolean isRunning();
}
