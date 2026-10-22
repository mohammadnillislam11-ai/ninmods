#include "client.h"
#include <signal.h>

SocketClient::SocketClient(){
    sockfd = -1;
    created = false;
    connected = false;
    isTcpChannel = false;
}

bool SocketClient::Create() {
    created = true;
    connected = false;
    isTcpChannel = false;
    return true;
}

bool SocketClient::Connect() {
    Close();

    // Channel 1: Abstract UNIX domain socket (\0nin_X9K2q)
    sockfd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (sockfd >= 0) {
        memset(socket_name, 0, sizeof(socket_name));
        memcpy(&socket_name[0], "\0", 1);
        strcpy(&socket_name[1], SOCKET_NAME);

        memset(&addr_unix, 0, sizeof(struct sockaddr_un));
        addr_unix.sun_family = AF_UNIX;
        strncpy(addr_unix.sun_path, socket_name, sizeof(addr_unix.sun_path) - 1);

        if (connect(sockfd, (sockaddr *) &addr_unix, sizeof(addr_unix)) == 0) {
            struct timeval tv;
            tv.tv_sec = 1;
            tv.tv_usec = 0; // 1000 ms (1 second) timeout
            setsockopt(sockfd, SOL_SOCKET, SO_RCVTIMEO, (const char*)&tv, sizeof(tv));
            setsockopt(sockfd, SOL_SOCKET, SO_SNDTIMEO, (const char*)&tv, sizeof(tv));
            connected = true;
            created = true;
            isTcpChannel = false;
            return true;
        } else {
            close(sockfd);
            sockfd = -1;
        }
    }

    // Channel 2 (SELinux Fallback): TCP Loopback (127.0.0.1:49221)
    // SELinux does not restrict non-root apps with INTERNET permission from connecting to localhost TCP!
    sockfd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (sockfd >= 0) {
        memset(&addr_tcp, 0, sizeof(addr_tcp));
        addr_tcp.sin_family = AF_INET;
        addr_tcp.sin_port = htons(TCP_PORT);
        addr_tcp.sin_addr.s_addr = inet_addr(TCP_HOST);

        if (connect(sockfd, (sockaddr *) &addr_tcp, sizeof(addr_tcp)) == 0) {
            int flag = 1;
            setsockopt(sockfd, IPPROTO_TCP, TCP_NODELAY, (char*)&flag, sizeof(int));

            struct timeval tv;
            tv.tv_sec = 1;
            tv.tv_usec = 0; // 1000 ms (1 second) timeout
            setsockopt(sockfd, SOL_SOCKET, SO_RCVTIMEO, (const char*)&tv, sizeof(tv));
            setsockopt(sockfd, SOL_SOCKET, SO_SNDTIMEO, (const char*)&tv, sizeof(tv));
            connected = true;
            created = true;
            isTcpChannel = true;
            return true;
        } else {
            close(sockfd);
            sockfd = -1;
        }
    }

    connected = false;
    created = false;
    return false;
}

int SocketClient::sendData(void *inData, size_t size) {
    char *buffer = (char *) inData;
    int numSent = 0;

    while (size) {
        do {
            numSent = ::send(sockfd, buffer, size, MSG_NOSIGNAL);
        } while (numSent == -1 && EINTR == errno);

        if (numSent <= 0) {
            Close();
            break;
        }

        size -= numSent;
        buffer += numSent;
    }
    return numSent;
}

bool SocketClient::send(void *inData, size_t size) {
    uint32_t length = htonl(size);
    if(sendData(&length, sizeof(uint32_t)) <= 0){
        return false;
    }
    return sendData((void*)inData, size) > 0;
}

int SocketClient::recvData(void *outData, size_t size) {
    char *buffer = (char *) outData;
    int numReceived = 0;

    while (size) {
        do {
            numReceived = ::recv(sockfd, buffer, size, 0);
        } while (numReceived == -1 && EINTR == errno);

        if (numReceived <= 0) {
            Close();
            break;
        }

        size -= numReceived;
        buffer += numReceived;
    }
    return numReceived;
}

size_t SocketClient::receive(void *outData) {
    uint32_t length = 0;
    int code = recvData(&length, sizeof(uint32_t));
    if(code > 0){
        length = ntohl(length);
        recvData(outData, static_cast<size_t>(length));
    }
    return length;
}

void SocketClient::Close() {
    connected = false;
    created = false;
    if (sockfd >= 0) {
        close(sockfd);
        sockfd = -1;
    }
}
