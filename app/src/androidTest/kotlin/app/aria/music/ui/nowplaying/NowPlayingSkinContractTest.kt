// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.aria.domain.*
import app.aria.music.R
import app.aria.music.player.PlayerStatus
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

typealias Skin = @Composable (NowPlayingUiState, () -> Long, NowPlayingActions, Modifier) -> Unit

/** One contract, run against every Now Playing skin: same tags, descriptions, 48dp targets, callbacks, banners and empty state. */
@RunWith(Parameterized::class)
class NowPlayingSkinContractTest(private val name: String, private val skin: Skin) {
    companion object {
        private val material: Skin = { s, p, a, m -> NowPlayingContent(s, p, a, m) }
        private val apple: Skin = { s, p, a, m -> AppleNowPlayingContent(s, p, a, m) }
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun skins(): List<Array<Any>> = listOf(arrayOf("material", material), arrayOf("apple", apple))
    }

    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private fun plural(id: Int, n: Int) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(id, n, n)
    private val track = Track(TrackId.youtube("dQw4w9WgXcQ"), "Title", listOf("Artist"), 200_000, null)
    private fun content(status: PlayerStatus = PlayerStatus.PAUSED, liked: Boolean = false, shuffle: Boolean = false, repeat: RepeatMode = RepeatMode.OFF, sleep: SleepTimer? = null) =
        NowPlayingUiState.Content(track, status, 200_000, shuffle, repeat, sleep, liked, 3, 0, 15)
    private val log = mutableListOf<String>()
    private val actions = NowPlayingActions(
        { log += "back" }, { log += "toggle" }, { log += "next" }, { log += "previous" }, { log += "seek:$it" }, { log += "shuffle:$it" }, { log += "repeat" },
        { log += "like" }, { log += "sleep:$it" }, { log += "sleepEnd" }, { log += "sleepOff" }, { log += "queue" },
    )

    /** One setContent per test (a second one in the same rule would replace the first). */
    private fun show(s: NowPlayingUiState, pseudo: Boolean = false, fontScale: Float = 1f) {
        log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { skin(s, { 65_000L }, actions, Modifier) } }
    }
    /** The skins scroll, so bring a control into view before touching it. */
    private fun tag(t: String) = rule.onNodeWithTag(t).also { it.performScrollTo() }
    private fun described(id: Int) = rule.onNodeWithContentDescription(str(id)).also { it.performScrollTo() }

    @Test fun controlsAreDescribedAndBigEnough() {
        show(content())
        for (id in listOf(R.string.action_play, R.string.action_next, R.string.action_previous, R.string.np_like, R.string.np_queue, R.string.np_shuffle_off, R.string.np_repeat_off, R.string.back))
            described(id).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        tag(TAG_NP_SLEEP).assertHeightIsAtLeast(48.dp)
        tag(TAG_NP_SEEK).assertHeightIsAtLeast(48.dp)
    }

    @Test fun stateTogglesChangeDescriptions() {
        show(content(PlayerStatus.PLAYING, liked = true, shuffle = true, repeat = RepeatMode.ONE))
        for (id in listOf(R.string.action_pause, R.string.np_unlike, R.string.np_shuffle_on, R.string.np_repeat_one)) described(id).assertIsDisplayed()
    }

    @Test fun waitingForNetworkKeepsPauseOnTheMainButton() {
        show(content(PlayerStatus.WAITING_FOR_NETWORK)); described(R.string.action_pause).assertIsDisplayed()
    }

    @Test fun callbacksFire() {
        show(content())
        tag(TAG_NP_PLAY).performClick(); described(R.string.action_next).performClick(); described(R.string.action_previous).performClick()
        tag(TAG_NP_LIKE).performClick(); tag(TAG_NP_QUEUE).performClick()
        described(R.string.np_shuffle_off).performClick(); described(R.string.np_repeat_off).performClick(); described(R.string.back).performClick()
        assertEquals(listOf("toggle", "next", "previous", "like", "queue", "shuffle:true", "repeat", "back"), log)
    }

    @Test fun sleepMenuOffersDefaultEndOfTrackAndOff() {
        show(content())
        tag(TAG_NP_SLEEP).performClick(); rule.onNodeWithText(plural(R.plurals.np_sleep_in_minutes, 15)).performClick()
        tag(TAG_NP_SLEEP).performClick(); rule.onNodeWithText(str(R.string.np_sleep_end_of_track)).performClick()
        tag(TAG_NP_SLEEP).performClick(); rule.onNodeWithText(str(R.string.np_sleep_off)).performClick()
        assertEquals(listOf("sleep:15", "sleepEnd", "sleepOff"), log)
    }

    @Test fun offlineBanner() { show(content(PlayerStatus.WAITING_FOR_NETWORK)); tag(TAG_NP_OFFLINE).assertIsDisplayed() }

    @Test fun errorBannerOffersRetry() {
        show(content(PlayerStatus.ERROR)); tag(TAG_NP_ERROR).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.retry)).assertHeightIsAtLeast(48.dp).performClick(); assertEquals(listOf("toggle"), log)
    }

    @Test fun noBannerWhenPlayingNormally() {
        show(content(PlayerStatus.PLAYING)); rule.onNodeWithTag(TAG_NP_OFFLINE).assertDoesNotExist(); rule.onNodeWithTag(TAG_NP_ERROR).assertDoesNotExist()
    }

    @Test fun emptyState() {
        show(NowPlayingUiState.Empty)
        rule.onNodeWithText(str(R.string.np_nothing_playing)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.back)).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("back"), log)
    }

    @Test fun showsTitleAndArtists() {
        show(content()); rule.onNodeWithText("Title").assertExists(); rule.onNodeWithText("Artist").assertExists()
    }

    @Test fun seekBarDescribesPositionAndLyricsSlotExists() {
        show(content())
        tag(TAG_NP_SEEK).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(R.string.np_seek_position, "1:05", "3:20")))
        tag(TAG_NP_LYRICS).assertExists()
    }

    @Test fun survivesPseudolocaleAndBigFonts() {
        show(content(PlayerStatus.PLAYING), pseudo = true, fontScale = 2f)
        tag(TAG_NP_PLAY).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        tag(TAG_NP_SLEEP).assertIsDisplayed(); tag(TAG_NP_SEEK).assertIsDisplayed()
    }

    @Test fun errorBannerSurvivesPseudolocaleAndBigFonts() {
        show(content(PlayerStatus.ERROR), pseudo = true, fontScale = 2f); tag(TAG_NP_ERROR).assertIsDisplayed()
    }
}
