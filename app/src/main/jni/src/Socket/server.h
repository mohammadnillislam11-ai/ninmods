#ifndef TCP_SOCKET_SERVER_H
#define TCP_SOCKET_SERVER_H

#include "Const.h"
#include <mutex>
#include <atomic>

class SocketServer {
public:
    int listenfd_unix;
    int listenfd_tcp;
    int acceptfd;
    std::atomic<bool> isCreated;
    sockaddr_un addr_unix;
    sockaddr_in addr_tcp;
    char socket_name[108]; // 108 sun_path length max
    mutable std::mutex m_ioMutex;

    SocketServer();
    ~SocketServer();

    bool Create();
    bool Accept();
    bool Bind();
    bool Listen();
    void Close();
    void CloseAccept();

    int sendData(void *inData, size_t size);
    bool send(void* inData, size_t size);

    int recvData(void *outData, size_t size);
    size_t receive(void* outData, size_t maxSize = 0x20000);
};

#endif
