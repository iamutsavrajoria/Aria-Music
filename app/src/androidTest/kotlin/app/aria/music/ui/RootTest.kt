// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
    private fun textShown(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test fun anEngineEventShowsAsASnackbar() {
        rule.setContent { AriaTestTheme { AriaRoot(legacySearch = { Text("SEARCH") }, controller = controller) } }
        engine.mutableEvents.tryEmit(skipped("Zed", "unavailable"))
        val expected = target.getString(R.string.msg_skipped_unavailable, "Zed")
        rule.waitUntil(5_000) { textShown(expected) }
        rule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test fun aNewerMessageReplacesTheOneOnScreenEvenWhenBothArriveAtOnce() {
        rule.setContent { AriaTestTheme { AriaRoot(legacySearch = { Text("SEARCH") }, controller = controller) } }
        val older = target.getString(R.string.msg_skipped_unavailable, "Old")
        val newer = target.getString(R.string.msg_skipped_network, "New")
        engine.mutableEvents.tryEmit(skipped("Old", "unavailable")); engine.mutableEvents.tryEmit(skipped("New", "network error"))
        rule.waitUntil(5_000) { textShown(newer) }
        rule.waitForIdle()
        rule.onNodeWithText(newer).assertIsDisplayed()
        rule.onNodeWithText(older).assertDoesNotExist()
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
