// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.aria.domain.*
import app.aria.domain.testing.Samples
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DetailPagesTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val log = mutableListOf<String>()
    private val album = AlbumActions({ log += "back" }, { log += "play:$it" }, { log += "shuffle" }, { log += "queue" }, { log += "save" }, { t, a -> log += "menu:${t.title}:$a" }, { log += "album:$it" }, { log += "retry" })
    private val artist = ArtistActions({ log += "back" }, { log += "playTop" }, { log += "shuffleTop" }, { log += "follow" }, { l, i -> log += "songs:${l.size}:$i" }, { log += "album:$it" }, { log += "artist:$it" }, { log += "playlist:$it" }, { t, a -> log += "menu:${t.title}:$a" }, { log += "retry" })
    private val playlist = PlaylistActions({ log += "back" }, { log += "play:$it" }, { log += "shuffle" }, { log += "queue" }, { log += "more" }, { t, a -> log += "menu:${t.title}:$a" }, { log += "retry" })
    private val albumDetail = AlbumDetail(Samples.album(1), Samples.tracks(2), listOf(Samples.album(2)))

    // Phone-sized screens cut a lazy list: bring a node into composition before using it.
    private fun scrollTo(matcher: SemanticsMatcher) { rule.onNodeWithTag(TAG_DETAIL_LIST).performScrollToNode(matcher) }

    private fun showAlbum(s: AlbumUiState, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { AlbumContent(s, album) } } }

    @Test fun albumHeaderActionsAndTracks() {
        showAlbum(AlbumUiState.Content(albumDetail, saved = false))
        rule.onNodeWithText("Album 1").assertExists(); rule.onNodeWithTag(TAG_DETAIL_PLAY).performClick(); rule.onNodeWithTag(TAG_DETAIL_SHUFFLE).performClick()
        rule.onNodeWithTag(TAG_DETAIL_SAVE).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        scrollTo(hasText("Song 2")); rule.onNodeWithText("Song 2").performClick()
        scrollTo(hasText("Album 2")); rule.onNodeWithText("Album 2").performClick()
        assertEquals(listOf("play:0", "shuffle", "save", "play:1", "album:MPREb_2"), log)
    }

    @Test fun albumAddToQueueAndBack() {
        showAlbum(AlbumUiState.Content(albumDetail, saved = false))
        rule.onNodeWithContentDescription(str(R.string.detail_add_to_queue_named, "Album 1")).assertWidthIsAtLeast(48.dp).performClick()
        rule.onNodeWithContentDescription(str(R.string.back)).assertWidthIsAtLeast(48.dp).performClick()
        assertEquals(listOf("queue", "back"), log)
    }

    @Test fun playAndShuffleNameTheAlbumForTalkBack() {
        showAlbum(AlbumUiState.Content(albumDetail, saved = false))
        rule.onNodeWithContentDescription(str(R.string.detail_play_named, "Album 1")).assertExists()
        rule.onNodeWithContentDescription(str(R.string.detail_shuffle_named, "Album 1")).assertExists()
    }

    @Test fun saveToggleDescriptionFlips() {
        // One setContent per test: the saved flag is state, flipped after the first assertion.
        val saved = androidx.compose.runtime.mutableStateOf(true)
        log.clear(); rule.setContent { AriaTestTheme { AlbumContent(AlbumUiState.Content(albumDetail, saved = saved.value), album) } }
        rule.onNodeWithContentDescription(str(R.string.detail_unsave)).assertExists()
        rule.runOnIdle { saved.value = false }
        rule.onNodeWithContentDescription(str(R.string.detail_save)).assertExists()
    }

    @Test fun likedTracksOfferUnlike() {
        showAlbum(AlbumUiState.Content(albumDetail, saved = false, liked = setOf(Samples.track(1).id)))
        rule.onNodeWithContentDescription(str(R.string.track_more, "Song 1")).performClick(); rule.onNodeWithText(str(R.string.track_unlike)).performClick()
        assertEquals(listOf("menu:Song 1:UNLIKE"), log)
    }

    @Test fun albumErrorOfflineLoading() {
        showAlbum(AlbumUiState.Error); rule.onNodeWithText(str(R.string.retry)).performClick(); assertEquals(listOf("retry"), log)
    }
    @Test fun albumOffline() { showAlbum(AlbumUiState.Offline); rule.onNodeWithTag("state:offline").assertIsDisplayed(); rule.onNodeWithText(str(R.string.retry)).performClick(); assertEquals(listOf("retry"), log) }
    @Test fun albumLoading() { showAlbum(AlbumUiState.Loading); rule.onNodeWithTag("state:loading").assertIsDisplayed() }
    @Test fun backWorksInEveryState() { showAlbum(AlbumUiState.Loading); rule.onNodeWithContentDescription(str(R.string.back)).performClick(); assertEquals(listOf("back"), log) }
    @Test fun albumSurvivesPseudolocaleAndBigFonts() {
        showAlbum(AlbumUiState.Content(albumDetail, false), pseudo = true, fontScale = 2f); rule.onNodeWithTag(TAG_DETAIL_PLAY).assertExists(); rule.onNodeWithTag(TAG_DETAIL_SHUFFLE).assertExists()
    }

    @Test fun artistPageShelvesAndActions() {
        val shelves = listOf(Shelf("Songs", null, Samples.tracks(2).map { BrowseItem.Song(it) }), Shelf("Albums", null, listOf(BrowseItem.Album(Samples.album(1)))))
        log.clear(); rule.setContent { AriaTestTheme { ArtistContent(ArtistUiState.Content(ArtistDetail(Samples.artist(1), "About text", "1M", shelves), followed = false), artist) } }
        scrollTo(hasText("About text")); rule.onNodeWithText("About text").assertExists()
        scrollTo(hasTestTag(TAG_DETAIL_PLAY)); rule.onNodeWithTag(TAG_DETAIL_PLAY).performClick(); rule.onNodeWithTag(TAG_DETAIL_SHUFFLE).performClick(); rule.onNodeWithTag(TAG_DETAIL_SAVE).performClick()
        scrollTo(hasText("Song 2")); rule.onNodeWithText("Song 2").performClick()
        // Scroll to the shelf row itself (like HomeTest) so the tile is on screen, not merely composed by prefetch.
        scrollTo(hasTestTag("artist:shelf:1")); rule.onNodeWithText("Album 1").performClick()
        assertEquals(listOf("playTop", "shuffleTop", "follow", "songs:2:1", "album:MPREb_1"), log)
    }

    @Test fun artistFollowDescriptionFlips() {
        rule.setContent { AriaTestTheme { ArtistContent(ArtistUiState.Content(ArtistDetail(Samples.artist(1), null, null, emptyList()), followed = true), artist) } }
        rule.onNodeWithContentDescription(str(R.string.detail_unfollow)).assertExists()
        rule.onNodeWithTag(TAG_DETAIL_PLAY).assertIsNotEnabled()
    }

    @Test fun artistErrorRetries() {
        log.clear(); rule.setContent { AriaTestTheme { ArtistContent(ArtistUiState.Error, artist) } }; rule.onNodeWithText(str(R.string.retry)).performClick(); assertEquals(listOf("retry"), log)
    }

    @Test fun playlistShowsMoreFailedWithRetryAndLoadsMoreAtTheEnd() {
        log.clear(); rule.setContent { AriaTestTheme { PlaylistContent(PlaylistUiState.Content(PlaylistDetail(Samples.playlist(1), Samples.tracks(30), "c"), loadingMore = false, moreFailed = true), playlist) } }
        rule.onNodeWithTag(TAG_DETAIL_LIST).performScrollToIndex(31); rule.onNodeWithTag(TAG_DETAIL_MORE_FAILED).assertExists(); rule.onNodeWithText(str(R.string.retry)).performClick()
        assertTrue("more" in log)
    }

    @Test fun aFailedPageIsNotRequestedAgainByScrolling() {
        log.clear(); rule.setContent { AriaTestTheme { PlaylistContent(PlaylistUiState.Content(PlaylistDetail(Samples.playlist(1), Samples.tracks(30), "c"), loadingMore = false, moreFailed = true), playlist) } }
        rule.onNodeWithTag(TAG_DETAIL_LIST).performScrollToIndex(31); rule.waitForIdle()
        assertTrue("more" !in log)
    }

    @Test fun scrollingToTheEndRequestsTheNextPage() {
        log.clear(); rule.setContent { AriaTestTheme { PlaylistContent(PlaylistUiState.Content(PlaylistDetail(Samples.playlist(1), Samples.tracks(30), "c"), loadingMore = false, moreFailed = false), playlist) } }
        rule.onNodeWithTag(TAG_DETAIL_LIST).performScrollToIndex(30); rule.waitForIdle()
        assertTrue("more" in log)
    }

    @Test fun noContinuationNeverRequestsMore() {
        log.clear(); rule.setContent { AriaTestTheme { PlaylistContent(PlaylistUiState.Content(PlaylistDetail(Samples.playlist(1), Samples.tracks(30), null), loadingMore = false, moreFailed = false), playlist) } }
        rule.onNodeWithTag(TAG_DETAIL_LIST).performScrollToIndex(30); rule.waitForIdle()
        assertTrue("more" !in log)
    }

    @Test fun playlistLoadingMoreShowsAFooter() {
        rule.setContent { AriaTestTheme { PlaylistContent(PlaylistUiState.Content(PlaylistDetail(Samples.playlist(1), Samples.tracks(30), "c"), loadingMore = true, moreFailed = false), playlist) } }
        rule.onNodeWithTag(TAG_DETAIL_LIST).performScrollToIndex(31); rule.onNodeWithText(str(R.string.detail_loading_more)).assertExists()
    }

    @Test fun playlistHeaderActionsAndTracks() {
        log.clear(); rule.setContent { AriaTestTheme { PlaylistContent(PlaylistUiState.Content(PlaylistDetail(Samples.playlist(1), Samples.tracks(2), null), false, false), playlist) } }
        rule.onNodeWithText("Playlist 1").assertExists(); rule.onNodeWithTag(TAG_DETAIL_PLAY).performClick(); rule.onNodeWithTag(TAG_DETAIL_SHUFFLE).performClick()
        rule.onNodeWithContentDescription(str(R.string.detail_add_to_queue_named, "Playlist 1")).performClick()
        scrollTo(hasText("Song 2")); rule.onNodeWithText("Song 2").performClick()
        assertEquals(listOf("play:0", "shuffle", "queue", "play:1"), log)
    }

    @Test fun playlistOfflineLoadingError() {
        log.clear(); rule.setContent { AriaTestTheme { PlaylistContent(PlaylistUiState.Offline, playlist) } }; rule.onNodeWithTag("state:offline").assertIsDisplayed()
    }
}
