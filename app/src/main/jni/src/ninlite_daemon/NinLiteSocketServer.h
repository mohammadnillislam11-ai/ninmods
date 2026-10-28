#ifndef NINLITE_SOCKET_SERVER_H
#define NINLITE_SOCKET_SERVER_H

/**
 * @file NinLiteSocketServer.h
 * @brief Pure UNIX domain abstract socket server (STORM_MODS architecture) for root daemon data streaming.
 *
 * Reliability contract:
 *  1. 100% genuine Linux AF_UNIX abstract domain socket matching STORM_MODS reference.
 *  2. Uses full sizeof(struct sockaddr_un) address binding identical to STORM_MODS server.cpp.
 *  3. Accepts connections cleanly without destroying the listening descriptor on client disconnect.
 *  4. Zero artificial timers and zero TCP dependencies.
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

#if __has_include(<sys/un.h>)
#include <sys/un.h>
#else
#include "../Includes/android_ide_stubs/sys/un.h"
#endif

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "Includes/NinliteProtocol.h"

#define NINLITE_BACKLOG 8

/**
 * @class NinLiteSocketServer
 * @brief Manages listening abstract domain socket for IPC communication (STORM_MODS style).
 */
class NinLiteSocketServer {
public:
    int listenfd;
    int acceptfd;
    bool isCreated;
    sockaddr_un addr_server;
    char socket_name[108];

    /**
     * @brief Constructor initializing socket file descriptors to invalid state.
     */
    NinLiteSocketServer() {
        isCreated = false;
        listenfd = -1;
        acceptfd = -1;
        memset(&addr_server, 0, sizeof(addr_server));
        memset(socket_name, 0, sizeof(socket_name));
    }

    /**
     * @brief Destructor ensuring all sockets are safely closed.
     */
    ~NinLiteSocketServer() {
        Close();
    }

    NinLiteSocketServer(const NinLiteSocketServer&) = delete;
    NinLiteSocketServer& operator=(const NinLiteSocketServer&) = delete;

    /**
     * @brief Creates the AF_UNIX socket file descriptor.
     * @return True on success, false otherwise.
     */
    bool Create() {
        if (listenfd < 0) {
            listenfd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
            isCreated = (listenfd >= 0);
        }
        return isCreated;
    }

    /**
     * @brief Binds socket to abstract domain name using full sizeof(struct sockaddr_un) (STORM_MODS).
     * @return True on success, false otherwise.
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
        socket_name[0] = '\0';
        memcpy(socket_name + 1, NINLITE_SOCKET_NAME, nameLen);

        if (bind(listenfd, (sockaddr*)&addr_server, sizeof(addr_server)) < 0) {
            Close();
            return false;
        }
        return true;
    }

    /**
     * @brief Listens for incoming client connections.
     * @return True on success, false otherwise.
     */
    bool Listen() {
        if (listenfd < 0) return false;
        if (listen(listenfd, NINLITE_BACKLOG) < 0) {
            Close();
            return false;
        }
        return true;
    }

    /**
     * @brief Accepts a client connection without destroying listener on transient error.
     * @return True on success, false otherwise.
     */
    bool Accept() {
        if (listenfd < 0) return false;
        int fd = accept4(listenfd, nullptr, nullptr, SOCK_CLOEXEC);
        if (fd < 0) {
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
     * @brief Closes listening socket and active client connection.
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
     * @return True if listening fd is valid.
     */
    bool IsListening() const {
        return (listenfd >= 0);
    }

    /**
     * @brief Sends a length-prefixed protocol frame to connected client.
     * @param inData Pointer to frame payload.
     * @param size Payload length in bytes.
     * @return True on success, false otherwise.
     */
    bool send(void* inData, size_t size) {
        if (acceptfd < 0) return false;
        return Ninlite::SendFrame(acceptfd, inData, size);
    }

    /**
     * @brief Receives a length-prefixed frame into destination buffer.
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
