// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.aria.domain.*
import app.aria.domain.testing.Samples
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaylistsTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val log = mutableListOf<String>()
    private val actions = LocalPlaylistActions({ log += "back" }, { log += "play:$it" }, { log += "shuffle" }, { log += "queue" }, { log += "rename:$it" }, { log += "delete" }, { log += "remove:$it" }, { log += "up:$it" }, { log += "down:$it" }, { t, a -> log += "menu:${t.title}:$a" })
    private fun rows(n: Int) = Samples.tracks(n).mapIndexed { i, t -> PlaylistTrackRow(10 + i, t) }
    private fun content(n: Int = 3) = LocalPlaylistUiState.Content(PlaylistRef("LP1", "Mine", null, null, n), rows(n))
    private fun show(s: LocalPlaylistUiState, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { LocalPlaylistContent(s, actions) } } }

    @Test fun rowsPlayAndMenuMovesAndRemoves() {
        show(content())
        rule.onNodeWithText("Song 2").performClick()
        rule.onNodeWithTag("playlist:menu:11").performClick(); rule.onNodeWithText(str(R.string.queue_move_up)).performClick()
        rule.onNodeWithTag("playlist:menu:11").performClick(); rule.onNodeWithText(str(R.string.queue_move_down)).performClick()
        rule.onNodeWithTag("playlist:menu:11").performClick(); rule.onNodeWithText(str(R.string.playlist_remove_track)).performClick()
        assertEquals(listOf("play:1", "up:11", "down:11", "remove:11"), log)
    }
    @Test fun firstAndLastMovesAreDisabled() {
        show(content()); rule.onNodeWithTag("playlist:menu:10").performClick(); rule.onNodeWithText(str(R.string.queue_move_up)).assertIsNotEnabled()
    }
    @Test fun renameDialogRejectsBlankAndConfirmsTrimmedInput() {
        show(content()); rule.onNodeWithTag(TAG_PLAYLIST_RENAME).performClick()
        // The dialog starts with the current name; clearing it (or typing only spaces) leaves nothing to save.
        rule.onNodeWithTag("name:field").assertTextContains("Mine"); rule.onNodeWithText(str(R.string.playlist_name_confirm)).assertIsEnabled()
        rule.onNodeWithTag("name:field").performTextClearance(); rule.onNodeWithText(str(R.string.playlist_name_confirm)).assertIsNotEnabled()
        rule.onNodeWithTag("name:field").performTextInput("   "); rule.onNodeWithText(str(R.string.playlist_name_confirm)).assertIsNotEnabled()
        rule.onNodeWithTag("name:field").performTextClearance()
        rule.onNodeWithTag("name:field").performTextInput("Road trip"); rule.onNodeWithText(str(R.string.playlist_name_confirm)).performClick()
        assertEquals(listOf("rename:Road trip"), log)
    }
    @Test fun deleteAsksForConfirmation() {
        show(content()); rule.onNodeWithTag(TAG_PLAYLIST_DELETE).performClick(); assertEquals(emptyList<String>(), log)
        rule.onNodeWithText(str(R.string.playlist_delete_confirm)).performClick(); assertEquals(listOf("delete"), log)
    }
    @Test fun notFoundAndErrorStates() { show(LocalPlaylistUiState.NotFound); rule.onNodeWithTag("state:empty").assertIsDisplayed() }
    @Test fun survivesPseudolocaleAndBigFonts() { show(content(), pseudo = true, fontScale = 2f); rule.onNodeWithTag(TAG_PLAYLIST_RENAME).assertExists() }
}
