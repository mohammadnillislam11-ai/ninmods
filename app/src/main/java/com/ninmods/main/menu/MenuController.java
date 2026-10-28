package com.ninmods.main.menu;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.ninmods.main.core.AppLogger;
import com.ninmods.main.core.FeatureCatalog;
import com.ninmods.main.core.FeatureSettings;
import com.ninmods.main.core.NativeBridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CENTRAL MENU SYSTEM & UNIFIED CONTROLLER
 * -----------------------------------------
 * The single authoritative central controller for all floating menu interactions.
 *
 * Architecture:
 *  1. Unified Activation & Action Rules:
 *     - All switch toggles, seekbar slides, and spinner selections follow the exact
 *       same standardized lifecycle across both brand UIs (NINMODS and NINLITE).
 *  2. Dynamic Options via JNI:
 *     - Feature options are NOT statically combined or hardcoded here.
 *     - Features are bound dynamically from JNI (GetFeatureList) or provided per brand/UI,
 *       and routed straight to the C++ engine via JNI Changes() and NativeBridge.
 *  3. Window & Drag Management:
 *     - Provides standard official WindowManager parameters and screen-bounded dragging.
 *  4. Multi-UI Synchronization:
 *     - Observers and listeners ensure that when any menu changes a setting,
 *       all other open menus and the engine synchronize instantaneously.
 */
public final class MenuController {

    private static final String TAG = "MenuController";
    private static volatile MenuController sInstance;

    public interface OnFeatureChangeListener {
        void onFeatureChanged(int featId, boolean state, float value);
    }

    /**
     * Dynamic feature model parsed from JNI DSL or constructed by a brand UI.
     */
    public static class MenuFeatureItem {
        public final int id;
        public final String name;
        public final String type; // "Toggle", "SeekBar", "Spinner", "Category"
        public final boolean defaultOn;
        public final int minVal;
        public final int maxVal;
        public final String[] options;
        public final String category;

        public MenuFeatureItem(int id, String name, String type, boolean defaultOn,
                               int minVal, int maxVal, String[] options, String category) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.defaultOn = defaultOn;
            this.minVal = minVal;
            this.maxVal = maxVal;
            this.options = options != null ? options : new String[0];
            this.category = category != null ? category : "GENERAL";
        }
    }

    private final List<OnFeatureChangeListener> mListeners = new CopyOnWriteArrayList<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private Context mContext;

    private MenuController() {}

    public static MenuController getInstance() {
        if (sInstance == null) {
            synchronized (MenuController.class) {
                if (sInstance == null) {
                    sInstance = new MenuController();
                }
            }
        }
        return sInstance;
    }

    public void init(Context context) {
        if (context == null) return;
        this.mContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        MenuPreferences.context = this.mContext;
        FeatureSettings.init(this.mContext);
    }

    public Context getContext() {
        return mContext;
    }

    public int dp(float dip) {
        if (mContext == null) return (int) dip;
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dip, mContext.getResources().getDisplayMetrics());
    }

    /**
     * Official WindowManager and Draggable Overlay Management.
     * Provides standard layout parameters and screen-boundary clamped drag handling.
     *
     * @param posX Initial X position on screen.
     * @param posY Initial Y position on screen.
     * @return Initialized WindowManager.LayoutParams configured for application overlay.
     */
    public WindowManager.LayoutParams createWindowLayoutParams(int posX, int posY) {
        return createWindowLayoutParams(posX, posY, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Creates standard official WindowManager parameters with custom dimensions.
     *
     * @param posX Initial X position on screen.
     * @param posY Initial Y position on screen.
     * @param width Width of the window.
     * @param height Height of the window.
     * @return Initialized WindowManager.LayoutParams configured for application overlay.
     */
    public WindowManager.LayoutParams createWindowLayoutParams(int posX, int posY, int width, int height) {
        int overlayType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = posX;
        params.y = posY;
        return params;
    }

    /**
     * Animates the expansion of the floating menu from collapsed icon to expanded card.
     * Uses smooth scale, alpha, and translation animations for an ultra-fluid experience.
     *
     * @param collapsedView The floating bubble icon view to shrink and hide.
     * @param expandedView The full menu card view to slide and expand.
     * @param params WindowManager layout parameters.
     * @param wm WindowManager service reference.
     * @param onExpand Optional callback triggered upon animation completion.
     */
    public void animateExpand(final View collapsedView, final View expandedView,
                              final WindowManager.LayoutParams params, final WindowManager wm,
                              final Runnable onExpand) {
        if (expandedView == null) return;

        expandedView.setVisibility(View.VISIBLE);
        expandedView.setAlpha(0f);
        expandedView.setScaleX(0.75f);
        expandedView.setScaleY(0.75f);
        expandedView.setTranslationX(-dp(20));

        if (collapsedView != null) {
            collapsedView.animate()
                    .alpha(0f)
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .setDuration(160)
                    .withEndAction(() -> collapsedView.setVisibility(View.GONE))
                    .start();
        }

        expandedView.animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationX(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (onExpand != null) {
                        onExpand.run();
                    }
                })
                .start();
    }

    /**
     * Animates the collapse of the floating menu back to the compact bubble icon.
     * Uses smooth reverse slide, scale, and spring overshoot for fluid feedback.
     *
     * @param collapsedView The floating bubble icon view to restore.
     * @param expandedView The full menu card view to shrink and hide.
     * @param params WindowManager layout parameters.
     * @param wm WindowManager service reference.
     * @param onCollapse Optional callback triggered upon animation completion.
     */
    public void animateCollapse(final View collapsedView, final View expandedView,
                                final WindowManager.LayoutParams params, final WindowManager wm,
                                final Runnable onCollapse) {
        if (expandedView != null) {
            expandedView.animate()
                    .alpha(0f)
                    .scaleX(0.75f)
                    .scaleY(0.75f)
                    .translationX(-dp(20))
                    .setDuration(160)
                    .withEndAction(() -> expandedView.setVisibility(View.GONE))
                    .start();
        }

        if (collapsedView != null) {
            collapsedView.setVisibility(View.VISIBLE);
            collapsedView.setAlpha(0f);
            collapsedView.setScaleX(0.5f);
            collapsedView.setScaleY(0.5f);
            collapsedView.animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(220)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .withEndAction(() -> {
                        if (onCollapse != null) {
                            onCollapse.run();
                        }
                    })
                    .start();
        }
    }

    /**
     * Central builder for modern floating logo widgets with an integrated corner close/kill button.
     * Tapping the corner close badge kills and dismisses the floating overlay completely.
     * Tapping or dragging the central bubble operates the full menu lifecycle.
     *
     * @param context Android context.
     * @param iconResId Drawable resource ID for the brand logo.
     * @param accentColor Primary theme accent color for the glowing ring.
     * @param dragListener Universal touch drag and tap listener.
     * @param onKillAction Callback executed when the close badge is clicked to kill the overlay.
     * @return Complete modern floating logo container with integrated corner kill button.
     */
    public FrameLayout buildFloatingLogoWithCloseBadge(
            final Context context,
            final int iconResId,
            final int accentColor,
            final View.OnTouchListener dragListener,
            final Runnable onKillAction) {

        final FrameLayout container = new FrameLayout(context);
        final int containerSize = dp(62);
        container.setLayoutParams(new FrameLayout.LayoutParams(containerSize, containerSize));
        container.setClipChildren(false);
        container.setClipToPadding(false);

        final int bubbleSize = dp(52);
        final FrameLayout bubble = new FrameLayout(context);
        FrameLayout.LayoutParams bubbleLp = new FrameLayout.LayoutParams(bubbleSize, bubbleSize);
        bubbleLp.gravity = Gravity.BOTTOM | Gravity.START;
        bubble.setLayoutParams(bubbleLp);

        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setShape(GradientDrawable.OVAL);
        bubbleBg.setColor(0xF00A0D14);
        bubbleBg.setStroke(dp(2.2f), accentColor);
        bubble.setBackground(bubbleBg);

        ImageView iv = new ImageView(context);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(dp(32), dp(32));
        ivLp.gravity = Gravity.CENTER;
        iv.setLayoutParams(ivLp);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            iv.setImageResource(iconResId);
        } catch (Throwable t) {
            try {
                iv.setImageResource(android.R.drawable.ic_dialog_info);
            } catch (Throwable ignored) {}
        }
        bubble.addView(iv);

        if (dragListener != null) {
            bubble.setOnTouchListener(dragListener);
        }
        container.addView(bubble);

        final FrameLayout closeBadge = new FrameLayout(context);
        final int badgeSize = dp(20);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(badgeSize, badgeSize);
        badgeLp.gravity = Gravity.TOP | Gravity.END;
        closeBadge.setLayoutParams(badgeLp);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(0xF2300A10);
        badgeBg.setStroke(dp(1.5f), 0xFFFF3B30);
        closeBadge.setBackground(badgeBg);
        closeBadge.setClickable(true);
        closeBadge.setFocusable(true);

        TextView closeIcon = new TextView(context);
        FrameLayout.LayoutParams ciLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ciLp.gravity = Gravity.CENTER;
        closeIcon.setLayoutParams(ciLp);
        closeIcon.setText("✕");
        closeIcon.setTextColor(0xFFFFFFFF);
        closeIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        closeIcon.setTypeface(Typeface.DEFAULT_BOLD);
        closeIcon.setGravity(Gravity.CENTER);
        closeBadge.addView(closeIcon);

        closeBadge.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                closeBadge.animate().scaleX(0.8f).scaleY(0.8f).setDuration(60).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                closeBadge.animate().scaleX(1.0f).scaleY(1.0f).setDuration(60).start();
                container.animate()
                        .alpha(0f)
                        .scaleX(0.3f)
                        .scaleY(0.3f)
                        .setDuration(160)
                        .withEndAction(() -> {
                            if (onKillAction != null) {
                                onKillAction.run();
                            }
                        })
                        .start();
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                closeBadge.animate().scaleX(1.0f).scaleY(1.0f).setDuration(60).start();
            }
            return true;
        });

        container.addView(closeBadge);
        return container;
    }

    /**
     * Centrally kills and dismisses the floating overlay completely.
     *
     * @param context Android context.
     * @param overlayId Overlay identifier ("nin" or "ninlite").
     * @param customCleanup Optional additional cleanup runnable.
     */
    public void killOverlay(final Context context, final String overlayId, final Runnable customCleanup) {
        mMainHandler.post(() -> {
            try {
                if (customCleanup != null) {
                    customCleanup.run();
                }
                if ("ninlite".equalsIgnoreCase(overlayId)) {
                    com.ninmods.main.menu.ninlite.NinLiteOverlayManager.stop();
                } else {
                    com.ninmods.main.overlay.NativeOverlayManager.stop();
                }
                AppLogger.log(TAG, "Floating overlay killed cleanly: " + overlayId);
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Failed to kill floating overlay: " + overlayId, t);
            }
        });
    }

    /**
     * Universal Official Drag & Movement Listener with Screen Boundary Clamping and Smooth Sliding.
     * Prevents the floating menu or icon from ever being lost off-screen and provides fluid animations.
     *
     * @param rootFrame The root overlay view container.
     * @param params Layout parameters for the floating window.
     * @param wm WindowManager instance used to update overlay coordinates.
     * @param collapsedView Collapsed floating bubble view.
     * @param expandedView Expanded full menu card view.
     * @param onExpand Action executed when expanding.
     * @param onCollapse Action executed when collapsing.
     * @return Motion touch listener configured with smooth slide and boundary snapping.
     */
    public View.OnTouchListener createDragTouchListener(
            final View rootFrame,
            final WindowManager.LayoutParams params,
            final WindowManager wm,
            final View collapsedView,
            final View expandedView,
            final Runnable onExpand,
            final Runnable onCollapse) {

        return new View.OnTouchListener() {
            private float touchStartX, touchStartY;
            private int initialX, initialY;
            private boolean isMoving = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                WindowManager.LayoutParams activeParams = params;
                WindowManager activeWm = wm;

                if (activeParams == null && rootFrame != null && rootFrame.getLayoutParams() instanceof WindowManager.LayoutParams) {
                    activeParams = (WindowManager.LayoutParams) rootFrame.getLayoutParams();
                }
                if (activeWm == null && rootFrame != null) {
                    activeWm = (WindowManager) rootFrame.getContext().getSystemService(Context.WINDOW_SERVICE);
                }
                if (activeParams == null || activeWm == null) {
                    return false;
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = event.getRawX();
                        touchStartY = event.getRawY();
                        initialX = activeParams.x;
                        initialY = activeParams.y;
                        isMoving = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - touchStartX;
                        float dy = event.getRawY() - touchStartY;
                        float moveDist = (float) Math.hypot(dx, dy);

                        if (moveDist > dp(6) || isMoving) {
                            isMoving = true;
                            if (rootFrame != null) rootFrame.setAlpha(0.70f);

                            DisplayMetrics dm = v.getContext().getResources().getDisplayMetrics();
                            int viewW = v.getWidth() > 0 ? v.getWidth() : dp(50);
                            int viewH = v.getHeight() > 0 ? v.getHeight() : dp(50);

                            int newX = initialX + (int) dx;
                            int newY = initialY + (int) dy;

                            activeParams.x = Math.max(0, Math.min(newX, dm.widthPixels - viewW));
                            activeParams.y = Math.max(0, Math.min(newY, dm.heightPixels - viewH));

                            try {
                                activeWm.updateViewLayout(rootFrame, activeParams);
                            } catch (Throwable ignored) {}
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (rootFrame != null) rootFrame.setAlpha(1.0f);
                        if (!isMoving) {
                            if (collapsedView != null && collapsedView.getVisibility() == View.VISIBLE) {
                                animateExpand(collapsedView, expandedView, activeParams, activeWm, onExpand);
                            } else if (expandedView != null && expandedView.getVisibility() == View.VISIBLE) {
                                animateCollapse(collapsedView, expandedView, activeParams, activeWm, onCollapse);
                            }
                        }
                        return true;
                }
                return false;
            }
        };
    }

    public void toggleFeature(Context context, int featNum, String featName, boolean isOn) {
        Context ctx = context != null ? context : mContext;
        if (ctx != null) MenuPreferences.context = ctx;

        // 1. Reference Mod Menu JNI Persistence & Callback
        MenuPreferences.changeFeatureBool(featName, featNum, isOn);

        // 2. Direct Native C++ Bridge
        try {
            NativeBridge.setFeatureState(featNum, isOn);
        } catch (Throwable t) {
            AppLogger.logE(TAG, "NativeBridge.setFeatureState failed: " + featNum, t);
        }

        // 3. Central FeatureSettings sync (notifies NINLITE and classic listeners)
        try {
            FeatureCatalog cat = FeatureCatalog.byNativeId(featNum);
            if (cat != null) {
                FeatureSettings.setEnabled(cat, isOn);
            }
        } catch (Throwable ignored) {}

        // 4. Notify all registered multi-UI observers
        notifyListeners(featNum, isOn, 0f);
    }

    public void setSliderValue(Context context, int featNum, String featName, int value) {
        Context ctx = context != null ? context : mContext;
        if (ctx != null) MenuPreferences.context = ctx;

        // 1. Reference Mod Menu JNI Persistence & Callback
        MenuPreferences.changeFeatureInt(featName, featNum, value);

        // 2. Direct Native C++ Bridge
        try {
            NativeBridge.setFeatureFloat(featNum, (float) value);
        } catch (Throwable t) {
            AppLogger.logE(TAG, "NativeBridge.setFeatureFloat failed: " + featNum, t);
        }

        // 3. Central FeatureSettings sync
        try {
            FeatureCatalog cat = FeatureCatalog.byNativeId(featNum);
            if (cat != null) {
                FeatureSettings.setIntValue(cat, value);
            }
        } catch (Throwable ignored) {}

        // 4. Notify all registered multi-UI observers
        notifyListeners(featNum, true, (float) value);
    }

    /**
     * Updates an option/spinner selection across all layers.
     */
    public void setOptionValue(Context context, int featNum, String featName, int optionIndex) {
        Context ctx = context != null ? context : mContext;
        if (ctx != null) MenuPreferences.context = ctx;

        // 1. Reference Mod Menu JNI Persistence & Callback
        MenuPreferences.changeFeatureInt(featName, featNum, optionIndex);

        // 2. Direct Native C++ Bridge
        try {
            NativeBridge.setFeatureFloat(featNum, (float) optionIndex);
        } catch (Throwable t) {
            AppLogger.logE(TAG, "NativeBridge.setFeatureFloat option failed: " + featNum, t);
        }

        // 3. Notify all registered multi-UI observers
        notifyListeners(featNum, true, (float) optionIndex);
    }

    /**
     * Retrieves the stored or live toggle state of a feature.
     *
     * @param context Context used to resolve shared preferences.
     * @param featNum Native feature index.
     * @param featName Feature name or key.
     * @param defaultVal Default state if no prior state was saved.
     * @return Stored or live boolean state of the feature.
     */
    public boolean getToggleState(Context context, int featNum, String featName, boolean defaultVal) {
        Context ctx = context != null ? context : mContext;
        if (ctx != null) MenuPreferences.context = ctx;

        // Check if engine already has a live state
        try {
            if (featNum > 0) {
                FeatureCatalog cat = FeatureCatalog.byNativeId(featNum);
                if (cat != null) {
                    return FeatureSettings.isEnabled(cat);
                }
            }
        } catch (Throwable ignored) {}

        return MenuPreferences.loadPrefBool(featName, featNum, defaultVal);
    }

    /**
     * Retrieves the stored or live slider value of a feature within bounds.
     *
     * @param context Context used to resolve shared preferences.
     * @param featNum Native feature index.
     * @param featName Feature name or key.
     * @param min Minimum allowable value.
     * @param max Maximum allowable value.
     * @param defaultVal Default value if unset or out of bounds.
     * @return Stored or live integer value within [min, max].
     */
    public int getSliderValue(Context context, int featNum, String featName, int min, int max, int defaultVal) {
        Context ctx = context != null ? context : mContext;
        if (ctx != null) MenuPreferences.context = ctx;

        int val = MenuPreferences.loadPrefInt(featName, featNum);
        if (val >= min && val <= max) {
            return val;
        }

        try {
            FeatureCatalog cat = FeatureCatalog.byNativeId(featNum);
            if (cat != null) {
                int engineVal = FeatureSettings.getIntValue(cat);
                if (engineVal >= min && engineVal <= max) return engineVal;
            }
        } catch (Throwable ignored) {}

        return defaultVal >= min && defaultVal <= max ? defaultVal : min;
    }

    /**
     * Retrieves the stored option selection index for a dropdown or spinner.
     *
     * @param context Context used to resolve shared preferences.
     * @param featNum Native feature index.
     * @param featName Feature name or key.
     * @param optionsCount Total number of options available.
     * @param defaultIndex Default index if unset or invalid.
     * @return Validated zero-based selection index.
     */
    public int getOptionIndex(Context context, int featNum, String featName, int optionsCount, int defaultIndex) {
        Context ctx = context != null ? context : mContext;
        if (ctx != null) MenuPreferences.context = ctx;

        int saved = MenuPreferences.loadPrefInt(featName, featNum);
        if (saved >= 0 && saved < optionsCount) {
            return saved;
        }
        return (defaultIndex >= 0 && defaultIndex < optionsCount) ? defaultIndex : 0;
    }

    /**
     * Binds an Android Switch to the central system with standardized activation behavior.
     *
     * @param sw Switch view component.
     * @param featNum Native feature identification number.
     * @param featName Human-readable feature name or key.
     * @param defaultVal Default boolean state if not configured.
     */
    public void bindSwitch(final Switch sw, final int featNum, final String featName, final boolean defaultVal) {
        if (sw == null) return;

        boolean initial = getToggleState(sw.getContext(), featNum, featName, defaultVal);
        sw.setChecked(initial);

        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            toggleFeature(buttonView.getContext(), featNum, featName, isChecked);
        });

        // Register listener for external updates from other menus
        addListener((fId, state, value) -> {
            if (fId == featNum && sw.isChecked() != state) {
                sw.setChecked(state);
            }
        });
    }

    /**
     * Binds an Android SeekBar to the central system with standardized sliding, bounds, and live label updates.
     *
     * @param seekBar SeekBar view component.
     * @param liveBadge Optional TextView displaying the real-time numeric value.
     * @param featNum Native feature identification number.
     * @param featName Human-readable feature name or key.
     * @param min Minimum allowable slider value.
     * @param max Maximum allowable slider value.
     * @param unit Optional measurement unit suffix (e.g., "%", "m").
     */
    public void bindSeekBar(final SeekBar seekBar, final TextView liveBadge, final int featNum,
                            final String featName, final int min, final int max, final String unit) {
        if (seekBar == null) return;

        seekBar.setMax(max - min);
        int current = getSliderValue(seekBar.getContext(), featNum, featName, min, max, min);
        seekBar.setProgress(current - min);

        if (liveBadge != null) {
            liveBadge.setText(current + (unit != null ? unit : ""));
        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int actual = min + progress;
                if (liveBadge != null) {
                    liveBadge.setText(actual + (unit != null ? unit : ""));
                }
                if (fromUser) {
                    setSliderValue(sb.getContext(), featNum, featName, actual);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Register listener for external updates from other menus
        addListener((fId, state, value) -> {
            if (fId == featNum) {
                int actual = (int) value;
                if (actual >= min && actual <= max && (min + seekBar.getProgress()) != actual) {
                    seekBar.setProgress(actual - min);
                    if (liveBadge != null) {
                        liveBadge.setText(actual + (unit != null ? unit : ""));
                    }
                }
            }
        });
    }

    /**
     * Binds a Spinner dropdown to the central system.
     *
     * @param spinner Spinner view component.
     * @param featNum Native feature identification number.
     * @param featName Human-readable feature name or key.
     * @param options Array of string option labels.
     * @param defaultIndex Default zero-based option index.
     */
    public void bindSpinner(final Spinner spinner, final int featNum, final String featName,
                            final String[] options, final int defaultIndex) {
        if (spinner == null || options == null || options.length == 0) return;

        int selected = getOptionIndex(spinner.getContext(), featNum, featName, options.length, defaultIndex);
        spinner.setSelection(selected, false);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position < 0 || position >= options.length) return;
                setOptionValue(parent.getContext(), featNum, featName, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Register listener for external updates from other menus
        addListener((fId, state, value) -> {
            if (fId == featNum) {
                int pos = (int) value;
                if (pos >= 0 && pos < options.length && spinner.getSelectedItemPosition() != pos) {
                    spinner.setSelection(pos, false);
                }
            }
        });
    }

    /**
     * Binds a styled Circle Checkbox (used in Cyberpunk/NINLITE & modern minimalist UIs)
     * to the central MenuController with full live synchronization.
     *
     * @param row View container for the clickable row.
     * @param circleBox Outer circular border frame.
     * @param innerDot Inner circular indicator dot.
     * @param featNum Native feature identification number.
     * @param featName Human-readable feature name or key.
     * @param defaultVal Default boolean state if not configured.
     * @param activeCircleColor ARGB color for active circle border and fill.
     * @param inactiveCircleColor ARGB color for inactive circle border.
     * @param dotBgColor ARGB color for inner indicator dot.
     */
    public void bindCircleCheckbox(final View row, final FrameLayout circleBox, final View innerDot,
                                   final int featNum, final String featName, final boolean defaultVal,
                                   final int activeCircleColor, final int inactiveCircleColor, final int dotBgColor) {
        if (row == null || circleBox == null) return;

        final Context ctx = row.getContext();
        final boolean[] state = { getToggleState(ctx, featNum, featName, defaultVal) };

        final Runnable updateUi = () -> {
            GradientDrawable circleBg = new GradientDrawable();
            circleBg.setShape(GradientDrawable.OVAL);
            circleBg.setColor(state[0] ? activeCircleColor : Color.TRANSPARENT);
            circleBg.setStroke(dp(2.2f), state[0] ? activeCircleColor : inactiveCircleColor);
            circleBox.setBackground(circleBg);

            if (innerDot != null) {
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(dotBgColor);
                innerDot.setBackground(dotBg);
                innerDot.setVisibility(state[0] ? View.VISIBLE : View.GONE);
            }
        };

        updateUi.run();

        row.setOnClickListener(v -> {
            boolean newState = !state[0];
            state[0] = newState;
            updateUi.run();
            toggleFeature(v.getContext(), featNum, featName, newState);
        });

        // Register listener for external updates from other menus
        addListener((fId, checked, val) -> {
            if (fId == featNum && state[0] != checked) {
                state[0] = checked;
                updateUi.run();
            }
        });
    }

    /**
     * Binds an Accent Slider with a live badge TextView (ensuring 100% uniform sliding rules).
     *
     * @param seekBar SeekBar view component.
     * @param liveBadge Optional TextView displaying the real-time numeric value.
     * @param featNum Native feature identification number.
     * @param featName Human-readable feature name or key.
     * @param min Minimum allowable slider value.
     * @param max Maximum allowable slider value.
     * @param unit Optional measurement unit suffix.
     */
    public void bindAccentSlider(final SeekBar seekBar, final TextView liveBadge, final int featNum,
                                 final String featName, final int min, final int max, final String unit) {
        bindSeekBar(seekBar, liveBadge, featNum, featName, min, max, unit);
    }

    /**
     * Binds a styled Dropdown Spinner with user-only selection guards.
     *
     * @param spinner Spinner view component.
     * @param featNum Native feature identification number.
     * @param featName Human-readable feature name or key.
     * @param options Array of string option labels.
     * @param defaultIndex Default zero-based option index.
     */
    public void bindDropdown(final Spinner spinner, final int featNum, final String featName,
                             final String[] options, final int defaultIndex) {
        bindSpinner(spinner, featNum, featName, options, defaultIndex);
    }

    /**
     * Parses raw JNI DSL strings into structured MenuFeatureItem objects dynamically.
     * Each brand UI can render these items in its own distinct visual design.
     *
     * @param rawDsl Array of DSL strings received from native C++ engine via GetFeatureList().
     * @return List of parsed MenuFeatureItem objects.
     */
    public static List<MenuFeatureItem> parseDsl(String[] rawDsl) {
        if (rawDsl == null || rawDsl.length == 0) {
            return Collections.emptyList();
        }

        List<MenuFeatureItem> items = new ArrayList<>();
        String currentCategory = "VISUALS";

        for (String row : rawDsl) {
            if (row == null || row.trim().isEmpty()) continue;

            if (row.startsWith("Category_")) {
                currentCategory = row.substring("Category_".length()).trim().toUpperCase();
                continue;
            }

            try {
                boolean trueByDefault = row.contains("_True");
                String cleaned = row.replaceFirst("_True", "");
                String[] tokens = cleaned.split("_");
                if (tokens.length < 2) continue;

                int featId;
                try {
                    featId = Integer.parseInt(tokens[0]);
                } catch (Exception e) {
                    continue;
                }

                String type = tokens[1];
                if ("Toggle".equalsIgnoreCase(type) && tokens.length >= 3) {
                    items.add(new MenuFeatureItem(featId, tokens[2], "Toggle", trueByDefault, 0, 0, null, currentCategory));
                } else if ("SeekBar".equalsIgnoreCase(type) && tokens.length >= 5) {
                    int min = Integer.parseInt(tokens[3]);
                    int max = Integer.parseInt(tokens[4]);
                    items.add(new MenuFeatureItem(featId, tokens[2], "SeekBar", false, min, max, null, currentCategory));
                } else if ("Spinner".equalsIgnoreCase(type) && tokens.length >= 4) {
                    String[] opts = tokens[3].split(",");
                    items.add(new MenuFeatureItem(featId, tokens[2], "Spinner", false, 0, 0, opts, currentCategory));
                }
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Error parsing DSL item: " + row, t);
            }
        }

        return items;
    }

    /**
     * Dynamically builds the menu feature list from the central FeatureCatalog.
     * Prevents hardcoded lists of features from existing in any menu UI.
     *
     * @return List of standardized MenuFeatureItem objects.
     */
    public static List<MenuFeatureItem> getFeaturesFromCatalog() {
        List<MenuFeatureItem> items = new ArrayList<>();
        for (FeatureCatalog feat : FeatureCatalog.values()) {
            String cat = "VISUALS";
            if (feat.group() == FeatureCatalog.Group.AIM) {
                cat = "AIMBOT";
            } else if (feat.group() == FeatureCatalog.Group.COMBAT) {
                cat = "COMBAT";
            }

            String type = (feat.type() == FeatureCatalog.Type.SLIDER) ? "SeekBar" : "Toggle";
            String readableName = feat.key().replace('.', ' ').toUpperCase();
            String[] options = null;

            if (feat == FeatureCatalog.ESP_BOX_STYLE) {
                type = "Spinner";
                readableName = "ESP BOX STYLE";
                options = new String[]{"STROKE", "FILLED", "CORNER", "ROUNDED"};
            } else if (feat == FeatureCatalog.ESP_LINE_SNAP) {
                type = "Spinner";
                readableName = "ESP LINE SNAP MODE";
                options = new String[]{"TOP", "CENTER", "BOTTOM"};
            } else if (feat == FeatureCatalog.ESP_MASTER) readableName = "ESP MASTER SWITCH";
            else if (feat == FeatureCatalog.ESP_BOX) readableName = "ESP BOX (2D)";
            else if (feat == FeatureCatalog.ESP_LINE) readableName = "ESP LINE (TRACER)";
            else if (feat == FeatureCatalog.ESP_HEALTH) readableName = "ESP HEALTH BAR";
            else if (feat == FeatureCatalog.ESP_NAME) readableName = "ESP PLAYER NAME";
            else if (feat == FeatureCatalog.ESP_DISTANCE) readableName = "ESP DISTANCE IN METERS";
            else if (feat == FeatureCatalog.ESP_WEAPON) readableName = "ESP WEAPON NAME";
            else if (feat == FeatureCatalog.ESP_THREAT_COUNT) readableName = "THREAT COUNTER";
            else if (feat == FeatureCatalog.ESP_INFO_LINE) readableName = "ESP INFO LINE";
            else if (feat == FeatureCatalog.AIM_AUTOLOCK) readableName = "AIM HARD (REAL AIM)";
            else if (feat == FeatureCatalog.AIM_STICKY) readableName = "AIM COLLIDER LOCK";
            else if (feat == FeatureCatalog.AIM_SILENT) readableName = "SILENT AIM (MAGIC BULLET)";
            else if (feat == FeatureCatalog.AIM_MAGNET) readableName = "AIM MAGNET V2";
            else if (feat == FeatureCatalog.AIM_IGNORE_KNOCKED) readableName = "IGNORE KNOCKED PLAYERS";
            else if (feat == FeatureCatalog.AIM_KILL_IN_CHEST) readableName = "KILL IN CHEST";
            else if (feat == FeatureCatalog.AIM_FOV_CIRCLE) readableName = "DRAW FOV CIRCLE";
            else if (feat == FeatureCatalog.COMBAT_NO_RECOIL) readableName = "NO RECOIL (100% STABLE)";
            else if (feat == FeatureCatalog.COMBAT_SPEED_HACK) readableName = "SPEED MOVEMENT (2X)";
            else if (feat == FeatureCatalog.COMBAT_PLAYER_TO_ME) readableName = "PLAYER TO ME (TELEPORT)";
            else if (feat == FeatureCatalog.COMBAT_UNDERCAM) readableName = "UNDERGROUND CAMERA";
            else if (feat == FeatureCatalog.COMBAT_ANTICHEAT) readableName = "ANTICHEAT REALTIME BYPASS";
            else if (feat == FeatureCatalog.AIM_FOV) readableName = "AIM FOV ANGLE";
            else if (feat == FeatureCatalog.AIM_MAX_DISTANCE) readableName = "MAX TARGET DISTANCE";
            else if (feat == FeatureCatalog.COMBAT_GAME_SPEED) readableName = "GAME SPEED MULTIPLIER";
            else if (feat == FeatureCatalog.ESP_TEXT_SIZE) readableName = "ESP TEXT SIZE";
            else if (feat == FeatureCatalog.AIM_HEAD_RATE) readableName = "HEAD RATE";
            else if (feat == FeatureCatalog.AIM_CHEST_RATE) readableName = "CHEST RATE";

            items.add(new MenuFeatureItem(
                    feat.nativeId(),
                    readableName,
                    type,
                    feat.defaultSwitch(),
                    feat.min(),
                    feat.max(),
                    options,
                    cat
            ));
        }
        return items;
    }

    /**
     * Resolves the target process ID of the detected game.
     *
     * @return Process identifier (PID) if detected, or -1 otherwise.
     */
    public int getTargetPid() {
        try {
            return NativeBridge.getDetectedGamePid();
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Determines whether the engine is actively connected to a target game process.
     *
     * @return True if target PID is valid and positive, false otherwise.
     */
    public boolean isGameConnected() {
        return getTargetPid() > 0;
    }

    /**
     * Retrieves the detected game title.
     *
     * @return Game title string.
     */
    public String getGameTitle() {
        try {
            String title = NativeBridge.getDetectedGameTitle();
            return (title != null && !title.isEmpty()) ? title : "Free Fire";
        } catch (Throwable t) {
            return "Free Fire";
        }
    }

    /**
     * Returns the engine communication channel description.
     *
     * @return Engine mode string.
     */
    public String getEngineMode() {
        return "Kernel Driver (Ring-0)";
    }

    /**
     * Registers a feature state change observer.
     *
     * @param listener Observer callback to add.
     */
    public void addListener(OnFeatureChangeListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    /**
     * Unregisters a feature state change observer.
     *
     * @param listener Observer callback to remove.
     */
    public void removeListener(OnFeatureChangeListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    private void notifyListeners(final int featNum, final boolean state, final float value) {
        mMainHandler.post(() -> {
            for (OnFeatureChangeListener l : mListeners) {
                try {
                    l.onFeatureChanged(featNum, state, value);
                } catch (Throwable t) {
                    AppLogger.logE(TAG, "Observer callback error: " + featNum, t);
                }
            }
        });
    }
}
