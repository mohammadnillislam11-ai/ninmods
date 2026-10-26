#pragma once

#include <string>
#include <jni.h>

namespace NinliteEngine {
    // Check if shell daemon is alive and listening on abstract unix socket
    bool CheckShellDaemonRunning();

    // Check emulator indicators
    bool CheckEmulatorDetected();

    // Get current connection mode string ("Phantom IPC", "Kernel Ring-0", etc.)
    const char* GetConnectionModeStr();

    // Launch daemon via root shell with live SELinux policies in a detached native thread
    void StartEngine(const std::string& nativeLibDir);

    // Stop daemon sync
    void StopEngine();

    // Check if initial setup is marked complete
    bool IsSetupDone();

    // Generate full JSON status object for Java presentation layer
    std::string GetStateJson();

    // Sync feature toggle with daemon
    void SyncFeature(int featId, bool enabled, int value);
}
