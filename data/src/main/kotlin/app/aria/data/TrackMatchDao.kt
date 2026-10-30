// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.aria.data.entities.TrackMatchEntity

@Dao
abstract class TrackMatchDao {
    @Query("SELECT * FROM track_match WHERE sourceProvider = :sp AND sourceId = :sid AND targetProvider = :tp")
    abstract suspend fun find(sp: String, sid: String, tp: String): TrackMatchEntity?

    @Query("SELECT * FROM track_match WHERE targetProvider = :tp AND targetId = :tid AND sourceProvider = :sp LIMIT 1")
    abstract suspend fun findReverse(tp: String, tid: String, sp: String): TrackMatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(entity: TrackMatchEntity)

    @Transaction
    open suspend fun upsertAutomatic(entity: TrackMatchEntity) {
        val existing = find(entity.sourceProvider, entity.sourceId, entity.targetProvider)
        if (existing?.isManualOverride == true) return
        insert(entity.copy(isManualOverride = false))
    }

    @Transaction
    open suspend fun upsertManual(entity: TrackMatchEntity) = insert(entity.copy(isManualOverride = true))
}
