#include "SocketServer.h"
#include <cstring>
#include <cerrno>
#include <fcntl.h>
#include <poll.h>
#include <android/log.h>

#define LOG_TAG "NinDaemonServer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

DaemonSocketServer::DaemonSocketServer() : m_unixFd(-1), m_tcpFd(-1), m_clientFd(-1) {
    memset(m_socketName, 0, sizeof(m_socketName));
}

DaemonSocketServer::~DaemonSocketServer() {
    Stop();
}

bool DaemonSocketServer::Start(const char* socketName, int tcpPort) {
    Stop();

    // 1. Channel 1: Linux Abstract UNIX Domain Socket
    m_unixFd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (m_unixFd >= 0) {
        struct sockaddr_un addr;
        memset(&addr, 0, sizeof(addr));
        addr.sun_family = AF_UNIX;
        addr.sun_path[0] = '\0';
        strncpy(addr.sun_path + 1, socketName, sizeof(addr.sun_path) - 2);
        socklen_t addrLen = sizeof(sa_family_t) + 1 + strlen(socketName);

        if (bind(m_unixFd, reinterpret_cast<struct sockaddr*>(&addr), addrLen) < 0) {
            LOGE("bind() failed for abstract socket \\0%s: %s", socketName, strerror(errno));
            close(m_unixFd);
            m_unixFd = -1;
        } else if (listen(m_unixFd, 4) < 0) {
            LOGE("listen() failed for abstract socket \\0%s: %s", socketName, strerror(errno));
            close(m_unixFd);
            m_unixFd = -1;
        } else {
            strncpy(m_socketName, socketName, sizeof(m_socketName) - 1);
            LOGI("Channel 1: Abstract socket listening on \\0%s (fd=%d)", socketName, m_unixFd);
        }
    }

    // 2. Channel 2: Local TCP Loopback (127.0.0.1:49221) - Immune to SELinux socket policy denials
    m_tcpFd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (m_tcpFd >= 0) {
        int opt = 1;
        setsockopt(m_tcpFd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

        struct sockaddr_in inAddr;
        memset(&inAddr, 0, sizeof(inAddr));
        inAddr.sin_family = AF_INET;
        inAddr.sin_port = htons(tcpPort);
        inAddr.sin_addr.s_addr = htonl(INADDR_LOOPBACK); // 127.0.0.1

        if (bind(m_tcpFd, reinterpret_cast<struct sockaddr*>(&inAddr), sizeof(inAddr)) < 0) {
            LOGE("bind() failed for TCP loopback 127.0.0.1:%d: %s", tcpPort, strerror(errno));
            close(m_tcpFd);
            m_tcpFd = -1;
        } else if (listen(m_tcpFd, 4) < 0) {
            LOGE("listen() failed for TCP loopback 127.0.0.1:%d: %s", tcpPort, strerror(errno));
            close(m_tcpFd);
            m_tcpFd = -1;
        } else {
            LOGI("Channel 2: TCP loopback listening on 127.0.0.1:%d (fd=%d)", tcpPort, m_tcpFd);
        }
    }

    return (m_unixFd >= 0 || m_tcpFd >= 0);
}

bool DaemonSocketServer::AcceptClient() {
    if (m_unixFd < 0 && m_tcpFd < 0) return false;
    CloseClient();

    struct pollfd pfd[2];
    int nfds = 0;
    int unixIdx = -1, tcpIdx = -1;

    if (m_unixFd >= 0) {
        unixIdx = nfds;
        pfd[nfds].fd = m_unixFd;
        pfd[nfds].events = POLLIN;
        pfd[nfds].revents = 0;
        nfds++;
    }
    if (m_tcpFd >= 0) {
        tcpIdx = nfds;
        pfd[nfds].fd = m_tcpFd;
        pfd[nfds].events = POLLIN;
        pfd[nfds].revents = 0;
        nfds++;
    }

    int ret = poll(pfd, nfds, 50); // 50ms polling timeout
    if (ret <= 0) return false;

    if (unixIdx >= 0 && (pfd[unixIdx].revents & POLLIN)) {
        m_clientFd = accept4(m_unixFd, nullptr, nullptr, SOCK_CLOEXEC);
        if (m_clientFd >= 0) {
            LOGI("Client accepted on Channel 1 (Abstract UNIX fd=%d)", m_clientFd);
            return true;
        }
    }

    if (tcpIdx >= 0 && (pfd[tcpIdx].revents & POLLIN)) {
        m_clientFd = accept4(m_tcpFd, nullptr, nullptr, SOCK_CLOEXEC);
        if (m_clientFd >= 0) {
            LOGI("Client accepted on Channel 2 (TCP loopback fd=%d)", m_clientFd);
            return true;
        }
    }

    return false;
}

void DaemonSocketServer::CloseClient() {
    if (m_clientFd >= 0) {
        close(m_clientFd);
        m_clientFd = -1;
    }
}

void DaemonSocketServer::Stop() {
    CloseClient();
    if (m_unixFd >= 0) {
        close(m_unixFd);
        m_unixFd = -1;
    }
    if (m_tcpFd >= 0) {
        close(m_tcpFd);
        m_tcpFd = -1;
    }
}

bool DaemonSocketServer::SendAll(int fd, const void* data, size_t size) {
    if (fd < 0 || !data || size == 0) return false;
    const uint8_t* ptr = static_cast<const uint8_t*>(data);
    size_t remaining = size;

    while (remaining > 0) {
        ssize_t n = write(fd, ptr, remaining);
        if (n < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        if (n == 0) return false;
        remaining -= static_cast<size_t>(n);
        ptr += n;
    }
    return true;
}

bool DaemonSocketServer::RecvAll(int fd, void* data, size_t size) {
    if (fd < 0 || !data || size == 0) return false;
    uint8_t* ptr = static_cast<uint8_t*>(data);
    size_t remaining = size;

    while (remaining > 0) {
        ssize_t n = read(fd, ptr, remaining);
        if (n < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        if (n == 0) return false;
        remaining -= static_cast<size_t>(n);
        ptr += n;
    }
    return true;
}

bool DaemonSocketServer::SendResponse(const NinResponse& resp) {
    if (m_clientFd < 0) return false;
    return SendAll(m_clientFd, &resp, sizeof(resp));
}

bool DaemonSocketServer::ReceiveRequest(NinRequest& req) {
    if (m_clientFd < 0) return false;
    if (!RecvAll(m_clientFd, &req, sizeof(req))) {
        CloseClient();
        return false;
    }
    return req.magic == NIN_MAGIC;
}
