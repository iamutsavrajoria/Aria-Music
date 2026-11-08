#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# WebView confinement (ADR-0002): the only WebView, JS bridge and JS enablement live in PoTokenWebView.kt.
# Self-tested by scripts/test_check_webview_safety.py. Usage: check-webview-safety.sh [repo-root]
set -uo pipefail
root="${1:-.}"
allowed="innertube/src/main/kotlin/app/aria/innertube/potoken/PoTokenWebView.kt"
fail=0
files() { find "$root" \( -name '*.kt' -o -name '*.java' \) -not -path '*/build/*' -not -path '*/.git/*' -not -path '*/.internal/*'; }
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
layouts() { find "$root" -name '*.xml' -path '*/res/layout*' -not -path '*/build/*' -not -path '*/.git/*' -not -path '*/.internal/*'; }
# Positive checks on the one confined file. Comment lines do not count, so a commented-out setting cannot satisfy a rule.
allowed_file="$root/$allowed"
code_lines() { grep -vE '^[[:space:]]*(//|/\*|\*)' "$allowed_file"; }
require() { # pattern, label (reads into a variable: grep -q on a pipe would trip pipefail via SIGPIPE)
  local code; code=$(code_lines)
  if ! grep -qE "$1" <<<"$code"; then echo "FAIL: $2 missing in $allowed"; fail=1; fi
}
check_confined 'addJavascriptInterface\(' 'addJavascriptInterface'
check_confined '(^|[^A-Za-z0-9_])WebView\(' 'WebView construction'
check_confined 'setJavaScriptEnabled\(true\)|javaScriptEnabled *= *true' 'JavaScript enablement'
check_forbidden 'setAllowFileAccess\(true\)|allowFileAccess *= *true' 'file access enabled'
check_forbidden 'loadUrl\("file:' 'file: URL loaded'

while IFS= read -r f; do echo "FAIL: WebView declared in a layout: ${f#"$root"/}"; fail=1; done < <(layouts | xargs -r grep -lE '<(android\.webkit\.)?WebView([[:space:]>/]|$)')
check_confined '@JavascriptInterface' '@JavascriptInterface method'
check_forbidden 'setAllowFileAccessFromFileURLs\(true\)|allowFileAccessFromFileURLs *= *true' 'file-URL file access enabled'
check_forbidden 'setAllowUniversalAccessFromFileURLs\(true\)|allowUniversalAccessFromFileURLs *= *true' 'universal access from file URLs enabled'
if [ ! -f "$allowed_file" ]; then
  echo "FAIL: confined WebView file missing: $allowed"; fail=1
else
  require 'blockNetworkLoads *= *true|setBlockNetworkLoads\(true\)' 'blockNetworkLoads = true'
  require 'allowContentAccess *= *false|setAllowContentAccess\(false\)' 'allowContentAccess = false'
  require 'mixedContentMode *= *WebSettings\.MIXED_CONTENT_NEVER_ALLOW|setMixedContentMode\(WebSettings\.MIXED_CONTENT_NEVER_ALLOW\)' 'MIXED_CONTENT_NEVER_ALLOW'
  require 'NavigationPolicy\.isAllowed\(' 'NavigationPolicy.isAllowed'
  bridges=$(code_lines | grep -cE '^[[:space:]]*@JavascriptInterface')
  if [ "$bridges" -ne 1 ]; then echo "FAIL: expected exactly one @JavascriptInterface method in $allowed, found $bridges"; fail=1; fi
fi
exit $fail
