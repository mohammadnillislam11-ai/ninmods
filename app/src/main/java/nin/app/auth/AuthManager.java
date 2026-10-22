package nin.app.auth;

import android.content.Context;
import nin.app.core.Prefs;

/**
 * Offline Authentication & License Key Verification Handler.
 * Designed to easily interface with future backend APIs without affecting UI components.
 */
public final class AuthManager {

    private static final String PREF_KEY_AUTH_STATUS = "auth_logged_in";

    private AuthManager() {}

    /**
     * Verifies username and password against offline development credentials.
     */
    public static boolean verifyLogin(String username, String password) {
        if (username == null || password == null) return false;
        String u = username.trim();
        String p = password.trim();
        if (u.isEmpty() || p.isEmpty()) return false;

        return (u.equalsIgnoreCase(AuthConstants.DEV_DEFAULT_USER) && p.equals(AuthConstants.DEV_DEFAULT_PASS))
                || (u.equalsIgnoreCase(AuthConstants.DEV_ALT_USER) && p.equals(AuthConstants.DEV_ALT_PASS))
                || (u.equalsIgnoreCase("admin") && p.equals("admin"));
    }

    /**
     * Verifies license key against offline development keys.
     */
    public static boolean verifyLicenseKey(String key) {
        if (key == null) return false;
        String k = key.trim();
        if (k.isEmpty()) return false;

        return k.equalsIgnoreCase(AuthConstants.DEV_FREE_LICENSE_KEY)
                || k.equalsIgnoreCase(AuthConstants.DEV_SIMPLE_KEY)
                || k.equalsIgnoreCase(AuthConstants.DEV_NUMERIC_KEY);
    }

    /**
     * Registers a user in offline development mode.
     */
    public static boolean registerUser(String username, String password) {
        if (username == null || password == null) return false;
        String u = username.trim();
        String p = password.trim();
        return !u.isEmpty() && !p.isEmpty();
    }

    /**
     * Resets a user's password in offline development mode.
     */
    public static boolean resetPassword(String username, String newPassword) {
        if (username == null || newPassword == null) return false;
        String u = username.trim();
        String p = newPassword.trim();
        return !u.isEmpty() && !p.isEmpty();
    }

    public static void setLoggedIn(Context context, boolean loggedIn) {
        if (context != null) {
            Prefs.with(context).writeBoolean(PREF_KEY_AUTH_STATUS, loggedIn);
        }
    }

    public static boolean isLoggedIn(Context context) {
        if (context == null) return false;
        return Prefs.with(context).readBoolean(PREF_KEY_AUTH_STATUS, false);
    }
}
