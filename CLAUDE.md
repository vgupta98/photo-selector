# Photo Selector — Project Guide for Claude

A Kotlin Compose Desktop app (macOS-targeted) for browsing, favouriting and
exporting photos from a local folder. This file captures the non-obvious
context. Read the source for everything else.

## Architecture

Clean architecture, single Gradle module, package
`com.vishalgupta.photoselector`:

- `domain/` — entities (`Photo`, `RootFolder`, `PhotoId`, `Category`,
  `CategoryId`), repository interfaces, use cases. No framework dependencies.
- `data/` — repository implementations: `filesystem/`, `categories/`,
  `image/` (decoding), `format/`, `export/`, `trash/` (move-to-Trash via
  `java.awt.Desktop.moveToTrash`), plus `io/` (the shared
  `AtomicJsonWriter`).
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
  declined at the grid with a toast rather than opened. Grid-originated
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

## Files worth knowing

- `build.gradle.kts` — version, Compose Desktop config, DMG packaging.
- `.github/workflows/draft-release.yml` — release PR workflow.
- `.github/workflows/release.yml` — tag + DMG + GitHub Release workflow.
- `scripts/dry-run-release.sh` — local dry-run of the release logic.
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
