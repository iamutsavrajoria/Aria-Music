// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

data class SavedQueue(
    val items: List<QueueItem>,          // play order
    val originalOrder: List<Long>,       // queueIds in insertion order (differs from items only when shuffled)
    val shuffle: Boolean,
    val repeat: RepeatMode,
    val nextQueueId: Long,
)

data class SavedPosition(val index: Int, val positionMs: Long)

data class RestoredQueue(val queue: SavedQueue, val position: SavedPosition)

interface QueueStore {
    /** Replaces the whole structure in one transaction. */
    suspend fun saveStructure(queue: SavedQueue)
    suspend fun savePosition(position: SavedPosition)
    suspend fun load(): RestoredQueue?
}
