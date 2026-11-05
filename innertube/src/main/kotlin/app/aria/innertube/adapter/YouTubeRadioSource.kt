// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.RadioSource
import app.aria.domain.Track
import app.aria.domain.TrackSource
import app.aria.innertube.YouTube
import app.aria.innertube.models.WatchEndpoint
import app.aria.innertube.pages.NextResult

class YouTubeRadioSource(
    private val next: suspend (WatchEndpoint) -> Result<NextResult> = { YouTube.next(it) },
) : RadioSource {
    override suspend fun related(seed: Track, limit: Int): Result<List<Track>> = runCatching {
        require(seed.id.source == TrackSource.YOUTUBE) { "Radio needs a YouTube seed" }
        val id = seed.id.value
        next(WatchEndpoint(videoId = id, playlistId = "RDAMVM$id")).getOrThrow()
            .items.filter { it.id != id }.take(limit).map { it.toTrack() }
    }
}
