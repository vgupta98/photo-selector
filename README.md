<p align="center">
  <img src="src/main/resources/icon/app-icon.png" width="140" alt="Rhenium app icon" />
</p>

<h1 align="center">Rhenium</h1>

<p align="center">
  <b>Cull thousands of shots in minutes — without your photos ever leaving your Mac.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-macOS-111111" alt="Platform: macOS" />
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="License: MIT" />
  <img src="https://img.shields.io/github/stars/vgupta98/photo-selector?style=social" alt="GitHub stars" />
</p>

Rhenium is a free, open-source, keyboard-driven photo culler for macOS. Open a
folder of JPEG, HEIC or camera RAW, and Rhenium groups the near-identical frames
for you — so you make one decision per *moment*, not one per frame. No account,
no cloud, no subscription: the similarity model runs entirely on-device and
nothing ever leaves your machine.

> **macOS today — Windows support is on the roadmap.** Star and watch the repo to follow along.

<!-- TODO(marketing): add a 10–15s screen-capture GIF of keyboard culling + the
     "Similar" grouping lens right here. This is the single highest-impact thing
     on the page — a culling app sells on motion, not prose. -->
<!-- ![Rhenium in action](docs/demo.gif) -->

## Why Rhenium

- **It groups the duplicates for you.** An on-device similarity model — plus a
  burst detector — collapses near-identical frames into one tile with the
  sharpest shot suggested as the pick, so a 12-frame burst is one keystroke, not
  twelve. No other open-source culler does this.
- **Reads what you actually shoot.** JPEG, PNG, HEIC (the iPhone default) and
  camera RAW — Canon, Sony, Nikon, Fujifilm, Adobe DNG, Panasonic and Olympus.
- **Private by design.** 100% local: no upload, no account, no telemetry. Your
  photos — and the model scoring them — never touch a network.
- **Keyboard-first and built for scale.** Arrow through, file with one key, never
  reach for the mouse. Handles 30k+ photo shoots with a cancellable scan and
  bitmap cache.

Built with Kotlin + Compose Multiplatform Desktop, following Clean Architecture.

## Features

- **Browse** an entire folder tree of JPEG / PNG / HEIC / camera RAW photos
  full-screen (HEIC/HEIF and RAW on macOS — RAW covers Canon, Sony, Nikon,
  Fujifilm, Adobe DNG, Panasonic and Olympus, shown via the camera's embedded
  preview).
- **Keyboard-first navigation** — `←` / `→` to move, `F` (or `Space`) to toggle Favourites, `1`…`9` to toggle the other categories, `G` to switch the grouping lens.
- **Categories** — sort photos into as many flat lists as you like (Selects, Maybes, For Album X…). **Favourites** is the built-in one; create, rename and delete the rest from the top bar. A photo can be in several at once.
- **Multi-select & bulk filing** — in the grid, `Cmd`-click to pick out photos, `Shift`-click to extend a run, or `Cmd+A` to select everything in view; then file the whole selection into Favourites (`F`) or a category (`1`…`9`), copy just the selection to a folder, or press `C` to open the selection together in **Inspect**. `Esc` clears it.
- **Delete to Trash** — pick photos in the grid and **Delete** (or `Cmd`+`Delete`) moves them to the macOS Trash after a confirmation; in the full-screen browser `Cmd`+`Delete` removes the current photo and advances to the next. Recoverable from Finder, and the deleted photos are dropped from every category.
- **Persistent** — categories are stored as a single `.photo-selector-categories.json` file inside your photo folder. Switch folders and each retains its own lists.
- **Inspect** — select two or more tiles in the grid (or press `C` on a focused group, or `C` in the browser for the current photo + its neighbour) to view a fixed set of photos together. It opens as an overview grid you can scan at a glance; press `Enter` (or the top-bar toggle) to **browse** the set full-screen, one photo at a time with pan/zoom, then toggle back. Arrows or `Tab` move the highlighted tile; `F` / `1`…`9` file it; `Esc` steps back to the grid, then out to wherever you opened Inspect from. A large set (past the overview cap, like a long burst) opens straight into browse.
- **Grouping lenses** — collapse near-identical frames into one grid tile with a count badge, so a moment is a single decision instead of a dozen near-identical thumbnails. A segmented control in the grid toolbar (or `G` to cycle it from the keyboard) picks the lens: **off** (flat grid), **bursts** (frames from the same camera within ~2 seconds — needs a real capture time, which today comes from JPEG EXIF, so HEIC isn't burst-grouped yet), or **similar** (visually near-identical shots, grouped on-device — nothing leaves your Mac — regardless of when they were taken, with the suggested-sharpest frame marked **Pick** as the representative; a hint you can override). The first cold similarity pass shows a one-time explainer and a progress banner; afterwards the result is cached, so re-opening the lens on an unchanged folder is instant, and a one-line summary names what was grouped. Hover a grouped tile for **Review →** (or press `C` with it focused) to open its frames straight in **Inspect**, or click it to **unfold it in place** and cull the frames inline with the usual keys (`F` / `1`…`9` file the focused frame); click **Collapse** (or `Esc`) to fold it back.
- **Category grids** with thumbnails; click any thumbnail to jump back to that photo in the browser. When viewing a category photo full-screen, **Show in All Photos** (top bar, or press `A`) jumps to where it sits in the full library.
- **Toast feedback** on every Favourites toggle so you can never silently lose a selection.
- **Export TXT** — write a category's photos as relative paths, one per line, UTF-8.
- **Export Copy** — copy a category into a destination folder while preserving subfolder structure. Pick OVERWRITE / SKIP / RENAME on conflicts.
- **Read-only volumes** are detected — selections still work in-memory with a clear banner.
- **Handles 30k+ photos**: cancellable directory scan with live progress, LRU bitmap cache, prefetcher.

## Requirements

- macOS 12 or later (Apple Silicon or Intel).
- That's it for end users — the bundled DMG ships its own JRE.

For developers:
- JDK 17 (any distribution).
- Gradle wrapper is included; no global Gradle needed.

## Install & run (end user)

### Option A — Homebrew (recommended)

```sh
brew install --cask vgupta98/tap/photo-selector
```

This taps the repo and installs **Rhenium.app** into `/Applications`. Update it
later with `brew upgrade --cask photo-selector`, or remove it with
`brew uninstall --cask photo-selector`. (The cask keeps its original
`photo-selector` token even though the app is now Rhenium.)

Because the app isn't notarised by Apple, macOS Gatekeeper blocks it on the first
launch. Homebrew prints the fix in its caveats — clear the quarantine flag once:

```sh
xattr -dr com.apple.quarantine "/Applications/Rhenium.app"
```

or right-click the app in Finder and choose **Open**. You only need to do this once.

### Option B — Download the DMG

1. Download `Rhenium-1.0.0.dmg` from the [Releases](https://github.com/vgupta98/photo-selector/releases) page (or build it yourself — see below).
2. Open the DMG and drag **Rhenium.app** to `/Applications`.
3. **First launch — get past the Gatekeeper warning.** The app is not notarised by Apple, so on the very first launch macOS will show a dialog like *"Apple could not verify 'Rhenium' is free of malware…"*. To allow it:
   1. Click **Done** to dismiss the dialog.
   2. Open **System Settings → Privacy & Security**.
   3. Scroll down to the **Security** section — you'll see a message like *"Rhenium was blocked to protect your Mac."*
   4. Click **Open Anyway**, then confirm with Touch ID / password.
   5. Double-click the app again — this time it launches normally. You only need to do this once.

Then, with either option: launch Rhenium, click **"Choose folder…"**, point it at your photo root, wait for the scan, then start browsing.

### Keyboard shortcuts

| Key | Action |
|---|---|
| `←` | Previous photo |
| `→` | Next photo |
| `F` or `Space` | Toggle Favourites for current photo |
| `1` … `9` | Toggle the current photo in the Nth custom category (grid + browser) |
| `C` | Open in **Inspect** — the current photo + its neighbour (browser), the grid selection, or a focused group's frames |
| `G` | (Grid) Cycle the grouping lens: Single → Bursts → Similar |
| `A` | (Browser, when the photo was opened from a category) Show this photo in the All Photos grid |
| `Cmd`+`A` | Select every photo in the current grid |
| `Cmd`+`Delete` | Move the selection (grid) or current photo (browser) to the Trash, after a confirm |

In **Inspect**, the selected photos open as an overview grid; `Tab` and the arrow keys move the highlighted tile, `F` / `1`…`9` file it, and `Enter` (or the top-bar toggle) opens the active photo in browse mode — full-screen, with `← →` to move across the set and `+` / `−` / `0` to zoom. `Esc` steps back: from browse to the grid, then out to wherever you opened Inspect from (the grid, or the browser).

In the **grid**, `Cmd`-click or `Shift`-click tiles (or `Cmd+A`) to multi-select, then `F` / `1`…`9` file the whole selection into a category, **Copy photos to folder…** copies just the selection, **Delete** (or `Cmd`+`Delete`) moves the selection to the macOS Trash after a confirm, `C` opens the selection in **Inspect**, and `Esc` clears it.

### Categories and exporting

From the All Photos top bar click **Categories** to open the dropdown: pick a category to open its grid, or **New category…** to create one. To file a photo into a custom category, focus it in the All Photos grid (or open it full-screen) and press the category's digit — `1` for the first custom category, `2` for the second, and so on; `F` always toggles Favourites. In the full-screen browser a heads-up legend along the bottom shows each category with its key and whether the current photo belongs to it; it auto-hides and reappears on any keypress or mouse move, and you can click a chip instead of using the keys. Inside a category grid, the **⋯** menu renames or deletes it (the built-in Favourites can't be renamed or deleted). From any category grid:

- **Export list (.txt)** — pick a destination `.txt` file; one relative path per line.
- **Copy photos to folder…** — pick a destination folder; subfolder structure is preserved. Use the policy menu to choose how filename conflicts are handled (default: rename to `foo (2).jpg`).

## Build from source

```bash
git clone https://github.com/vgupta98/photo-selector.git
cd photo-selector

# Run directly
./gradlew run

# Build a redistributable DMG
./gradlew packageDmg
# → build/compose/binaries/main/dmg/Rhenium-1.0.0.dmg
```

The DMG is built for the host architecture only. Build on Apple Silicon for an arm64 DMG, on Intel for an x86_64 DMG.

## Project layout

Single Gradle module organised by Clean Architecture layers:

```
src/main/kotlin/com/vishalgupta/photoselector/
├── Main.kt, App.kt                  # entry point + screen router
├── di/                              # manual DI graph (AppContainer)
├── domain/                          # pure-Kotlin entities, repositories, use cases
├── data/                            # filesystem, categories JSON, image decoding, on-device similarity (ai/), exporters, trash
└── presentation/                    # Compose screens + view models
    ├── rootpicker/
    ├── browser/
    ├── inspect/
    ├── survey/
    ├── grid/
    ├── navigation/
    ├── common/                      # file dialogs, system actions, hover
    └── designsystem/                # atomic design: theme tokens, atoms, molecules, organisms
```

Dependency rule: `presentation → domain`, `data → domain`. `domain` depends on nothing.

## Tech stack

- Kotlin 2.0.21, Compose Multiplatform 1.7.3, JDK 17
- Gradle 8.10.2 (wrapper)
- kotlinx-coroutines 1.9.0, kotlinx-serialization 1.7.3
- Skiko `Image.makeFromEncoded` for decode + hand-rolled LRU bitmap cache
- ONNX Runtime 1.20 for the on-device visual-similarity lens (a bundled
  MobileNetV3-Small embedding model; nothing is downloaded and no pixels leave
  the machine)

## License

MIT — see [LICENSE](LICENSE).
