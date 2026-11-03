// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class RepeatMode { OFF, ALL, ONE }

/** One queue entry. [queueId] is unique per entry so the same track can appear twice. */
data class QueueItem(val queueId: Long, val track: Track)

sealed interface PlaybackStatus {
    data object Idle : PlaybackStatus
    data object Buffering : PlaybackStatus
    data object Playing : PlaybackStatus
    data object Paused : PlaybackStatus
    data object WaitingForNetwork : PlaybackStatus
    data class Error(val message: String) : PlaybackStatus
}

/** [endOfTrack] true means "pause when the current track ends"; otherwise [endsAtEpochMs] is set. */
data class SleepTimer(val endsAtEpochMs: Long?, val endOfTrack: Boolean)

data class PlaybackState(
    /** In play order (already shuffled when [shuffle] is on). */
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val sleepTimer: SleepTimer? = null,
    val skipSilence: Boolean = false,
    val normalizeLoudness: Boolean = true,
    val endlessRadio: Boolean = true,
) {
    val current: QueueItem? get() = queue.getOrNull(currentIndex)
}

sealed interface PlaybackEvent {
    data class Skipped(val track: Track, val reason: String) : PlaybackEvent
    data class Stopped(val reason: String) : PlaybackEvent
}

/** What the UI and the media session talk to. */
interface PlaybackEngine {
    val state: StateFlow<PlaybackState>
    val events: SharedFlow<PlaybackEvent>
    fun playNow(tracks: List<Track>, startIndex: Int = 0)
    fun playNext(track: Track)
    fun addToEnd(tracks: List<Track>)
    fun removeAt(index: Int)
    fun move(from: Int, to: Int)
    fun clear()
    fun skipTo(index: Int)
    fun play()
    fun pause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setShuffle(on: Boolean)
    fun setRepeat(mode: RepeatMode)
    fun setSleepTimer(durationMs: Long?)
    fun setSleepAtEndOfTrack(on: Boolean)
    fun setSkipSilence(on: Boolean)
    fun setNormalizeLoudness(on: Boolean)
    fun setEndlessRadio(on: Boolean)
}

sealed interface PlayerError {
    /** The stream can never play (extractor said unavailable). */
    data object Permanent : PlayerError
    data class Http(val code: Int) : PlayerError
    data object Io : PlayerError
}

/** Events from the player back to the engine. */
interface PlayerListener {
    /** The player moved to [index] by itself (automatic advance or repeat-one loop). */
    fun onItemTransition(index: Int)
    fun onPlayWhenReadyChanged(playWhenReady: Boolean)
    fun onBufferingChanged(buffering: Boolean)
    /** The player played to the end of its playlist. */
    fun onEnded()
    fun onError(error: PlayerError)
}

/** Commands from the engine to the player. The player mirrors the engine's queue; the engine is the source of truth for order. */
interface PlayerOutput {
    var listener: PlayerListener?
    fun setPlaylist(items: List<QueueItem>, startIndex: Int, startPositionMs: Long, playWhenReady: Boolean)
    fun insert(index: Int, items: List<QueueItem>)
    fun remove(index: Int)
    fun move(from: Int, to: Int)
    /** Replace the playlist around the playing item without interrupting it. The current entry is the same before and after. */
    fun reorder(items: List<QueueItem>, currentIndex: Int)
    fun clear()
    fun seekTo(index: Int, positionMs: Long)
    fun setPlayWhenReady(play: Boolean)
    /** Re-prepare the current item (fresh URL resolve) and continue from [positionMs]. */
    fun retry(positionMs: Long)
    fun setRepeatOne(on: Boolean)
    fun setSkipSilence(on: Boolean)
    fun setVolume(volume: Float)
    fun positionMs(): Long
    fun durationMs(): Long
}
