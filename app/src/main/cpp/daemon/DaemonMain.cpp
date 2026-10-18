#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <csignal>
#include <android/log.h>
#include "DaemonProtocol.h"
#include "SocketServer.h"
#include "DaemonMem.h"
#include "../EntityScanner.h"
#include "../GameOffsets.h"

#define LOG_TAG "NinDaemonMain"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static volatile bool s_running = true;

static void HandleSignal(int sig) {
    if (sig == SIGINT || sig == SIGTERM) {
        LOGI("Termination signal %d received, shutting down daemon", sig);
        s_running = false;
    }
}

int main(int argc, char** argv) {
    // Ignore SIGPIPE and SIGHUP to avoid dying when parent shell disconnects or sudden client socket drops
    signal(SIGPIPE, SIG_IGN);
    signal(SIGHUP, SIG_IGN);
    signal(SIGINT, HandleSignal);
    signal(SIGTERM, HandleSignal);

    LOGI("==============================================");
    LOGI("   NINMODS Master Root Daemon (AArch64)      ");
    LOGI("   Unified Kernel & Dual-Channel Root Engine ");
    LOGI("==============================================");

    DaemonSocketServer server;
    if (!server.Start(NIN_SOCKET_NAME, NIN_TCP_PORT)) {
        LOGE("Failed to start dual IPC server on \\0%s / 127.0.0.1:%d", NIN_SOCKET_NAME, NIN_TCP_PORT);
        return 1;
    }

    LOGI("Master Daemon successfully listening on \\0%s and 127.0.0.1:%d", NIN_SOCKET_NAME, NIN_TCP_PORT);

    while (s_running) {
        if (!server.AcceptClient()) {
            if (!s_running) break;
            usleep(10000); // 10ms
            continue;
        }

        LOGI("Client connected to Master Daemon. Processing requests...");

        while (s_running) {
            NinRequest req{};
            if (!server.ReceiveRequest(req)) {
                LOGI("Client disconnected from Master Daemon");
                break;
            }

            NinResponse resp{};
            resp.magic = NIN_MAGIC;
            resp.status = -1;

            switch (req.cmd) {
                case CMD_PING: {
                    resp.status = 0;
                    resp.renderedCount = 0;
                    if (MasterMem::Get().IsAttached()) {
                        resp.diag[0] = MasterMem::Get().GetModuleBase("libil2cpp.so");
                    }
                    server.SendResponse(resp);
                    break;
                }

                case CMD_ATTACH: {
                    if (req.pid > 0) {
                        bool ok = MasterMem::Get().Init(req.pid, req.isMax != 0);
                        if (ok) {
                            resp.status = 0;
                            resp.diag[0] = MasterMem::Get().GetModuleBase("libil2cpp.so");
                        } else {
                            resp.status = -1;
                        }
                    }
                    server.SendResponse(resp);
                    break;
                }

                case CMD_SCAN_ESP: {
                    if (req.pid > 0) {
                        if (!MasterMem::Get().IsAttached() || MasterMem::Get().GetPid() != req.pid) {
                            MasterMem::Get().Init(req.pid, req.isMax != 0);
                        }

                        ScanDiagnostics diag{};
                        uint64_t dummyHandle = 1;
                        int dummyFd = MasterMem::Get().GetDriverFd();

                        int rendered = ScanAndComputeEsp(
                            req.pid,
                            dummyHandle,
                            dummyFd,
                            req.isMax != 0,
                            req.screenW,
                            req.screenH,
                            resp.entities,
                            NIN_MAX_ENTITIES,
                            &diag,
                            req.aimFov
                        );

                        resp.status = 0;
                        resp.renderedCount = static_cast<uint32_t>(rendered);
                        resp.diag[0] = diag.libBase;
                        resp.diag[1] = diag.gameFacade;
                        resp.diag[2] = diag.matchPtr;
                        resp.diag[3] = diag.localPlayer;
                        resp.diag[4] = diag.dictCount;
                        resp.diag[5] = diag.matrixValid ? 1 : 0;
                        resp.diag[6] = diag.renderedCount;
                        resp.diag[7] = static_cast<uint64_t>(diag.bestAimPitch * 1000.0f);
                        resp.diag[8] = static_cast<uint64_t>(diag.bestAimYaw * 1000.0f);
                        resp.diag[9] = diag.hasAimTarget ? 1 : 0;
                    }
                    server.SendResponse(resp);
                    break;
                }

                case CMD_WRITE_AIM: {
                    if (req.pid > 0 && req.localPlayer != 0) {
                        uint8_t elfClass = 2; // default 64-bit
                        uint64_t base = MasterMem::Get().GetModuleBase("libil2cpp.so");
                        if (base != 0) {
                            MasterMem::Get().Read(base + 4, &elfClass, 1);
                        }
                        bool is64 = (elfClass != 1);
                        const GameOffsets* off = GetActiveOffsets(req.isMax != 0, is64);
                        uint64_t aimAddr = req.localPlayer + off->aimRotation;

                        float cur[2] = {0.0f, 0.0f};
                        bool rOk = MasterMem::Get().Read(aimAddr, cur, sizeof(cur));
                        if (!rOk && off->aimRotationFallback != 0) {
                            aimAddr = req.localPlayer + off->aimRotationFallback;
                            rOk = MasterMem::Get().Read(aimAddr, cur, sizeof(cur));
                        }

                        if (rOk) {
                            float s = (req.smooth >= 1.0f) ? req.smooth : 5.0f;
                            float nextAngles[2] = {
                                cur[0] + (req.targetPitch - cur[0]) / s,
                                cur[1] + (req.targetYaw - cur[1]) / s
                            };
                            if (MasterMem::Get().Write(aimAddr, nextAngles, sizeof(nextAngles))) {
                                resp.status = 0;
                            }
                        }
                    }
                    server.SendResponse(resp);
                    break;
                }

                case CMD_COMBAT_MOD: {
                    if (req.pid > 0) {
                        if (!MasterMem::Get().IsAttached() || MasterMem::Get().GetPid() != req.pid) {
                            MasterMem::Get().Init(req.pid, req.isMax != 0);
                        }
                        uint8_t elfClass = 2;
                        uint64_t base = MasterMem::Get().GetModuleBase("libil2cpp.so");
                        if (base != 0) {
                            MasterMem::Get().Read(base + 4, &elfClass, 1);
                        }
                        bool is64 = (elfClass != 1);
                        const GameOffsets* off = GetActiveOffsets(req.isMax != 0, is64);

                        uint64_t facade = 0;
                        MasterMem::Get().Read(base + off->gameFacade, &facade, is64 ? 8 : 4);
                        if (!facade && off->gameFacadeInd != 0) {
                            MasterMem::Get().Read(base + off->gameFacadeInd, &facade, is64 ? 8 : 4);
                        }
                        if (facade != 0) {
                            uint64_t match = 0;
                            MasterMem::Get().Read(facade + off->currentMatch, &match, is64 ? 8 : 4);
                            if (!match && off->currentMatchFallback != 0) {
                                MasterMem::Get().Read(facade + off->currentMatchFallback, &match, is64 ? 8 : 4);
                            }
                            if (match != 0) {
                                uint64_t lp = 0;
                                MasterMem::Get().Read(match + off->localPlayer, &lp, is64 ? 8 : 4);
                                if (lp != 0) {
                                    // 1. Fly Hack (Bit 0)
                                    uint8_t flyVal = (req.combatFlags & 1) ? 1 : 0;
                                    MasterMem::Get().Write(lp + off->flyState, &flyVal, 1);
                                    if (off->flyStateFallback != 0) {
                                        MasterMem::Get().Write(lp + off->flyStateFallback, &flyVal, 1);
                                    }

                                    // 2. Speed Hack (Bit 1)
                                    float spdVal = (req.combatFlags & 2) ? ((req.speedMult > 0.0f) ? req.speedMult : 2.5f) : 1.0f;
                                    MasterMem::Get().Write(lp + off->forceSpeed, &spdVal, sizeof(float));
                                    if (off->forceSpeedFallback != 0) {
                                        MasterMem::Get().Write(lp + off->forceSpeedFallback, &spdVal, sizeof(float));
                                    }

                                    // 3. Ghost Hack (Bit 2)
                                    uint8_t ghostVal = (req.combatFlags & 4) ? 1 : 0;
                                    MasterMem::Get().Write(lp + off->ghostHack, &ghostVal, 1);

                                    // 4. WallBug (Bit 3)
                                    int ucVal = (req.combatFlags & 8) ? 0 : 1;
                                    MasterMem::Get().Write(lp + off->userControl, &ucVal, sizeof(int));
                                    if (off->userControlFallback != 0) {
                                        MasterMem::Get().Write(lp + off->userControlFallback, &ucVal, sizeof(int));
                                    }
                                }
                            }
                        }
                    }
                    resp.status = 0;
                    server.SendResponse(resp);
                    break;
                }

                case CMD_DISCONNECT: {
                    server.CloseClient();
                    break;
                }

                default:
                    server.SendResponse(resp);
                    break;
            }
        }
    }

    server.Stop();
    LOGI("Master Daemon stopped cleanly");
    return 0;
}
