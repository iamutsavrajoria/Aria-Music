// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.aria.domain.*
import app.aria.music.R
import app.aria.music.player.PlayerStatus
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NowPlayingTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val track = Track(TrackId.youtube("dQw4w9WgXcQ"), "Title", listOf("Artist"), 200_000, null)
    private fun content(status: PlayerStatus = PlayerStatus.PAUSED, liked: Boolean = false, shuffle: Boolean = false, repeat: RepeatMode = RepeatMode.OFF, sleep: SleepTimer? = null, durationMs: Long = 200_000) =
        NowPlayingUiState.Content(track, status, durationMs, shuffle, repeat, sleep, liked, 3, 0, 15)
    private class Calls { val log = mutableListOf<String>() }
    private fun actions(c: Calls) = NowPlayingActions(
        onBack = { c.log += "back" }, onToggle = { c.log += "toggle" }, onNext = { c.log += "next" }, onPrevious = { c.log += "previous" }, onSeek = { c.log += "seek:$it" },
        onShuffle = { c.log += "shuffle:$it" }, onRepeat = { c.log += "repeat" }, onLike = { c.log += "like" }, onSleepMinutes = { c.log += "sleep:$it" },
        onSleepEndOfTrack = { c.log += "sleepEnd" }, onSleepOff = { c.log += "sleepOff" }, onOpenQueue = { c.log += "queue" },
    )
    private fun show(s: NowPlayingUiState, c: Calls = Calls(), pseudo: Boolean = false, fontScale: Float = 1f, positionMs: Long = 65_000L) =
        rule.setContent { AriaTestTheme(pseudo, fontScale) { NowPlayingContent(s, { positionMs }, actions(c)) } }
    private fun plural(id: Int, n: Int) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(id, n, n)

    @Test fun controlsHaveDescriptionsAndMinimumSize() {
        show(content())
        for (id in listOf(R.string.action_play, R.string.action_next, R.string.action_previous, R.string.np_like, R.string.np_queue, R.string.np_shuffle_off, R.string.np_repeat_off))
            rule.onNodeWithContentDescription(str(id)).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }
    @Test fun stateTogglesChangeDescriptions() {
        show(content(PlayerStatus.PLAYING, liked = true, shuffle = true, repeat = RepeatMode.ONE))
        for (id in listOf(R.string.action_pause, R.string.np_unlike, R.string.np_shuffle_on, R.string.np_repeat_one)) rule.onNodeWithContentDescription(str(id)).assertIsDisplayed()
    }
    @Test fun repeatAllIsNamed() { show(content(repeat = RepeatMode.ALL)); rule.onNodeWithContentDescription(str(R.string.np_repeat_all)).assertIsDisplayed() }
    @Test fun clicksReachTheActions() {
        val c = Calls(); show(content(), c)
        rule.onNodeWithTag(TAG_NP_PLAY).performClick(); rule.onNodeWithContentDescription(str(R.string.action_next)).performClick()
        rule.onNodeWithContentDescription(str(R.string.action_previous)).performClick(); rule.onNodeWithTag(TAG_NP_LIKE).performClick(); rule.onNodeWithTag(TAG_NP_QUEUE).performClick()
        rule.onNodeWithContentDescription(str(R.string.np_shuffle_off)).performClick(); rule.onNodeWithContentDescription(str(R.string.np_repeat_off)).performClick()
        assertEquals(listOf("toggle", "next", "previous", "like", "queue", "shuffle:true", "repeat"), c.log)
    }
    @Test fun backReachesTheActionsFromTheContentState() {
        val c = Calls(); show(content(), c)
        rule.onNodeWithContentDescription(str(R.string.back)).performClick(); assertEquals(listOf("back"), c.log)
    }
    @Test fun sleepMenuOffersDefaultEndOfTrackAndOff() {
        val c = Calls(); show(content(), c)
        rule.onNodeWithTag(TAG_NP_SLEEP).performClick(); rule.onNodeWithText(plural(R.plurals.np_sleep_in_minutes, 15)).performClick()
        rule.onNodeWithTag(TAG_NP_SLEEP).performClick(); rule.onNodeWithText(str(R.string.np_sleep_end_of_track)).performClick()
        rule.onNodeWithTag(TAG_NP_SLEEP).performClick(); rule.onNodeWithText(str(R.string.np_sleep_off)).performClick()
        assertEquals(listOf("sleep:15", "sleepEnd", "sleepOff"), c.log)
    }
    @Test fun sleepLabelShowsTheActiveTimer() {
        show(content(sleep = SleepTimer(123L, false))); rule.onNodeWithText(str(R.string.np_sleep_active)).assertIsDisplayed()
    }
    @Test fun seekBarDescribesPosition() {
        show(content())
        rule.onNodeWithTag(TAG_NP_SEEK).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(R.string.np_seek_position, "1:05", "3:20")))
        rule.onNodeWithText("1:05").assertIsDisplayed(); rule.onNodeWithText("3:20").assertIsDisplayed()
    }
    @Test fun sleepMenuUsesTheSingularForOneMinute() {
        show(content().copy(sleepDefaultMinutes = 1)); rule.onNodeWithTag(TAG_NP_SLEEP).performClick()
        rule.onNodeWithText(plural(R.plurals.np_sleep_in_minutes, 1)).assertIsDisplayed()
        assertEquals("Sleep in 1 minute", plural(R.plurals.np_sleep_in_minutes, 1))
    }
    @Test fun seekCommitsOnceOnReleaseNotPerTick() {
        val c = Calls(); show(content(), c)
        rule.onNodeWithTag(TAG_NP_SEEK).performTouchInput { down(Offset(width * 0.2f, centerY)); moveTo(Offset(width * 0.4f, centerY)); moveTo(Offset(width * 0.6f, centerY)) }
        rule.waitForIdle(); assertEquals(emptyList<String>(), c.log)
        rule.onNodeWithTag(TAG_NP_SEEK).performTouchInput { moveTo(Offset(width * 0.5f, centerY)); up() }
        rule.waitForIdle()
        assertEquals(1, c.log.size); assertTrue(c.log.single().startsWith("seek:"))
        val ms = c.log.single().removePrefix("seek:").toLong()
        assertTrue("seek landed at $ms", ms in 80_000L..120_000L)
    }
    @Test fun seekCommitIsClampedToTheDuration() {
        val c = Calls(); show(content(), c)
        rule.onNodeWithTag(TAG_NP_SEEK).performTouchInput { down(Offset(width * 0.5f, centerY)); moveTo(Offset(width * 3f, centerY)); up() }
        rule.waitForIdle()
        assertEquals(listOf("seek:200000"), c.log)
    }
    @Test fun seekCommitIsClampedAtTheStart() {
        val c = Calls(); show(content(), c)
        rule.onNodeWithTag(TAG_NP_SEEK).performTouchInput { down(Offset(width * 0.5f, centerY)); moveTo(Offset(-width * 3f, centerY)); up() }
        rule.waitForIdle()
        assertEquals(listOf("seek:0"), c.log)
    }
    @Test fun seekBarIsDisabledWithoutADuration() {
        val c = Calls(); show(content(durationMs = 0), c)
        rule.onNodeWithTag(TAG_NP_SEEK).assertIsNotEnabled()
        rule.onNodeWithTag(TAG_NP_SEEK).performTouchInput { down(Offset(width * 0.5f, centerY)); moveTo(Offset(width * 0.9f, centerY)); up() }
        rule.waitForIdle(); assertEquals(emptyList<String>(), c.log)
    }
    @Test fun seekBarIsEnabledWithADuration() { show(content()); rule.onNodeWithTag(TAG_NP_SEEK).assertIsEnabled() }
    @Test fun shownPositionNeverExceedsTheDuration() {
        show(content(durationMs = 90_000), positionMs = 400_000L)
        rule.onNodeWithTag(TAG_NP_SEEK).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(R.string.np_seek_position, "1:30", "1:30")))
        rule.onNodeWithText("6:40").assertDoesNotExist()
    }
    @Test fun errorOfferingRetryAndOfflineBanner() {
        val c = Calls(); show(content(PlayerStatus.ERROR), c)
        rule.onNodeWithTag(TAG_NP_ERROR).assertIsDisplayed(); rule.onNodeWithText(str(R.string.retry)).performClick(); assertEquals(listOf("toggle"), c.log)
    }
    @Test fun offlineBannerShows() { show(content(PlayerStatus.WAITING_FOR_NETWORK)); rule.onNodeWithTag(TAG_NP_OFFLINE).assertIsDisplayed() }
    @Test fun waitingForNetworkOffersPause() { show(content(PlayerStatus.WAITING_FOR_NETWORK)); rule.onNodeWithContentDescription(str(R.string.action_pause)).assertIsDisplayed() }
    @Test fun bufferingKeepsThePauseDescription() { show(content(PlayerStatus.BUFFERING)); rule.onNodeWithContentDescription(str(R.string.action_pause)).assertIsDisplayed() }
    @Test fun emptyStateHasABackButton() {
        val c = Calls(); show(NowPlayingUiState.Empty, c); rule.onNodeWithContentDescription(str(R.string.back)).performClick(); assertEquals(listOf("back"), c.log)
    }
    @Test fun lyricsSlotExistsButIsEmpty() { show(content()); rule.onNodeWithTag(TAG_NP_LYRICS).assertExists() }
    @Test fun survivesPseudolocaleAndBigFonts() {
        show(content(PlayerStatus.PLAYING, sleep = SleepTimer(null, true)), pseudo = true, fontScale = 2f)
        rule.onNodeWithTag(TAG_NP_PLAY).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(TAG_NP_SLEEP).performScrollTo().assertIsDisplayed()
    }
}
