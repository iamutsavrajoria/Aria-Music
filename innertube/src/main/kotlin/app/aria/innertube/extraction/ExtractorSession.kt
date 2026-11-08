// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.extraction

import app.aria.innertube.YouTube
import app.aria.innertube.cipher.AriaPlayerConfigRepository
import com.metrolist.innertubex.InnerTubeLogEvent
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Owns the InnerTubeX extraction objects, rebuilt whenever the YouTube transport is replaced. Player-config
 * refresh stays off: [configRepository] is the disabled-mode repository, so no remote config store is built.
 */
class ExtractorSession(
    private val poTokens: PoTokenSource?,
    private val configRepository: PlayerConfigRepository,
) {
    private class Bundle(val generation: Long, val cipherService: YouTubeCipherService, val extractor: InnerTubeExtractor)

    @Volatile private var current: Bundle? = null
    private val mutex = Mutex()

    fun audioExtractor(): AudioExtractor = InnerTubeXAudioExtractor(this)

    internal suspend fun extractor(): InnerTubeExtractor {
        val transport = YouTube.extractionTransport()
        current?.takeIf { it.generation == transport.generation }?.let { return it.extractor }
        return mutex.withLock {
            val latest = YouTube.extractionTransport()
            current?.takeIf { it.generation == latest.generation }?.let { return@withLock it.extractor }
            try {
                current?.cipherService?.dispose()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w("old cipher disposal failed: %s", e::class.simpleName)
            }
            // Disabled mode (Aria's default): no RemotePlayerConfigStore, so the library never fetches a player config.
            val store: RemotePlayerConfigStore? =
                if (configRepository.enabled) RemotePlayerConfigStore(latest.httpClient, configRepository, logger) else null
            val cipherService = YouTubeCipherService(latest.httpClient, store, logger)
            val extractor = InnerTubeExtractor(
                configParser = YtConfigParserImpl(latest.httpClient, latest.innerTube, store, logger).withEmbeddedConfigFallback(),
                cipherService = cipherService,
                innerTube = latest.innerTube,
                tokenProvider = tokenProvider(),
                logger = logger,
            )
            Bundle(latest.generation, cipherService, extractor).also { current = it }.extractor
        }
    }

    private fun tokenProvider(): TokenProvider? {
        val source = poTokens ?: return null
        return object : TokenProvider {
            override val capabilities = TokenProviderCapabilities(
                providers = setOf(PoTokenProviderKind.WEB_BOTGUARD),
                usesWebView = true,
            )

            override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? =
                source.webClientPoToken(videoId, visitorData).let {
                    PoTokenResult(it.playerRequestToken, it.streamingDataToken, it.visitorData)
                }

            override suspend fun close() = source.close()
        }
    }

    /** Verbatim from Meld InnerTubeXPlayer.kt (fe9a666): falls back to the embed page when the watch page fails. */
    private fun YtConfigParser.withEmbeddedConfigFallback(): YtConfigParser =
        object : YtConfigParser by this {
            override suspend fun fetchConfig(videoId: String, useLoginCookies: Boolean) =
                try {
                    this@withEmbeddedConfigFallback.fetchConfig(videoId, useLoginCookies)
                } catch (_: IllegalStateException) {
                    this@withEmbeddedConfigFallback.fetchEmbeddedConfig(videoId, useLoginCookies = false)
                }
        }

    companion object {
        private const val TAG = "ExtractorSession"

        private val logger = InnerTubeLogger { event: InnerTubeLogEvent ->
            // event.details carries URLs and other request values, so only the message is forwarded.
            val message = event.message
            when (event.level) {
                InnerTubeLogLevel.DEBUG -> Timber.tag(event.tag).d(message)
                InnerTubeLogLevel.INFO -> Timber.tag(event.tag).i(message)
                InnerTubeLogLevel.WARN -> Timber.tag(event.tag).w(message)
                InnerTubeLogLevel.ERROR -> Timber.tag(event.tag).e(message)
            }
        }

        fun createDefault(poTokens: PoTokenSource?): ExtractorSession =
            ExtractorSession(poTokens, AriaPlayerConfigRepository(null))
    }
}
