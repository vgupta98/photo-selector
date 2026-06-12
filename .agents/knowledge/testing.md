# Testing harnesses

<!--
  Relocated from /CLAUDE.md (Build & Run). Read this when writing or running the
  screenshot tests or the recomposition-count checks. The *rule* that UI-touching
  changes need a screenshot test stays in CLAUDE.md → Conventions.
-->

## Headless screenshot tests

`src/test/kotlin/.../screenshot/` runs Compose Desktop UIs headlessly via
`createComposeRule()` and dumps PNGs under `build/screenshots/<name>.png`
(gitignored). Use `ComposeContentTestRule.dumpScreenshot("foo")` from
`ScreenshotSupport.kt`. The PNGs are inspectable — open them, diff them
against a golden, or have an LLM session read them back. This is the
preferred way to verify UI changes that don't require the real app window
(theming, layout, simple interactions). For things that need a live window
(native file picker, DMG packaging), fall back to `./gradlew run`.

## Checking for unnecessary recompositions

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
