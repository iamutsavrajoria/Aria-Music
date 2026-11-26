// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import app.aria.music.ui.common.TrackMenuAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ArtistViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMain() = Dispatchers.resetMain()

    private class Env(scope: kotlinx.coroutines.CoroutineScope, online: Boolean = true, val library: LibraryRepository = FakeLibraryRepository()) {
        val browse = FakeBrowseSource(); val engine = FakePlaybackEngine(); val network = FakeNetworkMonitor(online)
        val songs = Shelf("Songs", null, Samples.tracks(2).map { BrowseItem.Song(it) })
        val detail = ArtistDetail(Samples.artist(1), "About", "1M", listOf(songs, Shelf("Albums", null, listOf(BrowseItem.Album(Samples.album(1))))))
        init { browse.artists["UC1"] = Result.success(detail) }
        val vm = ArtistViewModel(SavedStateHandle(mapOf("id" to "UC1")), browse, library, PlayerController(engine, ServiceLauncher { }, scope), network)
            .also { scope.launch { it.state.collect { } } }
    }

    @Test fun contentAndFollowToggle() = runTest(dispatcher) {
        val e = Env(backgroundScope); assertFalse((e.vm.state.value as ArtistUiState.Content).followed)
        e.vm.toggleFollowed(); assertTrue((e.vm.state.value as ArtistUiState.Content).followed); assertEquals(listOf(Samples.artist(1)), e.library.artists("", LibrarySort.TITLE).first())
        e.vm.toggleFollowed(); assertFalse((e.vm.state.value as ArtistUiState.Content).followed)
    }

    @Test fun playAndShuffleUseTheTopTracks() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.vm.playTop(); e.vm.shuffleTop()
        assertEquals(Samples.tracks(2) to 0, e.engine.playNowCalls[0]); assertEquals(2, e.engine.playNowCalls[1].first.size)
    }

    @Test fun playingAShelfSongStartsThere() = runTest(dispatcher) { val e = Env(backgroundScope); e.vm.playShelfSongs(Samples.tracks(2), 1); assertEquals(Samples.tracks(2) to 1, e.engine.playNowCalls.single()) }

    @Test fun anArtistWithoutSongsPlaysNothing() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.browse.artists["UC1"] = Result.success(ArtistDetail(Samples.artist(1), null, null, emptyList())); e.vm.retry()
        e.vm.playTop(); e.vm.shuffleTop(); assertTrue(e.engine.playNowCalls.isEmpty())
    }

    @Test fun failureThenRetry() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.browse.artists["UC1"] = Result.failure(java.io.IOException()); e.vm.retry(); assertEquals(ArtistUiState.Error, e.vm.state.value)
        e.browse.artists["UC1"] = Result.success(e.detail); e.vm.retry(); assertTrue(e.vm.state.value is ArtistUiState.Content)
    }

    @Test fun anUnknownIdIsAnErrorAndOfflineSkipsTheNetwork() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.browse.artists.clear(); e.vm.retry(); assertEquals(ArtistUiState.Error, e.vm.state.value)
        assertEquals(ArtistUiState.Offline, Env(backgroundScope, online = false).vm.state.value)
    }

    @Test fun likedSongsOfTheShelvesAreMarkedAndUnlikeWorks() = runTest(dispatcher) {
        val e = Env(backgroundScope); val t = Samples.track(1)
        e.vm.trackAction(t, TrackMenuAction.LIKE); assertEquals(setOf(t.id), (e.vm.state.value as ArtistUiState.Content).liked)
        e.vm.trackAction(t, TrackMenuAction.UNLIKE); assertTrue((e.vm.state.value as ArtistUiState.Content).liked.isEmpty())
        e.vm.trackAction(t, TrackMenuAction.PLAY_NEXT); assertTrue("playNext" in e.engine.calls)
    }

    @Test fun toggleFollowedUsesTheStoredValueEvenWhenTheScreenCopyLags() = runTest(dispatcher) {
        val base = FakeLibraryRepository(); val writes = mutableListOf<Boolean>()
        val lagging = object : LibraryRepository by base {
            override fun isArtistFollowed(id: String) = flow { emit(base.isArtistFollowed(id).first()); awaitCancellation() }
            override suspend fun setArtistFollowed(artist: ArtistRef, followed: Boolean) { writes += followed; base.setArtistFollowed(artist, followed) }
        }
        val e = Env(backgroundScope, library = lagging)
        assertFalse((e.vm.state.value as ArtistUiState.Content).followed)
        base.setArtistFollowed(Samples.artist(1), true); writes.clear()
        assertFalse((e.vm.state.value as ArtistUiState.Content).followed)  // still the stale copy
        e.vm.toggleFollowed()
        assertEquals(listOf(false), writes)                                // the old `!screenCopy` code would write true
        assertTrue(base.artists("", LibrarySort.TITLE).first().isEmpty())
    }

    @Test fun toggleFollowedFlipsTheStoredValueNotTheScreensCopy() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.library.setArtistFollowed(Samples.artist(1), true)
        e.vm.toggleFollowed()
        assertFalse((e.vm.state.value as ArtistUiState.Content).followed); assertEquals(emptyList(), e.library.artists("", LibrarySort.TITLE).first())
    }
}
