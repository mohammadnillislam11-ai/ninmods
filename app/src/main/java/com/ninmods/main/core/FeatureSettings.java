package com.ninmods.main.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.ninmods.main.auth.AuthApiController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PERSISTENT, SINGLE WRITER state store for every overlay feature.
 *
 * <h3>Why the state was broken</h3>
 * Before this class existed the feature state lived in three places that never
 * agreed with each other:
 * <ol>
 *   <li>The C++ engine (the real truth while the app runs).</li>
 *   <li>The Java menus, which showed hard coded values such as
 *       {@code createCircleCheckboxRow("Silent Aim Enable", 301, true)} -
 *       that is why so many options looked "already enabled" although the
 *       engine had them switched off.</li>
 *   <li>Nothing at all on disk: every process restart reset every flag to the
 *       C++ default.</li>
 * </ol>
 *
 * <h3>The contract of this store</h3>
 * <ul>
 *   <li><b>Read</b> the live value from the C++ engine (it is the thing that
 *       actually renders the ESP and drives the aim), fall back to the stored
 *       preference when the native library is not loaded, and fall back to the
 *       catalog default as the last resort. The menu therefore always shows
 *       what the engine really does.</li>
 *   <li><b>Write</b> to the persisted store AND to the engine in one place, so
 *       no menu can ever update one without the other.</li>
 *   <li><b>Restore</b> the persisted state into the engine when the overlay
 *       starts, so the configuration survives a restart.</li>
 * </ul>
 */
public final class FeatureSettings {

    private static final String TAG = "FeatureSettings";

    /** Notified whenever a feature changes, so every open menu can refresh. */
    public interface Listener {
        void onFeatureChanged(FeatureCatalog feature, boolean switchValue, int sliderValue);
    }

    private static final List<Listener> sListeners = new ArrayList<>();
    private static SharedPreferences sPrefs;

    /* Backend kill switches. Fail OPEN: when the server cannot be reached the
     * product must stay fully usable. */
    private static boolean sEspAllowed = true;
    private static boolean sAimAllowed = true;

    private FeatureSettings() {}

    /**
     * Prepares the store. Must be called once with an application context
     * before any feature is read or written.
     */
    public static synchronized void init(Context context) {
        if (context == null) return;
        if (sPrefs == null) {
            sPrefs = context.getApplicationContext().getSharedPreferences(
                    context.getPackageName() + "_features",
                    Context.MODE_PRIVATE);
        }
    }

    // =====================================================================
    // Reading
    // =====================================================================

    /**
     * @return the live state of a switch. The C++ engine wins, because it is
     *         the component that actually applies the feature.
     */
    public static boolean isEnabled(FeatureCatalog feature) {
        if (feature == null) return false;
        Boolean live = readLiveSwitch(feature);
        if (live != null) return live;
        Boolean stored = readStoredSwitch(feature);
        if (stored != null) return stored;
        return feature.defaultSwitch();
    }

    /**
     * @return the live value of a slider, clamped into the valid range.
     */
    public static int getIntValue(FeatureCatalog feature) {
        if (feature == null) return 0;
        Float live = readLiveSlider(feature);
        if (live != null) return feature.clamp(live.intValue());
        Integer stored = readStoredSlider(feature);
        if (stored != null) return feature.clamp(stored);
        return feature.defaultValue();
    }

    // =====================================================================
    // Writing
    // =====================================================================

    /**
     * Switches a feature on or off and keeps the menu, the engine and the
     * persisted configuration in sync.
     */
    public static void setEnabled(FeatureCatalog feature, boolean value) {
        if (feature == null || feature.isSlider()) return;
        ensureInitialised();

        if (!isAllowedByServer(feature)) {
            if (value) {
                AppLogger.log(TAG, "Refused to enable " + feature.key()
                        + ": the server has switched this feature group off.");
            }
            /* Still write the false value so a previously enabled feature is
             * actually turned off in the engine. */
            value = false;
        }

        writeStoredSwitch(feature, value);
        pushSwitchToEngine(feature, value);
        notifyChanged(feature, value, 0);

        /* Switching on an ESP sub feature has to switch the ESP master on as
         * well, otherwise nothing is rendered. This used to happen silently
         * inside the native switch statement, which made the master switch in
         * the other menu show a stale value. It is done here so the change is
         * visible, persisted and pushed to the engine exactly once. */
        if (feature.group() == FeatureCatalog.Group.ESP && feature != FeatureCatalog.ESP_MASTER) {
            if (value) {
                setEnabled(FeatureCatalog.ESP_MASTER, true);
            }
        }
    }

    /**
     * Sets a slider / dropdown value and keeps every layer in sync.
     */
    public static void setIntValue(FeatureCatalog feature, int value) {
        if (feature == null) return;
        ensureInitialised();

        int clamped = feature.clamp(value);
        if (!isAllowedByServer(feature)) {
            clamped = feature.defaultValue();
        }

        writeStoredSlider(feature, clamped);
        pushSliderToEngine(feature, clamped);
        notifyChanged(feature, false, clamped);
    }

    // =====================================================================
    // Engine synchronisation
    // =====================================================================

    /**
     * Resets every persisted feature and pushes the real default to the C++ engine.
     */
    public static void resetAllToDefaults() {
        ensureInitialised();
        if (sPrefs == null) return;
        for (FeatureCatalog feature : FeatureCatalog.values()) {
            if (feature.isSlider()) {
                writeStoredSlider(feature, feature.defaultValue());
                pushSliderToEngine(feature, feature.defaultValue());
                notifyChanged(feature, false, feature.defaultValue());
            } else {
                writeStoredSwitch(feature, feature.defaultSwitch());
                pushSwitchToEngine(feature, feature.defaultSwitch());
                notifyChanged(feature, feature.defaultSwitch(), 0);
            }
        }
    }

    /**
     * Restores the persisted configuration into the C++ engine.
     *
     * This has to run AFTER the native overlay was launched, because the engine
     * resets its flags while it boots. Only features that the user actually
     * changed before are restored - everything else keeps the engine default,
     * so a fresh install behaves exactly like the engine does.
     */
    public static void applyAllToEngine() {
        ensureInitialised();
        if (sPrefs == null) return;

        int restored = 0;
        for (FeatureCatalog feature : FeatureCatalog.values()) {
            if (feature.isSlider()) {
                Integer stored = readStoredSlider(feature);
                if (stored == null) continue;
                int clamped = feature.clamp(stored);
                pushSliderToEngine(feature, clamped);
            } else {
                Boolean stored = readStoredSwitch(feature);
                if (stored == null) continue;
                boolean value = stored;
                if (!isAllowedByServer(feature)) {
                    value = false;
                }
                pushSwitchToEngine(feature, value);
            }
            restored++;
        }
        AppLogger.log(TAG, "Restored " + restored + " persisted feature value(s) into the engine");
    }

    /**
     * Applies the backend kill switches. Features of a switched off group are
     * forced off in the engine and can no longer be switched on.
     *
     * @param espAllowed   value of {@code esp_enabled} from the backend
     * @param aimAllowed   value of {@code aimbot_enabled} from the backend
     */
    public static void applyServerGates(boolean espAllowed, boolean aimAllowed) {
        ensureInitialised();
        sEspAllowed = espAllowed;
        sAimAllowed = aimAllowed;

        for (FeatureCatalog feature : FeatureCatalog.values()) {
            if (isAllowedByServer(feature)) continue;
            if (feature.isSlider()) continue;
            if (!isEnabled(feature)) continue;
            /* The group is off on the server: the feature must be off here
             * too, otherwise the menu would show a switch that does nothing. */
            writeStoredSwitch(feature, false);
            pushSwitchToEngine(feature, false);
            notifyChanged(feature, false, 0);
        }
        AppLogger.log(TAG, "Backend feature gates applied - ESP: " + (espAllowed ? "ON" : "OFF")
                + ", AIM: " + (aimAllowed ? "ON" : "OFF"));
    }

    /** @return true when the backend allows this feature to be used. */
    public static boolean isAllowedByServer(FeatureCatalog feature) {
        if (feature == null) return true;
        FeatureCatalog.Group group = feature.gateGroup();
        if (group == null) return true;
        if (group == FeatureCatalog.Group.ESP) return sEspAllowed;
        if (group == FeatureCatalog.Group.AIM) return sAimAllowed;
        return true;
    }

    /**
     * Maps the title of the running game to the game_type key the backend
     * uses in {@code game_configs}.
     *
     * @return the game type, or an empty string when the game is unknown.
     */
    public static String gameTypeForTitle(String gameTitle) {
        if (gameTitle == null) return "";
        String title = gameTitle.toLowerCase(Locale.US);
        if (title.contains("max")) return "ff_max";
        if (title.contains("normal")) return "ff_normal";
        return "";
    }

    /**
     * Reads the global feature kill switches of the backend and applies them.
     *
     * The backend stores one global switch per game type. It was never read
     * by the client, so an administrator switching a group off in the admin
     * panel had no effect on the app at all.
     *
     * Fails OPEN: a server that cannot be reached never disables a feature.
     */
    public static void refreshServerGates(final Context context, final String gameType) {
        if (context == null || gameType == null || gameType.isEmpty()) return;
        try {
            AuthApiController.fetchGameConfig(context, gameType, new AuthApiController.GameConfigCallback() {
                @Override
                public void onSuccess(boolean espEnabled, boolean aimbotEnabled, String noticeMessage) {
                    applyServerGates(espEnabled, aimbotEnabled);
                    if (noticeMessage != null && !noticeMessage.isEmpty()) {
                        AppLogger.log(TAG, "Server notice: " + noticeMessage);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    AppLogger.log(TAG, "Keeping every feature enabled - " + errorMessage);
                }
            });
        } catch (Throwable t) {
            AppLogger.logE(TAG, "Could not read the feature configuration of the server", t);
        }
    }

    // =====================================================================
    // Listener registry (keeps the two menus in sync with each other)
    // =====================================================================

    public static synchronized void addListener(Listener listener) {
        if (listener == null) return;
        if (!sListeners.contains(listener)) sListeners.add(listener);
    }

    public static synchronized void removeListener(Listener listener) {
        if (listener == null) return;
        sListeners.remove(listener);
    }

    private static void notifyChanged(FeatureCatalog feature, boolean switchValue, int sliderValue) {
        List<Listener> snapshot;
        synchronized (FeatureSettings.class) {
            snapshot = new ArrayList<>(sListeners);
        }
        for (Listener listener : snapshot) {
            try {
                listener.onFeatureChanged(feature, switchValue, sliderValue);
            } catch (Throwable t) {
                AppLogger.logE(TAG, "Feature listener failed for " + feature.key(), t);
            }
        }
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private static void ensureInitialised() {
        if (sPrefs == null) {
            AppLogger.log(TAG, "FeatureSettings was used before init() - the store is unavailable");
        }
    }

    private static Boolean readLiveSwitch(FeatureCatalog feature) {
        try {
            return NativeBridge.getFeatureState(feature.nativeId());
        } catch (Throwable t) {
            /* The native library is not loaded yet - the stored value is used. */
            return null;
        }
    }

    private static Float readLiveSlider(FeatureCatalog feature) {
        try {
            return NativeBridge.getFeatureFloat(feature.nativeId());
        } catch (Throwable t) {
            return null;
        }
    }

    private static Boolean readStoredSwitch(FeatureCatalog feature) {
        if (sPrefs == null) return null;
        if (!sPrefs.contains(feature.key())) return null;
        return sPrefs.getBoolean(feature.key(), feature.defaultSwitch());
    }

    private static Integer readStoredSlider(FeatureCatalog feature) {
        if (sPrefs == null) return null;
        if (!sPrefs.contains(feature.key())) return null;
        return sPrefs.getInt(feature.key(), feature.defaultValue());
    }

    private static void writeStoredSwitch(FeatureCatalog feature, boolean value) {
        if (sPrefs == null) return;
        sPrefs.edit().putBoolean(feature.key(), value).apply();
    }

    private static void writeStoredSlider(FeatureCatalog feature, int value) {
        if (sPrefs == null) return;
        sPrefs.edit().putInt(feature.key(), value).apply();
    }

    private static void pushSwitchToEngine(FeatureCatalog feature, boolean value) {
        try {
            NativeBridge.setFeatureState(feature.nativeId(), value);
        } catch (Throwable t) {
            AppLogger.logE(TAG, "Could not push " + feature.key() + " into the engine", t);
        }
    }

    private static void pushSliderToEngine(FeatureCatalog feature, int value) {
        try {
            NativeBridge.setFeatureFloat(feature.nativeId(), (float) value);
        } catch (Throwable t) {
            AppLogger.logE(TAG, "Could not push " + feature.key() + " into the engine", t);
        }
    }
}
