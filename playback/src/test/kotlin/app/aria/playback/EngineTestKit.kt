// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlin.random.Random

class FakeOutput : PlayerOutput {
    override var listener: PlayerListener? = null
    var playlist: List<QueueItem> = emptyList(); var index = -1
    // @JvmField: plain vars would generate setPlayWhenReady/setRepeatOne/setSkipSilence/setVolume and clash with the PlayerOutput overrides.
    @JvmField var playWhenReady = false
    var position = 0L; var duration = 200_000L
    @JvmField var volume = 1f
    @JvmField var repeatOne = false
    @JvmField var skipSilence = false
    val retries = mutableListOf<Long>(); val calls = mutableListOf<String>()
    override fun setPlaylist(items: List<QueueItem>, startIndex: Int, startPositionMs: Long, playWhenReady: Boolean) {
        playlist = items; index = startIndex; position = startPositionMs; this.playWhenReady = playWhenReady; calls += "setPlaylist"
    }
    override fun insert(index: Int, items: List<QueueItem>) {
        playlist = playlist.toMutableList().apply { addAll(index, items) }
        if (index <= this.index) this.index += items.size
        calls += "insert@$index"
    }
    override fun remove(index: Int) {
        playlist = playlist.toMutableList().apply { removeAt(index) }
        if (index < this.index) this.index--
        calls += "remove@$index"
    }
    override fun move(from: Int, to: Int) {
        playlist = playlist.toMutableList().apply { add(to, removeAt(from)) }
        index = when { from == index -> to; from < index && to >= index -> index - 1; from > index && to <= index -> index + 1; else -> index }
        calls += "move"
    }
    override fun reorder(items: List<QueueItem>, currentIndex: Int) { playlist = items; index = currentIndex; calls += "reorder" }
    override fun clear() { playlist = emptyList(); index = -1; calls += "clear" }
    override fun seekTo(index: Int, positionMs: Long) { this.index = index; position = positionMs; calls += "seekTo@$index" }
    override fun setPlayWhenReady(play: Boolean) { playWhenReady = play }
    override fun retry(positionMs: Long) { retries += positionMs }
    override fun setRepeatOne(on: Boolean) { repeatOne = on }
    override fun setSkipSilence(on: Boolean) { skipSilence = on }
    override fun setVolume(volume: Float) { this.volume = volume }
    override fun positionMs() = position
    override fun durationMs() = duration
}

class FakeStreams : StreamSource {
    val results = mutableMapOf<TrackId, Result<ResolvedStream>>()
    val resolved = mutableListOf<TrackId>(); val rejected = mutableListOf<TrackId>()
    var hang = false; var delayMs = 0L
    override suspend fun resolve(id: TrackId): Result<ResolvedStream> {
        resolved += id
        if (hang) kotlinx.coroutines.awaitCancellation()
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        return results[id] ?: Result.success(ResolvedStream("https://example.invalid/${id.raw}", "audio/webm", 160_000, null, null))
    }
    override fun markRejected(id: TrackId) { rejected += id }
}

class FakeStore : QueueStore {
    var saved: SavedQueue? = null; var position: SavedPosition? = null
    var structureSaves = 0; val positionSaves = mutableListOf<SavedPosition>()
    override suspend fun saveStructure(queue: SavedQueue) { saved = queue; structureSaves++ }
    override suspend fun savePosition(position: SavedPosition) { this.position = position; positionSaves += position }
    override suspend fun load(): RestoredQueue? = saved?.let { q -> position?.let { RestoredQueue(q, it) } }
}

class FakeRadio : RadioSource {
    var result: Result<List<Track>> = Result.success(emptyList()); val calls = mutableListOf<Track>()
    override suspend fun related(seed: Track, limit: Int): Result<List<Track>> { calls += seed; return result }
}

class FakeHistory : PlayEventRepository {
    val recorded = mutableListOf<Triple<Track, Long, Long>>()
    override suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long) { recorded += Triple(track, playedAtEpochMs, playTimeMs) }
    override suspend fun playCount(id: TrackId) = recorded.count { it.first.id == id }
}

class FakeNetwork(initial: Boolean = true) : NetworkMonitor {
    override val online = MutableStateFlow(initial)
}

/** The engine's ticker loops while playing, so tests use [advance], never advanceUntilIdle, and call [release]. */
@OptIn(ExperimentalCoroutinesApi::class)
class Harness(val scheduler: TestCoroutineScheduler) {
    val output = FakeOutput(); val streams = FakeStreams(); val store = FakeStore()
    val radio = FakeRadio(); val history = FakeHistory(); val network = FakeNetwork()
    var now = 1_000_000L
    private val dispatcher = StandardTestDispatcher(scheduler)
    val engine = DefaultPlaybackEngine(streams, store, radio, history, network, dispatcher, { now }, Random(1), dispatcher)
    fun attach(): Harness { engine.attach(output); return this }
    fun advance(ms: Long) { now += ms; scheduler.advanceTimeBy(ms); scheduler.runCurrent() }
    fun settle() = scheduler.runCurrent()
    fun release() = engine.release()
    val state get() = engine.state.value
}

/** Releases the engine when the test ends, even after a failed assertion: a leaked ticker would otherwise keep runTest draining virtual time forever. */
fun TestScope.harness(): Harness = Harness(testScheduler).also { h ->
    backgroundScope.coroutineContext[Job]!!.invokeOnCompletion { h.release() }
}
