# Release process

<!--
  Relocated from /CLAUDE.md. Read this only when cutting a release or debugging
  the release workflows. CLAUDE.md keeps the one-line "never commit to main"
  rule and points here.
-->

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
   - Runs on `macos-latest` (required for `packageReleaseDmg`).
   - Tags `vX.Y.Z`, builds the DMG, publishes a GitHub Release with the
     DMG attached.

## Required repo setting

Settings → Actions → General → Workflow permissions → **Allow GitHub
Actions to create and approve pull requests** must be ON. Without it,
`draft-release.yml` fails at the `gh pr create` step.

## After every release: back-merge into develop

The release workflow only updates `main`. `develop` keeps its old version
string until you sync it back, and the next Draft Release will refuse to
re-use the same version. Run after each release:

```bash
git checkout develop
git pull --no-ff origin main
git push
```

## Local dry-run

`scripts/dry-run-release.sh [auto|patch|minor|major]` prints exactly what
Draft Release would do (version, branch name, PR body) without touching
git state. Use this to sanity-check before triggering the real workflow.

## Recovering a failed release run

If `draft-release.yml` fails partway, the `release/vX.Y.Z` branch may
already be on origin. Don't finish the job manually — delete the branch
(`git push origin --delete release/vX.Y.Z`) and re-run the workflow. The
workflow's fail-fast "branch already exists" check is intentional.

## Pre-existing v1.0.0 tag

There is a pre-existing `v1.0.0` tag on the remote from before the release
pipeline existed. It is treated as the "previous release" for notes
generation; harmless.
