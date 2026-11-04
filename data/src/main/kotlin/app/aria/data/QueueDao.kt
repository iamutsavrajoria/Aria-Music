// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.*
import app.aria.data.entities.QueueItemEntity
import app.aria.data.entities.QueueStateEntity

@Dao
abstract class QueueDao {
    @Query("DELETE FROM queue_item") protected abstract suspend fun clearItems()
    @Insert protected abstract suspend fun insertItems(items: List<QueueItemEntity>)
    @Upsert protected abstract suspend fun upsertState(state: QueueStateEntity)
    @Query("SELECT * FROM queue_state WHERE id = 0") abstract suspend fun state(): QueueStateEntity?
    @Query("SELECT * FROM queue_item ORDER BY position") abstract suspend fun items(): List<QueueItemEntity>
    @Query("UPDATE queue_state SET currentIndex = :index, positionMs = :positionMs WHERE id = 0")
    protected abstract suspend fun updatePosition(index: Int, positionMs: Long): Int

    @Transaction
    open suspend fun replace(items: List<QueueItemEntity>, state: QueueStateEntity) {
        clearItems(); insertItems(items); upsertState(state)
    }

    /** Position may arrive before the first structure save; keep it so it is not lost. */
    @Transaction
    open suspend fun savePosition(index: Int, positionMs: Long, fallback: QueueStateEntity) {
        if (updatePosition(index, positionMs) == 0) upsertState(fallback)
    }
}
