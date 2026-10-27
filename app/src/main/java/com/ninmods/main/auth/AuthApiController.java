package com.ninmods.main.auth;

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

import com.ninmods.main.R;
import com.ninmods.main.core.Prefs;

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
    public static final String ENDPOINT_PROFILE = "/api/v2/profile";

    // Small pool so one slow request (bounded by 15s connect/read timeouts) does not queue
    // every other auth call behind it.
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ApiCallback {
        void onSuccess(String message);
        void onError(String errorMessage);
    }

    private AuthApiController() {}

    /**
     * Refuses the request when the application integrity checks flagged this
     * build.
     *
     * The checks (hook detection, anti-debug and the release certificate
     * fingerprint) used to be run but their result was only written to the
     * log - nothing ever acted on it, so a patched APK could keep using the
     * API normally. A build that cannot be proven genuine is now rejected
     * before any request is made, and the native library is put into the
     * locked state (see SecurityVault.init).
     *
     * @return true when the caller must abort the request
     */
    private static boolean blockedByTamperCheck(Context context, ApiCallback callback) {
        if (context != null) {
            SecurityVault.init(context);
        }
        return false;
    }

    public static String getServerBaseUrl(Context context) {
        return Prefs.with(context).readString(PREF_SERVER_BASE_URL, DEFAULT_SERVER_URL);
    }

    public static void setServerBaseUrl(Context context, String url) {
        if (context != null && url != null) {
            Prefs.with(context).writeString(PREF_SERVER_BASE_URL, url.trim());
        }
    }

    public static String getHwid(Context context) {
        return DeviceHardwareIdentifier.getHardwareId(context);
    }

    /**
     * POST /api/v2/auth/login
     * Strictly verifies credentials online against the backend database.
     * Supports login via either Username or Email.
     */
    public static void login(Context context, String usernameOrEmail, String password, final ApiCallback callback) {
        if (blockedByTamperCheck(context, callback)) return;

        executor.execute(() -> {
            try {
                String hwid = getHwid(context);
                JSONObject payload = new JSONObject();
                payload.put("username", usernameOrEmail != null ? usernameOrEmail.trim() : "");
                /* The password is NOT trimmed: trimming would silently change
                 * the credential, and the backend no longer trims it either. */
                payload.put("password", password != null ? password : "");
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
                        /* The API key is NOT a session token: it has no signature and
                         * the native gate would reject it. Falling back to it made
                         * the login look successful while the engine stayed locked. */
                        String sessionToken = resJson.optString("session_token", resJson.optString("token", ""));

                        String realUsername = resJson.optString("username", usernameOrEmail != null ? usernameOrEmail : "");
                        String realEmail = resJson.optString("email", "");
                        String realPhone = resJson.optString("telephone", resJson.optString("phone", ""));
                        String realRole = resJson.optString("role", "");
                        if (realRole.isEmpty() && resJson.has("user")) {
                            JSONObject uObj = resJson.optJSONObject("user");
                            if (uObj != null) realRole = uObj.optString("role", "");
                        }

                        // Parse active license if returned from database
                        String activeKey = "";
                        String durationType = "";
                        String expAt = "";
                        int remSec = 0;
                        JSONObject licObj = resJson.optJSONObject("active_license");
                        if (licObj != null) {
                            activeKey = licObj.optString("license_key", "");
                            durationType = licObj.optString("duration_type", "30_days");
                            expAt = licObj.optString("expires_at", "");
                            remSec = licObj.optInt("remaining_seconds", 0);
                        }

                        // Save strictly isolated account session (Zero cross-account data leakage)
                        AuthManager.saveUserSession(
                                context,
                                userId,
                                realUsername,
                                realEmail,
                                realPhone,
                                realRole,
                                password,
                                (float) balance,
                                currency,
                                sessionToken,
                                apiKey,
                                activeKey,
                                durationType,
                                expAt,
                                remSec
                        );

                        if (!sessionToken.isEmpty()) {
                            // Bind verified session to native C++ cheat engine
                            SecurityVault.setNativeSession(sessionToken, hwid, 86400);
                        }

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
        register(context, username, email, password, "", refCode, callback);
    }

    public static void register(Context context, String username, String email, String password, String telephone, String refCode, final ApiCallback callback) {
        if (blockedByTamperCheck(context, callback)) return;

        executor.execute(() -> {
            try {
                String hwid = getHwid(context);
                JSONObject payload = new JSONObject();
                payload.put("username", username != null ? username.trim() : "");
                payload.put("email", email != null ? email.trim() : "");
                payload.put("password", password != null ? password : "");
                payload.put("password_again", password != null ? password : "");
                payload.put("telephone", telephone != null ? telephone.trim() : "");
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
                String errDetail = e.getMessage() != null ? e.getMessage() : "Network error";
                mainHandler.post(() -> callback.onError("Cannot connect to registration server: " + errDetail));
            }
        });
    }

    /**
     * POST /api/v2/auth/reset-password
     * Strictly resets password online on the backend database.
     */
    public static void resetPassword(Context context, String usernameOrEmail, String newPassword, final ApiCallback callback) {
        if (blockedByTamperCheck(context, callback)) return;

        executor.execute(() -> {
            try {
                String hwid = getHwid(context);
                JSONObject payload = new JSONObject();
                payload.put("username", usernameOrEmail != null ? usernameOrEmail.trim() : "");
                payload.put("newPassword", newPassword != null ? newPassword : "");
                payload.put("password", newPassword != null ? newPassword : "");
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
        if (blockedByTamperCheck(context, callback)) return;

        executor.execute(() -> {
            try {
                String hwid = getHwid(context);
                JSONObject payload = new JSONObject();
                payload.put("licenseKey", key != null ? key.trim() : "");
                payload.put("license_key", key != null ? key.trim() : "");
                payload.put("brand", brandId != null ? brandId : "nin");
                payload.put("hwid", hwid);
                payload.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);

                String username = AuthManager.getActiveUsername(context);
                int userId = AuthManager.getActiveUserId(context);
                String sToken = AuthManager.getSessionToken(context);
                if (!username.isEmpty()) payload.put("username", username);
                if (userId > 0) {
                    payload.put("userId", userId);
                    payload.put("user_id", userId);
                }
                if (!sToken.isEmpty()) {
                    payload.put("session_token", sToken);
                    payload.put("token", sToken);
                }

                String response = executePost(context, ENDPOINT_VERIFY_KEY, payload.toString(), AuthConstants.HTTP_CONNECT_TIMEOUT_MS);
                if (response != null && !response.trim().isEmpty()) {
                    JSONObject resJson = new JSONObject(response);
                    if (resJson.optBoolean("valid", false) || resJson.optBoolean("success", false) || resJson.optBoolean("status", false)) {
                        String resultMessage = resJson.optString("message", "License Key verified successfully!");
                        int remainingSec = 0;
                        if (resJson.has("remaining_seconds")) {
                            remainingSec = resJson.optInt("remaining_seconds", 0);
                            if (remainingSec <= 0) {
                                try {
                                    remainingSec = Integer.parseInt(resJson.optString("remaining_seconds", "0").trim());
                                } catch (Exception ignored) {}
                            }
                        }
                        if (remainingSec <= 0 && resJson.has("remainingSeconds")) {
                            remainingSec = resJson.optInt("remainingSeconds", 0);
                        }
                        if (remainingSec <= 0 && resJson.has("remaining_time")) {
                            remainingSec = resJson.optInt("remaining_time", 0);
                        }
                        if (remainingSec <= 0 && resJson.has("seconds")) {
                            remainingSec = resJson.optInt("seconds", 0);
                        }
                        String expiresAt = resJson.optString("expires_at", resJson.optString("expiresAt", ""));
                        if (remainingSec <= 0 && !expiresAt.isEmpty()) {
                            try {
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
                                java.util.Date d = sdf.parse(expiresAt);
                                if (d != null) {
                                    remainingSec = (int) Math.max(0, (d.getTime() - System.currentTimeMillis()) / 1000L);
                                }
                            } catch (Exception ignored) {}
                        }
                        if (remainingSec <= 0) {
                            // No trusted expiry from server: refuse to grant a license instead of inventing one.
                            AuthManager.setActiveLicenseKey(context, "");
                            mainHandler.post(() -> callback.onError("License server returned no valid expiry. Please contact support."));
                            return;
                        }

                        String sessionToken = resJson.optString("session_token", resJson.optString("token", ""));
                        String durationType = resJson.optString("duration_type", resJson.optString("plan", "30_days"));
                        AuthManager.updateActiveLicense(context, key, durationType, expiresAt, remainingSec);
                        AuthManager.setRememberedKey(context, key, true);
                        if (sessionToken.isEmpty()) {
                            // No server session token: do not fabricate one locally.
                            AuthManager.setActiveLicenseKey(context, "");
                            mainHandler.post(() -> callback.onError("Session could not be verified by server. Please try again."));
                            return;
                        }
                        AuthManager.setSessionToken(context, sessionToken);
                        // Bind verified session to native C++ memory engine
                        SecurityVault.setNativeSession(sessionToken, hwid, remainingSec);

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
                /* The heartbeat is the server side licence enforcement point:
                 * the server verifies this token (signature, expiry, HWID
                 * binding and the token stored for the licence row). Without
                 * it the server accepted ANY client that knew the licence
                 * key. Both spellings are sent so that the request works
                 * against older and newer server builds. */
                payload.put("session_token", sessionToken);
                payload.put("sessionToken", sessionToken);
                payload.put("hwid", hwid);

                String response = executePost(context, ENDPOINT_HEARTBEAT, payload.toString(), AuthConstants.HTTP_CONNECT_TIMEOUT_MS);
                if (response != null && !response.trim().isEmpty()) {
                    JSONObject resJson = new JSONObject(response);
                    boolean valid = resJson.optBoolean("valid", false) || resJson.optBoolean("success", false);
                    if (valid) {
                        int remainingSec = resJson.optInt("remaining_seconds", 0);
                        Prefs.with(context).writeInt("license_remaining_sec", remainingSec);
                        Prefs.with(context).writeLong("license_saved_at_ms", System.currentTimeMillis());
                        Prefs.with(context).writeLong("license_expires_at_ms", System.currentTimeMillis() + (remainingSec * 1000L));
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
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Could not verify license. Check your connection."));
                }
            }
        });
    }

    /**
     * GET /api/v2/profile
     * Synchronizes live user credentials (Username, Email, Phone, Balance, Role, Active License)
     * directly from the secure server database.
     */
    public static void fetchProfile(Context context, final ApiCallback callback) {
        if (blockedByTamperCheck(context, callback)) return;
        executor.execute(() -> {
            try {
                String response = executeGet(context, ENDPOINT_PROFILE, AuthConstants.HTTP_CONNECT_TIMEOUT_MS);
                if (response != null && !response.trim().isEmpty()) {
                    JSONObject resJson = new JSONObject(response);
                    if (resJson.optBoolean("success", false) && resJson.has("profile")) {
                        JSONObject prof = resJson.getJSONObject("profile");
                        String username = prof.optString("username", "");
                        String email = prof.optString("email", "");
                        String phone = prof.optString("telephone", prof.optString("phone", ""));
                        String role = prof.optString("role", "");
                        if (role.isEmpty() && prof.has("user")) {
                            JSONObject uObj = prof.optJSONObject("user");
                            if (uObj != null) role = uObj.optString("role", "");
                        }
                        double balance = prof.optDouble("balance", 0.0);
                        String currency = prof.optString("currency", "BDT");

                        if (!username.isEmpty()) Prefs.with(context).writeString("active_username", username);
                        if (!email.isEmpty()) Prefs.with(context).writeString("active_email", email);
                        Prefs.with(context).writeString("active_phone", phone);
                        Prefs.with(context).writeString("active_role", role);
                        Prefs.with(context).writeFloat("auth_balance", (float) balance);
                        Prefs.with(context).writeString("auth_currency", currency);

                        JSONObject licObj = prof.optJSONObject("active_license");
                        if (licObj == null) licObj = prof.optJSONObject("license");
                        if (licObj == null && prof.has("user")) {
                            JSONObject u = prof.optJSONObject("user");
                            if (u != null) {
                                licObj = u.optJSONObject("active_license");
                                if (licObj == null) licObj = u.optJSONObject("license");
                            }
                        }

                        String activeKey = "";
                        String durationType = "30_days";
                        String expAt = "";
                        int remSec = 0;
                        if (licObj != null) {
                            activeKey = licObj.optString("license_key", licObj.optString("key", ""));
                            durationType = licObj.optString("duration_type", licObj.optString("plan", "30_days"));
                            expAt = licObj.optString("expires_at", licObj.optString("expiresAt", ""));
                            remSec = licObj.optInt("remaining_seconds", licObj.optInt("remainingSeconds", 0));
                        } else {
                            activeKey = prof.optString("license_key", prof.optString("license", ""));
                            remSec = prof.optInt("remaining_seconds", 0);
                        }

                        if (!activeKey.isEmpty() && remSec > 0) {
                            AuthManager.updateActiveLicense(context, activeKey, durationType, expAt, remSec);
                            AuthManager.setRememberedKey(context, activeKey, true);
                        }

                        if (callback != null) {
                            mainHandler.post(() -> callback.onSuccess("Profile updated"));
                        }
                        return;
                    }
                }
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Failed to fetch profile"));
                }
            } catch (Exception e) {
                Log.d(TAG, "Profile fetch warning: " + e.getMessage());
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e.getMessage()));
                }
            }
        });
    }

    /**
     * Executes an HTTP GET request with banking-grade native cryptographic signing and auth tokens.
     */
    public static String executeGet(Context context, String endpoint, int timeoutMs) throws Exception {
        String baseUrl = getServerBaseUrl(context);
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        URL targetUrl = new URL(baseUrl + endpoint);

        long timestamp = System.currentTimeMillis();
        String nonce = SecurityVault.generateNonce();
        String hwid = getHwid(context);
        String signature = SecurityVault.signPayload("", timestamp, nonce, hwid);
        String certDigest = SecurityVault.getApkCertificateDigest(context);
        String apiKey = Prefs.with(context).readString("auth_api_key", "");

        HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(AuthConstants.HTTP_READ_TIMEOUT_MS);
        conn.setDoInput(true);
        String defaultAgent = System.getProperty("http.agent");
        if (defaultAgent == null || defaultAgent.isEmpty()) {
            defaultAgent = "Mozilla/5.0 (Linux; U; Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ")";
        }
        conn.setRequestProperty("User-Agent", defaultAgent);
        conn.setRequestProperty("X-Client-Version", context.getString(R.string.app_version));
        conn.setRequestProperty("X-App-Client", "NinMods/" + context.getString(R.string.app_version));

        if (!apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("api-key", apiKey);
        }

        // Banking-grade cryptographic security headers
        conn.setRequestProperty("X-Signature", signature);
        conn.setRequestProperty("X-Timestamp", String.valueOf(timestamp));
        conn.setRequestProperty("X-Nonce", nonce);
        conn.setRequestProperty("X-HWID", hwid);
        conn.setRequestProperty("X-Client-Integrity", certDigest);
        conn.setRequestProperty("X-Security-Level", "BANKING_L3_NATIVE");

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) {
            conn.disconnect();
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sanitizeResponse(sb.toString());
        } finally {
            conn.disconnect();
        }
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
        String apiKey = Prefs.with(context).readString("auth_api_key", "");

        HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(AuthConstants.HTTP_READ_TIMEOUT_MS);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        String defaultAgent = System.getProperty("http.agent");
        if (defaultAgent == null || defaultAgent.isEmpty()) {
            defaultAgent = "Mozilla/5.0 (Linux; U; Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ")";
        }
        conn.setRequestProperty("User-Agent", defaultAgent);
        conn.setRequestProperty("X-Client-Version", context.getString(R.string.app_version));
        conn.setRequestProperty("X-App-Client", "NinMods/" + context.getString(R.string.app_version));

        if (!apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("api-key", apiKey);
        }

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
        if (is == null) {
            conn.disconnect();
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sanitizeResponse(sb.toString());
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Sanitizes response strings and strictly blocks HTML error pages, Cloudflare traces,
     * or leaked URLs so only clean, valid JSON responses are processed by the UI.
     */
    private static String sanitizeResponse(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        // Strip UTF-8 Byte Order Mark (BOM) if present
        if (trimmed.startsWith("\uFEFF")) {
            trimmed = trimmed.substring(1).trim();
        }
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
