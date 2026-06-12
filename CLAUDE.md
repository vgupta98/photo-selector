# Photo Selector — Project Guide for Claude

A Kotlin Compose Desktop app (macOS-targeted) for browsing, favouriting and
exporting photos from a local folder. This file captures the non-obvious
context. Read the source for everything else.

## Knowledge base

The rules you must follow, and the non-obvious *why*, live in this file. The
explorable *reference* — a file-by-file code map, the release machinery, and the
test harnesses — lives in `.agents/knowledge/`, read on demand:

- **`.agents/knowledge/code-map.md`** — read this *before* grepping the tree: a
  package/file/symbol index plus a by-task "open these files first" table.
- `.agents/knowledge/testing.md` — the screenshot + recomposition test harnesses.
- `.agents/knowledge/release.md` — the full release workflow and its recovery steps.

## Architecture

Clean architecture, single Gradle module, package
`com.vishalgupta.photoselector`:

- `domain/` — entities (`Photo`, `RootFolder`, `PhotoId`, `Category`,
  `CategoryId`, `PhotoGroup`), repository interfaces, use cases. No framework
  dependencies. `grouping/` holds grouping behind one `PhotoGrouper` seam
  (`suspend (List<Photo>, onProgress) -> List<PhotoGroup>` of `Single | Burst`,
  the callback reporting per-photo progress for the grid's bar): the
  pure `BurstGrouper` (time + camera, over a `CaptureMetadataSource`) and
  the pure `SimilarityGrouper` (visual, over precomputed embeddings +
  sharpness) both feed it, so the heuristic swaps without touching the grid.
  `PhotoGroup.Burst.keyIndex` is the representative frame — middle by default,
  the suggested-sharpest for a similarity cluster.
- `data/` — repository implementations: `filesystem/`, `categories/`,
  `image/` (decoding), `format/` (per-format `PhotoDecoder`s; `macos/`
  holds the JNA→ImageIO bridge for HEIC; `ExifReader` also backs
  `ExifCaptureMetadataSource` — capture time + camera for burst grouping,
  memoized per session by `CachingCaptureMetadataSource`), `ai/` (on-device
  visual-similarity grouping: the `EmbeddingModel` seam — the learned
  `OnnxEmbeddingModel` (MobileNetV3-Small via ONNX Runtime, default) and the
  classical, dependency-free `DownscaleGrayEmbeddingModel` (load-failure
  fallback) — plus `SharpnessScorer`, an `EmbeddingCache` mirroring
  `DiskThumbnailCache`, `PhotoFeatureExtractor`, and the `SimilarityPhotoGrouper`
  adapter; see **Known gotchas**), `export/`,
  `trash/` (move-to-Trash via `java.awt.Desktop.moveToTrash`), plus `io/`
  (the shared `AtomicJsonWriter`).
- `presentation/` — Compose UI + view models, organised by screen
  (`rootpicker/`, `grid/`, `browser/`, `compare/`, `survey/`), plus
  `navigation/` and `common/` (non-UI plumbing: file dialogs, system
  actions, hover).
- `presentation/designsystem/` — the Atomic Design system. `theme/`
  (tokens: `AppColors`/`Spacing`/`Dimens` read via `AppTheme.*`, plus
  `AppTypography`/`AppShapes`), then `atom/`, `molecule/`, `organism/`.
  Screens are the "pages" tier. Build UI from these and add shared
  tokens/components here rather than inlining literals in screens.
- `di/AppContainer.kt` — manual DI container. **No DI framework.** Add new
  wiring here.
- Navigation is a sealed `Screen` interface (`RootPicker | Grid | Browser |
  Compare | Survey`). `Screen.Grid` carries a `CategoryScope` (`AllPhotos |
  Category(id)`); each category opens as its *own* `Screen.Grid` (own scroll
  state) from the All Photos dropdown, never toggled in place. `Screen.Compare`
  is the two-up side-by-side view (two indices, one shared `ZoomState`);
  `Screen.Survey` is the overview-pick grid for a 3+ tile selection (capped at
  `MAX_SURVEY_PHOTOS`; larger is declined with a toast). Both open from the grid
  or browser via `C`; grid-originated Compare/Survey return to the grid on `Esc`
  (`Compare.returnToGrid`, `Survey.returnScrollIndex`), browser-originated
  Compare returns to the browser.
- **The grid is grouping/presentation only — mind the three index spaces.** The
  toolbar's segmented control picks a lens (`GridUiState.groupingMode`: `Off |
  Time | Similarity`, Time default); a non-`Off` mode resolves to one
  `PhotoGrouper` and regroups off-thread behind a determinate progress bar
  (`GridUiState.grouping`) for the cold, minute-long similarity pass. Adjacent
  burst frames collapse into one `PhotoGroup.Burst` tile; clicking expands it in
  place (`GridDisplayModel` explodes `expandedBurstId` into per-frame tiles
  fenced by a header/footer — one burst open at a time, `Esc` peels selection →
  collapse → grid-back; collapsed `F` files the whole burst, expanded `F` the
  focused frame). Two invariants here are recurring bug sources:
  - Focus, multi-select and keyboard filing run over `displayGroups` (tile-index
    space, shared via `GridDisplayModel`); browser/Compare/Survey nav and every
    persisted scroll index (`BrowsePosition.lastIndex`) stay **flat photo
    indices**. The grid is the *sole* translator (`tileIndexForFlat`) — never put
    a tile index on the nav wire or a flat index into grid focus.
  - Re-anchor focus by **photo identity** on every reshape
    (`GridViewModel.refocus`), never a bare index — a regroup renumbers tiles
    under the cursor, so an index silently slides onto a different burst.
- Photos live in N flat per-root categories; **Favourites** is the built-in one
  (fixed id `favourites`, not renamable/deletable). Memberships persist to
  `<root>/.photo-selector-categories.json` (v2; a legacy
  `.photo-selector-favourites.json` migrates in on first read, renamed `.bak`).
  `CategoriesRepository` exposes one `observeMemberships` map flow — scope and
  `slice()` stay predicate-blind for a future smart category.
- State plumbing: `StateFlow` for screen state, `SharedFlow` / `Channel`
  for one-shot events (toasts etc).

## Branching

- `develop` is the working branch. Day-to-day commits land here.
- `main` is the release branch and the GitHub default branch.
- Never commit directly to `main`. It only receives merges from
  `release/vX.Y.Z` branches opened by the **Draft Release** workflow.

## Build & Run

JDK 17 (Zulu or JBR — either works). Gradle wrapper checked in.

| Task | Command |
| --- | --- |
| Launch the app | `./gradlew run` |
| Type-check only | `./gradlew compileKotlin` |
| Run unit tests | `./gradlew test` |
| Build a macOS DMG | `./gradlew packageDmg` (output under `build/compose/binaries/`) |

`run` is the fastest signal for UI work. `compileKotlin` is enough when you
just want to verify a refactor builds.

### Test harnesses

Two desktop-friendly harnesses (no Layout Inspector here): **headless screenshot
tests** (`dumpScreenshot()` → inspectable PNGs, the preferred way to verify a UI
change without a live window) and **recomposition checks** (compiler stability
reports + `RecompositionTracker`/`GridRecompositionTest`). Mechanics, the exact
Gradle invocations, and the hard-won gotchas: `.agents/knowledge/testing.md`.

## Release process

Three workflows in `.github/workflows/` drive it: **`draft-release.yml`**
(manual; derives the SemVer bump from `main..develop` Conventional Commits and
opens the `release/vX.Y.Z` PR), **`release-perf.yml`** (posts a JMH cross-branch
diff on the release PR), and **`release.yml`** (tags + builds the DMG + publishes
the GitHub Release on merge). The `version` in `build.gradle.kts` is the single
source of truth, and after every release you must back-merge `main` into
`develop` or the next draft refuses the version.

Full mechanics — bump rules, the required repo setting, the local dry-run, and
recovering a half-finished run — are in `.agents/knowledge/release.md`.

## Conventions

- **Conventional Commits everywhere.** Release versioning depends on
  subjects parsing correctly (`feat:`, `fix:`, `feat!:`, etc).
- **Commit flow.** When asked to commit: stage the relevant files by name
  (never `git add -A`), then invoke the `/commit staged` skill — do not
  run `git diff`/`status`/`log` first; the skill handles that.
- **Reuse first; don't grow the code.** Before adding a file, composable,
  helper, parser, or test fake, look for an existing one to extend — the
  default is *extend, not fork*:
  - **UI:** compose from existing `atom/`/`molecule/`/`organism/` pieces;
    add a parameter to a component before writing a near-twin; reuse a
    whole screen where the flow fits (a burst opens the existing
    Compare/Survey, not a new viewer).
  - **Parsing/decoding:** extend the existing reader/registry (`ExifReader`,
    `DefaultPhotoFormatRegistry`) rather than writing a parallel one.
  - **Logic:** the second time the same logic appears, extract one helper
    (e.g. `fileIdsInto`) instead of copy-pasting.
  - **Tests:** shared fakes live in `src/test/.../testing/` — reuse them,
    never re-declare a private copy.
  Keep navigation/state on one source of truth and layer presentation over
  it (the grid groups the flat photo list rather than duplicating it). A
  file growing materially, or a new sibling that overlaps an existing one,
  is the signal to extract/extend. When something genuinely new is needed,
  pick the smallest seam — a param, a new `PhotoDecoder`, a strategy behind
  an existing interface.
- **UI-touching changes must include or update a screenshot test.** Any
  change that affects what the user sees on screen — new composables,
  layout tweaks, theming, a decode/render path feeding an existing
  composable — is not done until a `dumpScreenshot()`-backed test
  exercises it and the resulting PNG has been eyeballed. Unit pixel
  assertions on intermediate buffers don't count: they don't catch
  `ContentScale`/`Modifier` interactions or bitmap-conversion bugs in
  the Compose pipeline. The only carve-out is features that genuinely
  need a live window (native file dialogs, DMG packaging) — say so
  explicitly and fall back to `./gradlew run`. See
  `.agents/knowledge/testing.md` for the mechanics.
- **Structural changes mean re-reading the docs.** Adding or removing a
  top-level package, changing the DI wiring shape, renaming a public
  API, splitting a screen, changing how navigation/state is plumbed,
  modifying the build/release flow — any of these obliges you to
  re-read `CLAUDE.md` and `README.md` end-to-end and propose updates
  for anything they now misrepresent. "Propose" means show the diff in
  chat and wait for go-ahead before staging. Stale docs are worse than
  no docs because they actively mislead the next session. Make this an
  end-of-work self-check: whenever a change adds new source under
  `src/main`, pause before wrapping up and confirm both halves — (1) you
  *extended* an existing component/helper/parser/test-fake rather than
  forking a near-twin, and (2) any package, public-API, or
  navigation/state change is reflected in `CLAUDE.md` / `README.md` (and
  `.agents/knowledge/code-map.md` when a file's purpose moves).
- **Capturing a learning in `CLAUDE.md` has a high bar.** If the session
  surfaced something durable, team-relevant, and not derivable from the
  current code (a sharp edge, a workflow that has to happen in a
  specific order, a class of bug that keeps recurring), propose a
  `CLAUDE.md` edit with a one-line justification of *why* it's durable
  rather than session-specific. Per-user preferences and per-session
  context belong in the auto-memory system, not here. When in doubt,
  don't write the bullet — `CLAUDE.md` only stays useful while it
  stays short.
- **`README.md` is user-facing.** Only propose edits to it when the
  change touches something the README already documents: build
  commands, install steps, the architecture overview, supported
  platforms, or the release flow visible from the outside. Don't add
  internal LLM-discipline rules or in-progress design notes — those
  live in `CLAUDE.md` or stay out of the repo entirely.
- **No `Co-Authored-By: Claude`** lines in commit messages.
- **No emojis** in code, commits, or documentation unless explicitly
  requested.

## Known gotchas

- **macOS trackpad pinch zoom** does not reach Compose Desktop with stock
  JDK builds. We support two-finger scroll + `+` / `-` / `0` keys +
  double-click reset instead. Reflective bridges into Apple's gesture
  classes were tried and abandoned — don't reintroduce them.
- **`packageDmg` only runs on macOS.** CI uses `macos-latest`; locally you
  need to be on a Mac.
- **skiko cannot decode HEIC/HEIF.** Verified by probe on the bundled
  skiko (`Image.makeFromEncoded` throws). There is no maintained
  cross-platform JVM HEIC library on Maven (`org.bytedeco:libheif` does
  not exist; FFmpeg was rejected for DMG bloat). HEIC is decoded via a
  JNA bridge into the macOS ImageIO frameworks
  (`data/format/macos/MacImageIO.kt`) and registered in `AppContainer`
  **only on macOS**, behind the `PhotoDecoder` interface. A future
  Windows build adds its own decoder there — don't reintroduce a search
  for a cross-platform lib without re-checking Maven first.
- **Burst grouping reads capture time from JPEG EXIF only, and never
  falls back to mtime.** `ExifReader` is JPEG-only, so HEIC (and any
  EXIF-less file) has no `DateTimeOriginal`. `BurstGrouper` deliberately
  treats a frame with no readable capture time as ungroupable — it stays
  a `Single` — rather than leaning on file mtime, because a bulk copy
  flattens mtime and over-groups unrelated photos (the original mtime
  fallback shipped exactly that bug). So today **HEIC never groups**;
  the way to make it group is reading HEIC capture time (an ImageIO read,
  the same bridge `MacImageIO` already uses), not loosening the heuristic.
  Grouping can also be switched off or to another lens from the grid toolbar
  (`GridUiState.groupingMode`), and is recomputed off-thread on every
  re-slice, which is why `CachingCaptureMetadataSource` exists — keep it
  in the wiring.
- **Similarity grouping merges only *adjacent* frames and never crosses a folder
  boundary** (same contiguity rule as `BurstGrouper`; the expand-in-place burst
  UI fences a contiguous run, and a folder is an event boundary). Per-photo
  embeddings + sharpness are cached to disk (`EmbeddingCache`, keyed by content +
  model id, invalidated on source edit or model swap); the *grouping* itself is
  recomputed, never persisted. The shipped embedder is `OnnxEmbeddingModel` — a
  MobileNetV3-Small backbone (classifier stripped) bundled at
  `src/main/resources/models/mobilenetv3-small.onnx` (~6 MB); `dimensions` (1024)
  is probed from the graph at load, so a model swap needs no caller change.
  Regenerate the blob via `tools/embedding-model/` (pinned timm/torch,
  Apache-2.0) and **bump `OnnxEmbeddingModel`'s `id` whenever the vectors change**
  so the on-disk cache re-keys. Don't bake model assumptions into callers.
- **ONNX Runtime is a bundled native dependency.** The
  `com.microsoft.onnxruntime:onnxruntime` JAR ships a JNI `.dylib` (and the
  win/linux equivalents) that jpackage rolls into the DMG. Unlike the HEIC
  bridge (which loads system frameworks by name and bundles nothing), this is
  real native code in the app bundle — so DMG signing/notarization has to cover
  it, and `OnnxEmbeddingModel` construction must stay fail-soft (it falls back
  to the classical embedder) in case the runtime can't initialise on a given
  host.

## Files worth knowing

The file-by-file index lives in `.agents/knowledge/code-map.md` (package map +
by-task table). The two you'll reach for most: `build.gradle.kts` (version,
Compose Desktop config, DMG packaging) and
`di/AppContainer.kt` (all DI wiring — start here for a new screen or repository).

## Agent skills

Compose and Kotlin agent skills are vendored under `.claude/skills/`
from [chrisbanes/skills](https://github.com/chrisbanes/skills) (Apache
2.0). They are **model-invoked**, not hook-triggered: before writing or
reviewing Compose UI or non-trivial Kotlin (state, side effects,
recomposition, flows, value classes), check whether one matches and
invoke it via the Skill tool. They can also be invoked manually as
`/<skill-name>` (e.g. `/compose-recomposition-performance`) when a task
clearly calls for one. See `.claude/skills/README.md` for the list and
for how to re-vendor from upstream.
