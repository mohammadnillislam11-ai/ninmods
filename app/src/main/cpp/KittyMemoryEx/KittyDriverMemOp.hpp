#pragma once

#include "KittyMemOp.hpp"
#include "../DriverClient.h"

class KittyDriverMemOp : public IKittyMemOp
{
private:
    uint64_t _handle = 0;

public:
    KittyDriverMemOp() : IKittyMemOp(), _handle(0)
    {
    }

    bool init(pid_t pid) override
    {
        _pid = pid;
        int fd = DriverGetOpenFd();
        if (_pid > 0 && fd >= 0) {
            _handle = DriverAttach(fd, static_cast<uint32_t>(_pid));
        }
        return (_pid > 0 && _handle != 0);
    }

    size_t Read(uintptr_t address, void *buffer, size_t len, MemMode mode = MemMode::Normal) override
    {
        int fd = DriverGetOpenFd();
        if (!_pid || !buffer || !len || fd < 0 || !_handle) return 0;
        return DriverRead(fd, _handle, address, buffer, len) ? len : 0;
    }

    size_t Write(uintptr_t address, void *buffer, size_t len, MemMode mode = MemMode::Normal) override
    {
        int fd = DriverGetOpenFd();
        if (!_pid || !buffer || !len || fd < 0 || !_handle) return 0;
        return DriverWrite(fd, _handle, address, buffer, len) ? len : 0;
    }
};
