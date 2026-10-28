#ifndef NINLITE_SOCKET_SERVER_H
#define NINLITE_SOCKET_SERVER_H

/**
 * @file NinLiteSocketServer.h
 * @brief UNIX domain socket server used by the root daemon to stream entity data.
 *
 * Reliability contract:
 *  1. A client disconnecting must not destroy the listening socket.
 *  2. The framing length must be validated against caller buffer capacity.
 *  3. Accept failure must be recoverable without restarting the daemon process.
 */

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

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <string>
#include <vector>

#include "Includes/NinliteProtocol.h"

#define NINLITE_BACKLOG 8

/**
 * @class NinLiteSocketServer
 * @brief Manages the listening abstract domain socket for IPC communication.
 */
class NinLiteSocketServer {
public:
    int listenfd;
    int acceptfd;
    bool isCreated;
    sockaddr_un addr_server;
    char socket_name[108];

    NinLiteSocketServer() {
        isCreated = false;
        listenfd = -1;
        acceptfd = -1;
        memset(&addr_server, 0, sizeof(addr_server));
        memset(socket_name, 0, sizeof(socket_name));
    }

    ~NinLiteSocketServer() { Close(); }

    NinLiteSocketServer(const NinLiteSocketServer&) = delete;
    NinLiteSocketServer& operator=(const NinLiteSocketServer&) = delete;

    /**
     * @brief Creates the socket file descriptor.
     * @return true on success, false otherwise.
     */
    bool Create() {
        if (listenfd < 0) {
            listenfd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
            isCreated = (listenfd >= 0);
        }
        return isCreated;
    }

    /**
     * @brief Binds the socket to the abstract domain name.
     * @return true on success, false otherwise.
     */
    bool Bind() {
        if (listenfd < 0) return false;

        socklen_t addrLen = Ninlite::BuildAbstractAddress(&addr_server);
        if (addrLen == 0) {
            CloseClient();
            return false;
        }

        memset(socket_name, 0, sizeof(socket_name));
        size_t nameLen = strlen(NINLITE_SOCKET_NAME);
        if (nameLen > sizeof(socket_name) - 2) nameLen = sizeof(socket_name) - 2;
        memcpy(socket_name + 1, NINLITE_SOCKET_NAME, nameLen);

        if (bind(listenfd, (sockaddr*)&addr_server, addrLen) < 0) {
            CloseClient();
            return false;
        }
        return true;
    }

    /**
     * @brief Listens for incoming client connections.
     * @return true on success, false otherwise.
     */
    bool Listen() {
        if (listenfd < 0) return false;
        if (listen(listenfd, NINLITE_BACKLOG) < 0) {
            CloseClient();
            return false;
        }
        return true;
    }

    /**
     * @brief Accepts a client connection without closing the listener on failure.
     * @return true on success, false otherwise.
     */
    bool Accept() {
        if (listenfd < 0) return false;
        int fd = accept4(listenfd, nullptr, nullptr, SOCK_CLOEXEC);
        if (fd < 0) {
            CloseClient();
            return false;
        }
        CloseClient();
        acceptfd = fd;
        return true;
    }

    /**
     * @brief Closes only the current client connection.
     */
    void CloseClient() {
        if (acceptfd >= 0) {
            close(acceptfd);
            acceptfd = -1;
        }
    }

    /**
     * @brief Closes the listening socket and any active client connection.
     */
    void Close() {
        CloseClient();
        if (listenfd >= 0) {
            close(listenfd);
            listenfd = -1;
        }
        isCreated = false;
    }

    /**
     * @brief Reports whether the listener is active.
     * @return true if listening fd is valid.
     */
    bool IsListening() const { return listenfd >= 0; }

    /**
     * @brief Sends a length-prefixed protocol frame to the connected client.
     * @param inData Pointer to frame payload.
     * @param size Payload length in bytes.
     * @return true on success, false otherwise.
     */
    bool send(void* inData, size_t size) {
        if (acceptfd < 0) return false;
        return Ninlite::SendFrame(acceptfd, inData, size);
    }

    /**
     * @brief Receives a length-prefixed frame into the destination buffer.
     * @param outData Buffer to receive payload.
     * @param capacity Maximum capacity of destination buffer.
     * @return Number of payload bytes received, or 0 on error.
     */
    size_t receive(void* outData, size_t capacity) {
        if (acceptfd < 0 || outData == nullptr || capacity == 0) return 0;
        size_t got = Ninlite::RecvFrame(acceptfd, outData, capacity);
        if (got == 0) {
            CloseClient();
        }
        return got;
    }
};

#endif // NINLITE_SOCKET_SERVER_H
