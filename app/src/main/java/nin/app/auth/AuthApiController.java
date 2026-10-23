package nin.app.auth;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nin.app.R;
import nin.app.core.Prefs;

/**
 * Production Remote API Controller.
 * Manages all authentication, registration, password reset, license verification,
 * and session validation strictly via the remote backend API.
 * 
 * BANKING-GRADE TAMPER-RESISTANCE:
 * All requests are cryptographically signed via native C++ HMAC-SHA256 (SecurityVault).
 * Verified server session tokens are bound to the native Ring 0 memory engine.
 * ZERO offline fallbacks, ZERO hardcoded credentials, ZERO manual backdoor bypasses.
 */
public final class AuthApiController {

    private static final String TAG = "AuthApiController";

    public static final String PREF_SERVER_BASE_URL = "pref_server_base_url";
    public static final String DEFAULT_SERVER_URL = "https://ninmods.smmtor.com";

    // Strict Node.js Production API Endpoints
    public static final String ENDPOINT_LOGIN = "/api/v2/auth/login";
    public static final String ENDPOINT_REGISTER = "/api/v2/auth/register";
    public static final String ENDPOINT_RESET_PASS = "/api/v2/auth/reset-password";
    public static final String ENDPOINT_VERIFY_KEY = "/api/v2/license/verify";
    public static final String ENDPOINT_HEARTBEAT = "/api/v2/app/heartbeat";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ApiCallback {
        void onSuccess(String message);
        void onError(String errorMessage);
    }

    private AuthApiController() {}

    public static String getServerBaseUrl(Context context) {
        return Prefs.with(context).readString(PREF_SERVER_BASE_URL, DEFAULT_SERVER_URL);
    }

    public static void setServerBaseUrl(Context context, String url) {
        if (context != null && url != null) {
            Prefs.with(context).writeString(PREF_SERVER_BASE_URL, url.trim());
        }
    }

    public static String getHwid(Context context) {
        try {
            return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) {
            return "UNKNOWN_HWID";
        }
    }

    /**
     * POST /api/v2/auth/login
     * Strictly verifies credentials online against the backend database.
     * Supports login via either Username or Email.
     */
    public static void login(Context context, String usernameOrEmail, String password, final ApiCallback callback) {
        SecurityVault.init(context);

        executor.execute(() -> {
            try {
                String hwid = getHwid(context);
                JSONObject payload = new JSONObject();
                payload.put("username", usernameOrEmail != null ? usernameOrEmail.trim() : "");
                payload.put("password", password != null ? password.trim() : "");
                payload.put("hwid", hwid);
                payload.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
                payload.put("androidVersion", Build.VERSION.RELEASE);
                payload.put("clientVersion", context.getString(R.string.app_version));

                String response = executePost(context, ENDPOINT_LOGIN, payload.toString(), AuthConstants.HTTP_CONNECT_TIMEOUT_MS);
                if (response != null && !response.trim().isEmpty()) {
                    JSONObject resJson = new JSONObject(response);
                    if (resJson.optBoolean("success", false) || resJson.optBoolean("status", false)) {
                        String resultMessage = resJson.optString("message", "Login successful!");
                        String apiKey = resJson.optString("api_key", "");
                        double balance = resJson.optDouble("balance", 0.0);
                        String currency = resJson.optString("currency", "BDT");
                        int userId = resJson.optInt("user_id", 0);
                        String sessionToken = resJson.optString("session_token", resJson.optString("token", apiKey));

                        Prefs.with(context).writeString("auth_api_key", apiKey);
                        Prefs.with(context).writeFloat("auth_balance", (float) balance);
                        Prefs.with(context).writeString("auth_currency", currency);
                        Prefs.with(context).writeInt("auth_user_id", userId);
                        if (!sessionToken.isEmpty()) {
                            AuthManager.setSessionToken(context, sessionToken);
                            // Bind verified session to native C++ cheat engine
                            SecurityVault.setNativeSession(sessionToken, hwid, 86400);
                        }
                        AuthManager.setLoggedIn(context, true);

                        mainHandler.post(() -> callback.onSuccess(resultMessage));
                        return;
                    } else {
                        final String errMsg = resJson.optString("error", resJson.optString("message", "Invalid username/email or password!"));
                        mainHandler.post(() -> callback.onError(errMsg));
                        return;
                    }
                }
                mainHandler.post(() -> callback.onError("Invalid response from server. Please try again."));
            } catch (Exception e) {
                Log.e(TAG, "Login request failed: " + e.getMessage());
                mainHandler.post(() -> callback.onError("Cannot connect to server. Active internet connection required."));
            }
        });
    }

    /**
     * POST /api/v2/auth/register
     * Strictly creates an account online on the backend database with full schema synchronization.
     */
    public static void register(Context context, String username, String email, String password, String refCode, final ApiCallback callback) {
        SecurityVault.init(context);

        executor.execute(() -> {
            try {
                String hwid = getHwid(context);
                JSONObject payload = new JSONObject();
                payload.put("username", username != null ? username.trim() : "");
                payload.put("email", email != null ? email.trim() : "");
                payload.put("password", password != null ? password.trim() : "");
                payload.put("password_again", password != null ? password.trim() : "");
                if (refCode != null && !refCode.trim().isEmpty()) {
                    payload.put("ref_code", refCode.trim());
                    payload.put("ref_by", refCode.trim());
                }
                payload.put("hwid", hwid);
                payload.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
                payload.put("clientVersion", context.getString(R.string.app_version));

                String response = executePost(context, ENDPOINT_REGISTER, payload.toString(), AuthConstants.HTTP_CONNECT_TIMEOUT_MS);
                if (response != null && !response.trim().isEmpty()) {
                    JSONObject resJson = new JSONObject(response);
                    if (resJson.optBoolean("success", false) || resJson.optBoolean("status", false)) {
                        String resultMessage = resJson.optString("message", "Account created successfully! Please login.");
                        mainHandler.post(() -> callback.onSuccess(resultMessage));
                        return;
                    } else {
                        final String errMsg = resJson.optString("error", resJson.optString("message", "Registration failed!"));
                        mainHandler.post(() -> callback.onError(errMsg));
                        return;
                    }
                }
                mainHandler.post(() -> callback.onError("Invalid response from registration server. Please try again."));
            } catch (Exception e) {
                Log.e(TAG, "Registration request failed: " + e.getMessage());
                mainHandler.post(() -> callback.onError("Cannot connect to registration server. Active internet connection required."));
            }
        });
    }

    /**
     * POST /api/v2/auth/reset-password
     * Strictly resets password online on the backend database.
     */
    public static void resetPassword(Context context, String usernameOrEmail, String newPassword, final ApiCallback callback) {
        SecurityVault.init(context);

        executor.execute(() -> {
            try {
                String hwid = getHwid(context);
                JSONObject payload = new JSONObject();
                payload.put("username", usernameOrEmail != null ? usernameOrEmail.trim() : "");
                payload.put("newPassword", newPassword != null ? newPassword.trim() : "");
                payload.put("password", newPassword != null ? newPassword.trim() : "");
                payload.put("hwid", hwid);

                String response = executePost(context, ENDPOINT_RESET_PASS, payload.toString(), AuthConstants.HTTP_CONNECT_TIMEOUT_MS);
                if (response != null && !response.trim().isEmpty()) {
                    JSONObject resJson = new JSONObject(response);
                    if (resJson.optBoolean("success", false) || resJson.optBoolean("status", false)) {
                        String resultMessage = resJson.optString("message", "Password reset successful! Please login.");
                        mainHandler.post(() -> callback.onSuccess(resultMessage));
                        return;
                    } else {
                        final String errMsg = resJson.optString("error", resJson.optString("message", "Password reset failed!"));
                        mainHandler.post(() -> callback.onError(errMsg));
                        return;
                    }
                }
                mainHandler.post(() -> callback.onError("Invalid response from server. Please try again."));
            } catch (Exception e) {
                Log.e(TAG, "Password reset request failed: " + e.getMessage());
                mainHandler.post(() -> callback.onError("Cannot connect to server. Active internet connection required."));
            }
        });
    }

    /**
     * POST /api/v2/license/verify
     * Strictly verifies license key online, checks HWID binding, and retrieves remaining time.
     */
    public static void verifyLicenseKey(Context context, String key, String brandId, final ApiCallback callback) {
        SecurityVault.init(context);

        executor.execute(() -> {
            try {
                String hwid = getHwid(context);
                JSONObject payload = new JSONObject();
                payload.put("licenseKey", key != null ? key.trim() : "");
                payload.put("license_key", key != null ? key.trim() : "");
                payload.put("brand", brandId != null ? brandId : "nin");
                payload.put("hwid", hwid);
                payload.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);

                String response = executePost(context, ENDPOINT_VERIFY_KEY, payload.toString(), AuthConstants.HTTP_CONNECT_TIMEOUT_MS);
                if (response != null && !response.trim().isEmpty()) {
                    JSONObject resJson = new JSONObject(response);
                    if (resJson.optBoolean("valid", false) || resJson.optBoolean("success", false)) {
                        String resultMessage = resJson.optString("message", "License Key verified successfully!");
                        int remainingSec = resJson.optInt("remaining_seconds", 86400);
                        String expiresAt = resJson.optString("expires_at", "");
                        String sessionToken = resJson.optString("session_token", "");

                        Prefs.with(context).writeInt("license_remaining_sec", remainingSec);
                        Prefs.with(context).writeString("license_expires_at", expiresAt);
                        AuthManager.setActiveLicenseKey(context, key);
                        if (!sessionToken.isEmpty()) {
                            AuthManager.setSessionToken(context, sessionToken);
                            // Bind verified session to native C++ memory engine
                            SecurityVault.setNativeSession(sessionToken, hwid, remainingSec);
                        }

                        mainHandler.post(() -> callback.onSuccess(resultMessage));
                        return;
                    } else {
                        final String errMsg = resJson.optString("error", resJson.optString("message", "Invalid or expired License Key!"));
                        AuthManager.setActiveLicenseKey(context, "");
                        mainHandler.post(() -> callback.onError(errMsg));
                        return;
                    }
                }
                AuthManager.setActiveLicenseKey(context, "");
                mainHandler.post(() -> callback.onError("Invalid response from license server. Please try again."));
            } catch (Exception e) {
                Log.e(TAG, "License verification request failed: " + e.getMessage());
                AuthManager.setActiveLicenseKey(context, "");
                mainHandler.post(() -> callback.onError("License server unreachable! Active internet connection is required to verify key."));
            }
        });
    }

    /**
     * POST /api/v2/app/heartbeat
     * Verifies that the active license has not expired during live gameplay.
     */
    public static void heartbeat(Context context, final ApiCallback callback) {
        executor.execute(() -> {
            try {
                String activeKey = AuthManager.getActiveLicenseKey(context);
                String sessionToken = AuthManager.getSessionToken(context);
                String hwid = getHwid(context);
                if (activeKey.isEmpty()) {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError("No active license key found."));
                    }
                    return;
                }

                JSONObject payload = new JSONObject();
                payload.put("licenseKey", activeKey);
                payload.put("license_key", activeKey);
                payload.put("sessionToken", sessionToken);
                payload.put("hwid", hwid);

                String response = executePost(context, ENDPOINT_HEARTBEAT, payload.toString(), AuthConstants.HTTP_CONNECT_TIMEOUT_MS);
                if (response != null && !response.trim().isEmpty()) {
                    JSONObject resJson = new JSONObject(response);
                    boolean valid = resJson.optBoolean("valid", false) || resJson.optBoolean("success", false);
                    if (valid) {
                        int remainingSec = resJson.optInt("remaining_seconds", 0);
                        Prefs.with(context).writeInt("license_remaining_sec", remainingSec);
                        if (callback != null) {
                            mainHandler.post(() -> callback.onSuccess("Session valid"));
                        }
                    } else {
                        // Key expired or revoked while playing
                        AuthManager.clearSession(context);
                        final String msg = resJson.optString("error", "Subscription expired! Please renew.");
                        if (callback != null) {
                            mainHandler.post(() -> callback.onError(msg));
                        }
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Heartbeat check warning: " + e.getMessage());
            }
        });
    }

    /**
     * Executes an HTTP POST request with banking-grade native cryptographic signing.
     */
    private static String executePost(Context context, String endpoint, String jsonBody, int timeoutMs) throws Exception {
        String baseUrl = getServerBaseUrl(context);
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        URL targetUrl = new URL(baseUrl + endpoint);

        long timestamp = System.currentTimeMillis();
        String nonce = SecurityVault.generateNonce();
        String hwid = getHwid(context);
        String signature = SecurityVault.signPayload(jsonBody, timestamp, nonce, hwid);
        String certDigest = SecurityVault.getApkCertificateDigest(context);

        HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(AuthConstants.HTTP_READ_TIMEOUT_MS);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "NinMods-Banking-Client/90.3");
        conn.setRequestProperty("X-Client-Version", context.getString(R.string.app_version));

        // Banking-grade cryptographic security headers
        conn.setRequestProperty("X-Signature", signature);
        conn.setRequestProperty("X-Timestamp", String.valueOf(timestamp));
        conn.setRequestProperty("X-Nonce", nonce);
        conn.setRequestProperty("X-HWID", hwid);
        conn.setRequestProperty("X-Client-Integrity", certDigest);
        conn.setRequestProperty("X-Security-Level", "BANKING_L3_NATIVE");

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return null;

        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        String raw = sb.toString();
        return sanitizeResponse(raw);
    }

    /**
     * Sanitizes response strings and strictly blocks HTML error pages, Cloudflare traces,
     * or leaked URLs so only clean, valid JSON responses are processed by the UI.
     */
    private static String sanitizeResponse(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        // Strictly reject any HTML or XML response (Cloudflare, Nginx, or Web Server error dumps)
        if (trimmed.startsWith("<") || trimmed.contains("<!DOCTYPE") || trimmed.contains("<html") || trimmed.contains("<body")) {
            return null;
        }
        // Must start with standard JSON object or array character
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        return trimmed;
    }
}
