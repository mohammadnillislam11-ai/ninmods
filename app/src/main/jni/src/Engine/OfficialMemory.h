#ifndef NINMODS_OFFICIAL_MEMORY_H
#define NINMODS_OFFICIAL_MEMORY_H

/**
 * OFFICIAL LINUX PROCESS MEMORY ENGINE (POSIX & Syscalls)
 * =======================================================
 * Bridges OfficialMemory to the central official Linux MemoryIO implementation.
 *
 * All operations execute exclusively via:
 *  - Official Linux Syscall: process_vm_readv(2) / process_vm_writev(2)
 *  - Fallback POSIX: /proc/[pid]/mem pread64(2) / pwrite64(2)
 *  - Official POSIX: /proc/[pid]/maps for module base lookup
 */

#include "../Includes/MemoryIO.h"

#endif // NINMODS_OFFICIAL_MEMORY_H
