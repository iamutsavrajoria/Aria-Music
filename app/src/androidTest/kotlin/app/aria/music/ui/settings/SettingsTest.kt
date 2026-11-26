// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.aria.domain.AppSettings
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsTest {
    @get:Rule val rule = createComposeRule()
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg a: Any) = target.getString(id, *a)
    private fun plural(id: Int, n: Int) = target.resources.getQuantityString(id, n, n)
    private val log = mutableListOf<String>()
    private val actions = SettingsActions({ log += "back" }, { log += "history:$it" }, { log += "radio:$it" }, { log += "silence:$it" }, { log += "loudness:$it" }, { log += "sleep:$it" })

    /** One setContent per test (a second one in the same rule would replace the first). */
    private fun show(s: SettingsUiState = SettingsUiState.Content(AppSettings()), pseudo: Boolean = false, fontScale: Float = 1f) {
        log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { SettingsContent(s, "0.1.0", "Aria", actions) } }
    }

    private fun node(tag: String) = rule.onNodeWithTag(tag).also { it.performScrollTo() }

    @Test fun switchesReflectStateAndInvokeActions() {
        show()
        node(TAG_SETTINGS_RADIO).assertIsOn().performClick()
        node(TAG_SETTINGS_SILENCE).assertIsOff().performClick()
        node(TAG_SETTINGS_LOUDNESS).assertIsOn().performClick()
        assertEquals(listOf("radio:false", "silence:true", "loudness:false"), log)
    }

    @Test fun eachSwitchIsOneAccessibleControlNamedByItsLabel() {
        show()
        rule.onNodeWithTag(TAG_SETTINGS_SILENCE, useUnmergedTree = false)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Switch))
            .assertTextContains(str(R.string.settings_silence))
    }

    @Test fun rowsAreAtLeast48dp() {
        show()
        for (t in listOf(TAG_SETTINGS_RADIO, TAG_SETTINGS_SILENCE, TAG_SETTINGS_LOUDNESS, TAG_SETTINGS_SLEEP, TAG_SETTINGS_HISTORY)) node(t).assertHeightIsAtLeast(48.dp)
    }

    @Test fun backButtonGoesBack() {
        show(); rule.onNodeWithContentDescription(str(R.string.back)).assertHeightIsAtLeast(48.dp).performClick(); assertEquals(listOf("back"), log)
    }

    @Test fun historySliderDescribesItsValueWithAPlural() {
        show(SettingsUiState.Content(AppSettings(historyThresholdMs = 30_000)))
        node(TAG_SETTINGS_HISTORY).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, plural(R.plurals.settings_history_value, 30)))
        rule.onNodeWithText(str(R.string.settings_history_title)).assertExists()
        rule.onAllNodesWithText(plural(R.plurals.settings_history_value, 30)).assertCountEquals(1)
    }

    @Test fun historySliderCommitsFiveSecondSteps() {
        show(SettingsUiState.Content(AppSettings(historyThresholdMs = 30_000)))
        node(TAG_SETTINGS_HISTORY).performSemanticsAction(SemanticsActions.SetProgress) { it(45f) }
        assertEquals(listOf("history:45"), log)
    }

    @Test fun sleepDefaultMenuOffersTheChoices() {
        show(); node(TAG_SETTINGS_SLEEP).assertTextContains(plural(R.plurals.np_sleep_in_minutes, 15), substring = true).performClick()
        for (m in listOf(5, 10, 30, 45, 60, 90)) rule.onNodeWithText(plural(R.plurals.np_sleep_in_minutes, m)).assertExists()
        rule.onNodeWithText(plural(R.plurals.np_sleep_in_minutes, 45)).performClick()
        assertEquals(listOf("sleep:45"), log)
    }

    @Test fun aboutShowsNonAffiliationAndNoAnalytics() {
        show(); node(TAG_SETTINGS_ABOUT)
        rule.onNodeWithText(str(R.string.settings_about_non_affiliation, "Aria")).assertExists()
        rule.onNodeWithText(str(R.string.settings_about_no_analytics)).assertExists()
        rule.onNodeWithText(str(R.string.settings_version, "0.1.0")).assertExists()
    }

    @Test fun loadingState() { show(SettingsUiState.Loading); rule.onNodeWithTag("state:loading").assertIsDisplayed() }

    @Test fun survivesPseudolocaleAndBigFonts() {
        show(pseudo = true, fontScale = 2f)
        node(TAG_SETTINGS_RADIO).assertExists(); node(TAG_SETTINGS_ABOUT).assertIsDisplayed()
        node(TAG_SETTINGS_SLEEP).assertHeightIsAtLeast(48.dp)
    }
}
