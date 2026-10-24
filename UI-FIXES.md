# UI fixes

## Changes made

- Changed the login, signup, password reset, and profile screens from `adjustPan`
  to `adjustResize`. Their scrollable forms can now resize when the keyboard opens,
  so focused fields and the remaining form controls can be scrolled into view
  instead of the keyboard panning over them.
- Increased the shared password visibility button from 40 dp to 48 dp and added
  focusability, click semantics, and an accessibility description that changes
  between "Show password" and "Hide password".

## Scope

These changes are limited to standard Android screen layout and accessibility.
They do not change authentication, driver, native, or game-control behavior.
The separate `ModeSelectActivity` currently routes straight to the dashboard
instead of displaying its selector; it was left unchanged because restoring that
screen would change the app's mode-selection and launch flow.

## Validation

- XML manifest parsing and `git diff --check` passed.
- A full Android build still needs the project's configured Android SDK/NDK
  toolchain; the repository's GitHub Actions workflow is the intended build path.
- Additional screen-layout, onboarding, accessibility, and routing fixes are
  recorded in [APP-ROUTING-REVIEW.md](./APP-ROUTING-REVIEW.md).
