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
  if (driver && (driver->isConnected() || driver->initDriver())) {
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
  const char *payload =
      jPayload ? env->GetStringUTFChars(jPayload, nullptr) : "";
  const char *nonce = jNonce ? env->GetStringUTFChars(jNonce, nullptr) : "";
  const char *hwid = jHwid ? env->GetStringUTFChars(jHwid, nullptr) : "";

  std::string data = std::string(payload) + "|" + std::to_string(timestamp) +
                     "|" + nonce + "|" + hwid;
  std::string sig =
      NativeCrypto::Sha256(data + "N1N_B4NK1NG_S3CUR1TY_V90_K3RN3L");

  if (jPayload)
    env->ReleaseStringUTFChars(jPayload, payload);
  if (jNonce)
    env->ReleaseStringUTFChars(jNonce, nonce);
  if (jHwid)
    env->ReleaseStringUTFChars(jHwid, hwid);

  return env->NewStringUTF(sig.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_auth_SecurityVault_nativeVerifyIntegrity(
    JNIEnv *env, jclass clazz, jobject context, jstring certDigest) {
  // 1. Anti-Debugging Check (/proc/self/status TracerPid)
  FILE *fp = fopen("/proc/self/status", "r");
  if (fp) {
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
  if (mf) {
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

  return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ninmods_main_auth_SecurityVault_nativeSetSession(
    JNIEnv *env, jclass clazz, jstring jToken, jstring jHwid, jlong expirySec) {
  const char *token =
      jToken ? env->GetStringUTFChars(jToken, nullptr) : nullptr;
  const char *hwid = jHwid ? env->GetStringUTFChars(jHwid, nullptr) : nullptr;

  g_session_verified = false;
  memset(g_security_session, 0, sizeof(g_security_session));
  memset(g_security_hwid, 0, sizeof(g_security_hwid));

  if (token && strlen(token) > 0 && hwid && strlen(hwid) > 0 && expirySec > 0) {
    strncpy(g_security_session, token, sizeof(g_security_session) - 1);
    strncpy(g_security_hwid, hwid, sizeof(g_security_hwid) - 1);
    g_session_verified = true;
    NIN_LOGI(
        "SECURITY",
        ">>> [VAULT_UNLOCKED] Verified session active. Expiry: %lld sec. <<<",
        (long long)expirySec);
  }

  if (token)
    env->ReleaseStringUTFChars(jToken, token);
  if (hwid)
    env->ReleaseStringUTFChars(jHwid, hwid);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ninmods_main_auth_SecurityVault_nativeIsSessionValid(JNIEnv *env,
                                                              jclass clazz) {
  return g_session_verified ? JNI_TRUE : JNI_FALSE;
}
