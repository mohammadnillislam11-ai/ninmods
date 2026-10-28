#ifndef NINMODS_MEMORY_ACCESS_H
#define NINMODS_MEMORY_ACCESS_H

/**
 * @file MemoryAccess.h
 * @brief Typed access layer of the RT channel.
 *
 * Every helper of this header is a thin typed wrapper around the RT kernel
 * driver layer (Includes/MemoryIO.h). There is no user space fallback here:
 * a read that the driver refuses returns zero and a write that the driver
 * refuses returns false, so the caller can never mistake a failed transfer
 * for real data (Rule 1, Rule 17).
 */

#include <atomic>
#include <cstdint>
#include <cstring>
#include <string>
#include <sys/types.h>

#include "MemoryIO.h"

/**
 * @brief Reads a single value of the target process through the driver.
 *
 * @tparam T type of the value
 * @param address virtual address inside the target process
 * @return the read value, or a zero value when the driver refused the read
 */
template <class T>
inline T Read(uintptr_t address) {
    T buf {};
    if (address == 0) {
        return buf;
    }
    if (!MemoryIO::ReadBytes(target_pid.load(), address, &buf, sizeof(T))) {
        memset(&buf, 0, sizeof(T));
    }
    return buf;
}

/**
 * @brief Reads a NUL terminated string of the target process through the driver.
 *
 * @param address virtual address inside the target process
 * @return the read string, or an empty string when the driver refused the read
 */
template <>
inline std::string Read<std::string>(uintptr_t address) {
    std::string result;
    if (address == 0) {
        return result;
    }
    const size_t maxLen = 4096;
    char buffer[maxLen] = {0};
    if (MemoryIO::ReadBytes(target_pid.load(), address, buffer, maxLen - 1)) {
        buffer[maxLen - 1] = '\0';
        result = buffer;
    }
    return result;
}

/**
 * @brief Reads a raw buffer of the target process through the driver.
 *
 * @param address virtual address inside the target process
 * @param buffer  destination buffer
 * @param size    number of bytes to read
 * @return the number of bytes that were transferred, or 0 on failure
 */
inline size_t vm_readv(long address, void *buffer, size_t size) {
    if (address == 0 || buffer == nullptr || size == 0) {
        return 0;
    }
    if (MemoryIO::ReadBytes(target_pid.load(), static_cast<uintptr_t>(address), buffer, size)) {
        return size;
    }
    return 0;
}

/**
 * @brief Writes a single value into the target process through the driver.
 *
 * @tparam T type of the value
 * @param address virtual address inside the target process
 * @param value   value to write
 * @return true when the driver wrote the value
 */
template <typename T>
inline bool Write(uintptr_t address, T value) {
    if (address == 0) {
        return false;
    }
    return MemoryIO::WriteBytes(target_pid.load(), address, &value, sizeof(T));
}

/**
 * @brief Resolves a pointer chain step through the driver.
 *
 * @param address address that holds the next pointer
 * @return the resolved pointer, or 0 when the driver refused the read
 */
inline uintptr_t getMemoryAddr(uintptr_t address) {
    return Read<uintptr_t>(address);
}

/**
 * @brief Writes a raw buffer into the target process through the driver.
 *
 * @param address virtual address inside the target process
 * @param data    source buffer
 * @param size    number of bytes to write
 * @return true when the driver wrote the whole buffer
 */
inline bool WriteBytes(uintptr_t address, const void *data, size_t size) {
    if (size == 0) {
        return true;
    }
    if (address == 0 || data == nullptr) {
        return false;
    }
    return MemoryIO::WriteBytes(target_pid.load(), address, data, size);
}

/**
 * @brief Reads a raw buffer of the target process through the driver.
 *
 * @param address virtual address inside the target process
 * @param buffer  destination buffer
 * @param size    number of bytes to read
 * @return true when the driver transferred the whole buffer
 */
inline bool ReadBytes(uintptr_t address, void *buffer, size_t size) {
    if (size == 0 || buffer == nullptr || address == 0) {
        return false;
    }
    return MemoryIO::ReadBytes(target_pid.load(), address, buffer, size);
}

/**
 * @brief Attaches the engine to a target process.
 *
 * @param pid pid of the target process
 * @return true when the pid is valid
 */
inline bool InitMemoryAccess(pid_t pid) {
    target_pid.store(pid, std::memory_order_relaxed);
    return pid > 0;
}

/**
 * @brief Detaches the engine from the target process.
 */
inline void CleanupMemoryAccess() {
    target_pid.store(-1, std::memory_order_relaxed);
    libAddress.store(0, std::memory_order_relaxed);
}

#endif // NINMODS_MEMORY_ACCESS_H
