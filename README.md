# Photo Selector

A fast, keyboard-driven macOS desktop app for triaging large photo folders (e.g. wedding shoots with thousands of JPEGs). Open a folder, swipe through photos, sort them into categories (**Favourites** plus any you create — Selects, Maybes, …), then export a category as either a `.txt` of relative paths or a copied folder that preserves your subfolder structure.

Built with Kotlin + Compose Multiplatform Desktop, following Clean Architecture.

## Features

- **Browse** an entire folder tree of JPEG / PNG photos full-screen.
- **Keyboard-first navigation** — `←` / `→` to move, `F` (or `Space`) to toggle Favourites, `1`…`9` to toggle the other categories.
- **Categories** — sort photos into as many flat lists as you like (Selects, Maybes, For Album X…). **Favourites** is the built-in one; create, rename and delete the rest from the top bar. A photo can be in several at once.
- **Multi-select & bulk filing** — in the grid, `Cmd`-click to pick out photos, `Shift`-click to extend a run, or `Cmd+A` to select everything in view; then file the whole selection into Favourites (`F`) or a category (`1`…`9`), or copy just the selection to a folder. `Esc` clears it.
- **Persistent** — categories are stored as a single `.photo-selector-categories.json` file inside your photo folder. Switch folders and each retains its own lists.
- **Compare** — press `C` in the browser to put two photos side by side with synchronized pan/zoom for choosing between near-identical frames. `Tab` switches the active pane, `← →` swap one candidate, and `F` / `1`…`9` file the active pane.
- **Category grids** with thumbnails; click any thumbnail to jump back to that photo in the browser.
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

1. Download `PhotoSelector-1.0.0.dmg` from the [Releases](https://github.com/vgupta98/photo-selector/releases) page (or build it yourself — see below).
2. Open the DMG and drag **Photo Selector.app** to `/Applications`.
3. **First launch — get past the Gatekeeper warning.** The app is not notarised by Apple, so on the very first launch macOS will show a dialog like *"Apple could not verify 'PhotoSelector' is free of malware…"*. To allow it:
   1. Click **Done** to dismiss the dialog.
   2. Open **System Settings → Privacy & Security**.
   3. Scroll down to the **Security** section — you'll see a message like *"PhotoSelector was blocked to protect your Mac."*
   4. Click **Open Anyway**, then confirm with Touch ID / password.
   5. Double-click the app again — this time it launches normally. You only need to do this once.
4. Click **"Choose folder…"**, point it at your photo root, wait for the scan, then start browsing.

### Keyboard shortcuts

| Key | Action |
|---|---|
| `←` | Previous photo |
| `→` | Next photo |
| `F` or `Space` | Toggle Favourites for current photo |
| `1` … `9` | Toggle the current photo in the Nth custom category (grid + browser) |
| `C` | Open the current photo + its neighbour side by side (Compare) |
| `Cmd`+`A` | Select every photo in the current grid |

In **Compare**, `Tab` switches which pane is active, `← →` substitute the active pane's photo, `F` / `1`…`9` file the active pane, `+` / `−` / `0` zoom both panes, and `Esc` returns to the browser.

In the **grid**, `Cmd`-click or `Shift`-click tiles (or `Cmd+A`) to multi-select, then `F` / `1`…`9` file the whole selection into a category, **Copy photos to folder…** copies just the selection, and `Esc` clears it.

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
# → build/compose/binaries/main/dmg/PhotoSelector-1.0.0.dmg
```

The DMG is built for the host architecture only. Build on Apple Silicon for an arm64 DMG, on Intel for an x86_64 DMG.

## Project layout

Single Gradle module organised by Clean Architecture layers:

```
src/main/kotlin/com/vishalgupta/photoselector/
├── Main.kt, App.kt                  # entry point + screen router
├── di/                              # manual DI graph (AppContainer)
├── domain/                          # pure-Kotlin entities, repositories, use cases
├── data/                            # filesystem, categories JSON, image decoding, exporters
└── presentation/                    # Compose screens + view models
    ├── rootpicker/
    ├── browser/
    ├── compare/
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

## License

MIT — see [LICENSE](LICENSE).
