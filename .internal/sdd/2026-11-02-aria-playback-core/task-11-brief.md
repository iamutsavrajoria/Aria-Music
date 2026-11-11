### Task 11: WebView PoToken generator (port, confined)

Spec section 4 "WebView confinement" (branch 3, security, ADR-0002).

**Files:**
- Create: `innertube/src/main/kotlin/app/aria/innertube/potoken/{PoTokenWebView,PoTokenGenerator,JavaScriptUtil,PoTokenResult,PoTokenException,WebViewPoTokenSource,PoTokenValidator,NavigationPolicy}.kt`
- Create: `scripts/check-webview-safety.sh`, `scripts/test_check_webview_safety.py`
- Modify: `.github/workflows/ci.yml` (add the script to "Static checks"), `docs/provenance.csv`, `docs/data-flow.md`, `docs/providers/youtube-music.md`, `app/proguard-rules.pro`
- Test: `innertube/src/test/kotlin/app/aria/innertube/potoken/{PoTokenValidatorTest,NavigationPolicyTest}.kt`, `scripts/test_check_webview_safety.py`

**Interfaces:**
- Consumes: Task 10 `PoTokenSource`, `PoTokens`.
- Produces: `class WebViewPoTokenSource(context: Context) : PoTokenSource`; `object PoTokenValidator { fun isValid(token: String): Boolean }`; `object NavigationPolicy { fun isAllowed(url: String): Boolean }`.

**Acceptance Criteria:**
- Ported from Meld's `potoken/` package; upstream license read and recorded; ledger row present.
- The WebView is never attached to a window; `allowFileAccess`, `allowContentAccess`, geolocation, and `allowFileAccessFromFileURLs`/`allowUniversalAccessFromFileURLs` are off; cookies are not shared with other sessions (cookie manager untouched, `setAcceptThirdPartyCookies(false)`); navigation and subresource requests go through `NavigationPolicy` and anything else is cancelled or answered with an empty response.
- Exactly one JavaScript-bridge object with one `@JavascriptInterface` method that receives only a string; every token passes `PoTokenValidator` (`[A-Za-z0-9_\-+/=]{20,4096}`) before use.
- The WebView is destroyed after each batch (token pair cached in the generator, not the WebView).
- (Stress-test branch 3) Every WebView operation (create, evaluate, destroy) runs on `Dispatchers.Main` via `withContext(dispatcher)`, where `PoTokenGenerator(dispatcher: CoroutineDispatcher = Dispatchers.Main)` takes the dispatcher as a parameter; a `Mutex` allows one WebView at a time; generation has its own 15 s timeout so a hung BotGuard challenge fails fast instead of waiting for the engine's 20 s bound.
- `scripts/check-webview-safety.sh` fails the build if `addJavascriptInterface`, `WebView(` construction or `setJavaScriptEnabled(true)`/`javaScriptEnabled = true` appears outside `innertube/src/main/kotlin/app/aria/innertube/potoken/PoTokenWebView.kt`, or if `allowFileAccess = true`/`setAllowFileAccess(true)` or `loadUrl("file:` appears anywhere.
- `docs/data-flow.md` states that BotGuard sends visitor data and a challenge request to Google and involves no Aria server.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.potoken

import org.junit.Assert.*
import org.junit.Test

class PoTokenValidatorTest {
    @Test fun acceptsBase64UrlTokens() = assertTrue(PoTokenValidator.isValid("MnQ3_abc-DEF0123456789=="))
    @Test fun rejectsShortLongAndOddCharacters() {
        assertFalse(PoTokenValidator.isValid("short")); assertFalse(PoTokenValidator.isValid("a".repeat(4097)))
        assertFalse(PoTokenValidator.isValid("abc def ghi jkl mno pqr")); assertFalse(PoTokenValidator.isValid("<script>alert(1)</script>"))
    }
}

class NavigationPolicyTest {
    @Test fun allowsOnlyHttpsOnAllowedHosts() {
        assertTrue(NavigationPolicy.isAllowed("https://www.youtube.com/api/jnn/v1/Create"))
        assertFalse(NavigationPolicy.isAllowed("http://www.youtube.com/"))
        assertFalse(NavigationPolicy.isAllowed("https://evil.example/"))
        assertFalse(NavigationPolicy.isAllowed("https://www.youtube.com.evil.example/"))
        assertFalse(NavigationPolicy.isAllowed("file:///data/data/app.aria.music/databases/aria.db"))
        assertFalse(NavigationPolicy.isAllowed("javascript:alert(1)"))
    }
}
```

`scripts/test_check_webview_safety.py` (pytest): builds a temp tree with `innertube/src/main/kotlin/app/aria/innertube/potoken/PoTokenWebView.kt` containing `addJavascriptInterface(` and runs the script (exit 0); then adds `app/src/Bad.kt` with `webView.addJavascriptInterface(` (exit 1 and the file name in the output); `allowFileAccess = true` in any file (exit 1); `loadUrl("file:///x")` (exit 1); a clean tree (exit 0). The script takes the repo root as `$1` (default `.`).

- [ ] **Step 2:** Push. Expected: FAIL.

- [ ] **Step 3: Implement.**

3.1 `scripts/check-webview-safety.sh`:

```bash
#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# WebView confinement (ADR-0002): the only WebView, JS bridge and JS enablement live in PoTokenWebView.kt.
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
check_forbidden() {
  while IFS= read -r f; do
    if grep -nE "$1" "$f" >/dev/null; then echo "FAIL: $2: ${f#"$root"/}"; fail=1; fi
  done < <(files)
}
check_confined 'addJavascriptInterface\(' 'addJavascriptInterface'
check_confined '[^A-Za-z]WebView\(' 'WebView construction'
check_confined 'setJavaScriptEnabled\(true\)|javaScriptEnabled *= *true' 'JavaScript enablement'
check_forbidden 'setAllowFileAccess\(true\)|allowFileAccess *= *true' 'file access enabled'
check_forbidden 'loadUrl\("file:' 'file: URL loaded'
exit $fail
```

Make it executable (`chmod +x`), add `scripts/check-webview-safety.sh` to the "Static checks" step in `ci.yml`.

3.2 **Port.** Start from `docs/spikes/2026-11-playback-apis.md` item 4 (file list, header, JS hosts). Read the license and the six files `PoTokenGenerator.kt`, `PoTokenWebView.kt`, `JavaScriptUtil.kt`, `PoTokenResult.kt`, `PoTokenException.kt` under `app/src/main/kotlin/com/metrolist/music/utils/potoken/` of `FrancescoGrazioso/Meld` at commit `fe9a666227ff3d6667075e65a4330518a724524b` via WebFetch (if a file is truncated, fetch the GitHub "raw" or "blame" view in pieces; do not retype from memory). Package `app.aria.innertube.potoken`; keep upstream headers, add SPDX (the five Kotlin files carry no upstream header, so add SPDX only); also copy `app/src/main/assets/po_token.html` (214 lines, spike item 4) to the owning module's assets, record MIT attribution for LuanRT/BgUtils (named in the HTML header) in `CREDITS.md` and the ledger, and include the HTML in the provenance row; Meld's `PoTokenWebView` uses `YouTube.proxy` and a hard-coded Google web API key and `REQUEST_KEY`: supply Aria's own proxy hook and record those constants in the provider record; replace Meld-specific imports (`NewPipeExtractor`/`Timber` use is fine if the dependency exists; otherwise use `timber`, which `:innertube` already has); add the provenance row for the directory (`innertube/src/main/kotlin/app/aria/innertube/potoken`).

3.3 **Hardening edits in `PoTokenWebView.kt`** (each is a checked acceptance item):
- `WebView(context)` is created inside `withContext(dispatcher)` (main), never added to a view hierarchy; all calls into it stay in that context, and the generator's public `suspend` functions are safe to call from the ExoPlayer loader thread.
- `settings.javaScriptEnabled = true` (needed by BotGuard), `allowFileAccess = false`, `allowContentAccess = false`, `allowFileAccessFromFileURLs = false`, `allowUniversalAccessFromFileURLs = false`, `setGeolocationEnabled(false)`, `domStorageEnabled` only if the ported JS needs it, `mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW`, `blockNetworkLoads = true` (spike: all traffic is OkHttp to `www.youtube.com`; the page makes no requests; `NavigationPolicy` allows only `about:blank` and the `https://www.youtube.com` base-URL document); `CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)`.
- `WebViewClient`: `shouldOverrideUrlLoading` returns `!NavigationPolicy.isAllowed(url)`; `shouldInterceptRequest` returns an empty `WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))` for disallowed URLs; `onReceivedSslError` always `handler.cancel()`.
- One bridge: `class Bridge { @JavascriptInterface fun onResult(value: String) }` registered under a fixed name; the ported JS posts the token string to it; Kotlin validates with `PoTokenValidator` before use. No app object is exposed.
- `destroy()`: `webView.stopLoading(); webView.loadUrl("about:blank"); webView.removeJavascriptInterface(NAME); webView.destroy()` after each batch.
- `NavigationPolicy.isAllowed(url)`: parse with `java.net.URI`; allowed iff scheme `https` and host in `setOf("www.youtube.com")`; extend the set **only** with hosts the ported JavaScript is observed to need, one line of justification per host in a comment. `about:blank` is allowed.
- `PoTokenValidator`: `Regex("^[A-Za-z0-9_\\-+/=]{20,4096}$")`.
- `PoTokenGenerator`: keeps the last token pair and its expiry (as Meld does) but **not** the WebView; each cache miss creates a WebView, generates the player-request and streaming tokens, destroys the WebView in `finally`.

3.4 `WebViewPoTokenSource(context)` implements `PoTokenSource` using the generator (`webClientPoToken(videoId, visitorData)` returns `PoTokens`); `close()` destroys any WebView.

3.5 Docs and R8: `docs/data-flow.md` row (YouTube BotGuard challenge: sent = visitor data and a challenge request; to = Google; when = first playback and when the token expires; no Aria server); `docs/providers/youtube-music.md`: replace "Known gap: stream resolution may require a PoToken…" with the generator description and the "stays open until the maintainer confirms playback on a device" note; `app/proguard-rules.pro`: `-keepclassmembers class app.aria.innertube.potoken.PoTokenWebView$Bridge { @android.webkit.JavascriptInterface <methods>; }`.

- [ ] **Step 4:** Push; `scripts` pytest, unit tests, lint (WebView lint warnings such as `SetJavaScriptEnabled` and `JavascriptInterface` need `@SuppressLint` with a one-line justification pointing to ADR-0002), both flavors build. Expected: PASS.
- [ ] **Step 5: Commit** `feat(innertube): confined WebView PoToken generator ported from Meld`.

