# NINMODS — AI Agent Directives & Operating Protocol (`docs/GEMINI.md`)

This document governs the behavior, investigation procedures, and implementation standards for all Gemini / Antigravity AI agents in this workspace.

---

## 1. Deep Investigation & Holistic Code Review Before Any Edit

1. **Never Start Editing Immediately:**
   - Every AI agent MUST understand the complete system lifecycle before modifying any code.
   - Inspect existing architecture, manifests, configuration, and interfaces.

2. **Mandatory Full Code Review (Holistic Audit):**
   - Trace the complete execution path:
     `UI Entry Point` ➔ `MenuController` ➔ `JNI Bridge` ➔ `C++ Engine` ➔ `Kernel Driver` ➔ `ESP/Aimbot` ➔ `Render Overlay`
   - Review all callers and callees. Ensure changes do not break adjacent brand UIs or native hooks.

---

## 2. Mandatory Package & Import Header Verification

* Whenever editing or reviewing any file, the agent **MUST** inspect the file header:
  - Verify that the `package` declaration is present and exact (e.g., `package com.ninmods.main.menu;`).
  - Verify all necessary `import` statements are present (e.g. `import android.graphics.drawable.GradientDrawable;`, `import android.widget.Switch;`, etc.).
  - Remove broken, stale, or unused imports.
  - Never allow compilation errors due to missing imports or package declarations.

---

## 3. Mandatory Code Commenting Standard

* **Zero ASCII Banners:**
  Strictly do not write comment banners such as:
  `// =========================================================================`
  `// -------------------------------------------------------------------------`
* **Javadoc / Doxygen Style Required:**
  Always document functions, methods, and classes using standard Javadoc / Doxygen format with `@param`, `@return`, and `@throws` tags.

---

## 4. Strict Operating Guardrails

1. **Zero Push Without Explicit User Command:**
   - Never run `git push` or trigger cloud builds autonomously.
   - Wait for the user to explicitly type or say "পুশ করো".

2. **Zero Local Machine Compilation:**
   - Never run Gradle or NDK compilers locally. 100% of APK compilation occurs in GitHub Actions.

3. **Single Central Offsets File:**
   - Offsets exist exclusively in `Includes/gameoffsets.h`. Zero duplication in Kotlin or Java.

4. **Single Kotlin UI Overlay:**
   - All memory calculations and WorldToScreen projections run 100% in C++. Kotlin only has `EspOverlayService.kt` for canvas rendering.

5. **Files Under 300 Lines:**
   - Files under 300 lines must be rewritten completely and cleanly rather than edited in partial snippets.
