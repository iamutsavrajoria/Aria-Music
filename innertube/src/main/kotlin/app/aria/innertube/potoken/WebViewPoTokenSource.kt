// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.potoken

import android.content.Context
import app.aria.innertube.extraction.PoTokenSource
import app.aria.innertube.extraction.PoTokens

/** Web-client PoTokens from a hidden, confined WebView (ADR-0002). Tokens bind to [visitorData] and the video. */
class WebViewPoTokenSource(context: Context) : PoTokenSource {
    private val generator = PoTokenGenerator(context)

    override suspend fun webClientPoToken(videoId: String, visitorData: String): PoTokens {
        val result = generator.getWebClientPoToken(videoId, visitorData)
            ?: throw PoTokenException("System WebView is unavailable")
        return PoTokens(
            playerRequestToken = result.playerRequestPoToken,
            streamingDataToken = result.streamingDataPoToken,
            visitorData = visitorData,
        )
    }

    override fun close() = generator.close()
}
