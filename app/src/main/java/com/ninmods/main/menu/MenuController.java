import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
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
 *       same standardized lifecycle across every brand UI (NIN, NINLITE, Bull, Cross, Delta).
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
     * Universal Official Drag & Movement Listener with Screen Boundary Clamping.
     * Prevents the floating menu or icon from ever being lost off-screen.
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
                if (params == null || wm == null) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = event.getRawX();
                        touchStartY = event.getRawY();
                        initialX = params.x;
                        initialY = params.y;
                        isMoving = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - touchStartX;
                        float dy = event.getRawY() - touchStartY;
                        float moveDist = (float) Math.hypot(dx, dy);

                        if (moveDist > dp(6) || isMoving) {
                            isMoving = true;
                            if (rootFrame != null) rootFrame.setAlpha(0.70f);

                            // Clamp within screen boundaries
                            DisplayMetrics dm = v.getContext().getResources().getDisplayMetrics();
                            int viewW = v.getWidth() > 0 ? v.getWidth() : dp(50);
                            int viewH = v.getHeight() > 0 ? v.getHeight() : dp(50);

                            int newX = initialX + (int) dx;
                            int newY = initialY + (int) dy;

                            params.x = Math.max(0, Math.min(newX, dm.widthPixels - viewW));
                            params.y = Math.max(0, Math.min(newY, dm.heightPixels - viewH));

                            try {
                                wm.updateViewLayout(rootFrame, params);
                            } catch (Throwable ignored) {}
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (rootFrame != null) rootFrame.setAlpha(1.0f);
                        if (!isMoving) {
                            // Tap event
                            if (collapsedView != null && collapsedView.getVisibility() == View.VISIBLE) {
                                if (onExpand != null) {
                                    onExpand.run();
                                } else {
                                    collapsedView.setVisibility(View.GONE);
                                    if (expandedView != null) expandedView.setVisibility(View.VISIBLE);
                                }
                            } else if (expandedView != null && expandedView.getVisibility() == View.VISIBLE) {
                                if (onCollapse != null) {
                                    onCollapse.run();
                                }
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
                FeatureSettings.setSwitchState(cat, isOn);
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
                FeatureSettings.setSliderValue(cat, value);
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

    // =========================================================================
    // State Query Helpers (Restores state safely from Engine / Preferences)
    // =========================================================================

    public boolean getToggleState(Context context, int featNum, String featName, boolean defaultVal) {
        Context ctx = context != null ? context : mContext;
        if (ctx != null) MenuPreferences.context = ctx;

        // Check if engine already has a live state
        try {
            if (featNum > 0) {
                FeatureCatalog cat = FeatureCatalog.byNativeId(featNum);
                if (cat != null) {
                    return FeatureSettings.getSwitchState(cat);
                }
            }
        } catch (Throwable ignored) {}

        return MenuPreferences.loadPrefBool(featName, featNum, defaultVal);
    }

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
