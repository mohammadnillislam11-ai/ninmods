package nin.app.auth;

/**
 * Authentication & Network Configuration Constants.
 * All user credentials and license keys are strictly verified online via the backend API.
 * Zero offline, hardcoded, or manual fallback credentials.
 */
public final class AuthConstants {

    private AuthConstants() {}

    public static final int HTTP_CONNECT_TIMEOUT_MS = 7000;
    public static final int HTTP_READ_TIMEOUT_MS = 8000;
}
