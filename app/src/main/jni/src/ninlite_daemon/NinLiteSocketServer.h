#ifndef NINLITE_SOCKET_SERVER_H
#define NINLITE_SOCKET_SERVER_H

/**
 * NINLITE DAEMON SOCKET SERVER
 * ----------------------------
 * Robust UNIX domain socket server used by the root Daemon to stream entity
 * data to the Main App.
 *
 * Reliability contract (all of these were real production failures before):
 *  1. A client disconnecting MUST NOT destroy the listening socket. The old
 *     implementation called Close() from the read/write helpers, which closed
 *     the listener too, so after the very first probe connection the Daemon
 *     could never accept another client again.
 *  2. The framing length MUST be validated against the caller's buffer
 *     capacity before a single payload byte is copied, otherwise a malformed
 *     frame overflows a fixed size stack buffer.
 *  3. Accept() failure must be recoverable: the server has to be able to
 *     re-arm itself without restarting the process.
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

#include <stdlib.h>
#include <string>
#include <vector>
#include <errno.h>
#include <stdio.h>
#include <string.h>

#include "Includes/NinliteProtocol.h"

#define NINLITE_BACKLOG 8

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

    bool Create() {
        if (listenfd < 0) {
            listenfd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
            isCreated = (listenfd >= 0);
        }
        return isCreated;
    }

    bool Bind() {
        if (listenfd < 0) return false;

        /* The address is built by the shared helper (Includes/NinliteProtocol.h)
         * so that the name bytes AND the address length are byte-for-byte
         * identical to the ones the App uses in connect().
         *
         * The previous implementation copied the name with strncpy() into a
         * buffer that already started with a NUL, which truncated the copy at
         * the first NUL - and it then bound with sizeof(sockaddr_un) while the
         * App connected with a 26 byte address. In the abstract namespace the
         * name length comes from the address length, so the two sides ended up
         * on two different sockets and the Daemon link never came up. */
        socklen_t addrLen = Ninlite::BuildAbstractAddress(&addr_server);
        if (addrLen == 0) {
            CloseClient();
            return false;
        }

        /* Keep the human readable name in sync for logging. */
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

    bool Listen() {
        if (listenfd < 0) return false;
        if (listen(listenfd, NINLITE_BACKLOG) < 0) {
            CloseClient();
            return false;
        }
        return true;
    }

    /** Accepts one client. On failure the listener is preserved so the server
     *  can keep serving later connections. */
    bool Accept() {
        if (listenfd < 0) return false;
        int fd = accept4(listenfd, nullptr, nullptr, SOCK_CLOEXEC);
        if (fd < 0) {
            CloseClient();
            return false;
        }
        CloseClient();                          /* single client at a time */
        acceptfd = fd;
        return true;
    }

    /** Closes ONLY the current client connection. The listening socket stays
     *  alive - this is the critical difference from the previous Close(). */
    void CloseClient() {
        if (acceptfd >= 0) {
            close(acceptfd);
            acceptfd = -1;
        }
    }

    /** Full teardown (process exit path only). */
    void Close() {
        CloseClient();
        if (listenfd >= 0) {
            close(listenfd);
            listenfd = -1;
        }
        isCreated = false;
    }

    bool IsListening() const { return listenfd >= 0; }

    int sendData(void* inData, size_t size) {
        char* buffer = (char*)inData;
        int numSent = 0;
        while (size) {
            do {
                numSent = (int)write(acceptfd, buffer, size);
            } while (numSent == -1 && errno == EINTR);

            if (numSent <= 0) {
                CloseClient();
                break;
            }
            size -= (size_t)numSent;
            buffer += numSent;
        }
        return numSent;
    }

    bool send(void* inData, size_t size) {
        if (acceptfd < 0) return false;
        return Ninlite::SendFrame(acceptfd, inData, size);
    }

    int recvData(void* outData, size_t size) {
        char* buffer = (char*)outData;
        int numRecv = 0;
        while (size) {
            do {
                numRecv = (int)read(acceptfd, buffer, size);
            } while (numRecv == -1 && errno == EINTR);

            if (numRecv <= 0) {
                CloseClient();
                break;
            }
            size -= (size_t)numRecv;
            buffer += numRecv;
        }
        return numRecv;
    }

    /**
     * Reads one length prefixed frame into `outData`.
     *
     * `capacity` MUST be the real size of the destination buffer. A frame that
     * announces more bytes than the buffer can hold is rejected before any
     * payload byte is written. Returns the number of payload bytes read, or 0
     * on error / violation.
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
