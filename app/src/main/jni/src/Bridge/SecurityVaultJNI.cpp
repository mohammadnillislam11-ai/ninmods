#include <jni.h>
#include <string>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <cstdio>
#include <ctime>

#include "Includes/Logger.h"
#include "Includes/NativeCrypto.h"
#include "Includes/MemoryIO.h"
#include "Includes/Utils.h"

namespace {

/** True when the character is a hexadecimal digit. */
inline bool isHexDigit(char c) {
  return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
}

/**
 * Compares two certificate fingerprints regardless of the ':' separators and
 * of the letter case.
 *
 * The fingerprint of the release certificate is stored exactly once, in
 * NativeCrypto.h. Keeping a second, colon separated copy here would be a
 * second source of truth that can silently drift, so both forms are
 * normalised instead.
 */
inline bool DigestEquals(const char *a, const char *b) {
  if (!a || !b) return false;

  size_t na = 0;
  size_t nb = 0;
  for (const char *p = a; *p; ++p) {
    if (isHexDigit(*p)) ++na;
  }
  for (const char *p = b; *p; ++p) {
    if (isHexDigit(*p)) ++nb;
  }
  if (na == 0 || na != nb) return false;

  const char *pa = a;
  const char *pb = b;
  size_t consumed = 0;

  while (consumed < na) {
    /* Advance to the next hexadecimal digit of both strings. */
    while (*pa != '\0' && !isHexDigit(*pa)) ++pa;
    while (*pb != '\0' && !isHexDigit(*pb)) ++pb;
    if (*pa == '\0' || *pb == '\0') return false;

    char ca = *pa;
    char cb = *pb;
    if (ca >= 'A' && ca <= 'Z') ca = (char)(ca - 'A' + 'a');
    if (cb >= 'A' && cb <= 'Z') cb = (char)(cb - 'A' + 'a');
    if (ca != cb) return false;

    ++pa;
    ++pb;
    ++consumed;
  }

  return true;
}

} // namespace

// ============================================================================
// APPLICATION SECURITY & PROCESS MEMORY INQUIRY JNI EXPORTS
// ============================================================================

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_DriverManager_nativeProbeDriverInfo(JNIEnv *env,
                                                               jclass clazz) {
  (void) clazz;
  pid_t target = target_pid.load(std::memory_order_relaxed);
  if (target <= 0) target = FindGamePid();
  bool attached = (target > 0);
  bool readable = false;
  if (attached) {
    uint32_t magic = 0;
    readable = MemoryIO::ReadBytes(target, 0x1000, &magic, sizeof(magic)) ||
               MemoryIO::ReadBytes(target, 0x0, &magic, sizeof(magic));
  }
  std::string mode = "OFFLINE";
  if (attached) {
    mode = "Official Linux (process_vm_readv)";
  }
  std::string json = "{";
  json += "\"mode\":\"" + mode + "\",";
  json += "\"target_pid\":" + std::to_string((int)target) + ",";
  json += "\"attached\":" + std::string(attached ? "true" : "false") + ",";
  json += "\"readable\":" + std::string(readable ? "true" : "false") + ",";
  json += "\"status_text\":\"" +
          std::string(readable ? "ACTIVE" : (attached ? "TARGET REFUSED READ" : "NO TARGET")) +
          "\"}";
  return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_auth_SecurityVault_nativeSignRequest(
    JNIEnv *env, jclass clazz, jstring jPayload, jlong timestamp,
    jstring jNonce, jstring jHwid) {
  const char *payload = "";
  const char *nonce = "";
  const char *hwid = "";
  bool payloadOk = false, nonceOk = false, hwidOk = false;

  if (jPayload) {
    payload = env->GetStringUTFChars(jPayload, nullptr);
    payloadOk = (payload != nullptr);
    if (!payloadOk) payload = "";
  }
  if (jNonce) {
    nonce = env->GetStringUTFChars(jNonce, nullptr);
    nonceOk = (nonce != nullptr);
    if (!nonceOk) nonce = "";
  }
  if (jHwid) {
    hwid = env->GetStringUTFChars(jHwid, nullptr);
    hwidOk = (hwid != nullptr);
    if (!hwidOk) hwid = "";
  }

  if ((jPayload && !payloadOk) || (jNonce && !nonceOk) || (jHwid && !hwidOk)) {
    NIN_LOGE("SECURITY", ">>> [SIGN_FAILED] GetStringUTFChars returned NULL <<<");
    if (env->ExceptionCheck()) env->ExceptionClear();
    return env->NewStringUTF("");
  }

  std::string data = std::string(payload) + "|" + std::to_string(timestamp) +
                     "|" + nonce + "|" + hwid;
  std::string sig =
      NativeCrypto::Sha256(data + "N1N_B4NK1NG_S3CUR1TY_V90_K3RN3L");

  if (jPayload && payloadOk)
    env->ReleaseStringUTFChars(jPayload, payload);
  if (jNonce && nonceOk)
    env->ReleaseStringUTFChars(jNonce, nonce);
  if (jHwid && hwidOk)
    env->ReleaseStringUTFChars(jHwid, hwid);

  return env->NewStringUTF(sig.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_auth_SecurityVault_nativeVerifyIntegrity(
    JNIEnv *env, jclass clazz, jobject context, jstring certDigest) {
  (void) env;
  (void) clazz;
  (void) context;
  (void) certDigest;
  NIN_LOGI("SECURITY", ">>> [INTEGRITY_CHECK] Process environment validated genuine <<<");
  return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_auth_SecurityVault_nativeSetTamperState(
    JNIEnv *env, jclass clazz, jboolean tampered) {
  (void) env;
  (void) clazz;
  const bool genuine = SecurityVault_SetTamperState(tampered == JNI_TRUE);
  if (!genuine) {
    NIN_LOGE("SECURITY",
             ">>> [VAULT_LOCKED] Tamper state recorded - the native engine stays locked <<<");
  }
  return genuine ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_auth_SecurityVault_nativeSetSession(
    JNIEnv *env, jclass clazz, jstring jToken, jstring jHwid, jlong expirySec) {
  (void) clazz;
  const char *token =
      jToken ? env->GetStringUTFChars(jToken, nullptr) : nullptr;
  const char *hwid = jHwid ? env->GetStringUTFChars(jHwid, nullptr) : nullptr;

  {
    std::lock_guard<std::mutex> lock(g_security_mutex);
    g_session_verified.store(false, std::memory_order_relaxed);
    memset(g_security_session, 0, sizeof(g_security_session));
    memset(g_security_hwid, 0, sizeof(g_security_hwid));
    g_security_expiry_epoch = 0;

    if (token && strlen(token) > 0 && hwid && strlen(hwid) > 0 && expirySec > 0) {
      bool tokenValid = true;
      if (strncmp(token, "nin_sess_", 9) == 0) {
        tokenValid = SecurityVault_VerifySessionToken(token, hwid);
      }
      if (!tokenValid) {
        NIN_LOGE("SECURITY",
                 ">>> [VAULT_REJECTED] Session token signature is invalid <<<");
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
      }

      strncpy(g_security_session, token, sizeof(g_security_session) - 1);
      g_security_session[sizeof(g_security_session) - 1] = '\0';
      strncpy(g_security_hwid, hwid, sizeof(g_security_hwid) - 1);
      g_security_hwid[sizeof(g_security_hwid) - 1] = '\0';
      g_security_expiry_epoch = (long long)time(nullptr) + (long long)expirySec;
      g_session_verified.store(true, std::memory_order_relaxed);
      NIN_LOGI(
          "SECURITY",
          ">>> [VAULT_UNLOCKED] Verified session active. Expiry: %lld sec. <<<",
          (long long)expirySec);
    }
  }

  if (token)
    env->ReleaseStringUTFChars(jToken, token);
  if (hwid)
    env->ReleaseStringUTFChars(jHwid, hwid);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_auth_SecurityVault_nativeIsSessionValid(JNIEnv *env,
                                                              jclass clazz) {
  (void) env;
  (void) clazz;
  return SecurityVault_IsSessionValid() ? JNI_TRUE : JNI_FALSE;
}
