// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain.testing

import app.aria.domain.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class FakePlaybackEngine : PlaybackEngine {
    val mutableState = MutableStateFlow(PlaybackState())
    val mutableEvents = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    override val state: StateFlow<PlaybackState> = mutableState
    override val events: SharedFlow<PlaybackEvent> = mutableEvents
    val calls = mutableListOf<String>()
    val playNowCalls = mutableListOf<Pair<List<Track>, Int>>()
    val addedToEnd = mutableListOf<List<Track>>()
    var historyThresholdMs: Long? = null
    private var nextId = 1L

    private fun items(tracks: List<Track>) = tracks.map { QueueItem(nextId++, it) }
    override fun playNow(tracks: List<Track>, startIndex: Int) {
        calls += "playNow"; playNowCalls += tracks to startIndex
        mutableState.value = mutableState.value.copy(queue = items(tracks), currentIndex = startIndex, status = PlaybackStatus.Buffering)
    }
    override fun playNext(track: Track) { calls += "playNext" }
    override fun addToEnd(tracks: List<Track>) { calls += "addToEnd"; addedToEnd.add(tracks) }
    override fun removeAt(index: Int) { calls += "removeAt:$index" }
    override fun move(from: Int, to: Int) { calls += "move:$from:$to" }
    override fun clear() { calls += "clear" }
    override fun skipTo(index: Int) { calls += "skipTo:$index" }
    override fun play() { calls += "play" }
    override fun pause() { calls += "pause" }
    override fun next() { calls += "next" }
    override fun previous() { calls += "previous" }
    override fun seekTo(positionMs: Long) { calls += "seekTo:$positionMs" }
    override fun setShuffle(on: Boolean) { calls += "setShuffle:$on" }
    override fun setRepeat(mode: RepeatMode) { calls += "setRepeat:$mode" }
    override fun setSleepTimer(durationMs: Long?) { calls += "setSleepTimer:$durationMs" }
    override fun setSleepAtEndOfTrack(on: Boolean) { calls += "setSleepAtEndOfTrack:$on" }
    override fun setSkipSilence(on: Boolean) { calls += "setSkipSilence:$on" }
    override fun setNormalizeLoudness(on: Boolean) { calls += "setNormalizeLoudness:$on" }
    override fun setEndlessRadio(on: Boolean) { calls += "setEndlessRadio:$on" }
    override fun setHistoryThresholdMs(ms: Long) { historyThresholdMs = ms; calls += "setHistoryThresholdMs:$ms" }
}
