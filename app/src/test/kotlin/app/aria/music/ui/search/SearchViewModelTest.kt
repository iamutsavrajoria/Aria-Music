// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.search

import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private class Env(scope: kotlinx.coroutines.CoroutineScope, historyRepo: SearchHistoryRepository? = null, libraryRepo: LibraryRepository? = null) {
        val search = FakeSearchRepository(); val history = FakeSearchHistoryRepository(listOf("old")); val library = FakeLibraryRepository(); val engine = FakePlaybackEngine(); val network = FakeNetworkMonitor()
        val controller = PlayerController(engine, ServiceLauncher { }, scope)
        val vm = SearchViewModel(search, historyRepo ?: history, libraryRepo ?: library, controller, network)
        val messages = mutableListOf<PlayerMessage>()
        init { scope.launch { vm.state.collect { } }; scope.launch { controller.messages.collect { messages += it } } }
    }
    private val results = SearchResults(Samples.tracks(2), listOf(Samples.album(1)), emptyList(), emptyList())

    @Test fun blankQueryShowsHistory() = runTest(dispatcher) {
        val e = Env(backgroundScope); advanceUntilIdle()
        assertEquals(SearchUiState("", SearchMode.Idle(listOf("old"))), e.vm.state.value)
    }

    @Test fun suggestionsAreDebouncedToTheLastQuery() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.search.suggestionResult = Result.success(listOf("daft punk", "daft"))
        e.vm.onQueryChange("d"); advanceTimeBy(100); e.vm.onQueryChange("da"); advanceTimeBy(100); e.vm.onQueryChange("daf"); advanceTimeBy(350); runCurrent()
        assertEquals(listOf("daf"), e.search.suggestionQueries)
        assertEquals(SearchMode.Suggestions(listOf("daft punk", "daft"), listOf("old")), e.vm.state.value.mode)
    }

    @Test fun suggestionFailureFallsBackToHistoryOnly() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.search.suggestionResult = Result.failure(RuntimeException())
        e.vm.onQueryChange("x"); advanceTimeBy(400); runCurrent()
        assertEquals(SearchMode.Suggestions(emptyList(), listOf("old")), e.vm.state.value.mode)
    }

    @Test fun suggestionsAreNotRequestedWhileOffline() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.network.online.value = false
        e.vm.onQueryChange("x"); advanceTimeBy(400); runCurrent()
        assertEquals(emptyList(), e.search.suggestionQueries)
    }

    @Test fun submitSearchesStoresHistoryAndShowsResults() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.search.searchResult = Result.success(results)
        e.vm.onQueryChange("daft"); e.vm.submit(); advanceUntilIdle()
        assertEquals(SearchMode.Results(results), e.vm.state.value.mode); assertEquals("daft", e.history.items.value.first())
    }

    @Test fun emptyErrorAndOfflineStates() = runTest(dispatcher) {
        val e = Env(backgroundScope)
        e.search.searchResult = Result.success(SearchResults(emptyList(), emptyList(), emptyList(), emptyList())); e.vm.submit("a"); advanceUntilIdle()
        assertEquals(SearchMode.Empty, e.vm.state.value.mode)
        e.search.searchResult = Result.failure(java.io.IOException()); e.vm.submit("b"); advanceUntilIdle()
        assertEquals(SearchMode.Error, e.vm.state.value.mode)
        e.network.online.value = false; val calls = e.search.searchQueries.size; e.vm.submit("c"); advanceUntilIdle()
        assertEquals(SearchMode.Offline, e.vm.state.value.mode); assertEquals(calls, e.search.searchQueries.size)
    }

    @Test fun retryRerunsTheLastSubmittedQuery() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.search.searchResult = Result.failure(java.io.IOException()); e.vm.submit("q"); advanceUntilIdle()
        e.search.searchResult = Result.success(results); e.vm.retry(); advanceUntilIdle()
        assertEquals(listOf("q", "q"), e.search.searchQueries); assertTrue(e.vm.state.value.mode is SearchMode.Results)
    }

    @Test fun editingAfterResultsLeavesResultsMode() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.search.searchResult = Result.success(results); e.vm.submit("q"); advanceUntilIdle()
        e.vm.onQueryChange("qq"); advanceTimeBy(400); runCurrent()
        assertTrue(e.vm.state.value.mode is SearchMode.Suggestions)
    }

    @Test fun blankSubmitIsIgnored() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.vm.submit("   "); advanceUntilIdle()
        assertEquals(emptyList(), e.search.searchQueries); assertEquals(listOf("old"), e.history.items.value)
    }

    @Test fun historyCanBeRemovedAndCleared() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.history.add("two"); advanceUntilIdle()
        e.vm.removeHistory("old"); advanceUntilIdle(); assertEquals(listOf("two"), e.history.items.value)
        e.vm.clearHistory(); advanceUntilIdle(); assertEquals(SearchMode.Idle(emptyList()), e.vm.state.value.mode)
    }

    @Test fun playingASongUsesTheControllerAndLikeWritesTheLibrary() = runTest(dispatcher) {
        val e = Env(backgroundScope); val songs = Samples.tracks(3)
        e.vm.playSong(songs, 1); advanceUntilIdle()
        assertEquals(listOf(songs to 1), e.engine.playNowCalls)
        // With a queue present, "add to queue" appends instead of starting playback.
        e.engine.mutableState.value = PlaybackState(queue = songs.mapIndexed { i, t -> QueueItem(100L + i, t) }, currentIndex = 1); advanceUntilIdle()
        e.vm.playNext(songs[0]); e.vm.addToQueue(songs[2]); e.vm.toggleLike(songs[0], true); advanceUntilIdle()
        assertTrue("playNext" in e.engine.calls); assertEquals(listOf(listOf(songs[2])), e.engine.addedToEnd)
        assertEquals(1, e.engine.playNowCalls.size)
        assertEquals(songs[0], e.library.songs("", LibrarySort.TITLE).first().single().track)
    }

    @Test fun aLongResultListPlaysThroughTheWindowRule() = runTest(dispatcher) {
        val e = Env(backgroundScope); val songs = Samples.tracks(PlayerController.QUEUE_CAP + 100)
        e.vm.playSong(songs, 10); advanceUntilIdle()
        assertEquals(PlayerController.QUEUE_CAP, e.engine.playNowCalls.single().first.size)
        assertEquals(songs[10], e.engine.playNowCalls.single().first.first())
    }

    @Test fun aFailingLikeIsReportedNotThrown() = runTest(dispatcher) {
        val failing = object : LibraryRepository by FakeLibraryRepository() {
            override suspend fun setLiked(track: Track, liked: Boolean) { throw IllegalStateException("db") }
        }
        val e = Env(backgroundScope, libraryRepo = failing); e.vm.toggleLike(Samples.track(1), true); advanceUntilIdle()
        assertEquals(listOf<PlayerMessage>(PlayerMessage.LikeFailed), e.messages)
    }

    @Test fun aFailingHistoryWriteIsReportedNotThrown() = runTest(dispatcher) {
        val failing = object : SearchHistoryRepository by FakeSearchHistoryRepository() {
            override suspend fun remove(query: String) { throw IllegalStateException("db") }
            override suspend fun clear() { throw IllegalStateException("db") }
            override suspend fun add(query: String) { throw IllegalStateException("db") }
        }
        val e = Env(backgroundScope, historyRepo = failing); e.search.searchResult = Result.success(results)
        e.vm.removeHistory("x"); e.vm.clearHistory(); e.vm.submit("q"); advanceUntilIdle()
        assertEquals(3, e.messages.count { it == PlayerMessage.HistoryFailed })
        assertTrue(e.vm.state.value.mode is SearchMode.Results)
    }

    @Test fun anUnexpectedSearchExceptionBecomesTheErrorState() = runTest(dispatcher) {
        val throwing = object : SearchRepository {
            override suspend fun suggestions(query: String): Result<List<String>> = throw IllegalStateException("boom")
            override suspend fun search(query: String): Result<SearchResults> = throw IllegalStateException("boom")
        }
        val vm = SearchViewModel(throwing, FakeSearchHistoryRepository(), FakeLibraryRepository(), PlayerController(FakePlaybackEngine(), ServiceLauncher { }, backgroundScope), FakeNetworkMonitor())
        backgroundScope.launch { vm.state.collect { } }
        vm.onQueryChange("a"); advanceTimeBy(400); runCurrent(); vm.submit("a"); advanceUntilIdle()
        assertEquals(SearchMode.Error, vm.state.value.mode)
    }

    @Test fun resultSongsCarryTheirLikedStateFromTheLibrary() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.search.searchResult = Result.success(results); e.library.setLiked(results.songs[1], true)
        e.vm.submit("q"); advanceUntilIdle()
        assertEquals(setOf(results.songs[1].id), e.vm.state.value.liked)
        e.vm.toggleLike(results.songs[0], true); advanceUntilIdle()
        assertEquals(results.songs.map { it.id }.toSet(), e.vm.state.value.liked)
        e.vm.toggleLike(results.songs[0], false); advanceUntilIdle()
        assertEquals(setOf(results.songs[1].id), e.vm.state.value.liked)
        assertTrue(e.library.isInLibrary(results.songs[0].id).first())   // unliking keeps the song in the library
    }

    @Test fun aFailingLikedLookupKeepsTheResultsAndReadsAsNotLiked() = runTest(dispatcher) {
        val broken = object : LibraryRepository by FakeLibraryRepository() { override fun isLiked(id: TrackId) = flow<Boolean> { throw IllegalStateException("db") } }
        val e = Env(backgroundScope, libraryRepo = broken); e.search.searchResult = Result.success(results); e.vm.submit("q"); advanceUntilIdle()
        assertEquals(SearchMode.Results(results), e.vm.state.value.mode); assertEquals(emptySet(), e.vm.state.value.liked)
    }

    @Test fun likedLookupDoesNotSearchAgain() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.search.searchResult = Result.success(results); e.vm.submit("q"); advanceUntilIdle()
        e.vm.toggleLike(results.songs[0], true); advanceUntilIdle()
        assertEquals(listOf("q"), e.search.searchQueries)
    }
}
