#ifndef NINLITE_SOCKET_SERVER_H
#define NINLITE_SOCKET_SERVER_H

#if __has_include(<unistd.h>)
#include <unistd.h>
#else
#include "../Includes/android_ide_stubs/unistd.h"
#endif

#if __has_include(<sys/types.h>)
#include <sys/types.h>
#else
#include "../Includes/android_ide_stubs/sys/types.h"
#endif

#if __has_include(<sys/socket.h>)
#include <sys/socket.h>
#else
#include "../Includes/android_ide_stubs/sys/socket.h"
#endif

#if __has_include(<netinet/in.h>)
#include <netinet/in.h>
#else
#include "../Includes/android_ide_stubs/netinet/in.h"
#endif

#if __has_include(<arpa/inet.h>)
#include <arpa/inet.h>
#else
#include "../Includes/android_ide_stubs/arpa/inet.h"
#endif

#if __has_include(<netdb.h>)
#include <netdb.h>
#else
#include "../Includes/android_ide_stubs/netdb.h"
#endif

#if __has_include(<sys/un.h>)
#include <sys/un.h>
#else
#include "../Includes/android_ide_stubs/sys/un.h"
#endif

#include <stdlib.h>
#include <string>
#include <vector>
#include <errno.h>
#include <stdio.h>
#include <string.h>

#define NINLITE_SOCKET_NAME "ninlite_daemon_stream"
#define NINLITE_BACKLOG 8

class NinLiteSocketServer {
public:
    int listenfd, acceptfd;
    bool isCreated;
    sockaddr_un addr_server;
    char socket_name[108];

    NinLiteSocketServer() {
        isCreated = false;
        listenfd = -1;
        acceptfd = -1;
    }

    bool Create() {
        isCreated = (listenfd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0)) >= 0;
        return isCreated;
    }

    bool Bind() {
        memset(socket_name, 0, sizeof(socket_name));
        memcpy(&socket_name[0], "\0", 1); // Abstract namespace socket
        strcpy(&socket_name[1], NINLITE_SOCKET_NAME);

        memset(&addr_server, 0, sizeof(addr_server));
        addr_server.sun_family = AF_UNIX;
        strncpy(addr_server.sun_path, socket_name, sizeof(addr_server.sun_path) - 1);

        if (bind(listenfd, (sockaddr *) &addr_server, sizeof(addr_server)) < 0) {
            Close();
            return false;
        }
        return true;
    }

    bool Listen() {
        if (listen(listenfd, NINLITE_BACKLOG) < 0) {
            Close();
            return false;
        }
        return true;
    }

    bool Accept() {
        if ((acceptfd = accept4(listenfd, nullptr, nullptr, SOCK_CLOEXEC)) < 0) {
            Close();
            return false;
        }
        return true;
    }

    void Close() {
        if (listenfd >= 0) {
            close(listenfd);
            listenfd = -1;
        }
        if (acceptfd >= 0) {
            close(acceptfd);
            acceptfd = -1;
        }
        isCreated = false;
    }

    int sendData(void *inData, size_t size) {
        char *buffer = (char *) inData;
        int numSent = 0;
        while (size) {
            do {
                numSent = write(acceptfd, buffer, size);
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

    bool send(void* inData, size_t size) {
        uint32_t length = htonl(size);
        if (sendData(&length, sizeof(uint32_t)) <= 0) {
            return false;
        }
        return sendData(inData, size) > 0;
    }

    int recvData(void *outData, size_t size) {
        char *buffer = (char *) outData;
        int numRecv = 0;
        while (size) {
            do {
                numRecv = read(acceptfd, buffer, size);
            } while (numRecv == -1 && EINTR == errno);

            if (numRecv <= 0) {
                Close();
                break;
            }
            size -= numRecv;
            buffer += numRecv;
        }
        return numRecv;
    }

    size_t receive(void* outData) {
        uint32_t length = 0;
        int code = recvData(&length, sizeof(uint32_t));
        if (code > 0) {
            length = ntohl(length);
            size_t size = recvData(outData, length);
            return size;
        }
        return 0;
    }
};

#endif // NINLITE_SOCKET_SERVER_H
