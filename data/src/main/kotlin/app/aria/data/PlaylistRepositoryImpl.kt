// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.data.entities.PlaylistEntity
import app.aria.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class PlaylistRepositoryImpl(
    private val dao: PlaylistDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : PlaylistRepository {
    private fun now(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(clock()), ZoneOffset.UTC)

    private fun requireName(name: String) = name.trim().also { require(it.isNotEmpty()) { "playlist name must not be blank" } }

    override fun playlists(query: String, sort: LibrarySort): Flow<List<PlaylistRef>> =
        dao.playlists(query.escapeLike(), if (sort == LibrarySort.TITLE) 1 else 0).map { rows -> rows.map { it.toRef() } }
    override fun playlist(id: String): Flow<PlaylistRef?> = dao.playlist(id).map { it?.toRef() }
    override fun tracks(playlistId: String): Flow<List<PlaylistTrackRow>> = dao.entries(playlistId).map { rows ->
        rows.mapNotNull { r ->
            SongRow(r.id, r.title, r.duration, r.thumbnailUrl, r.liked, r.artistNames).toTrack()?.let { PlaylistTrackRow(r.entryId, it) }
        }
    }

    override suspend fun create(name: String): String {
        val entity = PlaylistEntity(name = requireName(name), createdAt = now(), lastUpdateTime = now(), isEditable = true, isLocal = true)
        dao.insertPlaylist(entity); return entity.id
    }
    override suspend fun rename(id: String, name: String) = dao.rename(id, requireName(name), now())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun addTracks(id: String, tracks: List<Track>) = dao.addTracks(id, tracks.map { it.toWrite() })
    override suspend fun removeEntry(playlistId: String, entryId: Int) = dao.deleteEntry(playlistId, entryId)
    override suspend fun reorder(playlistId: String, orderedEntryIds: List<Int>) = dao.reorder(playlistId, orderedEntryIds)
}
