// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.aria.data.entities.SearchHistory
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SearchHistoryDao {
    @Query("SELECT `query` FROM search_history ORDER BY id DESC LIMIT :limit") abstract fun recent(limit: Int): Flow<List<String>>
    @Query("DELETE FROM search_history WHERE `query` = :q") abstract suspend fun delete(q: String)
    @Query("DELETE FROM search_history") abstract suspend fun clear()
    @Insert protected abstract suspend fun insert(row: SearchHistory)
    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY id DESC LIMIT 50)") protected abstract suspend fun trim()

    @Transaction open suspend fun add(q: String) { delete(q); insert(SearchHistory(query = q)); trim() }
}
