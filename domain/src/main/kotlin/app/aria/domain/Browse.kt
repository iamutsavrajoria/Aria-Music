// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

data class AlbumRef(val id: String, val title: String, val artists: List<String>, val year: Int?, val thumbnailUrl: String?)
data class ArtistRef(val id: String, val name: String, val thumbnailUrl: String?)
data class PlaylistRef(val id: String, val title: String, val subtitle: String?, val thumbnailUrl: String?, val trackCount: Int?)

/** One tile or row of a shelf, search section or artist page. */
sealed interface BrowseItem {
    /** Stable, unique within a list; used as the LazyColumn/LazyRow key. */
    val key: String

    data class Song(val track: Track) : BrowseItem { override val key: String get() = "song:${track.id.raw}" }
    data class Album(val album: AlbumRef) : BrowseItem { override val key: String get() = "album:${album.id}" }
    data class Artist(val artist: ArtistRef) : BrowseItem { override val key: String get() = "artist:${artist.id}" }
    data class Playlist(val playlist: PlaylistRef) : BrowseItem { override val key: String get() = "playlist:${playlist.id}" }
}

data class Shelf(val title: String, val subtitle: String?, val items: List<BrowseItem>)

/** A remote shelf that parsed, or one that failed on its own ("section failed" state). */
sealed interface ShelfState {
    data class Ready(val shelf: Shelf) : ShelfState
    data class Failed(val title: String?) : ShelfState
}

data class AlbumDetail(val album: AlbumRef, val tracks: List<Track>, val otherVersions: List<AlbumRef>)

data class ArtistDetail(val artist: ArtistRef, val description: String?, val subscriberText: String?, val shelves: List<Shelf>) {
    /** Songs of the first shelf that has any; what "Play" and "Shuffle" on the artist page use. */
    val topTracks: List<Track>
        get() = shelves.firstNotNullOfOrNull { s ->
            s.items.filterIsInstance<BrowseItem.Song>().map { it.track }.takeIf { it.isNotEmpty() }
        } ?: emptyList()
}

data class PlaylistDetail(val playlist: PlaylistRef, val tracks: List<Track>, val continuation: String?)
data class TrackPage(val tracks: List<Track>, val continuation: String?)

/** Remote browse data. Implemented in :innertube. Failures are returned, never thrown. */
interface BrowseSource {
    suspend fun home(): Result<List<ShelfState>>
    suspend fun album(id: String): Result<AlbumDetail>
    suspend fun artist(id: String): Result<ArtistDetail>
    suspend fun playlist(id: String): Result<PlaylistDetail>
    suspend fun playlistMore(continuation: String): Result<TrackPage>
}
