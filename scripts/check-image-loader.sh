#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Every remote image goes through the URL policy loader (UI shell decision 6). Code outside app/.../music/image/ must not reach
# the default Coil loader or build raw Coil composables: use AriaImage. No other exemptions.
# Self-tested by scripts/test_check_image_loader.py.
set -uo pipefail
root="${1:-.}"
if [ ! -d "$root" ] || [ ! -r "$root" ]; then
  echo "FAIL: scan root '$root' is missing or unreadable"; exit 1
fi
fail=0
pattern='SingletonImageLoader\.get\b|\.imageLoader\b|AsyncImage\(|rememberAsyncImagePainter'
while IFS= read -r line; do
  file="${line%%:*}"
  case "$file" in
    */app/src/main/kotlin/app/aria/music/image/*) continue;;
  esac
  echo "FAIL: only app/.../music/image/ may use the default Coil loader or raw AsyncImage (use AriaImage): ${line#"$root"/}"; fail=1
done < <(grep -rnE "$pattern" "$root" --include=*.kt --exclude-dir=build --exclude-dir=.git --exclude-dir=.internal)
exit $fail
