// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class EngineExtrasTest {
    private fun radioTracks(from: Int, n: Int) = (from until from + n).map(::track)

    @Test fun radioTopsUpWhenFewRemain() = runTest {
        val h = Harness(testScheduler).attach()
        h.radio.result = Result.success(radioTracks(100, 40))
        h.engine.playNow((1..3).map(::track)); h.settle()
        assertEquals(3 + 25, h.state.queue.size); assertEquals(1, h.radio.calls.size)
        assertEquals(h.state.queue.size, h.output.playlist.size)
        h.release()
    }
    @Test fun radioSkipsDuplicatesAndFailureIsSilent() = runTest {
        val h = Harness(testScheduler).attach()
        h.radio.result = Result.success(listOf(track(1), track(2), track(50)))
        h.engine.playNow((1..2).map(::track)); h.settle()
        assertEquals(listOf("T1", "T2", "T50"), h.state.queue.map { it.track.title })
        h.radio.result = Result.failure(RuntimeException("offline")); h.engine.next(); h.settle()
        assertTrue(h.state.status !is PlaybackStatus.Error)
        h.release()
    }
    @Test fun radioOffWithRepeatOrToggle() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.setRepeat(RepeatMode.ALL); h.engine.playNow((1..2).map(::track)); h.settle()
        assertEquals(0, h.radio.calls.size)
        h.engine.setRepeat(RepeatMode.OFF); h.engine.setEndlessRadio(false); h.engine.next(); h.settle()
        assertEquals(0, h.radio.calls.size)
        h.release()
    }
    @Test fun queueIsCappedAtFiveHundredByDroppingPlayedTracks() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.setEndlessRadio(false)
        h.engine.playNow((1..490).map(::track), 484); h.settle()
        h.engine.setEndlessRadio(true)
        h.radio.result = Result.success(radioTracks(1000, 25)); h.engine.next(); h.settle()
        assertTrue(h.state.queue.size <= 500)
        assertEquals(h.state.queue.map { it.queueId }, h.output.playlist.map { it.queueId })
        assertEquals(h.state.current?.queueId, h.output.playlist[h.output.index].queueId)
        h.release()
    }
    @Test fun sleepTimerFadesThenPauses() = runTest {
        val h = Harness(testScheduler).attach(); h.engine.setEndlessRadio(false)
        h.engine.playNow((1..2).map(::track)); h.engine.onBufferingChanged(false); h.settle()
        h.engine.setSleepTimer(60_000)
        h.advance(54_000); assertEquals(1f, h.output.volume)
        h.advance(3_000); assertTrue(h.output.volume < 1f && h.output.volume > 0f)
        h.advance(3_100)
        assertFalse(h.output.playWhenReady); assertEquals(1f, h.output.volume); assertNull(h.state.sleepTimer)
        h.release()
    }
    @Test fun sleepTimerCanBeCancelled() = runTest {
        val h = Harness(testScheduler).attach(); h.engine.setEndlessRadio(false)
        h.engine.playNow((1..2).map(::track)); h.settle()
        h.engine.setSleepTimer(10_000); h.engine.setSleepTimer(null); h.advance(30_000)
        assertTrue(h.output.playWhenReady)
        h.release()
    }
    @Test fun endOfTrackPausesAtTransition() = runTest {
        val h = Harness(testScheduler).attach(); h.engine.setEndlessRadio(false)
        h.engine.playNow((1..3).map(::track)); h.engine.setSleepAtEndOfTrack(true)
        assertEquals(true, h.state.sleepTimer?.endOfTrack)
        h.engine.onItemTransition(1)
        assertFalse(h.output.playWhenReady); assertEquals(0L, h.output.position); assertNull(h.state.sleepTimer)
        h.release()
    }
    @Test fun skipSilenceIsForwarded() = runTest {
        val h = Harness(testScheduler).attach(); h.engine.setSkipSilence(true)
        assertTrue(h.output.skipSilence); assertTrue(h.state.skipSilence)
        h.release()
    }
    @Test fun loudnessOnlyAttenuates() = runTest {
        val h = Harness(testScheduler).attach(); h.engine.setEndlessRadio(false)
        val loud = track(1).id; val quiet = track(2).id
        h.streams.results[loud] = Result.success(ResolvedStream("https://a", "audio/webm", 1, null, null, loudnessDb = 6.0))
        h.streams.results[quiet] = Result.success(ResolvedStream("https://b", "audio/webm", 1, null, null, loudnessDb = -5.0))
        h.engine.playNow((1..2).map(::track)); h.settle()
        assertEquals(0.5f, h.output.volume, 0.01f)                       // 10^(-6/20)
        h.engine.next(); h.settle(); assertEquals(1f, h.output.volume)   // quieter track is left alone
        h.engine.skipTo(0); h.settle(); h.engine.setNormalizeLoudness(false)
        assertEquals(1f, h.output.volume)
        h.release()
    }
}
