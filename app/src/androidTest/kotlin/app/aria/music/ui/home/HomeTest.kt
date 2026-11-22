// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.home

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

class HomeTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val log = mutableListOf<String>()
    private val actions = HomeActions(
        { l, i -> log += "play:${l.size}:$i" }, { log += "album:$it" }, { log += "artist:$it" }, { log += "playlist:$it" }, { log += "refresh" }, { log += "settings" }, { t, a -> log += "menu:${t.title}:$a" },
    )
    private val shelf = Shelf("Fresh picks", "For you", listOf(BrowseItem.Song(Samples.track(1)), BrowseItem.Album(Samples.album(2)), BrowseItem.Artist(Samples.artist(3)), BrowseItem.Playlist(Samples.playlist(4))))
    private fun content(vararg shelves: ShelfState, outdated: Boolean = false, refreshFailed: Boolean = false) = RemoteSection.Content(shelves.toList(), outdated, refreshFailed)
    private fun state(remote: RemoteSection, quick: List<Track> = Samples.tracks(2, from = 20), recent: List<Track> = Samples.tracks(1, from = 9), liked: Set<TrackId> = emptySet()) =
        HomeUiState(quick, recent, remote, refreshing = false, liked = liked)
    private fun show(s: HomeUiState, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { HomeContent(s, actions) } } }
    private fun scrollList(to: SemanticsMatcher) { rule.onNodeWithTag(TAG_HOME_LIST).performScrollToNode(to) }

    @Test fun localAndRemoteSectionsRenderAndNavigate() {
        show(state(content(ShelfState.Ready(shelf))))
        rule.onNodeWithText(str(R.string.home_quick_picks)).assertExists(); rule.onNodeWithText("Fresh picks").assertExists()
        rule.onNodeWithTag("home:quick:0").performClick()
        rule.onNodeWithTag("home:shelf:0").performScrollToNode(hasText("Playlist 4"))
        rule.onNodeWithText("Song 1").performClick(); rule.onNodeWithText("Album 2").performClick(); rule.onNodeWithText("Artist 3").performClick(); rule.onNodeWithText("Playlist 4").performClick()
        scrollList(hasText(str(R.string.home_recently_played)))
        rule.onNodeWithText(str(R.string.home_recently_played)).assertExists()
        scrollList(hasTestTag("home:recent:0")); rule.onNodeWithTag("home:recent:0").performClick()
        assertEquals(listOf("play:2:0", "play:1:0", "album:MPREb_2", "artist:UC3", "playlist:PL4", "play:1:0"), log)
    }

    @Test fun tilesAnnounceTheirTypeToTalkBack() {
        show(state(content(ShelfState.Ready(shelf))))
        rule.onNodeWithTag("home:shelf:0").performScrollToNode(hasText("Playlist 4"))
        rule.onNode(hasContentDescription(str(R.string.browse_desc_song, "Song 1"))).assertExists()
        rule.onNode(hasContentDescription(str(R.string.browse_desc_album, "Album 2"))).assertExists()
        rule.onNode(hasContentDescription(str(R.string.browse_desc_artist, "Artist 3"))).assertExists()
        rule.onNode(hasContentDescription(str(R.string.browse_desc_playlist, "Playlist 4"))).assertExists()
    }

    @Test fun repeatedShelfItemsAndShelfTitlesDoNotCrash() {
        val dup = Shelf("Same", null, listOf(BrowseItem.Album(Samples.album(1)), BrowseItem.Album(Samples.album(1))))
        show(state(content(ShelfState.Ready(dup), ShelfState.Ready(dup))))
        rule.onAllNodesWithText("Same").assertCountEquals(2); rule.onAllNodesWithText("Album 1").assertCountEquals(2)
    }

    @Test fun staleBannerAndRefresh() {
        show(state(content(ShelfState.Ready(shelf), outdated = true)))
        rule.onNodeWithTag(TAG_HOME_STALE).assertIsDisplayed(); rule.onNodeWithText(str(R.string.home_stale)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.home_refresh)).performClick(); assertEquals(listOf("refresh"), log)
    }

    @Test fun noStaleBannerForFreshContent() { show(state(content(ShelfState.Ready(shelf)))); rule.onNodeWithTag(TAG_HOME_STALE).assertDoesNotExist() }

    @Test fun aFailedRefreshWithCachedContentSaysSo() {
        show(state(content(ShelfState.Ready(shelf), refreshFailed = true)))
        rule.onNodeWithText(str(R.string.home_refresh_failed)).assertExists(); rule.onNodeWithText("Fresh picks").assertExists()
    }

    @Test fun failedShelfShowsItsTitleAndNeighboursStay() {
        show(state(content(ShelfState.Ready(shelf), ShelfState.Failed("Charts"))))
        scrollList(hasTestTag(TAG_HOME_SHELF_FAILED))
        rule.onNodeWithTag(TAG_HOME_SHELF_FAILED).assertExists(); rule.onNodeWithText("Charts").assertExists(); rule.onNodeWithText(str(R.string.home_section_failed)).assertExists()
        rule.onNodeWithText("Fresh picks").assertExists()
        rule.onNodeWithText(str(R.string.retry)).performClick(); assertEquals(listOf("refresh"), log)
    }

    @Test fun emptyLocalSectionsShowAHint() {
        show(state(RemoteSection.Offline, quick = emptyList(), recent = emptyList()))
        rule.onNodeWithText(str(R.string.home_quick_picks_hint)).assertExists(); rule.onNodeWithTag("state:offline").assertIsDisplayed()
    }

    @Test fun localSectionsWorkWhileTheRemoteSectionIsOffline() {
        show(state(RemoteSection.Offline)); rule.onNodeWithTag("home:quick:1").performClick(); assertEquals(listOf("play:2:1"), log)
    }

    @Test fun errorSectionRetries() { show(state(RemoteSection.Error)); rule.onNodeWithTag("state:error").assertIsDisplayed(); rule.onNodeWithText(str(R.string.retry)).performClick(); assertEquals(listOf("refresh"), log) }
    @Test fun offlineSectionRetries() { show(state(RemoteSection.Offline)); rule.onNodeWithText(str(R.string.retry)).performClick(); assertEquals(listOf("refresh"), log) }
    @Test fun loadingSection() { show(state(RemoteSection.Loading)); rule.onNodeWithTag("state:loading").assertExists() }

    @Test fun settingsEntryIsReachable() {
        show(state(RemoteSection.Loading))
        rule.onNodeWithContentDescription(str(R.string.home_settings)).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick(); assertEquals(listOf("settings"), log)
    }

    @Test fun likedSongsShowTheirStateAndOfferUnlike() {
        show(state(RemoteSection.Loading, liked = setOf(Samples.track(20).id)))
        rule.onAllNodesWithContentDescription(str(R.string.track_liked)).assertCountEquals(1)
        rule.onNodeWithContentDescription(str(R.string.track_more, "Song 20")).performClick(); rule.onNodeWithText(str(R.string.track_unlike)).performClick()
        rule.onNodeWithContentDescription(str(R.string.track_more, "Song 21")).performClick(); rule.onNodeWithText(str(R.string.track_like)).performClick()
        assertEquals(listOf("menu:Song 20:UNLIKE", "menu:Song 21:LIKE"), log)
    }

    @Test fun survivesPseudolocaleAndBigFonts() {
        show(state(content(ShelfState.Ready(shelf), outdated = true, refreshFailed = true)), pseudo = true, fontScale = 2f)
        rule.onNodeWithTag(TAG_HOME_LIST).assertExists(); rule.onNodeWithTag(TAG_HOME_STALE).assertExists()
    }
}
