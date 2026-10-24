#include "Includes/Logger.h"
#include <cstring>
#include <mutex>

SocketClient client;

#define maxplayerCount 200

int startClient();
bool isConnected();
void stopClient();
bool initServer();
bool stopServer();

enum Mode {
    InitMode = 1,
    HackMode = 2,
    StopMode = 98,
    EspMode = 99,
};

struct Request {
    int Mode;
    bool m_IsOn;
    int value;
    int ScreenWidth;
    int ScreenHeight;
};

struct PlayerData {
    Vector3 Head;
    Vector3 Toe;
    bool IsCaido;
    float Distancia;
    int Health;
    bool Name;
    char PlayerName[64];
};

struct Response {
    bool Success;
    int PlayerCount;
    int VisibleEnemyCount;                // On‑screen enemies
    int VisibleNearOffScreenEnemyCount;   // Off‑screen but near screen edges
    int ProximityEnemyCount;              // Enemies within 30m of local player
    PlayerData Players[maxplayerCount];
};

int startClient(){
    signal(SIGPIPE, SIG_IGN);
    NIN_LOGI("CLIENT", "startClient() connecting to daemon (Dual-Channel: UNIX \\0%s / TCP %s:%d)...", SOCKET_NAME, TCP_HOST, TCP_PORT);
    client.Close();
    if (!client.Create()) {
        NIN_LOGE("CLIENT", "SocketClient::Create failed!");
        return -1;
    }
    if (!client.Connect()) {
        NIN_LOGE("CLIENT", "SocketClient::Connect failed: %s (errno: %d) on both UNIX and TCP channels!", strerror(errno), errno);
        return -1;
    }
    if (!initServer()) {
        NIN_LOGE("CLIENT", "initServer handshake failed on %s channel!", client.isTcpChannel ? "TCP" : "UNIX");
        client.Close();
        return -1;
    }
    NIN_LOGI("CLIENT", "Connected to daemon successfully via %s channel! Handshake OK.", client.isTcpChannel ? "TCP" : "UNIX");
    return 0;
}

bool isConnected(){
    return client.connected;
}

void stopClient() {
    if(client.created && isConnected()){
        stopServer();
        client.Close();
    }
}

bool initServer() {
    Request request{Mode::InitMode, true, 0};
    int code = client.send((void*) &request, sizeof(request));
    if(code > 0) {
        Response response{};
        size_t length = client.receive((void*) &response);
        if(length > 0) {
            return response.Success;
        }
    }
    return false;
}

bool stopServer() {
    Request request{Mode::StopMode};
    int code = client.send((void*) &request, sizeof(request));
    if(code > 0) {
        Response response{};
        size_t length = client.receive((void*) &response);
        if(length > 0) {
            return response.Success;
        }
    }
    return false;
}

inline std::mutex g_ipcTransactionMutex;

void SendBool(int32_t number, bool ftr) {
    if (!isConnected()) return;
    std::lock_guard<std::mutex> lock(g_ipcTransactionMutex);
    Request request{number, ftr};
    int code = client.send((void*) &request, sizeof(request));
    if (code > 0) {
        Response response{};
        client.receive((void*) &response);
    }
}

static uint32_t FloatBits(float value) {
    uint32_t bits = 0;
    std::memcpy(&bits, &value, sizeof(bits));
    return bits;
}

void SendFloat(int32_t number, float very) {
    if (!isConnected()) return;
    std::lock_guard<std::mutex> lock(g_ipcTransactionMutex);
    Request request{number, true, static_cast<int32_t>(FloatBits(very))};
    int code = client.send((void*) &request, sizeof(request));
    if (code > 0) {
        Response response{};
        client.receive((void*) &response);
    }
}

void SendInt(int32_t number, int value) {
    if (!isConnected()) return;
    std::lock_guard<std::mutex> lock(g_ipcTransactionMutex);
    Request request{number, true, value};
    int code = client.send((void*) &request, sizeof(request));
    if (code > 0) {
        static Response s_discardResponse{};
        client.receive((void*) &s_discardResponse, sizeof(s_discardResponse));
    }
}

static Response s_cachedResponse{};

Response getData(int screenWidth, int screenHeight){
    std::lock_guard<std::mutex> lock(g_ipcTransactionMutex);
    Request request{Mode::EspMode, true, 0, screenWidth, screenHeight};
    int code = client.send((void*) &request, sizeof(request));
    if (code > 0){
        size_t length = client.receive((void*) &s_cachedResponse, sizeof(s_cachedResponse));
        if (length >= sizeof(bool) + sizeof(int) && s_cachedResponse.Success){
            return s_cachedResponse;
        }
    }
    s_cachedResponse.Success = false;
    s_cachedResponse.PlayerCount = 0;
    return s_cachedResponse;
}

inline void SyncBoolToDaemon(int32_t number, bool value) {
    SendBool(number, value);
}

inline void SyncFloatToDaemon(int32_t number, float value) {
    SendFloat(number, value);
}

inline void SyncIntToDaemon(int32_t number, int value) {
    SendInt(number, value);
}