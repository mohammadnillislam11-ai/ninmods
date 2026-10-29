#ifndef NINLITE_PROTOCOL_H
#define NINLITE_PROTOCOL_H

/**
 * NINMODS :: NINLITE DAEMON WIRE PROTOCOL (Single Source of Truth)
 * ---------------------------------------------------------------
 * This header is the ONE and ONLY definition of the binary protocol that is
 * exchanged between the Main App (`com.ninmods.main`) and the Root Daemon
 * (`ninlite_daemon`).
 *
 * Why this file exists (root cause of the previous production failures):
 *  - The App and the Daemon used to declare the wire structures INDEPENDENTLY
 *    (`Response` in GameEngine.h vs `Response` in ninlite_daemon.cpp). Any
 *    field added, removed or reordered on one side silently desynchronised the
 *    other side and produced garbage entity data.
 *  - The App used to send an ASCII payload ("FEAT:3:1:0\n") while the Daemon
 *    expected a 4-byte length prefixed binary frame. The Daemon therefore read
 *    the ASCII bytes as a 1.1 GB frame length and smashed its own stack,
 *    crashing the Daemon the moment any feature was toggled.
 *
 * Everything below is fixed-width and free of C++ types with implementation
 * defined sizes (no `bool`, no `enum class`, no `long`) so the layout is
 * identical on every ABI.
 */

#include <cstdint>
#include <cstddef>
#include <cstring>
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/time.h>
#include <unistd.h>
#include "Logger.h"

#define SOCKET_NAME "УБИЙЦА. ХД"
#define NINLITE_SOCKET_NAME "УБИЙЦА. ХД"

namespace Ninlite {

/* ------------------------------------------------------------------
 * SHARED ABSTRACT SOCKET ADDRESS
 * ------------------------------------------------------------------
 * Builds the abstract UNIX socket address used by BOTH sides of the
 * IPC: the Daemon (bind) and the App (connect).
 *
 * ROOT CAUSE of the dead Daemon link: the Daemon used to bind with
 * sizeof(sockaddr_un) (= 110 bytes on Linux) while the App connected
 * with only 26 bytes. In the Linux ABSTRACT namespace the length of
 * the name is derived from the length of the address that is passed
 * to bind()/connect(), so the two sides addressed two completely
 * different sockets and every connect() failed with ECONNREFUSED.
 *
 * Both sides now build the address through this one helper, so the
 * name bytes AND the address length can never drift apart again.
 * ------------------------------------------------------------------ */
inline socklen_t BuildAbstractAddress(struct sockaddr_un* addr) {
    if (!addr) return 0;
    memset(addr, 0, sizeof(struct sockaddr_un));
    addr->sun_family = AF_UNIX;
    addr->sun_path[0] = '\0';                 /* leading NUL => abstract */

    size_t nameLen = strlen(NINLITE_SOCKET_NAME);
    if (nameLen > sizeof(addr->sun_path) - 2) {
        nameLen = sizeof(addr->sun_path) - 2;  /* keep room for the NUL */
    }
    memcpy(addr->sun_path + 1, NINLITE_SOCKET_NAME, nameLen);

    /* Use full sizeof(struct sockaddr_un) matching STORM_MODS server.cpp / client.cpp */
    return (socklen_t)sizeof(struct sockaddr_un);
}


/* ---------------------------------------------------------------- limits */

constexpr int      kMaxPlayers      = 100;
constexpr size_t   kPlayerNameLen   = 64;
constexpr int      kMaxFrameSize    = 64 * 1024;   /* hard frame cap */

/* --------------------------------------------------------- request modes */

enum RequestMode : int32_t {
    MODE_INIT         = 1,    /* handshake / health probe                 */
    MODE_HACK         = 2,    /* legacy handshake                         */
    MODE_STOP         = 98,   /* shutdown                                 */
    MODE_ESP          = 99,   /* request an entity snapshot               */
    MODE_MASTER       = 3,    /* master ESP enable                        */
    MODE_AIM_HARD     = 51,   /* hard aim enable                          */
    MODE_AIM_LOCK     = 54,   /* aim lock enable                          */
    MODE_IGNORE_KNOCK = 55,   /* ignore knocked targets                   */
    MODE_SILENT       = 57,   /* silent aim enable                        */
    MODE_AIM_FOV      = 58,   /* aim FOV (degrees)                        */
    MODE_CHEST_RATE   = 62,   /* chest shot rate (0..6)                   */
    MODE_HEAD_RATE    = 70,   /* head shot rate (0..3)                    */
    MODE_AIM_DISTANCE = 60,   /* max target distance (metres)             */
    MODE_LEGIT_DELAY  = 59,   /* legit / smooth delay (milliseconds)      */
    MODE_KILL_IN_CHEST = 61,  /* aim into the chest of a weak target      */
    MODE_DETECT       = 100,  /* ask the daemon for the game process      */
};

/* ---------------------------------------------------------- player flags */

enum PlayerFlags : uint32_t {
    PLAYER_FLAG_KNOCKED = 1u << 0,   /* downed / knocked state        */
    PLAYER_FLAG_BOT     = 1u << 1,   /* AI controlled client bot      */
    PLAYER_FLAG_SPECIAL = 1u << 2,   /* active skill transformation   */
    PLAYER_FLAG_VISIBLE = 1u << 3,   /* enemy head is on-screen       */
};

/* ------------------------------------------------------------ wire types */

#pragma pack(push, 4)
struct Request {
    int32_t  mode;          /* Matches Mode in STORM_MODS / Power */
    uint32_t enabled;       /* Matches m_IsOn in STORM_MODS / Power (4 bytes) */
    int32_t  value;         /* Matches value in STORM_MODS / Power */
    int32_t  screenWidth;   /* Matches ScreenWidth in STORM_MODS / Power */
    int32_t  screenHeight;  /* Matches ScreenHeight in STORM_MODS / Power */
};
#pragma pack(pop)

#pragma pack(push, 4)
struct PlayerData {
    float    head[3];      /* screen space X/Y (+Z depth)        */
    float    toe[3];       /* screen space X/Y (+Z depth)        */
    float    distance;     /* world distance in metres           */
    int32_t  health;       /* real HP, 0 when unknown            */
    int32_t  specialAbilities;
    uint32_t flags;        /* PlayerFlags bitmask                */
    uint16_t weaponId;
    uint16_t reserved;
    char     playerName[kPlayerNameLen];
};
#pragma pack(pop)

#pragma pack(push, 4)
struct Response {
    uint32_t success;
    int32_t  playerCount;
    int32_t  visibleEnemyCount;
    int32_t  visibleNearOffScreenEnemyCount;
    int32_t  proximityEnemyCount;
    /* THE THREAT COUNTER
     * The reference engine splits the enemies into the ones in front of the
     * camera and the ones behind it. The kernel engine (GameEngine.h)
     * already computed both, but the Daemon did not - so the counter was
     * always empty in the NINLITE mode. Both sides now report them.
     * The App copies them into Response::EnemiesInFront / EnemiesBehind. */
    int32_t  enemiesInFront;
    int32_t  enemiesBehind;
    PlayerData players[kMaxPlayers];
    /* The game process the Daemon (root) has detected. The App cannot find
     * it on its own since Android 9, so the Daemon reports it. */
    int32_t  detectedPid;
    char     detectedPackage[64];
};
#pragma pack(pop)

static_assert(sizeof(Request) == 20, "Ninlite::Request layout drifted");
/* head[3]+toe[3]+distance+health+specialAbilities+flags = 40 bytes,
 * weaponId(2)+reserved(2) = 44, playerName[64] = 108, already 4 byte
 * aligned because of #pragma pack(4). */
static_assert(sizeof(PlayerData) == 108, "Ninlite::PlayerData layout drifted");
/* 6 int32 counters (success, playerCount, visible, nearOffScreen, proximity,
 * enemiesInFront, enemiesBehind) = 7 * 4 = 28 bytes, then the player array,
 * then detectedPid (4) + detectedPackage (64) = 68 bytes. */
static_assert(sizeof(Response) == 28 + sizeof(PlayerData) * kMaxPlayers + 68,
               "Ninlite::Response layout drifted");

/* ------------------------------------------------------- frame transport */

/**
 * Length prefixed frame helpers shared by the client and the server.
 * The length prefix is a 32 bit big endian value, exactly like the
 * reference implementation.
 */
inline bool SendFrame(int fd, const void* payload, size_t size) {
    if (fd < 0 || payload == nullptr || size == 0 || size > (size_t)kMaxFrameSize) {
        return false;
    }
    uint32_t netLen = htonl((uint32_t)size);
    const char* buf = (const char*)&netLen;
    size_t remaining = sizeof(netLen);
    while (remaining > 0) {
        ssize_t n = ::write(fd, buf, remaining);
        if (n < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        if (n == 0) return false;
        buf += n;
        remaining -= (size_t)n;
    }

    buf = (const char*)payload;
    remaining = size;
    while (remaining > 0) {
        ssize_t n = ::write(fd, buf, remaining);
        if (n < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        if (n == 0) return false;
        buf += n;
        remaining -= (size_t)n;
    }
    return true;
}

/**
 * Reads one length prefixed frame.
 *
 * `capacity` is the number of bytes the caller can actually accept. A frame
 * announcing more than `capacity` bytes is REJECTED WITHOUT touching the
 * destination buffer. This is the guard that stops a malformed or hostile
 * peer from overflowing a fixed size stack buffer.
 *
 * Returns the number of payload bytes written, or 0 on error / protocol
 * violation.
 */
inline size_t RecvFrame(int fd, void* payload, size_t capacity) {
    if (fd < 0 || payload == nullptr || capacity == 0) return 0;

    uint32_t netLen = 0;
    char* lenBuf = (char*)&netLen;
    size_t remaining = sizeof(netLen);
    while (remaining > 0) {
        ssize_t n = ::read(fd, lenBuf, remaining);
        if (n < 0) {
            if (errno == EINTR) continue;
            return 0;
        }
        if (n == 0) return 0;              /* peer closed */
        lenBuf += n;
        remaining -= (size_t)n;
    }

    uint32_t length = ntohl(netLen);
    if (length == 0 || length > (uint32_t)kMaxFrameSize || length > capacity) {
        return 0;                            /* protocol violation */
    }

    char* buf = (char*)payload;
    remaining = length;
    while (remaining > 0) {
        ssize_t n = ::read(fd, buf, remaining);
        if (n < 0) {
            if (errno == EINTR) continue;
            return 0;
        }
        if (n == 0) return 0;
        buf += n;
        remaining -= (size_t)n;
    }
    return (size_t)length;
}

/* ---------------------------------------------------------- socket client */

/**
 * Persistent, reconnectable client for the Daemon control stream.
 *
 * Design notes:
 *  - One connection is reused for the whole session. The previous
 *    implementation opened a brand new socket for EVERY frame, which forced
 *    the Daemon to re-scan /proc and re-open the game memory on every single
 *    frame.
 *  - The client never tears the Daemon down: a failed request only closes the
 *    local descriptor, the listening socket on the Daemon side stays alive.
 *  - Every call is bounded by a socket timeout so a stuck Daemon can never
 *    freeze the render thread.
 */
class SocketClient {
public:
    SocketClient() : fd_(-1) {}
    ~SocketClient() { Close(); }

    SocketClient(const SocketClient&) = delete;
    SocketClient& operator=(const SocketClient&) = delete;

    bool IsConnected() const { return fd_ >= 0; }

    void Close() {
        if (fd_ >= 0) {
            ::close(fd_);
            fd_ = -1;
        }
    }

    /* Opens the abstract UNIX socket matching STORM_MODS client.cpp. */
    bool Connect() {
        if (fd_ >= 0) return true;

        int sock = ::socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
        if (sock < 0) return false;

        struct sockaddr_un addr;
        socklen_t len = BuildAbstractAddress(&addr);
        if (len == 0) {
            ::close(sock);
            return false;
        }

        if (::connect(sock, (struct sockaddr*)&addr, sizeof(addr)) != 0) {
            int err = errno;
            ::close(sock);
            NinLogger::Log("NIN_CLIENT", "[DEBUG] connect() error: %d (%s)", err, strerror(err));
            return false;
        }
        fd_ = sock;
        NinLogger::Log("NIN_CLIENT", "connect() succeeded, fd=%d", sock);
        return true;
    }

    /* Sends a request and (optionally) reads the matching response.
     * `resp` may be nullptr when no answer is expected. */
    bool Request(const Request& req, Response* resp) {
        if (!Connect()) return false;

        if (!SendFrame(fd_, &req, sizeof(req))) {
            int err = errno;
            NinLogger::Log("NIN_CLIENT", "[ERROR] SendFrame failed for mode %d: %d (%s)", req.mode, err, strerror(err));
            Close();
            return false;
        }
        if (resp == nullptr) return true;

        Response tmp;
        size_t got = RecvFrame(fd_, &tmp, sizeof(tmp));
        if (got < sizeof(Response)) {
            int err = errno;
            NinLogger::Log("NIN_CLIENT", "[ERROR] RecvFrame got %zu bytes (expected %zu): %d (%s)", got, sizeof(Response), err, strerror(err));
            Close();
            return false;
        }
        *resp = tmp;
        return true;
    }

private:
    int fd_;
};

} // namespace Ninlite

#endif // NINLITE_PROTOCOL_H
