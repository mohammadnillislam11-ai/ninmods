#include "server.h"
#include <signal.h>

SocketServer::SocketServer() : listenfd_unix(-1), listenfd_tcp(-1), acceptfd(-1), isCreated(false) {
    memset(&addr_unix, 0, sizeof(addr_unix));
    memset(&addr_tcp, 0, sizeof(addr_tcp));
    memset(socket_name, 0, sizeof(socket_name));
}

SocketServer::~SocketServer() {
    Close();
}

bool SocketServer::Create() {
    listenfd_unix = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    listenfd_tcp = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    isCreated = (listenfd_unix >= 0 || listenfd_tcp >= 0);
    return isCreated;
}

bool SocketServer::Bind() {
    bool unix_bound = false;
    bool tcp_bound = false;

    if (listenfd_unix >= 0) {
        memset(&addr_unix, 0, sizeof(addr_unix));
        addr_unix.sun_family = AF_UNIX;
        addr_unix.sun_path[0] = '\0';
        size_t name_len = strlen(SOCKET_NAME);
        if (name_len > sizeof(addr_unix.sun_path) - 2) name_len = sizeof(addr_unix.sun_path) - 2;
        memcpy(&addr_unix.sun_path[1], SOCKET_NAME, name_len);

        socklen_t addr_len = offsetof(struct sockaddr_un, sun_path) + 1 + name_len;
        int opt = 1;
        setsockopt(listenfd_unix, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

        int bufSize = SOCKET_BUFFER_SIZE;
        setsockopt(listenfd_unix, SOL_SOCKET, SO_RCVBUF, &bufSize, sizeof(bufSize));
        setsockopt(listenfd_unix, SOL_SOCKET, SO_SNDBUF, &bufSize, sizeof(bufSize));

        if (bind(listenfd_unix, (sockaddr *) &addr_unix, addr_len) == 0) {
            unix_bound = true;
        } else {
            close(listenfd_unix);
            listenfd_unix = -1;
        }
    }

    if (listenfd_tcp >= 0) {
        int opt = 1;
        setsockopt(listenfd_tcp, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

        int bufSize = SOCKET_BUFFER_SIZE;
        setsockopt(listenfd_tcp, SOL_SOCKET, SO_RCVBUF, &bufSize, sizeof(bufSize));
        setsockopt(listenfd_tcp, SOL_SOCKET, SO_SNDBUF, &bufSize, sizeof(bufSize));

        memset(&addr_tcp, 0, sizeof(addr_tcp));
        addr_tcp.sin_family = AF_INET;
        addr_tcp.sin_port = htons(TCP_PORT);
        addr_tcp.sin_addr.s_addr = inet_addr(TCP_HOST);

        if (bind(listenfd_tcp, (sockaddr *) &addr_tcp, sizeof(addr_tcp)) == 0) {
            tcp_bound = true;
        } else {
            close(listenfd_tcp);
            listenfd_tcp = -1;
        }
    }

    if (!unix_bound && !tcp_bound) {
        Close();
        return false;
    }
    return true;
}

bool SocketServer::Listen() {
    bool listening = false;
    if (listenfd_unix >= 0) {
        if (listen(listenfd_unix, BACKLOG) == 0) {
            listening = true;
        }
    }
    if (listenfd_tcp >= 0) {
        if (listen(listenfd_tcp, BACKLOG) == 0) {
            listening = true;
        }
    }
    return listening;
}

bool SocketServer::Accept() {
    struct pollfd fds[2];
    int nfds = 0;

    if (listenfd_unix >= 0) {
        fds[nfds].fd = listenfd_unix;
        fds[nfds].events = POLLIN;
        fds[nfds].revents = 0;
        nfds++;
    }
    if (listenfd_tcp >= 0) {
        fds[nfds].fd = listenfd_tcp;
        fds[nfds].events = POLLIN;
        fds[nfds].revents = 0;
        nfds++;
    }

    if (nfds == 0) return false;

    int ret = poll(fds, nfds, 500); // 500ms timeout
    if (ret <= 0) return false;

    for (int i = 0; i < nfds; i++) {
        if (fds[i].revents & POLLIN) {
            std::lock_guard<std::mutex> lock(m_ioMutex);
            if (acceptfd >= 0) {
                close(acceptfd);
                acceptfd = -1;
            }
            acceptfd = accept4(fds[i].fd, nullptr, nullptr, SOCK_CLOEXEC);
            if (acceptfd >= 0) {
                int flag = 1;
                setsockopt(acceptfd, IPPROTO_TCP, TCP_NODELAY, (char*)&flag, sizeof(int));
#ifdef TCP_QUICKACK
                setsockopt(acceptfd, IPPROTO_TCP, TCP_QUICKACK, (char*)&flag, sizeof(int));
#endif
                int bufSize = SOCKET_BUFFER_SIZE;
                setsockopt(acceptfd, SOL_SOCKET, SO_RCVBUF, &bufSize, sizeof(bufSize));
                setsockopt(acceptfd, SOL_SOCKET, SO_SNDBUF, &bufSize, sizeof(bufSize));
                return true;
            }
        }
    }
    return false;
}

int SocketServer::sendData(void *inData, size_t size) {
    char *buffer = (char *) inData;
    int numSent = 0;

    while (size) {
        do {
            numSent = ::send(acceptfd, buffer, size, MSG_NOSIGNAL);
        } while (numSent == -1 && EINTR == errno);

        if (numSent <= 0) {
            CloseAccept();
            break;
        }

        size -= numSent;
        buffer += numSent;
    }
    return numSent;
}

bool SocketServer::send(void* inData, size_t size) {
    std::lock_guard<std::mutex> lock(m_ioMutex);
    if (acceptfd < 0) return false;

    uint32_t length = htonl(size);
    if (sendData(&length, sizeof(uint32_t)) <= 0) {
        return false;
    }
    return sendData((void*)inData, size) > 0;
}

int SocketServer::recvData(void *outData, size_t size) {
    char *buffer = (char *) outData;
    int numReceived = 0;

    while (size) {
        do {
            numReceived = ::recv(acceptfd, buffer, size, 0);
        } while (numReceived == -1 && EINTR == errno);

        if (numReceived <= 0) {
            CloseAccept();
            break;
        }

        size -= numReceived;
        buffer += numReceived;
    }
    return numReceived;
}

size_t SocketServer::receive(void* outData, size_t maxSize) {
    std::lock_guard<std::mutex> lock(m_ioMutex);
    if (acceptfd < 0) return 0;

    uint32_t length = 0;
    int code = recvData(&length, sizeof(uint32_t));
    if (code <= 0) return 0;

    length = ntohl(length);
    if (length == 0 || length > 0x100000) { // 1MB sanity limit
        CloseAccept();
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

void SocketServer::CloseAccept() {
    if (acceptfd >= 0) {
        close(acceptfd);
        acceptfd = -1;
    }
}

void SocketServer::Close() {
    std::lock_guard<std::mutex> lock(m_ioMutex);
    CloseAccept();
    if (listenfd_unix >= 0) {
        close(listenfd_unix);
        listenfd_unix = -1;
    }
    if (listenfd_tcp >= 0) {
        close(listenfd_tcp);
        listenfd_tcp = -1;
    }
    isCreated = false;
}