// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.*
import app.aria.innertube.YouTube
import app.aria.innertube.pages.*
import kotlin.coroutines.cancellation.CancellationException

class YouTubeBrowseSource(
    home: suspend () -> Result<HomePage> = { YouTube.home() },
    album: suspend (String) -> Result<AlbumPage> = { YouTube.album(it) },
    artist: suspend (String) -> Result<ArtistPage> = { YouTube.artist(it) },
    playlist: suspend (String) -> Result<PlaylistPage> = { YouTube.playlist(it) },
    playlistMore: suspend (String) -> Result<PlaylistContinuationPage> = { YouTube.playlistContinuation(it) },
) : BrowseSource {
    // Differently named copies: inside this class `album(id)` would otherwise resolve to the member function, not the lambda.
    private val fetchHome = home
    private val fetchAlbum = album
    private val fetchArtist = artist
    private val fetchPlaylist = playlist
    private val fetchPlaylistMore = playlistMore

    private suspend inline fun <T, R> call(block: suspend () -> Result<T>, map: (T) -> R): Result<R> = try {
        val value = block().getOrElse { return Result.failure(it) }
        Result.success(map(value)) // not mapCatching: that would swallow a CancellationException from the mapper
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    override suspend fun home() = call(fetchHome) { it.toShelfStates() }
    override suspend fun album(id: String) = call({ fetchAlbum(id) }) { it.toDetail() }
    override suspend fun artist(id: String) = call({ fetchArtist(id) }) { it.toDetail() }
    override suspend fun playlist(id: String) = call({ fetchPlaylist(id) }) { it.toDetail() }
    override suspend fun playlistMore(continuation: String) =
        call({ fetchPlaylistMore(continuation) }) { page -> TrackPage(page.songs.mapNotNull { it.toTrackOrNull() }, page.continuation) }
}
