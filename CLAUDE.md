# Photo Selector — Project Guide for Claude

A Kotlin Compose Desktop app (macOS-targeted) for browsing, favouriting and
exporting photos from a local folder. This file captures the non-obvious
context. Read the source for everything else.

## Architecture

Clean architecture, single Gradle module, package
`com.vishalgupta.photoselector`:

- `domain/` — entities (`Photo`, `RootFolder`, `PhotoId`, `Category`,
  `CategoryId`, `PhotoGroup`), repository interfaces, use cases. No framework
  dependencies. `grouping/` holds grouping behind one `PhotoGrouper` seam
  (`suspend (List<Photo>) -> List<PhotoGroup>` of `Single | Burst`): the
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
  visual-similarity grouping: the `EmbeddingModel` seam with two
  implementations — the learned `OnnxEmbeddingModel` (a MobileNetV3-Small
  ONNX backbone run via ONNX Runtime, the default) and the classical,
  dependency-free `DownscaleGrayEmbeddingModel` (the load-failure fallback) —
  plus `SharpnessScorer`, an `EmbeddingCache` that mirrors `DiskThumbnailCache`,
  `PhotoFeatureExtractor`, and the `SimilarityPhotoGrouper` adapter), `export/`,
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
- Navigation is a sealed `Screen` interface (`RootPicker | Grid |
  Browser | Compare | Survey`). `Screen.Grid` carries a `CategoryScope`
  (`AllPhotos | Category(id)`). `Screen.Compare` is the two-up side-by-side
  view: it carries two indices into the scoped photo list (its two panes), is
  reached from the browser with `C` (current + next) or from a two-tile grid
  selection (also `C`), and shares one `ZoomState` across both panes so pan/zoom
  stay synchronized. A grid `C` over three-plus selected tiles instead opens
  `Screen.Survey` — an overview-pick grid (`presentation/survey/`) carrying the
  selected indices: one tile is active, arrows/`Tab` move it, `F`/`1`-`9` file
  it, no zoom. The side-by-side action is capped at `MAX_SURVEY_PHOTOS` (the
  survey grid is non-lazy and pins every tile's decode); a larger selection is
  declined at the grid with a toast rather than opened. The grid also
  collapses adjacent burst frames into one `PhotoGroup.Burst` tile (a
  stacked-frames count badge); clicking that tile **expands the burst in
  place** — `GridDisplayModel` explodes the open burst (`expandedBurstId`)
  into one tile per frame, bracketed by a full-width header and footer that
  fence the run off from the rest of the grid, so the same per-tile
  filing/selection acts on a single frame while it is open (collapsed
  `F` files the whole burst, expanded `F` files the focused frame). One
  burst expands at a time; `Esc` peels selection → collapse → grid-back.
  The toolbar's segmented control picks the grouping lens
  (`GridUiState.groupingMode`: `Off | Time | Similarity` — flat grid,
  time-based bursts (default), or on-device visual similarity); the view
  model resolves each non-`Off` mode to one `PhotoGrouper` and regroups
  off-thread with a mode-aware staleness guard. Grouping is grid-only presentation: focus,
  multi-select and keyboard filing operate over `displayGroups` (the tile
  index space, shared by the view model and the renderer via
  `GridDisplayModel`), while the **flat photo list** stays the index
  source for browser/Compare/Survey nav — and the grid is the *sole*
  translator between the two (`tileIndexForFlat`), so every scroll
  index/position on the wire (browser return, Compare/Survey return,
  persisted `BrowsePosition.lastIndex`) is a flat photo index. Grid-originated
  Compare/Survey return to the grid on `Esc`
  (`Compare.returnToGrid`, `Survey.returnScrollIndex`); browser-originated
  Compare still returns to the browser. Photos live in N flat per-root categories;
  **Favourites** is the built-in one (fixed id `favourites`, cannot be
  renamed or deleted). Each category is pushed as its own `Screen.Grid`
  instance from the All Photos categories dropdown, not toggled in place,
  so each view has its own scroll state. Memberships persist to
  `<root>/.photo-selector-categories.json` (v2); a legacy
  `.photo-selector-favourites.json` migrates into the built-in category
  on first read and is renamed `.bak`. `CategoriesRepository` exposes
  membership as one `observeMemberships` map flow (a future smart category
  resolves behind it — the scope and `slice()` stay predicate-blind).
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

### Headless screenshot tests

`src/test/kotlin/.../screenshot/` runs Compose Desktop UIs headlessly via
`createComposeRule()` and dumps PNGs under `build/screenshots/<name>.png`
(gitignored). Use `ComposeContentTestRule.dumpScreenshot("foo")` from
`ScreenshotSupport.kt`. The PNGs are inspectable — open them, diff them
against a golden, or have an LLM session read them back. This is the
preferred way to verify UI changes that don't require the real app window
(theming, layout, simple interactions). For things that need a live window
(native file picker, DMG packaging), fall back to `./gradlew run`.

### Checking for unnecessary recompositions

Two complementary tools, both desktop-friendly (no Layout Inspector here):

- **Compiler stability reports (static).** `./gradlew compileKotlin
  -PcomposeReports=true --rerun-tasks` dumps `*-composables.txt` /
  `*-classes.txt` under `build/compose_compiler/`. Read them to spot a
  composable that can't skip or a param/class that turned unstable. Off by
  default (zero build cost). `--rerun-tasks` is required — an up-to-date
  `compileKotlin` won't regenerate them.
- **Recomposition-count tests (dynamic).** `RecompositionTracker` +
  `GridRecompositionTest` assert that flipping one tile's favourite/focus
  recomposes only that tile. Three gotchas baked into that test, learned
  the hard way: (1) `record()` must sit directly in the body of the
  restartable composable you measure — a `@Composable () -> Unit` wrapper
  gets its own restart scope and measures the wrong thing; (2) drive state
  via `runOnIdle { }`, a bare test-thread write isn't observed; (3) use a
  plain layout, not a Lazy one, to isolate component skipping from the lazy
  grid's own item subcomposition.

## Release process

Three workflows in `.github/workflows/`:

1. **`draft-release.yml`** — manual (`workflow_dispatch`).
   - Reads `version = "X.Y.Z"` from `build.gradle.kts` (single source of
     truth — don't put the version anywhere else).
   - Walks `main..develop` Conventional Commit subjects and derives the
     bump:
     - `<type>(scope)?!:` or `BREAKING CHANGE:` in body → **major**
     - `feat(...):` → **minor**
     - everything else → **patch**
   - `bump_override` input (`auto|patch|minor|major`) forces a specific
     bump.
   - Creates `release/vX.Y.Z` off develop, commits
     `chore(release): bump version to X.Y.Z`, opens a PR titled
     `release: vX.Y.Z` against `main` with a grouped changelog.
2. **`release-perf.yml`** — fires on `pull_request: opened/synchronize`
   against `main` when the head branch starts with `release/`.
   - Runs JMH benchmarks on both the release branch and `main`, diffs
     the JSON outputs via `tools/perf/diff.sh`, and posts a sticky
     comment on the release PR.
   - Runs on `ubuntu-latest` (cheap shared runner; absolute scores are
     not comparable to local-Mac JMH runs but the cross-branch delta
     is). Treat deltas under ~10% as noise.
   - First release after the harness lands has no baseline on `main`;
     the workflow detects this and posts release-branch numbers only.
3. **`release.yml`** — fires on `pull_request: closed` against `main` when
   the head branch starts with `release/`.
   - Runs on `macos-latest` (required for `packageDmg`).
   - Tags `vX.Y.Z`, builds the DMG, publishes a GitHub Release with the
     DMG attached.

### Required repo setting

Settings → Actions → General → Workflow permissions → **Allow GitHub
Actions to create and approve pull requests** must be ON. Without it,
`draft-release.yml` fails at the `gh pr create` step.

### After every release: back-merge into develop

The release workflow only updates `main`. `develop` keeps its old version
string until you sync it back, and the next Draft Release will refuse to
re-use the same version. Run after each release:

```bash
git checkout develop
git pull --no-ff origin main
git push
```

### Local dry-run

`scripts/dry-run-release.sh [auto|patch|minor|major]` prints exactly what
Draft Release would do (version, branch name, PR body) without touching
git state. Use this to sanity-check before triggering the real workflow.

### Recovering a failed release run

If `draft-release.yml` fails partway, the `release/vX.Y.Z` branch may
already be on origin. Don't finish the job manually — delete the branch
(`git push origin --delete release/vX.Y.Z`) and re-run the workflow. The
workflow's fail-fast "branch already exists" check is intentional.

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
  **Build & Run → Headless screenshot tests** for the mechanics.
- **Structural changes mean re-reading the docs.** Adding or removing a
  top-level package, changing the DI wiring shape, renaming a public
  API, splitting a screen, changing how navigation/state is plumbed,
  modifying the build/release flow — any of these obliges you to
  re-read `CLAUDE.md` and `README.md` end-to-end and propose updates
  for anything they now misrepresent. "Propose" means show the diff in
  chat and wait for go-ahead before staging. Stale docs are worse than
  no docs because they actively mislead the next session.
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
- **There is a pre-existing `v1.0.0` tag** on the remote from before the
  release pipeline existed. It is treated as the "previous release" for
  notes generation; harmless.
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
- **Similarity grouping only merges *adjacent* frames, and persists nothing
  but the embeddings.** Like `BurstGrouper`, `SimilarityGrouper` forms
  contiguous runs in scan order — the grid's expand-in-place burst UI fences
  a contiguous run, so a cluster must be contiguous — meaning it groups
  visually-alike *neighbours*, not arbitrary matches scattered across a
  folder. It never groups across a folder boundary (a folder is an event
  boundary, same as `BurstGrouper`). Per-photo embeddings + sharpness are
  cached to disk (`EmbeddingCache`, keyed by content + model id, invalidated
  on source edit or model swap); the *grouping* itself is recomputed, never
  persisted. The shipped embedder is the learned `OnnxEmbeddingModel` — a
  MobileNetV3-Small backbone (ImageNet, classifier stripped so the output is
  the pooled feature vector) bundled as a classpath resource
  (`src/main/resources/models/mobilenetv3-small.onnx`, ~6 MB) and run via ONNX
  Runtime. `dimensions` (1024) is *probed from the graph at load*, so a model
  swap needs no caller change; the classical, dependency-free
  `DownscaleGrayEmbeddingModel` stays wired as the load-failure fallback. The
  blob is regenerated by the reproducible export in `tools/embedding-model/`
  (pinned timm/torch, Apache-2.0 weights) — bump `OnnxEmbeddingModel`'s `id`
  whenever the produced vectors change so the on-disk cache re-keys, and don't
  bake model assumptions into callers.
- **ONNX Runtime is a bundled native dependency.** The
  `com.microsoft.onnxruntime:onnxruntime` JAR ships a JNI `.dylib` (and the
  win/linux equivalents) that jpackage rolls into the DMG. Unlike the HEIC
  bridge (which loads system frameworks by name and bundles nothing), this is
  real native code in the app bundle — so DMG signing/notarization has to cover
  it, and `OnnxEmbeddingModel` construction must stay fail-soft (it falls back
  to the classical embedder) in case the runtime can't initialise on a given
  host.

## Files worth knowing

- `build.gradle.kts` — version, Compose Desktop config, DMG packaging.
- `.github/workflows/draft-release.yml` — release PR workflow.
- `.github/workflows/release.yml` — tag + DMG + GitHub Release workflow.
- `scripts/dry-run-release.sh` — local dry-run of the release logic.
- `tools/embedding-model/` — reproducible export of the bundled similarity
  ONNX model (pinned `requirements.txt`, `export_mobilenetv3.py`, expected
  SHA-256 in its `README.md`).
- `src/main/kotlin/com/vishalgupta/photoselector/di/AppContainer.kt` —
  central wiring; start here when adding a new screen or repository.

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
