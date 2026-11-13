// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.aria.data.entities.PlaylistEntity
import app.aria.data.entities.PlaylistSongMap
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

private const val PLAYLIST_COLUMNS =
    "p.id AS id, p.name AS name, COALESCE(p.thumbnailUrl, (SELECT s.thumbnailUrl FROM playlist_song_map m JOIN song s ON s.id = m.songId " +
        "WHERE m.playlistId = p.id AND s.thumbnailUrl IS NOT NULL ORDER BY m.position LIMIT 1)) AS thumbnailUrl, " +
        "(SELECT COUNT(*) FROM playlist_song_map m WHERE m.playlistId = p.id) AS trackCount"

@Dao
abstract class PlaylistDao : TrackWriterDao() {
    @Query(
        "SELECT $PLAYLIST_COLUMNS FROM playlist p WHERE p.isLocal = 1 AND (:q = '' OR p.name LIKE '%' || :q || '%' ESCAPE '\\') " +
            "ORDER BY CASE WHEN :byTitle = 1 THEN LOWER(p.name) END ASC, p.createdAt DESC",
    )
    abstract fun playlists(q: String, byTitle: Int): Flow<List<PlaylistRow>>

    @Query("SELECT $PLAYLIST_COLUMNS FROM playlist p WHERE p.id = :id AND p.isLocal = 1") abstract fun playlist(id: String): Flow<PlaylistRow?>

    @Query("SELECT pm.id AS entryId, $SONG_ROW_COLUMNS FROM playlist_song_map pm JOIN song s ON s.id = pm.songId WHERE pm.playlistId = :id ORDER BY pm.position")
    abstract fun entries(id: String): Flow<List<PlaylistEntryRow>>

    @Insert abstract suspend fun insertPlaylist(playlist: PlaylistEntity)
    @Query("UPDATE playlist SET name = :name, lastUpdateTime = :now WHERE id = :id AND isLocal = 1") abstract suspend fun rename(id: String, name: String, now: LocalDateTime)
    @Query("DELETE FROM playlist WHERE id = :id AND isLocal = 1") abstract suspend fun delete(id: String)
    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_song_map WHERE playlistId = :id") protected abstract suspend fun maxPosition(id: String): Int
    @Insert protected abstract suspend fun insertEntry(entry: PlaylistSongMap): Long
    @Query("DELETE FROM playlist_song_map WHERE id = :entryId AND playlistId = :playlistId") abstract suspend fun deleteEntry(playlistId: String, entryId: Int)
    @Query("SELECT id FROM playlist_song_map WHERE playlistId = :id ORDER BY position") protected abstract suspend fun entryIds(id: String): List<Int>
    @Query("UPDATE playlist_song_map SET position = :position WHERE id = :entryId AND playlistId = :playlistId")
    protected abstract suspend fun setPosition(playlistId: String, entryId: Int, position: Int)

    @Transaction
    open suspend fun addTracks(playlistId: String, writes: List<TrackWrite>) {
        var pos = maxPosition(playlistId) + 1
        for (w in writes) { ensure(w); insertEntry(PlaylistSongMap(playlistId = playlistId, songId = w.song.id, position = pos++)) }
    }

    /** Throws before writing anything when [ordered] is not exactly the current entry ids; the whole rewrite is one transaction. */
    @Transaction
    open suspend fun reorder(playlistId: String, ordered: List<Int>) {
        val current = entryIds(playlistId)
        require(ordered.size == current.size && ordered.sorted() == current.sorted()) { "entry ids do not match the playlist" }
        ordered.forEachIndexed { i, id -> setPosition(playlistId, id, i) }
    }
}
