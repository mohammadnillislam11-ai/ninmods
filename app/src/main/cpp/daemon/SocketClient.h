#pragma once
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <cstdint>
#include <cstddef>
#include <mutex>
#include "DaemonProtocol.h"

class DaemonSocketClient {
public:
    DaemonSocketClient();
    ~DaemonSocketClient();

    bool Connect(const char* socketName = NIN_SOCKET_NAME, int tcpPort = NIN_TCP_PORT);
    bool IsConnected() const { return m_fd >= 0; }
    void Disconnect();

    bool SendRequest(const NinRequest& req);
    bool ReceiveResponse(NinResponse& resp);

    // High-level helper methods
    bool Ping();
    bool Attach(int32_t pid, bool isMax, uint64_t& outLibBase);
    int ScanEsp(int32_t pid, bool isMax, float screenW, float screenH, float aimFov,
                float* outEntities, size_t maxEntities, uint64_t* outDiag);
    bool WriteAim(int32_t pid, uint64_t localPlayer, float targetPitch, float targetYaw, float smooth, bool isMax);
    bool UpdateCombat(int32_t pid, uint32_t combatFlags, float flyHeight, float speedMult);

private:
    std::mutex m_mutex;
    int m_fd;
    char m_socketName[108];

    bool SendAll(const void* data, size_t size);
    bool RecvAll(void* data, size_t size);
};

// Global singleton instance for the app
DaemonSocketClient& GetDaemonClient();
