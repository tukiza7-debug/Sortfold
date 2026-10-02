# Sortfold 1.1.0 — What Changed

A major overhaul: refreshed toolchain, a professional-pass over the whole
interface, 27 fixed bugs (several serious), and the same calm Sortfold —
teal accent, preview first, undo always.

## New & improved

- **Wizard** — a visual 1-2-3 stepper (Folder / Sort / Preview) with a
  header that glides between steps; clearer warnings; a light confirmation
  tick when you apply.
- **Home** — hero numbers (files sorted, recent operations), skeleton
  shimmer while loading, and a live card with animated progress while a sort
  is running.
- **Job results** — an animated outcome icon, collapsible Skipped/Failed
  lists with reasons, and undo that now tells you exactly what it restored.
- **History** — live search across operations, smoother entry animations.
- **Onboarding** — swipeable pages with an animated indicator.
- **Error Library** — export moved to an extended FAB with confirmation;
  grouped error cards and masked paths as before.
- **System** — reduced-motion now also stills the new springs and shimmer;
  light haptics on apply; predictive back and edge-to-edge as before.

## Fixed

Serious:
- REPLACE no longer deletes your existing file before the new copy is
  verified — a failed copy can never destroy data.
- A corrupted settings file can no longer break the app; it falls back to
  defaults.
- Two different errors arriving in the same millisecond no longer crash the
  Error Library list.
- A rare wizard start-up race could kill its initialisation silently —
  fixed.

Also fixed (selected):
- Undo now works for files picked straight from a storage root, and no
  longer gets stuck if the process dies mid-undo.
- Moving a file into the folder it already lives in no longer creates a
  "photo (1).jpg" duplicate.
- Renames are scoped per destination folder (no more needless
  "file (2).jpg" when destinations differ).
- Turning automatic update checks off actually stops the daily check; update
  notices now use the dedicated notification channel; any unexpected check
  failure is reported instead of crashing silently.
- The Android top-level folder is now treated as app-private (blocked from
  sorting into).
- Error export ZIPs are timestamped to the millisecond and escape file names
  properly in the CSV.
- The data-size row in Settings no longer walks your folders on the main
  thread.
- Progress announcements and the name-rule buttons are localized in all five
  languages.

## Under the hood

- Upgraded: AGP 8.13.0, Kotlin 2.2.20, Compose BOM 2025.09.01, Room 2.8.3,
  WorkManager 2.10.3, OkHttp 5.1.0, Gradle 8.14.3 — compiled against
  Android 16 (API 36).
- CI refreshed (checkout/setup-java/gradle actions v5) and the release
  pipeline adapted for AGP 8.13's bundle-vs-APK separation.
- 25 new regression tests (69 → 94), every bug fixed red-first.
