# Task 11 report: confined WebView PoToken generator

Status: DONE_WITH_CONCERNS (nothing compiled or run in CI yet; sandbox has no Gradle).

## Implemented
- `innertube/src/main/kotlin/app/aria/innertube/potoken/`: PoTokenWebView, PoTokenGenerator, JavaScriptUtil, PoTokenResult, PoTokenException (all ported from Meld fe9a666, read from a real checkout of that commit), plus written for Aria: WebViewPoTokenSource, PoTokenValidator, NavigationPolicy.
- `innertube/src/main/assets/po_token.html`: verbatim Meld copy plus two JS comment lines (SPDX, BgUtils attribution).
- `scripts/check-webview-safety.sh` (+ `scripts/test_check_webview_safety.py`), wired into the CI "Static checks" step. Ran locally: passes on the repo, fails on a deliberate bad sample (WebView(, addJavascriptInterface(, loadUrl("file:), sample removed. 9 new pytest tests pass; whole `scripts` suite 22 passed.
- Docs: provenance rows (directory and HTML), CREDITS.md (Meld row, LuanRT/BgUtils MIT row), data-flow.md row, youtube-music.md (replaces "Known gap", records REQUEST_KEY, Google web API key, spoofed UA, "stays open until the maintainer confirms playback on a device"), PRIVACY.md sentence, proguard keep rule.

## Tests written, not run
`PoTokenValidatorTest`, `NavigationPolicyTest` (exactly the brief's cases, split into the two named files). Pytest run locally as above. Unit tests and lint run only in CI.

## Files changed
New: the 8 Kotlin files, po_token.html, 2 test files, check-webview-safety.sh, test_check_webview_safety.py. Modified: ci.yml, provenance.csv, CREDITS.md, PRIVACY.md, data-flow.md, youtube-music.md, app/proguard-rules.pro.

## Deviations from Meld (and why)
- Bridge: Meld exposed six @JavascriptInterface methods on the WebView object itself. Now one `Bridge.onResult(String)` on a separate class registered as `PoTokenBridge`; the page sends tagged strings (S start, B botguard response, E error, M minter ready, T token, X token error). Kotlin accepts a message only in the expected phase (state machine), caps size at 1M chars, never treats it as a URL or code. Request keys are a plain counter (Meld embedded the identifier in JS, an injection risk via visitor data).
- Tokens: decoded token must pass PoTokenValidator before it resumes the caller; input byte list capped.
- Settings: file/content access off, FileURL access off, geolocation off, mixed content never, `blockNetworkLoads = true` (as Meld; plan's `false` was P6), no cache, DOM storage left off, `setAcceptThirdPartyCookies(false)`. WebViewClient: shouldOverrideUrlLoading and shouldInterceptRequest via NavigationPolicy (403 empty response), SSL errors cancelled.
- HTTP: OkHttp + `YouTube.proxy` replaced by `HttpURLConnection` with an injectable `proxyProvider` (default `{ YouTube.proxy }`), because okhttp is not a declared dependency of :innertube. JSON bodies built with JsonArray instead of string concatenation. Proxy auth (`YouTube.proxyAuth`) is NOT applied: authenticated proxies will fail.
- okio base64 replaced by `java.util.Base64`; JavaScriptUtil functions made `internal`.
- Meld's `androidx.collection`/`androidx.annotation` use removed (not on classpath).
- Lifetime: Meld kept one WebView per session with retry loop; now one WebView per cache miss, destroyed in `finally` (stopLoading, about:blank, removeJavascriptInterface, removeAllViews, destroy). The generator caches the last token pair (key videoId+visitorData) until the integrity-token expiry (minus 10 min), not the WebView. Mutex serialises batches. One 15 s `withTimeoutOrNull` for the whole batch (Meld: 8 s generator + 45 s init + 15 s per token); per-call timeouts removed. Dispatcher parameter (default Main) used for create, evaluate, destroy. Console log of the botguard response removed.

## Concerns
1. Unverified by compiler: every line was checked mentally only. Likeliest CI trouble: lint (SuppressLint ids, deprecation of the FileURL setters, `WebViewClient` overrides), Kotlin inference in PoTokenWebView.
2. The page-start trigger (`PoTokenBridge.onResult("S")` appended before the first `</script>`) and the WebView behaviour with `blockNetworkLoads = true` mirror Meld but cannot be tested here; real-device playback confirmation is still required.
3. Cost: a fresh WebView plus two Google requests per uncached track (spec requires per-batch destroy), so first playback of each track pays a few seconds; cache only hits for the same video and visitor data.
4. `Dispatchers.Main` needs kotlinx-coroutines-android at runtime; :innertube does not declare it, but the app already uses `Dispatchers.Main.immediate`, so it resolves there.
5. LuanRT/BgUtils LICENSE text and copyright line not read first-hand (MIT stated via zemer-cipher README); CREDITS row flags it as a pre-beta gate. Meld has no per-file headers; repo is GPL-3.0 without "or later" (same position as before), compatible.
6. No JavaScriptUtil unit test was added (not in the brief).
