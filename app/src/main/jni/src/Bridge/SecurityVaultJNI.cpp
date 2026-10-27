#include <jni.h>
#include <string>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <cstdio>

#include "Includes/Logger.h"
#include "Includes/NativeCrypto.h"
#include "Includes/driver.h"
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
// BANKING-GRADE APPLICATION SECURITY & DRIVER INQUIRY JNI EXPORTS
// ============================================================================

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_DriverManager_nativeProbeDriverInfo(JNIEnv *env,
                                                               jclass clazz) {
  bool is_active = false;
  std::string active_node = "None";
  int maj = 0;
  int min = 0;
  std::string dev_id_str = "None";
  std::string perms_str = "Inaccessible";
  std::string status_text = "Inactive";

  // 1. Primary: Check Unified c_driver Engine
  if (driver && (driver->isConnected() || EnsureDriverConnectedThrottled())) {
    is_active = true;
    active_node = (strlen(driver->getNodeName()) > 0) ? driver->getNodeName()
                                                      : "/dev/BYNb";
    int c_fd = driver->getFd();
    struct stat st{};
    if (stat(active_node.c_str(), &st) == 0) {
      maj = major(st.st_rdev);
      min = minor(st.st_rdev);
      char dev_buf[64];
      snprintf(dev_buf, sizeof(dev_buf), "Major: %d, Minor: %d (0x%lX)", maj,
               min, (unsigned long)st.st_rdev);
      dev_id_str = dev_buf;
    } else {
      dev_id_str = "Ring-0 Device (" + active_node + ")";
    }
    perms_str = "rw-rw-rw- (FD: " + std::to_string(c_fd) + " Connected)";
    status_text = std::string("ACTIVE & RESPONDING (") +
                  driver->getDriverModeName() + ")";
  }

  // 2. Fallback: Check official character & proc nodes
  if (!is_active) {
    const char *candidate_nodes[] = {"/dev/BYNb", "/dev/mem_tool", "/dev/nh",
                                     "/dev/wanbai", "/proc/uevents_records"};

    for (const char *node : candidate_nodes) {
      struct stat st;
      if (stat(node, &st) == 0 &&
          (S_ISCHR(st.st_mode) || S_ISREG(st.st_mode))) {
        active_node = node;
        is_active = true;
        maj = major(st.st_rdev);
        min = minor(st.st_rdev);
        char dev_buf[64];
        snprintf(dev_buf, sizeof(dev_buf), "Major: %d, Minor: %d (0x%lX)", maj,
                 min, (unsigned long)st.st_rdev);
        dev_id_str = dev_buf;

        int test_fd = open(node, O_RDWR);
        if (test_fd >= 0) {
          perms_str = "rw-rw-rw- (FD Open OK)";
          status_text = "ACTIVE & RESPONDING (Ring 0)";
          close(test_fd);
        } else {
          perms_str = "Node exists (Restricted permission)";
          status_text = "Node detected, run chmod 666 in loader";
        }
        break;
      }
    }
  }

  std::string json = "{";
  json += "\"is_active\":" + std::string(is_active ? "true" : "false") + ",";
  json += "\"active_node\":\"" + active_node + "\",";
  json += "\"major\":" + std::to_string(maj) + ",";
  json += "\"minor\":" + std::to_string(min) + ",";
  json += "\"device_id\":\"" + dev_id_str + "\",";
  json += "\"permissions\":\"" + perms_str + "\",";
  json += "\"status_text\":\"" + status_text + "\"";
  json += "}";

  return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_auth_SecurityVault_nativeSignRequest(
    JNIEnv *env, jclass clazz, jstring jPayload, jlong timestamp,
    jstring jNonce, jstring jHwid) {
  /* GetStringUTFChars returns NULL when the JVM is out of memory or when an
   * exception is pending. Building a std::string from that NULL pointer is
   * undefined behaviour and crashed the process inside the licence flow. */
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
  // 1. Anti-Debugging Check (/proc/self/status TracerPid)
  FILE *fp = fopen("/proc/self/status", "r");
  if (!fp) {
    /* The previous implementation skipped the whole check and returned
     * "verified" whenever /proc could not be read - a fail-open integrity
     * gate. If we cannot prove the process is clean we must not claim it is. */
    NIN_LOGE("SECURITY",
             ">>> [TAMPER_CHECK_FAILED] /proc/self/status unreadable - "
             "integrity cannot be proven <<<");
    return JNI_FALSE;
  }
  {
    char line[256];
    while (fgets(line, sizeof(line), fp)) {
      if (strncmp(line, "TracerPid:", 10) == 0) {
        int tracerPid = atoi(line + 10);
        if (tracerPid != 0) {
          fclose(fp);
          NIN_LOGE("SECURITY",
                   ">>> [TAMPER_DETECTED] Active TracerPid=%d attached <<<",
                   tracerPid);
          return JNI_FALSE;
        }
      }
    }
    fclose(fp);
  }

  // 2. Anti-Hook Framework Check (/proc/self/maps)
  FILE *mf = fopen("/proc/self/maps", "r");
  if (!mf) {
    NIN_LOGE("SECURITY",
             ">>> [TAMPER_CHECK_FAILED] /proc/self/maps unreadable - "
             "integrity cannot be proven <<<");
    return JNI_FALSE;
  }
  {
    char mapLine[512];
    while (fgets(mapLine, sizeof(mapLine), mf)) {
      if (strstr(mapLine, "frida-agent") || strstr(mapLine, "libfrida") ||
          strstr(mapLine, "libxposed") || strstr(mapLine, "libsubstrate")) {
        fclose(mf);
        NIN_LOGE("SECURITY",
                 ">>> [TAMPER_DETECTED] Hooking library mapped: %s <<<",
                 mapLine);
        return JNI_FALSE;
      }
    }
    fclose(mf);
  }

  // 3. APK signing certificate check.
  //
  // The certDigest parameter used to be accepted and then IGNORED, which
  // meant that a build re-signed with MT Manager was indistinguishable from
  // the original release: the app happily kept working and the engine kept
  // running. The fingerprint of the release certificate is compiled into this
  // native library and compared here.
  const char *expectedDigest = SecurityVault_ExpectedCertDigest();
  if (!certDigest) {
    NIN_LOGE("SECURITY",
             ">>> [TAMPER_CHECK_FAILED] certificate digest unavailable - "
             "the APK signature cannot be verified <<<");
    return JNI_FALSE;
  }

  const char *actualDigest = env->GetStringUTFChars(certDigest, nullptr);
  if (!actualDigest) {
    if (env->ExceptionCheck()) env->ExceptionClear();
    NIN_LOGE("SECURITY",
             ">>> [TAMPER_CHECK_FAILED] certificate digest could not be read <<<");
    return JNI_FALSE;
  }

  /* The expected fingerprint lives in exactly one place
   * (NativeCrypto.h :: SecurityVault_ExpectedCertDigest). The comparison
   * normalises the separators and the letter case, so the ':' separated form
   * and the plain hex form are both accepted without keeping a second copy
   * of the constant here. */
  const bool digestMatches = DigestEquals(actualDigest, expectedDigest);

  env->ReleaseStringUTFChars(certDigest, actualDigest);

  if (!digestMatches) {
    NIN_LOGE("SECURITY",
             ">>> [TAMPER_DETECTED] The APK was not signed with the release "
             "certificate - this build has been re-signed or modified <<<");
    return JNI_FALSE;
  }

  return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_auth_SecurityVault_nativeSetTamperState(
    JNIEnv *env, jclass clazz, jboolean tampered) {
  const bool genuine = SecurityVault_SetTamperState(tampered == JNI_TRUE);
  if (!genuine) {
    NIN_LOGE("SECURITY",
             ">>> [VAULT_LOCKED] Tamper state recorded - the native engine stays "
             "locked <<<");
  }
  return genuine ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_auth_SecurityVault_nativeSetSession(
    JNIEnv *env, jclass clazz, jstring jToken, jstring jHwid, jlong expirySec) {
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
      /* The token MUST carry a valid server signature. Without this check
       * any non-empty string unlocked the native engine, which made the
       * whole licence gate worthless: editing the DEX to call
       * SecurityVault.setNativeSession("x", "y", 999999) was enough.
       *
       * The signature covers the HWID, so a token issued for one device
       * cannot be replayed on another one either. */
      if (!SecurityVault_VerifySessionToken(token, hwid)) {
        NIN_LOGE("SECURITY",
                 ">>> [VAULT_REJECTED] Session token signature is missing or "
                 "invalid - the engine stays locked <<<");
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
      }

      strncpy(g_security_session, token, sizeof(g_security_session) - 1);
      g_security_session[sizeof(g_security_session) - 1] = '\0';
      strncpy(g_security_hwid, hwid, sizeof(g_security_hwid) - 1);
      g_security_hwid[sizeof(g_security_hwid) - 1] = '\0';
      /* The expiry used to be validated and then thrown away, so a session
       * stayed valid for the whole life of the process. */
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
  return SecurityVault_IsSessionValid() ? JNI_TRUE : JNI_FALSE;
}
