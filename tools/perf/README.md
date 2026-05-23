# Perf benchmarks

JMH (Java Microbenchmark Harness) micro-benchmarks for the two hot paths in
this app:

| Benchmark | What it measures | Why |
| --- | --- | --- |
| `DecodeBenchmark.fullDecode` | `SkikoImageLoader.load` cold latency on a 4000×3000 JPEG, no embedded thumb. | Regression catcher for the main decode + bitmap-convert pipeline. |
| `DecodeBenchmark.embeddedThumbFastPath` | Same loader, but a JPEG with an APP1-embedded thumb requested at a small viewport. | Regression catcher for the PR #10 fast path. |
| `FavouritesToggleBenchmark.toggleSameId` | `JsonFavouritesRepository.toggle` end-to-end latency. | Catches changes in the favourites write path (e.g. moving from debounced to inline writes). |

All benchmarks are written in Kotlin under `src/jmh/kotlin/`. The build is
wired through the [`me.champeau.jmh`](https://github.com/melix/jmh-gradle-plugin)
plugin in `build.gradle.kts`.

## Run

```bash
./gradlew jmh
```

Output:
- `build/jmh/results.json` — machine-readable, the file you compare across branches.
- `build/reports/jmh/` — Gradle HTML report (optional eyeballing).

Defaults: 2 forks × (3 warmup + 5 measurement) iterations × 1s each (~50s
end-to-end). To change duration, edit the `@Warmup` / `@Measurement` /
`@Fork` annotations on each benchmark class, or the `jmh { }` block in
`build.gradle.kts` (which takes precedence when set).

To attach JMH's built-in GC profiler (reports allocation rate per op),
set `profilers = listOf("gc")` in the `jmh { }` block and re-run.

## Compare against a baseline (e.g. `develop`)

JMH's gradle plugin does not auto-diff against another branch — the standard
flow is to run twice and diff the JSON. Use a worktree so you don't have to
stash:

```bash
# 1. Run on the current branch
./gradlew jmh
cp build/jmh/results.json /tmp/results-feature.json

# 2. Run on develop in a separate worktree
git worktree add ../photo-selector-develop develop
(cd ../photo-selector-develop && ./gradlew jmh)
cp ../photo-selector-develop/build/jmh/results.json /tmp/results-baseline.json
git worktree remove ../photo-selector-develop

# 3. Diff
./tools/perf/diff.sh /tmp/results-baseline.json /tmp/results-feature.json
```

`diff.sh` is a small `jq` script that prints each benchmark's mean score and
the % delta between baseline and feature. See its source for the formula.

## Interpreting results

- **Score units are microseconds (`us/op`)**, lower is better. Each benchmark
  uses `@BenchmarkMode(Mode.AverageTime)`.
- **The `Score Error` column is the 99.9% confidence interval half-width**.
  Treat any delta smaller than the sum of the two errors as noise.
- **First few warmup iterations always run hot** — they're discarded, but if
  you see warmup numbers dominating the JSON it usually means the workload is
  too short. Increase `time` in the `@Warmup`/`@Measurement` annotations.
- **macOS thermal throttling will skew everything.** Close other apps, plug in,
  and don't compare numbers taken in different cooling states.

## What's intentionally not here

- **Energy / power draw.** Not measurable from inside the JVM. On macOS,
  `sudo powermetrics --samplers cpu_power -i 1000 -n 30` against a workload
  is the right tool, but it lives outside Gradle and needs sudo.
- **Lifecycle / cancellation latency.** "How fast does prefetch stop after the
  caller scope dies" is a behavioural test, not a benchmark — the signature
  change in `ImageLoader.prefetch` is the proof. Activity Monitor / `top -pid`
  during manual driving is enough confirmation.
- **End-to-end UI flow timing.** JMH is the wrong tool for "how long does
  scanning a 5k-photo folder take." If we ever need that, JFR + a deterministic
  workload replay is the right next step.
