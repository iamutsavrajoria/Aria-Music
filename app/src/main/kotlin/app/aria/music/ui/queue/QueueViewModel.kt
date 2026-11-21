// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.Track
import app.aria.music.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One queue entry. [queueId] is unique per entry (the same song queued twice is two rows). */
data class QueueRow(val queueId: Long, val index: Int, val track: Track, val isCurrent: Boolean)

sealed interface QueueUiState {
    data object Empty : QueueUiState
    data class Content(val rows: List<QueueRow>) : QueueUiState
}

data class QueueActions(
    val onBack: () -> Unit,
    val onPlay: (Int) -> Unit,
    val onRemove: (Int) -> Unit,
    val onMoveUp: (Int) -> Unit,
    val onMoveDown: (Int) -> Unit,
    val onClear: () -> Unit,
)

@HiltViewModel
class QueueViewModel @Inject constructor(private val controller: PlayerController) : ViewModel() {
    // Position ticks never reach this flow's output: rows only change with the queue or the current index.
    val state: StateFlow<QueueUiState> = controller.ui.map { ui ->
        if (ui.queue.isEmpty()) QueueUiState.Empty
        else QueueUiState.Content(ui.queue.mapIndexed { i, q -> QueueRow(q.queueId, i, q.track, i == ui.currentIndex) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueueUiState.Empty)

    private val size get() = controller.ui.value.queue.size

    fun play(i: Int) { if (i in 0 until size) controller.skipTo(i) }
    fun remove(i: Int) { if (i in 0 until size) controller.removeAt(i) }
    fun moveUp(i: Int) { if (i in 1 until size) controller.move(i, i - 1) }
    fun moveDown(i: Int) { if (i in 0 until size - 1) controller.move(i, i + 1) }
    fun clear() = controller.clearQueue()
}
