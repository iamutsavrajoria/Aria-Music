// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.player

import app.aria.domain.*

enum class PlayerStatus { IDLE, BUFFERING, PLAYING, PAUSED, WAITING_FOR_NETWORK, ERROR }

/** Slow state: everything except the playback position (see PlayerController.positionMs). */
data class PlayerUiState(
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val status: PlayerStatus = PlayerStatus.IDLE,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val sleepTimer: SleepTimer? = null,
) {
    val current: QueueItem? get() = queue.getOrNull(currentIndex)
    val hasQueue: Boolean get() = queue.isNotEmpty()
    /** Playback is wanted: playing, buffering or stalled on the network. The play/pause button then pauses (ERROR and PAUSED play). */
    val isPlaying: Boolean get() = status == PlayerStatus.PLAYING || status == PlayerStatus.BUFFERING || status == PlayerStatus.WAITING_FOR_NETWORK
}

enum class SkipReason { UNAVAILABLE, NETWORK, OTHER }

sealed interface PlayerMessage {
    data class TrackSkipped(val title: String, val reason: SkipReason) : PlayerMessage
    data object TooManyFailures : PlayerMessage
    data class QueueWindowed(val queued: Int, val total: Int) : PlayerMessage
    data class QueueFull(val dropped: Int) : PlayerMessage
    /** A like/unlike could not be saved (database error). */
    data object LikeFailed : PlayerMessage
    /** A playlist could not be created, renamed, deleted or changed (database error). */
    data object PlaylistFailed : PlayerMessage
    /** Search history could not be saved, removed or cleared (database error). */
    data object HistoryFailed : PlayerMessage
    /** A manual Home refresh failed or was impossible (offline); the saved content stays on screen. */
    data object RefreshFailed : PlayerMessage
}

internal fun PlaybackState.toUi() = PlayerUiState(
    queue = queue, currentIndex = currentIndex,
    status = when (status) {
        PlaybackStatus.Idle -> PlayerStatus.IDLE
        PlaybackStatus.Buffering -> PlayerStatus.BUFFERING
        PlaybackStatus.Playing -> PlayerStatus.PLAYING
        PlaybackStatus.Paused -> PlayerStatus.PAUSED
        PlaybackStatus.WaitingForNetwork -> PlayerStatus.WAITING_FOR_NETWORK
        is PlaybackStatus.Error -> PlayerStatus.ERROR
    },
    durationMs = durationMs, shuffle = shuffle, repeat = repeat, sleepTimer = sleepTimer,
)

internal fun PlaybackEvent.toMessage(): PlayerMessage = when (this) {
    is PlaybackEvent.Skipped -> PlayerMessage.TrackSkipped(
        track.title,
        when (reason) { "unavailable" -> SkipReason.UNAVAILABLE; "network error" -> SkipReason.NETWORK; else -> SkipReason.OTHER },
    )
    is PlaybackEvent.Stopped -> PlayerMessage.TooManyFailures
}
