package com.ninmods.main.core;

/**
 * SINGLE SOURCE OF TRUTH for every user facing feature of the overlay menus.
 *
 * <h3>Why this file exists</h3>
 * The NINLITE menu and the classic overlay menu used to carry two DIFFERENT
 * numeric id spaces for the very same engine flag:
 *
 * <pre>
 *   NINLITE "ESP Box"        -&gt; id 111 -&gt; pEspPlayer.espCaixa
 *   classic "ESP BOX (2D)"  -&gt; id 2   -&gt; pEspPlayer.espCaixa
 *   NINLITE "Silent Aim"    -&gt; id 301 -&gt; pPlayer.silen4a
 *   classic "SILENT AIM"    -&gt; id 22  -&gt; pPlayer.silen4a
 * </pre>
 *
 * Both menus wrote the SAME native field, but each kept its own UI state, so
 * toggling a flag in one menu left the other menu showing a stale value. On
 * top of that the NINLITE menu hard coded its initial check box state
 * ({@code createCircleCheckboxRow("Silent Aim Enable", 301, true)}), which is
 * exactly why so many options appeared "already on" while the engine had them
 * off.
 *
 * <h3>The contract of this catalog</h3>
 * <ul>
 *   <li>Every feature is declared EXACTLY ONCE, with the ONE native id that
 *       writes the flag.</li>
 *   <li>The default value is the value the C++ engine itself starts with
 *       ({@code BooleanClient.h}), so a fresh install shows the real engine
 *       state instead of a made up one.</li>
 *   <li>The group decides which backend kill switch applies to the feature
 *       ({@code esp_enabled} / {@code aimbot_enabled}).</li>
 * </ul>
 */
public enum FeatureCatalog {

    // ------------------------------------------------------------------ ESP
    ESP_MASTER   ("esp.master",   1,  Type.SWITCH, false, Group.ESP),
    ESP_BOX      ("esp.box",      2,  Type.SWITCH, false, Group.ESP),
    ESP_LINE     ("esp.line",     4,  Type.SWITCH, false, Group.ESP),
    ESP_HEALTH   ("esp.health",   5,  Type.SWITCH, false, Group.ESP),
    ESP_NAME     ("esp.name",     6,  Type.SWITCH, false, Group.ESP),
    ESP_DISTANCE ("esp.distance", 7,  Type.SWITCH, false, Group.ESP),
    ESP_WEAPON   ("esp.weapon",   206, Type.SWITCH, false, Group.ESP),
    ESP_THREAT_COUNT ("esp.threatCount", 207, Type.SWITCH, false, Group.ESP),
    ESP_INFO_LINE("esp.infoLine", 208, Type.SWITCH, true,  Group.ESP),

    // ------------------------------------------------------------------ AIM
    AIM_AUTOLOCK       ("aim.autolock",       20, Type.SWITCH, false, Group.AIM),
    AIM_STICKY         ("aim.sticky",         21, Type.SWITCH, false, Group.AIM),
    AIM_SILENT         ("aim.silent",         22, Type.SWITCH, false, Group.AIM),
    AIM_MAGNET         ("aim.magnet",         23, Type.SWITCH, false, Group.AIM),
    AIM_IGNORE_KNOCKED ("aim.ignoreKnocked",  24, Type.SWITCH, true,  Group.AIM),
    AIM_KILL_IN_CHEST  ("aim.killInChest",    216, Type.SWITCH, false, Group.AIM),
    AIM_FOV_CIRCLE     ("aim.fovCircle",      27, Type.SWITCH, true,  Group.AIM),

    // --------------------------------------------------------------- COMBAT
    COMBAT_NO_RECOIL   ("combat.noRecoil",   40, Type.SWITCH, false, Group.COMBAT),
    COMBAT_SPEED_HACK  ("combat.speedHack",  41, Type.SWITCH, false, Group.COMBAT),
    COMBAT_PLAYER_TO_ME("combat.playerToMe", 42, Type.SWITCH, false, Group.COMBAT),
    COMBAT_UNDERCAM    ("combat.undercam",    43, Type.SWITCH, false, Group.COMBAT),
    COMBAT_ANTICHEAT   ("combat.bypass",     44, Type.SWITCH, false, Group.COMBAT),

    // -------------------------------------------------------------- SLIDERS
    AIM_FOV            ("aim.fov",        303, Type.SLIDER,  60,  10, 180, Group.AIM),
    AIM_MAX_DISTANCE   ("aim.distance",   101, Type.SLIDER, 150,  10, 350, Group.AIM),
    AIM_LEGIT_DELAY    ("aim.legitDelay", 304, Type.SLIDER,   0,   0,  10, Group.AIM),
    AIM_SILENT_TARGET  ("aim.silentTarget",305,Type.SLIDER,   1,   0,   3, Group.AIM),
    COMBAT_GAME_SPEED  ("combat.gameSpeed",102,Type.SLIDER,   1,   1,   5, Group.COMBAT),
    ESP_BOX_STYLE      ("esp.boxStyle",   201, Type.SLIDER,   0,   0,   3, Group.ESP),
    ESP_LINE_SNAP      ("esp.lineSnap",   202, Type.SLIDER,   0,   0,   2, Group.ESP),
    ESP_TEXT_SIZE      ("esp.textSize",   209, Type.SLIDER,  15,   8,  24, Group.ESP),
    AIM_HEAD_RATE      ("aim.headRate",   214, Type.SLIDER,   0,   0,   3, Group.AIM),
    AIM_CHEST_RATE     ("aim.chestRate",  215, Type.SLIDER,   0,   0,   6, Group.AIM);

    public enum Type { SWITCH, SLIDER }

    /** Feature families that the backend can switch off for a whole game type. */
    public enum Group { ESP, AIM, COMBAT }

    private final String mKey;
    private final int mNativeId;
    private final Type mType;
    private final boolean mDefaultSwitch;
    private final int mDefaultValue;
    private final int mMin;
    private final int mMax;
    private final Group mGroup;

    FeatureCatalog(String key, int nativeId, Type type, boolean defaultSwitch, Group group) {
        this(key, nativeId, type, defaultSwitch, 0, 0, 0, group);
    }

    FeatureCatalog(String key, int nativeId, Type type, int defaultValue, int min, int max, Group group) {
        this(key, nativeId, type, false, defaultValue, min, max, group);
    }

    private FeatureCatalog(String key, int nativeId, Type type, boolean defaultSwitch,
                           int defaultValue, int min, int max, Group group) {
        this.mKey = key;
        this.mNativeId = nativeId;
        this.mType = type;
        this.mDefaultSwitch = defaultSwitch;
        this.mDefaultValue = defaultValue;
        this.mMin = min;
        this.mMax = max;
        this.mGroup = group;
    }

    /** Stable storage key. Never change it: it is the persisted preference id. */
    public String key() {
        return mKey;
    }

    /** The ONE native id that writes this flag. */
    public int nativeId() {
        return mNativeId;
    }

    public Type type() {
        return mType;
    }

    public Group group() {
        return mGroup;
    }

    /** Value the C++ engine starts with for a switch. */
    public boolean defaultSwitch() {
        return mDefaultSwitch;
    }

    /** Value the C++ engine starts with for a slider. */
    public int defaultValue() {
        return mDefaultValue;
    }

    public int min() {
        return mMin;
    }

    public int max() {
        return mMax;
    }

    public boolean isSlider() {
        return mType == Type.SLIDER;
    }

    /** Clamps a slider value into the valid range of this feature. */
    public int clamp(int value) {
        if (value < mMin) return mMin;
        if (value > mMax) return mMax;
        return value;
    }

    /**
     * The backend kill switch that applies to this feature, or {@code null}
     * when the feature is never switched off by the server.
     */
    public Group gateGroup() {
        return mGroup == Group.COMBAT ? null : mGroup;
    }

    /**
     * The catalogue entry that owns a native feature id.
     *
     * The reference mod menu uses the native id as the index of its feature
     * rows, so a menu that is built from the native feature DSL has to be
     * able to translate an id back into its feature - that is the only way
     * to find out which backend kill switch belongs to a row.
     *
     * @param nativeId the engine feature id
     * @return the feature, or {@code null} when the id is not in the catalogue
     */
    public static FeatureCatalog byNativeId(int nativeId) {
        for (FeatureCatalog feature : values()) {
            if (feature.mNativeId == nativeId) {
                return feature;
            }
        }
        return null;
    }
}
