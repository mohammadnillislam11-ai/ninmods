# `service` Package Documentation (`com.ninmods.menu.service`)

## 1. Overview
The `service` package contains the foreground overlay services responsible for presenting the floating eagle logo, building the interactive cyberpunk mod menu panel, and rendering the 60 FPS ESP HUD overlay.

## 2. Directory & File Structure
```
service/
├── service.md              # Package documentation
├── FloatingMenuService.kt  # Draggable floating window manager (< 190 lines)
├── MenuPanelBuilder.kt     # Cyberpunk neon layout builder (< 195 lines)
└── EspOverlayService.kt    # Fullscreen hardware-accelerated HUD canvas service (< 90 lines)
```

## 3. How It Works (Workflow Mapping)
1. **`FloatingMenuService.kt`**:
   - Creates a `WindowManager` overlay with `TYPE_APPLICATION_OVERLAY`.
   - Displays the floating eagle icon. Tapping toggles between the icon and the expanded menu panel.
   - Attaches `DragTouchListener` for 1:1 screen dragging with automatic boundary clamping.
2. **`MenuPanelBuilder.kt`**:
   - Constructs the tabbed mod menu:
     * **ESP Tab**: Toggles for Box & Line, Skeleton, Health Bar, Name, Distance.
     * **Aimbot Tab**: Aimbot Auto Lock, Head Collider, Silent Aim, Auto Trigger, FOV slider (10–360°), Trigger modes (2x2 grid).
     * **Fly Hack Tab**: Enable Fly Hit, Speed slider (0–50), Height slider (0–80).
     * **Guest Reset Tab**: Guest ID reset button.
3. **`EspOverlayService.kt`**:
   - Spawns an untouchable, fullscreen overlay canvas (`EspCanvasView`).
   - Runs a 60 FPS render loop reading tracked enemy data from `EnemyTracker` and drawing 2D bounding boxes, snaplines, and health meters.
