// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.QueueItem
import app.aria.domain.SavedQueue
import app.aria.domain.Track
import kotlin.random.Random

/** Pure queue structure. [items] is in play order; [original] is insertion order and differs only while shuffled. */
internal class QueueModel(private val random: Random) {
    var items: List<QueueItem> = emptyList(); private set
    var index: Int = -1; private set
    var shuffle: Boolean = false; private set
    var nextId: Long = 1L; private set
    private var original: List<QueueItem> = emptyList()

    val current: QueueItem? get() = items.getOrNull(index)
    val originalOrder: List<Long> get() = original.map { it.queueId }

    private fun item(track: Track) = QueueItem(nextId++, track)
    private fun syncOriginal() { if (!shuffle) original = items }

    fun replace(tracks: List<Track>, startIndex: Int) {
        original = tracks.map(::item)
        items = original
        index = startIndex.coerceIn(0, items.lastIndex)
        if (shuffle) applyShuffle()
    }

    /** Returns the position the new item took in play order. */
    fun insertNext(track: Track): Int {
        val new = item(track)
        val pos = (index + 1).coerceIn(0, items.size)
        items = items.toMutableList().apply { add(pos, new) }
        if (shuffle) {
            val at = original.indexOfFirst { it.queueId == current?.queueId }
            original = original.toMutableList().apply { add((at + 1).coerceIn(0, size), new) }
        } else syncOriginal()
        return pos
    }

    fun append(tracks: List<Track>) {
        val added = tracks.map(::item)
        items = items + added
        original = if (shuffle) original + added else items
    }

    fun removeAt(i: Int): QueueItem {
        val removed = items[i]
        items = items.toMutableList().apply { removeAt(i) }
        if (i < index) index-- else if (index > items.lastIndex) index = items.lastIndex
        original = if (shuffle) original.filter { it.queueId != removed.queueId } else items
        return removed
    }

    fun move(from: Int, to: Int) {
        val m = items.toMutableList(); val moved = m.removeAt(from); m.add(to, moved); items = m
        index = when {
            from == index -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
        syncOriginal()
    }

    fun jumpTo(i: Int) { index = i }
    fun clear() { items = emptyList(); original = emptyList(); index = -1 }

    fun setShuffle(on: Boolean) {
        if (on == shuffle) return
        if (on) { original = items; shuffle = true; applyShuffle() }
        else {
            val cur = current
            val present = items.mapTo(HashSet()) { it.queueId }
            shuffle = false
            items = original.filter { it.queueId in present }
            index = items.indexOfFirst { it.queueId == cur?.queueId }
            original = items
        }
    }

    private fun applyShuffle() {
        val cur = current ?: return
        items = listOf(cur) + items.filter { it.queueId != cur.queueId }.shuffled(random)
        index = 0
    }

    /** Removes [n] already-played items from the front (radio cap). */
    fun dropPlayed(n: Int) {
        if (n <= 0) return
        val dropped = items.take(n).mapTo(HashSet()) { it.queueId }
        items = items.drop(n); index -= n
        original = if (shuffle) original.filter { it.queueId !in dropped } else items
    }

    fun restore(saved: SavedQueue, currentIndex: Int) {
        items = saved.items; shuffle = saved.shuffle; nextId = saved.nextQueueId
        val byId = items.associateBy { it.queueId }
        original = if (shuffle) saved.originalOrder.mapNotNull(byId::get) else items
        index = if (items.isEmpty()) -1 else currentIndex.coerceIn(0, items.lastIndex)
    }
}
