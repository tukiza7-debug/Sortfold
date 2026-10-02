# Sortfold 1.0.1 — Fix and Polish Pass: 10 Mandatory Audits

All ten passes were executed in one phase, in order. For each: what was
checked, what was found, what was fixed, and how it was re-tested.

## Pass 1 — Code

Checked: root-cause fixes for both field bugs, swallowed exceptions, dead
code, meaningless comments, lint status, naming.

Found and fixed:
- `MediaScanner.scanInternal` called `DocumentsContract.getDocumentId()` on a
  tree URI — the exact crash on the device. Fixed to `getTreeDocumentId()`
  and to surface failures as a typed `ScanFailedException` with the original
  cause preserved. All other `DocumentsContract.getDocumentId()` calls (Mover,
  UndoManager) were audited: they operate on document URIs produced by
  `buildDocumentUriUsingTree()` or `createDocument()`, which is correct.
- `UpdateRepository.check()` collapsed every failure into `null`; the API
  client returned `null` for any non-2xx. Replaced with a typed
  `UpdateException` hierarchy (Offline, Timeout, NoRelease, RateLimited with
  `X-RateLimit-Reset`, Http, Malformed, NoApkAsset); nothing is swallowed.
- Dead strings `nav_errors` and `update_check_failed` removed from all five
  locales after the navigation restructure (lint flagged them).
- Repository hygiene: 4027 build artifacts under `app/build/` were committed
  in the initial push. Untracked, `.gitignore` fixed with a `build/` rule;
  the repo now contains sources and docs only.
- Lint: 0 errors (release + debug). Remaining warnings are version-pin noise
  and one documented battery-optimization warning, unchanged in scope.

Re-tested: full unit suite green (69 tests in release variant, 69 in debug),
`lintDebug` and release lintVital clean.

## Pass 2 — Scanner and sorting

Checked: the exact failing URI from the device, nested folders, empty
folders, spaces/unicode names, revoked permission, all seven sort modes,
preview, undo, duplicates.

Found and fixed:
- A failing test was written FIRST using the device URI
  `content://com.android.externalstorage.documents/tree/primary%3APictures`;
  it reproduced the exact error (`IllegalArgumentException: Invalid URI` at
  `DocumentsContract.getDocumentId`) before the fix, and passes after.
- The scanner/mover queries now use the Bundle-based `query()` (API 26+),
  which is what documents providers actually support on O+; the legacy
  5-argument path is a pre-O compatibility shim.
- Rename-move semantics were locked by a test: source removed, content under
  the numbered name (`photo (2).jpg`).
- New tests: exact device URI, nested (non-recursive) folders, empty folder,
  unicode/spacing names, SecurityException → typed PERMISSION_REVOKED,
  progress callback, ensureFolder creation+reuse, MOVE/SKIP/RENAME/REPLACE
  policies, copyBack restore, UndoManager end-to-end with Room and the
  provider (2 files restored, sorted copies removed).

Re-tested: `MediaScannerTest`, `MoverTest`, `UndoAndExportTest`,
`RuleEngineTest`, `ModeSuggesterTest`, `DuplicatePolicyTest` all green.

## Pass 3 — Permissions and safety

Checked: Android 10–16 matrix, denial/revocation paths, unsafe folders,
data-loss risks, rollback.

Findings (verified by code and tests only):
- Manifest declares `READ_MEDIA_IMAGES`/`VIDEO` (+`READ_MEDIA_VISUAL_USER_SELECTED`
  for 14+ partial access), `READ_EXTERNAL_STORAGE` capped at SDK 32,
  `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE(_DATA_SYNC)`, `INTERNET`,
  `ACCESS_NETWORK_STATE`, and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (only
  offered after a system kill of a long job, never proactively).
- Every rationale is shown in-app before the system dialog; denied-permission
  degradation is explicit (`perm_degraded_*`), never a blank screen.
- File safety: every move is create → copy → size-verify → delete-source;
  a failed step deletes the partial destination (Mover tests cover the
  rollback); undo copies back before removing sorted copies.
- Storage root / app-private / system folders are classified by
  `StorageSafety` and surfaced as warnings; insufficient storage blocks Apply.
- Not on-device testable here: real runtime denial on Android 14/16 and the
  photo-picker partial-access flow. Manual checks listed in the handoff.

## Pass 4 — Background work

Checked: process death, app kill, pause/resume/cancel, Doze, foreground
service type, notifications, low storage.

Findings (verified by code review): the job state (job + per-file move log)
lives in Room; `SortWorker` is a foreground `CoroutineWorker` with the
`dataSync` type declared for Android 14+; pause/cancel write the requested
state into Room and cancel WorkManager work; resume re-enqueues and continues
from the last completed file; completion notifications deep-link into the
result screen; the battery-exemption offer appears only after the system
killed a job. Errors inside the worker flow into the Error Library (module
`worker`/`updater` covered; 30-day cleanup worker verified by schedule code).
Not testable in this environment: actual Doze on a device. Manual check
listed in the handoff.

## Pass 5 — Updater and networking (release build)

Checked: every failure state, R8 effects, rate limit, 404, offline, asset
selection, version compare, release-variant tests.

Found and fixed:
- Real root cause of the device error: the manifest had NO `INTERNET`
  permission (also confirmed "Periksa ekstensi Anda" was a mistranslation —
  fixed to "Periksa koneksi internet Anda"). `ACCESS_NETWORK_STATE` added for
  the offline pre-check.
- Repo is injected via BuildConfig (`-PgithubRepo=owner/name` in CI); the
  build fails if blank. Defaults to the published repo locally.
- MockWebServer tests: newer/same version, draft→NoRelease, 404, 403 with
  `X-RateLimit-Reset` parsed, 429, malformed JSON, release without APK,
  transport failure → Timeout, connection refused → Offline, endpoint path
  and `Accept`/`User-Agent` headers asserted.
- APK asset selection prefers the device ABI and falls back to universal
  (`pickApkForDevice`, unit-tested via URL matching contract).
- `-dontobfuscate` + `-keepattributes SourceFile,LineNumberTable`: the
  release dex keeps readable `com/sortfold.app.*` names (verified by dexdump
  on the built APK); shrinking stays on, no mapping file needed.
- kotlinx.serialization uses compile-time generated serializers, so release
  parsing does not depend on reflection; release-variant unit tests run green.

Re-tested: 69 tests on the release variant, 0 failures.

## Pass 6 — Error Library and privacy

Checked: location in navigation, deep links, grouping, filters, retention,
masking, readable traces, ZIP export.

Found and fixed:
- Error Library moved from a bottom tab to a Settings sub-screen with a back
  arrow; deep links wired from the scan-failure state, update-failure state,
  and the crash banner; predictive back enabled app-wide
  (`enableOnBackInvokedCallback`).
- Identical errors (module + type + message) are grouped into one card with a
  `×N` count and last-occurrence time; the detail screen lists occurrences.
- Filter chips rebuilt: two labeled rows (Severity, Module) in horizontally
  scrollable LazyRows, fixed height, reserved check-icon space so selected
  chips never change size, resource labels only.
- Privacy hole fixed: messages and stack traces are now path-masked at
  display, copy and export time unless "Include full paths" is on — the
  `content://` URI from the device report now renders as
  `content://com.android.externalstorage.documents/...` (tests assert the
  masked and full-path exports).
- Entries carry `versionCode` + git build ID (Room migration 1→2 adds the
  columns without data loss); traces are readable after `-dontobfuscate`.

Re-tested: `UndoAndExportTest`, grouped-card logic, string parity tests.

## Pass 7 — Layout and insets

Checked: single inset application, no nesting, clipping, card consistency,
the test matrix.

Found and fixed:
- The double top inset came from the old shell: root Scaffold padding applied
  to screens that hosted their own TopAppBar. Restructured: the root shell
  hosts no Scaffold; each screen owns exactly one `Scaffold(topBar,
  content, bottomBar)` and applies its padding once; compact width passes the
  bottom bar into the screen's own Scaffold; medium/expanded use a rail.
- Lists use `contentPadding` (insets + 16dp gutter) so the last item scrolls
  fully into view; fixed heights that clip were removed.
- Design tokens: one spacing scale (`Spacing`), one type scale, one shape
  scale, single theme file with light+dark tokens; consistent card style.
- New reusable helper (`LayoutAssertions`) walks the semantics tree and
  asserts no sibling interactive overlap and nothing outside window bounds;
  matrix tests run Home and Settings at 2× font in Indonesian, Arabic (RTL),
  Malay (1.5×) and Chinese (2×) — all green.

On-device matrix (320–840dp, gesture vs 3-button nav, dark theme) is
"verified by code and tests only" — manual spot checks in the handoff list.

## Pass 8 — Animation and motion

Checked: tokens, transitions, list item animation, layout shift, reduced
motion, main-thread jank risks.

Findings: one motion file (`Motion.kt`: press 100ms, small 200ms,
enter 300ms / exit 225ms, emphasized decelerate/accelerate); shared-axis X
for Settings sub-screens and wizard steps (RTL-mirrored), fade-through
between tabs; `animateItem()` on Home, History and Error Library lists;
filter chips reserve their check-icon space so toggling never changes size;
the logo loader appears only after 400ms and stays ≥300ms; reduced-motion
replaces transitions with near-instant fades; lists use stable keys; all
file work runs off the main thread.

## Pass 9 — Localization and accessibility

Found and fixed:
- values-in was rebuilt natively; every Malay word reported by the user
  (Ralat, Pemindah, tersenarai, urungkan, daripada, muat turun, kekal,
  sejarah, lalai, eksport, laluan, dihantar, bateri, …) is gone, and the
  connection mistranslation is fixed.
- New `TranslationQualityTest`: fails the build if any Malay word reappears
  in values-in, if any locale leaves a translatable string identical to
  English (language-neutral values are an explicit allowlist), and if
  severity/module labels regress to raw English.
- Placeholder parity enforced across all five locales (existing test).
- TalkBack: icons have content descriptions, error states use a polite live
  region, touch targets are ≥48dp (M3 minimum-interactive enforcement on
  chips; ListItems/buttons by spec), contrast uses M3 light/dark schemes.
- 200% font: covered by the matrix tests at the semantics level; visual
  confirmation on the device is in the manual list.

## Pass 10 — Release and CI

Checked: signing, version bump, assets, in-app update detection, README.

Findings and actions:
- Local release build signed with the rotated keystore (apksigner verified,
  SHA-256 `4a889c…d8cd4`); universal + 3 per-ABI APKs + AAB produced.
- Version bumped to 1.0.1; CI derives `versionName`/`versionCode` from the
  tag and now injects `GITHUB_REPO` (`-PgithubRepo=${{ github.repository }}`).
- The release must be published (not draft) so `/releases/latest` returns it;
  the release workflow fails on unmatched artifact globs, and CI re-runs the
  full test suite before publishing.
- README updated with the new Settings structure; no screenshots; logo and
  wordmark are local SVGs under `docs/`.
- In-app update detection 1.0.0 → 1.0.1 is verified against the real
  published release via the unauthenticated endpoint + `VersionCompare`
  (unit-tested): 1.0.1 > 1.0.0 → update available.

## Not verifiable in this environment

- Actual on-device rendering: overlap/clip-free layout is enforced by the
  semantics-tree assertions and Scaffold contract, but a human should
  confirm the visual result (see the manual checklist in the release
  handoff).
- Real Doze/app-standby behavior, real SAF picker flows on Android 14/16,
  and install-over-the-top of 1.0.0 (signature continuity was verified by
  comparing keystore certificates, not by installing).
