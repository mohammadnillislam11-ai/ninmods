# Memory and driver connection

`GameProcess` discovers the target process. `MemoryReader` opens only the RT or QX
channel explicitly selected by the user and delegates driver operations to
`DriverBridge`/`DriverClient.cpp`.

The app treats endpoint open and target verification as separate states. Target
verification requires the selected channel's module-base ioctl to resolve
`libil2cpp.so`; the resulting handle is bound to the current open channel.

There is no automatic RT/QX fallback, direct `/proc/<pid>/maps` base lookup, or
app-side SELinux/device-permission mutation in this connection path. QX is limited
to its selected endpoint list, which includes shared candidates used by QX loader
payload variants. The protocol check cannot identify the owning module on a shared
node. See [`cpp.md`](../../../../cpp/cpp.md) for the endpoints and statically checked
ioctl contract.
