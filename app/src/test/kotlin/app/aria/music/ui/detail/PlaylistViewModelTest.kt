// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import app.aria.music.ui.common.TrackMenuAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PlaylistViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMain() = Dispatchers.resetMain()

    private class Env(scope: kotlinx.coroutines.CoroutineScope, online: Boolean = true, val browse: BrowseSource = FakeBrowseSource().also { it.playlists["PL1"] = Result.success(PlaylistDetail(Samples.playlist(1), Samples.tracks(2), continuation = "c1")) }) {
        val fake = browse as? FakeBrowseSource; val engine = FakePlaybackEngine(); val library = FakeLibraryRepository()
        val vm = PlaylistViewModel(SavedStateHandle(mapOf("id" to "PL1")), browse, library, PlayerController(engine, ServiceLauncher { }, scope), FakeNetworkMonitor(online))
            .also { scope.launch { it.state.collect { } } }
    }

    @Test fun loadMoreAppendsAndDeduplicatesUntilNoContinuation() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.fake!!.more["c1"] = Result.success(TrackPage(Samples.tracks(3, from = 2), "c2")); e.fake.more["c2"] = Result.success(TrackPage(Samples.tracks(1, from = 9), null))
        e.vm.loadMore(); e.vm.loadMore(); e.vm.loadMore()
        val c = e.vm.state.value as PlaylistUiState.Content
        assertEquals(listOf(1, 2, 3, 4, 9).map { Samples.track(it) }, c.detail.tracks); assertNull(c.detail.continuation); assertFalse(c.moreFailed)
    }

    @Test fun failedLoadMoreKeepsTracksAndCanRetry() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.vm.loadMore()
        var c = e.vm.state.value as PlaylistUiState.Content; assertTrue(c.moreFailed); assertEquals(2, c.detail.tracks.size)
        e.fake!!.more["c1"] = Result.success(TrackPage(Samples.tracks(1, from = 7), null)); e.vm.loadMore()
        c = e.vm.state.value as PlaylistUiState.Content; assertFalse(c.moreFailed); assertEquals(3, c.detail.tracks.size)
    }

    @Test fun onlyOnePageIsRequestedAtATime() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<TrackPage>>(); var calls = 0
        val base = FakeBrowseSource().also { it.playlists["PL1"] = Result.success(PlaylistDetail(Samples.playlist(1), Samples.tracks(2), "c1")) }
        val slow = object : BrowseSource by base {
            override suspend fun playlistMore(continuation: String): Result<TrackPage> { calls++; return gate.await() }
        }
        val e = Env(backgroundScope, browse = slow); e.vm.loadMore(); e.vm.loadMore()
        assertEquals(1, calls); assertTrue((e.vm.state.value as PlaylistUiState.Content).loadingMore)
        gate.complete(Result.success(TrackPage(Samples.tracks(1, from = 5), null))); assertEquals(3, (e.vm.state.value as PlaylistUiState.Content).detail.tracks.size)
    }

    @Test fun playUsesLoadedTracks() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.vm.play(1); e.vm.shuffle(); e.vm.addToQueue()
        assertEquals(Samples.tracks(2) to 1, e.engine.playNowCalls[0]); assertEquals(2, e.engine.playNowCalls[1].first.size); assertEquals(1, e.engine.addedToEnd.size)
    }

    @Test fun offlineAndMissing() = runTest(dispatcher) {
        assertEquals(PlaylistUiState.Offline, Env(backgroundScope, online = false).vm.state.value)
        val e = Env(backgroundScope); e.fake!!.playlists["PL1"] = Result.failure(java.io.IOException()); e.vm.retry(); assertEquals(PlaylistUiState.Error, e.vm.state.value)
        e.fake.playlists.clear(); e.vm.retry(); assertEquals(PlaylistUiState.Error, e.vm.state.value)
    }

    @Test fun likedMarksFollowTheLibraryAndSurviveLoadMore() = runTest(dispatcher) {
        val e = Env(backgroundScope); val t = Samples.track(1)
        e.vm.trackAction(t, TrackMenuAction.LIKE); assertEquals(setOf(t.id), (e.vm.state.value as PlaylistUiState.Content).liked)
        e.fake!!.more["c1"] = Result.success(TrackPage(Samples.tracks(1, from = 7), null)); e.vm.loadMore()
        assertEquals(setOf(t.id), (e.vm.state.value as PlaylistUiState.Content).liked)
        e.vm.trackAction(t, TrackMenuAction.UNLIKE); assertTrue((e.vm.state.value as PlaylistUiState.Content).liked.isEmpty())
    }
}
