# Black Screen on First Launch — Root Cause & Fix

**Project:** `nin` (NINMODS Main App)
**Symptom:** A user who opens the app for the first time can end up on a completely black screen.
**Commit:** `fbfd5b1` — *fix(ui): remove the black screen on first launch for new users*
**Status:** all changes are pushed. **No APK was built or installed on a device in this
environment** (no Android SDK/NDK, project rule 11), so the fix is verified by source review
and by a `javac` syntax check only. A device smoke test is still required.

---

## 1. Why a black screen is even possible

The app theme sets the window background to a near black colour:

```xml
<!-- app/src/main/res/values/styles.xml -->
<style name="AppTheme" parent="@android:style/Theme.Material.NoActionBar">
    <item name="android:windowBackground">@color/nin_bg_dark</item>   <!-- #0A0C12 -->
```

Every screen in this app builds its own view **programmatically inside `onCreate()`**
(`MainActivity.buildSplashView()`, `LoginActivity.buildView()`, …). None of them uses a layout
XML for the content. That produces the failure mode:

> An exception is thrown anywhere between `Activity.onCreate()` and `setContentView()`
> → the activity dies **before it puts anything on the screen**
> → Android paints the window background
> → the user sees a flat, black screen.

There was no `try/catch` anywhere on that path, and the global crash handler installed by
`AppLogger` only writes to the log file — it never replaces the black window with anything
readable.

---

## 2. The concrete causes

### 2.1 No exception handling around the programmatic view construction
`MainActivity.onCreate()`, `LoginActivity.onCreate()`, `SignupActivity.onCreate()` and
`ResetPasswordActivity.onCreate()` all called `buildView()` unguarded. Any failure inside
those methods (resource lookup, inflation of a custom view, a field that is still `null`)
ended the activity before `setContentView()` was ever reached.

For a **new user** this is the most likely path: `MainActivity.startApp()` routes to
`LoginActivity` (an existing, signed-in user goes to `DashboardActivity` instead), so the
first screen a fresh install renders is the one built from scratch.

### 2.2 `ParticleNetworkView` can paint nothing at all
```java
// ui/ParticleNetworkView.java
protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (viewWidth <= 0 || viewHeight <= 0 || particleCount == 0) return;   // paints NOTHING
    canvas.drawColor(BG_COLOR);
```
The view itself had **no background**, and every screen above it uses a **transparent** root
(`root.setBackgroundColor(Color.TRANSPARENT)` in `LoginActivity`, `SignupActivity`,
`ResetPasswordActivity` and `DashboardActivity`). While the size is not yet known the view
paints nothing at all, so the window background shows through unchanged.

### 2.3 The forced full screen hardware layer
```java
setLayerType(LAYER_TYPE_HARDWARE, null);
```
`LAYER_TYPE_HARDWARE` allocates an **offscreen framebuffer for the entire screen**. The
project already hit the worse variant of this bug: commit `97c7ded`
*"Fix: Resolve black screen on Mali/Android 7-11"* replaced `LAYER_TYPE_SOFTWARE` (a full
screen bitmap → out of memory → solid black) with the hardware layer. The hardware layer is
better than software, but it still allocates a screen sized buffer, and that allocation is
exactly what fails on the affected Mali / Android 7-11 devices. The window is hardware
accelerated anyway, so the extra buffer buys nothing.

### 2.4 `AppLogger` used a shared, non thread safe `SimpleDateFormat`
```java
private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
```
`SimpleDateFormat` is **not** thread safe, and `AppLogger.log()` was called concurrently from
at least three threads: the UI thread (in `MainActivity.onCreate()`, *before* the splash view
exists), the background thread that requests root, and the file writer thread. A
`format()` failure there propagates straight out of `onCreate()` → black screen.
`AppLogger.log()` / `logE()` also had **no `try/catch`**, so any failure killed the caller.

### 2.5 (introduced by the previous hardening pass) A missing native library locked the account out
`SecurityVault.init()` was changed to fail closed when the native library could not be loaded,
and `AuthApiController` then refused every login, registration, password reset and licence
verification. On a device where `libninmods.so` cannot be loaded (unsupported ABI, older
Android), the user could not sign in **at all** — permanently, with no way out. A missing
library is not a tampered build: the native gate simply cannot be satisfied, so the engine
stays locked anyway while the account still has to be able to sign in.

---

## 3. The fix

| File | Change |
|---|---|
| `core/BaseActivity.java` | The themed base wrapper is attached **in `onCreate()`**, so the window is never a bare background. New `showFatalError(title, message)` renders a readable error panel with a **RELOAD APP** button. |
| `core/MainActivity.java` | `onCreate()` wrapped in `try/catch` → `showFatalError()` instead of a crash. |
| `auth/LoginActivity.java` | `buildView()` wrapped in `try/catch` → `showFatalError()`. |
| `auth/SignupActivity.java` | same. |
| `auth/ResetPasswordActivity.java` | same. |
| `ui/ParticleNetworkView.java` | The view paints its **own opaque backdrop** (`setBackgroundColor(BG_COLOR)`), so it can never be see-through. The **forced full screen hardware layer was removed** — the window stays hardware accelerated, without the extra screen sized buffer that fails on the affected devices. |
| `core/AppLogger.java` | One `SimpleDateFormat` **per thread** (`ThreadLocal`), plus `try/catch` around `log()` / `logE()` — logging can never take the app down. |
| `auth/SecurityVault.java` | **"native library unavailable" is now distinct from "tampered build"**. A device without the native library keeps the engine locked, but the account can still sign in. A real integrity failure (certificate mismatch, injected hooking framework) still blocks everything. |
| `auth/AuthManager.java` | The local password hash no longer trims the password, matching the backend (which stopped trimming it as well). |

### The new error screen
`BaseActivity.showFatalError()` builds a centred panel on the already attached background:

```
       Could not open the login screen
  The interface could not be created on this device.
  Please reload the app or reinstall the latest release.

                    [ RELOAD APP ]
```

---

## 4. Verification

* `javac` syntax check over all nine modified Java files: **no syntax errors** (the only
  reported errors are `cannot find symbol` / `package android does not exist`, because there
  is no `android.jar` in this environment).
* Manual brace/structure review of every changed method.
* **NOT VERIFIED:** no build, no install, no device run.

---

## 5. What is still needed

1. **Build the APK and smoke test it on a real device** — in particular on one of the
   affected Mali / Android 7-11 devices, and on a device that has **no root** (the native
   library path).
2. Check `files/logs/ninmods.log` on the device (the app writes its own crash log there) if
   a black screen is still reported — the new `try/catch` blocks now log the exact exception.
3. The CI build of this repository was **already failing before this change** with a native
   compile error (`Client.cpp: 'thickness' undeclared`). That was fixed upstream in commit
   `3d6acae`; verify the current `main` builds.
