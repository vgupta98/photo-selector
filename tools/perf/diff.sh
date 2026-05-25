#!/usr/bin/env bash
# Diff two JMH JSON result files. Usage:
#   tools/perf/diff.sh <baseline.json> <feature.json>
#
# Prints one row per benchmark with: name, baseline mean, feature mean, %delta.
# A positive %delta means the feature ran SLOWER than baseline (worse).

set -euo pipefail

if [ $# -ne 2 ]; then
    echo "usage: $0 <baseline.json> <feature.json>" >&2
    exit 1
fi

BASELINE="$1"
FEATURE="$2"

if [ ! -f "$BASELINE" ] || [ ! -f "$FEATURE" ]; then
    echo "error: one or both result files missing" >&2
    exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
    echo "error: this script needs jq. brew install jq" >&2
    exit 1
fi

printf "%-60s  %12s  %12s  %10s  %s\n" "benchmark" "baseline" "feature" "delta" "unit"
printf -- "----------------------------------------------------------------------------------------------------------------\n"

jq -r '.[] | [.benchmark, .primaryMetric.score, .primaryMetric.scoreUnit] | @tsv' "$BASELINE" \
    | while IFS=$'\t' read -r name baseline_score unit; do
        feat_score=$(jq -r --arg name "$name" '.[] | select(.benchmark == $name) | .primaryMetric.score' "$FEATURE")
        if [ -z "$feat_score" ] || [ "$feat_score" = "null" ]; then
            printf "%-60s  %12s  %12s  %10s  %s\n" "$name" "$baseline_score" "(missing)" "-" "$unit"
            continue
        fi
        delta_pct=$(awk -v b="$baseline_score" -v f="$feat_score" 'BEGIN { if (b == 0) print "n/a"; else printf "%+.2f%%", (f - b) / b * 100 }')
        # Strip the long benchmark prefix for readability.
        short_name="${name#com.vishalgupta.photoselector.perf.}"
        printf "%-60s  %12.3f  %12.3f  %10s  %s\n" "$short_name" "$baseline_score" "$feat_score" "$delta_pct" "$unit"
    done
