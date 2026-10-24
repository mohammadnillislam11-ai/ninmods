# App UI and routing review

## Changes made

- Internal screens were declared `exported="true"`, allowing other installed
  apps to launch dashboard, authentication, profile, device-info, and mode
  selection activities directly. These activities are now private to this app.
  The launcher `MainActivity` remains exported; it is the only activity with the
  launcher intent filter.
- `DashboardActivity.onResume()` rebuilds its entire body. Returning from profile,
  device settings, or another screen therefore discarded the user's scroll
  position. The dashboard now restores the prior scroll offset after the
  refreshed body is laid out.
- Removed mandatory notification and location permission prompts because no
  notification or device-location feature uses them. Their manifest permissions
  were removed as well.
- Made storage access optional during onboarding. The app explains that access
  is for diagnostic-log export and lets users continue without granting it.
- Added the Free Fire India package to Android 11+ package visibility queries so
  the dashboard can correctly detect that supported install.
- Device-status probing now runs off the UI thread, ignores stale responses, and
  reports a visible failure instead of blocking the screen.
- Profile and device-information rows now constrain long values to their column
  and ellipsize safely. Root information now reports that an `su` binary was
  detected rather than claiming root access was verified.
- Header controls now meet a 48 dp touch target and have screen-reader labels;
  long sidebar account text is truncated instead of overflowing.

## Follow-up observations

- `ModeSelectActivity` immediately routes to the dashboard from `onCreate()`;
  its selector UI is unreachable. It remains private and unchanged because
  restoring mode selection would change the app's flow and native mode behavior.
- The dashboard has a login check, and logout clears the task before returning to
  login. No other route to the dashboard was found outside those app-owned
  navigation paths.

## Scope

This review covers standard Android screen layout, onboarding permissions,
accessibility, activity routing, and UI lifecycle. It does not modify driver,
native memory, or game-control behavior.

## Validation

- Manifest XML parsing, exported-activity assertions, and `git diff --check`
  passed.
- A full Android/NDK build has not been run in this environment. Avoid dispatching
  the current release workflow as validation: it deletes existing GitHub
  Releases before publishing a new one.
