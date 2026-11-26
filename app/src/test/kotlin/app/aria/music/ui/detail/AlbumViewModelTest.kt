// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import app.aria.music.ui.common.TrackMenuAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class AlbumViewModelTest {
    // runTest must share this dispatcher, or backgroundScope collectors would run on a second scheduler.
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMain() = Dispatchers.resetMain()

    private class Env(scope: kotlinx.coroutines.CoroutineScope, online: Boolean = true, val library: LibraryRepository = FakeLibraryRepository()) {
        val browse = FakeBrowseSource(); val engine = FakePlaybackEngine(); val network = FakeNetworkMonitor(online)
        val messages = mutableListOf<PlayerMessage>()
        val controller = PlayerController(engine, ServiceLauncher { }, scope)
        val detail = AlbumDetail(Samples.album(1), Samples.tracks(3), listOf(Samples.album(2)))
        init { browse.albums["MPREb_1"] = Result.success(detail) }
        val vm = AlbumViewModel(SavedStateHandle(mapOf("id" to "MPREb_1")), browse, library, controller, network)
            .also { scope.launch { it.state.collect { } }; scope.launch { controller.messages.collect { m -> messages += m } } }
    }

    @Test fun loadsContentWithSavedFlag() = runTest(dispatcher) {
        val e = Env(backgroundScope); val c = e.vm.state.value as AlbumUiState.Content
        assertEquals(e.detail, c.detail); assertFalse(c.saved)
    }

    @Test fun toggleSavedWritesTheLibraryAndFlipsTheFlag() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.vm.toggleSaved()
        assertTrue((e.vm.state.value as AlbumUiState.Content).saved); assertEquals(listOf(Samples.album(1)), e.library.albums("", LibrarySort.TITLE).first())
        e.vm.toggleSaved(); assertFalse((e.vm.state.value as AlbumUiState.Content).saved)
    }

    @Test fun playStartsFromTheTappedTrackAndShuffleUsesAllTracks() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.vm.play(2); e.vm.shuffle(); e.vm.addToQueue()
        assertEquals(Samples.tracks(3) to 2, e.engine.playNowCalls[0]); assertEquals(3, e.engine.playNowCalls[1].first.size); assertEquals(1, e.engine.addedToEnd.size)
    }

    @Test fun missingAlbumIsAnErrorAndRetryReloads() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.browse.albums["MPREb_1"] = Result.failure(java.io.IOException())
        e.vm.retry(); assertEquals(AlbumUiState.Error, e.vm.state.value)
        e.browse.albums["MPREb_1"] = Result.success(e.detail); e.vm.retry(); assertTrue(e.vm.state.value is AlbumUiState.Content)
    }

    @Test fun anUnknownIdIsAnErrorNotACrash() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.browse.albums.clear(); e.vm.retry()
        assertEquals(AlbumUiState.Error, e.vm.state.value)
    }

    @Test fun offlineDoesNotCallTheNetwork() = runTest(dispatcher) {
        val counting = object : BrowseSource by FakeBrowseSource() { var calls = 0; override suspend fun album(id: String): Result<AlbumDetail> { calls++; return Result.failure(java.io.IOException()) } }
        val vm = AlbumViewModel(SavedStateHandle(mapOf("id" to "x")), counting, FakeLibraryRepository(), PlayerController(FakePlaybackEngine(), ServiceLauncher { }, backgroundScope), FakeNetworkMonitor(false))
        backgroundScope.launch { vm.state.collect { } }
        assertEquals(AlbumUiState.Offline, vm.state.value); assertEquals(0, counting.calls)
    }

    @Test fun retryWhileStillOfflineStaysOffline() = runTest(dispatcher) {
        val e = Env(backgroundScope, online = false); e.vm.retry(); assertEquals(AlbumUiState.Offline, e.vm.state.value)
        e.network.online.value = true; e.vm.retry(); assertTrue(e.vm.state.value is AlbumUiState.Content)
    }

    @Test fun trackMenuActionsReachTheControllerAndLibrary() = runTest(dispatcher) {
        val e = Env(backgroundScope); val t = Samples.track(1)
        e.vm.trackAction(t, TrackMenuAction.PLAY_NEXT); e.vm.trackAction(t, TrackMenuAction.ADD_TO_QUEUE); e.vm.trackAction(t, TrackMenuAction.LIKE)
        assertTrue("playNext" in e.engine.calls); assertTrue(e.library.isLiked(t.id).first())
        assertTrue(t.id in (e.vm.state.value as AlbumUiState.Content).liked)
        e.vm.trackAction(t, TrackMenuAction.UNLIKE); assertFalse(e.library.isLiked(t.id).first())
        assertTrue((e.vm.state.value as AlbumUiState.Content).liked.isEmpty())
    }

    @Test fun aFailingSaveIsReportedNotThrown() = runTest(dispatcher) {
        val broken = object : LibraryRepository by FakeLibraryRepository() { override suspend fun setAlbumSaved(album: AlbumRef, saved: Boolean) = error("db") }
        val e = Env(backgroundScope, library = broken); e.vm.toggleSaved()
        assertEquals(listOf<PlayerMessage>(PlayerMessage.LikeFailed), e.messages)
    }

    @Test fun anAlbumOverTheQueueCapIsWindowedByTheController() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.browse.albums["MPREb_1"] = Result.success(AlbumDetail(Samples.album(1), Samples.tracks(700), emptyList())); e.vm.retry()
        e.vm.play(650)
        assertEquals(50, e.engine.playNowCalls.single().first.size); assertEquals(listOf<PlayerMessage>(PlayerMessage.QueueWindowed(50, 700)), e.messages)
    }

    @Test fun toggleSavedFlipsTheStoredValueNotTheScreensCopy() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.library.setAlbumSaved(Samples.album(1), true)
        e.vm.toggleSaved()
        assertFalse((e.vm.state.value as AlbumUiState.Content).saved); assertEquals(emptyList(), e.library.albums("", LibrarySort.TITLE).first())
    }

    @Test fun backToBackTogglesAlternate() = runTest(dispatcher) {
        val writes = mutableListOf<Boolean>(); val base = FakeLibraryRepository()
        val e = Env(backgroundScope, library = object : LibraryRepository by base { override suspend fun setAlbumSaved(album: AlbumRef, saved: Boolean) { writes += saved; base.setAlbumSaved(album, saved) } })
        e.vm.toggleSaved(); e.vm.toggleSaved(); e.vm.toggleSaved()
        assertEquals(listOf(true, false, true), writes)
    }
}
