# Sortfold 1.0.0 — Six-Pass Audit Report

All six passes ran inside the single build phase, in order. Each pass lists
what was checked, what was found, what was fixed, and the re-test result.
Everything below was verified by building, running unit tests, lint, and
signing locally; items that cannot be verified without a physical device are
listed in "Still unverified" and in the final manual checklist.

---

## Pass 1 — Code audit

**Checked:** dead code, duplication, naming, architecture boundaries, tests, lint, compiler warnings.

**Found and fixed:**
- Dead code removed: `MediaFile.pathForSourceDetection` (superseded by tree-label based source detection), unused `ConfirmDialog` composable, unused `SortJobDao.lastJob`, `AutoRuleDao.byId`, `ErrorDao.count`, unused `windowSizeClass` property on `MainActivity`, unused imports (`FilterChip`, `Box`, `ErrorExporter`), a `movedCount` counter that was incremented but never read, and a leftover `stopStateLabel` writing a transient status string to Room.
- JVM signature clashes: two `var ... private set` properties collided with public setter-shaped functions (`setMediaReadDenied`, `setJobId`, `setGranularity`, `setPolicy`). Renamed the functions (`onMediaPermissionsResult`, `updateJobId`, `chooseGranularity`, `choosePolicy`).
- A duplicated `consumePendingDefaultTree` introduced during editing was removed.
- Lint: `ProduceStateDoesNotAssignValue` error replaced with `remember` + `LaunchedEffect`; unused-quantity plural items removed for `ms`/`in`; an always-true `SDK_INT >= 29` branch removed; unused colour removed.
- Deliberate suppression: `ObsoleteSdkInt` is ignored only for `res/mipmap-anydpi-v26` with a documented reason — AGP's resource merger silently drops unversioned `mipmap-anydpi` adaptive-icon XML, so the icons must stay in the `-v26` folder (which always matches at minSdk 29 anyway).

**Re-test:** `testDebugUnitTest` 36/36 pass, `lintDebug` 0 errors, `assembleDebug` green. Remaining lint warnings are 51 + 3 version-pin notices (`GradleDependency`/`AndroidGradlePluginVersion` — deliberate, reproducible builds), 8 `PluralsCandidate` style suggestions, 2 informational.

**Architecture:** single `:app` module with clean package boundaries — `core.model`, `core.rules` (pure Kotlin, unit-testable), `core.scanner`, `core.mover`, `core.history`, `data.db`, `data.prefs`, `data.repo`, `work`, `error`, `ui.*`. Manual DI container (`AppContainer`) — explicit, no framework magic.

---

## Pass 2 — Functional audit

**Checked:** all 7 sort modes, preview, undo, duplicates, wizard flow, edge cases, every settings item, language switching.

**Found and fixed (this pass caught real gaps):**
- Several settings were stored but not wired (the spec forbids dummy toggles). Now wired and covered by code + tests:
  - Default sort mode, default duplicate policy, default date granularity → applied by `WizardViewModel` init from DataStore.
  - Default destination folder → preselected and scanned in step 1 via `pendingDefaultTree`.
  - Naming style → new `SortConfig.dateStyleIso` flows into `RuleEngine.dateSegment` ("2024-05" vs "November 2023"), covered by two new unit tests.
  - Confirm-before-apply toggle → the wizard now skips the confirm dialog when the user turned it off.
  - Notifications toggle → `SortWorker.notifyProgress` checks the setting before showing progress.
  - Battery exemption → previously written but never surfaced; now Home shows a card *only* when `jobKilledBySystem` was set by the worker, offering the system dialog or dismissing.
- Unreachable warning removed: the cross-storage warning can never fire because the destination is always inside the granted source tree by design; the string was removed from all five locales (the parity test enforces this).
- Edge cases: empty folder (blocked with empty state), same-name files (skip/rename/replace, unit tested), unreadable files (per-file failure recorded in the move log, job ends PARTIAL), huge folders (preview capped at 100 visible rows + counter; apply iterates Room rows, memory-safe at 10k+), metadata read failures fall back to file dates / SD bucket.
- Language switch: `AppCompatDelegate.setApplicationLocales` applies instantly to all activities and dialogs; `autoStoreLocales` service persists on Android 12 and below, system per-app language on 13+; DataStore mirrors the choice and is re-applied on cold start if empty.

**Re-test:** 36/36 unit tests pass, including all 7 modes, combined-mode nesting, duplicate policies, suggestion heuristics, and 5-locale string/placeholder parity.

---

## Pass 3 — Permission and safety audit

**Checked:** every permission path on Android 10-15, denial/permanent-denial/revocation, data-loss risk, unsafe targets, rollback.

**Verified by code review + unit tests:**
- SAF folder access needs no runtime permission on any version; the media-read matrix requests `READ_MEDIA_IMAGES/VIDEO` (+ `READ_MEDIA_VISUAL_USER_SELECTED` on 14+) on 33+, `READ_EXTERNAL_STORAGE` on 32 and below.
- In-app rationale is shown *before* each system dialog, including a "continue without" path (SAF still works, dates/resolutions degrade with an explanation card).
- Permanent denial: the media step shows a degraded-mode card with a shortcut to app settings; notifications denial only removes the progress notification (worker catches `SecurityException` on every notify).
- Notification permission is requested before the first background job only — never at launch, never all at once.
- Battery optimisation exemption is declared so it *can* be offered, but is only ever surfaced reactively after the system stops a long job.
- Data safety: every move is create-destination → stream copy → size verify → delete source; a failed step deletes the partial copy and the source stays untouched. Replace deletes the destination copy only after an explicit warning, and the original source file is never touched first.
- Unsafe destinations: tree classification blocks `Android/data`, `Android/obb` and system trees (hard block) and warns on storage roots (unit tested via `classifyTreeId`).
- Storage: Apply is blocked if free space < plan size + margin; warned when free space < 2x plan size.

---

## Pass 4 — Background audit

**Checked:** process death, app kill mid-job, pause/resume/cancel, Doze, foreground service type, notifications, low storage, error-library integration, retention, export.

**Verified by code review:**
- Planned moves live in Room; `SortWorker` (CoroutineWorker, long-running, `dataSync` foreground type declared for the WorkManager service and manifest) persists progress after every file. Process death, Doze stop or app kill land in `finalize` → job PAUSED (and `jobKilledBySystem` set for the battery offer); a fresh worker (Resume button in Result screen / History) continues from the first PLANNED row.
- Pause and Cancel both go through the notification receiver or the wizard: requestStop → `cancelUniqueWork` → worker finishes the current file, never leaves a half-moved file; cancel keeps moved files and offers Undo.
- Completion notifications fire only for DONE / PARTIAL / FAILED and deep-link to the result screen via `MainActivity.pendingJobId`.
- Any mover failure is logged to the Error Library with the job id; the daily `CleanupWorker` deletes errors after 30 days and undo history per the user's 1/7/30-day setting.
- Android 15 note: `dataSync` foreground service type has a 6-hour-per-24h budget on targetSdk 35; for typical media batches this is plenty, and if the system stops the job the resume path recovers it. This is the honest platform constraint.

**Not runnable in this sandbox:** the exported ZIP was code-reviewed (report.txt + errors.json + moves.csv via FileProvider share sheet) but not generated on a live device.

---

## Pass 5 — UI/UX, responsive, logo and animation audit (code-based)

**Checked:** WindowSizeClass breakpoints, rotation/split-screen/foldable/font-scale behaviour, insets, spacing, hierarchy, dark mode, accessibility, motion, logo, AI-slop signals. No screenshots exist; nothing visual is claimed as rendered.

- Breakpoints: compact = bottom navigation, medium = navigation rail, expanded = rail + two-pane wizard (options left, live preview right) + 720dp max content width on Home.
- State: wizard state lives in the ViewModel; `rememberSaveable` for step-local UI state; running jobs are untouched by recreation because they live in WorkManager + Room.
- Insets & input: edge-to-edge enabled, Scaffold-managed insets, `adjustResize`, scrollable screens everywhere, 48dp+ interactive targets (M3 minimum-touch-target enforcement plus explicit heights).
- Accessibility: contentDescription on every icon action, loader and progress; TalkBack-friendly labels on nav items; palette checked for AA contrast (primary teal on light surface ≈ 7.4:1, dark-theme teal on dark ≈ 8:1).
- Motion: fade-through 220/150ms for navigation, standard easing; `LocalReducedMotion` (app toggle OR system animator-scale = 0) switches transitions and the sorted-bars loader to static forms.
- Logo: reviewed at vector level — two flat colours, chunky geometric folder + three sorted bars, single-path even-odd monochrome layer, legible at 24dp because the bars are 3.5/108 of the canvas (≈ 0.8dp at 24dp, still visible).
- AI-slop check: no gradients, no glassmorphism, no emoji icons, no repeated identical card grids, no lorem ipsum — all strings are real product copy in five languages.

---

## Pass 6 — Release audit

**Checked:** signing, version bump, assets, update check, README, clean build.

- Release workflow (`.github/workflows/release.yml`): tag `v*` → JDK 17 → tests → decode `KEYSTORE_BASE64` secret → `assembleRelease` + `bundleRelease` with `-PversionName`/`-PversionCode` from the tag and run number → renames to `Sortfold-v{version}-{abi}.apk` + universal + AAB → generates 3-5-line notes from git log → publishes the GitHub Release.
- Signing verified locally: `apksigner verify` shows the Sortfold certificate; v1+v2 schemes enabled; keystore and credentials delivered separately (never committed).
- CI on `main`: tests + lint + debug build — green.
- In-app update check points at `tukiza7-debug/Sortfold` `releases/latest` and compares semver (unit tested); download via system DownloadManager; never force-updates.
- README: logo/wordmark resolve inside the repo (`docs/*.svg`), badge row, sort-mode table, collapsible install, 5-step how-it-works, languages table, no screenshots or placeholders.

---

## Still unverified (needs a real device)

1. **Nothing visual is verified.** All UI statements are verified by code, previews of parameters and tests only. On a device, check: wizard step transitions, two-pane layout on a tablet/foldable, dark theme, dynamic colour, splash hand-off, notification actions, RTL layout in Arabic, 200% font scale, landscape on a small phone.
2. SAF end-to-end on real providers (move across folders in the same tree, `renameDocument` quirks of vendor file managers).
3. Foreground-service start under aggressive OEM battery managers (Xiaomi/Huawei variants) — resume path exists, but real-device behaviour should be confirmed.
4. The exported ZIP opening in a real unzip tool and the share sheet hand-off.
5. Android 15 `dataSync` 6-hour budget behaviour on very large jobs.
6. Clean install + upgrade path from a published release (no prior release exists to upgrade from; v1.0.1+ will exercise it).
