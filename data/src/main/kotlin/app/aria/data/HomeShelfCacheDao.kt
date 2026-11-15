// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.aria.data.entities.HomeShelfCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HomeShelfCacheDao {
    @Query("SELECT * FROM home_shelf_cache ORDER BY position") abstract fun observe(): Flow<List<HomeShelfCacheEntity>>
    @Query("DELETE FROM home_shelf_cache") protected abstract suspend fun clear()
    @Insert protected abstract suspend fun insert(rows: List<HomeShelfCacheEntity>)
    @Query("SELECT * FROM home_shelf_cache ORDER BY position") protected abstract suspend fun snapshot(): List<HomeShelfCacheEntity>

    @Transaction open suspend fun replaceAll(rows: List<HomeShelfCacheEntity>) { clear(); insert(rows) }

    /**
     * Drops every row only if the table still holds exactly [expected] (checked and cleared in one transaction).
     * Slot keys are reused, so a reader that saw corrupt rows must not wipe a newer [replaceAll] that landed meanwhile.
     */
    @Transaction open suspend fun clearIfUnchanged(expected: List<HomeShelfCacheEntity>): Boolean {
        if (snapshot() != expected) return false
        clear()
        return true
    }
}
