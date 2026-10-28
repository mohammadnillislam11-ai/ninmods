package com.ninmods.main.menu;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * MENU PREFERENCE STORE
 * ---------------------
 * Official and reliable preference store for the floating mod menu system.
 *
 * Rules:
 *  - Every feature value is keyed by the decimal string of its numeric ID.
 *  - Native Changes() JNI callback is the single authoritative writer to the C++ engine.
 *  - 'loadPref' (feature index -1) controls whether saved settings are restored on launch.
 *  - All SharedPreferences access is 100% null-safe, synchronized, and exception-guarded.
 */
public class MenuPreferences {

    private static volatile SharedPreferences sSharedPreferences;
    private static volatile MenuPreferences sInstance;

    public static volatile Context context;
    public static volatile boolean loadPref = false;
    public static volatile boolean isExpanded = false;

    private static final String DEFAULT_STRING_VALUE = "";
    private static final int DEFAULT_INT_VALUE = 0;
    private static final long DEFAULT_LONG_VALUE = 0L;
    private static final boolean DEFAULT_BOOLEAN_VALUE = false;

    /**
     * Native JNI callback into C++ engine (registered in Setup.cpp).
     */
    public static native void Changes(Context context, int featNum, String featName,
                                      int value, long Lvalue, boolean isOn, String inputText);

    // =========================================================================
    // Core Reference Menu Actions (Routes directly to SharedPreferences & JNI)
    // =========================================================================

    public static void changeFeatureBool(String featureName, int featureNum, boolean bool) {
        with(context).writeBoolean(featureNum, bool);
        try {
            Changes(context, featureNum, featureName, 0, 0, bool, null);
        } catch (Throwable ignored) {}
    }

    public static void changeFeatureInt(String featureName, int featureNum, int value) {
        with(context).writeInt(featureNum, value);
        try {
            Changes(context, featureNum, featureName, value, 0, false, null);
        } catch (Throwable ignored) {}
    }

    public static void changeFeatureLong(String featureName, int featureNum, long Lvalue) {
        with(context).writeLong(String.valueOf(featureNum), Lvalue);
        try {
            Changes(context, featureNum, featureName, 0, Lvalue, false, null);
        } catch (Throwable ignored) {}
    }

    public static void changeFeatureString(String featureName, int featureNum, String inputString) {
        with(context).writeString(featureNum, inputString);
        try {
            Changes(context, featureNum, featureName, 0, 0, false, inputString);
        } catch (Throwable ignored) {}
    }

    public static boolean loadPrefBool(String featureName, int featureNum, boolean bDef) {
        boolean bool = with(context).readBoolean(featureNum, bDef);
        if (featureNum == -1) {
            loadPref = bool;
        }
        if (featureNum == -3) {
            isExpanded = bool;
        }
        if (loadPref || featureNum < 0) {
            bDef = bool;
        }
        try {
            Changes(context, featureNum, featureName, 0, 0, bDef, null);
        } catch (Throwable ignored) {}
        return bDef;
    }

    public static int loadPrefInt(String featureName, int featureNum) {
        if (loadPref) {
            int value = with(context).readInt(featureNum);
            try {
                Changes(context, featureNum, featureName, value, 0, false, null);
            } catch (Throwable ignored) {}
            return value;
        }
        return 0;
    }

    public static long loadPrefLong(String featureName, int featureNum) {
        if (loadPref) {
            long Lvalue = with(context).readLong(String.valueOf(featureNum));
            try {
                Changes(context, featureNum, featureName, 0, Lvalue, false, null);
            } catch (Throwable ignored) {}
            return Lvalue;
        }
        return 0;
    }

    public static String loadPrefString(String featureName, int featureNum) {
        if (loadPref || featureNum <= 0) {
            String text = with(context).readString(featureNum);
            try {
                Changes(context, featureNum, featureName, 0, 0, false, text);
            } catch (Throwable ignored) {}
            return text;
        }
        return "";
    }

    // =========================================================================
    // Instance Lifecycle & Safe SharedPreferences Resolution
    // =========================================================================

    private static Context resolveContext(Context ctx) {
        if (ctx != null) return ctx;
        if (context != null) return context;
        return com.ninmods.main.overlay.NativeOverlayManager.getContext();
    }

    private MenuPreferences(Context ctx) {
        Context safeCtx = resolveContext(ctx);
        if (safeCtx != null) {
            Context appCtx = safeCtx.getApplicationContext() != null ? safeCtx.getApplicationContext() : safeCtx;
            sSharedPreferences = appCtx.getSharedPreferences(
                    appCtx.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        }
    }

    private MenuPreferences(Context ctx, String preferencesName) {
        Context safeCtx = resolveContext(ctx);
        if (safeCtx != null) {
            Context appCtx = safeCtx.getApplicationContext() != null ? safeCtx.getApplicationContext() : safeCtx;
            sSharedPreferences = appCtx.getSharedPreferences(
                    preferencesName, Context.MODE_PRIVATE);
        }
    }

    public static synchronized MenuPreferences with(Context ctx) {
        if (sInstance == null || sSharedPreferences == null) {
            sInstance = new MenuPreferences(ctx);
        }
        return sInstance;
    }

    public static synchronized MenuPreferences with(Context ctx, boolean force) {
        if (force || sInstance == null) {
            sInstance = new MenuPreferences(ctx);
        }
        return sInstance;
    }

    public static synchronized MenuPreferences with(Context ctx, String name) {
        if (sInstance == null || sSharedPreferences == null) {
            sInstance = new MenuPreferences(ctx, name);
        }
        return sInstance;
    }

    public static synchronized MenuPreferences with(Context ctx, String name, boolean force) {
        if (force || sInstance == null) {
            sInstance = new MenuPreferences(ctx, name);
        }
        return sInstance;
    }

    private SharedPreferences getPrefs() {
        if (sSharedPreferences == null) {
            Context safeCtx = resolveContext(context);
            if (safeCtx != null) {
                Context appCtx = safeCtx.getApplicationContext() != null ? safeCtx.getApplicationContext() : safeCtx;
                sSharedPreferences = appCtx.getSharedPreferences(
                        appCtx.getPackageName() + "_preferences", Context.MODE_PRIVATE);
            }
        }
        return sSharedPreferences;
    }

    // =========================================================================
    // Type-Safe, Exception-Guarded Read / Write Methods
    // =========================================================================

    public boolean readBoolean(String key) {
        return readBoolean(key, DEFAULT_BOOLEAN_VALUE);
    }

    public boolean readBoolean(int key) {
        return readBoolean(String.valueOf(key), DEFAULT_BOOLEAN_VALUE);
    }

    public boolean readBoolean(int key, boolean defVal) {
        return readBoolean(String.valueOf(key), defVal);
    }

    public boolean readBoolean(String key, boolean defVal) {
        SharedPreferences p = getPrefs();
        if (p == null) return defVal;
        try {
            return p.getBoolean(key, defVal);
        } catch (Throwable t) {
            return defVal;
        }
    }

    public void writeBoolean(String key, boolean val) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(key, val).apply();
        }
    }

    public void writeBoolean(int key, boolean val) {
        writeBoolean(String.valueOf(key), val);
    }

    public int readInt(String key) {
        return readInt(key, DEFAULT_INT_VALUE);
    }

    public int readInt(int key) {
        return readInt(String.valueOf(key), DEFAULT_INT_VALUE);
    }

    public int readInt(int key, int defVal) {
        return readInt(String.valueOf(key), defVal);
    }

    public int readInt(String key, int defVal) {
        SharedPreferences p = getPrefs();
        if (p == null) return defVal;
        try {
            return p.getInt(key, defVal);
        } catch (Throwable t) {
            return defVal;
        }
    }

    public void writeInt(String key, int val) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putInt(key, val).apply();
        }
    }

    public void writeInt(int key, int val) {
        writeInt(String.valueOf(key), val);
    }

    public long readLong(String key) {
        return readLong(key, DEFAULT_LONG_VALUE);
    }

    public long readLong(int key) {
        return readLong(String.valueOf(key), DEFAULT_LONG_VALUE);
    }

    public long readLong(String key, long defVal) {
        SharedPreferences p = getPrefs();
        if (p == null) return defVal;
        try {
            return p.getLong(key, defVal);
        } catch (Throwable t) {
            return defVal;
        }
    }

    public void writeLong(String key, long val) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putLong(key, val).apply();
        }
    }

    public void writeLong(int key, long val) {
        writeLong(String.valueOf(key), val);
    }

    public String readString(String key) {
        return readString(key, DEFAULT_STRING_VALUE);
    }

    public String readString(int key) {
        return readString(String.valueOf(key), DEFAULT_STRING_VALUE);
    }

    public String readString(String key, String defVal) {
        SharedPreferences p = getPrefs();
        if (p == null) return defVal;
        try {
            String res = p.getString(key, defVal);
            return res != null ? res : defVal;
        } catch (Throwable t) {
            return defVal;
        }
    }

    public void writeString(String key, String val) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putString(key, val).apply();
        }
    }

    public void writeString(int key, String val) {
        writeString(String.valueOf(key), val);
    }

    public boolean contains(String key) {
        SharedPreferences p = getPrefs();
        return p != null && p.contains(key);
    }

    public void remove(String key) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().remove(key).apply();
        }
    }

    public void clear() {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().clear().apply();
        }
    }
}
