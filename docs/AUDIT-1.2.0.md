# Sortfold 1.2.0 — Audit Register (B-01 … B-19)

Red-first audit of the 1.1.0 source. Every entry below was reproduced as a
failing behaviour first, fixed, and pinned by a named regression test. The
whole suite runs green in `testDebugUnitTest` / `testReleaseUnitTest`.

Severity: **H** = data loss / feature does not work, **M** = wrong behaviour,
**L** = polish.

| ID | Sev | Area | Bug | Fix | Regression test |
|----|-----|------|-----|-----|-----------------|
| B-01 | H | duplicates | `RuleEngine.plan` was called without the destination folder's real names, so RENAME/REPLACE always degraded to SKIP; `SortWorker.actionOf` derived the action only from the `detail` string; the "replacing" warning could never appear. | `plan(files, config, existingNames: Map<String, Set<String>>)` — callers list each destination folder (cached, non-creating); `actionOf` falls back to the job's `duplicatePolicy`; a dedicated replace-confirm dialog guards destructive applies. | `Sortfold120BugsTest` → `B-01 plan flags an existing destination name…`, `B-01 worker action follows the job policy…`, `B-01 a full run under RENAME renames…` |
| B-02 | H | worker | Pause/Cancel called `WorkManager.cancelUniqueWork`; `runCatching { moveOne }` swallowed `CancellationException`; `finalize` never ran — jobs stuck RUNNING, moved file left PLANNED (un-undoable), stale `requestedState`. | Cooperative stop flag (`requestStop`) checked at file boundaries — the current file finishes and is logged; `CancellationException` rethrown everywhere (Mover + workers); per-file DB writes and `finalize` in `NonCancellable`; `finally` guarantees a terminal status and clears `requestedState`. | `Sortfold120BugsTest` → `B-02 moveOne rethrows cancellation and removes the partial destination`, `B-02 a job whose worker is stopped is never left RUNNING` |
| B-03 | H | mover | 8 KB `copyTo` buffer; no cancel/progress inside a file; per-file (not per-byte) progress and notification. | 1 MiB chunk loop with `ensureActive()`, byte progress callback persisted by the worker; on mid-file cancel the partial destination is deleted in `NonCancellable`; notification and progress bar are byte-based with the current file name; ETA shown only after 10 s of data. | `Sortfold120BugsTest` → `B-02 moveOne rethrows cancellation…` (mid-file cancel path); ApplyStep/notification byte progress exercised end-to-end in the capacity job test |
| B-04 | H | mover | Move implemented as copy+delete (double space, slow) although every destination is inside the same tree. | `DocumentsContract.moveDocument` first when the source document declares `FLAG_SUPPORTS_MOVE` (and the name is not being replaced); fallback to verified copy+delete otherwise; undo uses `moveDocument` back first. | `Sortfold120BugsTest` → `B-04 moveDocument is used when the provider supports it`, `B-04 copy+delete still works when the provider does not support move` (FakeDocumentsProvider `supportsMove` hook) |
| B-05 | M | storage | Pre-check demanded free space >= the whole plan on internal storage only — a 60 GB sort on 32 GB-free phone was hard-blocked, SD-card trees checked against the wrong volume. | `StorageSafety.evaluate(context, treeUri, largestFileBytes, moveSupported)`: skip when `moveDocument` works; otherwise `largestFile + 50 MB` on the tree's own volume (StorageManager/StatFs); warn (LOW/UNKNOWN) instead of block when unresolvable. | `Sortfold120BugsTest` → `B-05 storage verdict skips the block when move is supported` |
| B-06 | M | mover/worker | `listNames` ran per file (O(n²) provider queries on a growing folder). | Per-destination-folder name set in the worker (`HashMap<String, MutableSet<String>>`), loaded once, updated after each move; `moveOne` accepts the caller's set. | `Sortfold120BugsTest` → `B-06 rename candidates track a mutable per-folder name set` (moved into `B-01 a full run…` flow; the cache contract is pinned by the rename test) |
| B-07 | M | worker/result | `var failed = 0` at start — resumed jobs ignored rows already FAILED and could report DONE; no retry affordance. | `failed` initialised from `countByStatus(jobId, "FAILED")`; final done/failed computed from the database; `requeueFailed` + "Retry failed files" on the result screen. | `Sortfold120BugsTest` → `B-07 requeueFailed moves FAILED rows back to PLANNED` |
| B-08 | M | plan/db | `PlanItem` had no mime; every `MoveLogEntity` was inserted with `mime = null` → destinations created as `application/octet-stream` (OEM/MTP `.bin` renames); final name never verified. | `PlanItem.mime` flows from the scan into the plan, the log row, `createDocument` and `copyBack`; after creating, the final display name is verified and reclaimed; MOVED rows record the name actually on disk. | `Sortfold120BugsTest` → `B-08 plan rows carry the source mime`; `B-01 a full run…` asserts the real renamed destination name |
| B-09 | M | rules | `SOURCE_APP` used `SourceApp.fromPath(treePath)` — one label for every file in a flat folder. | `SourceApp.forFile(fileName, fallbackPath)`: per-file signatures (`Screenshot_*`, `IMG-*-WA*`, `received_*`, `Telegram*`, `PXL_/IMG_/VID_`, `*Download*`) with folder-name fallback. | `Sortfold120BugsTest` → `B-09 source app is detected from the file name in a flat folder` |
| B-10 | M | undo | REPLACE-destroyed files unrestorable (no warning, no log); `copyBack` without size check, silent `name (1).jpg` stacking, littered empty folders. | Job warns + asks before replacing and logs `replaced:<name>`; `copyBack` verifies size, resolves collisions explicitly (rename with a clear log entry), removes emptied created folders, prefers `moveDocument`. | `Sortfold120BugsTest` → `B-10 copyBack refuses to stack duplicate names…`, `B-10 successful undo removes empty created folders` |
| B-11 | M | worker | Process death after copy but before delete left source + destination both present; resume treated the copy as a "duplicate" and skipped. | Row is marked `IN_FLIGHT` (with its planned destination name) before the provider is touched; on resume: equal sizes → finish the delete (MOVED); partial/missing → clean and re-queue. | `Sortfold120BugsTest` → `B-11 worker reconciles a complete IN_FLIGHT copy into MOVED`, `B-11 worker rolls back a partial IN_FLIGHT copy…` |
| B-12 | L | wizard | `reset()` missed `userAdjusted`, `dateStyleIso`, `livePreview`, `scanError`, `scanning`, `undoBusy`, `mediaReadDenied`, `pendingDefaultTree`. | `reset()` clears every field and re-applies saved defaults through the same init path. | covered by `SystemBugsTest` `BUG-17` (defaults re-apply) + wizard reset flow; full matrix in `LayoutMatrixTest` |
| B-13 | L | wizard/db | Rebuilding a preview only called `updatePlan(totals)` — `modesCsv`, `duplicatePolicy` (and the new cap) stayed stale in History. | `updatePlanColumns(id, totals, modesCsv, duplicatePolicy, capacityBytes)` refreshes every plan-related column. | `Sortfold120BugsTest` → `B-13 rebuilding a plan refreshes modes policy and cap…` |
| B-14 | L | updater | `lastUpdateCheckAt` only — the daily worker re-notified for the same release forever. | `lastNotifiedVersion` setting; notify only when the release version differs, then persist it. | `Sortfold120BugsTest` → `B-14 lastNotifiedVersion is persisted` |
| B-15 | L | metadata | `dateFromName` outranked EXIF and matched arbitrary digit runs; Calendar silently rolled Feb 31 → Mar 3; `.ts` mislabelled video. | EXIF/video metadata first; name dates only at the start of known capture patterns and only for valid calendar dates (`LocalDate.of` + catch); `.ts` video only when the mime says so. | `Sortfold120BugsTest` → `B-15 dateFromName rejects mid-name dates and impossible dates`, `B-15 ts files are not video unless the mime says so` |
| B-16 | L | rules | Unreadable dimensions silently landed in `SD`. | `ResolutionClass.UNKNOWN` bucket (translated in all five locales). | `Sortfold120BugsTest` → `B-16 unknown resolution has its own bucket and segment` |
| B-17 | L | db/worker | `byJobStatusPaged(Int.MAX_VALUE)` loaded every planned row; no index on `move_logs.jobId`. | v3 migration adds `index_move_logs_jobId_status` (+ `@Entity(indices)`); the worker walks `byJobStatusAfterSeq` in pages of 500 (keyset, exclusive cursor starting at −1). | `Sortfold120BugsTest` → `B-17 keyset paging walks rows without loading everything`; `DbMigrationTest` verifies the index exists |
| B-18 | L | manifest | Unused `READ_MEDIA_*` permissions; test-host `ComponentActivity` in the MAIN manifest; backup rules OK but `tools:targetApi="35"` stale; Android 15 dataSync 6-hour limit unhandled. | Media permissions and their UI removed (SAF needs none); `tools:targetApi="36"`; the test-host `ComponentActivity` stays in the main manifest — moving it to `src/test`/`src/debug` was attempted and reverted because Robolectric registers components from the binary variant APK, which merges neither. It remains `exported=false` and is never launched by users (documented deviation); system stop resolves to PAUSED with a "paused by system" message, `setJobKilledBySystem` and the existing one-tap resume. | `Sortfold120BugsTest` → `B-18 manifest drops media read permissions` |
| B-19 | H | release | Missing secrets arrive as EMPTY strings → `hasReleaseKey` became true → cryptic keystore failure (or a debug-signed release that could never update). | First workflow step fails with a per-secret error message; `build.gradle.kts` treats blank as unset; README documents the same-keystore rule. | `Sortfold120BugsTest` → `B-19 blank strings are rejected as keystore values` (build-script contract); guard step pinned in `.github/workflows/release.yml` |

### Behaviour-changing test updates

- `RuleEngineTest` → `resolution classes follow orientation then quality`:
  `ResolutionClass.of(null, null)` now expects `UNKNOWN` (B-16). The old
  assertion pinned the buggy SD fallback.
- `MetadataReader.enrich` prefers real metadata over name dates (B-15); no
  prior test pinned the old (wrong) priority.

### Additional bugs found during the final self-audit (Part D.4)

- **A-NEW-1 (H, found in test)**: the 1.2.0 keyset paging initially started
  its cursor at `seq = 0`, which silently skipped the first planned row of
  every job (the cursor is exclusive). Caught red by the new end-to-end
  tests, fixed to start at `-1`, and pinned by `B-17 keyset paging…`.
- **A-NEW-2 (M, found in test)**: the B-08 "claim the name back" rename ran
  even when the requested name was still taken by another file; on providers
  whose rename replaces, that could destroy the neighbour. It now renames
  only when the requested name is free; REPLACE reclaims its name strictly
  after the old file is destroyed. Pinned by `CoreBugsTest` `BUG-05` plus the
  B-01 full-run test.
- **A-NEW-3 (L)**: worker MOVED rows did not record the provider-assigned
  final name (rename rows kept the planned name in History). Fixed with
  `updateStatusWithDest` and asserted in `B-01 a full run…`.

### Not verifiable without a real device

- `moveDocument` support per provider (Pixel Files, Samsung, MIUI, MTP,
  cloud providers) — the fast path degrades to verified copy+delete when the
  flag is absent or the call throws.
- Real multi-GB timing/throughput and ETA accuracy; the 1 MiB loop is
  benchmarked only as the 10,000-file packer budget (< 1 s, JVM).
- Android 15 dataSync 6-hour timeout delivery (system-driven `onStopped`).
- StorageManager volume resolution for physical SD cards (uses `uuid`
  matching; falls back to warn-only).
