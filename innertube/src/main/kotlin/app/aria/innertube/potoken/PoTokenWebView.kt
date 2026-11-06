// SPDX-License-Identifier: GPL-3.0-only
// Ported from Meld (GPL-3.0) app/src/main/kotlin/com/metrolist/music/utils/potoken/PoTokenWebView.kt at fe9a666; see docs/provenance.csv.
// Changes for Aria's confinement design (ADR-0002): single string-only JavaScript bridge with a message protocol and
// phase checks, NavigationPolicy on navigation and subresource requests, hardened settings, dispatcher parameter,
// validated tokens, per-batch lifetime (the generator creates and destroys one instance per cache miss), and
// java.net HTTP with an injectable proxy hook instead of OkHttp and Meld's YouTube.proxy.
package app.aria.innertube.potoken

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The only class in the app that constructs a WebView, enables JavaScript or registers a JavaScript bridge;
 * `scripts/check-webview-safety.sh` enforces that. See docs/decisions/ADR-0002-playback-trust-boundaries.md.
 *
 * The WebView is never attached to a window, is created and used only on [dispatcher] (main), makes no network
 * loads itself, and is destroyed by [close] after each batch of tokens.
 */
// JavaScript is required by BotGuard; the bridge and the settings below are the confinement described in ADR-0002
// (hidden WebView, NavigationPolicy-only navigation, blockNetworkLoads, one string-only bridge, validated output).
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
// The two FileURL settings are deprecated since API 30 but are set to false explicitly on purpose.
@Suppress("DEPRECATION")
class PoTokenWebView private constructor(
    context: Context,
    private val dispatcher: CoroutineDispatcher,
    private val proxyProvider: () -> Proxy?,
    // to be used exactly once only during initialization!
    private val continuation: CancellableContinuation<PoTokenWebView>,
) {
    private val appContext = context.applicationContext ?: context
    private val webView = WebView(context)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // Guards the single-shot init continuation: initialization errors can arrive from several
    // paths (JS console "Uncaught", botguard request failure, renderer-gone) and a second resume
    // on a continuation throws IllegalStateException.
    private val initResumed = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    /**
     * Set when the renderer died. Callers check this to recreate immediately.
     */
    @Volatile
    var isDead: Boolean = false
        private set
    private val poTokenContinuations =
        Collections.synchronizedMap(HashMap<String, CancellableContinuation<String>>())

    // Makes each generatePoToken call's continuation key unique (see generatePoToken).
    private val requestCounter = AtomicLong()
    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        onInitializationErrorCloseAndCancel(t)
    }

    // Order of the messages the page may send through the bridge. Anything out of order is ignored.
    private val phaseLock = Any()
    private var phase = PHASE_NEW

    @Volatile
    private var expirationInstant: Instant = Instant.EPOCH

    /** When the integrity token behind this WebView's minter expires (with a safety margin); the generator caches until then. */
    val expiration: Instant get() = expirationInstant

    /** The one object exposed to page JavaScript. It receives a single string and nothing else is reachable. */
    class Bridge internal constructor(private val sink: (String) -> Unit) {
        @JavascriptInterface
        fun onResult(value: String) {
            sink(value)
        }
    }

    private val bridge = Bridge { onBridgeMessage(it) }

    //region Initialization
    init {
        val webViewSettings = webView.settings
        webViewSettings.javaScriptEnabled = true // needed by BotGuard; see class comment
        webViewSettings.userAgentString = USER_AGENT
        webViewSettings.blockNetworkLoads = true // the WebView does not need internet access
        webViewSettings.allowFileAccess = false
        webViewSettings.allowContentAccess = false
        webViewSettings.allowFileAccessFromFileURLs = false
        webViewSettings.allowUniversalAccessFromFileURLs = false
        webViewSettings.setGeolocationEnabled(false)
        webViewSettings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webViewSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        // The ported JavaScript does not use localStorage or sessionStorage, so domStorageEnabled stays at its default (off).

        // Cookies are not shared: the cookie manager is not otherwise touched and third-party cookies are refused.
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        // so that we can run async functions and get back the result
        webView.addJavascriptInterface(bridge, NAME)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                val msg = m.message()
                // Log all console messages for debugging
                when (m.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Timber.tag(TAG).e("JS: $msg")
                    ConsoleMessage.MessageLevel.WARNING -> Timber.tag(TAG).w("JS: $msg")
                    else -> Timber.tag(TAG).d("JS: $msg")
                }

                if (msg.contains("Uncaught")) {
                    val fmt = "\"$msg\", source: ${m.sourceId()} (${m.lineNumber()})"
                    if (initResumed.get()) {
                        // Post-init: our static HTML already executed fine, so an uncaught error
                        // here comes from Google's remotely-served botguard/minter JS: transient,
                        // NOT a BadWebViewException, which would permanently disable poTokens for
                        // the session in PoTokenGenerator (same rationale as onRenderProcessGone).
                        Timber.tag(TAG).e("Uncaught JavaScript error after initialization")
                        isDead = true
                        val exception = PoTokenException(fmt)
                        close()
                        popAllPoTokenContinuations().forEach { (_, cont) ->
                            runCatching { cont.resumeWithException(exception) }
                        }
                    } else {
                        val exception = BadWebViewException(fmt)
                        Timber.tag(TAG).e("This WebView implementation is unavailable")

                        onInitializationErrorCloseAndCancel(exception)
                        popAllPoTokenContinuations().forEach { (_, cont) ->
                            runCatching { cont.resumeWithException(exception) }
                        }
                    }
                }
                return super.onConsoleMessage(m)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !NavigationPolicy.isAllowed(request.url.toString())

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (NavigationPolicy.isAllowed(request.url.toString())) {
                    null
                } else {
                    WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
            }

            // API 26+ callback (minSdk is 26); on providers that don't deliver it the generator's batch
            // timeout carries the recovery.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                val didCrash = runCatching { detail.didCrash() }.getOrNull()
                Timber.tag(TAG).e("PoToken WebView render process gone (didCrash=$didCrash)")
                isDead = true
                // Transient (OOM kill under memory pressure), NOT a BadWebViewException: that
                // would permanently disable poTokens for the session in PoTokenGenerator.
                val exception = PoTokenException("WebView render process gone (didCrash=$didCrash)")
                onInitializationErrorCloseAndCancel(exception)
                popAllPoTokenContinuations().forEach { (_, cont) ->
                    runCatching { cont.resumeWithException(exception) }
                }
                // Consume the event so the framework doesn't kill the app process.
                return true
            }
        }
    }

    /**
     * Must be called right after instantiating [PoTokenWebView] to perform the actual
     * initialization. This will asynchronously go through all the steps needed to load BotGuard,
     * run it, and obtain an `integrityToken`.
     */
    private fun loadHtmlAndObtainBotguard() {
        Timber.tag(TAG).d("loadHtmlAndObtainBotguard() called")

        scope.launch(exceptionHandler) {
            val html = withContext(Dispatchers.IO) {
                appContext.assets.open("po_token.html").bufferedReader().use { it.readText() }
            }

            // sends the start message through the bridge when the page script has run
            val data = html.replaceFirst("</script>", "\n$NAME.onResult(\"$MSG_START\")</script>")
            webView.loadDataWithBaseURL("https://www.youtube.com", data, "text/html", "utf-8", null)
        }
    }

    /** Runs on [dispatcher] after the page announced itself. */
    private fun downloadAndRunBotguard() {
        Timber.tag(TAG).d("downloadAndRunBotguard() called")

        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/Create",
            JsonArray(listOf(JsonPrimitive(REQUEST_KEY))).toString(),
        ) { responseBody ->
            val parsedChallengeData = parseChallengeData(responseBody)
            webView.evaluateJavascript(
                """try {
                    data = $parsedChallengeData
                    runBotGuard(data).then(function (result) {
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $NAME.onResult("$MSG_BOTGUARD" + result.botguardResponse)
                    }, function (error) {
                        $NAME.onResult("$MSG_INIT_ERROR" + error + "\n" + error.stack)
                    })
                } catch (error) {
                    $NAME.onResult("$MSG_INIT_ERROR" + error + "\n" + error.stack)
                }""",
                null
            )
        }
    }

    /** Runs on [dispatcher] after the BotGuard run produced [botguardResponse]. */
    private fun onRunBotguardResult(botguardResponse: String) {
        Timber.tag(TAG).d("BotGuard response received")
        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/GenerateIT",
            JsonArray(listOf(JsonPrimitive(REQUEST_KEY), JsonPrimitive(botguardResponse))).toString(),
        ) { responseBody ->
            Timber.tag(TAG).d("GenerateIT response received")
            try {
                val (integrityToken, expirationTimeInSeconds) = parseIntegrityTokenData(responseBody)
                Timber.tag(TAG).d("Parsed integrity token; expires in $expirationTimeInSeconds sec")

                // leave 10 minutes of margin just to be sure
                expirationInstant = Instant.now().plusSeconds(expirationTimeInSeconds).minus(10, ChronoUnit.MINUTES)

                if (!advance(PHASE_BOTGUARD_DONE, PHASE_MINTER_REQUESTED)) return@makeBotguardServiceRequest

                // Store integrityToken and create the minter callback ONCE
                // NOTE: createPoTokenMinter is async, so we use .then()
                webView.evaluateJavascript(
                    """try {
                        this.integrityToken = $integrityToken
                        createPoTokenMinter(webPoSignalOutput, integrityToken).then(function() {
                            $NAME.onResult("$MSG_MINTER_READY")
                        }).catch(function(error) {
                            $NAME.onResult("$MSG_INIT_ERROR" + error + "\n" + (error.stack || ''))
                        })
                    } catch (error) {
                        $NAME.onResult("$MSG_INIT_ERROR" + error + "\n" + error.stack)
                    }""",
                    null
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e("Failed to parse integrity token data type=${e::class.simpleName ?: "unknown"}")
                onInitializationErrorCloseAndCancel(PoTokenException("parseIntegrityTokenData failed: ${e.message}"))
            }
        }
    }
    //endregion

    //region Bridge messages
    /**
     * Entry point for the single bridge method. Runs on the JavaBridge thread and treats the string as untrusted
     * (the page runs Google-supplied JavaScript): size-capped, tagged by its first character, accepted only in the
     * expected phase, never executed or used as a URL.
     */
    private fun onBridgeMessage(value: String) {
        if (closed.get() || value.isEmpty() || value.length > MAX_MESSAGE_CHARS) return
        val payload = value.substring(1)
        when (value[0]) {
            MSG_START -> if (advance(PHASE_NEW, PHASE_STARTED)) {
                scope.launch(exceptionHandler) { downloadAndRunBotguard() }
            }

            MSG_BOTGUARD -> if (advance(PHASE_STARTED, PHASE_BOTGUARD_DONE)) {
                scope.launch(exceptionHandler) { onRunBotguardResult(payload) }
            }

            MSG_INIT_ERROR -> if (currentPhase() < PHASE_READY) {
                Timber.tag(TAG).e("PO-token JavaScript initialization failed")
                onInitializationErrorCloseAndCancel(buildExceptionForJsError(payload))
            }

            MSG_MINTER_READY -> if (advance(PHASE_MINTER_REQUESTED, PHASE_READY)) {
                Timber.tag(TAG).d("poToken minter created successfully, initialization complete")
                if (initResumed.compareAndSet(false, true)) {
                    continuation.resume(this)
                }
            }

            MSG_TOKEN -> if (currentPhase() == PHASE_READY) onObtainPoTokenResult(payload)
            MSG_TOKEN_ERROR -> if (currentPhase() == PHASE_READY) onObtainPoTokenError(payload)
        }
    }

    private fun advance(from: Int, to: Int): Boolean = synchronized(phaseLock) {
        if (phase == from) {
            phase = to
            true
        } else {
            false
        }
    }

    private fun currentPhase(): Int = synchronized(phaseLock) { phase }
    //endregion

    //region Obtaining poTokens
    /**
     * Mints a token bound to [identifier]. The caller bounds the time (the generator applies one 15 s timeout to
     * the whole batch).
     */
    suspend fun generatePoToken(identifier: String): String {
        if (isDead || closed.get()) {
            // Fail fast: the generator's next batch creates a WebView from scratch.
            throw PoTokenException("PoToken WebView is dead/closed, instance must be recreated")
        }
        // Continuations are keyed by a per-call unique key: concurrent calls for the same identifier would otherwise
        // silently overwrite each other's continuation. The key is a plain counter, so it is safe to embed in JavaScript.
        val requestKey = requestCounter.incrementAndGet().toString()
        return withContext(dispatcher) {
            suspendCancellableCoroutine<String> { cont ->
                Timber.tag(TAG).d("PO-token generation requested")
                poTokenContinuations[requestKey] = cont
                cont.invokeOnCancellation { poTokenContinuations.remove(requestKey) }
                // The IIFE keeps requestKey/u8Identifier lexically captured per call: bare globals
                // here would let a concurrent call reassign them before this call's promise
                // resolves, delivering this token to the other call's continuation.
                webView.evaluateJavascript(
                    """(function() {
                        var requestKey = "$requestKey"
                        try {
                            var u8Identifier = ${stringToU8(identifier)}
                            obtainPoToken(u8Identifier).then(function(poTokenU8) {
                                $NAME.onResult("$MSG_TOKEN" + requestKey + "\n" + poTokenU8.join(","))
                            }).catch(function(error) {
                                $NAME.onResult("$MSG_TOKEN_ERROR" + requestKey + "\n" + error + "\n" + (error.stack || ''))
                            })
                        } catch (error) {
                            $NAME.onResult("$MSG_TOKEN_ERROR" + requestKey + "\n" + error + "\n" + error.stack)
                        }
                    })()""",
                    null
                )
            }
        }
    }

    /** Payload is "requestKey\nerror text". Always transient: the minter was already created successfully. */
    private fun onObtainPoTokenError(payload: String) {
        val newline = payload.indexOf('\n')
        if (newline < 0) return
        val requestKey = payload.substring(0, newline)
        val error = payload.substring(newline + 1)
        // Not buildExceptionForJsError: a "SyntaxError" here comes from Google's challenge/program data, not a broken
        // WebView engine, and the BadWebViewException mapping would permanently disable poTokens for the session.
        popPoTokenContinuation(requestKey)?.let { runCatching { it.resumeWithException(PoTokenException(error)) } }
    }

    /** Payload is "requestKey\nbyte,byte,...". The decoded token must pass [PoTokenValidator] before anyone sees it. */
    private fun onObtainPoTokenResult(payload: String) {
        val newline = payload.indexOf('\n')
        if (newline < 0) return
        val requestKey = payload.substring(0, newline)
        val poTokenU8 = payload.substring(newline + 1)
        Timber.tag(TAG).d("Encoded PO-token result received")
        val cont = popPoTokenContinuation(requestKey) ?: return
        val poToken = try {
            if (poTokenU8.length > MAX_TOKEN_BYTES_CHARS) throw PoTokenException("token too long")
            u8ToBase64(poTokenU8)
        } catch (t: Throwable) {
            runCatching { cont.resumeWithException(t) }
            return
        }
        if (!PoTokenValidator.isValid(poToken)) {
            runCatching { cont.resumeWithException(PoTokenException("PO token failed validation")) }
            return
        }
        Timber.tag(TAG).d("PO token decoded")
        runCatching { cont.resume(poToken) }
    }
    //endregion

    //region Handling multiple emitters
    private fun popPoTokenContinuation(identifier: String): CancellableContinuation<String>? {
        return poTokenContinuations.remove(identifier)
    }

    private fun popAllPoTokenContinuations(): Map<String, CancellableContinuation<String>> {
        synchronized(poTokenContinuations) {
            val result = HashMap(poTokenContinuations)
            poTokenContinuations.clear()
            return result
        }
    }
    //endregion

    //region Utils
    private fun makeBotguardServiceRequest(
        url: String,
        data: String,
        handleResponseBody: (String) -> Unit,
    ) {
        scope.launch(exceptionHandler) {
            val (httpCode, body) = withContext(Dispatchers.IO) { postJson(url, data) }
            // Treat an empty 200 body as a failure too: handleResponseBody would otherwise pass
            // "" to parseChallengeData/parseIntegrityTokenData and fail later with a murkier error.
            if (body.isNullOrEmpty()) {
                onInitializationErrorCloseAndCancel(PoTokenException("Invalid botguard response (code=$httpCode, empty body)"))
            } else {
                handleResponseBody(body)
            }
        }
    }

    /** All traffic of this class: Kotlin-side HTTPS POST to www.youtube.com through the injected proxy hook. No cookies are sent. */
    private fun postJson(url: String, data: String): Pair<Int, String?> {
        require(url.startsWith("https://") && NavigationPolicy.isAllowed(url)) { "URL not allowed" }
        val target = URL(url)
        val proxy = proxyProvider()
        val connection = (if (proxy != null) target.openConnection(proxy) else target.openConnection()) as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json+protobuf")
            connection.setRequestProperty("x-goog-api-key", GOOGLE_API_KEY)
            connection.setRequestProperty("x-user-agent", "grpc-web-javascript/0.1")
            connection.outputStream.use { it.write(data.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = if (code == 200) connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
            return code to body
        } finally {
            connection.disconnect()
        }
    }

    private fun onInitializationErrorCloseAndCancel(error: Throwable) {
        close()
        if (initResumed.compareAndSet(false, true)) {
            // The continuation may have been cancelled by the caller's timeout.
            runCatching { continuation.resumeWithException(error) }
        }
    }

    /** Safe from any thread: the WebView teardown runs on the main thread (or on [dispatcher] otherwise). */
    fun close() {
        if (!closed.compareAndSet(false, true)) return

        scope.cancel()
        val exception = PoTokenException("PoToken WebView closed")
        popAllPoTokenContinuations().forEach { (_, cont) -> runCatching { cont.resumeWithException(exception) } }

        // WebView methods must run on the thread that created the WebView (main), but some callers arrive
        // on the JavaBridge thread, so hop instead of letting it throw and leak the instance.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyWebView()
        } else {
            CoroutineScope(dispatcher).launch { destroyWebView() }
        }
    }

    private fun destroyWebView() {
        // After a render-process crash some WebView methods can throw: never let teardown crash.
        runCatching {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeJavascriptInterface(NAME)
            webView.removeAllViews()
            webView.destroy()
        }.onFailure { Timber.tag(TAG).w("PO-token WebView teardown failed type=${it::class.simpleName ?: "unknown"}") }
    }
    //endregion

    companion object {
        private const val TAG = "PoTokenWebView"

        // Constants from Meld (recorded in docs/providers/youtube-music.md): a Google web API key and the BotGuard request key.
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"

        /** Name the bridge is registered under in the page. */
        internal const val NAME = "PoTokenBridge"

        // Bridge message tags (first character of the string passed to Bridge.onResult).
        private const val MSG_START = 'S'
        private const val MSG_BOTGUARD = 'B'
        private const val MSG_INIT_ERROR = 'E'
        private const val MSG_MINTER_READY = 'M'
        private const val MSG_TOKEN = 'T'
        private const val MSG_TOKEN_ERROR = 'X'

        private const val PHASE_NEW = 0
        private const val PHASE_STARTED = 1
        private const val PHASE_BOTGUARD_DONE = 2
        private const val PHASE_MINTER_REQUESTED = 3
        private const val PHASE_READY = 4

        private const val MAX_MESSAGE_CHARS = 1_000_000

        // A token is about 110 to 128 bytes; 4 digits and a comma per byte is far above that and far below the cap.
        private const val MAX_TOKEN_BYTES_CHARS = 5_000
        private const val HTTP_TIMEOUT_MS = 10_000

        /** Creates the WebView on [dispatcher] and suspends until BotGuard and the minter are ready. The caller bounds the time. */
        suspend fun create(context: Context, dispatcher: CoroutineDispatcher, proxyProvider: () -> Proxy?): PoTokenWebView {
            var created: PoTokenWebView? = null
            try {
                return withContext(dispatcher) {
                    suspendCancellableCoroutine<PoTokenWebView> { cont ->
                        val potWv = PoTokenWebView(context, dispatcher, proxyProvider, cont)
                        created = potWv
                        potWv.loadHtmlAndObtainBotguard()
                    }
                }
            } catch (e: CancellationException) {
                // Caller cancelled or timed out: don't leak the half-initialized WebView.
                closeQuietly(created, dispatcher)
                throw e
            }
        }

        private suspend fun closeQuietly(potWv: PoTokenWebView?, dispatcher: CoroutineDispatcher) {
            if (potWv == null) return
            withContext(NonCancellable + dispatcher) {
                // Mark init resumed so a late JS/network callback can't resume a cancelled continuation.
                potWv.initResumed.set(true)
                potWv.close()
            }
        }
    }
}
