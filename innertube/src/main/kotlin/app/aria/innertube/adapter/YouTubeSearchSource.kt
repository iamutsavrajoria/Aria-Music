// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.SearchSource
import app.aria.domain.Track
import app.aria.innertube.YouTube
import app.aria.innertube.models.SongItem

class YouTubeSearchSource : SearchSource {
    override suspend fun searchTracks(query: String): Result<List<Track>> =
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).map { result ->
            result.items.filterIsInstance<SongItem>().map { it.toTrack() }
        }
}
