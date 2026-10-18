
#include "SocketClient.h"
#include <cstring>
#include <cerrno>
#include <algorithm>
#include <netinet/tcp.h>
#include <android/log.h>

#define LOG_TAG "NinDaemonClient"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

DaemonSocketClient::DaemonSocketClient() : m_fd(-1) {
    memset(m_socketName, 0, sizeof(m_socketName));
}

DaemonSocketClient::~DaemonSocketClient() {
    Disconnect();
}

bool DaemonSocketClient::Connect(const char* socketName, int tcpPort) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_fd >= 0) {
        close(m_fd);
        m_fd = -1;
    }

    // Channel 1: Attempt Linux Abstract UNIX Domain Socket
    int unixSock = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (unixSock >= 0) {
        struct sockaddr_un addr;
        memset(&addr, 0, sizeof(addr));
        addr.sun_family = AF_UNIX;
        addr.sun_path[0] = '\0';
        strncpy(addr.sun_path + 1, socketName, sizeof(addr.sun_path) - 2);

        socklen_t addrLen = sizeof(sa_family_t) + 1 + strlen(socketName);

        if (connect(unixSock, reinterpret_cast<struct sockaddr*>(&addr), addrLen) == 0) {
            m_fd = unixSock;
            strncpy(m_socketName, socketName, sizeof(m_socketName) - 1);
            LOGI("Connected to daemon via Channel 1 (Abstract UNIX \\0%s, fd=%d)", socketName, m_fd);
            return true;
        }
        close(unixSock);
    }

    // Channel 2: Fallback to Local TCP Loopback (127.0.0.1:49221)
    int tcpSock = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (tcpSock >= 0) {
        int nodelay = 1;
        setsockopt(tcpSock, IPPROTO_TCP, TCP_NODELAY, reinterpret_cast<char*>(&nodelay), sizeof(nodelay));

        struct sockaddr_in srvAddr;
        memset(&srvAddr, 0, sizeof(srvAddr));
        srvAddr.sin_family = AF_INET;
        srvAddr.sin_port = htons(tcpPort);
        srvAddr.sin_addr.s_addr = htonl(INADDR_LOOPBACK); // 127.0.0.1

        if (connect(tcpSock, reinterpret_cast<struct sockaddr*>(&srvAddr), sizeof(srvAddr)) == 0) {
            m_fd = tcpSock;
            snprintf(m_socketName, sizeof(m_socketName), "127.0.0.1:%d", tcpPort);
            LOGI("Connected to daemon via Channel 2 (TCP Loopback 127.0.0.1:%d, fd=%d)", tcpPort, m_fd);
            return true;
        }
        close(tcpSock);
    }

    m_fd = -1;
    return false;
}

void DaemonSocketClient::Disconnect() {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_fd >= 0) {
        close(m_fd);
        m_fd = -1;
    }
}

bool DaemonSocketClient::SendAll(const void* data, size_t size) {
    if (m_fd < 0 || !data || size == 0) return false;
    const uint8_t* ptr = static_cast<const uint8_t*>(data);
    size_t remaining = size;

    while (remaining > 0) {
        ssize_t n = write(m_fd, ptr, remaining);
        if (n < 0) {
            if (errno == EINTR) continue;
            close(m_fd);
            m_fd = -1;
            return false;
        }
        if (n == 0) {
            close(m_fd);
            m_fd = -1;
            return false;
        }
        remaining -= static_cast<size_t>(n);
        ptr += n;
    }
    return true;
}

bool DaemonSocketClient::RecvAll(void* data, size_t size) {
    if (m_fd < 0 || !data || size == 0) return false;
    uint8_t* ptr = static_cast<uint8_t*>(data);
    size_t remaining = size;

    while (remaining > 0) {
        ssize_t n = read(m_fd, ptr, remaining);
        if (n < 0) {
            if (errno == EINTR) continue;
            close(m_fd);
            m_fd = -1;
            return false;
        }
        if (n == 0) {
            close(m_fd);
            m_fd = -1;
            return false;
        }
        remaining -= static_cast<size_t>(n);
        ptr += n;
    }
    return true;
}

bool DaemonSocketClient::SendRequest(const NinRequest& req) {
    if (m_fd < 0) return false;
    return SendAll(&req, sizeof(req));
}

bool DaemonSocketClient::ReceiveResponse(NinResponse& resp) {
    if (m_fd < 0) return false;
    if (!RecvAll(&resp, sizeof(resp))) return false;
    return resp.magic == NIN_MAGIC;
}

bool DaemonSocketClient::Ping() {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_fd < 0) return false;

    NinRequest req{};
    req.magic = NIN_MAGIC;
    req.cmd = CMD_PING;
    if (!SendRequest(req)) return false;

    NinResponse resp{};
    if (!ReceiveResponse(resp)) return false;
    return resp.status == 0;
}

bool DaemonSocketClient::Attach(int32_t pid, bool isMax, uint64_t& outLibBase) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_fd < 0 || pid <= 0) return false;

    NinRequest req{};
    req.magic = NIN_MAGIC;
    req.cmd = CMD_ATTACH;
    req.pid = pid;
    req.isMax = isMax ? 1 : 0;
    if (!SendRequest(req)) return false;

    NinResponse resp{};
    if (!ReceiveResponse(resp)) return false;
    outLibBase = resp.diag[0];
    return resp.status == 0;
}

int DaemonSocketClient::ScanEsp(int32_t pid, bool isMax, float screenW, float screenH, float aimFov,
                                float* outEntities, size_t maxEntities, uint64_t* outDiag) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_fd < 0 || !outEntities || maxEntities == 0 || pid <= 0) return 0;

    NinRequest req{};
    req.magic = NIN_MAGIC;
    req.cmd = CMD_SCAN_ESP;
    req.pid = pid;
    req.isMax = isMax ? 1 : 0;
    req.screenW = screenW;
    req.screenH = screenH;
    req.aimFov = aimFov;

    if (!SendRequest(req)) return 0;

    NinResponse resp{};
    if (!ReceiveResponse(resp)) return 0;
    if (resp.status != 0) return 0;

    int count = static_cast<int>(std::min(static_cast<size_t>(resp.renderedCount), maxEntities));
    if (count > 0) {
        memcpy(outEntities, resp.entities, count * NIN_ENTITY_STRIDE * sizeof(float));
    }
    if (outDiag) {
        memcpy(outDiag, resp.diag, 10 * sizeof(uint64_t));
    }
    return count;
}

bool DaemonSocketClient::WriteAim(int32_t pid, uint64_t localPlayer, float targetPitch, float targetYaw, float smooth, bool isMax) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_fd < 0 || pid <= 0) return false;

    NinRequest req{};
    req.magic = NIN_MAGIC;
    req.cmd = CMD_WRITE_AIM;
    req.pid = pid;
    req.localPlayer = localPlayer;
    req.targetPitch = targetPitch;
    req.targetYaw = targetYaw;
    req.smooth = smooth;
    req.isMax = isMax ? 1 : 0;

    if (!SendRequest(req)) return false;

    NinResponse resp{};
    if (!ReceiveResponse(resp)) return false;
    return resp.status == 0;
}

bool DaemonSocketClient::UpdateCombat(int32_t pid, uint32_t combatFlags, float flyHeight, float speedMult) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_fd < 0 || pid <= 0) return false;

    NinRequest req{};
    req.magic = NIN_MAGIC;
    req.cmd = CMD_COMBAT_MOD;
    req.pid = pid;
    req.combatFlags = combatFlags;
    req.flyHeight = flyHeight;
    req.speedMult = speedMult;

    if (!SendRequest(req)) return false;

    NinResponse resp{};
    if (!ReceiveResponse(resp)) return false;
    return resp.status == 0;
}

DaemonSocketClient& GetDaemonClient() {
    static DaemonSocketClient s_client;
    return s_client;
}
