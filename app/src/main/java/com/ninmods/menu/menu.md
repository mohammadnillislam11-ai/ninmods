# `menu` Package Documentation (`com.ninmods.menu`)

## 1. Overview
The `menu` package is the top-level orchestration layer for the in-game mod menu application (`main_app`). It coordinates login validation, foreground service management, overlay permissions, and UI lifecycle.

## 2. Directory & File Structure
```
com/ninmods/menu/
├── menu.md             # Package documentation
├── NinApplication.kt   # Application class initializing global state (< 40 lines)
├── MainActivity.kt     # Main launcher handling overlay permission & start button (< 130 lines)
├── service/            # Floating window overlay and hardware-accelerated ESP canvas
├── memory/             # Game process detection, IL2CPP offsets & direct memory reader
├── ipc/                # Explicit RT/QX driver JNI bridge
└── ui/theme/           # Cyber navy color palette, typography and Compose themes
```

## 3. How It Works (Workflow Mapping)
- **`MainActivity.kt`**:
  * Verifies `Settings.canDrawOverlays(this)`. If permission is absent, prompts the user to grant "Display over other apps".
  * Upon permission approval, displays the start launcher.
  * Tapping "START MOD MENU" starts `FloatingMenuService` and `EspOverlayService` via foreground service intents.
- **`NinApplication.kt`**:
  * Initializes application-wide state containers and logging tags.
  * The driver channel is selected explicitly in the driver status panel; RT and QX
    remain isolated and do not automatically fall back to each other.
