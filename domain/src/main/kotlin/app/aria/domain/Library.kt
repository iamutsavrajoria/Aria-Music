// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlinx.coroutines.flow.Flow

enum class LibrarySort { RECENTLY_ADDED, TITLE }

/** Slim row of the Library Songs list. */
data class LibrarySong(val track: Track, val liked: Boolean)

/** Local library (ADR-0003). Lists are Flows backed by SQL queries; [query] is a plain-text filter ("" = none). */
interface LibraryRepository {
    fun songs(query: String, sort: LibrarySort): Flow<List<LibrarySong>>
    fun albums(query: String, sort: LibrarySort): Flow<List<AlbumRef>>
    fun artists(query: String, sort: LibrarySort): Flow<List<ArtistRef>>
    fun isLiked(id: TrackId): Flow<Boolean>
    fun isInLibrary(id: TrackId): Flow<Boolean>
    fun isAlbumSaved(id: String): Flow<Boolean>
    fun isArtistFollowed(id: String): Flow<Boolean>
    /** Upserts the song; liking also sets inLibrary, unliking keeps it. */
    suspend fun setLiked(track: Track, liked: Boolean)
    /** Upserts the song; removing from the library also clears the like. */
    suspend fun setInLibrary(track: Track, inLibrary: Boolean)
    suspend fun setAlbumSaved(album: AlbumRef, saved: Boolean)
    suspend fun setArtistFollowed(artist: ArtistRef, followed: Boolean)
}

/** One playlist entry; [entryId] identifies the row (the same track may appear twice). */
data class PlaylistTrackRow(val entryId: Int, val track: Track)

/** User-created playlists (local only). */
interface PlaylistRepository {
    fun playlists(query: String, sort: LibrarySort): Flow<List<PlaylistRef>>
    fun playlist(id: String): Flow<PlaylistRef?>
    fun tracks(playlistId: String): Flow<List<PlaylistTrackRow>>
    /** [name] is trimmed; a blank name throws IllegalArgumentException. */
    suspend fun create(name: String): String
    /** [name] is trimmed; a blank name throws IllegalArgumentException and nothing changes. */
    suspend fun rename(id: String, name: String)
    suspend fun delete(id: String)
    suspend fun addTracks(id: String, tracks: List<Track>)
    suspend fun removeEntry(playlistId: String, entryId: Int)
    /** Rewrites positions in one transaction. [orderedEntryIds] must be exactly the playlist's current entry ids, else IllegalArgumentException and nothing changes. */
    suspend fun reorder(playlistId: String, orderedEntryIds: List<Int>)
}
