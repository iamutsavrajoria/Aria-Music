// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.queue

import app.aria.domain.PlaybackState
import app.aria.domain.QueueItem
import app.aria.domain.testing.FakePlaybackEngine
import app.aria.domain.testing.Samples
import app.aria.music.player.PlayerController
import app.aria.music.player.ServiceLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.env(): Pair<FakePlaybackEngine, QueueViewModel> {
        val engine = FakePlaybackEngine(); val vm = QueueViewModel(PlayerController(engine, ServiceLauncher { }, backgroundScope))
        backgroundScope.launch { vm.state.collect { } }; return engine to vm
    }
    private fun FakePlaybackEngine.queue(n: Int, current: Int = 0) {
        mutableState.value = PlaybackState(queue = Samples.tracks(n).mapIndexed { i, t -> QueueItem(100L + i, t) }, currentIndex = current)
    }

    @Test fun emptyThenRowsWithCurrentMarked() = runTest {
        val (engine, vm) = env(); assertEquals(QueueUiState.Empty, vm.state.value)
        engine.queue(3, current = 1); runCurrent()
        val rows = (vm.state.value as QueueUiState.Content).rows
        assertEquals(listOf(100L, 101L, 102L), rows.map { it.queueId }); assertEquals(listOf(false, true, false), rows.map { it.isCurrent })
        assertEquals(listOf(0, 1, 2), rows.map { it.index })
    }

    @Test fun duplicatesStayDistinctRows() = runTest {
        val (engine, vm) = env(); val t = Samples.track(1)
        engine.mutableState.value = PlaybackState(queue = listOf(QueueItem(1, t), QueueItem(2, t)), currentIndex = 0); runCurrent()
        assertEquals(2, (vm.state.value as QueueUiState.Content).rows.map { it.queueId }.toSet().size)
    }

    @Test fun commandsMapToTheController() = runTest {
        val (engine, vm) = env(); engine.queue(4); runCurrent()
        vm.play(2); vm.remove(3); vm.moveUp(2); vm.moveDown(1); vm.clear()
        assertEquals(listOf("skipTo:2", "removeAt:3", "move:2:1", "move:1:2", "clear"), engine.calls)
    }

    @Test fun outOfRangeMovesAreIgnored() = runTest {
        val (engine, vm) = env(); engine.queue(3); runCurrent()
        vm.moveUp(0); vm.moveDown(2); vm.moveUp(-1); vm.moveDown(9)
        assertTrue(engine.calls.isEmpty())
    }

    @Test fun outOfRangePlayAndRemoveAreIgnored() = runTest {
        val (engine, vm) = env(); engine.queue(3); runCurrent()
        vm.play(-1); vm.play(3); vm.remove(-1); vm.remove(3)
        assertTrue(engine.calls.isEmpty())
    }

    @Test fun stateDoesNotReemitWhenOnlyThePositionChanges() = runTest {
        val (engine, vm) = env(); engine.queue(2); runCurrent()
        val seen = mutableListOf<QueueUiState>(); backgroundScope.launch { vm.state.collect { seen += it } }; runCurrent()
        val before = seen.size
        engine.mutableState.value = engine.mutableState.value.copy(positionMs = 5_000); runCurrent()
        assertEquals(before, seen.size)
    }
}
