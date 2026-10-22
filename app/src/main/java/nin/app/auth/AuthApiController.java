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
 * Advanced Node.js Backend API Controller.
 * Manages all network requests routed through '/api/v2/' endpoints for authentication,
 * registration, password reset, and license key verification.
 * 
 * Features automatic graceful fallback: if the remote Node.js server is not reachable
 * or offline during development, it seamlessly falls back to local validation with
 * an authentic network handshake delay so the Android loading indicator functions perfectly.
 */
public final class AuthApiController {

    private static final String TAG = "AuthApiController";

    public static final String PREF_SERVER_BASE_URL = "pref_server_base_url";
    public static final String DEFAULT_SERVER_URL = "http://127.0.0.1:5000";

    // Strict Node.js v2 Route Standards
    public static final String ENDPOINT_LOGIN = "/api/v2/auth/login";
    public static final String ENDPOINT_REGISTER = "/api/v2/auth/register";
    public static final String ENDPOINT_RESET_PASS = "/api/v2/auth/reset-password";
    public static final String ENDPOINT_VERIFY_KEY = "/api/v2/license/verify";

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

    private static String getHwid(Context context) {
        try {
            return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) {
            return "UNKNOWN_HWID";
        }
    }

    /**
     * POST /api/v2/auth/login
     */
    public static void login(Context context, String username, String password, final ApiCallback callback) {
        executor.execute(() -> {
            boolean networkSuccess = false;
            String resultMessage = "";

            try {
                JSONObject payload = new JSONObject();
                payload.put("username", username);
                payload.put("password", password);
                payload.put("hwid", getHwid(context));
                payload.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
                payload.put("androidVersion", Build.VERSION.RELEASE);
                payload.put("clientVersion", context.getString(R.string.app_version));

                String response = executePost(context, ENDPOINT_LOGIN, payload.toString(), 2500);
                if (response != null) {
                    JSONObject resJson = new JSONObject(response);
                    if (resJson.optBoolean("success", false) || resJson.optBoolean("status", false)) {
                        networkSuccess = true;
                        resultMessage = resJson.optString("message", "Login successful!");
                    } else {
                        final String errMsg = resJson.optString("message", "Invalid credentials!");
                        mainHandler.post(() -> callback.onError(errMsg));
                        return;
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Node.js server unreachable on " + ENDPOINT_LOGIN + ", engaging offline dev fallback: " + e.getMessage());
            }

            if (!networkSuccess) {
                // Simulate authentic Node.js handshake (950ms)
                try { Thread.sleep(950); } catch (InterruptedException ignored) {}

                if (AuthManager.verifyLogin(username, password)) {
                    mainHandler.post(() -> callback.onSuccess("Login successful!"));
                } else {
                    mainHandler.post(() -> callback.onError("Invalid credentials! Please try again."));
                }
            } else {
                final String finalMsg = resultMessage;
                mainHandler.post(() -> callback.onSuccess(finalMsg));
            }
        });
    }

    /**
     * POST /api/v2/auth/register
     */
    public static void register(Context context, String username, String password, final ApiCallback callback) {
        executor.execute(() -> {
            boolean networkSuccess = false;
            String resultMessage = "";

            try {
                JSONObject payload = new JSONObject();
                payload.put("username", username);
                payload.put("password", password);
                payload.put("hwid", getHwid(context));
                payload.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
                payload.put("clientVersion", context.getString(R.string.app_version));

                String response = executePost(context, ENDPOINT_REGISTER, payload.toString(), 2500);
                if (response != null) {
                    JSONObject resJson = new JSONObject(response);
                    if (resJson.optBoolean("success", false) || resJson.optBoolean("status", false)) {
                        networkSuccess = true;
                        resultMessage = resJson.optString("message", "Account created successfully!");
                    } else {
                        final String errMsg = resJson.optString("message", "Registration failed!");
                        mainHandler.post(() -> callback.onError(errMsg));
                        return;
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Node.js server unreachable on " + ENDPOINT_REGISTER + ", engaging offline dev fallback");
            }

            if (!networkSuccess) {
                // Simulate authentic Node.js handshake (950ms)
                try { Thread.sleep(950); } catch (InterruptedException ignored) {}

                if (AuthManager.registerUser(username, password)) {
                    mainHandler.post(() -> callback.onSuccess("Account created successfully! Please login."));
                } else {
                    mainHandler.post(() -> callback.onError("Username already exists. Please choose another."));
                }
            } else {
                final String finalMsg = resultMessage;
                mainHandler.post(() -> callback.onSuccess(finalMsg));
            }
        });
    }

    /**
     * POST /api/v2/auth/reset-password
     */
    public static void resetPassword(Context context, String username, String newPassword, final ApiCallback callback) {
        executor.execute(() -> {
            boolean networkSuccess = false;
            String resultMessage = "";

            try {
                JSONObject payload = new JSONObject();
                payload.put("username", username);
                payload.put("newPassword", newPassword);
                payload.put("hwid", getHwid(context));

                String response = executePost(context, ENDPOINT_RESET_PASS, payload.toString(), 2500);
                if (response != null) {
                    JSONObject resJson = new JSONObject(response);
                    if (resJson.optBoolean("success", false) || resJson.optBoolean("status", false)) {
                        networkSuccess = true;
                        resultMessage = resJson.optString("message", "Password reset successful!");
                    } else {
                        final String errMsg = resJson.optString("message", "User not found!");
                        mainHandler.post(() -> callback.onError(errMsg));
                        return;
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Node.js server unreachable on " + ENDPOINT_RESET_PASS + ", engaging offline dev fallback");
            }

            if (!networkSuccess) {
                // Simulate authentic Node.js handshake (950ms)
                try { Thread.sleep(950); } catch (InterruptedException ignored) {}

                if (AuthManager.resetPassword(username, newPassword)) {
                    mainHandler.post(() -> callback.onSuccess("Password reset successful! Please login."));
                } else {
                    mainHandler.post(() -> callback.onError("User not found! Please check username or sign up."));
                }
            } else {
                final String finalMsg = resultMessage;
                mainHandler.post(() -> callback.onSuccess(finalMsg));
            }
        });
    }

    /**
     * POST /api/v2/license/verify
     */
    public static void verifyLicenseKey(Context context, String key, String brandId, final ApiCallback callback) {
        executor.execute(() -> {
            boolean networkSuccess = false;
            String resultMessage = "";

            try {
                JSONObject payload = new JSONObject();
                payload.put("licenseKey", key);
                payload.put("brand", brandId != null ? brandId : "nin");
                payload.put("hwid", getHwid(context));
                payload.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);

                String response = executePost(context, ENDPOINT_VERIFY_KEY, payload.toString(), 2500);
                if (response != null) {
                    JSONObject resJson = new JSONObject(response);
                    if (resJson.optBoolean("valid", false) || resJson.optBoolean("success", false)) {
                        networkSuccess = true;
                        resultMessage = resJson.optString("message", "Key verified!");
                    } else {
                        final String errMsg = resJson.optString("message", "Invalid License Key!");
                        mainHandler.post(() -> callback.onError(errMsg));
                        return;
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Node.js server unreachable on " + ENDPOINT_VERIFY_KEY + ", engaging offline dev fallback");
            }

            if (!networkSuccess) {
                // Simulate authentic Node.js handshake (950ms)
                try { Thread.sleep(950); } catch (InterruptedException ignored) {}

                if (AuthManager.verifyLicenseKey(key)) {
                    mainHandler.post(() -> callback.onSuccess("Key verified!"));
                } else {
                    mainHandler.post(() -> callback.onError("Invalid Key! Please enter a valid test key (" + AuthConstants.DEV_FREE_LICENSE_KEY + ")."));
                }
            } else {
                final String finalMsg = resultMessage;
                mainHandler.post(() -> callback.onSuccess(finalMsg));
            }
        });
    }

    private static String executePost(Context context, String endpoint, String jsonBody, int timeoutMs) throws Exception {
        String baseUrl = getServerBaseUrl(context);
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        URL targetUrl = new URL(baseUrl + endpoint);

        HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "NinMods-Android-Client/2.0");
        conn.setRequestProperty("X-Client-Version", context.getString(R.string.app_version));

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
        return sb.toString();
    }
}
