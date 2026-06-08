#!/usr/bin/env bash
#
# Advisory Stop hook — keeps the "Reuse first; don't grow the code" and
# "structural changes mean re-reading the docs" conventions self-maintaining.
#
# Fires only when the working tree has NEW source under src/main (a new .kt
# file, or a brand-new package directory) while CLAUDE.md / README.md are
# untouched — i.e. exactly the moment duplication or an undocumented package
# can slip in. It emits a quiet `additionalContext` reminder (the assistant
# sees it on the next prompt); it never blocks and never loops. Delete this
# file (and the Stop entry in .claude/settings.json) to remove it.

cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
command -v git >/dev/null 2>&1 || exit 0
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0

# Untracked (??) or staged-added (A) entries under src/main that are either a
# .kt file or a new directory (git collapses a brand-new package to one "dir/").
new_sources=$(git status --porcelain -- src/main 2>/dev/null \
  | grep -E '^(\?\?|A )' \
  | grep -Ec '(\.kt$|/$)')

# Anything pending on the docs that already record structure.
docs_touched=$(git status --porcelain -- CLAUDE.md README.md 2>/dev/null | grep -c .)

if [ "${new_sources:-0}" -gt 0 ] && [ "${docs_touched:-0}" -eq 0 ]; then
  cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"Stop","additionalContext":"Reuse & docs check: this change adds new source under src/main while CLAUDE.md and README.md are untouched. Before wrapping up: (1) confirm you EXTENDED an existing component / helper / parser / test-fake rather than forking a near-twin (CLAUDE.md -> 'Reuse first; don't grow the code'); (2) if a package, public API, or navigation/state shape changed, propose CLAUDE.md / README updates ('structural changes mean re-reading the docs')."}}
JSON
fi
exit 0
