// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.PlaybackStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineCapAndServiceTest {
    // ---- service starter (fix: transport controls must start the service after a restore or idle stop) ----
    @Test fun playWithoutOutputStartsTheServiceOnce() = runTest {
        val h = harness()
        h.engine.playNow((1..2).map(::track)); h.settle()
        var started = 0; h.engine.serviceStarter = { started++ }
        h.engine.play()
        assertEquals(1, started)
        h.release()
    }
    @Test fun nextPreviousAndSkipToWithoutOutputStartTheService() = runTest {
        val h = harness()
        h.engine.playNow((1..3).map(::track)); h.settle()
        var started = 0; h.engine.serviceStarter = { started++ }
        h.engine.next(); assertEquals(1, started)
        h.engine.previous(); assertEquals(2, started)
        h.engine.skipTo(2); assertEquals(3, started)
        h.release()
    }
    @Test fun playNowWithoutOutputStartsTheService() = runTest {
        val h = harness()
        var started = 0; h.engine.serviceStarter = { started++ }
        h.engine.playNow((1..2).map(::track))
        assertEquals(1, started)
        h.release()
    }
    @Test fun commandsWithAnOutputDoNotStartTheService() = runTest {
        val h = harness().attach()
        var started = 0; h.engine.serviceStarter = { started++ }
        h.engine.playNow((1..3).map(::track)); h.engine.pause(); h.engine.play(); h.engine.next(); h.engine.previous(); h.engine.skipTo(2)
        assertEquals(0, started)
        h.release()
    }
    @Test fun aFailingStarterDoesNotBreakTheCommand() = runTest {
        val h = harness()
        h.engine.serviceStarter = { throw IllegalStateException("background start not allowed") }
        h.engine.playNow((1..2).map(::track))
        assertEquals(2, h.state.queue.size)
        h.release()
    }

    // ---- queue cap (spec: 500 items) ----
    @Test fun playNowKeepsTheTappedItemAndFollowersWithinTheCap() = runTest {
        val h = harness().attach()
        h.engine.playNow((1..600).map(::track), 550); h.settle()
        assertEquals(500, h.state.queue.size); assertEquals(500, h.output.playlist.size)
        assertEquals("T551", h.state.current?.track?.title); assertEquals("T600", h.state.queue.last().track.title)
        h.engine.playNow((1..600).map(::track), 0); h.settle()
        assertEquals(500, h.state.queue.size); assertEquals("T1", h.state.current?.track?.title); assertEquals(0, h.state.currentIndex)
        h.release()
    }
    @Test fun addToEndTruncatesToTheCap() = runTest {
        val h = harness().attach()
        h.engine.playNow((1..498).map(::track))
        h.engine.addToEnd((1001..1005).map(::track)); h.settle()
        assertEquals(500, h.state.queue.size); assertEquals(500, h.output.playlist.size)
        assertEquals("T1002", h.state.queue.last().track.title)
        h.engine.addToEnd(listOf(track(2000))); h.settle()
        assertEquals(500, h.state.queue.size)
        h.release()
    }

    // ---- engine-initiated edits (the player may report an end while its playlist is being edited) ----
    @Test fun removingThePlayingLastTrackKeepsPlaying() = runTest {
        val h = harness().attach()
        h.engine.playNow((1..3).map(::track), 2)
        h.output.onRemove = { h.engine.onEnded() }
        h.engine.removeAt(2)
        assertEquals(1, h.state.currentIndex); assertTrue(h.output.playWhenReady)
        assertTrue(h.state.status != PlaybackStatus.Paused)
        h.release()
    }
    @Test fun removingThePlayingLastTrackWhilePausedStaysPaused() = runTest {
        val h = harness().attach()
        h.engine.playNow((1..3).map(::track), 2); h.engine.pause()
        h.output.onRemove = { h.engine.onEnded() }
        h.engine.removeAt(2)
        assertEquals(1, h.state.currentIndex); assertFalse(h.output.playWhenReady)
        assertEquals(PlaybackStatus.Paused, h.state.status)
        h.release()
    }
    @Test fun endOfPlaybackOutsideAnEditStillStops() = runTest {
        val h = harness().attach()
        h.engine.playNow((1..2).map(::track), 1)
        h.engine.onEnded()
        assertEquals(PlaybackStatus.Paused, h.state.status)
        h.release()
    }
    @Test fun endOfPlaybackOnAnEmptyQueueIsIgnored() = runTest {
        val h = harness().attach()
        h.engine.onEnded()
        assertEquals(PlaybackStatus.Idle, h.state.status); assertEquals(emptyList(), h.output.calls)
        h.release()
    }
}
