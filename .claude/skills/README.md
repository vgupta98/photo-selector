# Vendored agent skills

This directory contains Compose and Kotlin agent skills vendored from
[chrisbanes/skills](https://github.com/chrisbanes/skills) (Apache 2.0 —
see [`LICENSE`](LICENSE)). Each skill is a single `SKILL.md` and
auto-triggers when its description matches the task at hand; no manual
invocation is needed.

## Skills included

### Compose — state and side effects

- `compose-state-authoring`
- `compose-state-hoisting`
- `compose-state-holder-ui-split`
- `compose-side-effects`

### Compose — performance

- `compose-recomposition-performance`
- `compose-stability-diagnostics`
- `compose-state-deferred-reads`

### Compose — UI API design, layout, testing

- `compose-modifier-and-layout-style`
- `compose-slot-api-pattern`
- `compose-animations`
- `compose-focus-navigation`
- `compose-ui-testing-patterns`

### Kotlin

- `kotlin-coroutines-structured-concurrency`
- `kotlin-flow-state-event-modeling`
- `kotlin-types-value-class`

## Not vendored

- `kotlin-multiplatform-expect-actual` — this project is pure JVM Compose
  Desktop, not Kotlin Multiplatform.

## Updating from upstream

These files are vendored copies, not a git submodule. To pick up upstream
changes, re-fetch each `SKILL.md` from
`chrisbanes/skills:skills/<name>/SKILL.md` on the desired ref and update
this directory in a single PR. Re-vendor the `LICENSE` alongside if it
has changed.
