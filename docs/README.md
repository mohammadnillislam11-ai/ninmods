# NINMODS Documentation Index (`docs/README.md`)

Welcome to the central documentation directory for the NINMODS project. All AI agents, contributors, and developers must read and strictly follow the documents located here before modifying any code.

---

## Documentation Structure

1. **[`docs/RULES.md`](file:///c:/Users/Sajala/Documents/mods/docs/RULES.md)**:
   - Mandatory engineering standards and non-negotiable rules.
   - Javadoc/Doxygen comment style specification (prohibition of ASCII banner comments).
   - Mandatory Package & Import Header Audit Rule (verifying `package` declaration and all `import` statements).
   - Mandatory end-to-end holistic code review for all AI agents.
   - Kernel driver only policy for `ninmods` and internal mode for `ninlite`.
   - Single central offsets architecture (`gameoffsets.h`).
   - Zero local builds and strict GitHub Actions CI/CD rules.
   - User explicit confirmation required before any `git push`.

2. **[`docs/MEMORY.md`](file:///c:/Users/Sajala/Documents/mods/docs/MEMORY.md)**:
   - Low-level Linux/Android memory reading architecture.
   - Ring-0 Character Device IOCTL communication (`/dev/BYNb`, `IOCTL_RT_READ 0x801`, `IOCTL_RT_WRITE 0x802`, `IOCTL_RT_BASE 0x803`).
   - `COPY_MEMORY` and `MODULE_BASE` struct specifications.
   - `ninlite` internal mode using `process_vm_readv` Linux syscall.
   - SELinux configurations and node permission handling.

3. **[`docs/GEMINI.md`](file:///c:/Users/Sajala/Documents/mods/docs/GEMINI.md)**:
   - Directives and operating guidelines for Gemini and Antigravity AI agents.
   - Deep investigation workflows, system impact mapping, import verification, and verification procedures.
