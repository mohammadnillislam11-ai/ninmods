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

    public static boolean hasActiveLicense(Context context) {
        if (context == null) return false;
        String key = getActiveLicenseKey(context);
        if (key == null || key.trim().isEmpty()) return false;
        long remaining = getRemainingSeconds(context);
        return remaining > 0;
    }

    public static long getRemainingSeconds(Context context) {
        if (context == null) return 0;
        long targetMs = Prefs.with(context).readLong("license_expires_at_ms", 0);
        if (targetMs <= 0) {
            String expStr = Prefs.with(context).readString("license_expires_at", "");
            if (!expStr.isEmpty()) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
                    java.util.Date d = sdf.parse(expStr);
                    if (d != null) {
                        targetMs = d.getTime();
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
                                Prefs.with(context).writeLong("license_expires_at_ms", targetMs);
                                break;
                            }
                        } catch (Exception ignoredFmt) {}
                    }
                }
            }
        }

        if (targetMs <= 0) {
            int baseSec = Prefs.with(context).readInt("license_remaining_sec", 0);
            long savedAt = Prefs.with(context).readLong("license_saved_at_ms", 0);
            if (baseSec <= 0) return 0;
            if (savedAt <= 0) {
                savedAt = System.currentTimeMillis();
                Prefs.with(context).writeLong("license_saved_at_ms", savedAt);
            }
            targetMs = savedAt + (baseSec * 1000L);
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
        String dt = Prefs.with(context).readString("license_duration_type", "");
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
        String exp = Prefs.with(context).readString("license_expires_at", "");
        if (exp != null && !exp.trim().isEmpty()) {
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
            int userId = Prefs.with(context).readInt("auth_user_id", 0);
            if (userId > 0) {
                Prefs.with(context).writeString("saved_license_key_user_" + userId, "");
                Prefs.with(context).writeBoolean("remember_license_key_user_" + userId, false);
            }
            Prefs.with(context).writeString("saved_license_key", "");
            Prefs.with(context).writeBoolean("remember_license_key", false);
            Prefs.with(context).writeBoolean(PREF_KEY_AUTH_STATUS, false);
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
