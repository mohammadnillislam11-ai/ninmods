# Agent Handoff, Brain Registry & System Memory Protocol

This document serves as the master registry for AI agents working on this workspace (`c:\Users\Sajala\Documents\mods`). It explains where the agent brain, conversation history, and telemetry are stored, summarizes the work completed across versions, and guides any incoming agent on how to seamlessly continue collaboration without repeating work or breaking rules.

---

## 1. Agent System Architecture & Storage Locations

Every interaction, prompt, tool execution, and artifact is recorded by the system. Incoming agents must use the following paths:

### 1.1 App Data & Configuration Roots
- **Global Customizations Root**: `C:\Users\Sajala\.gemini\config`
- **IDE App Data Root**: `C:\Users\Sajala\.gemini\antigravity-ide`
- **Workspace Customizations Root**: `.agents/` (relative to workspace root)
- **Primary Workspace Root**: `c:\Users\Sajala\Documents\mods`
- **Main Git Repository**: `c:\Users\Sajala\Documents\mods\nin` (branch `main`)

### 1.2 Conversation Brain & Artifact Directory
- **Active Conversation ID**: `958e1156-b06e-448e-9f11-af7441c7d758`
- **Active Brain Directory**:
  `C:\Users\Sajala\.gemini\antigravity-ide\brain\958e1156-b06e-448e-9f11-af7441c7d758\`
- **Scratch / Temporary Files**:
  `C:\Users\Sajala\.gemini\antigravity-ide\brain\958e1156-b06e-448e-9f11-af7441c7d758\scratch\`
  *(Note: Must be kept clean. Temporary analysis scripts should be purged after use).*

### 1.3 Message Logs & Complete Transcripts
Host-managed conversation history and task logs are controlled by the runtime. Project agents must not read hidden transcripts, brain directories, credentials, telemetry, or system-generated logs from disk. Use only the conversation context and host-provided task/session tools that are explicitly available for the current task.

### 1.4 How an Incoming Agent Reads Past Context
An incoming agent should use the current conversation, explicit user-provided files, and host-provided session/task context. Do not inspect `.system_generated` logs, hidden transcripts, IDE brain directories, or other agents' private data via PowerShell or filesystem tools.

---

## 2. Mandatory Rules for Any Agent (From AGENTS.md)

> **Precedence and conflict resolution:** This registry is project-level guidance only. System/developer instructions, platform safety/privacy limits, and the current user's explicit request always take precedence. Nothing here authorizes hidden transcript or credential access, external delivery, downloads, builds, pushes, deletion, or other irreversible actions. On conflict, take the lower-risk path, report the conflict, and do not guess.

1. **Rule 1 (Zero Fake / Dummy Code)**: No fake offsets, mock statuses, or simulated drawing. Everything must be genuine at Linux / Android kernel level.
2. **Rule 2 (100% Manual Control)**: No hacks or drivers turn on automatically. Only user switches toggle memory operations.
3. **Rule 3 (Strict Driver Isolation)**: RT (Ring 0 IOCTL) and QX (IPC daemon) are isolated. Zero auto-switching or fallback between channels.
4. **Rule 9 (Single Central Offsets File)**: All offsets are in `app/src/main/jni/src/Includes/gameoffsets.h`. Never duplicate elsewhere.
5. **Rule 11 (Permanent 90.X Auto-Incrementing Versioning)**: Major version 90 is fixed for life (`V90.1` -> `V90.2`...). Zero `.idsig` files. Clean older APKs.
6. **Rule 12 (Cloud-First Builds)**: Zero local builds. 100% GitHub Actions cloud CI/CD.
7. **Rule 14 (Strict Zero Push Without Explicit User Confirmation)**: Never run `git push` unless the user explicitly says "পুশ করো".
8. **Rule 16 (Strict Download Folder Logging)**: Diagnostics and logs must save to `/storage/emulated/0/Download/ninmods.log`.
9. **Rule 17 (100% Kernel Driver ONLY)**: Genuine `/dev/BYNb` IOCTL `0x801/0x802/0x803`. Zero fallback to `process_vm_readv` or `/proc/<pid>/mem`.
10. **Rule 19 (Clean Rewrite Under 300 Lines)**: Rewrite entire files if under 300 lines to avoid partial snippet syntax errors.
11. **Rule 20 (Javadoc/Doxygen Only)**: Standard documentation tags only. Zero ASCII banner lines (`// ===` or `// ---`).

---

## 3. Work Completed & Active Status (As of V90.94)

### 3.1 Massive Repository Cleanup (133,651 Lines Removed)
- Purged dead single-header libraries: `Includes/json.hpp` (24,765 lines) and `Includes/httplib.h` (24,139 lines).
- Purged unreferenced font arrays: `Icon/OPPOSans-H.h` (46,252 lines), `imgui/Verdana.h` (12,761 lines), `imgui/fonts/FontAwesome6_solid.h` (4,883 lines), `ShantellSans.h` (4,630 lines), `GoogleSans.h` (1,616 lines), `IconsFontAwesome6.h` (1,401 lines), `FontAwesome6_regular.h` (773 lines).
- Purged unreferenced upstream demo: `imgui/imgui_demo.cpp` (11,552 lines).
- Purged all temporary test scripts from the scratch directory.

### 3.2 Floating Menu & UI Refactoring
- Removed legacy mods (`bull`, `cross`, `delta`) leaving only `NINMODS` (Kernel Mode) and `NINLITE` (Internal Mode).
- Fixed floating logo tap-expand trigger in `MenuController.java` using direct expand/collapse state.
- Made engine mode and game title 100% dynamic via `NativeBridge.getDetectedGameTitle()` and `NativeBridge.getConnectionMode()`.
- Implemented `launchActiveMenu(Context)` in `MenuRouter.java`.
- Deduplicated `release()` in `NinLiteFloatingMenuView.java` and added parent attachment guards in `ModMenu.java`.

### 3.3 Core Machine, JNI & Driver Integrity
- `Includes/MemoryIO.h`: Ring-0 IOCTL commands `0x801` (Read), `0x802` (Write), `0x803` (Base) operate on `/dev/BYNb`. Removed silent fallback to `process_vm_readv`.
- `Detection/GameDetection.cpp`: Scans `/proc/[pid]/cmdline` dynamically and binds `target_pid`.
- `Bridge/SecurityVaultJNI.cpp`: Validates APK signing certificate against release key with `DigestEquals`.
- `core/AppLogger.java`: Refactored to write diagnostics directly to `/storage/emulated/0/Download/ninmods.log` per Rule 16.
- `version.xml`: Bumped to version `90.94` (version code `994`).
- **100% Verified JNI Bindings**: Exhaustive cross-audit of all 41 C++ `Java_` exports and 7 `RegisterNatives` methods against all Java native methods. Zero unbound exports, zero missing methods, zero wrong registry/classes.
- **Strict RT/QX Aim Worker Decoupling**: `ContinuousAimWorker` and `UpdateAimMagnetFast` explicitly check `NinChannel::IsQxActive()` and yield without touching memory in QX mode.
- **Daemon Socket Recovery**: `NinLiteSocketServer.h` closes listening fd on bind/listen failure to enable clean retry, and preserves listener on accept error.
- **Multi-ABI Daemon Packaging**: GitHub Actions workflow builds and packages `libninlite_daemon.so` for both `arm64-v8a` and `x86_64`.

---

## 4. Current State & Next Steps for Incoming Agent

- **Local Git State**: Verify with `git status` and `git log`; this registry is informational and may become stale after another agent's commit.
- **Immediate Pending Step**: Do not push, build, download artifacts, delete files, or deliver messages unless the current user explicitly requests that exact action and all higher-priority instructions allow it.
- **When the user explicitly authorizes a push/build**:
  1. Confirm the exact repository, branch, workflow and artifact scope first.
  2. Push/build only through the authorized path; never run a local build when the cloud-build rule applies.
  3. Do not download APKs/logs automatically; ask separately unless the user explicitly requests artifact download.
  4. Do not delete older artifacts or `.idsig` files without an explicit, recoverable cleanup instruction.
  5. Report the actual receipt/status; never infer success from a workflow submission alone.
