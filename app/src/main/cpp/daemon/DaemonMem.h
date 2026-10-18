#pragma once
#include <cstdint>
#include <cstddef>
#include <sys/types.h>
#include <unistd.h>
#include <string>

// Single Master Memory Engine for Standalone Root Daemon
// Automatically selects Kernel Driver IOCTL if available,
// or transparently falls back to Root process_vm_readv / /proc/pid/mem.
class MasterMem {
public:
    static MasterMem& Get();

    MasterMem();
    ~MasterMem();

    bool Init(int pid, bool isMax);
    void Detach();

    bool Read(uint64_t addr, void* buf, size_t size);
    bool Write(uint64_t addr, const void* buf, size_t size);
    uint64_t GetModuleBase(const char* name);

    template <typename T>
    inline T ReadVal(uint64_t addr) {
        T val{};
        Read(addr, &val, sizeof(T));
        return val;
    }

    template <typename T>
    inline bool WriteVal(uint64_t addr, T val) {
        return Write(addr, &val, sizeof(T));
    }

    inline uint64_t ReadPtr(uint64_t addr, bool is64) {
        if (addr == 0) return 0;
        if (is64) return ReadVal<uint64_t>(addr);
        return static_cast<uint64_t>(ReadVal<uint32_t>(addr));
    }

    int GetPid() const { return m_pid; }
    int GetDriverFd() const { return m_driverFd; }
    bool IsAttached() const { return m_pid > 0; }
    bool IsKernelMode() const { return m_driverFd > 2; }
    const char* GetEngineName() const { return m_driverFd > 2 ? "KERNEL_RT" : "ROOT_IPC"; }
    void SetDriverFd(int fd) { m_driverFd = (fd > 2) ? fd : -1; }
    void SetIl2cppBase(uint64_t base) { if (base > 0) m_il2cppBase = base; }
    uint64_t GetCachedIl2cppBase() const { return m_il2cppBase; }

private:
    int m_pid;
    bool m_isMax;
    int m_driverFd;
    int m_memFd;
    uint64_t m_il2cppBase;
    char m_activeNode[128];

    void ProbeKernelDriver();
    void OpenProcMem();
};
