// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.search

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.aria.domain.*
import app.aria.domain.testing.Samples
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val log = mutableListOf<String>()
    private val actions = SearchActions(
        { log += "query:$it" }, { log += "submit:$it" }, { log += "retry" }, { log += "remove:$it" }, { log += "clear" },
        { songs, i -> log += "play:${songs.size}:$i" }, { t, a -> log += "menu:${t.title}:$a" }, { log += "album:$it" }, { log += "artist:$it" }, { log += "playlist:$it" },
    )
    private val results = SearchResults(Samples.tracks(2), listOf(Samples.album(1)), listOf(Samples.artist(1)), listOf(Samples.playlist(1)))
    private fun show(s: SearchUiState, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { SearchContent(s, actions) } } }

    @Test fun historyIsListedRemovableAndClearable() {
        show(SearchUiState("", SearchMode.Idle(listOf("daft", "punk"))))
        rule.onNodeWithText("daft").performClick(); rule.onNodeWithContentDescription(str(R.string.search_remove_history, "daft")).performClick(); rule.onNodeWithText(str(R.string.search_clear_history)).performClick()
        assertEquals(listOf("submit:daft", "remove:daft", "clear"), log)
    }

    @Test fun suggestionsSubmitOnTap() {
        show(SearchUiState("da", SearchMode.Suggestions(listOf("daft punk"), emptyList()))); rule.onNodeWithText("daft punk").performClick(); assertEquals(listOf("submit:daft punk"), log)
    }

    @Test fun typingAndTheClearButtonReportTheQuery() {
        show(SearchUiState("da", SearchMode.Suggestions(emptyList(), emptyList())))
        rule.onNodeWithContentDescription(str(R.string.search_clear_query)).performClick()
        rule.onNodeWithTag("search:field").performTextReplacement("dax")
        assertEquals(listOf("query:", "query:dax"), log)
    }

    @Test fun theImeSearchActionSubmitsTheQuery() {
        show(SearchUiState("daft", SearchMode.Suggestions(emptyList(), emptyList())))
        rule.onNodeWithTag("search:field").performImeAction()
        assertEquals(listOf("submit:daft"), log)
    }

    @Test fun resultsAreGroupedAndNavigable() {
        show(SearchUiState("q", SearchMode.Results(results)))
        for (id in listOf(R.string.search_section_songs, R.string.search_section_albums, R.string.search_section_artists, R.string.search_section_playlists)) rule.onNodeWithText(str(id)).assertExists()
        rule.onNodeWithText("Song 2").performClick(); rule.onNodeWithText("Album 1").performClick()
        rule.onNodeWithTag("search:artist:UC1").performClick(); rule.onNodeWithText("Playlist 1").performClick()
        assertEquals(listOf("play:2:1", "album:MPREb_1", "artist:UC1", "playlist:PL1"), log)
    }

    @Test fun duplicateResultsDoNotCrashTheList() {
        val dup = Samples.track(1)
        show(SearchUiState("q", SearchMode.Results(SearchResults(listOf(dup, dup), listOf(Samples.album(1), Samples.album(1)), emptyList(), emptyList()))))
        rule.onAllNodesWithText("Song 1").assertCountEquals(2)
    }

    @Test fun emptySectionsAreHidden() {
        show(SearchUiState("q", SearchMode.Results(SearchResults(Samples.tracks(1), emptyList(), emptyList(), emptyList()))))
        rule.onNodeWithText(str(R.string.search_section_albums)).assertDoesNotExist()
    }

    @Test fun songMenuOffersThreeActions() {
        show(SearchUiState("q", SearchMode.Results(results)))
        rule.onNodeWithContentDescription(str(R.string.track_more, "Song 1")).performClick(); rule.onNodeWithText(str(R.string.track_play_next)).performClick()
        assertEquals(listOf("menu:Song 1:PLAY_NEXT"), log)
    }

    @Test fun songMenuAddToQueueAndLike() {
        show(SearchUiState("q", SearchMode.Results(results)))
        rule.onNodeWithContentDescription(str(R.string.track_more, "Song 1")).performClick(); rule.onNodeWithText(str(R.string.track_add_to_queue)).performClick()
        rule.onNodeWithContentDescription(str(R.string.track_more, "Song 1")).performClick(); rule.onNodeWithText(str(R.string.track_like)).performClick()
        assertEquals(listOf("menu:Song 1:ADD_TO_QUEUE", "menu:Song 1:LIKE"), log)
    }

    @Test fun errorStateRetries() { show(SearchUiState("q", SearchMode.Error)); rule.onNodeWithText(str(R.string.retry)).performClick(); assertEquals(listOf("retry"), log) }
    @Test fun offlineState() { show(SearchUiState("q", SearchMode.Offline)); rule.onNodeWithTag("state:offline").assertIsDisplayed(); rule.onNodeWithText(str(R.string.retry)).performClick(); assertEquals(listOf("retry"), log) }
    @Test fun emptyState() { show(SearchUiState("q", SearchMode.Empty)); rule.onNodeWithTag("state:empty").assertIsDisplayed() }
    @Test fun loadingState() { show(SearchUiState("q", SearchMode.Loading)); rule.onNodeWithTag("state:loading").assertIsDisplayed() }

    @Test fun survivesPseudolocaleAndBigFonts() { show(SearchUiState("q", SearchMode.Results(results)), pseudo = true, fontScale = 2f); rule.onNodeWithText("Song 1").assertExists() }

    @Test fun likedResultSongsShowTheirStateAndOfferUnlike() {
        show(SearchUiState("q", SearchMode.Results(results), liked = setOf(Samples.track(1).id)))
        rule.onAllNodesWithContentDescription(str(R.string.track_liked)).assertCountEquals(1)
        rule.onNodeWithContentDescription(str(R.string.track_more, "Song 1")).performClick(); rule.onNodeWithText(str(R.string.track_unlike)).performClick()
        assertEquals(listOf("menu:Song 1:UNLIKE"), log)
    }
}
