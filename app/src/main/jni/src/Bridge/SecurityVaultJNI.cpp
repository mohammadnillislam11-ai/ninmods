#include <jni.h>

#include <cstdio>
#include <cstring>
#include <ctime>
#include <fcntl.h>
#include <string>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <unistd.h>

#include "Includes/Logger.h"
#include "Includes/MemoryIO.h"
#include "Includes/NativeCrypto.h"
#include "Includes/Utils.h"

namespace {

/**
 * @brief Reports whether a character is a hexadecimal digit.
 *
 * @param c character to test
 * @return true when the character is a hexadecimal digit
 */
inline bool isHexDigit(char c) {
  return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
}

/**
 * @brief Compares two certificate fingerprints regardless of the ':' separators
 * and of the letter case.
 *
 * The fingerprint of the release certificate is stored exactly once, in
 * NativeCrypto.h. Keeping a second, colon separated copy here would be a
 * second source of truth that can silently drift, so both forms are
 * normalised instead.
 *
 * @param a first fingerprint
 * @param b second fingerprint
 * @return true when both fingerprints carry the same hexadecimal digits
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

/**
 * @brief Builds the permission string of the open driver node.
 *
 * @param fd descriptor of the driver node
 * @return the permission string of the node
 */
inline std::string DescribePermissions(int fd) {
  if (fd < 0) return "Inaccessible";
  struct stat nodeStat {};
  if (fstat(fd, &nodeStat) != 0) return "Inaccessible";

  std::string permissions;
  permissions += (nodeStat.st_mode & S_IRUSR) ? 'r' : '-';
  permissions += (nodeStat.st_mode & S_IWUSR) ? 'w' : '-';
  permissions += (nodeStat.st_mode & S_IXUSR) ? 'x' : '-';
  permissions += (nodeStat.st_mode & S_IRGRP) ? 'r' : '-';
  permissions += (nodeStat.st_mode & S_IWGRP) ? 'w' : '-';
  permissions += (nodeStat.st_mode & S_IXGRP) ? 'x' : '-';
  permissions += (nodeStat.st_mode & S_IROTH) ? 'r' : '-';
  permissions += (nodeStat.st_mode & S_IWOTH) ? 'w' : '-';
  permissions += (nodeStat.st_mode & S_IXOTH) ? 'x' : '-';
  return permissions;
}

/**
 * @brief Escapes a text so it can be placed inside a JSON document.
 *
 * @param value text that may contain quotes or control characters
 * @return the escaped text
 */
inline std::string JsonEscape(const std::string &value) {
  std::string escaped;
  escaped.reserve(value.size() + 8);
  for (char c : value) {
    if (c == '"' || c == '\\') {
      escaped += '\\';
      escaped += c;
    } else if (static_cast<unsigned char>(c) < 0x20) {
      escaped += ' ';
    } else {
      escaped += c;
    }
  }
  return escaped;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_ninmods_main_core_DriverManager_nativeProbeDriverInfo(JNIEnv *env,
                                                               jclass clazz) {
  (void) clazz;

  /*
   * The probe answers with the keys the Java layer parses
   * (DriverManager.inquireDriverInfo): is_active, active_node, major, minor,
   * device_id, permissions and status_text. The old answer used a different
   * key set, so the Java parser never saw a single value of it.
   */
  pid_t target = target_pid.load(std::memory_order_relaxed);
  if (target <= 0) target = FindGamePid();

  int fd = MemoryIO::GetDriverFd();
  bool driverLoaded = (fd >= 0);
  bool driverReady = MemoryIO::IsDriverReady();

  bool attached = (target > 0);
  bool readable = false;
  if (attached && driverReady) {
    uint32_t magic = 0;
    readable = MemoryIO::ReadBytes(target, 0x1000, &magic, sizeof(magic)) ||
               MemoryIO::ReadBytes(target, 0x0, &magic, sizeof(magic));
  }

  std::string node = driverLoaded ? MemoryIO::GetDriverNode() : "None";
  int major = 0;
  int minor = 0;
  if (driverLoaded) {
    struct stat nodeStat {};
    if (fstat(fd, &nodeStat) == 0) {
      major = (int) major(nodeStat.st_rdev);
      minor = (int) minor(nodeStat.st_rdev);
    }
  }

  std::string statusText;
  if (!driverLoaded) {
    statusText = "NOT LOADED / INACTIVE";
  } else if (!driverReady) {
    statusText = "NODE OPEN, IOCTL NOT VERIFIED";
  } else if (!attached) {
    statusText = "DRIVER ONLINE (WAITING FOR THE GAME)";
  } else if (!readable) {
    statusText = "DRIVER ONLINE, TARGET REFUSED READ";
  } else {
    statusText = "ACTIVE (Ring 0 Kernel)";
  }

  std::string json = "{";
  json += "\"is_active\":" + std::string(driverReady ? "true" : "false") + ",";
  json += "\"active_node\":\"" + JsonEscape(node) + "\",";
  json += "\"major\":" + std::to_string(major) + ",";
  json += "\"minor\":" + std::to_string(minor) + ",";
  json += "\"device_id\":\"Major: " + std::to_string(major) + ", Minor: " +
          std::to_string(minor) + "\",";
  json += "\"permissions\":\"" + JsonEscape(DescribePermissions(fd)) + "\",";
  json += "\"target_pid\":" + std::to_string((int) target) + ",";
  json += "\"attached\":" + std::string(attached ? "true" : "false") + ",";
  json += "\"readable\":" + std::string(readable ? "true" : "false") + ",";
  json += "\"mode\":\"RT Ring-0 Driver (IOCTL)\",";
  json += "\"status_text\":\"" + JsonEscape(statusText) + "\"";
  json += "}";
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
  (void) clazz;
  (void) context;

  /*
   * The check compares the fingerprint of the APK that is really installed
   * with the fingerprint that was compiled into the native library. A build
   * that was re-signed with another key - which is what a patcher does - does
   * not match and is reported as tampered. The old implementation ignored the
   * fingerprint and always answered "genuine".
   */
  const char *digest = nullptr;
  if (certDigest) {
    digest = env->GetStringUTFChars(certDigest, nullptr);
  }

  if (digest == nullptr || digest[0] == '\0') {
    if (digest) env->ReleaseStringUTFChars(certDigest, digest);
    if (env->ExceptionCheck()) env->ExceptionClear();
    NIN_LOGE("SECURITY", ">>> [INTEGRITY_CHECK] No certificate fingerprint available <<<");
    return JNI_FALSE;
  }

  bool genuine = DigestEquals(digest, SecurityVault_ExpectedCertDigest());
  if (digest) env->ReleaseStringUTFChars(certDigest, digest);

  if (genuine) {
    NIN_LOGI("SECURITY", ">>> [INTEGRITY_CHECK] Signing certificate matches <<<");
    return JNI_TRUE;
  }

  NIN_LOGE("SECURITY",
           ">>> [INTEGRITY_CHECK] Signing certificate does NOT match the expected one <<<");
  return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_auth_SecurityVault_nativeSetTamperState(
    JNIEnv *env, jclass clazz, jboolean tampered) {
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
      /*
       * A session token is only accepted when it carries the expected prefix
       * AND its signature is verified. The old code accepted every token that
       * did not start with the prefix without checking it at all.
       */
      bool tokenValid = false;
      if (strncmp(token, "nin_sess_", 9) == 0) {
        tokenValid = SecurityVault_VerifySessionToken(token, hwid);
      }
      if (!tokenValid) {
        NIN_LOGE("SECURITY",
                 ">>> [VAULT_REJECTED] Session token signature is invalid <<<");
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (token) env->ReleaseStringUTFChars(jToken, token);
        if (hwid) env->ReleaseStringUTFChars(jHwid, hwid);
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
  (void) clazz;
  return SecurityVault_IsSessionValid() ? JNI_TRUE : JNI_FALSE;
}
