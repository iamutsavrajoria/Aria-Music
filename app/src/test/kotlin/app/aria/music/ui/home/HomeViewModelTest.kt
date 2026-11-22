// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.home

import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private class Env(val scope: kotlinx.coroutines.CoroutineScope, online: Boolean = true, libraryRepo: LibraryRepository? = null) {
        val home = FakeHomeRepository(); val library = FakeLibraryRepository(); val engine = FakePlaybackEngine(); val network = FakeNetworkMonitor(online)
        val controller = PlayerController(engine, ServiceLauncher { }, scope)
        val messages = mutableListOf<PlayerMessage>()
        val vm by lazy {
            HomeViewModel(home, libraryRepo ?: library, controller, network).also {
                scope.launch { it.state.collect { } }; scope.launch { controller.messages.collect { messages += it } }
            }
        }
    }
    private fun shelf(t: String) = Shelf(t, null, listOf(BrowseItem.Song(Samples.track(1))))

    @Test fun cacheShowsImmediatelyAndRevalidatesOnlyWhenNeeded() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.home.remoteState.value = HomeRemote(listOf(shelf("A")), 1L, outdated = false); e.home.revalidate = false
        e.vm; advanceUntilIdle()
        val c = e.vm.state.value.remote as RemoteSection.Content
        assertEquals(listOf(ShelfState.Ready(shelf("A"))), c.shelves); assertFalse(c.outdated); assertEquals(0, e.home.refreshCalls)
    }

    @Test fun staleCacheIsMarkedAndRevalidated() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.home.remoteState.value = HomeRemote(listOf(shelf("A")), 1L, outdated = true)
        e.home.refreshResult = Result.failure(java.io.IOException())
        e.vm; advanceUntilIdle()
        val c = e.vm.state.value.remote as RemoteSection.Content
        assertTrue(c.outdated); assertTrue(c.refreshFailed); assertEquals(1, e.home.refreshCalls)
        assertEquals(emptyList(), e.messages)   // the automatic revalidation is silent; the banner already says so
    }

    @Test fun freshResponseShowsFailedShelvesInPlace() = runTest(dispatcher) {
        val e = Env(backgroundScope)
        e.home.refreshResult = Result.success(HomeRefresh(listOf(ShelfState.Ready(shelf("A")), ShelfState.Failed("B"))))
        e.vm; advanceUntilIdle()
        val c = e.vm.state.value.remote as RemoteSection.Content
        assertEquals(listOf(ShelfState.Ready(shelf("A")), ShelfState.Failed("B")), c.shelves); assertFalse(c.refreshFailed)
    }

    @Test fun noCacheAndFailureIsErrorThenRetrySucceeds() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.home.refreshResult = Result.failure(java.io.IOException())
        e.vm; advanceUntilIdle(); assertEquals(RemoteSection.Error, e.vm.state.value.remote)
        e.home.refreshResult = Result.success(HomeRefresh(listOf(ShelfState.Ready(shelf("A"))))); e.vm.refresh(); advanceUntilIdle()
        assertTrue(e.vm.state.value.remote is RemoteSection.Content)
    }

    @Test fun offlineWithoutCacheNeverCallsTheNetwork() = runTest(dispatcher) {
        val e = Env(backgroundScope, online = false); e.vm; advanceUntilIdle()
        assertEquals(RemoteSection.Offline, e.vm.state.value.remote); assertEquals(0, e.home.refreshCalls)
        e.vm.refresh(); advanceUntilIdle(); assertEquals(0, e.home.refreshCalls)
    }

    @Test fun comingBackOnlineLoadsTheMissingShelves() = runTest(dispatcher) {
        val e = Env(backgroundScope, online = false)
        e.home.refreshResult = Result.success(HomeRefresh(listOf(ShelfState.Ready(shelf("A")))))
        e.vm; advanceUntilIdle(); assertEquals(RemoteSection.Offline, e.vm.state.value.remote)
        e.network.online.value = true; advanceUntilIdle()
        assertEquals(1, e.home.refreshCalls); assertTrue(e.vm.state.value.remote is RemoteSection.Content)
    }

    @Test fun cachedShelvesStillShowOffline() = runTest(dispatcher) {
        val e = Env(backgroundScope, online = false); e.home.remoteState.value = HomeRemote(listOf(shelf("A")), 1L, outdated = false)
        e.vm; advanceUntilIdle()
        assertTrue(e.vm.state.value.remote is RemoteSection.Content); assertEquals(0, e.home.refreshCalls)
    }

    @Test fun aFailedPullToRefreshKeepsTheCacheAndTellsTheUser() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.home.remoteState.value = HomeRemote(listOf(shelf("A")), 1L, outdated = false); e.home.revalidate = false
        e.home.refreshResult = Result.failure(java.io.IOException())
        e.vm; advanceUntilIdle(); e.vm.refresh(); advanceUntilIdle()
        val c = e.vm.state.value.remote as RemoteSection.Content
        assertEquals(listOf(ShelfState.Ready(shelf("A"))), c.shelves); assertTrue(c.refreshFailed)
        assertEquals(listOf<PlayerMessage>(PlayerMessage.RefreshFailed), e.messages); assertFalse(e.vm.state.value.refreshing)
    }

    @Test fun localSectionsAreIndependentOfTheRemoteOne() = runTest(dispatcher) {
        val e = Env(backgroundScope, online = false); e.home.quick.value = Samples.tracks(2); e.home.recent.value = Samples.tracks(1, from = 5)
        e.vm; advanceUntilIdle()
        assertEquals(2, e.vm.state.value.quickPicks.size); assertEquals(listOf(Samples.track(5)), e.vm.state.value.recent)
    }

    @Test fun playingUsesTheController() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.vm.play(Samples.tracks(3), 2); advanceUntilIdle()
        assertEquals(listOf(Samples.tracks(3) to 2), e.engine.playNowCalls)
    }

    @Test fun likeAndUnlikeUpdateTheLikedSetOfTheVisibleSongs() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.home.quick.value = Samples.tracks(2)
        e.vm; advanceUntilIdle(); assertEquals(emptySet(), e.vm.state.value.liked)
        e.vm.like(Samples.track(1)); advanceUntilIdle(); assertEquals(setOf(Samples.track(1).id), e.vm.state.value.liked)
        e.vm.unlike(Samples.track(1)); advanceUntilIdle(); assertEquals(emptySet(), e.vm.state.value.liked)
    }

    @Test fun aFailingLikeIsReportedNotThrown() = runTest(dispatcher) {
        val failing = object : LibraryRepository by FakeLibraryRepository() {
            override suspend fun setLiked(track: Track, liked: Boolean) { throw IllegalStateException("db") }
        }
        val e = Env(backgroundScope, libraryRepo = failing); e.vm.like(Samples.track(1)); advanceUntilIdle()
        assertEquals(listOf<PlayerMessage>(PlayerMessage.LikeFailed), e.messages)
    }

    @Test fun queueActionsUseTheController() = runTest(dispatcher) {
        val e = Env(backgroundScope); val songs = Samples.tracks(3)
        e.engine.mutableState.value = PlaybackState(queue = songs.mapIndexed { i, t -> QueueItem(100L + i, t) }, currentIndex = 0); advanceUntilIdle()
        e.vm.playNext(songs[1]); e.vm.addToQueue(Samples.track(9)); advanceUntilIdle()
        assertTrue("playNext" in e.engine.calls); assertEquals(listOf(listOf(Samples.track(9))), e.engine.addedToEnd)
    }

    @Test fun aLongSectionPlaysThroughTheWindowRule() = runTest(dispatcher) {
        val e = Env(backgroundScope); val songs = Samples.tracks(PlayerController.QUEUE_CAP + 100)
        e.vm.play(songs, 50); advanceUntilIdle()
        assertEquals(PlayerController.QUEUE_CAP, e.engine.playNowCalls.single().first.size); assertEquals(0, e.engine.playNowCalls.single().second)
        assertEquals(listOf<PlayerMessage>(PlayerMessage.QueueWindowed(PlayerController.QUEUE_CAP, songs.size)), e.messages)
    }

    @Test fun likedStateFollowsTheLibraryNotJustTheCall() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.home.recent.value = Samples.tracks(1, from = 7)
        e.library.setLiked(Samples.track(7), true)
        e.vm; advanceUntilIdle(); assertEquals(setOf(Samples.track(7).id), e.vm.state.value.liked)
    }
}
