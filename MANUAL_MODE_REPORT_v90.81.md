# NINMODS v90.81 — Manual-Only Mode & Non-Root Crash Fix

**Date:** 2026-10-27
**Version:** 90.80 → **90.81** (version_code 980 → **981**)
**Problem:** Non-root device e menu theke feature on korlei app freeze/ANR ( "NINMODS সাদা দিচ্ছে না" ), ar background e constant auto driver-probe (`[c_driver] DRIVER_FAIL` loop) chaluchhe. SAFE-mode gate feature toggle block korche.

**Goal (user request):** Shob custom/auto system remove — nothing runs automatically. Everything is enabled ONLY by the user, manually, from the menu.

---

## 1. Root Causes Found

| # | Cause | Symptom |
|---|-------|---------|
| 1 | `c_driver::initDriver()` was called from **every memory read** while offline | Hundreds of `open()` syscalls + 2 log lines per engine tick |
| 2 | `Detect()` ran `popen("su -c ...")` and full `/proc` walks **on the UI thread** (menu polls every 500 ms) | A hanging `su` binary blocks the UI → **ANR dialog** |
| 3 | `CheckShellDaemonRunning()` walked every process cmdline on the UI thread, no cache | Extra /proc load per 500 ms poll |
| 4 | SAFE-mode gate (`IsFeatureBlockedInSafeMode`) refused features 3, 20, 22, 23, 33, 41, 42, 43, 44, 216 | `BLOCKED: feature 22 is not available in SAFE mode` — user cannot toggle manually |
| 5 | `NativeBridge.runAsRoot()` used unbounded `waitFor()` | A broken `su` parks the calling thread forever |

## 2. Changes

### 2.1 Driver probing is now MANUAL (`Includes/driver.h`)
- New gate: `c_driver::AllowProbing()` / `s_probeAllowed` (default **off**).
- `initDriver()` returns immediately while the gate is closed — **zero probes, zero logs** until the user acts.
- Probe cooldown added: at most **one probe per 2 seconds** from any thread, so offline memory reads fail fast instead of re-probing on every read.
- The gate is opened only by manual user actions:
  - any feature toggle → `NinFeatureSetState()` (`Client.cpp`)
  - the **Start** button → `NinliteEngine::StartEngine()`

### 2.2 Per-read probe storm fixed (`Includes/Utils.h`, `GameEngine.h`)
- `OpenDriver()` now has the same 2-second attempt cooldown (one log pair per 2 s, not per read).
- `CreateDataList()` no longer calls unthrottled `InitMemoryAccess()`; it uses `EnsureDriverConnectedThrottled()`.

### 2.3 Game detection moved OFF the UI thread (`Detection/GameDetection.cpp`)
- `Detect()` now only **reads the cached state** (a `kill(pid,0)` check + one lock). It never spawns a shell or walks /proc on the caller.
- At most **one background scan per second** on a short-lived detached thread (daemon query → root shell scan → /proc walk). One scan in flight at a time.
- `RootShellScan` is gated behind `RootAvailable()` — an `access()` check of the known `su` paths, cached. **A non-root device never spawns a shell at all.**

### 2.4 SAFE-mode gate REMOVED (`Client.cpp`)
- `IsFeatureBlockedInSafeMode()`, `isButrulVersion()`, `g_hackVersion` — deleted.
- Every feature id is now toggled exactly as the user sets it in the menu. No refusal, no auto state.
- `setModeNative` JNI exports kept as no-ops (Java still calls them).
- Dead `EnsureDriverConnected()` (defined, never called) removed.

### 2.5 UI-thread /proc load removed (`Engine/NinliteEngine.cpp`)
- `CheckShellDaemonRunning()`: the /proc cmdline fallback result is cached for 3 seconds (the menu polls it every 500 ms).

### 2.6 Root shell wait bounded (`NativeBridge.java`)
- `runAsRoot()`: output drain moved to its own thread; `join(25 s)` bound; `destroyForcibly()` + `interrupt()` on timeout. A broken `su` can no longer park a thread forever.

## 3. What is automatic now (intended)

Nothing that touches the game or the kernel:

| Action | Trigger |
|--------|---------|
| Driver node probe | only after a manual feature toggle / Start, ≤ 1 per 2 s |
| Game process scan | background thread, ≤ 1 per 2 s while the panel polls status (display only) |
| Feature execution | only while the user's toggle is on |
| Daemon injection | only when the user presses **Start** |

## 4. Files Changed (8 files)

```
app/src/main/java/com/ninmods/main/core/NativeBridge.java   | 33 ++++++--
app/src/main/jni/src/Client.cpp                             | 70 +++++------------
app/src/main/jni/src/Detection/GameDetection.cpp            | 91 ++++++++++++++++------
app/src/main/jni/src/Engine/NinliteEngine.cpp               | 53 +++++++++----
app/src/main/jni/src/GameEngine.h                           |  6 +-
app/src/main/jni/src/Includes/Utils.h                       | 12 +++
app/src/main/jni/src/Includes/driver.h                      | 38 +++++++++
app/src/main/res/values/version.xml                         |  4 +-
```

## 5. Verification

- [x] Brace balance on all 6 modified C++ files (diff = 0)
- [x] No dangling references: `isButrulVersion`, `g_hackVersion`, `IsFeatureBlockedInSafeMode`, dead `EnsureDriverConnected()`
- [x] Non-root device: `RootAvailable()` false → no `su` spawn; probe gate closed → no `/dev` probing until a manual toggle
- [x] UI thread: no shell spawn, no /proc walk, no unthrottled probe on any menu-poll path
- [ ] Compile verification — CI (Rule 12: no local compiler)

## 6. Expected Behaviour After This Build (non-root test device)

1. App opens → **no** `[c_driver] DRIVER_INIT` spam, **no** `su` spawns.
2. Menu opens → status rows update from a cheap cached state (background scan).
3. Toggling ANY feature (aim, ESP, etc.) → the flag is set, `BLOCKED` lines are gone. With no driver the engine reads return 0 and nothing renders — no crash, no freeze.
4. Pressing **Start** → the root script is attempted once; on non-root it fails fast (≤ 25 s bound) and the status stays "Standby".
5. On a rooted device with the loader: the first probe happens within 2 s of the first manual action, and the engine works as before.
