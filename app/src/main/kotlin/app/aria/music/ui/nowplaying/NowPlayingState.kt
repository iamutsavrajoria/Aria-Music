// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

import app.aria.domain.RepeatMode
import app.aria.domain.SleepTimer
import app.aria.domain.Track
import app.aria.music.player.PlayerStatus

/** Shared by every Now Playing skin. The playback position is deliberately not part of it (see NowPlayingViewModel.positionMs). */
sealed interface NowPlayingUiState {
    data object Empty : NowPlayingUiState
    data class Content(
        val track: Track,
        val status: PlayerStatus,
        val durationMs: Long,
        val shuffle: Boolean,
        val repeat: RepeatMode,
        val sleepTimer: SleepTimer?,
        val liked: Boolean,
        val queueSize: Int,
        val queueIndex: Int,
        val sleepDefaultMinutes: Int,
    ) : NowPlayingUiState {
        /** Playback is wanted (playing, buffering or stalled on the network): the main button pauses. */
        val isPlaying: Boolean get() = status == PlayerStatus.PLAYING || status == PlayerStatus.BUFFERING || status == PlayerStatus.WAITING_FOR_NETWORK
    }
}

/** Skin-independent user intents. */
data class NowPlayingActions(
    val onBack: () -> Unit,
    val onToggle: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onShuffle: (Boolean) -> Unit,
    val onRepeat: () -> Unit,
    val onLike: () -> Unit,
    val onSleepMinutes: (Int) -> Unit,
    val onSleepEndOfTrack: () -> Unit,
    val onSleepOff: () -> Unit,
    val onOpenQueue: () -> Unit,
)
