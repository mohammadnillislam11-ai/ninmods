# `main_app` Module Documentation

## 1. Overview
`main_app` (NINMODS Menu) is the native floating overlay and ESP HUD application. It renders an eagle-head floating button, provides a neon cyberpunk mod menu, attaches to the target game process, reads and writes memory via the dual-engine bridge, and renders a 60 FPS hardware-accelerated canvas overlay.

## 2. Directory Structure
```
main_app/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Window alert overlay permissions & services
│   │   ├── cpp/                         # Native memory client & fallback JNI
│   │   │   ├── CMakeLists.txt
│   │   │   ├── driver_client.h          # IOCTL data structures & function signatures
│   │   │   ├── driver_client.cpp        # Dual-Engine: IOCTL + process_vm_readv fallback
│   │   │   └── memory_native.cpp        # JNI dispatchers for DriverBridge
│   │   └── java/com/ninmods/menu/
│   │       ├── NinApplication.kt        # Application lifecycle
│   │       ├── MainActivity.kt          # Overlay permissions & service launch
│   │       ├── service/                 # Floating menu & ESP canvas overlay services
│   │       ├── memory/                  # PID detection, IL2CPP offsets & memory reader
│       │   ├── ipc/                     # Direct RT driver JNI bridge
│   │       └── ui/theme/                # Theme colors and typography tokens
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## 3. How It Works (Workflow Mapping)
1. **Overlay Services**:
   - Starting the mod starts `FloatingMenuService`, rendering a 1:1 draggable floating logo and neon mod menu.
   - Concurrently, `EspOverlayService` creates a fullscreen transparent canvas (`EspCanvasView`) with hardware acceleration for drawing ESP boxes, snaplines, skeletons, and health bars.
2. **Process Discovery & Offset Mapping**:
   - `GameProcess` discovers the running Free Fire PID using `ps -A`.
   - `MemoryReader` interfaces with the native library, while `OffsetManager` provides the IL2CPP pointer offsets for player entities, root transforms, and camera view matrices.
3. **Dual-Engine Memory Bridge**:
   - `driver_client.cpp` automatically selects the execution pathway:
     * Fast Path: `/dev/ninmods_driver` kernel IOCTL when kernel module is loaded.
     * Universal Fallback: Direct `process_vm_readv` syscall, `/proc/[pid]/mem` file reading, and `/proc/[pid]/maps` parsing when running in fallback mode.
