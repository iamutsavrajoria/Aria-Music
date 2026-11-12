// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.AppSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineHistoryThresholdTest {
    private fun playing(h: Harness): Harness {
        h.engine.playNow((1..2).map(::track)); h.settle()
        h.engine.onPlayWhenReadyChanged(true); h.engine.onBufferingChanged(false); h.settle()
        return h
    }

    @Test fun defaultIsThirtySeconds() = runTest {
        val h = playing(harness().attach())
        assertEquals(30_000L, h.state.historyThresholdMs)
        h.release()
    }

    @Test fun recordsAtTheConfiguredThreshold() = runTest {
        val h = playing(harness().attach())
        h.engine.setHistoryThresholdMs(45_000)
        h.advance(44_000); assertEquals(0, h.history.recorded.size)
        h.advance(1_500); assertEquals(1, h.history.recorded.size)
        h.advance(60_000); assertEquals(1, h.history.recorded.size)
        h.release()
    }

    @Test fun tooSmallIsClampedToTenSeconds() = runTest {
        val h = playing(harness().attach())
        h.engine.setHistoryThresholdMs(1)
        assertEquals(AppSettings.MIN_HISTORY_THRESHOLD_MS, h.state.historyThresholdMs)
        h.advance(9_000); assertEquals(0, h.history.recorded.size)
        h.advance(1_500); assertEquals(1, h.history.recorded.size)
        h.release()
    }

    @Test fun tooLargeIsClampedToTwoMinutes() = runTest {
        val h = playing(harness().attach())
        h.engine.setHistoryThresholdMs(10_000_000)
        assertEquals(AppSettings.MAX_HISTORY_THRESHOLD_MS, h.state.historyThresholdMs)
        h.advance(119_000); assertEquals(0, h.history.recorded.size)
        h.advance(1_500); assertEquals(1, h.history.recorded.size)
        h.release()
    }

    @Test fun loweringMidTrackAppliesToTheCurrentTrack() = runTest {
        val h = playing(harness().attach())
        h.advance(20_000); assertEquals(0, h.history.recorded.size)
        h.engine.setHistoryThresholdMs(15_000); h.advance(1_000)
        assertEquals(1, h.history.recorded.size)
        h.release()
    }
}
