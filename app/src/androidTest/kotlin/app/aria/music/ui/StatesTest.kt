// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.aria.music.R
import app.aria.music.ui.common.ErrorState
import app.aria.music.ui.common.LoadingState
import app.aria.music.ui.common.OfflineState
import app.aria.music.ui.common.TAG_OFFLINE
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StatesTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun errorAndOfflineOfferRetry() {
        var retries = 0
        rule.setContent { AriaTestTheme { ErrorState(onRetry = { retries++ }) } }
        rule.onNodeWithText(str(R.string.error_title)).assertIsDisplayed(); rule.onNodeWithText(str(R.string.retry)).performClick()
        assertEquals(1, retries)
    }
    @Test fun offlineAndEmptyAndLoadingRender() {
        rule.setContent { AriaTestTheme(pseudo = true) { OfflineState(onRetry = {}) } }
        rule.onNodeWithTag(TAG_OFFLINE).assertIsDisplayed()
    }
    @Test fun loadingHasADescription() {
        rule.setContent { AriaTestTheme { LoadingState() } }
        rule.onNodeWithContentDescription(str(R.string.loading)).assertIsDisplayed()
    }
}
