#include "Includes/Logger.h"

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
    NIN_LOGI("CLIENT", "startClient() connecting to \\0%s ...", SOCKET_NAME);
    client = SocketClient();
    if (!client.Create()) {
        NIN_LOGE("CLIENT", "SocketClient::Create failed!");
        return -1;
    }
    if (!client.Connect()) {
        NIN_LOGE("CLIENT", "SocketClient::Connect to \\0%s failed: %s (errno: %d)", SOCKET_NAME, strerror(errno), errno);
        return -1;
    }
    if (!initServer()) {
        NIN_LOGE("CLIENT", "initServer handshake failed!");
        return -1;
    }
    NIN_LOGI("CLIENT", "Connected to daemon successfully!");
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

void SendBool(int32_t number, bool ftr) {
    if (!isConnected()) return;
    Request request{number, ftr};
    int code = client.send((void*) &request, sizeof(request));
    if (code > 0) {
        Response response{};
        size_t length = client.receive((void*) &response);
    }
}

void SendFloat(int32_t number, float very) {
    if (!isConnected()) return;
    Request request{number, true, static_cast<int>(very)};
    int code = client.send((void*) &request, sizeof(request));
    if (code > 0) {
        Response response{};
        size_t length = client.receive((void*) &response);
    }
}

void SendInt(int32_t number, int value) {
    if (!isConnected()) return;
    Request request{number, true, value};
    int code = client.send((void*) &request, sizeof(request));
    if (code > 0) {
        Response response{};
        size_t length = client.receive((void*) &response);
    }
}

Response getData(int screenWidth, int screenHeight){
    Request request{Mode::EspMode, true,0, screenWidth, screenHeight};
    int code = client.send((void*) &request, sizeof(request));
    if(code > 0){
        Response response{};
        size_t length = client.receive((void*) &response);
        if(length > 0){
            return response;
        }
    }
    Response response{false, 0};
    return response;
}