#ifndef TCP_SOCKET_CLIENT_H
#define TCP_SOCKET_CLIENT_H
#include "Const.h"
#include <mutex>
#include <atomic>

class SocketClient {
public:
    int sockfd;
    sockaddr_un addr_unix;
    sockaddr_in addr_tcp;
    char socket_name[108]; // 108 sun_path length max
    std::atomic<bool> created;
    std::atomic<bool> connected;
    std::atomic<bool> isTcpChannel;
    mutable std::mutex m_ioMutex;

    SocketClient();
    ~SocketClient();

    bool Create();
    bool Connect();
    void Close();

    int sendData(void *inData, size_t size);
    bool send(void* inData, size_t size);

    int recvData(void *outData, size_t size);
    size_t receive(void* outData, size_t maxSize = 0x20000);
};

#endif
