#ifndef NINMODS_UTILS_H
#define NINMODS_UTILS_H

/**
 * @file Utils.h
 * @brief Shared helpers of the RT channel.
 *
 * The helpers of this header belong to the in-app engine (RT channel). They
 * locate processes and read game structures through the kernel driver layer
 * of Includes/MemoryAccess.h. The QX channel of the root daemon is not part
 * of this translation unit, so the two channels stay isolated (Rule 3).
 */

#include <jni.h>

#include <atomic>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <unistd.h>

#include "Logger.h"
#include "MemoryAccess.h"
#include "NativeCrypto.h"
#include "TextCodec.h"
#include "TransformTypes.h"
#include "Detection/GameDetection.h"

/**
 * @brief Which build of the game is currently attached.
 *
 * false = Free Fire Normal, true = Free Fire MAX.
 */
inline std::atomic<bool> g_isGameMax{false};

/**
 * @brief Finds the pid of the game process.
 *
 * The detection itself lives in Detection/GameDetection.cpp and reads the
 * answer of the root daemon, because an app may not read the cmdline of a
 * process of another user since Android 9.
 *
 * @return the pid of the game process, or 0 when the game was not found
 */
inline pid_t FindGamePid() {
    NinDetection::GameProcess process = NinDetection::Detect();
    if (process.isDetected && process.pid > 0) {
        g_isGameMax.store(process.isMax, std::memory_order_relaxed);
        return process.pid;
    }
    return 0;
}

/**
 * @brief Helper for the Mono dictionary structures of the game.
 *
 * Every access goes through the driver, the class itself never dereferences
 * a remote pointer on its own.
 */
class MonoDictionary {
public:
    /**
     * @brief Returns the address of the value array of the dictionary.
     *
     * @return the address of the value array, or 0 when the read failed
     */
    uintptr_t getValues() {
        return Read<uintptr_t>(reinterpret_cast<uintptr_t>(this) + 0x18) + 0x20;
    }

    /**
     * @brief Returns the number of entries of the dictionary.
     *
     * @return the entry count, or 0 when the read failed
     */
    int getNumValues() {
        return Read<int>(reinterpret_cast<uintptr_t>(this) + 0x20);
    }
};

#endif  // NINMODS_UTILS_H
