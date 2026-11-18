// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.miniplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.music.player.PlayerController
import app.aria.music.player.PlayerStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface MiniPlayerUiState {
    data object Hidden : MiniPlayerUiState
    data class Showing(
        val title: String,
        val artists: List<String>,
        val thumbnailUrl: String?,
        val isPlaying: Boolean,
        val status: PlayerStatus,
        val durationMs: Long,
    ) : MiniPlayerUiState
}

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(private val controller: PlayerController) : ViewModel() {
    val state: StateFlow<MiniPlayerUiState> = controller.ui.map { ui ->
        val item = ui.current
        if (item == null) MiniPlayerUiState.Hidden
        else MiniPlayerUiState.Showing(item.track.title, item.track.artists, item.track.thumbnailUrl, ui.isPlaying, ui.status, ui.durationMs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MiniPlayerUiState.Hidden)

    /** Position is a separate flow so only the progress line recomposes/redraws on every tick. */
    val positionMs: StateFlow<Long> = controller.positionMs

    fun toggle() = controller.togglePlayPause()
    fun next() = controller.next()
}
