# Sortfold 1.2.0 — What Changed

The headline feature: **Split by capacity**. Point Sortfold at a folder,
choose a limit (1–4 GB or any custom size), and it fills `Part 01`,
`Part 02`, … folders that each stay under the cap. Oversized files are kept
together in `Oversized` and flagged, never split. Alongside it: 19 bug fixes
(several serious), faster multi-GB moves, and a motion polish pass.

## New: Split by capacity (the eighth sort mode)

- **Presets and custom sizes** — 1, 2, 3, 4 GB chips plus a custom field
  with decimals (2.5), MB/GB unit choice, and validation from 50 MB to 1 TB
  with inline errors. Nothing crashes on empty or nonsense input.
- **Decimal or binary GB** — decimal (1 GB = 1,000,000,000 bytes) matches
  what phone storage screens show; binary (1 GiB = 1,073,741,824 bytes) is
  one toggle away in Settings. The active unit is spelled out under the input.
- **Keep order or fewest folders** — "Keep order" packs by capture date and
  natural name order (file2 before file10); "Fewest folders" uses
  First-Fit-Decreasing to minimise the folder count.
- **Your own prefix** — "Part" by default, sanitised (no `/ \ : * ? " < > |`,
  max 24 characters).
- **Live estimate** — "About N folders · largest Part 3.94 GB" computed from
  the full scan, debounced and packed off the main thread.
- **Per-folder preview** — `Part 01 · 38 files · 1.97 GB / 2 GB` rows with
  thin progress bars; oversized files get a labelled row and warning chip.
- **Auto-sort aware** — daily rules can use the capacity mode and continue
  numbering where the previous run stopped: existing `Part NN` folders are
  measured first, the last partial folder is topped up, and files already in
  part folders are never re-sorted.
- **Suggested for big folders** — scans of 8 GB or more now suggest the
  capacity mode first ("big-total").
- **Remembered** — the last capacity/order/prefix/unit become the defaults,
  but never override a choice you already made this session.

## Faster and safer moves

- Instant moves: when the storage provider allows it, files are moved with
  `moveDocument` — no copying, no double space. The verified copy+delete
  fallback remains for providers that don't.
- Byte-accurate progress: the notification and progress bar now track bytes
  with the current file name and an ETA (shown after 10 s), so a 3 GB video
  no longer looks frozen at "1 of 40".
- Self-healing: a process death mid-file is reconciled on the next run —
  complete copies finish, partial ones are redone. Nothing is left half-moved.
- Honest duplicate handling: RENAME and REPLACE now really work (the plan
  finally sees what is already in the destination folder), and replacing
  files asks with its own confirmation because undo cannot restore them.
- Retry failed files: a partial job offers a one-tap retry pass over just
  the failed rows.

## Motion polish (Part C)

- Press feedback actually springs now (the 1.1.0 scale snapped silently).
- Fade-through navigation between top-level screens: the old screen fades
  out in 90 ms, the new one fades in with a gentle 8% scale-up.
- Predictive back inside the wizard: the step follows your gesture (scales
  toward 0.92) and commits on release.
- Mode cards cross-fade and their check icon springs in; the capacity panel
  expands smoothly; the preset chips share one sliding selection indicator;
  the folder estimate counts up and down.
- Reduced motion stills all of it — cross-fades only, as before.

## Fixed

Serious:
- **RENAME and REPLACE duplicate policies never worked** — the plan never
  saw the destination folder's contents, so both behaved like SKIP. Fixed
  end-to-end (plan, worker safety net, and a real rename in the log).
- **Pause/Cancel could strand a job as RUNNING** and desync the undo log —
  pause/cancel are now cooperative (the current file finishes and is logged)
  and no code path can leave a job stuck.
- **Large files copied through an 8 KB buffer with no in-file progress** —
  1 MiB chunks with cancellation checks and byte progress now.
- **Every move was a full copy+delete** — `moveDocument` is used when the
  provider supports it, with fallback.
- **The release build could silently lose its signing config** — GitHub
  passes empty strings for missing secrets; the workflow now fails fast with
  a clear message, and blank secrets are treated as unset in the build.

Also fixed (selected):
- Storage pre-check measured the wrong volume and blocked legitimate jobs;
  it now checks the tree's own volume against the largest file and only
  warns when it cannot know.
- Undo collisions no longer stack "name (1).jpg" silently; sizes are
  verified and emptied folders are cleaned up.
- A resumed job could report DONE despite earlier failures; done/failed are
  now computed from the database.
- Planned rows lost their mime type (some providers then renamed files to
  `.bin`); the mime travels with the plan and the final name is verified.
- SOURCE_APP mode put every file of a flat folder into one bucket; sources
  are now detected per file (`Screenshot_*`, `IMG-*-WA*`, `PXL_*`, …).
- The wizard reset left stale state behind; it clears everything and
  re-applies saved defaults.
- Rebuilding a preview left stale modes/policy on the job row.
- The update notification fired every day for the same version; it now
  notifies once per new version.
- Name-date detection matched arbitrary digit runs and rolled impossible
  dates (Feb 31) over; metadata wins and only valid capture-pattern dates
  are used. `.ts` files are no longer mislabelled video.
- Unreadable resolutions get their own `Unknown` folder instead of being
  mislabelled `SD`.
- The worker loads planned rows in keyset-paged pages of 500 backed by a new
  (jobId, status) index instead of loading everything.
- The app no longer requests media read permissions it never used, and the
  test-only activity no longer ships in release builds (debug variant only).
- Android 15's 6-hour dataSync foreground limit is handled: the job pauses
  with a clear message and one-tap resume.

## For upgraders

- Room database v3 with a real migration — existing jobs, logs and rules
  survive. A migration test opens a v2-shaped database and verifies it.
- versionName 1.2.0, versionCode 3. Same keystore required (see README).
