// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.player

import app.aria.domain.*
import app.aria.music.di.ApplicationScope
import app.aria.playback.DefaultPlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** The only playback entry point for screens. Wraps the engine; starts the playback service before a command that needs it. */
@Singleton
class PlayerController @Inject constructor(
    private val engine: PlaybackEngine,
    private val launcher: ServiceLauncher,
    @ApplicationScope scope: CoroutineScope,
) {
    val ui: StateFlow<PlayerUiState> =
        engine.state.map { it.toUi() }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, PlayerUiState())

    /** Collected only by the seek bar and the mini-player progress line. */
    val positionMs: StateFlow<Long> =
        engine.state.map { it.positionMs }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, 0L)

    private val channel = Channel<PlayerMessage>(Channel.BUFFERED)
    val messages: Flow<PlayerMessage> = channel.receiveAsFlow()

    init { scope.launch { engine.events.collect { channel.trySend(it.toMessage()) } } }

    fun play(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val tapped = startIndex.coerceIn(0, tracks.lastIndex)
        launcher.start()
        if (tracks.size > QUEUE_CAP) {
            val window = tracks.subList(tapped, minOf(tracks.size, tapped + QUEUE_CAP))
            engine.playNow(window, 0)
            channel.trySend(PlayerMessage.QueueWindowed(window.size, tracks.size))
        } else {
            engine.playNow(tracks, tapped)
        }
    }

    fun playNext(track: Track) { launcher.start(); engine.playNext(track) }

    fun addToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (!ui.value.hasQueue) { play(tracks, 0); return }
        val room = (QUEUE_CAP - ui.value.queue.size).coerceAtLeast(0)
        if (tracks.size > room) channel.trySend(PlayerMessage.QueueFull(tracks.size - room))
        launcher.start(); engine.addToEnd(tracks)
    }

    fun skipTo(index: Int) = engine.skipTo(index)
    fun removeAt(index: Int) = engine.removeAt(index)
    fun move(from: Int, to: Int) = engine.move(from, to)
    fun clearQueue() = engine.clear()
    fun togglePlayPause() { if (ui.value.isPlaying) engine.pause() else engine.play() }
    fun next() = engine.next()
    fun previous() = engine.previous()
    fun seekTo(positionMs: Long) = engine.seekTo(positionMs)
    fun setShuffle(on: Boolean) = engine.setShuffle(on)
    fun cycleRepeat() = engine.setRepeat(when (ui.value.repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF })
    fun setSleepTimer(minutes: Int?) = engine.setSleepTimer(minutes?.let { it * 60_000L })
    fun setSleepAtEndOfTrack(on: Boolean) = engine.setSleepAtEndOfTrack(on)

    private companion object { const val QUEUE_CAP = DefaultPlaybackEngine.QUEUE_CAP }
}
