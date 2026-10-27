# NINMODS v90.79 — Parity & Cleanup Report

**Date:** 2026-10-27  
**Version:** 90.78 → **90.79**  
**Version Code:** 978 → **979**  
**Branch:** `parity/ninlite-kernel-sync`  
**Status:** ✅ Complete (pending CI / user push)

---

## 1. Version Bump

| File | Before | After |
|------|--------|-------|
| `app/src/main/res/values/version.xml` | 90.78 / 978 | **90.79 / 979** |

---

## 2. Engine Parity (Kernel ↔ NINLITE Daemon)

### 2.1 Single Canonical Feature Writer
- **File:** `Client.cpp`
- Removed 152-line dead `Floater_ChangesID` duplicate writer.
- All feature state now flows through `NinFeatureSetState(int featId, bool bState)`.
- SAFE-mode gate `IsFeatureBlockedInSafeMode()` enforces blocked features: **3, 20, 22, 23, 33, 41, 42, 43, 44, 216**.

### 2.2 Head / Chest Shot Pattern (STORM Reference)
- **Files:** `GameEngine.h`, `ninlite_daemon.cpp`
- Replaced broken 100-tick counter with `HeadRate` + `ChestRate` pattern array.
- Daemon now builds the same pattern as the kernel engine and advances it on every fire burst.

### 2.3 FOV Circle Unification
- **Files:** `Client.cpp`, `GameEngine.h`, `ninlite_daemon.cpp`
- Unified formula across all 3 locations:
  ```
  radius = (AimFov / 180) * (screenWidth * 0.55)
  ```
- Previously: daemon used `screenHeight * 0.55`, Client.cpp used height too — caused aim target mismatch across engines.

### 2.4 Target Selection Rules
- **File:** `ninlite_daemon.cpp`
- Now matches kernel engine exactly:
  - Target must be **on-screen**
  - FOV circle checked **only** for silent aim & hard aim (not for collider lock)
  - Closest target to crosshair wins (screen distance only, no world distance mixing)

### 2.5 KillInChest (Daemon)
- **Files:** `NinliteProtocol.h`, `BooleanServer.h`, `ninlite_daemon.cpp`
- Added `MODE_KILL_IN_CHEST = 61` to protocol.
- Daemon now interpolates chest point (35% head→hip) when pattern selects chest.
- Previously completely non-functional in NINLITE mode.

### 2.6 Threat Counter
- **File:** `ninlite_daemon.cpp`
- Added `enemiesInFront` / `enemiesBehind` dot-product calculation (was zero in NINLITE mode).

---

## 3. Dead Code Removal

| Removed | Location | Reason |
|---------|----------|--------|
| `imgui/Toggle.h` | `src/imgui/` | Unused ImGui widget |
| `ESP espOverlay` global | `Client.cpp` | Dead global variable |
| `Floater_ChangesID` writer | `Client.cpp` | 152-line duplicate, divergent IDs |
| `Floater` Java class references | `Client.cpp` | Java class already removed |
| `fovawm` flag | `BooleanClient.h` | Never referenced |
| `selectTarget` flag | `BooleanClient.h`, `BooleanServer.h` | Never referenced |
| `targetByDistance` flag | `BooleanClient.h` | Never referenced |

---

## 4. Build System Fix

| File | Fix |
|------|-----|
| `Android.mk` | `src/Detector` → `src/Detection` (path rename) |
| `Android.mk` | Removed `src/UI/Brands` (directory deleted) |

---

## 5. Default Value Fixes

| File | Field | Old Default | New Default | Impact |
|------|-------|-------------|-------------|--------|
| `BooleanServer.h` | `IgnoreKnock` | `false` | **`true`** | Daemon ignored knocked players on fresh start (mismatch) |
| `BooleanServer.h` | `AimFov` | `0.0f` | **`60.0f`** | Daemon had zero FOV until first request |
| `BooleanServer.h` | `AimDistance` | `250.0f` | **`150.0f`** | Daemon aimed at different range than kernel |

---

## 6. Files Changed (11 files)

```
app/src/main/java/com/ninmods/main/esp/ESPView.java        |   8 +-
app/src/main/jni/Android.mk                                 |   3 +-
app/src/main/jni/src/BooleanClient.h                        |   4 -
app/src/main/jni/src/Client.cpp                           | 723 +++++++++++++++------
app/src/main/jni/src/GameEngine.h                        |  55 +-
app/src/main/jni/src/Includes/NinliteProtocol.h           |  14 +-
app/src/main/jni/src/Includes/gameoffsets.h              |   2 +
app/src/main/jni/src/imgui/Toggle.h                      |  40 --
app/src/main/jni/src/ninlite_daemon/BooleanServer.h       |  15 +-
app/src/main/jni/src/ninlite_daemon/ninlite_daemon.cpp   | 109 +++-
app/src/main/res/values/version.xml                      |   4 +-
------------------------------------------------------------
11 files changed, 728 insertions(+), 249 deletions(-)
```

---

## 7. Remaining Tasks (Blocked by Rules)

| Task | Status | Blocker |
|------|--------|---------|
| Compile verification | ⏳ Pending | Rule 12 — no local compiler; use CI only |
| Git push to remote | ⏳ Pending | Rule 14 — requires explicit "push" command from user |

---

## 8. Verification Checklist

- [x] Brace balance checked across all 6 modified files
- [x] No duplicate function definitions
- [x] `localPlayer` scope verified for `OffIsFiring()` call
- [x] SAFE-mode blocked feature list matches specification
- [x] FOV formula identical in Client.cpp, GameEngine.h, and ninlite_daemon.cpp
- [x] Daemon target selection matches kernel engine rules
- [x] Version bumped to 90.79 / 979
- [x] Dead flags removed (fovawm, selectTarget, targetByDistance)
- [x] Android.mk paths corrected

---

**Next step:** Run CI build for compile verification, then `git push` when ready.
