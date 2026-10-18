#include <jni.h>
#include "EntityScanner.h"
#include "DriverClient.h"
#include "GameOffsets.h"
#include "daemon/SocketClient.h"

extern "C" {

JNIEXPORT jint JNICALL
Java_com_ninmods_menu_ipc_DriverBridge_nativeScanEsp(
    JNIEnv* env,
    jobject,
    jint pid,
    jlong handle,
    jboolean isMax,
    jfloat screenW,
    jfloat screenH,
    jobject directBuffer,
    jlongArray outDiag,
    jfloat aimFov
) {
    if (!directBuffer || pid <= 0) return 0;
    jfloat* rawBuf = static_cast<jfloat*>(env->GetDirectBufferAddress(directBuffer));
    if (!rawBuf) return 0;
    jlong capacity = env->GetDirectBufferCapacity(directBuffer);
    int maxEntities = (capacity / sizeof(float)) / 10;
    if (maxEntities <= 0) return 0;

    auto& client = GetDaemonClient();
    if (!client.IsConnected()) {
        client.Connect(NIN_SOCKET_NAME);
    }

    if (client.IsConnected()) {
        uint64_t diagBuf[10] = {0};
        int rendered = client.ScanEsp(
            pid,
            isMax == JNI_TRUE,
            screenW,
            screenH,
            aimFov,
            rawBuf,
            maxEntities,
            diagBuf
        );

        if (outDiag) {
            jsize dLen = env->GetArrayLength(outDiag);
            jsize toCopy = (dLen < 10) ? dLen : 10;
            jlong d[10];
            for (int i = 0; i < toCopy; ++i) {
                d[i] = static_cast<jlong>(diagBuf[i]);
            }
            env->SetLongArrayRegion(outDiag, 0, toCopy, d);
        }
        return rendered;
    }

    // Direct memory fallback if driver is attached
    ScanDiagnostics diag{};
    const int driverFd = DriverGetOpenFd();
    uint64_t h = static_cast<uint64_t>(handle);
    if (h == 0) return 0;

    int rendered = ScanAndComputeEsp(
        pid,
        h,
        driverFd,
        isMax == JNI_TRUE,
        screenW,
        screenH,
        rawBuf,
        maxEntities,
        &diag,
        static_cast<float>(aimFov)
    );

    if (outDiag) {
        jsize dLen = env->GetArrayLength(outDiag);
        jsize toCopy = (dLen < 10) ? dLen : 10;
        jlong d[10];
        d[0] = static_cast<jlong>(diag.libBase);
        d[1] = static_cast<jlong>(diag.gameFacade);
        d[2] = static_cast<jlong>(diag.matchPtr);
        d[3] = static_cast<jlong>(diag.localPlayer);
        d[4] = static_cast<jlong>(diag.dictCount);
        d[5] = static_cast<jlong>(diag.matrixValid ? 1 : 0);
        d[6] = static_cast<jlong>(diag.renderedCount);
        d[7] = static_cast<jlong>(diag.bestAimPitch * 1000.0f);
        d[8] = static_cast<jlong>(diag.bestAimYaw * 1000.0f);
        d[9] = static_cast<jlong>(diag.hasAimTarget ? 1 : 0);
        env->SetLongArrayRegion(outDiag, 0, toCopy, d);
    }

    return rendered;
}

JNIEXPORT jboolean JNICALL
Java_com_ninmods_menu_ipc_DriverBridge_nativeWriteAim(
    JNIEnv*,
    jobject,
    jint pid,
    jlong handle,
    jlong localPlayer,
    jfloat targetPitch,
    jfloat targetYaw,
    jfloat smoothFactor,
    jboolean isMax
) {
    if (localPlayer == 0 || pid <= 0) return JNI_FALSE;

    auto& client = GetDaemonClient();
    if (!client.IsConnected()) {
        client.Connect(NIN_SOCKET_NAME);
    }

    if (client.IsConnected()) {
        return client.WriteAim(pid, static_cast<uint64_t>(localPlayer), targetPitch, targetYaw, smoothFactor, isMax == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
    }

    // Direct memory fallback
    const int driverFd = DriverGetOpenFd();
    uint64_t h = static_cast<uint64_t>(handle);
    if (h == 0) return JNI_FALSE;

    uint8_t elfClass = 2; // default 64-bit
    uint64_t base = DriverGetModuleBase(driverFd, static_cast<uint32_t>(pid), "libil2cpp.so");
    if (base != 0) {
        DriverRead(driverFd, h, base + 4, &elfClass, 1);
    }
    bool is64 = (elfClass != 1);
    const GameOffsets* off = GetActiveOffsets(isMax == JNI_TRUE, is64);
    uint64_t aimAddr = static_cast<uint64_t>(localPlayer) + off->aimRotation;

    float cur[2] = {0.0f, 0.0f};
    bool rOk = DriverRead(driverFd, h, aimAddr, cur, sizeof(cur));
    if (!rOk && off->aimRotationFallback != 0) {
        aimAddr = static_cast<uint64_t>(localPlayer) + off->aimRotationFallback;
        rOk = DriverRead(driverFd, h, aimAddr, cur, sizeof(cur));
    }
    if (!rOk) return JNI_FALSE;

    float s = (smoothFactor >= 1.0f) ? smoothFactor : 5.0f;
    float nextAngles[2] = {
        cur[0] + (targetPitch - cur[0]) / s,
        cur[1] + (targetYaw - cur[1]) / s
    };

    return DriverWrite(driverFd, h, aimAddr, nextAngles, sizeof(nextAngles)) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
