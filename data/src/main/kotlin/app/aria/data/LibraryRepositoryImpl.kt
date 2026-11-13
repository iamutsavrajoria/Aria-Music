// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.data.entities.AlbumEntity
import app.aria.data.entities.ArtistEntity
import app.aria.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class LibraryRepositoryImpl(
    private val dao: LibraryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : LibraryRepository {
    private fun now(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(clock()), ZoneOffset.UTC)
    private fun LibrarySort.flag() = if (this == LibrarySort.TITLE) 1 else 0

    override fun songs(query: String, sort: LibrarySort): Flow<List<LibrarySong>> =
        dao.songs(query.escapeLike(), sort.flag()).map { rows -> rows.mapNotNull { it.toLibrarySong() } }
    override fun albums(query: String, sort: LibrarySort): Flow<List<AlbumRef>> =
        dao.albums(query.escapeLike(), sort.flag()).map { rows -> rows.map { it.toRef() } }
    override fun artists(query: String, sort: LibrarySort): Flow<List<ArtistRef>> =
        dao.artists(query.escapeLike(), sort.flag()).map { rows -> rows.map { it.toRef() } }
    override fun isLiked(id: TrackId) = dao.isLiked(id.raw)
    override fun isInLibrary(id: TrackId) = dao.isInLibrary(id.raw)
    override fun isAlbumSaved(id: String) = dao.isAlbumSaved(id)
    override fun isArtistFollowed(id: String) = dao.isArtistFollowed(id)

    override suspend fun setLiked(track: Track, liked: Boolean) = dao.setLiked(track.toWrite(), liked, now())
    override suspend fun setInLibrary(track: Track, inLibrary: Boolean) = dao.setInLibrary(track.toWrite(), inLibrary, now())

    override suspend fun setAlbumSaved(album: AlbumRef, saved: Boolean) {
        if (!saved) { dao.removeAlbum(album.id); return }
        val entity = AlbumEntity(
            id = album.id, title = album.title, year = album.year, thumbnailUrl = album.thumbnailUrl,
            songCount = 0, duration = 0, inLibrary = now(),
        )
        dao.saveAlbum(entity, album.artists.filter { it.isNotBlank() }.map { ArtistEntity(id = artistIdForName(it), name = it.trim()) })
    }

    override suspend fun setArtistFollowed(artist: ArtistRef, followed: Boolean) {
        if (followed) dao.upsertArtist(ArtistEntity(id = artist.id, name = artist.name, thumbnailUrl = artist.thumbnailUrl, bookmarkedAt = now()))
        else dao.unfollowArtist(artist.id)
    }
}
