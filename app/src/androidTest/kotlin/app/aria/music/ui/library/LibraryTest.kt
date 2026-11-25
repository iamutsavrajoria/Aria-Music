// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.library

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.aria.domain.*
import app.aria.domain.testing.Samples
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val log = mutableListOf<String>()
    private val actions = LibraryActions(
        { log += "tab:$it" }, { log += "query:$it" }, { log += "sort:$it" }, { l, i -> log += "play:${l.size}:$i" }, { log += "playAll" }, { log += "shuffleAll" },
        { t, a -> log += "menu:${t.title}:$a" }, { log += "album:$it" }, { log += "artist:$it" }, { log += "playlist:$it" },
    )
    private fun songs(n: Int) = LibraryContent.Songs(Samples.tracks(n).map { LibrarySong(it, it.title.endsWith("1")) })
    private fun show(s: LibraryUiState, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { LibraryContent(s, actions) } } }
    private fun state(c: LibraryContent, tab: LibraryTab = LibraryTab.SONGS, q: String = "") = LibraryUiState(tab, q, LibrarySort.RECENTLY_ADDED, c)

    @Test fun tabsAreBigEnoughAndSwitch() {
        show(state(songs(2)))
        for (t in LibraryTab.entries) rule.onNodeWithTag("library:tab:${t.name}").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("library:tab:ALBUMS").performClick(); assertEquals(listOf("tab:ALBUMS"), log)
    }

    @Test fun songRowsPlayFromTheTappedRow() {
        show(state(songs(3))); rule.onNodeWithText("Song 3").performClick(); assertEquals(listOf("play:3:2"), log)
    }

    @Test fun playAndShuffleWork() {
        show(state(songs(3)))
        rule.onNodeWithText(str(R.string.library_play_all)).performClick(); rule.onNodeWithText(str(R.string.library_shuffle_all)).performClick()
        assertEquals(listOf("playAll", "shuffleAll"), log)
    }

    @Test fun emptySongs() { show(state(LibraryContent.Empty)); rule.onNodeWithText(str(R.string.library_empty_songs)).assertExists(); rule.onNodeWithText(str(R.string.library_no_matches)).assertDoesNotExist() }
    @Test fun emptyAlbums() { show(state(LibraryContent.Empty, LibraryTab.ALBUMS)); rule.onNodeWithText(str(R.string.library_empty_albums)).assertExists() }
    @Test fun noMatches() { show(state(LibraryContent.NoMatches, q = "zzz")); rule.onNodeWithText(str(R.string.library_no_matches)).assertExists(); rule.onNodeWithText(str(R.string.library_empty_songs)).assertDoesNotExist() }
    @Test fun errorOffersRetryOfTheOpenTab() { show(state(LibraryContent.Error, LibraryTab.ALBUMS)); rule.onNodeWithTag("state:error").assertExists(); rule.onNodeWithText(str(R.string.retry)).performClick(); assertEquals(listOf("tab:ALBUMS"), log) }
    @Test fun loadingState() { show(state(LibraryContent.Loading)); rule.onNodeWithTag("state:loading").assertExists() }

    @Test fun albumRowNavigates() {
        show(state(LibraryContent.Albums(listOf(Samples.album(1))), LibraryTab.ALBUMS)); rule.onNodeWithText("Album 1").performClick(); assertEquals(listOf("album:MPREb_1"), log)
    }
    @Test fun artistRowNavigates() { show(state(LibraryContent.Artists(listOf(Samples.artist(2))), LibraryTab.ARTISTS)); rule.onNodeWithText("Artist 2").performClick(); assertEquals(listOf("artist:UC2"), log) }
    @Test fun playlistRowNavigatesAndShowsTheSongCount() {
        show(state(LibraryContent.Playlists(listOf(Samples.playlist(3))), LibraryTab.PLAYLISTS)); rule.onNodeWithText("Playlist 3").performClick(); assertEquals(listOf("playlist:PL3"), log)
        rule.onNodeWithText("Someone, 10 songs", substring = true).assertExists()
    }

    @Test fun sortChipFeedsTheAction() { show(state(songs(2))); rule.onNodeWithText(str(R.string.library_sort_title)).performClick(); assertEquals(listOf("sort:TITLE"), log) }
    @Test fun typingFeedsTheQueryAfterTheDebounce() { show(state(songs(2))); rule.onNodeWithTag(TAG_LIBRARY_QUERY).performTextInput("ab"); rule.mainClock.advanceTimeBy(500); rule.waitForIdle(); assertEquals(listOf("query:ab"), log) }

    @Test fun aLargeListScrollsToTheEnd() {
        show(state(songs(1_500))); rule.onNodeWithTag(TAG_LIBRARY_LIST).performScrollToKey("song:track001500"); rule.onNodeWithText("Song 1500").assertIsDisplayed()
    }

    @Test fun songMenuHasLibraryActions() {
        show(state(songs(1))); rule.onNodeWithContentDescription(str(R.string.track_more, "Song 1")).performClick(); rule.onNodeWithText(str(R.string.library_remove_from_library)).performClick()
        assertEquals(listOf("menu:Song 1:REMOVE_FROM_LIBRARY"), log)
    }

    @Test fun removeLikeIsOfferedOnlyForLikedSongs() {
        show(state(songs(2)))
        rule.onNodeWithContentDescription(str(R.string.track_more, "Song 2")).performClick(); rule.onNodeWithText(str(R.string.library_remove_like)).assertDoesNotExist()
    }

    @Test fun likedSongsOfferRemoveLike() {
        show(state(songs(1))); rule.onNodeWithContentDescription(str(R.string.track_more, "Song 1")).performClick(); rule.onNodeWithText(str(R.string.library_remove_like)).performClick()
        assertEquals(listOf("menu:Song 1:UNLIKE"), log)
    }

    @Test fun survivesPseudolocaleAndBigFonts() { show(state(songs(3)), pseudo = true, fontScale = 2f); rule.onNodeWithTag("library:tab:SONGS").assertIsDisplayed() }

    @Test fun theFilterFieldHasARealLabel() {
        show(state(songs(2)))
        rule.onNodeWithText(str(R.string.library_filter_label)).assertExists()
    }

    @Test fun playAndShuffleNameWhatTheyPlayForTalkBack() {
        show(state(songs(2)))
        rule.onNodeWithContentDescription(str(R.string.library_play_all_desc)).performClick(); rule.onNodeWithContentDescription(str(R.string.library_shuffle_all_desc)).performClick()
        assertEquals(listOf("playAll", "shuffleAll"), log)
    }
}
