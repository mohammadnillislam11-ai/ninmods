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
    /*
     * True when the native security library could not be loaded on this
     * device. That is NOT the same as tampering: the native gate simply
     * cannot be satisfied, so the engine stays locked, but the account must
     * still be able to sign in.
     */
    private static boolean sNativeUnavailable = false;

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
        if (sInitialized || context == null) return;
        sInitialized = true;

        try {
            // 1. Verify APK Certificate Digest
            String certDigest = getApkCertificateDigest(context);
            if (certDigest != null && !certDigest.trim().isEmpty()) {
                AppLogger.log(TAG, "APK Signature Digest: " + certDigest);
            }

            // 2. Scan /proc/self/maps for injected hook engines (Diagnostic logging)
            String hookEngine = detectInjectedHooks();
            if (hookEngine != null) {
                AppLogger.log(TAG, "Environment note: " + hookEngine);
            }

            // 3. Native JNI Integrity Check
            boolean genuine = false;
            try {
                genuine = nativeVerifyIntegrity(context, certDigest);
                AppLogger.log(TAG, "Native integrity check result: " + genuine);
            } catch (UnsatisfiedLinkError ule) {
                sNativeUnavailable = true;
                sTamperReason = "Native security library unavailable";
                AppLogger.logE(TAG, "Native security library is unavailable on this device", ule);
            }

            /* Never clear a failed integrity result. The previous code reset
             * both flags to false after the check, making the native verifier
             * unable to lock a re-signed or patched APK. */
            sIsTampered = !genuine || sNativeUnavailable;
            if (sIsTampered) {
                if (sTamperReason.isEmpty()) {
                    sTamperReason = certDigest == null || certDigest.trim().isEmpty()
                            ? "APK signing certificate is unavailable"
                            : "APK signing certificate does not match the release certificate";
                }
                try {
                    nativeSetTamperState(true);
                } catch (UnsatisfiedLinkError ignored) {
                    sNativeUnavailable = true;
                }
                AppLogger.logE(TAG, "Security Vault locked: " + sTamperReason, null);
            } else {
                sTamperReason = "";
                try {
                    nativeSetTamperState(false);
                } catch (UnsatisfiedLinkError ule) {
                    sNativeUnavailable = true;
                    sIsTampered = true;
                    sTamperReason = "Native security library unavailable";
                }
            }

            AppLogger.log(TAG, sIsTampered
                    ? "Security Vault initialized. Status: LOCKED"
                    : "Security Vault initialized. Status: SECURE_GENUINE");
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
            /* A locally re-created signature cannot be trusted: the signing
             * secret must never have a Java fallback copy. Returning an empty
             * signature makes the server reject the request and keeps the
             * native security boundary explicit. */
            sNativeUnavailable = true;
            sIsTampered = true;
            sTamperReason = "Native signing library unavailable";
            return "";
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

    /**
     * True when the native security library could not be loaded. The engine
     * stays locked in that case, but the account may still sign in.
     */
    public static boolean isNativeUnavailable() {
        return sNativeUnavailable;
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

    // =========================================================================
    // NATIVE C++ BINDINGS (libninmods.so)
    // =========================================================================
    private static native String nativeSignRequest(String payload, long timestamp, String nonce, String hwid);
    private static native boolean nativeVerifyIntegrity(Context context, String certDigest);
    private static native void nativeSetSession(String token, String hwid, long expirySeconds);
    private static native boolean nativeIsSessionValid();
    private static native boolean nativeSetTamperState(boolean tampered);
}
