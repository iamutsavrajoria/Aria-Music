// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import app.aria.music.ui.shell.AlbumRoute
import app.aria.music.ui.shell.HomeRoute
import app.aria.music.ui.shell.LibraryRoute
import app.aria.music.ui.shell.SearchRoute
import app.aria.music.ui.shell.navigateTopLevel
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
import org.junit.Assert.assertNotEquals
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

    @Test fun pseudolocaleActuallyChangesTheTabLabelsAndTheyStayVisibleAtDoubleFontScale() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val pseudo = pseudoContext(target)
        for (t in TopLevel.entries) {
            val normal = target.getString(t.label); val xa = pseudo.getString(t.label)
            assertNotEquals("en-XA must change ${t.name}", normal, xa); assertTrue("en-XA text must be longer: $xa", xa.length > normal.length)
        }
        show(pseudo = true, fontScale = 2f)
        for (t in TopLevel.entries) {
            rule.onNodeWithTag("tab:${t.name}").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            rule.onNodeWithText(pseudo.getString(t.label)).assertIsDisplayed()
            rule.onNodeWithText(target.getString(t.label)).assertDoesNotExist()
        }
    }

    @Test fun tabsKeepTheirStateAndBackStackWhileAnotherTabIsShown() {
        lateinit var nav: NavHostController
        rule.setContent {
            AriaTestTheme {
                nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val selected = TopLevel.entries.firstOrNull { t -> entry?.destination?.hierarchy?.any { it.hasRoute(t.route::class) } == true }
                AriaShellContent(selected, nav::navigateTopLevel, SnackbarHostState(), miniPlayer = {}) { padding ->
                    NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
                        composable<HomeRoute> { Text("HOME_PAGE") }
                        composable<SearchRoute> { Text("SEARCH_PAGE") }
                        composable<LibraryRoute> {
                            var count by rememberSaveable { mutableIntStateOf(0) }
                            Column {
                                Text("count=$count")
                                Button(onClick = { count++ }, modifier = Modifier.testTag("inc")) { Text("+") }
                                Button(onClick = { nav.navigate(AlbumRoute("a")) }, modifier = Modifier.testTag("open")) { Text("open") }
                            }
                        }
                        composable<AlbumRoute> { Text("ALBUM_PAGE") }
                    }
                }
            }
        }
        rule.onNodeWithText("HOME_PAGE").assertIsDisplayed()
        rule.onNodeWithTag("tab:LIBRARY").performClick()
        rule.onNodeWithTag("tab:LIBRARY").assertIsSelected()
        rule.onNodeWithTag("inc").performClick(); rule.onNodeWithTag("inc").performClick()
        rule.onNodeWithText("count=2").assertIsDisplayed()
        // Library -> Home -> Library: the counter survives.
        rule.onNodeWithTag("tab:HOME").performClick(); rule.onNodeWithText("HOME_PAGE").assertIsDisplayed()
        rule.onNodeWithTag("tab:LIBRARY").performClick(); rule.onNodeWithText("count=2").assertIsDisplayed()
        // Drill into a detail screen (no bar there), go Home by navigation, reselect Library: the detail screen is restored on top.
        rule.onNodeWithTag("open").performClick(); rule.onNodeWithText("ALBUM_PAGE").assertIsDisplayed()
        rule.runOnUiThread { nav.navigateTopLevel(TopLevel.HOME) }
        rule.onNodeWithText("HOME_PAGE").assertIsDisplayed()
        rule.runOnUiThread { nav.navigateTopLevel(TopLevel.LIBRARY) }
        rule.onNodeWithText("ALBUM_PAGE").assertIsDisplayed()
        rule.runOnUiThread { nav.popBackStack() }
        rule.onNodeWithText("count=2").assertIsDisplayed()
    }
}
