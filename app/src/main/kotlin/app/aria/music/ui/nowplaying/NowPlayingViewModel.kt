// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aria.domain.LibraryRepository
import app.aria.domain.SettingsRepository
import app.aria.music.player.PlayerController
import app.aria.music.player.PlayerMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val controller: PlayerController,
    private val library: LibraryRepository,
    settings: SettingsRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val liked: Flow<Boolean> = controller.ui.map { it.current?.track?.id }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(false) else library.isLiked(id) }

    val state: StateFlow<NowPlayingUiState> = combine(
        controller.ui, liked, settings.settings.map { it.sleepTimerDefaultMinutes }.distinctUntilChanged(),
    ) { ui, liked, sleepDefault ->
        val item = ui.current
        if (item == null) NowPlayingUiState.Empty
        else NowPlayingUiState.Content(item.track, ui.status, if (ui.durationMs > 0) ui.durationMs else item.track.durationMs ?: 0L, ui.shuffle, ui.repeat, ui.sleepTimer, liked, ui.queue.size, ui.currentIndex, sleepDefault)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingUiState.Empty)

    /** Read only by the seek bar and its time labels, so a tick never recomposes anything else. */
    val positionMs: StateFlow<Long> = controller.positionMs

    fun actions(onBack: () -> Unit, onOpenQueue: () -> Unit) = NowPlayingActions(
        onBack = onBack,
        onToggle = controller::togglePlayPause,
        onNext = controller::next,
        onPrevious = controller::previous,
        onSeek = controller::seekTo,
        onShuffle = controller::setShuffle,
        onRepeat = controller::cycleRepeat,
        onLike = {
            val c = state.value as? NowPlayingUiState.Content ?: return@NowPlayingActions
            // Liking also puts the song in the library (ADR-0003); unliking keeps it there.
            viewModelScope.launch {
                try {
                    library.setLiked(c.track, !c.liked)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    controller.notify(PlayerMessage.LikeFailed)
                }
            }
        },
        onSleepMinutes = controller::setSleepTimer,
        onSleepEndOfTrack = { controller.setSleepAtEndOfTrack(true) },
        onSleepOff = { controller.setSleepTimer(null) },
        onOpenQueue = onOpenQueue,
    )
}
