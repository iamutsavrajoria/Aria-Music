// SPDX-License-Identifier: GPL-3.0-only
// Ported from Meld (GPL-3.0) app/src/main/kotlin/com/metrolist/music/utils/potoken/PoTokenGenerator.kt at fe9a666; see docs/provenance.csv.
// Changed: dispatcher and proxy parameters; the token pair (not the WebView) is cached; one WebView per cache miss,
// destroyed in finally; one 15 s timeout for the whole batch; no retry loop (every batch already starts from a fresh WebView).
package app.aria.innertube.potoken

import android.content.Context
import android.webkit.CookieManager
import app.aria.innertube.YouTube
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.net.Proxy
import java.time.Instant

/**
 * Safe to call from any thread: every WebView operation is moved to [dispatcher] (main) inside. One WebView exists at
 * a time ([lock]); it lives only for one batch (streaming token for the session, then the player token for the video).
 */
class PoTokenGenerator(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val proxyProvider: () -> Proxy? = { YouTube.proxy },
    private val clock: () -> Instant = { Instant.now() },
) {
    private val applicationContext = context.applicationContext ?: context

    @Volatile
    private var webViewSupported: Boolean? = null

    @Volatile
    private var webViewBadImpl = false // whether the system has a bad WebView implementation

    private class Cached(val videoId: String, val sessionId: String, val result: PoTokenResult, val expiresAt: Instant)

    private val lock = Mutex()

    // The last token pair and its expiry. The WebView is never kept.
    @Volatile
    private var cached: Cached? = null

    @Volatile
    private var active: PoTokenWebView? = null

    /** Returns null when the system WebView is unavailable or broken; throws [PoTokenException] on failure or timeout. */
    suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? = lock.withLock {
        cached?.let {
            if (it.videoId == videoId && it.sessionId == sessionId && clock().isBefore(it.expiresAt)) return@withLock it.result
        }
        if (webViewBadImpl || !isWebViewSupported()) {
            Timber.tag(TAG).d("WebView not available")
            return@withLock null
        }
        try {
            // A hung BotGuard challenge or a culled WebView process must fail fast so the extractor falls through to
            // clients that need no token.
            withTimeoutOrNull(BATCH_TIMEOUT_MS) { generateBatch(videoId, sessionId) }
                ?: run {
                    Timber.tag(TAG).w("poToken generation timed out after ${BATCH_TIMEOUT_MS}ms")
                    throw PoTokenException("poToken generation timed out after ${BATCH_TIMEOUT_MS}ms")
                }
        } catch (e: BadWebViewException) {
            Timber.tag(TAG).e("Could not obtain PO token because WebView is unavailable")
            webViewBadImpl = true
            null
        }
    }

    /** Drops the cached tokens and destroys a WebView that is mid-batch, if any. */
    fun close() {
        cached = null
        active?.close()
    }

    private suspend fun isWebViewSupported(): Boolean {
        webViewSupported?.let { return it }
        // CookieManager.getInstance() throws when the device has no usable WebView provider.
        val supported = withContext(dispatcher) { runCatching { CookieManager.getInstance() }.isSuccess }
        webViewSupported = supported
        return supported
    }

    private suspend fun generateBatch(videoId: String, sessionId: String): PoTokenResult {
        val webView = PoTokenWebView.create(applicationContext, dispatcher, proxyProvider)
        active = webView
        try {
            // The streaming poToken needs to be generated exactly once before generating any other (player) tokens.
            val streamingPot = webView.generatePoToken(sessionId)
            val playerPot = webView.generatePoToken(videoId)
            val result = PoTokenResult(playerRequestPoToken = streamingPot, streamingDataPoToken = playerPot)
            cached = Cached(videoId, sessionId, result, webView.expiration)
            Timber.tag(TAG).d("PO token generated successfully")
            return result
        } finally {
            active = null
            withContext(NonCancellable + dispatcher) { webView.close() }
        }
    }

    private companion object {
        const val TAG = "PoTokenGenerator"

        // The whole batch (WebView start, two BotGuard requests, two tokens) must finish within this; the extraction
        // engine's own bound is 20 s.
        const val BATCH_TIMEOUT_MS = 15_000L
    }
}
