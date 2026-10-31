// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.ResolvedStream
import app.aria.domain.StreamSource
import app.aria.domain.StreamUnavailableException
import app.aria.domain.TrackId
import app.aria.domain.TrackSource
import app.aria.innertube.YouTube
import com.metrolist.innertubex.models.YouTubeClient

/**
 * Skeleton resolver: asks the ANDROID_MUSIC client for the player response and takes a direct audio
 * URL. Formats that need signature deciphering or a PoToken are not handled here; they surface as
 * StreamUnavailableException and are covered later by the playback sub-project.
 */
class YouTubeStreamSource(private val clock: () -> Long = System::currentTimeMillis) : StreamSource {
    override suspend fun resolve(id: TrackId): Result<ResolvedStream> = runCatching {
        require(id.source == TrackSource.YOUTUBE) { "Not a YouTube track: $id" }
        val response = YouTube.player(id.value, client = YouTubeClient.ANDROID_MUSIC).getOrThrow()
        val data = response.streamingData ?: throw StreamUnavailableException("No streaming data for $id")
        val best = data.bestAudio() ?: throw StreamUnavailableException("No direct audio URL for $id")
        ResolvedStream(
            url = best.url!!,
            mimeType = best.mimeType,
            bitrate = best.bitrate,
            contentLength = best.contentLength,
            expiresAtEpochMs = clock() + data.expiresInSeconds * 1000L,
        )
    }
}
