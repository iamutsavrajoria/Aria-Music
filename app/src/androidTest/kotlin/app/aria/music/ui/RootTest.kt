// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.aria.domain.PlaybackEvent
import app.aria.domain.testing.FakePlaybackEngine
import app.aria.domain.testing.Samples
import app.aria.music.R
import app.aria.music.player.PlayerController
import app.aria.music.ui.shell.AriaRoot
import app.aria.music.ui.shell.NotificationPermissionDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The shell wired to a real PlayerController over a fake engine: the message path from engine event to snackbar. */
class RootTest {
    @get:Rule val rule = createComposeRule()
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine = FakePlaybackEngine()
    // Unconfined so the controller subscribes to engine events during construction; its message channel buffers anyway.
    private val controller = PlayerController(engine, { }, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

    private fun skipped(title: String, reason: String) = PlaybackEvent.Skipped(Samples.track(1, title), reason)

    // The snackbar times out on the test clock, so the clock is stepped by hand instead of letting idle-waiting run it past the timeout.
    @Test fun anEngineEventShowsAsASnackbar() {
        rule.mainClock.autoAdvance = false
        rule.setContent { AriaTestTheme { AriaRoot(controller = controller, miniPlayer = {}) } }
        engine.mutableEvents.tryEmit(skipped("Zed", "unavailable"))
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithText(target.getString(R.string.msg_skipped_unavailable, "Zed")).assertIsDisplayed()
    }

    @Test fun aNewerMessageReplacesTheOneOnScreenEvenWhenBothArriveAtOnce() {
        rule.mainClock.autoAdvance = false
        rule.setContent { AriaTestTheme { AriaRoot(controller = controller, miniPlayer = {}) } }
        engine.mutableEvents.tryEmit(skipped("Old", "unavailable")); engine.mutableEvents.tryEmit(skipped("New", "network error"))
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithText(target.getString(R.string.msg_skipped_network, "New")).assertIsDisplayed()
        rule.onNodeWithText(target.getString(R.string.msg_skipped_unavailable, "Old")).assertDoesNotExist()
    }

    @Test fun aNewerMessageReplacesOneAlreadyOnScreen() {
        rule.mainClock.autoAdvance = false
        rule.setContent { AriaTestTheme { AriaRoot(controller = controller, miniPlayer = {}) } }
        engine.mutableEvents.tryEmit(skipped("Old", "unavailable")); rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText(target.getString(R.string.msg_skipped_unavailable, "Old")).assertIsDisplayed()
        engine.mutableEvents.tryEmit(skipped("New", "network error")); rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithText(target.getString(R.string.msg_skipped_network, "New")).assertIsDisplayed()
        rule.onNodeWithText(target.getString(R.string.msg_skipped_unavailable, "Old")).assertDoesNotExist()
    }

    @Test fun theMiniPlayerSlotIsShownOnATopLevelRouteAndHiddenOnNowPlaying() {
        rule.setContent {
            AriaTestTheme {
                AriaRoot(
                    controller = controller,
                    miniPlayer = { onExpand -> Text("MINI", Modifier.clickable(onClick = onExpand)) },
                    nowPlaying = { onBack, _ -> Text("NOWPLAYING", Modifier.clickable(onClick = onBack)) },
                )
            }
        }
        rule.onNodeWithText("MINI").assertIsDisplayed()
        rule.onNodeWithText("MINI").performClick()
        rule.onNodeWithText("NOWPLAYING").assertIsDisplayed()
        rule.onNodeWithText("MINI").assertDoesNotExist()
        rule.onNodeWithText("NOWPLAYING").performClick()      // back
        rule.onNodeWithText("MINI").assertIsDisplayed()
        rule.onNodeWithText("NOWPLAYING").assertDoesNotExist()
    }

    @Test fun theMiniPlayerSlotIsHiddenOnTheQueueRoute() {
        rule.setContent {
            AriaTestTheme {
                AriaRoot(
                    controller = controller,
                    miniPlayer = { onExpand -> Text("MINI", Modifier.clickable(onClick = onExpand)) },
                    nowPlaying = { _, onQueue -> Text("NOWPLAYING", Modifier.clickable(onClick = onQueue)) },
                    queue = { onBack -> Text("QUEUE", Modifier.clickable(onClick = onBack)) },
                )
            }
        }
        rule.onNodeWithText("MINI").performClick()
        rule.onNodeWithText("NOWPLAYING").performClick()
        rule.onNodeWithText("QUEUE").assertIsDisplayed()
        rule.onNodeWithText("MINI").assertDoesNotExist()
        rule.onNodeWithText("QUEUE").performClick()           // back to Now Playing
        rule.onNodeWithText("NOWPLAYING").assertIsDisplayed()
    }

    @Test fun permissionDialogButtonsReportAllowAndNotNow() {
        var allowed = 0; var notNow = 0
        rule.setContent { AriaTestTheme { NotificationPermissionDialog(onAllow = { allowed++ }, onNotNow = { notNow++ }) } }
        rule.onNodeWithText(target.getString(R.string.notif_title)).assertIsDisplayed()
        rule.onNodeWithText(target.getString(R.string.notif_allow)).performClick()
        rule.onNodeWithText(target.getString(R.string.notif_not_now)).performClick()
        assertEquals(1, allowed); assertEquals(1, notNow)
    }
}
