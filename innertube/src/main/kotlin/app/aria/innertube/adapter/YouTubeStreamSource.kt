// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.ResolvedStream
import app.aria.domain.StreamSource
import app.aria.domain.StreamUnavailableException
import app.aria.domain.TrackId
import app.aria.domain.TrackSource
import app.aria.innertube.extraction.AudioExtractor
import app.aria.innertube.extraction.ExtractorSession
import app.aria.innertube.extraction.FailedClientMemory

class YouTubeStreamSource(
    private val extractor: AudioExtractor,
    private val memory: FailedClientMemory,
    private val clock: () -> Long = System::currentTimeMillis,
) : StreamSource {
    private val lastClient = java.util.concurrent.ConcurrentHashMap<TrackId, String>()

    override suspend fun resolve(id: TrackId): Result<ResolvedStream> = runCatching {
        require(id.source == TrackSource.YOUTUBE) { "Not a YouTube track: $id" }
        val audio = extractor.extract(id.value, memory.failed(id.value))
        if (!audio.url.startsWith("https://")) throw StreamUnavailableException("Extractor returned a non-https stream URL")
        lastClient[id] = audio.client
        val stream = audio.toResolvedStream()
        stream.copy(expiresAtEpochMs = stream.expiresAtEpochMs ?: (clock() + DEFAULT_TTL_MS))
    }

    override fun markRejected(id: TrackId) {
        lastClient[id]?.let { memory.record(id.value, it) }
    }

    companion object {
        const val DEFAULT_TTL_MS = 300_000L
        fun create(session: ExtractorSession) =
            YouTubeStreamSource(session.audioExtractor(), FailedClientMemory(clock = System::currentTimeMillis))
    }
}
