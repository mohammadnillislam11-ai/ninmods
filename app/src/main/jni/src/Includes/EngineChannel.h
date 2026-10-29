#ifndef NINMODS_ENGINE_CHANNEL_H
#define NINMODS_ENGINE_CHANNEL_H

/**
 * @file EngineChannel.h
 * @brief Explicit selection of the active memory channel.
 *
 * The project has two completely independent memory channels:
 *
 *   - RT: the in-app engine talks to the Ring-0 kernel driver with the
 *     documented IOCTL commands (Includes/MemoryIO.h),
 *   - QX: the root daemon reads the game and streams the scene to the app
 *     over the UNIX domain socket (Engine/DaemonLink.h).
 *
 * Rule 3 (Strict Driver Isolation) demands that the user selects the channel
 * and that a failing channel never switches to the other one on its own.
 * The selection is therefore stored explicitly here, the Java layer sets it
 * when the user starts a menu, and the engine reads it instead of guessing
 * from a process id.
 */

#include <atomic>

namespace NinChannel {

/** Identifiers of the two independent memory channels. */
enum ChannelId : int {
    CHANNEL_RT = 0,  /**< Ring-0 kernel driver, used by the in-app engine. */
    CHANNEL_QX = 1   /**< Root daemon over IPC, used by the QX brand. */
};

/** Channel that is currently selected by the user. */
inline std::atomic<int> g_activeChannel{CHANNEL_RT};

/**
 * @brief Selects the active memory channel.
 *
 * @param channel one of CHANNEL_RT or CHANNEL_QX
 */
inline bool SetActiveChannel(int channel) {
    if (channel != CHANNEL_RT && channel != CHANNEL_QX) {
        return false;
    }
    g_activeChannel.store(channel, std::memory_order_relaxed);
    return true;
}

/**
 * @brief Returns the active memory channel.
 *
 * @return CHANNEL_RT or CHANNEL_QX
 */
inline int GetActiveChannel() {
    return g_activeChannel.load(std::memory_order_relaxed);
}

/**
 * @brief Reports whether the QX channel is active.
 *
 * @return true when the root daemon over IPC is the selected channel
 */
inline bool IsQxActive() {
    return GetActiveChannel() == CHANNEL_QX;
}

/**
 * @brief Reports whether the RT channel is active.
 *
 * @return true when the Ring-0 kernel driver is the selected channel
 */
inline bool IsRtActive() {
    return GetActiveChannel() == CHANNEL_RT;
}

/**
 * @brief Returns the name of the active channel.
 *
 * @return the channel name for the UI and the logs
 */
inline const char *DescribeActiveChannel() {
    return IsQxActive() ? "Internal Engine (Direct Syscall)" : "RT/QX (Ring-0 Kernel Driver)";
}

} // namespace NinChannel

#endif // NINMODS_ENGINE_CHANNEL_H
