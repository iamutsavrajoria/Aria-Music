// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.Track
import app.aria.domain.TrackId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

internal fun track(n: Int) = Track(TrackId.youtube("track%06d".format(n)), "T$n", listOf("A"), 200_000L, null)

class QueueModelTest {
    private fun model(n: Int, start: Int = 0) = QueueModel(Random(1)).apply { replace((1..n).map(::track), start) }
    private fun QueueModel.titles() = items.map { it.track.title }

    @Test fun replaceSetsIndex() {
        val m = model(3, start = 1)
        assertEquals(listOf("T1", "T2", "T3"), m.titles()); assertEquals(1, m.index)
    }
    @Test fun insertNextGoesAfterCurrent() {
        val m = model(3, 1); val pos = m.insertNext(track(9))
        assertEquals(2, pos); assertEquals(listOf("T1", "T2", "T9", "T3"), m.titles()); assertEquals(1, m.index)
    }
    @Test fun removeBeforeCurrentShiftsIndex() {
        val m = model(4, 2); m.removeAt(0)
        assertEquals(1, m.index); assertEquals("T3", m.current?.track?.title)
    }
    @Test fun removeLastEmptiesQueue() {
        val m = model(1); m.removeAt(0)
        assertEquals(-1, m.index); assertEquals(emptyList(), m.items)
    }
    @Test fun moveTracksCurrent() {
        val m = model(4, 1); m.move(1, 3)
        assertEquals(3, m.index); assertEquals(listOf("T1", "T3", "T4", "T2"), m.titles())
        val m2 = model(4, 1); m2.move(3, 0)
        assertEquals(2, m2.index)
    }
    @Test fun shuffleKeepsCurrentFirstAndRoundTrips() {
        val m = model(6, 2); val cur = m.current
        m.setShuffle(true)
        assertEquals(cur, m.current); assertEquals(0, m.index); assertEquals(6, m.items.size)
        m.setShuffle(false)
        assertEquals(listOf("T1", "T2", "T3", "T4", "T5", "T6"), m.titles()); assertEquals(cur, m.current)
    }
    @Test fun shuffleOffDropsRemovedAndKeepsAddedInOrder() {
        val m = model(4, 0); m.setShuffle(true)
        val removed = m.items[2]; m.removeAt(2); m.append(listOf(track(7)))
        m.setShuffle(false)
        assertEquals(false, m.items.any { it.queueId == removed.queueId }); assertEquals("T7", m.items.last().track.title)
    }
    @Test fun dropPlayedFromFront() {
        val m = model(5, 3); m.dropPlayed(2)
        assertEquals(listOf("T3", "T4", "T5"), m.titles()); assertEquals(1, m.index)
    }
}
