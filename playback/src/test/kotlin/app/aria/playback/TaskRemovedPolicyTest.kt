// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.PlaybackStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskRemovedPolicyTest {
    @Test fun serviceStaysWhilePlaybackIsWanted() {
        assertTrue(keepRunningAfterTaskRemoved(PlaybackStatus.Playing))
        assertTrue(keepRunningAfterTaskRemoved(PlaybackStatus.Buffering))
        assertTrue(keepRunningAfterTaskRemoved(PlaybackStatus.WaitingForNetwork))
    }

    @Test fun serviceStopsWhenNothingIsWanted() {
        assertFalse(keepRunningAfterTaskRemoved(PlaybackStatus.Idle))
        assertFalse(keepRunningAfterTaskRemoved(PlaybackStatus.Paused))
        assertFalse(keepRunningAfterTaskRemoved(PlaybackStatus.Error("x")))
    }
}
