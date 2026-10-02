<p align="center">
  <img src="docs/sortfold-logo.svg" alt="Sortfold icon" width="96" />
</p>
<h1 align="center">Sortfold</h1>
<p align="center"><em>Tidy your media folders. Preview first, undo anytime.</em></p>
<p align="center">
  <a href="https://github.com/tukiza7-debug/Sortfold/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/tukiza7-debug/Sortfold?include_prereleases&label=release&color=0F6B5F"></a>
  <a href="https://github.com/tukiza7-debug/Sortfold/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/tukiza7-debug/Sortfold/total?label=downloads&color=0F6B5F"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-MIT-0F6B5F"></a>
  <img alt="Android" src="https://img.shields.io/badge/Android-10%2B-3DDC84">
</p>

Sortfold organizes the images and videos in a folder you choose. It scans with the system folder picker (SAF, scoped storage), shows a dry-run preview of every move, then sorts into `Images`, `Videos` and nested subfolders. Nothing is deleted, every move is logged, and one tap undoes the last operation.

## What it does

- Pick a source folder; Sortfold scans it and suggests the most useful sort mode.
- Review a full plan: each file, its destination folder and its size — before anything moves.
- Apply in the background with real progress, pause/cancel from the notification, and resume after app restarts.
- Undo the last operation with one tap; duplicates are skipped, renamed or replaced, your choice.
- Every caught error lands in a built-in Error Library you can filter, inspect and export as a ZIP.

## Sort modes

Combine any of the seven; when combined, folders nest in a fixed order (type first, name rules last).

| # | Mode | Folders it creates |
|---|--------------|--------------------------------------------|
| 1 | File type | `Images`, `Videos`, `Other` |
| 2 | Date taken | `2024` or `2024-05` from capture date |
| 3 | Source app | `Camera`, `Screenshots`, `WhatsApp`, `Telegram`, `Downloads` |
| 4 | Resolution / orientation | `Portrait`, `SD`, `HD`, `4K` |
| 5 | Size | `Small` (< 1 MB), `Medium`, `Large` (> 50 MB) |
| 6 | Extension | `jpg`, `png`, `mp4`, `mkv`, … |
| 7 | Name pattern | your own keyword/prefix rules |

## How it works

1. **Choose folder** — SAF picker; Sortfold only reads inside the granted tree.
2. **Choose sort type** — one or more of the seven modes, plus duplicate handling.
3. **Preview** — the exact list of moves with warnings (large batch, low storage, unsafe targets). Apply is blocked if storage is insufficient.
4. **Apply** — a foreground `WorkManager` job (dataSync) moves files copy-then-delete, resumable from the last completed file.
5. **Result** — summary with skipped and failed items and reasons, plus one-tap Undo.

## Install

<details>
<summary>Expand install steps</summary>

1. Open the [latest release](https://github.com/tukiza7-debug/Sortfold/releases/latest).
2. Download the APK for your device (`arm64-v8a` covers most phones; `universal` works everywhere).
3. Install it. The app asks for media access only when it needs it, with an explanation first.

Or build it yourself from source (below).
</details>

## Languages

| Language | Code |
|------------------|--------|
| English | `en` |
| Bahasa Melayu | `ms` |
| Bahasa Indonesia | `in` |
| العربية (RTL) | `ar` |
| 简体中文 | `zh-CN` |
| System default | — |

Switching is instant via per-app language settings (Android 13+) and in-app switching (Android 10–12).

## Settings

Nine groups, every row functional: Language, Appearance (theme, dynamic color, reduce animations), Sorting defaults (mode, duplicate handling, destination folder, confirm-before-apply, large-batch threshold, auto-sort rules), History (retention, clear), Notifications, Updates (auto-check, check now, current version), Storage (app data size, clear cache), Diagnostics (Error Library with entry count and 30-day retention, include-full-paths toggle) and About. The Error Library is a sub-screen of Settings, deep-linked from scan errors, update errors and crash notices; identical errors are grouped with a repeat count and exported as a ZIP with paths masked by default.

## What's new in 1.1.0

A refreshed toolchain (AGP 8.13 / Kotlin 2.2.20 / Compose BOM 2025.09 / Android 16), a design-system pass with springs, skeletons and a wizard stepper, live progress on Home, search in History, a pager-based onboarding, and 27 bug fixes — including safer REPLACE semantics (your files can no longer be lost to a failed copy), crash-proof settings storage, and reliable undo. See [docs/CHANGELOG-1.1.0.md](docs/CHANGELOG-1.1.0.md) and [docs/AUDIT-1.1.0.md](docs/AUDIT-1.1.0.md).

## Build from source

```bash
git clone https://github.com/tukiza7-debug/Sortfold.git
cd Sortfold
./gradlew assembleDebug
```

Requirements: JDK 17, Android SDK 36. Release signing reads `KEYSTORE_FILE`, `KEY_ALIAS`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD` from the environment; CI wires them from repository secrets.

## Contributing

Issues and pull requests are welcome. Keep changes small, keep every UI string in all five languages (a unit test enforces parity), and match the existing code style.

## Licence

[MIT](LICENSE). No ads, no analytics, no account. The network is used only to check GitHub Releases for updates.
