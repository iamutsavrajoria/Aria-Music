// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.extraction

import app.aria.domain.ResolvedStream

data class ExtractedAudio(
    val url: String, val mimeType: String, val codecs: String?, val bitrate: Int, val contentLength: Long?,
    val expiresAtEpochMs: Long?, val loudnessDb: Double?, val client: String,
) {
    fun toResolvedStream(): ResolvedStream = ResolvedStream(
        url = url, mimeType = if (codecs.isNullOrBlank()) mimeType else "$mimeType; codecs=\"$codecs\"",
        bitrate = bitrate, contentLength = contentLength, expiresAtEpochMs = expiresAtEpochMs, loudnessDb = loudnessDb,
    )
}
