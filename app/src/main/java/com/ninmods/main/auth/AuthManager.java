package com.ninmods.main.auth;

import android.content.Context;
import com.ninmods.main.core.Prefs;

/**
 * Session State & Authentication Persistence Handler.
 * Professional Multi-Account Session Architecture:
 * - Each account has an isolated session store (scoped by userId / username).
 * - Multi-account data isolation guarantees zero cross-account credential, license, or profile leakage.
 * - Online verification strictly via AuthApiController.
 */
public final class AuthManager {

    private static final String PREF_KEY_AUTH_STATUS = "auth_logged_in";
    private static final String PREF_KEY_ACTIVE_TOKEN = "auth_session_token";
    private static final String PREF_KEY_ACTIVE_KEY = "active_license_key";
    private static final String PREF_ACTIVE_USER_ID = "active_session_user_id";
    private static final String PREF_ACTIVE_USERNAME = "active_session_username";

    private AuthManager() {}

    /**
     * Returns the unique session scope prefix for the current active account.
     * e.g., "session_u12_" or "session_u_alpha_"
     */
    public static String getActiveUserScope(Context context) {
        if (context == null) return "session_guest_";
        int userId = Prefs.with(context).readInt(PREF_ACTIVE_USER_ID, 0);
        if (userId <= 0) {
            userId = Prefs.with(context).readInt("auth_user_id", 0);
        }
        if (userId > 0) {
            return "session_u" + userId + "_";
        }
        String username = Prefs.with(context).readString(PREF_ACTIVE_USERNAME, "");
        if (username.isEmpty()) {
            username = Prefs.with(context).readString("active_username", "");
        }
        if (!username.isEmpty()) {
            return "session_u_" + username.toLowerCase().replaceAll("[^a-zA-Z0-9_]", "_") + "_";
        }
        return "session_guest_";
    }

    public static int getActiveUserId(Context context) {
        if (context == null) return 0;
        int userId = Prefs.with(context).readInt(PREF_ACTIVE_USER_ID, 0);
        if (userId <= 0) {
            userId = Prefs.with(context).readInt("auth_user_id", 0);
        }
        return userId;
    }

    /**
     * Saves a full account session with complete isolation.
     * Any previous session data for another user remains completely separated in its own scope.
     */
    public static void saveUserSession(
            Context context,
            int userId,
            String username,
            String email,
            String phone,
            String role,
            String password,
            float balance,
            String currency,
            String sessionToken,
            String apiKey,
            String licenseKey,
            String durationType,
            String expiresAt,
            int remainingSec
    ) {
        if (context == null) return;

        // Set the active account session pointer
        Prefs.with(context).writeInt(PREF_ACTIVE_USER_ID, userId);
        Prefs.with(context).writeString(PREF_ACTIVE_USERNAME, username != null ? username : "");
        Prefs.with(context).writeBoolean(PREF_KEY_AUTH_STATUS, true);

        String scope = getActiveUserScope(context);

        // Store scoped session attributes
        Prefs.with(context).writeBoolean(scope + "logged_in", true);
        Prefs.with(context).writeInt(scope + "user_id", userId);
        Prefs.with(context).writeString(scope + "username", username != null ? username : "");
        Prefs.with(context).writeString(scope + "email", email != null ? email : "");
        Prefs.with(context).writeString(scope + "phone", phone != null ? phone : "");
        Prefs.with(context).writeString(scope + "role", role != null ? role : "");
        Prefs.with(context).writeString(scope + "password", password != null ? password : "");
        Prefs.with(context).writeFloat(scope + "balance", balance);
        Prefs.with(context).writeString(scope + "currency", currency != null ? currency : "BDT");
        Prefs.with(context).writeString(scope + "session_token", sessionToken != null ? sessionToken : "");
        Prefs.with(context).writeString(scope + "api_key", apiKey != null ? apiKey : "");

        long now = System.currentTimeMillis();
        long expiresAtMs = (remainingSec > 0) ? (now + (remainingSec * 1000L)) : 0;

        if (licenseKey != null && !licenseKey.trim().isEmpty() && remainingSec > 0) {
            Prefs.with(context).writeString(scope + "license_key", licenseKey.trim());
            Prefs.with(context).writeString(scope + "license_duration_type", durationType != null ? durationType : "");
            Prefs.with(context).writeString(scope + "license_expires_at", expiresAt != null ? expiresAt : "");
            Prefs.with(context).writeInt(scope + "license_remaining_sec", remainingSec);
            Prefs.with(context).writeLong(scope + "license_saved_at_ms", now);
            Prefs.with(context).writeLong(scope + "license_expires_at_ms", expiresAtMs);
        } else {
            // Preserve existing valid license if already saved on this account
            String existingKey = Prefs.with(context).readString(scope + "license_key", "");
            if (existingKey.isEmpty()) {
                existingKey = Prefs.with(context).readString(scope + "saved_license_key", "");
            }
            if (existingKey.isEmpty() && userId > 0) {
                existingKey = Prefs.with(context).readString("saved_license_key_user_" + userId, "");
            }
            long existingRemaining = getRemainingSeconds(context);
            if (existingKey != null && !existingKey.trim().isEmpty() && existingRemaining > 0) {
                // Key is already active and valid for this account, keep it intact
                Prefs.with(context).writeString(scope + "license_key", existingKey.trim());
            } else {
                // Truly un-licensed account
                Prefs.with(context).writeString(scope + "license_key", "");
                Prefs.with(context).writeString(scope + "license_duration_type", "");
                Prefs.with(context).writeString(scope + "license_expires_at", "");
                Prefs.with(context).writeInt(scope + "license_remaining_sec", 0);
                Prefs.with(context).writeLong(scope + "license_saved_at_ms", 0);
                Prefs.with(context).writeLong(scope + "license_expires_at_ms", 0);
            }
        }

        // Synchronize active compatibility keys strictly for this active user
        syncActiveKeysFromScope(context, scope);
    }

    /**
     * Synchronizes active getters from the active user's scoped session.
     */
    private static void syncActiveKeysFromScope(Context context, String scope) {
        if (context == null) return;
        Prefs.with(context).writeInt("auth_user_id", Prefs.with(context).readInt(scope + "user_id", 0));
        Prefs.with(context).writeString("active_username", Prefs.with(context).readString(scope + "username", ""));
        Prefs.with(context).writeString("active_email", Prefs.with(context).readString(scope + "email", ""));
        Prefs.with(context).writeString("active_phone", Prefs.with(context).readString(scope + "phone", ""));
        Prefs.with(context).writeString("active_role", Prefs.with(context).readString(scope + "role", ""));
        Prefs.with(context).writeString("active_password", Prefs.with(context).readString(scope + "password", ""));
        Prefs.with(context).writeFloat("auth_balance", Prefs.with(context).readFloat(scope + "balance", 0.0f));
        Prefs.with(context).writeString("auth_currency", Prefs.with(context).readString(scope + "currency", "BDT"));
        Prefs.with(context).writeString(PREF_KEY_ACTIVE_TOKEN, Prefs.with(context).readString(scope + "session_token", ""));
        Prefs.with(context).writeString("auth_api_key", Prefs.with(context).readString(scope + "api_key", ""));

        // License sync
        String lKey = Prefs.with(context).readString(scope + "license_key", "");
        Prefs.with(context).writeString(PREF_KEY_ACTIVE_KEY, lKey);
        Prefs.with(context).writeString("license_duration_type", Prefs.with(context).readString(scope + "license_duration_type", ""));
        Prefs.with(context).writeString("license_expires_at", Prefs.with(context).readString(scope + "license_expires_at", ""));
        Prefs.with(context).writeInt("license_remaining_sec", Prefs.with(context).readInt(scope + "license_remaining_sec", 0));
        Prefs.with(context).writeLong("license_saved_at_ms", Prefs.with(context).readLong(scope + "license_saved_at_ms", 0));
        Prefs.with(context).writeLong("license_expires_at_ms", Prefs.with(context).readLong(scope + "license_expires_at_ms", 0));
    }

    public static void updateActiveLicense(Context context, String key, String durationType, String expiresAt, int remainingSec) {
        if (context == null) return;
        String scope = getActiveUserScope(context);
        long now = System.currentTimeMillis();
        long expiresAtMs = now + (remainingSec * 1000L);

        Prefs.with(context).writeString(scope + "license_key", key != null ? key.trim() : "");
        Prefs.with(context).writeString(scope + "license_duration_type", durationType != null ? durationType : "30_days");
        Prefs.with(context).writeString(scope + "license_expires_at", expiresAt != null ? expiresAt : "");
        Prefs.with(context).writeInt(scope + "license_remaining_sec", remainingSec);
        Prefs.with(context).writeLong(scope + "license_saved_at_ms", now);
        Prefs.with(context).writeLong(scope + "license_expires_at_ms", expiresAtMs);

        // Also update active legacy keys
        Prefs.with(context).writeString(PREF_KEY_ACTIVE_KEY, key != null ? key.trim() : "");
        Prefs.with(context).writeString("license_duration_type", durationType != null ? durationType : "30_days");
        Prefs.with(context).writeString("license_expires_at", expiresAt != null ? expiresAt : "");
        Prefs.with(context).writeInt("license_remaining_sec", remainingSec);
        Prefs.with(context).writeLong("license_saved_at_ms", now);
        Prefs.with(context).writeLong("license_expires_at_ms", expiresAtMs);
    }

    public static void setRememberedKey(Context context, String key, boolean remember) {
        if (context == null) return;
        String scope = getActiveUserScope(context);
        int userId = getActiveUserId(context);

        Prefs.with(context).writeString(scope + "saved_license_key", remember && key != null ? key.trim() : "");
        Prefs.with(context).writeBoolean(scope + "remember_key", remember);

        if (userId > 0) {
            Prefs.with(context).writeString("saved_license_key_user_" + userId, remember && key != null ? key.trim() : "");
            Prefs.with(context).writeBoolean("remember_license_key_user_" + userId, remember);
        }
    }

    public static String getRememberedKey(Context context) {
        if (context == null) return "";
        String scope = getActiveUserScope(context);
        String key = Prefs.with(context).readString(scope + "saved_license_key", "");
        if (key.isEmpty()) {
            int userId = getActiveUserId(context);
            if (userId > 0) {
                key = Prefs.with(context).readString("saved_license_key_user_" + userId, "");
            }
        }
        return key;
    }

    public static boolean isKeyRemembered(Context context) {
        if (context == null) return false;
        String scope = getActiveUserScope(context);
        boolean remember = Prefs.with(context).readBoolean(scope + "remember_key", false);
        if (!remember) {
            int userId = getActiveUserId(context);
            if (userId > 0) {
                remember = Prefs.with(context).readBoolean("remember_license_key_user_" + userId, false);
            }
        }
        return remember;
    }

    public static String getActiveUsername(Context context) {
        if (context == null) return "";
        String scope = getActiveUserScope(context);
        String val = Prefs.with(context).readString(scope + "username", "");
        if (val.isEmpty()) {
            val = Prefs.with(context).readString("active_username", "");
        }
        return val;
    }

    public static String getActiveEmail(Context context) {
        if (context == null) return "";
        String scope = getActiveUserScope(context);
        String val = Prefs.with(context).readString(scope + "email", "");
        if (val.isEmpty()) {
            val = Prefs.with(context).readString("active_email", "");
        }
        return val;
    }

    public static String getActivePhone(Context context) {
        if (context == null) return "";
        String scope = getActiveUserScope(context);
        String val = Prefs.with(context).readString(scope + "phone", "");
        if (val.isEmpty()) {
            val = Prefs.with(context).readString("active_phone", "");
        }
        return val;
    }

    public static String getActiveRole(Context context) {
        if (context == null) return "";
        String scope = getActiveUserScope(context);
        String val = Prefs.with(context).readString(scope + "role", "");
        if (val.isEmpty()) {
            val = Prefs.with(context).readString("active_role", "");
        }
        return val;
    }

    public static float getActiveBalance(Context context) {
        if (context == null) return 0.0f;
        String scope = getActiveUserScope(context);
        return Prefs.with(context).readFloat(scope + "balance", Prefs.with(context).readFloat("auth_balance", 0.0f));
    }

    public static String getActiveCurrency(Context context) {
        if (context == null) return "BDT";
        String scope = getActiveUserScope(context);
        String val = Prefs.with(context).readString(scope + "currency", "");
        if (val.isEmpty()) {
            val = Prefs.with(context).readString("auth_currency", "BDT");
        }
        return val;
    }

    public static void setLoggedIn(Context context, boolean loggedIn) {
        if (context != null) {
            Prefs.with(context).writeBoolean(PREF_KEY_AUTH_STATUS, loggedIn);
            String scope = getActiveUserScope(context);
            Prefs.with(context).writeBoolean(scope + "logged_in", loggedIn);
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
            String scope = getActiveUserScope(context);
            Prefs.with(context).writeString(scope + "session_token", token);
        }
    }

    public static String getSessionToken(Context context) {
        if (context == null) return "";
        String scope = getActiveUserScope(context);
        String token = Prefs.with(context).readString(scope + "session_token", "");
        if (token.isEmpty()) {
            token = Prefs.with(context).readString(PREF_KEY_ACTIVE_TOKEN, "");
        }
        return token;
    }

    public static void setActiveLicenseKey(Context context, String key) {
        if (context != null) {
            String sanitized = key != null ? key.trim() : "";
            Prefs.with(context).writeString(PREF_KEY_ACTIVE_KEY, sanitized);
            String scope = getActiveUserScope(context);
            Prefs.with(context).writeString(scope + "license_key", sanitized);
        }
    }

    public static String getActiveLicenseKey(Context context) {
        if (context == null) return "";
        String scope = getActiveUserScope(context);
        String key = Prefs.with(context).readString(scope + "license_key", "");
        if (key.isEmpty()) {
            key = Prefs.with(context).readString(scope + "saved_license_key", "");
        }
        if (key.isEmpty()) {
            int userId = getActiveUserId(context);
            if (userId > 0) {
                key = Prefs.with(context).readString("saved_license_key_user_" + userId, "");
            }
        }
        if (key.isEmpty()) {
            key = Prefs.with(context).readString(PREF_KEY_ACTIVE_KEY, "");
        }
        if (key.isEmpty()) {
            key = getRememberedKey(context);
        }
        if (!key.isEmpty() && Prefs.with(context).readString(scope + "license_key", "").isEmpty()) {
            Prefs.with(context).writeString(scope + "license_key", key);
            Prefs.with(context).writeString(PREF_KEY_ACTIVE_KEY, key);
        }
        return key;
    }

    public static boolean hasActiveLicense(Context context) {
        if (context == null) return false;
        String key = getActiveLicenseKey(context);
        if (key == null || key.trim().isEmpty()) return false;
        long remaining = getRemainingSeconds(context);
        return remaining > 0;
    }

    public static long getRemainingSeconds(Context context) {
        if (context == null) return 0;
        String scope = getActiveUserScope(context);
        long targetMs = Prefs.with(context).readLong(scope + "license_expires_at_ms", 0);
        if (targetMs <= 0) {
            targetMs = Prefs.with(context).readLong("license_expires_at_ms", 0);
        }
        if (targetMs <= 0) {
            String expStr = Prefs.with(context).readString(scope + "license_expires_at", "");
            if (expStr.isEmpty()) {
                expStr = Prefs.with(context).readString("license_expires_at", "");
            }
            if (!expStr.isEmpty()) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
                    java.util.Date d = sdf.parse(expStr);
                    if (d != null) {
                        targetMs = d.getTime();
                        Prefs.with(context).writeLong(scope + "license_expires_at_ms", targetMs);
                        Prefs.with(context).writeLong("license_expires_at_ms", targetMs);
                    }
                } catch (Exception ignored) {
                    String[] formats = new String[]{
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        "yyyy-MM-dd'T'HH:mm:ss'Z'",
                        "yyyy-MM-dd'T'HH:mm:ss",
                        "yyyy-MM-dd HH:mm:ss",
                        "yyyy-MM-dd"
                    };
                    for (String fmt : formats) {
                        try {
                            java.text.SimpleDateFormat isoSdf = new java.text.SimpleDateFormat(fmt, java.util.Locale.US);
                            if (fmt.contains("Z")) isoSdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                            java.util.Date d = isoSdf.parse(expStr);
                            if (d != null) {
                                targetMs = d.getTime();
                                Prefs.with(context).writeLong(scope + "license_expires_at_ms", targetMs);
                                Prefs.with(context).writeLong("license_expires_at_ms", targetMs);
                                break;
                            }
                        } catch (Exception ignoredFmt) {}
                    }
                }
            }
        }

        if (targetMs <= 0) {
            int baseSec = Prefs.with(context).readInt(scope + "license_remaining_sec", 0);
            if (baseSec <= 0) baseSec = Prefs.with(context).readInt("license_remaining_sec", 0);
            long savedAt = Prefs.with(context).readLong(scope + "license_saved_at_ms", 0);
            if (savedAt <= 0) savedAt = Prefs.with(context).readLong("license_saved_at_ms", 0);
            if (baseSec <= 0) return 0;
            if (savedAt <= 0) {
                savedAt = System.currentTimeMillis();
                Prefs.with(context).writeLong(scope + "license_saved_at_ms", savedAt);
                Prefs.with(context).writeLong("license_saved_at_ms", savedAt);
            }
            targetMs = savedAt + (baseSec * 1000L);
            Prefs.with(context).writeLong(scope + "license_expires_at_ms", targetMs);
            Prefs.with(context).writeLong("license_expires_at_ms", targetMs);
        }

        long now = System.currentTimeMillis();
        return Math.max(0, (targetMs - now) / 1000L);
    }

    public static long[] getRemainingTimeComponents(long remainingSec) {
        if (remainingSec <= 0) {
            return new long[]{0, 0, 0, 0};
        }
        long days = remainingSec / 86400;
        long hours = (remainingSec % 86400) / 3600;
        long minutes = (remainingSec % 3600) / 60;
        long seconds = remainingSec % 60;
        return new long[]{days, hours, minutes, seconds};
    }

    public static String formatRemainingTime(long remainingSec) {
        if (remainingSec <= 0) return "Expired / No Active Plan";
        long[] c = getRemainingTimeComponents(remainingSec);
        return String.format(java.util.Locale.US, "%02dd : %02dh : %02dm : %02ds", c[0], c[1], c[2], c[3]);
    }

    public static String getActivePlanName(Context context) {
        if (context == null) return "No Active Plan";
        String scope = getActiveUserScope(context);
        String dt = Prefs.with(context).readString(scope + "license_duration_type", "");
        if (dt.isEmpty()) {
            dt = Prefs.with(context).readString("license_duration_type", "");
        }
        if (dt.contains("30") || dt.contains("monthly")) return "Monthly Membership (30 Days)";
        if (dt.contains("7") || dt.contains("week")) return "Weekly Pass (7 Days)";
        if (dt.contains("1_day") || dt.contains("daily")) return "Daily Pass (24 Hours)";
        if (dt.contains("1_hour") || dt.contains("trial")) return "Trial Pass (1 Hour)";
        if (dt.contains("lifetime")) return "Lifetime Access";
        if (!dt.isEmpty()) return dt.replace("_", " ").toUpperCase() + " Plan";
        return hasActiveLicense(context) ? "Active License" : "No Active License";
    }

    public static String getExpiryDate(Context context) {
        if (context == null) return "N/A";
        String scope = getActiveUserScope(context);
        String exp = Prefs.with(context).readString(scope + "license_expires_at", "");
        if (exp.isEmpty()) {
            exp = Prefs.with(context).readString("license_expires_at", "");
        }
        if (!exp.trim().isEmpty()) {
            return exp;
        }
        long sec = getRemainingSeconds(context);
        if (sec > 0) {
            long targetMs = System.currentTimeMillis() + (sec * 1000L);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
            return sdf.format(new java.util.Date(targetMs));
        }
        return "N/A";
    }

    public static void clearSession(Context context) {
        if (context != null) {
            String scope = getActiveUserScope(context);

            // Mark this specific session logged out
            Prefs.with(context).writeBoolean(scope + "logged_in", false);

            // Clear global active session pointers
            Prefs.with(context).writeBoolean(PREF_KEY_AUTH_STATUS, false);
            Prefs.with(context).writeInt(PREF_ACTIVE_USER_ID, 0);
            Prefs.with(context).writeString(PREF_ACTIVE_USERNAME, "");
            Prefs.with(context).writeString(PREF_KEY_ACTIVE_TOKEN, "");
            Prefs.with(context).writeString(PREF_KEY_ACTIVE_KEY, "");
            Prefs.with(context).writeInt("license_remaining_sec", 0);
            Prefs.with(context).writeLong("license_saved_at_ms", 0);
            Prefs.with(context).writeLong("license_expires_at_ms", 0);
            Prefs.with(context).writeString("license_expires_at", "");
            Prefs.with(context).writeString("license_duration_type", "");

            Prefs.with(context).writeString("active_username", "");
            Prefs.with(context).writeString("active_email", "");
            Prefs.with(context).writeString("active_phone", "");
            Prefs.with(context).writeString("active_role", "");
            Prefs.with(context).writeString("active_password", "");
            Prefs.with(context).writeFloat("auth_balance", 0.0f);
            Prefs.with(context).writeString("auth_currency", "BDT");
            Prefs.with(context).writeString("auth_api_key", "");
            Prefs.with(context).writeInt("auth_user_id", 0);

            try {
                SecurityVault.resetNativeSession();
            } catch (Throwable ignored) {}
        }
    }
}
