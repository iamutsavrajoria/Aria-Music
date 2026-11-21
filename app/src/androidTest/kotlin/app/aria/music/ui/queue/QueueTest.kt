// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.queue

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.aria.domain.Track
import app.aria.domain.TrackId
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QueueTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private fun row(i: Int, current: Boolean = false) = QueueRow(100L + i, i, Track(TrackId.youtube("track%06d".format(i)), "Song $i", listOf("A"), 1_000, null), current)
    private val log = mutableListOf<String>()
    private val actions = QueueActions({ log += "back" }, { log += "play:$it" }, { log += "remove:$it" }, { log += "up:$it" }, { log += "down:$it" }, { log += "clear" })
    private fun show(s: QueueUiState, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { QueueContent(s, actions) } } }

    @Test fun emptyState() { show(QueueUiState.Empty); rule.onNodeWithTag("state:empty").assertIsDisplayed() }

    @Test fun backButtonReportsBack() {
        show(QueueUiState.Empty)
        rule.onNodeWithContentDescription(str(R.string.back)).performClick(); assertEquals(listOf("back"), log)
    }

    @Test fun rowsMarkTheCurrentOneAndPlayOnTap() {
        show(QueueUiState.Content(listOf(row(0), row(1, current = true), row(2))))
        rule.onNodeWithTag("queue:row:101").assertContentDescriptionContains(str(R.string.queue_now_playing))
        rule.onNodeWithTag("queue:row:102").performClick(); assertEquals(listOf("play:2"), log)
    }

    @Test fun headerShowsAPluralCount() {
        show(QueueUiState.Content(listOf(row(0))))
        rule.onNodeWithText("1 song").assertIsDisplayed()
    }

    @Test fun menuMovesAndRemoves() {
        show(QueueUiState.Content(listOf(row(0), row(1), row(2))))
        rule.onNodeWithTag("queue:menu:101").performClick(); rule.onNodeWithText(str(R.string.queue_move_up)).performClick()
        rule.onNodeWithTag("queue:menu:101").performClick(); rule.onNodeWithText(str(R.string.queue_move_down)).performClick()
        rule.onNodeWithTag("queue:menu:101").performClick(); rule.onNodeWithText(str(R.string.queue_remove)).performClick()
        assertEquals(listOf("up:1", "down:1", "remove:1"), log)
    }

    @Test fun edgeMovesAreDisabled() {
        show(QueueUiState.Content(listOf(row(0), row(1))))
        rule.onNodeWithTag("queue:menu:100").performClick(); rule.onNodeWithText(str(R.string.queue_move_up)).assertIsNotEnabled()
        rule.onNodeWithText(str(R.string.queue_move_down)).assertIsEnabled()
    }

    @Test fun clearAsksForConfirmation() {
        show(QueueUiState.Content(listOf(row(0))))
        rule.onNodeWithText(str(R.string.queue_clear)).performClick(); assertEquals(emptyList<String>(), log)
        rule.onNodeWithText(str(R.string.queue_clear_confirm)).performClick(); assertEquals(listOf("clear"), log)
    }

    @Test fun largeQueueScrollsAndSurvivesBigFonts() {
        show(QueueUiState.Content((0 until 500).map { row(it) }), pseudo = true, fontScale = 2f)
        rule.onNodeWithTag(TAG_QUEUE_LIST).performScrollToIndex(499); rule.onNodeWithTag("queue:row:599").assertIsDisplayed()
    }
}
