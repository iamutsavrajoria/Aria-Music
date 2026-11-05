// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.extraction

data class PoTokens(val playerRequestToken: String, val streamingDataToken: String, val visitorData: String)

/** Supplies web-client PoTokens. No implementation exists yet; extraction runs without one when null. */
interface PoTokenSource {
    suspend fun webClientPoToken(videoId: String, visitorData: String): PoTokens
    fun close()
}
