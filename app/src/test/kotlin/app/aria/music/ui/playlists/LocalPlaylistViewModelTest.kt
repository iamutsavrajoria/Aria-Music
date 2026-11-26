// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

import androidx.lifecycle.SavedStateHandle
import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
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
class LocalPlaylistViewModelTest {
    // runTest must share this dispatcher, or backgroundScope collectors would run on a second scheduler.
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMain() = Dispatchers.resetMain()

    private class Env {
        val repo = FakePlaylistRepository(); val engine = FakePlaybackEngine(); var id = ""
        lateinit var vm: LocalPlaylistViewModel
        suspend fun init(scope: kotlinx.coroutines.CoroutineScope): Env {
            id = repo.create("Mine"); repo.addTracks(id, Samples.tracks(3))
            vm = LocalPlaylistViewModel(SavedStateHandle(mapOf("id" to id)), repo, PlayerController(engine, ServiceLauncher { }, scope)); scope.launch { vm.state.collect { } }; return this
        }
        fun titles() = ((vm.state.value as LocalPlaylistUiState.Content).rows.map { it.track.title })
        fun entry(i: Int) = (vm.state.value as LocalPlaylistUiState.Content).rows[i].entryId
    }

    @Test fun contentPlayShuffleQueue() = runTest(dispatcher) {
        val e = Env().init(backgroundScope)
        assertEquals(listOf("Song 1", "Song 2", "Song 3"), e.titles())
        e.vm.play(1); e.vm.shuffle(); e.vm.addToQueue()
        assertEquals(Samples.tracks(3) to 1, e.engine.playNowCalls[0]); assertEquals(3, e.engine.playNowCalls[1].first.size); assertEquals(1, e.engine.addedToEnd.size)
    }

    @Test fun moveUpAndDownRewriteTheWholeOrder() = runTest(dispatcher) {
        val e = Env().init(backgroundScope)
        e.vm.moveUp(e.entry(2)); assertEquals(listOf("Song 1", "Song 3", "Song 2"), e.titles())
        e.vm.moveDown(e.entry(0)); assertEquals(listOf("Song 3", "Song 1", "Song 2"), e.titles())
    }

    @Test fun edgeMovesAndUnknownIdsAreIgnored() = runTest(dispatcher) {
        val e = Env().init(backgroundScope)
        e.vm.moveUp(e.entry(0)); e.vm.moveDown(e.entry(2)); e.vm.moveUp(9_999)
        assertEquals(listOf("Song 1", "Song 2", "Song 3"), e.titles())
    }

    @Test fun removeRenameAndDelete() = runTest(dispatcher) {
        val e = Env().init(backgroundScope)
        e.vm.remove(e.entry(1)); assertEquals(listOf("Song 1", "Song 3"), e.titles())
        e.vm.rename("  New name  "); assertEquals("New name", (e.vm.state.value as LocalPlaylistUiState.Content).playlist.title)
        e.vm.rename("   "); assertEquals("New name", (e.vm.state.value as LocalPlaylistUiState.Content).playlist.title)
        e.vm.delete(); assertTrue(e.vm.deleted.value); assertEquals(LocalPlaylistUiState.NotFound, e.vm.state.value)
    }

    @Test fun namesAreCleaned() {
        assertEquals("A", PlaylistNames.clean("  A ")); assertNull(PlaylistNames.clean("   ")); assertEquals(100, PlaylistNames.clean("x".repeat(300))!!.length)
    }

    @Test fun duplicateEntriesAreSeparateRowsAndRemoveDeletesOne() = runTest(dispatcher) {
        val e = Env().init(backgroundScope)
        e.repo.addTracks(e.id, listOf(Samples.track(1)))
        assertEquals(listOf("Song 1", "Song 2", "Song 3", "Song 1"), e.titles())
        assertEquals(4, (e.vm.state.value as LocalPlaylistUiState.Content).rows.map { it.entryId }.toSet().size)
        e.vm.remove(e.entry(3)); assertEquals(listOf("Song 1", "Song 2", "Song 3"), e.titles())
    }

    @Test fun aFailingEditIsReportedAsAPlaylistFailureNotALibraryOne() = runTest(dispatcher) {
        val base = FakePlaylistRepository()
        val id = base.create("Mine"); base.addTracks(id, Samples.tracks(2))
        val broken = object : PlaylistRepository by base {
            override suspend fun rename(id: String, name: String) = error("db")
            override suspend fun delete(id: String) = error("db")
            override suspend fun removeEntry(playlistId: String, entryId: Int) = error("db")
            override suspend fun reorder(playlistId: String, orderedEntryIds: List<Int>) = error("db")
        }
        val messages = mutableListOf<PlayerMessage>()
        val controller = PlayerController(FakePlaybackEngine(), ServiceLauncher { }, backgroundScope)
        backgroundScope.launch { controller.messages.collect { messages += it } }
        val vm = LocalPlaylistViewModel(SavedStateHandle(mapOf("id" to id)), broken, controller); backgroundScope.launch { vm.state.collect { } }
        val entry = (vm.state.value as LocalPlaylistUiState.Content).rows.first().entryId
        vm.rename("New"); vm.delete(); vm.remove(entry); vm.moveDown(entry)
        assertEquals(List<PlayerMessage>(4) { PlayerMessage.PlaylistFailed }, messages); assertFalse(vm.deleted.value)
    }

    @Test fun anEmptyPlaylistDoesNotStartPlayback() = runTest(dispatcher) {
        val e = Env().init(backgroundScope)
        e.repo.tracks(e.id).first().forEach { e.vm.remove(it.entryId) }
        e.vm.play(); e.vm.shuffle(); e.vm.addToQueue()
        assertTrue(e.engine.playNowCalls.isEmpty()); assertTrue(e.engine.addedToEnd.isEmpty())
    }
}
