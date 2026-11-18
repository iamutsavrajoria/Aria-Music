// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import app.aria.music.R
import app.aria.music.ui.common.EmptyState
import app.aria.music.ui.common.ErrorState
import app.aria.music.ui.common.LoadingState
import app.aria.music.ui.common.OfflineState
import app.aria.music.ui.common.TAG_EMPTY
import app.aria.music.ui.common.TAG_ERROR
import app.aria.music.ui.common.TAG_LOADING
import app.aria.music.ui.common.TAG_OFFLINE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class StatesTest {
    @get:Rule val rule = createComposeRule()
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = target.getString(id)
    private fun pseudo(id: Int) = pseudoContext(target).getString(id)

    @Test fun errorShowsItsTextAndRetryCallsBack() {
        var retries = 0
        rule.setContent { AriaTestTheme { ErrorState(onRetry = { retries++ }) } }
        rule.onNodeWithTag(TAG_ERROR).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.error_title)).assertIsDisplayed(); rule.onNodeWithText(str(R.string.error_body)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.retry)).performClick()
        assertEquals(1, retries)
    }

    @Test fun offlineShowsItsTextAndRetryCallsBack() {
        var retries = 0
        rule.setContent { AriaTestTheme { OfflineState(onRetry = { retries++ }) } }
        rule.onNodeWithTag(TAG_OFFLINE).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.offline_title)).assertIsDisplayed(); rule.onNodeWithText(str(R.string.offline_body)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.retry)).performClick()
        assertEquals(1, retries)
    }

    @Test fun emptyShowsTitleAndOptionalBody() {
        rule.setContent { AriaTestTheme { EmptyState(title = R.string.home_placeholder, body = R.string.library_placeholder) } }
        rule.onNodeWithTag(TAG_EMPTY).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.home_placeholder)).assertIsDisplayed(); rule.onNodeWithText(str(R.string.library_placeholder)).assertIsDisplayed()
    }

    @Test fun loadingHasADescription() {
        rule.setContent { AriaTestTheme { LoadingState() } }
        rule.onNodeWithTag(TAG_LOADING).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.loading)).assertIsDisplayed()
    }

    @Test fun offlineRendersPseudolocalizedTextAndStaysUsableAtLargeFonts() {
        assertNotEquals("en-XA must change the offline title", str(R.string.offline_title), pseudo(R.string.offline_title))
        var retries = 0
        rule.setContent { AriaTestTheme(pseudo = true, fontScale = 2f) { OfflineState(onRetry = { retries++ }) } }
        rule.onNodeWithText(pseudo(R.string.offline_title)).assertIsDisplayed()
        rule.onNodeWithText(pseudo(R.string.retry)).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, retries)
    }
}
