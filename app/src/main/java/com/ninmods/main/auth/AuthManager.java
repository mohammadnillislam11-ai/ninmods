package com.ninmods.main.auth;

import android.content.Context;
import com.ninmods.main.core.Prefs;

/**
 * Session State & Authentication Persistence Handler.
 * All credentials and license verifications are strictly performed online via AuthApiController.
 * Zero offline bypasses or hardcoded validations exist in this codebase.
 */
public final class AuthManager {

    private static final String PREF_KEY_AUTH_STATUS = "auth_logged_in";
    private static final String PREF_KEY_ACTIVE_TOKEN = "auth_session_token";
    private static final String PREF_KEY_ACTIVE_KEY = "active_license_key";

    private AuthManager() {}

    public static void setLoggedIn(Context context, boolean loggedIn) {
        if (context != null) {
            Prefs.with(context).writeBoolean(PREF_KEY_AUTH_STATUS, loggedIn);
            if (!loggedIn) {
                clearSession(context);
            }
        }
    }

    public static boolean isLoggedIn(Context context) {
        if (context == null) return false;
        return Prefs.with(context).readBoolean(PREF_KEY_AUTH_STATUS, false);
    }

    public static void setSessionToken(Context context, String token) {
        if (context != null && token != null) {
            Prefs.with(context).writeString(PREF_KEY_ACTIVE_TOKEN, token);
        }
    }

    public static String getSessionToken(Context context) {
        if (context == null) return "";
        return Prefs.with(context).readString(PREF_KEY_ACTIVE_TOKEN, "");
    }

    public static void setActiveLicenseKey(Context context, String key) {
        if (context != null && key != null) {
            Prefs.with(context).writeString(PREF_KEY_ACTIVE_KEY, key);
        }
    }

    public static String getActiveLicenseKey(Context context) {
        if (context == null) return "";
        return Prefs.with(context).readString(PREF_KEY_ACTIVE_KEY, "");
    }

    public static void clearSession(Context context) {
        if (context != null) {
            Prefs.with(context).writeBoolean(PREF_KEY_AUTH_STATUS, false);
            Prefs.with(context).writeString(PREF_KEY_ACTIVE_TOKEN, "");
            Prefs.with(context).writeString(PREF_KEY_ACTIVE_KEY, "");
            Prefs.with(context).writeInt("license_remaining_sec", 0);
            Prefs.with(context).writeString("license_expires_at", "");
        }
    }
}
