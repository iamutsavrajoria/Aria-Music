// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

import app.aria.domain.*
import app.aria.domain.testing.*
import kotlinx.coroutines.Dispatchers
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
        assertNull(vm.createAndAdd("  ", Samples.track(1))); val id = vm.createAndAdd(" New ", Samples.track(1))!!
        assertEquals("New", repo.playlist(id).first()?.title); assertEquals(1, repo.tracks(id).first().size)
    }
}
