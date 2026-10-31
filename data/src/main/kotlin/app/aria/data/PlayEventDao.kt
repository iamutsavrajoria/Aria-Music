// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.aria.data.entities.Event
import app.aria.data.entities.SongEntity

@Dao
abstract class PlayEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSong(song: SongEntity)

    @Insert
    protected abstract suspend fun insertEvent(event: Event)

    @Query("SELECT COUNT(*) FROM event WHERE songId = :songId")
    abstract suspend fun countEvents(songId: String): Int

    @Transaction
    open suspend fun record(song: SongEntity, event: Event) {
        insertSong(song)
        insertEvent(event)
    }
}
