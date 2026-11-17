// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.aria.music.ui.shell.AriaShellContent
import app.aria.music.ui.shell.TopLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShellTest {
    @get:Rule val rule = createComposeRule()

    private fun show(selected: TopLevel? = TopLevel.HOME, pseudo: Boolean = false, fontScale: Float = 1f, onSelect: (TopLevel) -> Unit = {}) = rule.setContent {
        AriaTestTheme(pseudo, fontScale) {
            AriaShellContent(selected, onSelect, SnackbarHostState(), miniPlayer = { Text("MINI", modifier = Modifier.testTag("mini")) }) { Text("BODY") }
        }
    }

    @Test fun barShowsThreeTabsAndReportsSelection() {
        val picked = mutableListOf<TopLevel>(); show(onSelect = { picked += it })
        for (t in TopLevel.entries) rule.onNodeWithTag("tab:${t.name}").assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        rule.onNodeWithTag("tab:SEARCH").performClick(); rule.onNodeWithTag("tab:LIBRARY").performClick()
        assertEquals(listOf(TopLevel.SEARCH, TopLevel.LIBRARY), picked)
        rule.onNodeWithTag("tab:HOME").assertIsSelected()
    }

    @Test fun miniPlayerSitsAboveTheBar() {
        show()
        val mini = rule.onNodeWithTag("mini").getUnclippedBoundsInRoot(); val tab = rule.onNodeWithTag("tab:HOME").getUnclippedBoundsInRoot()
        assertTrue("mini player $mini must be above tabs $tab", mini.bottom <= tab.top)
    }

    @Test fun barIsHiddenOnFullScreenRoutes() { show(selected = null); rule.onNodeWithTag("tab:HOME").assertDoesNotExist(); rule.onNodeWithText("BODY").assertIsDisplayed() }

    @Test fun survivesPseudolocaleAndBigFonts() {
        show(pseudo = true, fontScale = 2f)
        for (t in TopLevel.entries) rule.onNodeWithTag("tab:${t.name}").assertIsDisplayed()
    }
}
