#include <jni.h>
#include <vector>
#include "DriverClient.h"
#include "daemon/SocketClient.h"
#include "daemon/DaemonMem.h"

extern "C" {

JNIEXPORT jint JNICALL
Java_com_ninmods_menu_ipc_DriverBridge_nativeOpen(JNIEnv*, jobject, jint mode) {
    auto& client = GetDaemonClient();
    if (!client.IsConnected()) {
        client.Connect(NIN_SOCKET_NAME);
    }
    return DriverOpen(static_cast<DriverMode>(mode));
}

JNIEXPORT void JNICALL
Java_com_ninmods_menu_ipc_DriverBridge_nativeClose(JNIEnv*, jobject, jint fd) {
    auto& client = GetDaemonClient();
    client.Disconnect();
    DriverClose(fd);
}

JNIEXPORT jboolean JNICALL
Java_com_ninmods_menu_ipc_DriverBridge_nativePingDaemon(JNIEnv*, jobject) {
    auto& client = GetDaemonClient();
    if (!client.IsConnected()) {
        client.Connect(NIN_SOCKET_NAME);
    }
    return client.Ping() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_ninmods_menu_ipc_DriverBridge_nativeAttach(JNIEnv*, jobject, jint fd, jint pid) {
    auto& client = GetDaemonClient();
    if (!client.IsConnected()) {
        client.Connect(NIN_SOCKET_NAME);
    }
    if (client.IsConnected()) {
        uint64_t libBase = 0;
        if (client.Attach(pid, false, libBase)) {
            return static_cast<jlong>(pid);
        }
    }
    return (jlong)DriverAttach(fd, (uint32_t)pid);
}

JNIEXPORT void JNICALL
Java_com_ninmods_menu_ipc_DriverBridge_nativeDetach(JNIEnv*, jobject, jlong handle) {
    const int fd = DriverGetOpenFd();
    if (fd >= 0) DriverDetach(fd, (uint64_t)handle);
}

JNIEXPORT jbyteArray JNICALL
Java_com_ninmods_menu_ipc_DriverBridge_nativeReadBytes(JNIEnv* env, jobject, jlong handle, jlong addr, jint size) {
    if (size <= 0 || size > 65536) return nullptr;
    const int fd = DriverGetOpenFd();
    std::vector<uint8_t> buffer(size);
    if (!DriverRead(fd, (uint64_t)handle, (uint64_t)addr, buffer.data(), size)) {
        return nullptr;
    }
    jbyteArray arr = env->NewByteArray(size);
    env->SetByteArrayRegion(arr, 0, size, reinterpret_cast<jbyte*>(buffer.data()));
    return arr;
}

JNIEXPORT jlong JNICALL
Java_com_ninmods_menu_ipc_DriverBridge_nativeGetModuleBase(JNIEnv* env, jobject, jint pid, jstring modName) {
    if (!modName || pid <= 0) return 0;
    const char* str = env->GetStringUTFChars(modName, nullptr);
    if (!str) return 0;

    uint64_t base = 0;

    // Fast-path: Check if already resolved and cached
    if (strcmp(str, "libil2cpp.so") == 0 && MasterMem::Get().GetCachedIl2cppBase() > 0) {
        base = MasterMem::Get().GetCachedIl2cppBase();
    }

    // 1. Root Daemon channel (highest privilege, bypasses all SELinux domains)
    if (base == 0) {
        auto& client = GetDaemonClient();
        if (!client.IsConnected()) {
            client.Connect(NIN_SOCKET_NAME);
        }
        if (client.IsConnected()) {
            uint64_t daemonBase = 0;
            if (client.Attach(pid, false, daemonBase) && daemonBase > 0) {
                base = daemonBase;
                MasterMem::Get().SetIl2cppBase(base);
            }
        }
    }

    // 2. Direct Driver / Ring-0 Kernel IOCTL
    if (base == 0) {
        const int fd = DriverGetOpenFd();
        base = DriverGetModuleBase(fd, (uint32_t)pid, str);
        if (base > 0) {
            MasterMem::Get().SetIl2cppBase(base);
        }
    }

    // 3. In-process direct /proc/<pid>/maps parse fallback
    if (base == 0) {
        char mapPath[64];
        snprintf(mapPath, sizeof(mapPath), "/proc/%d/maps", pid);
        FILE* fp = fopen(mapPath, "r");
        if (fp) {
            char line[512];
            uint64_t elf1 = 0, elf2 = 0;
            uint64_t fallback = 0;
            while (fgets(line, sizeof(line), fp)) {
                if (strstr(line, str)) {
                    unsigned long long start = 0;
                    if (sscanf(line, "%llx-", &start) == 1 && start != 0) {
                        if (fallback == 0) fallback = static_cast<uint64_t>(start);
                        unsigned char magic[4] = {0};
                        if (MasterMem::Get().Read(static_cast<uint64_t>(start), magic, 4)) {
                            if (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F') {
                                if (elf1 == 0) elf1 = static_cast<uint64_t>(start);
                                else if (elf2 == 0) elf2 = static_cast<uint64_t>(start);
                            }
                        }
                    }
                }
            }
            fclose(fp);
            base = (elf2 != 0) ? elf2 : ((elf1 != 0) ? elf1 : fallback);
            if (base > 0) {
                MasterMem::Get().SetIl2cppBase(base);
            }
        }
    }

    env->ReleaseStringUTFChars(modName, str);
    return (jlong)base;
}

JNIEXPORT jstring JNICALL
Java_com_ninmods_menu_ipc_DriverBridge_nativeGetActiveNode(JNIEnv* env, jobject) {
    auto& client = GetDaemonClient();
    if (client.IsConnected() && client.Ping()) {
        return env->NewStringUTF("MASTER_DAEMON");
    }
    const char* path = DriverGetActiveNode();
    return env->NewStringUTF(path ? path : "");
}

}
