#pragma once
#include <cstdint>
#include <cstddef>
#include <sys/types.h>
#include <unistd.h>

enum class DriverMode : uint8_t {
    None = 0,
    RT = 1,
    QX = 2
};

#define IOCTL_MEM_READ          0x801
#define IOCTL_MEM_WRITE         0x802
#define IOCTL_MODULE_BASE       0x803

struct DriverMemoryIo {
    uint32_t pid;
    uint32_t _pad;
    uint64_t address;
    uint64_t buffer;
    uint64_t size;
};

struct DriverModuleBaseIo {
    uint32_t pid;
    uint32_t _pad;
    uint64_t name_ptr;
    uint64_t base;
};

static_assert(sizeof(DriverMemoryIo) == 32, "DriverMemoryIo must be exactly 32 bytes");
static_assert(sizeof(DriverModuleBaseIo) == 24, "DriverModuleBaseIo must be exactly 24 bytes");

int DriverOpen(DriverMode mode);
int DriverGetOpenFd();
void DriverClose(int fd);
uint64_t DriverAttach(int fd, uint32_t pid);
void DriverDetach(int fd, uint64_t handle);
bool DriverRead(int fd, uint64_t handle, uint64_t addr, void* buf, size_t size);
bool DriverWrite(int fd, uint64_t handle, uint64_t addr, const void* buf, size_t size);
uint64_t DriverGetModuleBase(int fd, uint32_t pid, const char* name);
const char* DriverGetActiveNode();
