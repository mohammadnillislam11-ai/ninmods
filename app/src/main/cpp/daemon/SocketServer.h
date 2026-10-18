#pragma once
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <cstdint>
#include <cstddef>
#include "DaemonProtocol.h"

class DaemonSocketServer {
public:
    DaemonSocketServer();
    ~DaemonSocketServer();

    bool Start(const char* socketName = NIN_SOCKET_NAME, int tcpPort = NIN_TCP_PORT);
    bool AcceptClient();
    bool SendResponse(const NinResponse& resp);
    bool ReceiveRequest(NinRequest& req);
    void CloseClient();
    void Stop();

    bool IsRunning() const { return m_unixFd >= 0 || m_tcpFd >= 0; }
    bool HasClient() const { return m_clientFd >= 0; }

private:
    int m_unixFd;
    int m_tcpFd;
    int m_clientFd;
    char m_socketName[108];

    bool SendAll(int fd, const void* data, size_t size);
    bool RecvAll(int fd, void* data, size_t size);
};
