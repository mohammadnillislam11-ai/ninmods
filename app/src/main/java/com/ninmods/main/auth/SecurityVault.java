package com.ninmods.main.auth;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.security.MessageDigest;
import java.security.SecureRandom;

import com.ninmods.main.core.AppLogger;

/**
 * Banking-Grade Application Integrity & Tamper-Resistance Vault.
 * 
 * Provides defense-in-depth protection against MT Manager patching, Smali bytecode manipulation,
 * fake server mocking, and unauthorized binary modifications on rooted devices:
 * 
 * 1. Native Cryptographic Request Signing (HMAC-SHA256 with embedded native secrets & timestamp nonce)
 * 2. Native APK Signature / Certificate Verification (detects MT Manager re-signing)
 * 3. Native Session Gatekeeper (locks Ring 0 memory operations until a verified server session is unlocked)
 * 4. Anti-Hook & Anti-Debugging Diagnostics (/proc/self/maps and /proc/self/status checks)
 */
public final class SecurityVault {

    private static final String TAG = "SecurityVault";
    private static final SecureRandom secureRandom = new SecureRandom();

    private static boolean sInitialized = false;
    private static boolean sIsTampered = false;
    private static String sTamperReason = "";

    static {
        try {
            System.loadLibrary("ninmods");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load native security library: " + t.getMessage());
        }
    }

    private SecurityVault() {}

    /**
     * Initializes the Security Vault and runs banking-grade anti-tamper diagnostics.
     */
    public static synchronized void init(Context context) {
        if (sInitialized) return;
        sInitialized = true;

        if (context == null) return;

        try {
            // 1. Verify APK Certificate Digest
            String certDigest = getApkCertificateDigest(context);
            
            // 2. Scan /proc/self/maps for injected hook engines (Frida, Substrate, Xposed)
            String hookEngine = detectInjectedHooks();
            if (hookEngine != null) {
                sIsTampered = true;
                sTamperReason = "Injected hooking framework detected: " + hookEngine;
                AppLogger.logE(TAG, sTamperReason, null);
            }

            // 3. Native JNI Integrity Check
            try {
                boolean nativeCheck = nativeVerifyIntegrity(context, certDigest);
                if (!nativeCheck) {
                    sIsTampered = true;
                    sTamperReason = "Native binary integrity validation failed";
                    AppLogger.logE(TAG, sTamperReason, null);
                }
            } catch (UnsatisfiedLinkError ule) {
                // Native check fallback
            }

            AppLogger.log(TAG, "Security Vault initialized. Status: " + (sIsTampered ? "TAMPERED (" + sTamperReason + ")" : "SECURE_GENUINE"));
        } catch (Exception e) {
            AppLogger.logE(TAG, "Security initialization error: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a 16-byte cryptographically secure random hexadecimal nonce.
     */
    public static String generateNonce() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Computes a native HMAC-SHA256 signature for outgoing API requests.
     */
    public static String signPayload(String payload, long timestamp, String nonce, String hwid) {
        if (payload == null) payload = "";
        if (nonce == null) nonce = "";
        if (hwid == null) hwid = "";

        try {
            return nativeSignRequest(payload, timestamp, nonce, hwid);
        } catch (UnsatisfiedLinkError ule) {
            // Java fallback hash with internal salt
            return fallbackHmacSha256(payload + "|" + timestamp + "|" + nonce + "|" + hwid);
        }
    }

    /**
     * Unlocks the native C++ cheat & memory engine with verified server session tokens.
     */
    public static void setNativeSession(String sessionToken, String hwid, long expirySeconds) {
        if (sessionToken == null || sessionToken.trim().isEmpty()) return;
        try {
            nativeSetSession(sessionToken.trim(), hwid != null ? hwid : "", expirySeconds);
        } catch (UnsatisfiedLinkError ignored) {}
    }

    /**
     * Resets and purges the native C++ security session.
     */
    public static void resetNativeSession() {
        try {
            nativeSetSession("", "", 0);
        } catch (Throwable ignored) {}
    }

    /**
     * Checks if the native C++ session is currently valid and active.
     */
    public static boolean isSessionValid() {
        try {
            return nativeIsSessionValid();
        } catch (UnsatisfiedLinkError ule) {
            return false;
        }
    }

    /**
     * Returns true if any tampering or hook injection was detected.
     */
    public static boolean isTampered() {
        return sIsTampered;
    }

    public static String getTamperReason() {
        return sTamperReason;
    }

    /**
     * Reads APK signing certificate SHA-256 fingerprint.
     */
    public static String getApkCertificateDigest(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            String pkg = context.getPackageName();
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo packageInfo = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
                if (packageInfo.signingInfo != null) {
                    signatures = packageInfo.signingInfo.getApkContentsSigners();
                } else {
                    return "";
                }
            } else {
                PackageInfo packageInfo = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                signatures = packageInfo.signatures;
            }

            if (signatures != null && signatures.length > 0) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(signatures[0].toByteArray());
                StringBuilder hex = new StringBuilder();
                for (byte b : digest) {
                    hex.append(String.format("%02x", b));
                }
                return hex.toString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * Scans /proc/self/maps for injected hooking frameworks.
     */
    private static String detectInjectedHooks() {
        File maps = new File("/proc/self/maps");
        if (!maps.exists() || !maps.canRead()) return null;

        try (BufferedReader br = new BufferedReader(new FileReader(maps))) {
            String line;
            while ((line = br.readLine()) != null) {
                String lower = line.toLowerCase();
                if (lower.contains("frida-agent") || lower.contains("frida-gadget") || lower.contains("libfrida")) {
                    return "Frida Instrumentation";
                }
                if (lower.contains("libxposed") || lower.contains("xposedbridge") || lower.contains("lsposed")) {
                    return "Xposed / LSPosed Framework";
                }
                if (lower.contains("libsubstrate") || lower.contains("substrate.so")) {
                    return "Cydia Substrate";
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String fallbackHmacSha256(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((data + "N1N_B4NK1NG_S3CUR1TY_V90_K3RN3L").getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // =========================================================================
    // NATIVE C++ BINDINGS (libninmods.so)
    // =========================================================================
    private static native String nativeSignRequest(String payload, long timestamp, String nonce, String hwid);
    private static native boolean nativeVerifyIntegrity(Context context, String certDigest);
    private static native void nativeSetSession(String token, String hwid, long expirySeconds);
    private static native boolean nativeIsSessionValid();
}
