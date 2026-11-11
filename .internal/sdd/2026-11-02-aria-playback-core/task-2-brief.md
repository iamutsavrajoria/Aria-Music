### Task 2: Queue model and engine core (queue, shuffle, repeat, navigation)

**Files:**
- Create: `playback/src/main/kotlin/app/aria/playback/QueueModel.kt`, `DefaultPlaybackEngine.kt`
- Test: `playback/src/test/kotlin/app/aria/playback/QueueModelTest.kt`, `EngineTestKit.kt`, `EngineQueueTest.kt`

**Interfaces:**
- Consumes: Task 1 contracts.
- Produces: `internal class QueueModel(random)` and `class DefaultPlaybackEngine(streams, store, radio, history, network, dispatcher, clock, random, workDispatcher) : PlaybackEngine, PlayerListener` with `attach(PlayerOutput)`, `detach(PlayerOutput)`, `release()`. Later tasks add members inside this class; the hooks `onCurrentChanged()` and `onStructureChanged()` exist from this task.

**Acceptance Criteria:**
- playNow / playNext / addToEnd / removeAt / move / clear / skipTo behave per tests and mirror to the output.
- Shuffle on keeps the current track playing and first; off restores insertion order (minus removed items).
- Previous restarts when more than 3 s were played. Repeat off/all/one behave per tests.
- Commands issued before `attach` take effect when the output attaches.

- [ ] **Step 1: Failing tests.** `QueueModelTest.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.Track
import app.aria.domain.TrackId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

internal fun track(n: Int) = Track(TrackId.youtube("track%06d".format(n)), "T$n", listOf("A"), 200_000L, null)

class QueueModelTest {
    private fun model(n: Int, start: Int = 0) = QueueModel(Random(1)).apply { replace((1..n).map(::track), start) }
    private fun QueueModel.titles() = items.map { it.track.title }

    @Test fun replaceSetsIndex() {
        val m = model(3, start = 1)
        assertEquals(listOf("T1", "T2", "T3"), m.titles()); assertEquals(1, m.index)
    }
    @Test fun insertNextGoesAfterCurrent() {
        val m = model(3, 1); val pos = m.insertNext(track(9))
        assertEquals(2, pos); assertEquals(listOf("T1", "T2", "T9", "T3"), m.titles()); assertEquals(1, m.index)
    }
    @Test fun removeBeforeCurrentShiftsIndex() {
        val m = model(4, 2); m.removeAt(0)
        assertEquals(1, m.index); assertEquals("T3", m.current?.track?.title)
    }
    @Test fun removeLastEmptiesQueue() {
        val m = model(1); m.removeAt(0)
        assertEquals(-1, m.index); assertEquals(emptyList(), m.items)
    }
    @Test fun moveTracksCurrent() {
        val m = model(4, 1); m.move(1, 3)
        assertEquals(3, m.index); assertEquals(listOf("T1", "T3", "T4", "T2"), m.titles())
        val m2 = model(4, 1); m2.move(3, 0)
        assertEquals(2, m2.index)
    }
    @Test fun shuffleKeepsCurrentFirstAndRoundTrips() {
        val m = model(6, 2); val cur = m.current
        m.setShuffle(true)
        assertEquals(cur, m.current); assertEquals(0, m.index); assertEquals(6, m.items.size)
        m.setShuffle(false)
        assertEquals(listOf("T1", "T2", "T3", "T4", "T5", "T6"), m.titles()); assertEquals(cur, m.current)
    }
    @Test fun shuffleOffDropsRemovedAndKeepsAddedInOrder() {
        val m = model(4, 0); m.setShuffle(true)
        val removed = m.items[2]; m.removeAt(2); m.append(listOf(track(7)))
        m.setShuffle(false)
        assertEquals(false, m.items.any { it.queueId == removed.queueId }); assertEquals("T7", m.items.last().track.title)
    }
    @Test fun dropPlayedFromFront() {
        val m = model(5, 3); m.dropPlayed(2)
        assertEquals(listOf("T3", "T4", "T5"), m.titles()); assertEquals(1, m.index)
    }
}
```

`EngineTestKit.kt` (shared fakes + harness; used by every later engine test):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.random.Random

class FakeOutput : PlayerOutput {
    override var listener: PlayerListener? = null
    var playlist: List<QueueItem> = emptyList(); var index = -1
    var playWhenReady = false; var position = 0L; var duration = 200_000L
    var volume = 1f; var repeatOne = false; var skipSilence = false
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
```

`EngineQueueTest.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.PlaybackStatus
import app.aria.domain.RepeatMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineQueueTest {
    private fun titles(h: Harness) = h.state.queue.map { it.track.title }

    @Test fun playNowMirrorsQueueToOutput() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.playNow((1..3).map(::track), 1); h.settle()
        assertEquals(listOf("T1", "T2", "T3"), h.output.playlist.map { it.track.title })
        assertEquals(1, h.output.index); assertTrue(h.output.playWhenReady); assertEquals(1, h.state.currentIndex)
        h.release()
    }
    @Test fun commandsBeforeAttachApplyOnAttach() = runTest {
        val h = Harness(testScheduler)
        h.engine.playNow((1..2).map(::track)); h.settle()
        h.attach()
        assertEquals(2, h.output.playlist.size); assertTrue(h.output.playWhenReady)
        h.release()
    }
    @Test fun playNextAddToEndRemoveMove() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.playNow((1..3).map(::track)); h.engine.playNext(track(9)); h.engine.addToEnd(listOf(track(7)))
        assertEquals(listOf("T1", "T9", "T2", "T3", "T7"), titles(h))
        h.engine.move(4, 0); assertEquals(listOf("T7", "T1", "T9", "T2", "T3"), titles(h)); assertEquals(1, h.state.currentIndex)
        h.engine.removeAt(0); assertEquals(0, h.state.currentIndex)
        assertEquals(titles(h), h.output.playlist.map { it.track.title })
        h.release()
    }
    @Test fun removingCurrentMovesToNeighbour() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.playNow((1..3).map(::track), 1); h.engine.removeAt(1)
        assertEquals("T3", h.state.current?.track?.title); assertEquals(1, h.output.index)
        h.release()
    }
    @Test fun addToEmptyQueueStartsPlaying() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.addToEnd(listOf(track(1)))
        assertEquals("T1", h.state.current?.track?.title); assertTrue(h.output.playWhenReady)
        h.release()
    }
    @Test fun clearEmptiesEverything() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.playNow((1..2).map(::track)); h.engine.clear()
        assertEquals(PlaybackStatus.Idle, h.state.status); assertEquals(emptyList(), h.output.playlist)
        h.release()
    }
    @Test fun shuffleUsesReorderAndKeepsCurrent() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.playNow((1..5).map(::track), 2); val cur = h.state.current
        h.engine.setShuffle(true)
        assertEquals(cur, h.state.current); assertEquals(0, h.state.currentIndex)
        assertTrue("reorder" in h.output.calls)
        h.engine.setShuffle(false)
        assertEquals(listOf("T1", "T2", "T3", "T4", "T5"), titles(h)); assertEquals(cur, h.state.current)
        h.release()
    }
    @Test fun previousRestartsAfterThreeSeconds() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.playNow((1..3).map(::track), 1)
        h.output.position = 3_500; h.engine.previous()
        assertEquals(1, h.state.currentIndex); assertEquals(0L, h.output.position)
        h.output.position = 1_000; h.engine.previous()
        assertEquals(0, h.state.currentIndex)
        h.release()
    }
    @Test fun nextAtEndStopsWhenRepeatOffAndWrapsWhenAll() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.playNow((1..2).map(::track), 1); h.engine.next()
        assertEquals(PlaybackStatus.Paused, h.state.status); assertEquals(1, h.state.currentIndex)
        h.engine.setRepeat(RepeatMode.ALL); h.engine.next()
        assertEquals(0, h.state.currentIndex); assertTrue(h.output.playWhenReady)
        h.release()
    }
    @Test fun repeatOneIsPassedToThePlayer() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.setRepeat(RepeatMode.ONE); assertTrue(h.output.repeatOne)
        h.release()
    }
    @Test fun playerEndedWrapsWithRepeatAll() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.playNow((1..2).map(::track), 1); h.engine.setRepeat(RepeatMode.ALL)
        h.engine.onEnded(); assertEquals(0, h.state.currentIndex)
        h.release()
    }
    @Test fun playerTransitionUpdatesIndex() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.playNow((1..3).map(::track)); h.engine.onItemTransition(1)
        assertEquals(1, h.state.currentIndex)
        h.release()
    }
}
```

- [ ] **Step 2:** Push; `:playback:test` fails (no `QueueModel`/engine). Expected: FAIL. Also add to `playback/build.gradle.kts`: `testImplementation(kotlin("test"))` is not available in an Android library by default, so use `libs.junit` and replace `kotlin.test` imports with `org.junit.Assert.*`-style **or** add `testImplementation(kotlin("test"))` (preferred; `:domain` already uses it). Use the latter.

- [ ] **Step 3: Implement `QueueModel.kt`:**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.QueueItem
import app.aria.domain.SavedQueue
import app.aria.domain.Track
import kotlin.random.Random

/** Pure queue structure. [items] is in play order; [original] is insertion order and differs only while shuffled. */
internal class QueueModel(private val random: Random) {
    var items: List<QueueItem> = emptyList(); private set
    var index: Int = -1; private set
    var shuffle: Boolean = false; private set
    var nextId: Long = 1L; private set
    private var original: List<QueueItem> = emptyList()

    val current: QueueItem? get() = items.getOrNull(index)
    val originalOrder: List<Long> get() = original.map { it.queueId }

    private fun item(track: Track) = QueueItem(nextId++, track)
    private fun syncOriginal() { if (!shuffle) original = items }

    fun replace(tracks: List<Track>, startIndex: Int) {
        original = tracks.map(::item)
        items = original
        index = startIndex.coerceIn(0, items.lastIndex)
        if (shuffle) applyShuffle()
    }

    /** Returns the position the new item took in play order. */
    fun insertNext(track: Track): Int {
        val new = item(track)
        val pos = (index + 1).coerceIn(0, items.size)
        items = items.toMutableList().apply { add(pos, new) }
        if (shuffle) {
            val at = original.indexOfFirst { it.queueId == current?.queueId }
            original = original.toMutableList().apply { add((at + 1).coerceIn(0, size), new) }
        } else syncOriginal()
        return pos
    }

    fun append(tracks: List<Track>) {
        val added = tracks.map(::item)
        items = items + added
        original = if (shuffle) original + added else items
    }

    fun removeAt(i: Int): QueueItem {
        val removed = items[i]
        items = items.toMutableList().apply { removeAt(i) }
        if (i < index) index-- else if (index > items.lastIndex) index = items.lastIndex
        original = if (shuffle) original.filter { it.queueId != removed.queueId } else items
        return removed
    }

    fun move(from: Int, to: Int) {
        val m = items.toMutableList(); val moved = m.removeAt(from); m.add(to, moved); items = m
        index = when {
            from == index -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
        syncOriginal()
    }

    fun jumpTo(i: Int) { index = i }
    fun clear() { items = emptyList(); original = emptyList(); index = -1 }

    fun setShuffle(on: Boolean) {
        if (on == shuffle) return
        if (on) { original = items; shuffle = true; applyShuffle() }
        else {
            val cur = current
            val present = items.mapTo(HashSet()) { it.queueId }
            shuffle = false
            items = original.filter { it.queueId in present }
            index = items.indexOfFirst { it.queueId == cur?.queueId }
            original = items
        }
    }

    private fun applyShuffle() {
        val cur = current ?: return
        items = listOf(cur) + items.filter { it.queueId != cur.queueId }.shuffled(random)
        index = 0
    }

    /** Removes [n] already-played items from the front (radio cap). */
    fun dropPlayed(n: Int) {
        if (n <= 0) return
        val dropped = items.take(n).mapTo(HashSet()) { it.queueId }
        items = items.drop(n); index -= n
        original = if (shuffle) original.filter { it.queueId !in dropped } else items
    }

    fun restore(saved: SavedQueue, currentIndex: Int) {
        items = saved.items; shuffle = saved.shuffle; nextId = saved.nextQueueId
        val byId = items.associateBy { it.queueId }
        original = if (shuffle) saved.originalOrder.mapNotNull(byId::get) else items
        index = if (items.isEmpty()) -1 else currentIndex.coerceIn(0, items.lastIndex)
    }
}
```

`DefaultPlaybackEngine.kt` (core; later tasks add members at the marked hooks):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random

/**
 * All playback behaviour, pure Kotlin. Every method must be called on [dispatcher] (main in production).
 * The engine owns queue order and content; the player owns position and play/pause (spec section 2).
 */
class DefaultPlaybackEngine(
    private val streams: StreamSource,
    private val store: QueueStore,
    private val radio: RadioSource,
    private val history: PlayEventRepository,
    private val network: NetworkMonitor,
    dispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    random: Random = Random.Default,
    /** Where stream resolution runs (CPU-heavy cipher work stays off the main thread). Tests pass the test dispatcher. */
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PlaybackEngine, PlayerListener {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    internal val model = QueueModel(random)
    private var output: PlayerOutput? = null

    private var repeatMode = RepeatMode.OFF
    private var wantPlay = false
    private var buffering = false
    private var waiting = false
    private var errorMsg: String? = null
    private var positionMs = 0L
    private var sleepTimer: SleepTimer? = null
    private var skipSilence = false
    private var normalizeLoudness = true
    private var endlessRadio = true

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    private var tickJob: Job? = null

    private fun status(): PlaybackStatus = when {
        errorMsg != null -> PlaybackStatus.Error(errorMsg!!)
        model.items.isEmpty() -> PlaybackStatus.Idle
        waiting -> PlaybackStatus.WaitingForNetwork
        !wantPlay -> PlaybackStatus.Paused
        buffering -> PlaybackStatus.Buffering
        else -> PlaybackStatus.Playing
    }

    private fun publish() {
        _state.value = PlaybackState(
            queue = model.items, currentIndex = model.index, status = status(), positionMs = positionMs,
            durationMs = output?.durationMs()?.takeIf { it > 0 } ?: model.current?.track?.durationMs ?: 0L,
            shuffle = model.shuffle, repeat = repeatMode, sleepTimer = sleepTimer,
            skipSilence = skipSilence, normalizeLoudness = normalizeLoudness, endlessRadio = endlessRadio,
        )
    }

    // ---- hooks extended by later tasks -------------------------------------------------------------
    /** The current track changed (command, transition or skip). */
    private fun onCurrentChanged() { positionMs = 0L; publish() }

    /** Queue items, order, shuffle or repeat changed. */
    private fun onStructureChanged() { publish() }

    // ---- lifecycle -----------------------------------------------------------------------------------
    fun attach(out: PlayerOutput) {
        output = out; out.listener = this
        out.setRepeatOne(repeatMode == RepeatMode.ONE); out.setSkipSilence(skipSilence)
        if (model.items.isNotEmpty()) out.setPlaylist(model.items, model.index, positionMs, wantPlay)
        updateTicker(); publish()
    }

    fun detach(out: PlayerOutput) {
        if (output !== out) return
        positionMs = out.positionMs(); out.listener = null; output = null
        wantPlay = false; buffering = false; updateTicker(); publish()
    }

    fun release() { scope.cancel() }

    private fun updateTicker() {
        val shouldRun = wantPlay && !waiting && output != null
        if (shouldRun && tickJob?.isActive != true) tickJob = scope.launch { while (true) { delay(TICK_MS); onTick() } }
        else if (!shouldRun) { tickJob?.cancel(); tickJob = null }
    }

    private fun onTick() {
        val out = output ?: return
        positionMs = out.positionMs()
        publish()
    }

    // ---- queue commands -------------------------------------------------------------------------------
    override fun playNow(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        model.replace(tracks, startIndex)
        errorMsg = null; wantPlay = true; buffering = true
        output?.setPlaylist(model.items, model.index, 0L, true)
        updateTicker(); onCurrentChanged(); onStructureChanged()
    }

    override fun playNext(track: Track) {
        if (model.items.isEmpty()) return playNow(listOf(track))
        val pos = model.insertNext(track)
        output?.insert(pos, listOf(model.items[pos])); onStructureChanged()
    }

    override fun addToEnd(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (model.items.isEmpty()) return playNow(tracks)
        val start = model.items.size
        model.append(tracks)
        output?.insert(start, model.items.subList(start, model.items.size)); onStructureChanged()
    }

    override fun removeAt(index: Int) {
        if (index !in model.items.indices) return
        val wasCurrent = index == model.index
        model.removeAt(index); output?.remove(index)
        if (model.items.isEmpty()) { wantPlay = false; buffering = false; updateTicker(); onCurrentChanged() }
        else if (wasCurrent) goTo(model.index, wantPlay)
        onStructureChanged()
    }

    override fun move(from: Int, to: Int) {
        if (from !in model.items.indices || to !in model.items.indices || from == to) return
        model.move(from, to); output?.move(from, to); onStructureChanged()
    }

    override fun clear() {
        model.clear(); output?.clear(); wantPlay = false; buffering = false; errorMsg = null
        updateTicker(); onCurrentChanged(); onStructureChanged()
    }

    override fun skipTo(index: Int) { if (index in model.items.indices) goTo(index, true) }

    private fun goTo(index: Int, play: Boolean) {
        model.jumpTo(index)
        errorMsg = null; wantPlay = play; buffering = play
        output?.seekTo(index, 0L); output?.setPlayWhenReady(play)
        updateTicker(); onCurrentChanged()
    }

    // ---- transport ------------------------------------------------------------------------------------
    override fun play() {
        if (model.items.isEmpty()) return
        if (errorMsg != null) { goTo(model.index, true); return }
        wantPlay = true; output?.setPlayWhenReady(true); updateTicker(); publish()
    }

    override fun pause() {
        wantPlay = false; output?.setPlayWhenReady(false); updateTicker(); publish()
    }

    private fun nextIndexOrNull(): Int? = when {
        model.items.isEmpty() -> null
        model.index < model.items.lastIndex -> model.index + 1
        repeatMode == RepeatMode.ALL -> 0
        else -> null
    }

    override fun next() {
        val target = nextIndexOrNull()
        if (target != null) goTo(target, true) else stopAtEnd()
    }

    override fun previous() {
        if (model.items.isEmpty()) return
        val played = output?.positionMs() ?: positionMs
        when {
            played > PREVIOUS_RESTART_MS -> seekTo(0L)
            model.index > 0 -> goTo(model.index - 1, true)
            repeatMode == RepeatMode.ALL -> goTo(model.items.lastIndex, true)
            else -> seekTo(0L)
        }
    }

    override fun seekTo(positionMs: Long) {
        this.positionMs = positionMs; output?.seekTo(model.index, positionMs); publish()
    }

    private fun stopAtEnd() {
        wantPlay = false; buffering = false; positionMs = 0L
        output?.setPlayWhenReady(false); output?.seekTo(model.index, 0L)
        updateTicker(); publish()
    }

    // ---- modes ----------------------------------------------------------------------------------------
    override fun setShuffle(on: Boolean) {
        if (on == model.shuffle) return
        model.setShuffle(on); output?.reorder(model.items, model.index); onStructureChanged()
    }

    override fun setRepeat(mode: RepeatMode) {
        repeatMode = mode; output?.setRepeatOne(mode == RepeatMode.ONE); onStructureChanged()
    }

    override fun setSleepTimer(durationMs: Long?) { /* Task 5 */ }
    override fun setSleepAtEndOfTrack(on: Boolean) { /* Task 5 */ }
    override fun setSkipSilence(on: Boolean) { /* Task 5 */ }
    override fun setNormalizeLoudness(on: Boolean) { /* Task 5 */ }
    override fun setEndlessRadio(on: Boolean) { /* Task 5 */ }

    // ---- PlayerListener -------------------------------------------------------------------------------
    override fun onItemTransition(index: Int) {
        if (index !in model.items.indices) return
        model.jumpTo(index); onCurrentChanged()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
        if (wantPlay == playWhenReady) return
        wantPlay = playWhenReady; updateTicker(); publish()
    }

    override fun onBufferingChanged(buffering: Boolean) { this.buffering = buffering; publish() }

    override fun onEnded() {
        if (repeatMode == RepeatMode.ALL && model.items.isNotEmpty()) goTo(0, true) else stopAtEnd()
    }

    override fun onError(error: PlayerError) { errorMsg = "Playback error"; wantPlay = false; updateTicker(); publish() }

    companion object {
        const val TICK_MS = 500L
        const val PREVIOUS_RESTART_MS = 3_000L
    }
}
```

> The five `{ /* Task 5 */ }` bodies are intentional: Task 5 owns them and its tests fail until it replaces them. They are not left in the final tree (Task 5 acceptance).

- [ ] **Step 4:** Push; `:playback:test` green for `QueueModelTest` and `EngineQueueTest`. The old `PlayerControllerTest` still passes (old code untouched until Task 8). Expected: PASS. Fix code, not tests, on mismatch; if a test expectation is itself wrong (for example the Harness index math), fix the test and say so in the commit message.
- [ ] **Step 5: Commit** `feat(playback): queue model and engine core`.

