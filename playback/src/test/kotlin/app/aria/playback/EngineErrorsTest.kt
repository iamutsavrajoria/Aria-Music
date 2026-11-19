// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class EngineErrorsTest {
    private fun started(h: Harness, n: Int = 3, start: Int = 0) { h.engine.playNow((1..n).map(::track), start); h.settle() }

    @Test fun urlIsCachedUntilExpiry() = runTest {
        val h = harness().attach(); val id = track(1).id
        h.streams.results[id] = Result.success(ResolvedStream("https://a", "audio/webm", 1, null, h.now + 120_000))
        assertTrue(h.engine.streamFor(id).isSuccess); h.engine.streamFor(id)
        assertEquals(1, h.streams.resolved.count { it == id })
        h.now += 100_000  // inside the 60 s margin of expiry
        h.engine.streamFor(id)
        assertEquals(2, h.streams.resolved.count { it == id })
        h.release()
    }
    @Test fun hungResolveTimesOutAsTransientFailure() = runTest {
        val h = harness().attach(); h.streams.hang = true
        val result = async { h.engine.streamFor(track(1).id) }
        h.advance(19_999); assertFalse(result.isCompleted)
        h.advance(2); assertTrue(result.await().exceptionOrNull() is java.io.IOException)
        h.release()
    }
    @Test fun concurrentRequestsShareOneResolve() = runTest {
        val h = harness().attach(); h.streams.delayMs = 1_000; val id = track(1).id
        val a = async { h.engine.streamFor(id) }; val b = async { h.engine.streamFor(id) }
        h.advance(1_000)
        assertTrue(a.await().isSuccess && b.await().isSuccess)
        assertEquals(1, h.streams.resolved.count { it == id })
        h.release()
    }
    @Test fun currentAndNextAreResolvedOnTrackChange() = runTest {
        val h = harness().attach(); started(h)
        assertTrue(track(1).id in h.streams.resolved); assertTrue(track(2).id in h.streams.resolved)
        assertFalse(track(3).id in h.streams.resolved)
        h.release()
    }
    @Test fun permanentErrorSkipsImmediatelyWithoutRetry() = runTest {
        val h = harness().attach(); started(h)
        val seen = mutableListOf<PlaybackEvent>(); val job = backgroundScope.launch { h.engine.events.toList(seen) }; h.settle()
        h.engine.onError(PlayerError.Permanent); h.settle()
        assertEquals(1, h.state.currentIndex); assertTrue(h.output.retries.isEmpty())
        assertTrue(seen.single() is PlaybackEvent.Skipped)
        job.cancel(); h.release()
    }
    @Test fun http403RefreshesOnceThenBacksOff() = runTest {
        val h = harness().attach(); started(h)
        h.engine.onError(PlayerError.Http(403)); h.settle()
        assertEquals(listOf(track(1).id), h.streams.rejected); assertEquals(1, h.output.retries.size)
        h.engine.onError(PlayerError.Http(403))          // second 403: transient path
        h.advance(999); assertEquals(1, h.output.retries.size)
        h.advance(1);   assertEquals(2, h.output.retries.size)
        h.release()
    }
    @Test fun transientRetriesAtOneTwoFourSecondsThenSkips() = runTest {
        val h = harness().attach(); started(h)
        h.engine.onError(PlayerError.Io); h.advance(1_000); assertEquals(1, h.output.retries.size)
        h.engine.onError(PlayerError.Io); h.advance(2_000); assertEquals(2, h.output.retries.size)
        h.engine.onError(PlayerError.Io); h.advance(4_000); assertEquals(3, h.output.retries.size)
        h.engine.onError(PlayerError.Io); h.settle()
        assertEquals(1, h.state.currentIndex)
        h.release()
    }
    @Test fun offlineWaitsThenResumes() = runTest {
        val h = harness().attach(); started(h)
        h.network.online.value = false
        h.engine.onError(PlayerError.Io); h.settle()
        assertEquals(PlaybackStatus.WaitingForNetwork, h.state.status); assertEquals(0, h.state.currentIndex)
        h.advance(60_000); assertEquals(0, h.state.currentIndex)
        h.network.online.value = true; h.settle()
        assertEquals(1, h.output.retries.size); assertNotEquals(PlaybackStatus.WaitingForNetwork, h.state.status)
        h.release()
    }
    @Test fun pausingWhileWaitingForTheNetworkShowsPausedAndStaysPaused() = runTest {
        val h = harness().attach(); started(h)
        h.network.online.value = false
        h.engine.onError(PlayerError.Io); h.settle()
        assertEquals(PlaybackStatus.WaitingForNetwork, h.state.status)
        h.engine.pause(); h.settle()
        assertEquals(PlaybackStatus.Paused, h.state.status)
        h.network.online.value = true; h.settle()
        assertEquals(PlaybackStatus.Paused, h.state.status); assertFalse(h.output.playWhenReady)
        h.release()
    }
    @Test fun threeConsecutiveFailuresStop() = runTest {
        val h = harness().attach(); started(h, n = 6)
        repeat(3) { h.engine.onError(PlayerError.Permanent); h.settle() }
        assertTrue(h.state.status is PlaybackStatus.Error); assertFalse(h.output.playWhenReady)
        h.release()
    }
    @Test fun successResetsTheFailureCount() = runTest {
        val h = harness().attach(); started(h, n = 6)
        h.engine.onError(PlayerError.Permanent); h.engine.onError(PlayerError.Permanent)
        h.engine.onBufferingChanged(false)               // a track reached ready while playing
        h.engine.onError(PlayerError.Permanent); h.settle()
        assertFalse(h.state.status is PlaybackStatus.Error)
        h.release()
    }
}
