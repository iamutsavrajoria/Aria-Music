// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.PlaybackStatus
import app.aria.domain.RepeatMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineQueueTest {
    private fun titles(h: Harness) = h.state.queue.map { it.track.title }

    @Test fun playNowMirrorsQueueToOutput() = runTest {
        val h = harness().attach()
        h.engine.playNow((1..3).map(::track), 1); h.settle()
        assertEquals(listOf("T1", "T2", "T3"), h.output.playlist.map { it.track.title })
        assertEquals(1, h.output.index); assertTrue(h.output.playWhenReady); assertEquals(1, h.state.currentIndex)
        h.release()
    }
    @Test fun commandsBeforeAttachApplyOnAttach() = runTest {
        val h = harness()
        h.engine.playNow((1..2).map(::track)); h.settle()
        h.attach()
        assertEquals(2, h.output.playlist.size); assertTrue(h.output.playWhenReady)
        h.release()
    }
    @Test fun playNextAddToEndRemoveMove() = runTest {
        val h = harness().attach()
        h.engine.playNow((1..3).map(::track)); h.engine.playNext(track(9)); h.engine.addToEnd(listOf(track(7)))
        assertEquals(listOf("T1", "T9", "T2", "T3", "T7"), titles(h))
        h.engine.move(4, 0); assertEquals(listOf("T7", "T1", "T9", "T2", "T3"), titles(h)); assertEquals(1, h.state.currentIndex)
        h.engine.removeAt(0); assertEquals(0, h.state.currentIndex)
        assertEquals(titles(h), h.output.playlist.map { it.track.title })
        h.release()
    }
    @Test fun removingCurrentMovesToNeighbour() = runTest {
        val h = harness().attach()
        h.engine.playNow((1..3).map(::track), 1); h.engine.removeAt(1)
        assertEquals("T3", h.state.current?.track?.title); assertEquals(1, h.output.index)
        h.release()
    }
    @Test fun addToEmptyQueueStartsPlaying() = runTest {
        val h = harness().attach()
        h.engine.addToEnd(listOf(track(1)))
        assertEquals("T1", h.state.current?.track?.title); assertTrue(h.output.playWhenReady)
        h.release()
    }
    @Test fun clearEmptiesEverything() = runTest {
        val h = harness().attach()
        h.engine.playNow((1..2).map(::track)); h.engine.clear()
        assertEquals(PlaybackStatus.Idle, h.state.status); assertEquals(emptyList(), h.output.playlist)
        h.release()
    }
    @Test fun shuffleUsesReorderAndKeepsCurrent() = runTest {
        val h = harness().attach()
        h.engine.playNow((1..5).map(::track), 2); val cur = h.state.current
        h.engine.setShuffle(true)
        assertEquals(cur, h.state.current); assertEquals(0, h.state.currentIndex)
        assertTrue("reorder" in h.output.calls)
        h.engine.setShuffle(false)
        assertEquals(listOf("T1", "T2", "T3", "T4", "T5"), titles(h)); assertEquals(cur, h.state.current)
        h.release()
    }
    @Test fun previousRestartsAfterThreeSeconds() = runTest {
        val h = harness().attach()
        h.engine.playNow((1..3).map(::track), 1)
        h.output.position = 3_500; h.engine.previous()
        assertEquals(1, h.state.currentIndex); assertEquals(0L, h.output.position)
        h.output.position = 1_000; h.engine.previous()
        assertEquals(0, h.state.currentIndex)
        h.release()
    }
    @Test fun nextAtEndStopsWhenRepeatOffAndWrapsWhenAll() = runTest {
        val h = harness().attach()
        h.engine.playNow((1..2).map(::track), 1); h.engine.next()
        assertEquals(PlaybackStatus.Paused, h.state.status); assertEquals(1, h.state.currentIndex)
        h.engine.setRepeat(RepeatMode.ALL); h.engine.next()
        assertEquals(0, h.state.currentIndex); assertTrue(h.output.playWhenReady)
        h.release()
    }
    @Test fun repeatOneIsPassedToThePlayer() = runTest {
        val h = harness().attach()
        h.engine.setRepeat(RepeatMode.ONE); assertTrue(h.output.repeatOne)
        h.release()
    }
    @Test fun playerEndedWrapsWithRepeatAll() = runTest {
        val h = harness().attach()
        h.engine.playNow((1..2).map(::track), 1); h.engine.setRepeat(RepeatMode.ALL)
        h.engine.onEnded(); assertEquals(0, h.state.currentIndex)
        h.release()
    }
    @Test fun playerTransitionUpdatesIndex() = runTest {
        val h = harness().attach()
        h.engine.playNow((1..3).map(::track)); h.engine.onItemTransition(1)
        assertEquals(1, h.state.currentIndex)
        h.release()
    }
}
