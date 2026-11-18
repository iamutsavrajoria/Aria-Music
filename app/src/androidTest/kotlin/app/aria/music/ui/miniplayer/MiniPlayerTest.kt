// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.miniplayer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.aria.music.R
import app.aria.music.player.PlayerStatus
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MiniPlayerTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private fun state(playing: Boolean = false, status: PlayerStatus = if (playing) PlayerStatus.PLAYING else PlayerStatus.PAUSED) =
        MiniPlayerUiState.Showing("Title", listOf("A", "B"), null, playing, status, 200_000)

    private fun show(s: MiniPlayerUiState.Showing, pseudo: Boolean = false, fontScale: Float = 1f, toggle: () -> Unit = {}, next: () -> Unit = {}, expand: () -> Unit = {}) =
        rule.setContent { AriaTestTheme(pseudo, fontScale) { MiniPlayerContent(s, { 50_000L }, toggle, next, expand) } }

    @Test fun showsTitleArtistsAndDescribesPlayPause() {
        show(state(playing = false)); rule.onNodeWithText("Title").assertIsDisplayed(); rule.onNodeWithText("A, B").assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.action_play)).assertIsDisplayed()
    }
    @Test fun playingShowsPause() { show(state(playing = true)); rule.onNodeWithContentDescription(str(R.string.action_pause)).assertIsDisplayed() }
    @Test fun controlsAreAtLeast48dpAndInvoke() {
        var t = 0; var n = 0; var e = 0; show(state(), toggle = { t++ }, next = { n++ }, expand = { e++ })
        rule.onNodeWithTag(TAG_MINI_PLAY).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag(TAG_MINI_NEXT).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithContentDescription(str(R.string.mini_open_now_playing, "Title")).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf(1, 1, 1), listOf(t, n, e))
    }
    @Test fun errorStatusIsAnnounced() { show(state(status = PlayerStatus.ERROR)); rule.onNodeWithTag(TAG_MINI_PLAYER).assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.StateDescription)) }
    @Test fun survivesPseudolocaleAndBigFonts() { show(state(), pseudo = true, fontScale = 2f); rule.onNodeWithTag(TAG_MINI_PLAY).assertIsDisplayed(); rule.onNodeWithTag(TAG_MINI_NEXT).assertIsDisplayed() }
}
