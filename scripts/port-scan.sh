#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Lists imports in ported code that still point into the source projects, and fails above a threshold.
# usage: scripts/port-scan.sh <dir> [max-distinct=10]
set -euo pipefail
dir=${1:?usage: port-scan.sh <dir> [max-distinct]}
max=${2:-10}
foreign='^(echo\.music|com\.music\.(echo|innertube)|com\.metrolist\.(music|spotify|innertube\b))'
found=$(grep -rhoE '^import [A-Za-z0-9_.]+' "$dir" --include=*.kt | sed 's/^import //' | grep -E "$foreign" | sort -u || true)
count=$(printf '%s' "$found" | grep -c . || true)
[ -n "$found" ] && printf '%s\n' "$found"
echo "distinct foreign imports: $count (limit $max)"
if [ "$count" -gt "$max" ]; then echo "STOP: scope is larger than planned; report to the maintainer" >&2; exit 1; fi
