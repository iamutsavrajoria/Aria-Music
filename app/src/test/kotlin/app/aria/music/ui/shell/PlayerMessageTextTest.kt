// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aria.music.player.PlayerController
import app.aria.music.player.PlayerMessage
import app.aria.music.player.SkipReason
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class PlayerMessageTextTest {
    private val r = ApplicationProvider.getApplicationContext<Context>().resources

    @Test fun everySkipReasonUsesItsOwnTextAndTheTitle() {
        assertEquals("Skipped Zed: not available", PlayerMessage.TrackSkipped("Zed", SkipReason.UNAVAILABLE).text(r))
        assertEquals("Skipped Zed: network error", PlayerMessage.TrackSkipped("Zed", SkipReason.NETWORK).text(r))
        assertEquals("Skipped Zed: could not play", PlayerMessage.TrackSkipped("Zed", SkipReason.OTHER).text(r))
    }

    @Test fun tooManyFailures() {
        assertEquals("Stopped after several tracks failed in a row", PlayerMessage.TooManyFailures.text(r))
    }

    @Test fun windowedQueueFormatsBothCountsForTheLocale() {
        Locale.setDefault(Locale.US)
        assertEquals("Queued 500 of 1,200 songs, starting from the one you tapped", PlayerMessage.QueueWindowed(500, 1_200).text(r))
    }

    @Test fun queueFullUsesThePluralAndTheRealCap() {
        Locale.setDefault(Locale.US)
        assertEquals(500, PlayerController.QUEUE_CAP)
        assertEquals("1 song did not fit in the queue (limit 500)", PlayerMessage.QueueFull(1).text(r))
        assertEquals("50 songs did not fit in the queue (limit 500)", PlayerMessage.QueueFull(50).text(r))
    }
}
