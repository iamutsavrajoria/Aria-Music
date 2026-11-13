// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.aria.data.entities.ArtistEntity
import app.aria.data.entities.Event
import app.aria.data.entities.SongArtistMap
import app.aria.data.entities.SongEntity

@Dao
abstract class PlayEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSong(song: SongEntity)

    @Insert
    protected abstract suspend fun insertEvent(event: Event)

    @Query("SELECT COUNT(*) FROM event WHERE songId = :songId")
    abstract suspend fun countEvents(songId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertArtist(artist: ArtistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSongArtist(map: SongArtistMap): Long

    @Transaction
    open suspend fun record(write: TrackWrite, event: Event) {
        insertSong(write.song)
        write.artists.forEachIndexed { i, a -> insertArtist(a); insertSongArtist(SongArtistMap(write.song.id, a.id, i)) }
        insertEvent(event)
    }
}
