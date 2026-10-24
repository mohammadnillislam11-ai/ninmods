#ifndef TCP_SOCKET_CLIENT_H
#define TCP_SOCKET_CLIENT_H
#include "Const.h"

class SocketClient {
public:
    int sockfd;
    sockaddr_un addr_unix;
    sockaddr_in addr_tcp;
    char socket_name[108]; // 108 sun_path length max
    bool created, connected;
    bool isTcpChannel;

    SocketClient();

    bool Create();
    bool Connect();
    void Close();

    int sendData(void *inData, size_t size);
    bool send(void* inData, size_t size);

    int recvData(void *outData, size_t size);
    size_t receive(void* outData, size_t maxSize = 0x20000);
};

#endif
