#ifndef NINMODS_MEMORY_ACCESS_H
#define NINMODS_MEMORY_ACCESS_H

/*
 * MEMORY ACCESS - typed API for the target process.
 * All calls go to MemoryIO (process_vm_readv / process_vm_writev syscalls).
 * 100% genuine Linux official APIs with zero fake or custom hacks.
 */

#include <atomic>
#include <cstdint>
#include <cstring>
#include <string>
#include <sys/types.h>
#include "MemoryIO.h"

/* Typed read of one value from the target. A failed transfer yields zero. */
template <class T>
inline T Read(uintptr_t address) {
    T buf{};
    if (address == 0) return buf;
    if (!MemoryIO::ReadBytes(target_pid.load(), address, &buf, sizeof(T))) {
        memset(&buf, 0, sizeof(T));
    }
    return buf;
}

/* Typed read of a NUL terminated string, at most 4095 bytes. */
template <>
inline std::string Read<std::string>(uintptr_t address) {
    std::string result;
    if (address == 0) return result;
    const size_t max_len = 4096;
    char buffer[max_len] = {0};
    if (MemoryIO::ReadBytes(target_pid.load(), address, buffer, max_len - 1)) {
        buffer[max_len - 1] = '\0';
        result = buffer;
    }
    return result;
}

inline size_t vm_readv(long address, void *buffer, size_t size) {
    if (address == 0 || buffer == nullptr || size == 0) return 0;
    if (MemoryIO::ReadBytes(target_pid.load(), (uintptr_t)address, buffer, size)) {
        return size;
    }
    return 0;
}

/* Typed write of one value to the target. */
template <typename T>
inline bool Write(uintptr_t address, T value) {
    if (address == 0) return false;
    return MemoryIO::WriteBytes(target_pid.load(), address, &value, sizeof(T));
}

inline uintptr_t getMemoryAddr(uintptr_t address) {
    return Read<uintptr_t>(address);
}

inline bool WriteBytes(uintptr_t address, const void *data, size_t size) {
    if (size == 0) return true;
    if (address == 0 || data == nullptr) return false;
    return MemoryIO::WriteBytes(target_pid.load(), address, data, size);
}

inline bool ReadBytes(uintptr_t address, void *buffer, size_t size) {
    if (size == 0 || buffer == nullptr || address == 0) return false;
    return MemoryIO::ReadBytes(target_pid.load(), address, buffer, size);
}

/* Attach: store the target pid. */
inline bool InitMemoryAccess(pid_t pid) {
    target_pid.store(pid, std::memory_order_relaxed);
    return pid > 0;
}

/* Detach: reset target pid. */
inline void CleanupMemoryAccess() {
    target_pid.store(-1, std::memory_order_relaxed);
    libAddress.store(0, std::memory_order_relaxed);
}

#endif // NINMODS_MEMORY_ACCESS_H
