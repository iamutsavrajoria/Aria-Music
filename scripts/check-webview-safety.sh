#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# WebView confinement (ADR-0002): the only WebView, JS bridge and JS enablement live in PoTokenWebView.kt.
# Self-tested by scripts/test_check_webview_safety.py. Usage: check-webview-safety.sh [repo-root]
set -uo pipefail
root="${1:-.}"
allowed="innertube/src/main/kotlin/app/aria/innertube/potoken/PoTokenWebView.kt"
fail=0
files() { find "$root" -name '*.kt' -not -path '*/build/*' -not -path '*/.git/*' -not -path '*/.internal/*'; }
check_confined() { # pattern, label
  while IFS= read -r f; do
    rel="${f#"$root"/}"
    if [ "$rel" != "$allowed" ] && grep -nE "$1" "$f" >/dev/null; then echo "FAIL: $2 outside $allowed: $rel"; fail=1; fi
  done < <(files)
}
check_forbidden() { # pattern, label
  while IFS= read -r f; do
    if grep -nE "$1" "$f" >/dev/null; then echo "FAIL: $2: ${f#"$root"/}"; fail=1; fi
  done < <(files)
}
check_confined 'addJavascriptInterface\(' 'addJavascriptInterface'
check_confined '(^|[^A-Za-z0-9_])WebView\(' 'WebView construction'
check_confined 'setJavaScriptEnabled\(true\)|javaScriptEnabled *= *true' 'JavaScript enablement'
check_forbidden 'setAllowFileAccess\(true\)|allowFileAccess *= *true' 'file access enabled'
check_forbidden 'loadUrl\("file:' 'file: URL loaded'
exit $fail
