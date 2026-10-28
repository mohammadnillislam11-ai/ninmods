# NINMODS — Memory Architecture & Systems Interface (`docs/MEMORY.md`)

This document specifies the low-level memory reading and IPC architecture used across `ninmods` and `ninlite`.

---

## 1. High-Level Architecture Overview

```
+-------------------------------------------------------------------------+
|                              Android UI                                 |
|         (FloatingMenuView, EspOverlayService, MenuController)           |
+-------------------------------------------------------------------------+
                                    |
                                    v  JNI Bridge (Java_com_ninmods_main_*)
+-------------------------------------------------------------------------+
|                           Native C++ Core                               |
|        (FeatureTable, AimCore, EspEngine, MemoryIO, Menu.cpp)           |
+-------------------------------------------------------------------------+
                    |                                 |
                    | (ninmods: Ring-0)               | (ninlite: Internal)
                    v                                 v
+---------------------------------------+   +-----------------------------+
|    Genuine Linux Kernel Driver        |   |    Official Linux Syscall   |
|         (/dev/BYNb Character Node)    |   |     (process_vm_readv)      |
|    IOCTL_RT_READ  (0x801)             |   +-----------------------------+
|    IOCTL_RT_WRITE (0x802)             |                 |
|    IOCTL_RT_BASE  (0x803)             |                 |
+---------------------------------------+                 |
                    |                                     |
                    +------------------+------------------+
                                       |
                                       v
                     +-----------------------------------+
                     |      Target Game Process Memory   |
                     |         (Unity / libil2cpp.so)    |
                     +-----------------------------------+
```

---

## 2. Kernel Driver Mode (`ninmods`)

`ninmods` operates exclusively through a genuine Linux kernel module character device driver.

### Character Device Nodes Probed
The native engine probes the following character device nodes in order:
1. `/dev/BYNb` (Primary standard node)
2. `/dev/mem_tool`
3. `/dev/nh`
4. `/dev/wanbai`
5. `/proc/uevents_records`

### IOCTL Command Definitions
Located in `Includes/MemoryIO.h`:
```c
#define IOCTL_RT_READ   0x801
#define IOCTL_RT_WRITE  0x802
#define IOCTL_RT_BASE   0x803
```

### IOCTL Data Structures
```c
// 32-byte memory copy structure for IOCTL_RT_READ and IOCTL_RT_WRITE
struct COPY_MEMORY {
    pid_t pid;          // Target game process PID
    uintptr_t addr;     // Virtual memory address in target process
    void *buffer;       // Destination buffer in current process
    size_t size;        // Number of bytes to read/write
};

// 24-byte module lookup structure for IOCTL_RT_BASE
struct MODULE_BASE {
    pid_t pid;          // Target game process PID
    char *name;         // Null-terminated module name (e.g. "libil2cpp.so")
    uintptr_t base;     // Resolved virtual base address returned by driver
};
```

### SELinux & Node Permissions
Before communication, root privileges apply permissions:
```sh
chmod 666 /dev/BYNb* /dev/mem_tool* /dev/nh* /dev/wanbai* /proc/uevents_records
setenforce 0
```

---

## 3. Internal Mode (`ninlite`)

`ninlite` is designed for internal execution:
- Uses the Linux kernel syscall `process_vm_readv` (Syscall number 270 on AArch64, 376 on ARMv7).
- Directly reads memory buffers between processes without injecting libraries into the game address space.
- Fallback: Direct POSIX `/proc/[pid]/mem` via `pread64` with file descriptor caching.

---

## 4. Single Central Offsets Architecture (`gameoffsets.h`)

All virtual memory offsets (pointers, matrices, entity coordinates, health, bone matrices) are strictly defined in **exactly one file**:
- `app/src/main/jni/src/Includes/gameoffsets.h`
- Never duplicate offsets in Java, Kotlin, or multiple header files.
- The offsets file acts as the `.env` configuration file for the C++ game engine.
