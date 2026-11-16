#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Screens depend on :domain interfaces only. :data and :innertube implementations are wired in app/.../di/ alone;
# Matches any mention (imports with alias/wildcard/indent/CRLF/trailing junk, and fully-qualified references).
# UI code reaches playback through PlayerController, never the engine. Self-tested by scripts/test_check_screen_imports.py.
set -uo pipefail
root="${1:-.}"
base="$root/app/src/main/kotlin"
[ -d "$base" ] || exit 0
fail=0
while IFS= read -r line; do
  case "$line" in */app/aria/music/di/*) continue;; esac
  echo "FAIL: only the di package may import :data or :innertube: ${line#"$root"/}"; fail=1
done < <(grep -rnE '\bapp\.aria\.(data|innertube)\b' "$base" --include=*.kt 2>/dev/null)
if [ -d "$base/app/aria/music/ui" ]; then
  while IFS= read -r line; do
    echo "FAIL: UI code must use PlayerController, not the engine: ${line#"$root"/}"; fail=1
  done < <(grep -rnE '\bapp\.aria\.(playback\b|domain\.(PlaybackEngine\b|\*))' "$base/app/aria/music/ui" --include=*.kt 2>/dev/null)
fi
exit $fail
