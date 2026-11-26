// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.library

import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private class Env(scope: kotlinx.coroutines.CoroutineScope, val library: LibraryRepository = FakeLibraryRepository(), val fake: FakeLibraryRepository = library as? FakeLibraryRepository ?: FakeLibraryRepository()) {
        val playlists = FakePlaylistRepository(); val engine = FakePlaybackEngine(); val messages = mutableListOf<PlayerMessage>()
        val controller = PlayerController(engine, ServiceLauncher { }, scope)
        val vm = LibraryViewModel(library, playlists, controller).also { scope.launch { it.state.collect { } }; scope.launch { controller.messages.collect { m -> messages += m } } }
    }

    private fun songs(e: Env) = (e.vm.state.value.content as LibraryContent.Songs).rows

    @Test fun emptyThenSongsThenNoMatches() = runTest(dispatcher) {
        val e = Env(backgroundScope); assertEquals(LibraryContent.Empty, e.vm.state.value.content)
        e.fake.setInLibrary(Samples.track(1), true); e.fake.setInLibrary(Samples.track(2, "Zebra"), true)
        assertEquals(2, songs(e).size)
        e.vm.setQuery("nothing-like-this"); assertEquals(LibraryContent.NoMatches, e.vm.state.value.content)
    }

    @Test fun anEmptyTabWithNoFilterIsEmptyNotNoMatches() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.vm.setTab(LibraryTab.ALBUMS)
        assertEquals(LibraryContent.Empty, e.vm.state.value.content)
    }

    @Test fun sortAndFilterAreAppliedByTheRepository() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.fake.setInLibrary(Samples.track(1, "Banana"), true); e.fake.setInLibrary(Samples.track(2, "apple"), true)
        e.vm.setSort(LibrarySort.TITLE)
        assertEquals(listOf("apple", "Banana"), songs(e).map { it.track.title })
        e.vm.setQuery("ban"); assertEquals(listOf("Banana"), songs(e).map { it.track.title })
    }

    @Test fun onlyLibrarySongsAreListedAndLikedIsCarried() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.fake.setLiked(Samples.track(1), true); e.fake.setInLibrary(Samples.track(2), true)
        assertEquals(mapOf(Samples.track(1).id to true, Samples.track(2).id to false), songs(e).associate { it.track.id to it.liked })
    }

    @Test fun tabsSwitchContent() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.fake.setAlbumSaved(Samples.album(1), true); e.fake.setArtistFollowed(Samples.artist(1), true); e.playlists.create("Mix")
        e.vm.setTab(LibraryTab.ALBUMS); assertEquals(listOf(Samples.album(1)), (e.vm.state.value.content as LibraryContent.Albums).rows)
        e.vm.setTab(LibraryTab.ARTISTS); assertEquals(listOf(Samples.artist(1)), (e.vm.state.value.content as LibraryContent.Artists).rows)
        e.vm.setTab(LibraryTab.PLAYLISTS); assertEquals(listOf("Mix"), (e.vm.state.value.content as LibraryContent.Playlists).rows.map { it.title })
    }

    @Test fun playingFromARowQueuesTheVisibleListFromThere() = runTest(dispatcher) {
        val e = Env(backgroundScope); Samples.tracks(3).forEach { e.fake.setInLibrary(it, true) }
        val shown = songs(e).map { it.track }
        e.vm.play(shown, 1)
        assertEquals(listOf(shown to 1), e.engine.playNowCalls)
    }

    @Test fun listsLongerThanTheCapQueueAWindowAndTellTheUser() = runTest(dispatcher) {
        val e = Env(backgroundScope); val many = Samples.tracks(700)
        e.vm.play(many, 650)
        assertEquals(50, e.engine.playNowCalls.single().first.size); assertEquals(listOf<PlayerMessage>(PlayerMessage.QueueWindowed(50, 700)), e.messages)
    }

    @Test fun playAllAndShuffleAllUseTheWholeFilteredList() = runTest(dispatcher) {
        val e = Env(backgroundScope); Samples.tracks(5).forEach { e.fake.setInLibrary(it, true) }
        e.vm.playAll(); e.vm.shuffleAll()
        assertEquals(5, e.engine.playNowCalls[0].first.size); assertEquals(5, e.engine.playNowCalls[1].first.size)
        assertEquals(e.engine.playNowCalls[0].first.toSet(), e.engine.playNowCalls[1].first.toSet())
    }

    @Test fun playAllRespectsTheFilter() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.fake.setInLibrary(Samples.track(1, "Banana"), true); e.fake.setInLibrary(Samples.track(2, "Cherry"), true)
        e.vm.setQuery("ban"); e.vm.playAll()
        assertEquals(listOf("Banana"), e.engine.playNowCalls.single().first.map { it.title })
    }

    @Test fun playAllOnAnEmptyListDoesNothing() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.vm.playAll(); e.vm.shuffleAll(); assertTrue(e.engine.playNowCalls.isEmpty())
    }

    @Test fun songActionsWriteTheLibraryOrQueue() = runTest(dispatcher) {
        val e = Env(backgroundScope); val t = Samples.track(1); e.fake.setLiked(t, true)
        e.vm.trackAction(t, LibraryTrackAction.UNLIKE); assertFalse(e.fake.isLiked(t.id).first()); assertTrue(e.fake.isInLibrary(t.id).first())
        e.vm.trackAction(t, LibraryTrackAction.REMOVE_FROM_LIBRARY); assertFalse(e.fake.isInLibrary(t.id).first())
        e.vm.trackAction(t, LibraryTrackAction.PLAY_NEXT); assertTrue("playNext" in e.engine.calls)
    }

    @Test fun aFailingWriteIsReportedNotThrown() = runTest(dispatcher) {
        val broken = object : LibraryRepository by FakeLibraryRepository() { override suspend fun setInLibrary(track: Track, inLibrary: Boolean) = error("db") }
        val e = Env(backgroundScope, broken)
        e.vm.trackAction(Samples.track(1), LibraryTrackAction.REMOVE_FROM_LIBRARY)
        assertEquals(listOf<PlayerMessage>(PlayerMessage.LikeFailed), e.messages)
    }

    @Test fun createPlaylistTrimsTheNameAndIgnoresBlankOnes() = runTest(dispatcher) {
        val e = Env(backgroundScope)
        e.vm.createPlaylist("   "); e.vm.createPlaylist("")
        assertTrue(e.playlists.playlists("", LibrarySort.TITLE).first().isEmpty())
        e.vm.createPlaylist("  Road trip  ")
        assertEquals(listOf("Road trip"), e.playlists.playlists("", LibrarySort.TITLE).first().map { it.title }); assertTrue(e.messages.isEmpty())
    }

    @Test fun aFailingCreatePlaylistIsReportedAsAPlaylistFailure() = runTest(dispatcher) {
        val base = FakePlaylistRepository()
        val broken = object : PlaylistRepository by base { override suspend fun create(name: String): String = error("db") }
        val messages = mutableListOf<PlayerMessage>()
        val controller = PlayerController(FakePlaybackEngine(), ServiceLauncher { }, backgroundScope)
        backgroundScope.launch { controller.messages.collect { messages += it } }
        LibraryViewModel(FakeLibraryRepository(), broken, controller).createPlaylist("Mix")
        assertEquals(listOf<PlayerMessage>(PlayerMessage.PlaylistFailed), messages)
    }

    @Test fun repositoryFailureShowsTheErrorState() = runTest(dispatcher) {
        val broken = object : LibraryRepository by FakeLibraryRepository() { override fun songs(query: String, sort: LibrarySort) = flow<List<LibrarySong>> { error("db") } }
        assertEquals(LibraryContent.Error, Env(backgroundScope, broken).vm.state.value.content)
    }

    @Test fun selectingTheOpenTabAgainRetriesAfterAnError() = runTest(dispatcher) {
        var fail = true; val base = FakeLibraryRepository(); base.setInLibrary(Samples.track(1), true)
        val flaky = object : LibraryRepository by base { override fun songs(query: String, sort: LibrarySort) = if (fail) flow<List<LibrarySong>> { error("db") } else base.songs(query, sort) }
        val e = Env(backgroundScope, flaky); assertEquals(LibraryContent.Error, e.vm.state.value.content)
        fail = false; e.vm.setTab(LibraryTab.SONGS)
        assertEquals(1, songs(e).size)
    }

    @Test fun anErrorClearsWhenTheFilterChanges() = runTest(dispatcher) {
        var fail = true; val base = FakeLibraryRepository(); base.setInLibrary(Samples.track(1), true)
        val flaky = object : LibraryRepository by base { override fun songs(query: String, sort: LibrarySort) = if (fail) flow<List<LibrarySong>> { error("db") } else base.songs(query, sort) }
        val e = Env(backgroundScope, flaky); fail = false; e.vm.setQuery("Song")
        assertEquals(1, songs(e).size)
    }

    @Test fun aWhitespaceOnlyFilterIsNoFilter() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.fake.setInLibrary(Samples.track(1), true)
        e.vm.setQuery("   ")
        assertEquals("", e.vm.state.value.query); assertEquals(1, songs(e).size)
    }

    @Test fun aWhitespaceOnlyFilterOnAnEmptyTabIsEmptyNotNoMatches() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.vm.setQuery(" \t ")
        assertEquals(LibraryContent.Empty, e.vm.state.value.content)
    }

    @Test fun theFilterIsTrimmedBeforeItReachesTheRepository() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.fake.setInLibrary(Samples.track(1, "Banana"), true); e.fake.setInLibrary(Samples.track(2, "Cherry"), true)
        e.vm.setQuery("  ban "); assertEquals("ban", e.vm.state.value.query); assertEquals(listOf("Banana"), songs(e).map { it.track.title })
    }

    @Test fun changingTheTabNeverShowsTheOldTabsRowsUnderTheNewTab() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.fake.setInLibrary(Samples.track(1), true); e.fake.setAlbumSaved(Samples.album(1), true)
        val seen = mutableListOf<LibraryUiState>()
        backgroundScope.launch { e.vm.state.collect { seen += it } }
        e.vm.setTab(LibraryTab.ALBUMS)
        assertTrue(seen.none { it.tab == LibraryTab.ALBUMS && it.content is LibraryContent.Songs }, "stale frame: $seen")
        assertTrue(seen.none { it.tab == LibraryTab.SONGS && it.content is LibraryContent.Albums }, "stale frame: $seen")
        assertEquals(listOf(Samples.album(1)), (e.vm.state.value.content as LibraryContent.Albums).rows)
    }

    @Test fun aFailedTabShowsLoadingAtOnceWhenRetried() = runTest(dispatcher) {
        val failing = object : LibraryRepository by FakeLibraryRepository() {
            var fail = true
            override fun songs(query: String, sort: LibrarySort): Flow<List<LibrarySong>> = if (fail) flow { throw java.io.IOException() } else flowOf(emptyList())
        }
        val e = Env(backgroundScope, failing)
        assertEquals(LibraryContent.Error, e.vm.state.value.content)
        val seen = mutableListOf<LibraryContent>(); backgroundScope.launch { e.vm.state.collect { seen += it.content } }
        failing.fail = false; e.vm.setTab(LibraryTab.SONGS)
        assertEquals(listOf(LibraryContent.Error, LibraryContent.Loading, LibraryContent.Empty), seen)
    }
}
