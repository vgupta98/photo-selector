#!/usr/bin/env bash
# Dry-run the version-bump and changelog logic from .github/workflows/draft-release.yml
# and .github/workflows/release.yml — without creating any branches, commits, tags or PRs.
#
# Usage:
#   scripts/dry-run-release.sh [auto|patch|minor|major]
#
# Reads main..develop in your local checkout. Make sure main and develop are up to date.

set -euo pipefail

BUMP_OVERRIDE="${1:-auto}"
case "$BUMP_OVERRIDE" in
  auto|patch|minor|major) ;;
  *) echo "Usage: $0 [auto|patch|minor|major]" >&2; exit 2 ;;
esac

BASE="main"
HEAD="develop"

if ! git rev-parse --verify "$BASE" >/dev/null 2>&1; then
  echo "Local branch '$BASE' not found. Run: git fetch origin $BASE:$BASE" >&2
  exit 1
fi
if ! git rev-parse --verify "$HEAD" >/dev/null 2>&1; then
  echo "Local branch '$HEAD' not found." >&2
  exit 1
fi

RANGE="${BASE}..${HEAD}"
COMMITS=$(git log --pretty=format:'%H' "$RANGE" || true)
if [ -z "$COMMITS" ]; then
  echo "No commits in ${RANGE} — nothing to release." >&2
  exit 1
fi

CURRENT=$(grep -E '^version = "' build.gradle.kts | head -1 | sed -E 's/^version = "([^"]+)".*/\1/')
[ -n "$CURRENT" ] || { echo "Could not parse version from build.gradle.kts" >&2; exit 1; }

# Derive bump
if [ "$BUMP_OVERRIDE" != "auto" ]; then
  BUMP="$BUMP_OVERRIDE"
else
  BUMP="patch"
  while read -r sha; do
    [ -z "$sha" ] && continue
    SUBJECT=$(git log -1 --pretty=format:'%s' "$sha")
    BODY=$(git log -1 --pretty=format:'%b' "$sha")
    if echo "$SUBJECT" | grep -qE '^[a-zA-Z]+(\([^)]+\))?!:'; then BUMP="major"; break; fi
    if echo "$BODY" | grep -qE '^BREAKING[ -]CHANGE:';            then BUMP="major"; break; fi
    if echo "$SUBJECT" | grep -qE '^feat(\([^)]+\))?:' && [ "$BUMP" != "major" ]; then BUMP="minor"; fi
  done <<< "$COMMITS"
fi

IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT"
case "$BUMP" in
  major) MAJOR=$((MAJOR+1)); MINOR=0; PATCH=0 ;;
  minor) MINOR=$((MINOR+1)); PATCH=0 ;;
  patch) PATCH=$((PATCH+1)) ;;
esac
NEXT="$MAJOR.$MINOR.$PATCH"

# Render sections
NOTES=$(mktemp)
render_section() {
  local heading="$1"; local pattern="$2"
  local matches
  matches=$(git log --pretty=format:'- %s (%h)' "$RANGE" --grep="$pattern" --extended-regexp || true)
  matches=$(echo "$matches" | grep -v '^- chore(release): bump version to' || true)
  if [ -n "$matches" ]; then
    {
      echo "### $heading"
      echo "$matches"
      echo
    } >> "$NOTES"
  fi
}

{
  echo "## Summary"
  echo
  echo "Release v${NEXT}. Merging this PR will tag the commit on main and publish a GitHub Release with the macOS DMG attached."
  echo
  echo "## Changelog"
  echo
} > "$NOTES"

render_section "Breaking Changes" '^[a-zA-Z]+(\([^)]+\))?!:'
render_section "Features"         '^feat(\([^)]+\))?:'
render_section "Bug Fixes"        '^fix(\([^)]+\))?:'
render_section "Documentation"    '^docs(\([^)]+\))?:'

OTHER=$(git log --pretty=format:'- %s (%h)' "$RANGE" --extended-regexp \
  --invert-grep \
  --grep='^[a-zA-Z]+(\([^)]+\))?!:' \
  --grep='^feat(\([^)]+\))?:' \
  --grep='^fix(\([^)]+\))?:' \
  --grep='^docs(\([^)]+\))?:' \
  --grep='^chore\(release\):' || true)
if [ -n "$OTHER" ]; then
  {
    echo "### Other Changes"
    echo "$OTHER"
    echo
  } >> "$NOTES"
fi

cat <<EOF
========================================
  Release dry-run
========================================
Current version : ${CURRENT}
Bump            : ${BUMP}$( [ "$BUMP_OVERRIDE" != "auto" ] && echo " (forced)" )
Next version    : ${NEXT}
Release branch  : release/v${NEXT}
Bump commit     : chore(release): bump version to ${NEXT}
PR title        : release: v${NEXT}
Tag             : v${NEXT}
Commits in ${RANGE}:
EOF
git log --pretty=format:'  %h %s' "$RANGE"
echo
echo
echo "----- Generated PR body -----"
cat "$NOTES"
echo "----- End PR body -----"
rm -f "$NOTES"
