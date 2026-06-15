<!--
  Agent knowledge base. Read these BEFORE fanning out across the source tree —
  the point is to spend one targeted read instead of many greps.

  Convention:
  - /CLAUDE.md is the always-loaded contract: doctrine, conventions, the
    recurring-bug invariants, and gotchas. Behavioural rules live there.
  - These files are read on demand and hold the explorable *reference*:
    where code lives, the release machinery, the test harnesses.
  - Keep the two in sync, not overlapping. If a fact is a rule you must always
    follow, it belongs in CLAUDE.md; if it's "where/how", it belongs here.
  - Sections an agent generated may be marked with an HTML comment; leave
    human-authored prose untouched when regenerating.
-->

# Knowledge base

| File | Read it when you need to… |
| --- | --- |
| [`code-map.md`](code-map.md) | find where something lives — package/file/symbol index plus a by-task "open these files first" table. **Start here.** |
| [`testing.md`](testing.md) | write or run the headless screenshot tests or the recomposition-count checks. |
| [`release.md`](release.md) | cut a release, debug the release workflows, or back-merge into develop. |

Everything else — architecture overview, conventions, the grid index-space
invariants, and the known gotchas (HEIC, ONNX, similarity grouping) — lives in
[`/CLAUDE.md`](../../CLAUDE.md).
