// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HomeLocalDao {
    @Query(
        "SELECT $SONG_ROW_COLUMNS FROM song s JOIN (SELECT songId, COUNT(*) AS plays, MAX(timestamp) AS lastAt FROM event GROUP BY songId) e " +
            "ON e.songId = s.id WHERE s.hideFromQuickPicks = 0 ORDER BY e.plays DESC, e.lastAt DESC LIMIT :limit",
    )
    abstract fun quickPicks(limit: Int): Flow<List<SongRow>>

    @Query(
        "SELECT $SONG_ROW_COLUMNS FROM song s JOIN (SELECT songId, MAX(timestamp) AS lastAt FROM event GROUP BY songId) e " +
            "ON e.songId = s.id ORDER BY e.lastAt DESC LIMIT :limit",
    )
    abstract fun recentlyPlayed(limit: Int): Flow<List<SongRow>>
}
