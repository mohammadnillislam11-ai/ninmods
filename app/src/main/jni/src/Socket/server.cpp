#include "server.h"
#include <signal.h>

SocketServer::SocketServer(){
    listenfd_unix = -1;
    listenfd_tcp = -1;
    acceptfd = -1;
    isCreated = false;
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
        memset(socket_name, 0, sizeof(socket_name));
        memcpy(&socket_name[0], "\0", 1);
        strcpy(&socket_name[1], SOCKET_NAME);

        memset(&addr_unix, 0, sizeof(addr_unix));
        addr_unix.sun_family = AF_UNIX;
        strncpy(addr_unix.sun_path, socket_name, sizeof(addr_unix.sun_path) - 1);

        int opt = 1;
        setsockopt(listenfd_unix, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

        if (bind(listenfd_unix, (sockaddr *) &addr_unix, sizeof(addr_unix)) == 0) {
            unix_bound = true;
        } else {
            close(listenfd_unix);
            listenfd_unix = -1;
        }
    }

    if (listenfd_tcp >= 0) {
        int opt = 1;
        setsockopt(listenfd_tcp, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

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
            acceptfd = accept4(fds[i].fd, nullptr, nullptr, SOCK_CLOEXEC);
            if (acceptfd >= 0) {
                int flag = 1;
                setsockopt(acceptfd, IPPROTO_TCP, TCP_NODELAY, (char*)&flag, sizeof(int));
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
            numSent = send(acceptfd, buffer, size, MSG_NOSIGNAL);
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
    uint32_t length = htonl(size);
    if(sendData(&length, sizeof(uint32_t)) <= 0){
        return false;
    }
    return sendData((void*)inData, size) > 0;
}

int SocketServer::recvData(void *outData, size_t size) {
    char *buffer = (char *) outData;
    int numReceived = 0;

    while (size) {
        do {
            numReceived = recv(acceptfd, buffer, size, 0);
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

size_t SocketServer::receive(void* outData) {
    uint32_t length = 0;
    int code = recvData(&length, sizeof(uint32_t));
    if(code > 0){
        length = ntohl(length);
        recvData(outData, static_cast<size_t>(length));
    }
    return length;
}

void SocketServer::CloseAccept() {
    if (acceptfd >= 0) {
        close(acceptfd);
        acceptfd = -1;
    }
}

void SocketServer::Close() {
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