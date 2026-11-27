// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.onboarding

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val log = mutableListOf<String>()
    private val actions = OnboardingActions({ log += "finish" }, { log += "notify" })
    private fun show(notify: Boolean = true, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { OnboardingContent(notify, actions) } } }

    @Test fun walksThroughThreePagesAndFinishes() {
        show()
        rule.onNodeWithText(str(R.string.onboarding_welcome_title)).assertIsDisplayed(); rule.onNodeWithTag(TAG_ONBOARDING_NEXT).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText(str(R.string.onboarding_privacy_title)).assertIsDisplayed(); rule.onNodeWithTag(TAG_ONBOARDING_NEXT).performClick()
        rule.onNodeWithText(str(R.string.onboarding_notifications_title)).assertIsDisplayed(); rule.onNodeWithTag(TAG_ONBOARDING_FINISH).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("finish"), log)
    }
    @Test fun backReturnsToThePreviousPage() {
        show(); rule.onNodeWithTag(TAG_ONBOARDING_NEXT).performClick(); rule.onNodeWithTag(TAG_ONBOARDING_BACK).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText(str(R.string.onboarding_welcome_title)).assertIsDisplayed()
    }
    @Test fun backIsHiddenOnTheFirstPage() { show(); rule.onNodeWithTag(TAG_ONBOARDING_BACK).assertDoesNotExist() }
    @Test fun skipFinishesImmediately() { show(); rule.onNodeWithTag(TAG_ONBOARDING_SKIP).assertHeightIsAtLeast(48.dp).performClick(); assertEquals(listOf("finish"), log) }
    @Test fun notificationButtonOnlyRequestsOnTap() {
        show(); rule.onNodeWithTag(TAG_ONBOARDING_NEXT).performClick(); rule.onNodeWithTag(TAG_ONBOARDING_NEXT).performClick()
        assertEquals(emptyList<String>(), log); rule.onNodeWithTag(TAG_ONBOARDING_NOTIFY).assertHeightIsAtLeast(48.dp).performClick(); assertEquals(listOf("notify"), log)
    }
    @Test fun withoutTheNotificationPageThePrivacyPageFinishes() {
        show(notify = false); rule.onNodeWithTag(TAG_ONBOARDING_NEXT).performClick(); rule.onNodeWithTag(TAG_ONBOARDING_FINISH).assertIsDisplayed(); rule.onNodeWithText(str(R.string.onboarding_notifications_title)).assertDoesNotExist()
        rule.onNodeWithTag(TAG_ONBOARDING_PAGE).assertContentDescriptionContains(str(R.string.onboarding_page_of, 2, 2))
    }
    @Test fun privacyPageNamesTheAppAndNonAffiliation() {
        show(); rule.onNodeWithTag(TAG_ONBOARDING_NEXT).performClick()
        rule.onNodeWithText(str(R.string.onboarding_privacy_body, str(R.string.app_name))).assertIsDisplayed()
    }
    @Test fun pageCounterIsAnnounced() {
        show(); rule.onNodeWithTag(TAG_ONBOARDING_PAGE).assertContentDescriptionContains(str(R.string.onboarding_page_of, 1, 3))
        rule.onNodeWithTag(TAG_ONBOARDING_NEXT).performClick(); rule.onNodeWithTag(TAG_ONBOARDING_PAGE).assertContentDescriptionContains(str(R.string.onboarding_page_of, 2, 3))
    }
    @Test fun survivesPseudolocaleAndBigFonts() {
        show(pseudo = true, fontScale = 2f)
        rule.onNodeWithTag(TAG_ONBOARDING_NEXT).assertExists().assertIsDisplayed(); rule.onNodeWithTag(TAG_ONBOARDING_SKIP).assertIsDisplayed()
    }
}
