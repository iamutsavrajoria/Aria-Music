// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class EngineHistoryResumeTest {
    private fun playing(h: Harness, n: Int = 2): Harness {
        h.engine.playNow((1..n).map(::track)); h.settle()
        h.engine.onPlayWhenReadyChanged(true); h.engine.onBufferingChanged(false); h.settle()
        return h
    }

    @Test fun recordedOnceAtThirtySeconds() = runTest {
        val h = playing(Harness(testScheduler).attach())
        h.advance(29_500); assertEquals(0, h.history.recorded.size)
        h.advance(1_000); assertEquals(1, h.history.recorded.size)
        h.advance(60_000); assertEquals(1, h.history.recorded.size)
        assertEquals(track(1), h.history.recorded.single().first)
        h.release()
    }
    @Test fun pausedTimeDoesNotCount() = runTest {
        val h = playing(Harness(testScheduler).attach())
        h.advance(20_000); h.engine.pause(); h.advance(60_000)
        assertEquals(0, h.history.recorded.size)
        h.engine.play(); h.advance(10_500)
        assertEquals(1, h.history.recorded.size)
        h.release()
    }
    @Test fun newTrackStartsANewCount() = runTest {
        val h = playing(Harness(testScheduler).attach())
        h.advance(20_000); h.engine.next(); h.engine.onBufferingChanged(false); h.advance(20_000)
        assertEquals(0, h.history.recorded.size)
        h.advance(10_500); assertEquals(1, h.history.recorded.size); assertEquals(track(2), h.history.recorded.single().first)
        h.release()
    }
    @Test fun structureSavedOnlyOnChange() = runTest {
        val h = playing(Harness(testScheduler).attach()); val base = h.store.structureSaves
        h.advance(40_000); assertEquals(base, h.store.structureSaves)
        h.engine.addToEnd(listOf(track(9))); h.settle(); assertEquals(base + 1, h.store.structureSaves)
        assertEquals(3, h.store.saved?.items?.size)
        h.release()
    }
    @Test fun positionSavedOnPauseTrackChangeAndEveryFifteenSeconds() = runTest {
        val h = playing(Harness(testScheduler).attach())
        h.output.position = 14_000; h.advance(15_000)
        assertTrue(h.store.positionSaves.any { it.positionMs == 14_000L })
        val before = h.store.positionSaves.size
        h.output.position = 20_000; h.engine.pause(); h.settle()
        assertEquals(before + 1, h.store.positionSaves.size); assertEquals(20_000L, h.store.position?.positionMs)
        h.engine.next(); h.settle()
        assertEquals(1, h.store.position?.index)
        h.release()
    }
    @Test fun restoreRebuildsPausedAtSavedPosition() = runTest {
        val a = playing(Harness(testScheduler).attach())
        a.engine.setShuffle(true); a.engine.setRepeat(RepeatMode.ALL)
        a.output.position = 33_000; a.engine.pause(); a.settle(); a.release()

        val b = Harness(testScheduler); b.store.saved = a.store.saved; b.store.position = a.store.position
        b.attach(); b.engine.restore(); b.settle()
        assertEquals(a.state.queue.map { it.queueId }, b.state.queue.map { it.queueId })
        assertTrue(b.state.shuffle); assertEquals(RepeatMode.ALL, b.state.repeat)
        assertEquals(PlaybackStatus.Paused, b.state.status); assertEquals(33_000L, b.output.position)
        assertFalse(b.output.playWhenReady)
        b.release()
    }
    @Test fun restoreIgnoredWhenQueueAlreadyExists() = runTest {
        val h = Harness(testScheduler).attach(); h.engine.playNow(listOf(track(1)))
        h.store.saved = SavedQueue(listOf(QueueItem(5, track(5))), listOf(5), false, RepeatMode.OFF, 6)
        h.store.position = SavedPosition(0, 0); h.engine.restore()
        assertEquals("T1", h.state.current?.track?.title)
        h.release()
    }
}
