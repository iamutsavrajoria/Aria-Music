// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.miniplayer

import app.aria.domain.*
import app.aria.domain.testing.FakePlaybackEngine
import app.aria.domain.testing.Samples
import app.aria.music.player.PlayerController
import app.aria.music.player.ServiceLauncher
import app.aria.music.player.PlayerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MiniPlayerViewModelTest {
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.vm(engine: FakePlaybackEngine): MiniPlayerViewModel {
        val vm = MiniPlayerViewModel(PlayerController(engine, ServiceLauncher { }, backgroundScope))
        backgroundScope.launch { vm.state.collect { } }; return vm
    }

    @Test fun hiddenWithoutAQueue() = runTest { assertEquals(MiniPlayerUiState.Hidden, vm(FakePlaybackEngine()).state.value) }

    @Test fun showsTheCurrentTrack() = runTest {
        val engine = FakePlaybackEngine(); val vm = vm(engine)
        engine.mutableState.value = PlaybackState(queue = listOf(QueueItem(1, Samples.track(1))), currentIndex = 0, status = PlaybackStatus.Playing, durationMs = 180_000)
        runCurrent()
        val s = vm.state.value as MiniPlayerUiState.Showing
        assertEquals("Song 1", s.title); assertEquals(listOf("Artist 1"), s.artists); assertTrue(s.isPlaying); assertEquals(PlayerStatus.PLAYING, s.status); assertEquals(180_000L, s.durationMs)
    }

    @Test fun toggleAndNextDelegate() = runTest {
        val engine = FakePlaybackEngine(); val vm = vm(engine)
        engine.mutableState.value = PlaybackState(queue = listOf(QueueItem(1, Samples.track(1))), currentIndex = 0, status = PlaybackStatus.Paused); runCurrent()
        vm.toggle(); vm.next()
        assertEquals(listOf("play", "next"), engine.calls)
    }
}
