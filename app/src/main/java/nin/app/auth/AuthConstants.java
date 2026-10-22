package nin.app.auth;

/**
 * Centralized Authentication & License Key Definitions.
 * 
 * NOTE: These are temporary offline development credentials as specified in plan.md.
 * In future production versions, this module will be replaced with remote backend API authentication.
 */
public final class AuthConstants {

    private AuthConstants() {}

    // Primary Development Free Credentials
    public static final String DEV_DEFAULT_USER = "admin";
    public static final String DEV_DEFAULT_PASS = "123456";

    // Alternative convenient development credentials
    public static final String DEV_ALT_USER = "free";
    public static final String DEV_ALT_PASS = "123";

    // Temporary Offline License Keys for Brand Menu
    public static final String DEV_FREE_LICENSE_KEY = "NIN-FREE-2026";
    public static final String DEV_SIMPLE_KEY = "FREE";
    public static final String DEV_NUMERIC_KEY = "123456";
}
