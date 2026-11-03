// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackContractsTest {
    private fun track(n: Int) = Track(TrackId.youtube("track%06d".format(n)), "T$n", listOf("A"), null, null)

    @Test fun currentIsItemAtIndex() {
        val q = listOf(QueueItem(1, track(1)), QueueItem(2, track(2)))
        assertEquals(2L, PlaybackState(queue = q, currentIndex = 1).current?.queueId)
        assertNull(PlaybackState(queue = q, currentIndex = -1).current)
        assertNull(PlaybackState().current)
    }

    @Test fun resolvedStreamLoudnessDefaultsToNull() {
        assertNull(ResolvedStream("https://x", "audio/webm", 1, null, null).loudnessDb)
    }
}
