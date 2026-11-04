// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per queue entry. [position] is play order; [originalPosition] is insertion order. */
@Entity(tableName = "queue_item")
data class QueueItemEntity(
    @PrimaryKey val queueId: Long,
    val position: Int,
    val originalPosition: Int,
    val trackId: String,
    val title: String,
    /** Artists joined with U+001F (unit separator). */
    val artists: String,
    val durationMs: Long?,
    val thumbnailUrl: String?,
)

/** Single row, id always 0. */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    val shuffle: Boolean,
    val repeat: String,
    val nextQueueId: Long,
    val currentIndex: Int,
    val positionMs: Long,
)
