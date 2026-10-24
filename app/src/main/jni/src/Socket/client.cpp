#include "client.h"
#include <signal.h>

SocketClient::SocketClient() : sockfd(-1), created(false), connected(false), isTcpChannel(false) {
    memset(&addr_unix, 0, sizeof(addr_unix));
    memset(&addr_tcp, 0, sizeof(addr_tcp));
    memset(socket_name, 0, sizeof(socket_name));
}

SocketClient::~SocketClient() {
    Close();
}

bool SocketClient::Create() {
    created = true;
    connected = false;
    isTcpChannel = false;
    return true;
}

bool SocketClient::Connect() {
    std::lock_guard<std::mutex> lock(m_ioMutex);
    if (sockfd >= 0) {
        close(sockfd);
        sockfd = -1;
    }
    connected = false;

    // Channel 1: Abstract UNIX domain socket (\0nin_X9K2q)
    sockfd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (sockfd >= 0) {
        memset(&addr_unix, 0, sizeof(struct sockaddr_un));
        addr_unix.sun_family = AF_UNIX;
        addr_unix.sun_path[0] = '\0';
        size_t name_len = strlen(SOCKET_NAME);
        if (name_len > sizeof(addr_unix.sun_path) - 2) name_len = sizeof(addr_unix.sun_path) - 2;
        memcpy(&addr_unix.sun_path[1], SOCKET_NAME, name_len);

        socklen_t addr_len = offsetof(struct sockaddr_un, sun_path) + 1 + name_len;

        // Tune 256KB buffer for ultra-high FPS ESP data stream
        int bufSize = SOCKET_BUFFER_SIZE;
        setsockopt(sockfd, SOL_SOCKET, SO_RCVBUF, &bufSize, sizeof(bufSize));
        setsockopt(sockfd, SOL_SOCKET, SO_SNDBUF, &bufSize, sizeof(bufSize));

        if (connect(sockfd, (sockaddr *) &addr_unix, addr_len) == 0) {
            struct timeval tv;
            tv.tv_sec = 1;
            tv.tv_usec = 0; // 1000 ms timeout
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
    sockfd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (sockfd >= 0) {
        memset(&addr_tcp, 0, sizeof(addr_tcp));
        addr_tcp.sin_family = AF_INET;
        addr_tcp.sin_port = htons(TCP_PORT);
        addr_tcp.sin_addr.s_addr = inet_addr(TCP_HOST);

        int flag = 1;
        setsockopt(sockfd, IPPROTO_TCP, TCP_NODELAY, (char*)&flag, sizeof(int));
#ifdef TCP_QUICKACK
        setsockopt(sockfd, IPPROTO_TCP, TCP_QUICKACK, (char*)&flag, sizeof(int));
#endif
        int bufSize = SOCKET_BUFFER_SIZE;
        setsockopt(sockfd, SOL_SOCKET, SO_RCVBUF, &bufSize, sizeof(bufSize));
        setsockopt(sockfd, SOL_SOCKET, SO_SNDBUF, &bufSize, sizeof(bufSize));

        if (connect(sockfd, (sockaddr *) &addr_tcp, sizeof(addr_tcp)) == 0) {
            struct timeval tv;
            tv.tv_sec = 1;
            tv.tv_usec = 0; // 1000 ms timeout
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
    std::lock_guard<std::mutex> lock(m_ioMutex);
    if (!connected || sockfd < 0) return false;

    uint32_t length = htonl(size);
    if (sendData(&length, sizeof(uint32_t)) <= 0) {
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

size_t SocketClient::receive(void *outData, size_t maxSize) {
    std::lock_guard<std::mutex> lock(m_ioMutex);
    if (!connected || sockfd < 0) return 0;

    uint32_t length = 0;
    int code = recvData(&length, sizeof(uint32_t));
    if (code <= 0) return 0;

    length = ntohl(length);
    if (length == 0 || length > 0x100000) { // 1MB sanity limit
        Close();
        return 0;
    }

    if (maxSize > 0 && length > maxSize) {
        recvData(outData, maxSize);
        size_t excess = length - maxSize;
        char drain[256];
        while (excess > 0) {
            size_t chunk = (excess > sizeof(drain)) ? sizeof(drain) : excess;
            if (recvData(drain, chunk) <= 0) break;
            excess -= chunk;
        }
        return maxSize;
    }

    int rec = recvData(outData, static_cast<size_t>(length));
    return (rec > 0) ? static_cast<size_t>(rec) : 0;
}

void SocketClient::Close() {
    connected = false;
    created = false;
    if (sockfd >= 0) {
        close(sockfd);
        sockfd = -1;
    }
}
