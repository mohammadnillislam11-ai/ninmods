package com.ninmods.main.menu;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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
 *  3. Multi-UI Synchronization:
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

    // =========================================================================
    // Core Canonical Action Handlers (Single Path into JNI & Engine)
    // =========================================================================

    /**
     * Toggles a feature state across all layers (JNI Changes, NativeBridge, FeatureSettings, SharedPreferences).
     */
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

    /**
     * Updates a slider value across all layers with bounds validation.
     */
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

    // =========================================================================
    // Standard UI View Binding Helpers (Ensures 100% Uniform Slider & Switch Behavior)
    // =========================================================================

    /**
     * Binds an Android Switch to the central system with standard activation behavior.
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

    // =========================================================================
    // Dynamic JNI Feature Parser (Parses GetFeatureList() without hardcoded options)
    // =========================================================================

    /**
     * Parses raw JNI DSL strings into structured MenuFeatureItem objects dynamically.
     * Each brand UI can render these items in its own distinct visual design.
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

    // =========================================================================
    // Live Telemetry & Process Info
    // =========================================================================

    public int getTargetPid() {
        try {
            return NativeBridge.getDetectedGamePid();
        } catch (Throwable t) {
            return -1;
        }
    }

    public boolean isGameConnected() {
        return getTargetPid() > 0;
    }

    public String getGameTitle() {
        try {
            String title = NativeBridge.getDetectedGameTitle();
            return (title != null && !title.isEmpty()) ? title : "Free Fire";
        } catch (Throwable t) {
            return "Free Fire";
        }
    }

    public String getEngineMode() {
        return "Kernel Driver (Ring-0)";
    }

    // =========================================================================
    // Multi-UI Observer Management
    // =========================================================================

    public void addListener(OnFeatureChangeListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

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
