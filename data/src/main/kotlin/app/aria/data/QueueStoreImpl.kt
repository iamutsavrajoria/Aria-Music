// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.data.entities.QueueItemEntity
import app.aria.data.entities.QueueStateEntity
import app.aria.domain.*

class QueueStoreImpl(private val dao: QueueDao) : QueueStore {
    override suspend fun saveStructure(queue: SavedQueue) {
        val originalPos = queue.originalOrder.withIndex().associate { it.value to it.index }
        val rows = queue.items.mapIndexed { i, it ->
            QueueItemEntity(
                queueId = it.queueId, position = i, originalPosition = originalPos[it.queueId] ?: i,
                trackId = it.track.id.raw, title = it.track.title, artists = it.track.artists.joinToString(SEP),
                durationMs = it.track.durationMs, thumbnailUrl = it.track.thumbnailUrl,
            )
        }
        val old = dao.state()
        dao.replace(rows, QueueStateEntity(0, queue.shuffle, queue.repeat.name, queue.nextQueueId, old?.currentIndex ?: 0, old?.positionMs ?: 0L))
    }

    override suspend fun savePosition(position: SavedPosition) {
        dao.savePosition(position.index, position.positionMs, QueueStateEntity(0, false, RepeatMode.OFF.name, 1L, position.index, position.positionMs))
    }

    override suspend fun load(): RestoredQueue? {
        val state = dao.state() ?: return null
        val rows = dao.items()
        if (rows.isEmpty()) return null
        val items = rows.map {
            QueueItem(it.queueId, Track(TrackId.parse(it.trackId), it.title, if (it.artists.isEmpty()) emptyList() else it.artists.split(SEP), it.durationMs, it.thumbnailUrl))
        }
        val original = rows.sortedBy { it.originalPosition }.map { it.queueId }
        return RestoredQueue(
            SavedQueue(items, original, state.shuffle, RepeatMode.valueOf(state.repeat), state.nextQueueId),
            SavedPosition(state.currentIndex, state.positionMs),
        )
    }

    private companion object { const val SEP = "\u001F" }
}
