// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

import app.aria.domain.*
import app.aria.domain.testing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistPickerViewModelTest {
    // runTest must share this dispatcher, or backgroundScope collectors would run on a second scheduler.
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun addsToAnExistingPlaylistWithoutPuttingTheSongInTheLibrary() = runTest(dispatcher) {
        val repo = FakePlaylistRepository(); val id = repo.create("Mix"); val vm = PlaylistPickerViewModel(repo); backgroundScope.launch { vm.playlists.collect { } }
        assertEquals(listOf("Mix"), vm.playlists.value.map { it.title })
        vm.addTo(id, Samples.track(1)); assertEquals(1, repo.tracks(id).first().size)
    }

    @Test fun createAndAddRejectsBlankNames() = runTest(dispatcher) {
        val repo = FakePlaylistRepository(); val vm = PlaylistPickerViewModel(repo)
        assertEquals(PlaylistPickerViewModel.Outcome.BlankName, vm.createAndAdd("  ", Samples.track(1)))
        assertTrue(repo.playlists("", LibrarySort.TITLE).first().isEmpty())
        val id = (vm.createAndAdd(" New ", Samples.track(1)) as PlaylistPickerViewModel.Outcome.Added).playlistId
        assertEquals("New", repo.playlist(id).first()?.title); assertEquals(1, repo.tracks(id).first().size)
    }

    @Test fun aFailedAddKeepsTheCreatedPlaylistSoARetryDoesNotDuplicateIt() = runTest(dispatcher) {
        val base = FakePlaylistRepository(); var failAdd = true
        val repo = object : PlaylistRepository by base { override suspend fun addTracks(id: String, tracks: List<Track>) { if (failAdd) error("db"); base.addTracks(id, tracks) } }
        val vm = PlaylistPickerViewModel(repo)
        assertFailsWith<IllegalStateException> { vm.createAndAdd("Mix", Samples.track(1)) }
        assertEquals(1, base.playlists("", LibrarySort.TITLE).first().size)
        failAdd = false
        val id = (vm.createAndAdd(" Mix ", Samples.track(1)) as PlaylistPickerViewModel.Outcome.Added).playlistId
        assertEquals(listOf("Mix"), base.playlists("", LibrarySort.TITLE).first().map { it.title })
        assertEquals(1, base.tracks(id).first().size)
        // The created playlist is remembered only until its first song is in: the next "Mix" is a new playlist.
        vm.createAndAdd("Mix", Samples.track(2)); assertEquals(2, base.playlists("", LibrarySort.TITLE).first().size)
    }

    @Test fun aDismissedDialogDoesNotLeaveAPlaylistForTheNextOneToReuse() = runTest(dispatcher) {
        val base = FakePlaylistRepository(); var fail = true
        val repo = object : PlaylistRepository by base { override suspend fun addTracks(id: String, tracks: List<Track>) { if (fail) error("db"); base.addTracks(id, tracks) } }
        val vm = PlaylistPickerViewModel(repo)
        assertFailsWith<IllegalStateException> { vm.createAndAdd("Mix", Samples.track(1)) }
        vm.dismissed(); fail = false
        vm.createAndAdd("Mix", Samples.track(1))
        assertEquals(2, base.playlists("", LibrarySort.TITLE).first().size)   // the abandoned empty one plus a fresh one
    }

    @Test fun theCreatedPlaylistIsNotReusedForADifferentSong() = runTest(dispatcher) {
        val base = FakePlaylistRepository(); var fail = true
        val repo = object : PlaylistRepository by base { override suspend fun addTracks(id: String, tracks: List<Track>) { if (fail) error("db"); base.addTracks(id, tracks) } }
        val vm = PlaylistPickerViewModel(repo)
        assertFailsWith<IllegalStateException> { vm.createAndAdd("Mix", Samples.track(1)) }
        fail = false
        val id = (vm.createAndAdd("Mix", Samples.track(2)) as PlaylistPickerViewModel.Outcome.Added).playlistId
        assertEquals(2, base.playlists("", LibrarySort.TITLE).first().size)
        assertEquals(listOf(Samples.track(2).id), base.tracks(id).first().map { it.track.id })
    }

    @Test fun aRememberedPlaylistThatWasDeletedMeanwhileGetsOneFreshRetry() = runTest(dispatcher) {
        val base = FakePlaylistRepository(); var phase = 0   // 0: fail the first add; 1: the remembered playlist is gone
        val repo = object : PlaylistRepository by base {
            override suspend fun addTracks(id: String, tracks: List<Track>) {
                if (phase == 0) error("db")
                if (phase == 1 && base.playlist(id).first() == null) error("gone")
                base.addTracks(id, tracks)
            }
        }
        val vm = PlaylistPickerViewModel(repo)
        assertFailsWith<IllegalStateException> { vm.createAndAdd("Mix", Samples.track(1)) }
        val stale = base.playlists("", LibrarySort.TITLE).first().single().id
        base.delete(stale); phase = 1
        val id = (vm.createAndAdd("Mix", Samples.track(1)) as PlaylistPickerViewModel.Outcome.Added).playlistId
        assertNotEquals(stale, id)
        assertEquals(1, base.tracks(id).first().size)
    }

    @Test fun aSecondTransientFailureKeepsTheRememberedPlaylistSoNothingIsOrphanedOrDuplicated() = runTest(dispatcher) {
        val base = FakePlaylistRepository(); var fail = true
        val repo = object : PlaylistRepository by base { override suspend fun addTracks(id: String, tracks: List<Track>) { if (fail) error("db"); base.addTracks(id, tracks) } }
        val vm = PlaylistPickerViewModel(repo)
        assertFailsWith<IllegalStateException> { vm.createAndAdd("Mix", Samples.track(1)) }
        assertFailsWith<IllegalStateException> { vm.createAndAdd("Mix", Samples.track(1)) }
        assertEquals(1, base.playlists("", LibrarySort.TITLE).first().size)
        fail = false
        val id = (vm.createAndAdd("Mix", Samples.track(1)) as PlaylistPickerViewModel.Outcome.Added).playlistId
        assertEquals(listOf("Mix"), base.playlists("", LibrarySort.TITLE).first().map { it.title })
        assertEquals(1, base.tracks(id).first().size)
    }

    @Test fun aCreateStillRunningWhenTheDialogIsDismissedLeavesNothingToReuse() = runTest(dispatcher) {
        val base = FakePlaylistRepository(); val createGate = CompletableDeferred<Unit>(); val addGate = CompletableDeferred<Unit>(); var gated = true
        val repo = object : PlaylistRepository by base {
            override suspend fun create(name: String): String { if (gated) createGate.await(); return base.create(name) }
            override suspend fun addTracks(id: String, tracks: List<Track>) { if (gated) addGate.await(); base.addTracks(id, tracks) }
        }
        val vm = PlaylistPickerViewModel(repo)
        val first = async { vm.createAndAdd("Mix", Samples.track(1)) }
        vm.dismissed()                       // the dialog is closed while the create is in flight
        createGate.complete(Unit)            // the create finishes, then the add hangs and is cancelled with the dialog
        first.cancel(); first.join(); gated = false
        vm.createAndAdd("Mix", Samples.track(1))
        assertEquals(2, base.playlists("", LibrarySort.TITLE).first().size)   // a fresh playlist, not the stale one
    }

    @Test fun aTapWhileAnAddIsInFlightIsIgnored() = runTest(dispatcher) {
        val base = FakePlaylistRepository(); val id = base.create("Mix"); val gate = CompletableDeferred<Unit>(); var adds = 0
        val repo = object : PlaylistRepository by base { override suspend fun addTracks(id: String, tracks: List<Track>) { adds++; gate.await(); base.addTracks(id, tracks) } }
        val vm = PlaylistPickerViewModel(repo)
        val first = async { vm.addTo(id, Samples.track(1)) }
        assertEquals(PlaylistPickerViewModel.Outcome.Busy, vm.addTo(id, Samples.track(1)))
        assertEquals(PlaylistPickerViewModel.Outcome.Busy, vm.createAndAdd("Other", Samples.track(1)))
        gate.complete(Unit)
        assertEquals(PlaylistPickerViewModel.Outcome.Added(id), first.await())
        assertEquals(1, adds); assertEquals(1, base.tracks(id).first().size); assertEquals(1, base.playlists("", LibrarySort.TITLE).first().size)
        // The lock is released afterwards.
        assertEquals(PlaylistPickerViewModel.Outcome.Added(id), vm.addTo(id, Samples.track(2)))
    }

    @Test fun theLockIsReleasedAfterAFailure() = runTest(dispatcher) {
        val base = FakePlaylistRepository(); val id = base.create("Mix"); var fail = true
        val repo = object : PlaylistRepository by base { override suspend fun addTracks(id: String, tracks: List<Track>) { if (fail) error("db"); base.addTracks(id, tracks) } }
        val vm = PlaylistPickerViewModel(repo)
        assertFailsWith<IllegalStateException> { vm.addTo(id, Samples.track(1)) }
        fail = false; assertEquals(PlaylistPickerViewModel.Outcome.Added(id), vm.addTo(id, Samples.track(1)))
    }
}

class PlaylistNamesTest {
    @Test fun trimsAndRejectsBlank() { assertEquals("A", PlaylistNames.clean("  A ")); assertNull(PlaylistNames.clean("   ")) }

    @Test fun truncatesByCodePointsNeverSplittingASurrogatePair() {
        val grin = "\uD83D\uDE00"   // one code point, two UTF-16 units
        val long = grin.repeat(150)
        val cleaned = PlaylistNames.clean(long)!!
        assertEquals(PlaylistNames.MAX, cleaned.codePointCount(0, cleaned.length))
        assertEquals(grin.repeat(PlaylistNames.MAX), cleaned)
        // A pair straddling the UTF-16 limit survives whole.
        val straddle = "a".repeat(99) + grin + "tail"
        assertEquals("a".repeat(99) + grin, PlaylistNames.clean(straddle))
        assertEquals("x".repeat(100), PlaylistNames.clean("x".repeat(300)))
        assertEquals(PlaylistNames.MAX, PlaylistNames.limit(long).codePointCount(0, PlaylistNames.limit(long).length))
    }
}
