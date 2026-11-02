# Aria Playback Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use beads-superpowers:subagent-driven-development (recommended) or beads-superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax. (`bd` is not installed in this environment; track tasks with the task list.)

**Goal:** A real background player: search results play with notification and lock-screen controls, survive restarts, and recover from stream errors, with a ported extractor and WebView PoToken path.

**Architecture:** A pure-Kotlin `DefaultPlaybackEngine` in `:playback` owns queue order, errors, history, resume, radio and sleep timer. A thin Media3 adapter (`Media3PlayerOutput`, `AriaPlaybackService`) mirrors the queue into ExoPlayer and resolves stream URLs at load time. `:innertube` gets the extractor adapter, signed cipher-config handling and a confined WebView PoToken generator. Spec: `.internal/specs/2026-11-02-aria-playback-core-design.md` (stress-tested, 7 branches). ADR: `docs/decisions/ADR-0002-playback-trust-boundaries.md`.

**Tech Stack:** Kotlin 2.4.10, Media3 1.10.1, Room 2.8.4, Hilt 2.60.1, coroutines 1.11.0, InnerTubeX v0.5.2, Robolectric 4.16.1 (SDK 35), JVM 21.

**Verification caveat (read first):** the sandbox cannot run Gradle. **CI is the only compiler.** Code below is uncompiled; "Expected" lines are predictions. Fix code, not expectations. Read CI with `env -u GH_TOKEN gh api repos/iamutsavrajoria/Aria-Music/actions/runs?event=push&per_page=1` and failure annotations via `check-runs/<jobId>/annotations`. A task is done only when CI is green for its commit. Media3 and InnerTubeX API names marked **(verify)** were written from memory or from summaries; confirm against the library source on CI failure.

## Global Constraints

- `compileSdk 37`, `targetSdk 36`, `minSdk 26`, JVM 21; Media3 1.10.1; Room 2.8.x; `isCoreLibraryDesugaringEnabled` on apps that use `:innertube`.
- `:playback` depends only on `:domain` (plus listed DSP modules later). No Hilt in `:playback`. `:innertube` may use `:domain` and `:core`. `checkModuleGraph` must stay green.
- Track IDs only through `TrackId`. Every new file starts with `// SPDX-License-Identifier: GPL-3.0-only`.
- Every ported file keeps its upstream header (if any), gets the SPDX line, and a row in `docs/provenance.csv` **in the same commit** (CI runs `scripts/check-provenance.py`). Upstream license must be read before porting; unknown or incompatible code is not ported (report BLOCKED).
- No third-party endpoints except YouTube. No Firebase/analytics. Cleartext traffic stays off. Release builds log no tokens, URLs with query strings, cookies or IPs.
- `aria.db` is excluded from backup; Room schema is not frozen until first beta (version stays 1; the committed `data/schemas/.../1.json` must be regenerated and committed when entities change).
- Engine rules: all engine methods run on one dispatcher (main in production). Engine never imports Android or Media3.
- Debug APKs are private Actions artifacts only; no GitHub release (ADR-0002, release gates).
- Cipher-config updates stay **disabled** until production keys exist.
- No `TODO(sub-project 2)` may remain at the end.

## File Structure

```
domain/src/main/kotlin/app/aria/domain/
  Sources.kt            (modify)  ResolvedStream.loudnessDb, StreamSource.markRejected, RadioSource, NetworkMonitor
  Playback.kt           (create)  PlaybackEngine, PlaybackState, QueueItem, RepeatMode, PlaybackEvent, PlayerOutput, PlayerListener, PlayerError
  QueueStore.kt         (create)  QueueStore, SavedQueue, SavedPosition, RestoredQueue
playback/src/main/kotlin/app/aria/playback/
  QueueModel.kt         (create)  pure queue + shuffle structure
  DefaultPlaybackEngine.kt (create) all behaviour
  Media3PlayerOutput.kt, AriaPlaybackService.kt, PlaybackEngineProvider.kt, TrustPolicy.kt, PlayerErrorMapper.kt
data/.../QueueEntities.kt, QueueDao.kt, QueueStoreImpl.kt
innertube/.../extraction/ (ExtractorSession, FailedClientMemory, ExtractedAudio), adapter/YouTubeStreamSource.kt, YouTubeRadioSource.kt
innertube/.../cipher/ (AriaPlayerConfigRepository, CipherConfigUpdater); core/.../cipher/CipherConfigVerifier.kt
innertube/.../potoken/ (ported, confined)
app/.../ AriaApp, di/AppModule, PlayerScreen, ConnectivityNetworkMonitor
scripts/check-webview-safety.sh (+ pytest), .github/workflows/debug-apk.yml, ci.yml (service job)
docs/ provider record, data-flow, release-gates, manual-device-checklist.md
```

---

### Task 1: Domain contracts

**Files:**
- Modify: `domain/build.gradle.kts`, `domain/src/main/kotlin/app/aria/domain/Sources.kt`
- Create: `domain/src/main/kotlin/app/aria/domain/Playback.kt`, `QueueStore.kt`
- Test: `domain/src/test/kotlin/app/aria/domain/PlaybackContractsTest.kt`

**Interfaces:**
- Consumes: existing `Track`, `TrackId`, `ResolvedStream`, `StreamSource`.
- Produces: everything listed in the code below; later tasks use these exact names.

**Acceptance Criteria:**
- `ResolvedStream` has `loudnessDb: Double? = null` (old call sites compile unchanged).
- `StreamSource.markRejected(id)` has a default no-op.
- `PlaybackState.current` returns the item at `currentIndex` or null.
- `:domain` stays pure Kotlin (only kotlinx-coroutines added).

- [ ] **Step 1: Failing test**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackContractsTest {
    private fun track(n: Int) = Track(TrackId.youtube("track%06d".format(n)), "T$n", listOf("A"), null, null)

    @Test fun currentIsItemAtIndex() {
        val q = listOf(QueueItem(1, track(1)), QueueItem(2, track(2)))
        assertEquals(2L, PlaybackState(queue = q, currentIndex = 1).current?.queueId)
        assertNull(PlaybackState(queue = q, currentIndex = -1).current)
        assertNull(PlaybackState().current)
    }

    @Test fun resolvedStreamLoudnessDefaultsToNull() {
        assertNull(ResolvedStream("https://x", "audio/webm", 1, null, null).loudnessDb)
    }
}
```

- [ ] **Step 2:** Push; CI `:domain:test` fails (unresolved `QueueItem`). Expected: FAIL.

- [ ] **Step 3: Implement.** `domain/build.gradle.kts`: add `api(libs.coroutines.core)` to `dependencies`. In `Sources.kt` replace `ResolvedStream` and `StreamSource`, add two interfaces:

```kotlin
data class ResolvedStream(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val contentLength: Long?,
    val expiresAtEpochMs: Long?,
    /** dB relative to the reference loudness; positive means louder than the reference. Null when unknown. */
    val loudnessDb: Double? = null,
)

interface StreamSource {
    suspend fun resolve(id: TrackId): Result<ResolvedStream>

    /** The player was refused (HTTP 403/410) using the last resolved stream; prefer a different client next time. */
    fun markRejected(id: TrackId) {}
}

interface RadioSource {
    /** Tracks related to [seed], at most [limit]. Failures are returned, never thrown. */
    suspend fun related(seed: Track, limit: Int): Result<List<Track>>
}

interface NetworkMonitor {
    val online: kotlinx.coroutines.flow.StateFlow<Boolean>
}
```

`Playback.kt`:

```kotlin
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
```

`QueueStore.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

data class SavedQueue(
    val items: List<QueueItem>,          // play order
    val originalOrder: List<Long>,       // queueIds in insertion order (differs from items only when shuffled)
    val shuffle: Boolean,
    val repeat: RepeatMode,
    val nextQueueId: Long,
)

data class SavedPosition(val index: Int, val positionMs: Long)

data class RestoredQueue(val queue: SavedQueue, val position: SavedPosition)

interface QueueStore {
    /** Replaces the whole structure in one transaction. */
    suspend fun saveStructure(queue: SavedQueue)
    suspend fun savePosition(position: SavedPosition)
    suspend fun load(): RestoredQueue?
}
```

- [ ] **Step 4:** Push; `:domain:test` and the whole `build` green. Expected: PASS. (Existing `StreamSource` implementers need no change thanks to the default.)
- [ ] **Step 5: Commit** `feat(domain): playback contracts and ports` (with the attribution lines).

---

### Task 2: Queue model and engine core (queue, shuffle, repeat, navigation)

**Files:**
- Create: `playback/src/main/kotlin/app/aria/playback/QueueModel.kt`, `DefaultPlaybackEngine.kt`
- Test: `playback/src/test/kotlin/app/aria/playback/QueueModelTest.kt`, `EngineTestKit.kt`, `EngineQueueTest.kt`

**Interfaces:**
- Consumes: Task 1 contracts.
- Produces: `internal class QueueModel(random)` and `class DefaultPlaybackEngine(streams, store, radio, history, network, dispatcher, clock, random) : PlaybackEngine, PlayerListener` with `attach(PlayerOutput)`, `detach(PlayerOutput)`, `release()`. Later tasks add members inside this class; the hooks `onCurrentChanged()` and `onStructureChanged()` exist from this task.

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
    var nowProvider: () -> Long = { 0L }
    override suspend fun resolve(id: TrackId): Result<ResolvedStream> {
        resolved += id
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
    val engine = DefaultPlaybackEngine(streams, store, radio, history, network, StandardTestDispatcher(scheduler), { now }, Random(1))
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

- [ ] **Step 4:** Push; `:playback:test` green for `QueueModelTest` and `EngineQueueTest`. The old `PlayerControllerTest` still passes (old code untouched until Task 7). Expected: PASS. Fix code, not tests, on mismatch; if a test expectation is itself wrong (for example the Harness index math), fix the test and say so in the commit message.
- [ ] **Step 5: Commit** `feat(playback): queue model and engine core`.

### Task 3: Engine stream URLs and error handling

**Files:**
- Modify: `playback/src/main/kotlin/app/aria/playback/DefaultPlaybackEngine.kt`
- Test: `playback/src/test/kotlin/app/aria/playback/EngineErrorsTest.kt`

**Interfaces:**
- Consumes: Task 2 engine (`onCurrentChanged`, `goTo`, `nextIndexOrNull`, `stopAtEnd`, `updateTicker`, `publish`, `_events`), `FakeStreams`, `FakeNetwork`, `FakeOutput.retries`.
- Produces: `suspend fun streamFor(id: TrackId): Result<ResolvedStream>` (thread-safe; the Media3 adapter calls it from the loader thread); error behaviour per spec section 3 "Errors".

**Acceptance Criteria:**
- A resolved URL is cached; an expired one (within 60 s of `expiresAtEpochMs`) is re-resolved.
- The current and next tracks' URLs are resolved when the current track changes.
- `PlayerError.Permanent`: skip immediately, no retry, `PlaybackEvent.Skipped` emitted.
- `Http(403|410)`: one refresh (`markRejected`, cache dropped, `retry`); a second one is treated as transient.
- Transient: retries at 1 s, 2 s, 4 s then skip. Offline: status `WaitingForNetwork`, no skip, retry when online again.
- Three consecutive failed tracks stop the engine in `PlaybackStatus.Error` with `PlaybackEvent.Stopped`; a track that reaches ready-while-playing resets the count.

- [ ] **Step 1: Failing tests** `EngineErrorsTest.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class EngineErrorsTest {
    private fun started(h: Harness, n: Int = 3, start: Int = 0) { h.engine.playNow((1..n).map(::track), start); h.settle() }

    @Test fun urlIsCachedUntilExpiry() = runTest {
        val h = Harness(testScheduler).attach(); val id = track(1).id
        h.streams.results[id] = Result.success(ResolvedStream("https://a", "audio/webm", 1, null, h.now + 120_000))
        assertTrue(h.engine.streamFor(id).isSuccess); h.engine.streamFor(id)
        assertEquals(1, h.streams.resolved.count { it == id })
        h.now += 100_000  // inside the 60 s margin of expiry
        h.engine.streamFor(id)
        assertEquals(2, h.streams.resolved.count { it == id })
        h.release()
    }
    @Test fun currentAndNextAreResolvedOnTrackChange() = runTest {
        val h = Harness(testScheduler).attach(); started(h)
        assertTrue(track(1).id in h.streams.resolved); assertTrue(track(2).id in h.streams.resolved)
        assertFalse(track(3).id in h.streams.resolved)
        h.release()
    }
    @Test fun permanentErrorSkipsImmediatelyWithoutRetry() = runTest {
        val h = Harness(testScheduler).attach(); started(h)
        val seen = mutableListOf<PlaybackEvent>(); val job = backgroundScope.launch { h.engine.events.toList(seen) }; h.settle()
        h.engine.onError(PlayerError.Permanent); h.settle()
        assertEquals(1, h.state.currentIndex); assertTrue(h.output.retries.isEmpty())
        assertTrue(seen.single() is PlaybackEvent.Skipped)
        job.cancel(); h.release()
    }
    @Test fun http403RefreshesOnceThenBacksOff() = runTest {
        val h = Harness(testScheduler).attach(); started(h)
        h.engine.onError(PlayerError.Http(403)); h.settle()
        assertEquals(listOf(track(1).id), h.streams.rejected); assertEquals(1, h.output.retries.size)
        h.engine.onError(PlayerError.Http(403))          // second 403: transient path
        h.advance(999); assertEquals(1, h.output.retries.size)
        h.advance(1);   assertEquals(2, h.output.retries.size)
        h.release()
    }
    @Test fun transientRetriesAtOneTwoFourSecondsThenSkips() = runTest {
        val h = Harness(testScheduler).attach(); started(h)
        h.engine.onError(PlayerError.Io); h.advance(1_000); assertEquals(1, h.output.retries.size)
        h.engine.onError(PlayerError.Io); h.advance(2_000); assertEquals(2, h.output.retries.size)
        h.engine.onError(PlayerError.Io); h.advance(4_000); assertEquals(3, h.output.retries.size)
        h.engine.onError(PlayerError.Io); h.settle()
        assertEquals(1, h.state.currentIndex)
        h.release()
    }
    @Test fun offlineWaitsThenResumes() = runTest {
        val h = Harness(testScheduler).attach(); started(h)
        h.network.online.value = false
        h.engine.onError(PlayerError.Io); h.settle()
        assertEquals(PlaybackStatus.WaitingForNetwork, h.state.status); assertEquals(0, h.state.currentIndex)
        h.advance(60_000); assertEquals(0, h.state.currentIndex)
        h.network.online.value = true; h.settle()
        assertEquals(1, h.output.retries.size); assertNotEquals(PlaybackStatus.WaitingForNetwork, h.state.status)
        h.release()
    }
    @Test fun threeConsecutiveFailuresStop() = runTest {
        val h = Harness(testScheduler).attach(); started(h, n = 6)
        repeat(3) { h.engine.onError(PlayerError.Permanent); h.settle() }
        assertTrue(h.state.status is PlaybackStatus.Error); assertFalse(h.output.playWhenReady)
        h.release()
    }
    @Test fun successResetsTheFailureCount() = runTest {
        val h = Harness(testScheduler).attach(); started(h, n = 6)
        h.engine.onError(PlayerError.Permanent); h.engine.onError(PlayerError.Permanent)
        h.engine.onBufferingChanged(false)               // a track reached ready while playing
        h.engine.onError(PlayerError.Permanent); h.settle()
        assertFalse(h.state.status is PlaybackStatus.Error)
        h.release()
    }
}
```

(Add `import kotlinx.coroutines.flow.toList` and `import kotlinx.coroutines.launch` at the top.)

- [ ] **Step 2:** Push. Expected: FAIL (`streamFor` missing; `onError` sets a generic error).

- [ ] **Step 3: Implement.** In `DefaultPlaybackEngine.kt` add imports `java.util.concurrent.ConcurrentHashMap`, then these members (place the fields after `private var tickJob`, the functions before `companion object`), **replace** the Task 2 `onError`, **extend** `onCurrentChanged` and `onBufferingChanged`:

```kotlin
    // fields
    private val resolved = ConcurrentHashMap<TrackId, ResolvedStream>()
    private var consecutiveFailures = 0
    private var retryCount = 0
    private var refreshTried = false
    private var retryJob: Job? = null

    /** Thread-safe: also called from ExoPlayer's loader thread by the Media3 adapter. */
    suspend fun streamFor(id: TrackId): Result<ResolvedStream> {
        resolved[id]?.takeIf { !it.isExpired() }?.let { return Result.success(it) }
        return streams.resolve(id).onSuccess { resolved[id] = it }
    }

    private fun ResolvedStream.isExpired(): Boolean {
        val expires = expiresAtEpochMs ?: return false
        return expires - EXPIRY_MARGIN_MS <= clock()
    }

    private fun prefetch(item: QueueItem?) { if (item != null) scope.launch { streamFor(item.track.id) } }

    private fun reload() { output?.retry(output?.positionMs() ?: positionMs) }

    override fun onError(error: PlayerError) {
        val item = model.current ?: return
        retryJob?.cancel()
        when (error) {
            PlayerError.Permanent -> skipAfterFailure(item, "unavailable")
            is PlayerError.Http ->
                if ((error.code == 403 || error.code == 410) && !refreshTried) {
                    refreshTried = true
                    resolved.remove(item.track.id); streams.markRejected(item.track.id)
                    reload()
                } else transient(item)
            PlayerError.Io -> transient(item)
        }
    }

    private fun transient(item: QueueItem) {
        if (!network.online.value) { waitForNetwork(); return }
        if (retryCount >= BACKOFF_MS.size) { skipAfterFailure(item, "network error"); return }
        val wait = BACKOFF_MS[retryCount++]
        retryJob = scope.launch { delay(wait); reload() }
    }

    private fun waitForNetwork() {
        waiting = true; updateTicker(); publish()
        retryJob = scope.launch {
            network.online.first { it }
            waiting = false; updateTicker(); reload(); publish()
        }
    }

    private fun skipAfterFailure(item: QueueItem, reason: String) {
        retryCount = 0
        consecutiveFailures++
        _events.tryEmit(PlaybackEvent.Skipped(item.track, reason))
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            val msg = "Stopped after $MAX_CONSECUTIVE_FAILURES tracks failed in a row"
            errorMsg = msg; wantPlay = false; buffering = false
            output?.setPlayWhenReady(false); updateTicker()
            _events.tryEmit(PlaybackEvent.Stopped(msg)); publish(); return
        }
        val target = nextIndexOrNull()
        if (target != null) goTo(target, true) else stopAtEnd()
    }
```

`onCurrentChanged` becomes:

```kotlin
    private fun onCurrentChanged() {
        retryJob?.cancel(); waiting = false; retryCount = 0; refreshTried = false
        positionMs = 0L
        prefetch(model.current)
        prefetch(model.items.getOrNull(model.index + 1))
        publish()
    }
```

`onBufferingChanged` becomes:

```kotlin
    override fun onBufferingChanged(buffering: Boolean) {
        this.buffering = buffering
        if (!buffering && wantPlay) { consecutiveFailures = 0; retryCount = 0 }
        publish()
    }
```

Add to the `companion object`: `const val EXPIRY_MARGIN_MS = 60_000L`, `const val MAX_CONSECUTIVE_FAILURES = 3`, `val BACKOFF_MS = longArrayOf(1_000, 2_000, 4_000)`. In `play()` the error branch must also reset: `consecutiveFailures = 0`.

- [ ] **Step 4:** Push; `:playback:test` green (Task 2 tests still pass). Expected: PASS.
- [ ] **Step 5: Commit** `feat(playback): stream URL cache and classified error handling`.

---

### Task 4: Engine history and resume

**Files:**
- Modify: `DefaultPlaybackEngine.kt`
- Test: `playback/src/test/kotlin/app/aria/playback/EngineHistoryResumeTest.kt`

**Interfaces:**
- Consumes: `PlayEventRepository`, `QueueStore`, `SavedQueue`, `SavedPosition`, `RestoredQueue`, `Harness.store`, `Harness.history`.
- Produces: `suspend fun restore()` on the engine (called once at app start).

**Acceptance Criteria:**
- A play is recorded once per track, after 30 s of actual listening while playing (not on exit, not while paused or buffering, seeking does not add time).
- Structure is saved only when it changes (one write per change, latest wins). Position is saved on pause, on track change and every 15 s while playing.
- `restore()` rebuilds queue, shuffle, repeat, index and position, paused, and pushes it to an attached output. It does nothing if the engine already has a queue.

- [ ] **Step 1: Failing tests:**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class EngineHistoryResumeTest {
    private fun playing(h: Harness, n: Int = 2): Harness {
        h.engine.playNow((1..n).map(::track)); h.settle()
        h.engine.onPlayWhenReadyChanged(true); h.engine.onBufferingChanged(false); h.settle()
        return h
    }

    @Test fun recordedOnceAtThirtySeconds() = runTest {
        val h = playing(Harness(testScheduler).attach())
        h.advance(29_500); assertEquals(0, h.history.recorded.size)
        h.advance(1_000); assertEquals(1, h.history.recorded.size)
        h.advance(60_000); assertEquals(1, h.history.recorded.size)
        assertEquals(track(1), h.history.recorded.single().first)
        h.release()
    }
    @Test fun pausedTimeDoesNotCount() = runTest {
        val h = playing(Harness(testScheduler).attach())
        h.advance(20_000); h.engine.pause(); h.advance(60_000)
        assertEquals(0, h.history.recorded.size)
        h.engine.play(); h.advance(10_500)
        assertEquals(1, h.history.recorded.size)
        h.release()
    }
    @Test fun newTrackStartsANewCount() = runTest {
        val h = playing(Harness(testScheduler).attach())
        h.advance(20_000); h.engine.next(); h.engine.onBufferingChanged(false); h.advance(20_000)
        assertEquals(0, h.history.recorded.size)
        h.advance(10_500); assertEquals(1, h.history.recorded.size); assertEquals(track(2), h.history.recorded.single().first)
        h.release()
    }
    @Test fun structureSavedOnlyOnChange() = runTest {
        val h = playing(Harness(testScheduler).attach()); val base = h.store.structureSaves
        h.advance(40_000); assertEquals(base, h.store.structureSaves)
        h.engine.addToEnd(listOf(track(9))); h.settle(); assertEquals(base + 1, h.store.structureSaves)
        assertEquals(3, h.store.saved?.items?.size)
        h.release()
    }
    @Test fun positionSavedOnPauseTrackChangeAndEveryFifteenSeconds() = runTest {
        val h = playing(Harness(testScheduler).attach())
        h.output.position = 14_000; h.advance(15_000)
        assertTrue(h.store.positionSaves.any { it.positionMs == 14_000L })
        val before = h.store.positionSaves.size
        h.output.position = 20_000; h.engine.pause(); h.settle()
        assertEquals(before + 1, h.store.positionSaves.size); assertEquals(20_000L, h.store.position?.positionMs)
        h.engine.next(); h.settle()
        assertEquals(1, h.store.position?.index)
        h.release()
    }
    @Test fun restoreRebuildsPausedAtSavedPosition() = runTest {
        val a = playing(Harness(testScheduler).attach())
        a.engine.setShuffle(true); a.engine.setRepeat(RepeatMode.ALL)
        a.output.position = 33_000; a.engine.pause(); a.settle(); a.release()

        val b = Harness(testScheduler); b.store.saved = a.store.saved; b.store.position = a.store.position
        b.attach(); b.engine.restore(); b.settle()
        assertEquals(a.state.queue.map { it.queueId }, b.state.queue.map { it.queueId })
        assertTrue(b.state.shuffle); assertEquals(RepeatMode.ALL, b.state.repeat)
        assertEquals(PlaybackStatus.Paused, b.state.status); assertEquals(33_000L, b.output.position)
        assertFalse(b.output.playWhenReady)
        b.release()
    }
    @Test fun restoreIgnoredWhenQueueAlreadyExists() = runTest {
        val h = Harness(testScheduler).attach(); h.engine.playNow(listOf(track(1)))
        h.store.saved = SavedQueue(listOf(QueueItem(5, track(5))), listOf(5), false, RepeatMode.OFF, 6)
        h.store.position = SavedPosition(0, 0); h.engine.restore()
        assertEquals("T1", h.state.current?.track?.title)
        h.release()
    }
}
```

- [ ] **Step 2:** Push. Expected: FAIL (`restore` missing; nothing recorded or saved).

- [ ] **Step 3: Implement.** Add imports `kotlinx.coroutines.channels.Channel`. Fields (with the others):

```kotlin
    private var listenedMs = 0L
    private var recorded = false
    private var sinceSaveMs = 0L
    private val structureSaves = Channel<SavedQueue>(Channel.CONFLATED)
    private val positionSaves = Channel<SavedPosition>(Channel.CONFLATED)

    init {
        // Single writers per channel: saves are serialized and the latest value wins.
        scope.launch { for (s in structureSaves) runCatching { store.saveStructure(s) } }
        scope.launch { for (p in positionSaves) runCatching { store.savePosition(p) } }
    }
```

Replace `onTick` and extend the two hooks:

```kotlin
    private fun onTick() {
        val out = output ?: return
        positionMs = out.positionMs()
        if (status() == PlaybackStatus.Playing) {
            listenedMs += TICK_MS; sinceSaveMs += TICK_MS
            if (!recorded && listenedMs >= HISTORY_THRESHOLD_MS) { recorded = true; recordPlay() }
            if (sinceSaveMs >= POSITION_SAVE_MS) savePosition()
        }
        publish()
    }

    private fun recordPlay() {
        val track = model.current?.track ?: return
        val played = listenedMs; val startedAt = clock() - played
        scope.launch { runCatching { history.record(track, startedAt, played) } }
    }

    private fun savePosition() {
        sinceSaveMs = 0L
        positionSaves.trySend(SavedPosition(model.index, positionMs))
    }

    private fun snapshot() = SavedQueue(model.items, model.originalOrder, model.shuffle, repeatMode, model.nextId)

    suspend fun restore() {
        if (model.items.isNotEmpty()) return
        val saved = runCatching { store.load() }.getOrNull() ?: return
        if (model.items.isNotEmpty()) return            // a command arrived while loading
        model.restore(saved.queue, saved.position.index)
        repeatMode = saved.queue.repeat; positionMs = saved.position.positionMs
        wantPlay = false; buffering = false
        output?.setRepeatOne(repeatMode == RepeatMode.ONE)
        if (model.items.isNotEmpty()) output?.setPlaylist(model.items, model.index, positionMs, false)
        updateTicker(); publish()
    }
```

`onCurrentChanged` additionally (at its start): `listenedMs = 0L; recorded = false; sinceSaveMs = 0L` and (before `publish()`) `if (model.items.isNotEmpty()) savePosition()`. `onStructureChanged` becomes `{ structureSaves.trySend(snapshot()); publish() }`. In `pause()` and in `onPlayWhenReadyChanged` when `playWhenReady == false`, call `positionMs = output?.positionMs() ?: positionMs; savePosition()` before `publish()`. In `detach`, call `savePosition()` after `positionMs = out.positionMs()`. Companion: `const val HISTORY_THRESHOLD_MS = 30_000L`, `const val POSITION_SAVE_MS = 15_000L`. In `playNow`, `clear` etc. no extra code (they already call the hooks).

`restore()` note: `playNow` of a restored session after process death is a normal command; the position write in `onCurrentChanged` for an empty queue is skipped by the `isNotEmpty` guard.

- [ ] **Step 4:** Push; all engine tests green. Expected: PASS.
- [ ] **Step 5: Commit** `feat(playback): history at 30 s, queue persistence and restore`.

---

### Task 5: Engine endless radio, sleep timer, skip silence and loudness

**Files:**
- Modify: `DefaultPlaybackEngine.kt`
- Test: `playback/src/test/kotlin/app/aria/playback/EngineExtrasTest.kt`

**Interfaces:**
- Consumes: `RadioSource`, `FakeRadio`, `ResolvedStream.loudnessDb`, `PlayerOutput.setVolume/setSkipSilence`.
- Produces: working `setSleepTimer`, `setSleepAtEndOfTrack`, `setSkipSilence`, `setNormalizeLoudness`, `setEndlessRadio`. Settings are in memory only (user settings screens come in sub-project 3); they reset to defaults after process death.

**Acceptance Criteria:**
- When fewer than 5 tracks remain and repeat is off, a batch of at most 25 new (deduplicated) tracks is appended; the queue never exceeds 500 (played tracks are dropped from the front); failure is silent and retried on the next track change; repeat on disables radio.
- Sleep timer: after the duration the volume fades to 0 over 5 s, then pauses and restores volume; "end of current track" pauses at the next transition and leaves the next track ready at 0. Setting null cancels.
- Skip silence is forwarded. Loudness only attenuates: `volume = 10^(-db/20)` for `db > 0`, never below 0.1, 1.0 for `db <= 0`, null or when disabled.
- No `/* Task 5 */` placeholder remains.

- [ ] **Step 1: Failing tests:**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class EngineExtrasTest {
    private fun radioTracks(from: Int, n: Int) = (from until from + n).map(::track)

    @Test fun radioTopsUpWhenFewRemain() = runTest {
        val h = Harness(testScheduler).attach()
        h.radio.result = Result.success(radioTracks(100, 40))
        h.engine.playNow((1..3).map(::track)); h.settle()
        assertEquals(3 + 25, h.state.queue.size); assertEquals(1, h.radio.calls.size)
        assertEquals(h.state.queue.size, h.output.playlist.size)
        h.release()
    }
    @Test fun radioSkipsDuplicatesAndFailureIsSilent() = runTest {
        val h = Harness(testScheduler).attach()
        h.radio.result = Result.success(listOf(track(1), track(2), track(50)))
        h.engine.playNow((1..2).map(::track)); h.settle()
        assertEquals(listOf("T1", "T2", "T50"), h.state.queue.map { it.track.title })
        h.radio.result = Result.failure(RuntimeException("offline")); h.engine.next(); h.settle()
        assertTrue(h.state.status !is PlaybackStatus.Error)
        h.release()
    }
    @Test fun radioOffWithRepeatOrToggle() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.setRepeat(RepeatMode.ALL); h.engine.playNow((1..2).map(::track)); h.settle()
        assertEquals(0, h.radio.calls.size)
        h.engine.setRepeat(RepeatMode.OFF); h.engine.setEndlessRadio(false); h.engine.next(); h.settle()
        assertEquals(0, h.radio.calls.size)
        h.release()
    }
    @Test fun queueIsCappedAtFiveHundredByDroppingPlayedTracks() = runTest {
        val h = Harness(testScheduler).attach()
        h.engine.setEndlessRadio(false)
        h.engine.playNow((1..490).map(::track), 484); h.settle()
        h.engine.setEndlessRadio(true)
        h.radio.result = Result.success(radioTracks(1000, 25)); h.engine.next(); h.settle()
        assertTrue(h.state.queue.size <= 500)
        assertEquals(h.state.queue.map { it.queueId }, h.output.playlist.map { it.queueId })
        assertEquals(h.state.current?.queueId, h.output.playlist[h.output.index].queueId)
        h.release()
    }
    @Test fun sleepTimerFadesThenPauses() = runTest {
        val h = Harness(testScheduler).attach(); h.engine.setEndlessRadio(false)
        h.engine.playNow((1..2).map(::track)); h.engine.onBufferingChanged(false); h.settle()
        h.engine.setSleepTimer(60_000)
        h.advance(54_000); assertEquals(1f, h.output.volume)
        h.advance(3_000); assertTrue(h.output.volume < 1f && h.output.volume > 0f)
        h.advance(3_100)
        assertFalse(h.output.playWhenReady); assertEquals(1f, h.output.volume); assertNull(h.state.sleepTimer)
        h.release()
    }
    @Test fun sleepTimerCanBeCancelled() = runTest {
        val h = Harness(testScheduler).attach(); h.engine.setEndlessRadio(false)
        h.engine.playNow((1..2).map(::track)); h.settle()
        h.engine.setSleepTimer(10_000); h.engine.setSleepTimer(null); h.advance(30_000)
        assertTrue(h.output.playWhenReady)
        h.release()
    }
    @Test fun endOfTrackPausesAtTransition() = runTest {
        val h = Harness(testScheduler).attach(); h.engine.setEndlessRadio(false)
        h.engine.playNow((1..3).map(::track)); h.engine.setSleepAtEndOfTrack(true)
        assertEquals(true, h.state.sleepTimer?.endOfTrack)
        h.engine.onItemTransition(1)
        assertFalse(h.output.playWhenReady); assertEquals(0L, h.output.position); assertNull(h.state.sleepTimer)
        h.release()
    }
    @Test fun skipSilenceIsForwarded() = runTest {
        val h = Harness(testScheduler).attach(); h.engine.setSkipSilence(true)
        assertTrue(h.output.skipSilence); assertTrue(h.state.skipSilence)
        h.release()
    }
    @Test fun loudnessOnlyAttenuates() = runTest {
        val h = Harness(testScheduler).attach(); h.engine.setEndlessRadio(false)
        val loud = track(1).id; val quiet = track(2).id
        h.streams.results[loud] = Result.success(ResolvedStream("https://a", "audio/webm", 1, null, null, loudnessDb = 6.0))
        h.streams.results[quiet] = Result.success(ResolvedStream("https://b", "audio/webm", 1, null, null, loudnessDb = -5.0))
        h.engine.playNow((1..2).map(::track)); h.settle()
        assertEquals(0.5f, h.output.volume, 0.01f)                       // 10^(-6/20)
        h.engine.next(); h.settle(); assertEquals(1f, h.output.volume)   // quieter track is left alone
        h.engine.skipTo(0); h.settle(); h.engine.setNormalizeLoudness(false)
        assertEquals(1f, h.output.volume)
        h.release()
    }
}
```

- [ ] **Step 2:** Push. Expected: FAIL.

- [ ] **Step 3: Implement.** Add imports `kotlin.math.pow`. Fields:

```kotlin
    private var radioBusy = false
    private var sleepJob: Job? = null
    private var sleepEndOfTrack = false
    private var fadeFactor = 1f
```

Replace the five placeholder methods and add helpers:

```kotlin
    override fun setSkipSilence(on: Boolean) { skipSilence = on; output?.setSkipSilence(on); publish() }

    override fun setNormalizeLoudness(on: Boolean) { normalizeLoudness = on; applyVolume(); publish() }

    override fun setEndlessRadio(on: Boolean) { endlessRadio = on; maybeTopUp(); publish() }

    override fun setSleepTimer(durationMs: Long?) {
        cancelSleep()
        if (durationMs == null) { publish(); return }
        sleepTimer = SleepTimer(clock() + durationMs, endOfTrack = false)
        sleepJob = scope.launch {
            delay((durationMs - FADE_MS).coerceAtLeast(0L))
            for (i in 1..FADE_STEPS) { fadeFactor = 1f - i / FADE_STEPS.toFloat(); applyVolume(); delay(FADE_MS / FADE_STEPS) }
            pause(); fadeFactor = 1f; applyVolume(); sleepTimer = null; publish()
        }
        publish()
    }

    override fun setSleepAtEndOfTrack(on: Boolean) {
        cancelSleep()
        sleepEndOfTrack = on
        if (on) sleepTimer = SleepTimer(null, endOfTrack = true)
        publish()
    }

    private fun cancelSleep() {
        sleepJob?.cancel(); sleepJob = null
        fadeFactor = 1f; sleepEndOfTrack = false; sleepTimer = null; applyVolume()
    }

    /** Normalization only attenuates, so it can never clip (spec branch 7). */
    private fun loudnessGain(): Float {
        if (!normalizeLoudness) return 1f
        val db = model.current?.let { resolved[it.track.id]?.loudnessDb } ?: return 1f
        if (db <= 0.0) return 1f
        return 10.0.pow(-db / 20.0).toFloat().coerceAtLeast(MIN_GAIN)
    }

    private fun applyVolume() { output?.setVolume((loudnessGain() * fadeFactor).coerceIn(0f, 1f)) }

    private fun maybeTopUp() {
        if (!endlessRadio || repeatMode != RepeatMode.OFF || radioBusy) return
        val seed = model.current ?: return
        if (model.items.size - model.index - 1 >= RADIO_TRIGGER) return
        radioBusy = true
        scope.launch {
            try { radio.related(seed.track, RADIO_BATCH).onSuccess { appendRadio(it) } }
            finally { radioBusy = false }
        }
    }

    private fun appendRadio(found: List<Track>) {
        val known = model.items.mapTo(HashSet()) { it.track.id }
        var fresh = found.filter { known.add(it.id) }.take(RADIO_BATCH)
        val overflow = model.items.size + fresh.size - QUEUE_CAP
        if (overflow > 0) {
            val drop = minOf(overflow, model.index)
            repeat(drop) { output?.remove(0) }
            model.dropPlayed(drop)
            fresh = fresh.take((QUEUE_CAP - model.items.size).coerceAtLeast(0))
        }
        if (fresh.isEmpty()) { onStructureChanged(); return }
        val start = model.items.size
        model.append(fresh)
        output?.insert(start, model.items.subList(start, model.items.size)); onStructureChanged()
    }

    /** Sleep "end of track": the player already started the next item, so pause and park it at 0. */
    private fun applyEndOfTrackSleep(index: Int) {
        if (!sleepEndOfTrack) return
        sleepEndOfTrack = false; sleepTimer = null; wantPlay = false
        output?.setPlayWhenReady(false); output?.seekTo(index, 0L); updateTicker()
    }
```

Hook edits: `onCurrentChanged` (end, before `publish()`) adds `applyVolume(); maybeTopUp()`. `streamFor` after `onSuccess { resolved[id] = it }` also `if (id == model.current?.track?.id) scope.launch { applyVolume() }`. `setRepeat` adds `maybeTopUp()`. `onItemTransition` after `onCurrentChanged()` adds `applyEndOfTrackSleep(index)`. `onEnded`: first line `sleepEndOfTrack = false; sleepTimer = null`. Companion: `RADIO_TRIGGER = 5`, `RADIO_BATCH = 25`, `QUEUE_CAP = 500`, `FADE_MS = 5_000L`, `FADE_STEPS = 20`, `MIN_GAIN = 0.1f`.

- [ ] **Step 4:** Push; all engine tests green. Expected: PASS. Then `grep -n "Task 5" playback/src/main` returns nothing.
- [ ] **Step 5: Commit** `feat(playback): endless radio, sleep timer, skip silence, loudness`.

### Task 6: `QueueStore` in `:data`

**Files:**
- Create: `data/src/main/kotlin/app/aria/data/entities/QueueEntities.kt`, `data/src/main/kotlin/app/aria/data/QueueDao.kt`, `data/src/main/kotlin/app/aria/data/QueueStoreImpl.kt`
- Modify: `data/src/main/kotlin/app/aria/data/AriaDatabase.kt`, `data/schemas/app.aria.data.AriaDatabase/1.json`, `.github/workflows/ci.yml`
- Test: `data/src/test/kotlin/app/aria/data/QueueStoreTest.kt`

**Interfaces:**
- Consumes: `QueueStore`, `SavedQueue`, `SavedPosition`, `RestoredQueue`, `QueueItem`, `Track`, `TrackId`.
- Produces: `class QueueStoreImpl(private val dao: QueueDao) : QueueStore`; `AriaDatabase.queueDao()`.

**Acceptance Criteria:**
- `saveStructure` replaces all rows in one transaction; `savePosition` touches only the position row; `load()` returns null when nothing was saved and otherwise the exact items, original order, shuffle, repeat and next id.
- The committed schema JSON contains the two new tables (CI step "Schema is committed and unchanged" passes).
- Database version stays 1 and there is no migration (schema is not frozen before beta).

- [ ] **Step 1: Failing test:**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueStoreTest {
    private lateinit var db: AriaDatabase
    private lateinit var store: QueueStoreImpl
    private fun item(id: Long, vid: String) =
        QueueItem(id, Track(TrackId.youtube(vid), "Title $id", listOf("A", "B"), 123_000L, "https://t/$id"))

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AriaDatabase::class.java)
            .allowMainThreadQueries().build()
        store = QueueStoreImpl(db.queueDao())
    }
    @After fun close() = db.close()

    @Test fun loadIsNullWhenNothingSaved() = runBlocking { assertNull(store.load()) }

    @Test fun roundTripKeepsEverything() = runBlocking {
        val items = listOf(item(3, "aaaaaaaaaa1"), item(1, "aaaaaaaaaa2"), item(2, "aaaaaaaaaa3"))
        val q = SavedQueue(items, listOf(1, 2, 3), shuffle = true, repeat = RepeatMode.ALL, nextQueueId = 4)
        store.saveStructure(q); store.savePosition(SavedPosition(1, 42_000))
        val r = store.load()!!
        assertEquals(q, r.queue); assertEquals(SavedPosition(1, 42_000), r.position)
    }

    @Test fun structureReplacesOldRows() = runBlocking {
        store.saveStructure(SavedQueue(listOf(item(1, "aaaaaaaaaa1"), item(2, "aaaaaaaaaa2")), listOf(1, 2), false, RepeatMode.OFF, 3))
        store.saveStructure(SavedQueue(listOf(item(2, "aaaaaaaaaa2")), listOf(2), false, RepeatMode.ONE, 3))
        store.savePosition(SavedPosition(0, 0))
        val r = store.load()!!
        assertEquals(listOf(2L), r.queue.items.map { it.queueId }); assertEquals(RepeatMode.ONE, r.queue.repeat)
    }

    @Test fun positionSaveDoesNotTouchStructure() = runBlocking {
        val q = SavedQueue(listOf(item(1, "aaaaaaaaaa1")), listOf(1), false, RepeatMode.OFF, 2)
        store.saveStructure(q); store.savePosition(SavedPosition(0, 1)); store.savePosition(SavedPosition(0, 2))
        assertEquals(q, store.load()!!.queue); assertEquals(2L, store.load()!!.position.positionMs)
    }
}
```

- [ ] **Step 2:** Push; CI fails (`queueDao` missing). Expected: FAIL.

- [ ] **Step 3: Implement.** `QueueEntities.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per queue entry. [position] is play order; [originalPosition] is insertion order. */
@Entity(tableName = "queue_item")
data class QueueItemEntity(
    @PrimaryKey val queueId: Long,
    val position: Int,
    val originalPosition: Int,
    val trackId: String,
    val title: String,
    /** Artists joined with U+001F (unit separator). */
    val artists: String,
    val durationMs: Long?,
    val thumbnailUrl: String?,
)

/** Single row, id always 0. */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    val shuffle: Boolean,
    val repeat: String,
    val nextQueueId: Long,
    val currentIndex: Int,
    val positionMs: Long,
)
```

`QueueDao.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.*
import app.aria.data.entities.QueueItemEntity
import app.aria.data.entities.QueueStateEntity

@Dao
abstract class QueueDao {
    @Query("DELETE FROM queue_item") protected abstract suspend fun clearItems()
    @Insert protected abstract suspend fun insertItems(items: List<QueueItemEntity>)
    @Upsert protected abstract suspend fun upsertState(state: QueueStateEntity)
    @Query("SELECT * FROM queue_state WHERE id = 0") abstract suspend fun state(): QueueStateEntity?
    @Query("SELECT * FROM queue_item ORDER BY position") abstract suspend fun items(): List<QueueItemEntity>
    @Query("UPDATE queue_state SET currentIndex = :index, positionMs = :positionMs WHERE id = 0")
    protected abstract suspend fun updatePosition(index: Int, positionMs: Long): Int

    @Transaction
    open suspend fun replace(items: List<QueueItemEntity>, state: QueueStateEntity) {
        clearItems(); insertItems(items); upsertState(state)
    }

    /** Position may arrive before the first structure save; keep it so it is not lost. */
    @Transaction
    open suspend fun savePosition(index: Int, positionMs: Long, fallback: QueueStateEntity) {
        if (updatePosition(index, positionMs) == 0) upsertState(fallback)
    }
}
```

`QueueStoreImpl.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.data.entities.QueueItemEntity
import app.aria.data.entities.QueueStateEntity
import app.aria.domain.*

class QueueStoreImpl(private val dao: QueueDao) : QueueStore {
    override suspend fun saveStructure(queue: SavedQueue) {
        val originalPos = queue.originalOrder.withIndex().associate { it.value to it.index }
        val rows = queue.items.mapIndexed { i, it ->
            QueueItemEntity(
                queueId = it.queueId, position = i, originalPosition = originalPos[it.queueId] ?: i,
                trackId = it.track.id.raw, title = it.track.title, artists = it.track.artists.joinToString(SEP),
                durationMs = it.track.durationMs, thumbnailUrl = it.track.thumbnailUrl,
            )
        }
        val old = dao.state()
        dao.replace(rows, QueueStateEntity(0, queue.shuffle, queue.repeat.name, queue.nextQueueId, old?.currentIndex ?: 0, old?.positionMs ?: 0L))
    }

    override suspend fun savePosition(position: SavedPosition) {
        dao.savePosition(position.index, position.positionMs, QueueStateEntity(0, false, RepeatMode.OFF.name, 1L, position.index, position.positionMs))
    }

    override suspend fun load(): RestoredQueue? {
        val state = dao.state() ?: return null
        val rows = dao.items()
        if (rows.isEmpty()) return null
        val items = rows.map {
            QueueItem(it.queueId, Track(TrackId.parse(it.trackId), it.title, if (it.artists.isEmpty()) emptyList() else it.artists.split(SEP), it.durationMs, it.thumbnailUrl))
        }
        val original = rows.sortedBy { it.originalPosition }.map { it.queueId }
        return RestoredQueue(
            SavedQueue(items, original, state.shuffle, RepeatMode.valueOf(state.repeat), state.nextQueueId),
            SavedPosition(state.currentIndex, state.positionMs),
        )
    }

    private companion object { const val SEP = "\u001F" }
}
```

`AriaDatabase.kt`: add `QueueItemEntity::class, QueueStateEntity::class` to `entities`, imports, and `abstract fun queueDao(): QueueDao`; update the KDoc table count (25 tables).

- [ ] **Step 4: Get the schema from CI.** Push. Expect the unit tests green but **"Schema is committed and unchanged" red** (the plugin regenerated `1.json`). Add a permanent diagnostic so the file can be fetched without gzip tricks: in `ci.yml` after the schema step add

```yaml
      - name: Upload schema when it changed
        if: failure()
        uses: actions/upload-artifact@<SHA> # <tag>
        with: { name: room-schema, path: data/schemas, retention-days: 3 }
```

Resolve `<SHA>` for a current `actions/upload-artifact` release with `env -u GH_TOKEN gh api repos/actions/upload-artifact/git/ref/tags/<tag> --jq .object.sha` (dereference annotated tags via `git/tags/<sha>`), keep the `# <tag>` comment (`scripts/check-actions-pinned.sh` requires a full SHA). Push, download the artifact (`env -u GH_TOKEN gh run download <run-id> -n room-schema -D /tmp/schema`), copy `app.aria.data.AriaDatabase/1.json` over `data/schemas/app.aria.data.AriaDatabase/1.json`, verify it contains `queue_item` and `queue_state`, commit. If artifact download is refused by the sandbox, fall back to the foundation method (gzip+base64 in annotations via a temporary script file, not inline YAML).
- [ ] **Step 5:** Push; all jobs green. Expected: PASS. **Commit** `feat(data): Room-backed QueueStore`.

---

### Task 7: Media3 adapter and service (replaces the skeleton player)

**Files:**
- Create: `playback/src/main/kotlin/app/aria/playback/{PlaybackEngineProvider,TrustPolicy,PlayerErrorMapper,Media3PlayerOutput,EngineForwardingPlayer}.kt`
- Modify: `playback/src/main/kotlin/app/aria/playback/AriaPlaybackService.kt`, `playback/build.gradle.kts`, `playback/src/main/AndroidManifest.xml` (unchanged unless CI complains)
- Delete: `PlayerController.kt`, `ResumeStore.kt` (both classes `ResumeStore` and `InMemoryResumeStore`), `PlayerControllerTest.kt`, and the `PlaybackHost` object
- Test: `playback/src/test/kotlin/app/aria/playback/TrustPolicyTest.kt`, `PlayerErrorMapperTest.kt`, `playback/src/test/resources/robolectric.properties` (`sdk=35`)

**Interfaces:**
- Consumes: `DefaultPlaybackEngine.attach/detach/streamFor`, `PlayerOutput`, `PlayerListener`, `PlayerError`.
- Produces: `interface PlaybackEngineProvider { val playbackEngine: DefaultPlaybackEngine }` (implemented by the `Application`), `object TrustPolicy { fun isAllowed(...): Boolean }`, `PlayerErrorMapper.map(Throwable): PlayerError`, `class PermanentStreamException`, `AriaPlaybackService.start(context)`.
- **:app is broken between this task and Task 11** (it still imports `PlaybackHost`). Do Tasks 7 and 11 on one branch and push after Task 11, or keep a temporary shim: leave `SkeletonScreen.kt` compiling by deleting it in this task and making `MainActivity` show a plain `Text`. Prefer the shim (CI stays green per task).

**Acceptance Criteria:**
- A same-package controller, the system, the media-notification controller and a trusted controller are accepted; any other package is rejected (`TrustPolicy`).
- `PlayerErrorMapper`: cause chain containing `PermanentStreamException` maps to `Permanent`; `HttpDataSource.InvalidResponseCodeException` to `Http(code)`; anything else to `Io`.
- ExoPlayer is built with audio focus handling, becoming-noisy pause, network wake mode; stream URLs are resolved through `ResolvingDataSource` using `engine.streamFor`.
- Notification/Bluetooth/session play, pause, next, previous and seeks go to the engine (`EngineForwardingPlayer`).
- `PlaybackHost`, `PlayerController`, the 500 ms delay and every `TODO(sub-project 2)` in `:playback` are gone.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustPolicyTest {
    private fun allowed(pkg: String, trusted: Boolean = false, notification: Boolean = false) =
        TrustPolicy.isAllowed(packageName = pkg, ownPackage = "app.aria.music", isTrusted = trusted, isNotificationController = notification)

    @Test fun ownPackageIsAllowed() = assertTrue(allowed("app.aria.music"))
    @Test fun systemTrustedIsAllowed() = assertTrue(allowed("com.android.systemui", trusted = true))
    @Test fun notificationControllerIsAllowed() = assertTrue(allowed("androidx.media3.session", notification = true))
    @Test fun unknownPackageIsRejected() = assertFalse(allowed("com.evil.app"))
    @Test fun lookAlikePackageIsRejected() = assertFalse(allowed("app.aria.music.evil"))
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import app.aria.domain.PlayerError
import app.aria.domain.StreamUnavailableException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class PlayerErrorMapperTest {
    @Test fun permanentInCauseChain() {
        val e = RuntimeException("wrap", IOException(PermanentStreamException(StreamUnavailableException("gone"))))
        assertEquals(PlayerError.Permanent, PlayerErrorMapper.map(e))
    }
    @Test fun httpCodeIsPreserved() {
        val http = HttpDataSource.InvalidResponseCodeException(403, "Forbidden", null, emptyMap(), DataSpec(Uri.EMPTY), ByteArray(0))
        assertEquals(PlayerError.Http(403), PlayerErrorMapper.map(RuntimeException(http)))
    }
    @Test fun everythingElseIsIo() = assertEquals(PlayerError.Io, PlayerErrorMapper.map(IOException("timeout")))
}
```

- [ ] **Step 2:** Push. Expected: FAIL (classes missing).

- [ ] **Step 3: Implement.** `playback/build.gradle.kts`: add `testImplementation(kotlin("test"))` (if not yet), `testImplementation(libs.robolectric)`, `testImplementation(libs.androidx.test.core)`; `implementation(libs.media3.datasource)` is not needed (ExoPlayer brings `media3-datasource`; if the compiler cannot see `ResolvingDataSource`, add the catalog alias `media3-datasource = androidx.media3:media3-datasource` and use it). Add `src/test/resources/robolectric.properties` with `sdk=35`.

```kotlin
// PlaybackEngineProvider.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

/** Implemented by the Application. No DI framework in :playback: the service asks the Application for the engine. */
interface PlaybackEngineProvider { val playbackEngine: DefaultPlaybackEngine }
```

```kotlin
// TrustPolicy.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

/** Who may connect to the media session. Android Auto and other controllers are added in a later sub-project. */
object TrustPolicy {
    fun isAllowed(packageName: String, ownPackage: String, isTrusted: Boolean, isNotificationController: Boolean): Boolean =
        packageName == ownPackage || isTrusted || isNotificationController
}
```

```kotlin
// PlayerErrorMapper.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import androidx.media3.datasource.HttpDataSource
import app.aria.domain.PlayerError
import java.io.IOException

/** The resolver found the stream permanently unavailable. Thrown inside ResolvingDataSource. */
class PermanentStreamException(cause: Throwable) : IOException(cause)

object PlayerErrorMapper {
    fun map(error: Throwable): PlayerError {
        var t: Throwable? = error; var depth = 0
        while (t != null && depth++ < 12) {
            if (t is PermanentStreamException) return PlayerError.Permanent
            if (t is HttpDataSource.InvalidResponseCodeException) return PlayerError.Http(t.responseCode)
            t = t.cause
        }
        return PlayerError.Io
    }
}
```

```kotlin
// Media3PlayerOutput.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.aria.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** ExoPlayer behind the engine's PlayerOutput. Items are lightweight (`aria://track/<id>`); URLs resolve at load time. */
@UnstableApi
class Media3PlayerOutput(context: Context, private val engine: DefaultPlaybackEngine) : PlayerOutput, Player.Listener {
    override var listener: PlayerListener? = null

    val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                ResolvingDataSource.Factory(DefaultDataSource.Factory(context)) { spec -> resolve(spec) },
            ),
        )
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    init { exo.addListener(this) }

    /** Runs on ExoPlayer's loader thread, never on the main thread. */
    private fun resolve(spec: DataSpec): DataSpec {
        if (spec.uri.scheme != SCHEME) return spec
        val raw = spec.uri.lastPathSegment ?: throw IOException("Bad track URI")
        val id = try { TrackId.parse(raw) } catch (e: IllegalArgumentException) { throw PermanentStreamException(e) }
        val stream = try {
            runBlocking { engine.streamFor(id) }.getOrThrow()
        } catch (e: CancellationException) { throw e
        } catch (e: StreamUnavailableException) { throw PermanentStreamException(e)
        } catch (e: Exception) { throw IOException(e) }
        return spec.withUri(stream.url.toUri())
    }

    private fun item(q: QueueItem): MediaItem = MediaItem.Builder()
        .setMediaId(q.queueId.toString())
        .setUri(Uri.Builder().scheme(SCHEME).authority("track").appendPath(q.track.id.raw).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(q.track.title).setArtist(q.track.artists.joinToString())
                .setArtworkUri(q.track.thumbnailUrl?.toUri())
                .setIsPlayable(true).setIsBrowsable(false)
                .build(),
        ).build()

    // ---- PlayerOutput ---------------------------------------------------------------------------------
    override fun setPlaylist(items: List<QueueItem>, startIndex: Int, startPositionMs: Long, playWhenReady: Boolean) {
        exo.setMediaItems(items.map(::item), startIndex, startPositionMs)
        exo.playWhenReady = playWhenReady
        exo.prepare()
    }
    override fun insert(index: Int, items: List<QueueItem>) { exo.addMediaItems(index, items.map(::item)) }
    override fun remove(index: Int) { exo.removeMediaItem(index) }
    override fun move(from: Int, to: Int) { exo.moveMediaItem(from, to) }
    override fun reorder(items: List<QueueItem>, currentIndex: Int) {
        val cur = exo.currentMediaItemIndex
        if (cur + 1 < exo.mediaItemCount) exo.removeMediaItems(cur + 1, exo.mediaItemCount)
        if (cur > 0) exo.removeMediaItems(0, cur)
        exo.addMediaItems(0, items.subList(0, currentIndex).map(::item))
        exo.addMediaItems(exo.mediaItemCount, items.subList(currentIndex + 1, items.size).map(::item))
    }
    override fun clear() { exo.clearMediaItems() }
    override fun seekTo(index: Int, positionMs: Long) { exo.seekTo(index, positionMs) }
    override fun setPlayWhenReady(play: Boolean) { exo.playWhenReady = play }
    override fun retry(positionMs: Long) { exo.seekTo(exo.currentMediaItemIndex, positionMs); exo.prepare() }
    override fun setRepeatOne(on: Boolean) { exo.repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    override fun setSkipSilence(on: Boolean) { exo.skipSilenceEnabled = on }
    override fun setVolume(volume: Float) { exo.volume = volume }
    override fun positionMs(): Long = exo.currentPosition
    override fun durationMs(): Long = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    fun release() { exo.removeListener(this); exo.release() }

    // ---- Player.Listener ------------------------------------------------------------------------------
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            listener?.onItemTransition(exo.currentMediaItemIndex)
        }
    }
    override fun onPlaybackStateChanged(state: Int) {
        when (state) {
            Player.STATE_BUFFERING -> listener?.onBufferingChanged(true)
            Player.STATE_READY -> listener?.onBufferingChanged(false)
            Player.STATE_ENDED -> listener?.onEnded()
        }
    }
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { listener?.onPlayWhenReadyChanged(playWhenReady) }
    override fun onPlayerError(error: PlaybackException) { listener?.onError(PlayerErrorMapper.map(error)) }

    private companion object { const val SCHEME = "aria" }
}
```

```kotlin
// EngineForwardingPlayer.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.aria.domain.PlaybackEngine

/** Routes every transport command from the session, notification and headset buttons to the engine. */
@UnstableApi
class EngineForwardingPlayer(player: Player, private val engine: PlaybackEngine) : ForwardingPlayer(player) {
    override fun play() = engine.play()
    override fun pause() = engine.pause()
    override fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) engine.play() else engine.pause()
    override fun seekToNext() = engine.next()
    override fun seekToNextMediaItem() = engine.next()
    override fun seekToPrevious() = engine.previous()
    override fun seekToPreviousMediaItem() = engine.previous()
    override fun seekTo(positionMs: Long) = engine.seekTo(positionMs)
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex == currentMediaItemIndex) engine.seekTo(positionMs) else engine.skipTo(mediaItemIndex)
    }
}
```

```kotlin
// AriaPlaybackService.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.aria.domain.PlaybackStatus

@UnstableApi
class AriaPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var output: Media3PlayerOutput? = null
    private lateinit var engine: DefaultPlaybackEngine

    override fun onCreate() {
        super.onCreate()
        engine = (application as PlaybackEngineProvider).playbackEngine
        val out = Media3PlayerOutput(this, engine).also { output = it }
        session = MediaSession.Builder(this, EngineForwardingPlayer(out.exo, engine))
            .setCallback(TrustedCallback(packageName))
            .build()
        engine.attach(out)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val status = engine.state.value.status
        if (status != PlaybackStatus.Playing && status != PlaybackStatus.Buffering) stopSelf()
    }

    override fun onDestroy() {
        output?.let { engine.detach(it) }
        session?.release(); session = null
        output?.release(); output = null
        super.onDestroy()
    }

    private class TrustedCallback(private val own: String) : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            if (TrustPolicy.isAllowed(controller.packageName, own, controller.isTrusted, session.isMediaNotificationController(controller))) {
                MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            } else MediaSession.ConnectionResult.reject()
    }

    companion object {
        /** startService, not startForegroundService: Media3 promotes the service itself once playback starts. */
        fun start(context: Context) { context.startService(Intent(context, AriaPlaybackService::class.java)) }
    }
}
```

Delete the old files and add the `:app` shim described under Interfaces (replace `SkeletonScreen` call in `MainActivity` by a `Text`; remove `ResumeStore` injections, `SharedPrefsResumeStore`, and its provider in `AppModule`). Keep `:app` compiling; Task 11 builds the real UI.

- [ ] **Step 4:** Push; fix API-name mismatches reported by CI **(verify: `isTrusted`, `isMediaNotificationController`, `ConnectionResult.reject`, `ForwardingPlayer` overrides, `ResolvingDataSource.Factory` lambda form, `@OptIn(UnstableApi)` placement)**. Expected after fixes: PASS, `:app` assembles both flavors.
- [ ] **Step 5: Commit** `feat(playback): Media3 output and service driven by the engine; remove skeleton host`.

### Task 8: Signed cipher-config handling (updates disabled)

Spec section 4 "Cipher player-config hosting and trust" (stress-test branch 4, ADR-0002). Ported Meld code fetches player configs from another project's GitHub repository; Aria ships a bundled default and only ever accepts signed updates, and the update path stays **off** until production keys exist.

**Files:**
- Create: `core/src/main/kotlin/app/aria/core/cipher/{CipherConfigVerifier,RegexGuard}.kt`
- Create: `innertube/src/main/kotlin/app/aria/innertube/cipher/{CipherConfigStore,FileCipherConfigStore,CipherConfigUpdater,AriaPlayerConfigRepository}.kt`
- Create: `innertube/src/main/resources/aria/player_configs.json` (bundled default)
- Modify: `docs/provenance.csv`, `docs/release-gates.md`
- Test: `core/src/test/kotlin/app/aria/core/cipher/{CipherConfigVerifierTest,RegexGuardTest}.kt`, `innertube/src/test/kotlin/app/aria/innertube/cipher/{CipherConfigUpdaterTest,FileCipherConfigStoreTest}.kt`

**Interfaces:**
- Consumes: Tink `Ed25519Verify` / `Ed25519Sign` (already in `:core`), kotlinx.serialization JSON (already in `:core`), InnerTubeX `PlayerConfigRepository` **(verify: read `cipher/PlayerConfigRepository.kt` at tag v0.5.2; Meld's `AndroidPlayerConfigRepository` exposes `enabled`, `sourceUrl`, `defaultSourceUrl` and a cached JSON, so mirror whatever members the interface declares)**.
- Produces:
  - `class CipherConfigVerifier(publicKeys: List<ByteArray>, allowedTopLevelKeys: Set<String>, maxAgeMs: Long, maxFutureSkewMs: Long = 300_000, lastAcceptedVersion: () -> Long, clock: () -> Long)` with `fun verify(payload: ByteArray, signature: ByteArray): CipherVerifyResult` (`Valid(version: Long, configJson: String)` / `Invalid(reason: String)`) and `companion fun signingInput(payload): ByteArray` (context `aria-cipher-config-v1\n`).
  - `object RegexGuard { fun isSafe(pattern: String): Boolean }`.
  - `interface CipherConfigStore { fun readConfig(): String?; fun lastVersion(): Long; fun write(json: String, version: Long) }`, `class FileCipherConfigStore(dir: File)`.
  - `class CipherConfigUpdater(enabled: Boolean, url: String?, fetch: suspend (String) -> FetchedConfig?, verifier: CipherConfigVerifier, store: CipherConfigStore)` with `suspend fun updateOnce(): UpdateResult` (`Disabled`, `NoUpdate`, `Applied(version)`, `Rejected(reason)`); `data class FetchedConfig(val payload: ByteArray, val signature: ByteArray)`.
  - `class AriaPlayerConfigRepository(store: CipherConfigStore?, defaultJson: () -> String)` implementing InnerTubeX's repository: `enabled = false`, `sourceUrl`/`defaultSourceUrl` empty (so InnerTubeX's remote store never makes a network call), content = verified stored config if present, else the bundled default.

**Acceptance Criteria:**
- Verifier rejects: bad signature, tampered payload, payload over 256 KB, unknown envelope field, config with a top-level key not in `allowedTopLevelKeys`, version not greater than `lastAcceptedVersion()`, older than `maxAgeMs`, dated more than 5 min in the future, JSON deeper than 6 levels, any string over 2048 chars, any value under a key containing `regex` or `pattern` (case-insensitive) that fails `RegexGuard`.
- `RegexGuard` rejects patterns over 512 chars, invalid patterns, nested quantifiers such as `(a+)+$`, and any pattern that exhausts a 200 000-character-read budget on adversarial inputs; accepts ordinary patterns.
- `CipherConfigUpdater` with `enabled = false` (or a blank url) never calls `fetch`.
- No code path performs a network call to a repository other than an Aria-controlled URL; with updates disabled there is no network call at all.
- The bundled default has a provenance row naming its origin and a license that was read (see Step 3.4).

- [ ] **Step 1: Failing tests (core).**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.cipher

import com.google.crypto.tink.subtle.Ed25519Sign
import org.junit.Assert.*
import org.junit.Test

class CipherConfigVerifierTest {
    private val pair = Ed25519Sign.KeyPair.newKeyPair()
    private val other = Ed25519Sign.KeyPair.newKeyPair()
    private var now = 10_000_000L
    private var lastVersion = 4L
    private val keys = setOf("playerConfigs", "version")

    private fun verifier() = CipherConfigVerifier(listOf(pair.publicKey), keys, maxAgeMs = 7 * 86_400_000L, lastAcceptedVersion = { lastVersion }, clock = { now })
    private fun sign(payload: ByteArray, key: ByteArray = pair.privateKey) =
        Ed25519Sign(key).sign(CipherConfigVerifier.signingInput(payload))
    private fun envelope(version: Long = 5, issued: Long = now, config: String = """{"playerConfigs":{"a":{"regex":"[a-z]+"}}}""") =
        """{"version":$version,"issuedAtEpochMs":$issued,"config":$config}""".toByteArray()
    private fun check(payload: ByteArray, sig: ByteArray = sign(payload)) = verifier().verify(payload, sig)
    private fun assertInvalid(r: CipherVerifyResult, part: String) {
        assertTrue("expected Invalid but was $r", r is CipherVerifyResult.Invalid)
        assertTrue((r as CipherVerifyResult.Invalid).reason, r.reason.contains(part, ignoreCase = true))
    }

    @Test fun validEnvelopePasses() {
        val r = check(envelope()); assertTrue(r.toString(), r is CipherVerifyResult.Valid)
        assertEquals(5L, (r as CipherVerifyResult.Valid).version)
    }
    @Test fun wrongKeyRejected() = assertInvalid(check(envelope(), sign(envelope(), other.privateKey)), "signature")
    @Test fun tamperedPayloadRejected() { val p = envelope(); val s = sign(p); assertInvalid(check(envelope(version = 6), s), "signature") }
    @Test fun oversizedRejected() = assertInvalid(check(ByteArray(256 * 1024 + 1)), "larger")
    @Test fun unknownEnvelopeFieldRejected() =
        assertInvalid(check("""{"version":5,"issuedAtEpochMs":$now,"config":{},"extra":1}""".toByteArray()), "malformed")
    @Test fun unknownTopLevelKeyRejected() = assertInvalid(check(envelope(config = """{"playerConfigs":{},"evil":{}}""")), "key")
    @Test fun rollbackRejected() { assertInvalid(check(envelope(version = 4)), "version"); assertInvalid(check(envelope(version = 2)), "version") }
    @Test fun staleRejected() = assertInvalid(check(envelope(issued = now - 8 * 86_400_000L)), "old")
    @Test fun futureRejected() = assertInvalid(check(envelope(issued = now + 600_000L)), "future")
    @Test fun deepJsonRejected() = assertInvalid(check(envelope(config = """{"playerConfigs":{"a":{"b":{"c":{"d":{"e":{"f":{}}}}}}}}""")), "deep")
    @Test fun longStringRejected() = assertInvalid(check(envelope(config = """{"playerConfigs":{"a":"${"x".repeat(2049)}"}}""")), "long")
    @Test fun unsafeRegexRejected() = assertInvalid(check(envelope(config = """{"playerConfigs":{"a":{"regex":"(a+)+$"}}}""")), "regex")
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.cipher

import org.junit.Assert.*
import org.junit.Test

class RegexGuardTest {
    @Test fun ordinaryPatternsPass() {
        assertTrue(RegexGuard.isSafe("[a-zA-Z0-9_$]{1,8}"))
        assertTrue(RegexGuard.isSafe("""function\s*\(a\)\{[^}]*\}"""))
    }
    @Test fun nestedQuantifiersRejected() {
        assertFalse(RegexGuard.isSafe("(a+)+$")); assertFalse(RegexGuard.isSafe("(.*)*x")); assertFalse(RegexGuard.isSafe("(a|aa)+$"))
    }
    @Test fun invalidAndHugeRejected() { assertFalse(RegexGuard.isSafe("([")); assertFalse(RegexGuard.isSafe("a".repeat(513))) }
}
```

Updater and store tests (innertube, JVM, no Android):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.cipher

import app.aria.core.cipher.CipherConfigVerifier
import com.google.crypto.tink.subtle.Ed25519Sign
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CipherConfigUpdaterTest {
    @get:Rule val tmp = TemporaryFolder()
    private val pair = Ed25519Sign.KeyPair.newKeyPair()
    private val now = 5_000_000L
    private fun store() = FileCipherConfigStore(tmp.newFolder())
    private fun verifier(store: CipherConfigStore) =
        CipherConfigVerifier(listOf(pair.publicKey), setOf("playerConfigs"), 86_400_000L, lastAcceptedVersion = store::lastVersion, clock = { now })
    private fun fetched(version: Long, key: ByteArray = pair.privateKey): FetchedConfig {
        val p = """{"version":$version,"issuedAtEpochMs":$now,"config":{"playerConfigs":{}}}""".toByteArray()
        return FetchedConfig(p, Ed25519Sign(key).sign(CipherConfigVerifier.signingInput(p)))
    }

    @Test fun disabledNeverFetches() = runBlocking {
        var calls = 0; val s = store()
        val u = CipherConfigUpdater(false, "https://example.invalid/c", { calls++; fetched(1) }, verifier(s), s)
        assertEquals(UpdateResult.Disabled, u.updateOnce()); assertEquals(0, calls)
    }
    @Test fun blankUrlNeverFetches() = runBlocking {
        var calls = 0; val s = store()
        assertEquals(UpdateResult.Disabled, CipherConfigUpdater(true, "", { calls++; null }, verifier(s), s).updateOnce()); assertEquals(0, calls)
    }
    @Test fun validSignedConfigIsStored() = runBlocking {
        val s = store()
        val r = CipherConfigUpdater(true, "https://example.invalid/c", { fetched(7) }, verifier(s), s).updateOnce()
        assertEquals(UpdateResult.Applied(7), r); assertEquals(7L, s.lastVersion()); assertNotNull(s.readConfig())
    }
    @Test fun badSignatureLeavesStoreUntouched() = runBlocking {
        val s = store(); val other = Ed25519Sign.KeyPair.newKeyPair()
        val r = CipherConfigUpdater(true, "https://example.invalid/c", { fetched(7, other.privateKey) }, verifier(s), s).updateOnce()
        assertTrue(r is UpdateResult.Rejected); assertEquals(0L, s.lastVersion()); assertNull(s.readConfig())
    }
    @Test fun fetchFailureIsNoUpdate() = runBlocking {
        val s = store()
        assertEquals(UpdateResult.NoUpdate, CipherConfigUpdater(true, "https://example.invalid/c", { null }, verifier(s), s).updateOnce())
    }
}
```

`FileCipherConfigStoreTest`: write `{"a":1}` v3, read it back, `lastVersion() == 3`, a new instance on the same dir sees the same values, `lastVersion()` is 0 when empty.

- [ ] **Step 2:** Push. Expected: FAIL (classes missing).

- [ ] **Step 3: Implement.**

3.1 `RegexGuard.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.cipher

/** Bounds regexes that arrive in signed-but-remote data. Heuristics plus a work budget: a pattern that cannot finish on adversarial input is unsafe. */
object RegexGuard {
    private const val MAX_LENGTH = 512
    private const val BUDGET = 200_000
    private val NESTED_QUANTIFIER = Regex("""[+*}]\)\s*[+*{]""")
    private val QUANTIFIED_ALTERNATION = Regex("""\([^()]*\|[^()]*\)[+*{]""")
    private val ADVERSARIAL = listOf("a".repeat(48) + "!", "ab".repeat(32) + "!", " ".repeat(48) + "x", "0".repeat(64) + "?")

    fun isSafe(pattern: String): Boolean {
        if (pattern.length > MAX_LENGTH) return false
        val regex = try { Regex(pattern) } catch (_: Exception) { return false }
        if (NESTED_QUANTIFIER.containsMatchIn(pattern) || QUANTIFIED_ALTERNATION.containsMatchIn(pattern)) return false
        return ADVERSARIAL.all { input ->
            try { regex.containsMatchIn(Budgeted(input, BUDGET)); true } catch (_: BudgetExceeded) { false }
        }
    }

    private class BudgetExceeded : RuntimeException()

    private class Budgeted(private val base: String, private var left: Int) : CharSequence {
        override val length get() = base.length
        override fun get(index: Int): Char { if (--left < 0) throw BudgetExceeded(); return base[index] }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = Budgeted(base.substring(startIndex, endIndex), left)
        override fun toString() = base
    }
}
```

3.2 `CipherConfigVerifier.kt`: follow `RegistryVerifier` (same package style, `Ed25519Verify`, `GeneralSecurityException`). Envelope:

```kotlin
@Serializable private data class Envelope(val version: Long, val issuedAtEpochMs: Long, val config: JsonObject)
```
decode with `Json { ignoreUnknownKeys = false }`; order of checks: size, signature, parse (reason starts `malformed`), version (`version must be greater than N`), age (`config is N ms old`), future (`dated N ms in the future`), top-level keys (`unexpected key: x`), then a recursive walk (`depth > 6` → "json too deep"; string over 2048 → "string too long"; value under key matching `regex|pattern` that is a string failing `RegexGuard` → "unsafe regex at key"). Reason texts must contain the words the tests check (`signature`, `larger`, `malformed`, `key`, `version`, `old`, `future`, `deep`, `long`, `regex`). `Valid(version, configJson = envelope.config.toString())`. Never put secrets in the config: it is signed, not encrypted, and public.

3.3 innertube classes: `FileCipherConfigStore(dir)` writes `config.json` and `version.txt` via a temp file and atomic rename; `readConfig()` returns null if absent; `lastVersion()` 0 if absent or unreadable. `CipherConfigUpdater.updateOnce()`: `if (!enabled || url.isNullOrBlank()) return Disabled`; `val f = runCatching { fetch(url) }.getOrNull() ?: return NoUpdate`; `when (val r = verifier.verify(f.payload, f.signature)) { is Valid -> { store.write(r.configJson, r.version); Applied(r.version) } is Invalid -> Rejected(r.reason) }`. `AriaPlayerConfigRepository`: implement the InnerTubeX interface found in Step 3.0 below; its JSON is `store?.readConfig() ?: defaultJson()`. The sample `defaultJson` loader used by the app is `{ AriaPlayerConfigRepository::class.java.getResourceAsStream("/aria/player_configs.json")!!.bufferedReader().use { it.readText() } }`.

3.4 **Bundled default and license (blocking pre-step; do first).** (a) Check whether InnerTubeX v0.5.2 already embeds a default config (look for `withEmbeddedConfigFallback`, an `assets`/`resources` JSON, or a default in `PlayerConfigRepository`); if it does, use that and skip (b). (b) Otherwise read the license of `ZemerTeam/zemer-cipher` (GitHub repository page and LICENSE). If it is GPL-3.0-compatible (MIT, Apache-2.0, GPL-3.0, BSD), copy `library/src/main/assets/player_configs.json` from the pinned commit into `innertube/src/main/resources/aria/player_configs.json`. If it has no license or an incompatible one, **stop and report BLOCKED**; the controller will ask the maintainer. Add the provenance row (`path,origin_repo,origin_commit,origin_license,kind,notes`) for `innertube/src/main/resources/aria` with the origin commit SHA and the license text actually read. Derive `allowedTopLevelKeys` for production use from this file's top-level keys and record them in the test that loads the bundled default (`BundledConfigTest`: parses as JSON; top-level keys are exactly the constant `AriaCipherConfig.ALLOWED_TOP_LEVEL_KEYS`).

3.5 `docs/release-gates.md`, "Before the first public build": add `- [ ] Cipher config: generate production Ed25519 key(s), host the signed config at an Aria-controlled URL, embed the public key(s), then enable \`CipherConfigUpdater\` (disabled in the alpha; extractor hot-fixes between app releases need this).`

- [ ] **Step 4:** Push; `:core:test`, `:innertube:testDebugUnitTest`, provenance and license checks green. Expected: PASS.
- [ ] **Step 5: Commit** `feat(innertube,core): signed cipher config with updates disabled`.

---

### Task 9: Extractor adapter, failed-client memory and radio source

**Files:**
- Create: `innertube/src/main/kotlin/app/aria/innertube/extraction/{PoTokenSource,ExtractedAudio,FailedClientMemory,AudioExtractor,ExtractorSession,InnerTubeXAudioExtractor}.kt`, `innertube/src/main/kotlin/app/aria/innertube/adapter/YouTubeRadioSource.kt`
- Modify: `innertube/src/main/kotlin/app/aria/innertube/adapter/YouTubeStreamSource.kt`, `innertube/src/test/kotlin/app/aria/innertube/smoke/PlaybackSmokeTest.kt`, `app/src/main/kotlin/app/aria/music/di/AppModule.kt` (constructor change only), `docs/provenance.csv`
- Test: `innertube/src/test/kotlin/app/aria/innertube/extraction/{FailedClientMemoryTest,ExtractedAudioTest}.kt`, `innertube/src/test/kotlin/app/aria/innertube/adapter/{YouTubeStreamSourceTest,YouTubeRadioSourceTest}.kt`

**Interfaces:**
- Consumes: Task 8 `AriaPlayerConfigRepository`, `StreamSource`, `RadioSource`, `YouTube.next`, `WatchEndpoint`, `NextResult`, `SongItem.toTrack()`.
- Produces:
  - `data class PoTokens(val playerRequestToken: String, val streamingDataToken: String, val visitorData: String)`; `interface PoTokenSource { suspend fun webClientPoToken(videoId: String, visitorData: String): PoTokens; fun close() }`.
  - `data class ExtractedAudio(val url: String, val mimeType: String, val codecs: String?, val bitrate: Int, val contentLength: Long?, val expiresAtEpochMs: Long?, val loudnessDb: Double?, val client: String)` with `fun toResolvedStream(): ResolvedStream`.
  - `class FailedClientMemory(ttlMs: Long = 300_000, clock: () -> Long)` with `record(videoId, client)`, `failed(videoId): Set<String>`.
  - `interface AudioExtractor { suspend fun extract(videoId: String, excludeClients: Set<String>): ExtractedAudio }` (throws `StreamUnavailableException` for permanent failures, any other exception for transient ones).
  - `class YouTubeStreamSource(extractor: AudioExtractor, memory: FailedClientMemory, clock: () -> Long)`; `companion fun create(session: ExtractorSession): YouTubeStreamSource`.
  - `class ExtractorSession(poTokens: PoTokenSource?, configRepository: PlayerConfigRepository)` : `AudioExtractor` wrapper factory (`fun audioExtractor(): AudioExtractor`), `companion fun createDefault(poTokens: PoTokenSource?): ExtractorSession`.
  - `class YouTubeRadioSource(next: suspend (WatchEndpoint) -> Result<NextResult> = { YouTube.next(it) }) : RadioSource`.

**Acceptance Criteria:**
- `resolve` returns `ResolvedStream` with `loudnessDb`, an absolute `expiresAtEpochMs` (extractor value, else `now + 5 min`), `mimeType` with codecs when present.
- A rejected stream (`markRejected`) makes the next `resolve` for that video exclude the client that served it; memory entries expire after the TTL.
- Permanent extractor failures surface as `StreamUnavailableException`; others as ordinary failures (so the engine retries them as transient).
- `YouTubeRadioSource` returns at most `limit` tracks, never the seed, from `next(WatchEndpoint(videoId, playlistId = "RDAMVM<id>"))`, and fails (not throws) for non-YouTube seeds.
- The Task 1 skeleton resolver (ANDROID_MUSIC client only, no cipher, no PoToken) is gone. Without a PoToken source, extraction still works for clients that do not need one.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.extraction

import org.junit.Assert.assertEquals
import org.junit.Test

class FailedClientMemoryTest {
    private var now = 0L
    private val m = FailedClientMemory(ttlMs = 300_000, clock = { now })
    @Test fun remembersPerVideo() { m.record("v1", "ANDROID_VR"); assertEquals(setOf("ANDROID_VR"), m.failed("v1")); assertEquals(emptySet<String>(), m.failed("v2")) }
    @Test fun entriesExpire() { m.record("v1", "IOS"); now = 299_999; assertEquals(setOf("IOS"), m.failed("v1")); now = 300_001; assertEquals(emptySet<String>(), m.failed("v1")) }
    @Test fun accumulatesClients() { m.record("v1", "A"); m.record("v1", "B"); assertEquals(setOf("A", "B"), m.failed("v1")) }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.extraction

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtractedAudioTest {
    private fun audio(expires: Long? = null, codecs: String? = "opus", loud: Double? = 3.5) =
        ExtractedAudio("https://x", "audio/webm", codecs, 160_000, 1000L, expires, loud, "ANDROID_VR")
    @Test fun mapsFields() {
        val r = audio(expires = 99L).toResolvedStream()
        assertEquals("audio/webm; codecs=\"opus\"", r.mimeType); assertEquals(3.5, r.loudnessDb); assertEquals(99L, r.expiresAtEpochMs); assertEquals(160_000, r.bitrate)
    }
    @Test fun mimeWithoutCodecs() = assertEquals("audio/webm", audio(codecs = null).toResolvedStream().mimeType)
    @Test fun missingLoudnessStaysNull() = assertEquals(null, audio(loud = null).toResolvedStream().loudnessDb)
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.StreamUnavailableException
import app.aria.domain.TrackId
import app.aria.innertube.extraction.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class YouTubeStreamSourceTest {
    private class FakeExtractor : AudioExtractor {
        val excluded = mutableListOf<Set<String>>(); var failure: Throwable? = null; var client = "ANDROID_VR"; var expires: Long? = null
        override suspend fun extract(videoId: String, excludeClients: Set<String>): ExtractedAudio {
            excluded += excludeClients; failure?.let { throw it }
            return ExtractedAudio("https://x/$videoId", "audio/webm", "opus", 128_000, 10L, expires, 2.0, client)
        }
    }
    private var now = 1_000L
    private val id = TrackId.youtube("dQw4w9WgXcQ")
    private val extractor = FakeExtractor()
    private val source = YouTubeStreamSource(extractor, FailedClientMemory(clock = { now }), { now })

    @Test fun defaultExpiryIsFiveMinutesWhenExtractorHasNone() = runBlocking {
        assertEquals(1_000L + 300_000L, source.resolve(id).getOrThrow().expiresAtEpochMs)
    }
    @Test fun extractorExpiryWins() = runBlocking { extractor.expires = 77L; assertEquals(77L, source.resolve(id).getOrThrow().expiresAtEpochMs) }
    @Test fun rejectedClientIsExcludedNextTime() = runBlocking {
        source.resolve(id); source.markRejected(id); extractor.client = "IOS"; source.resolve(id)
        assertEquals(emptySet<String>(), extractor.excluded[0]); assertEquals(setOf("ANDROID_VR"), extractor.excluded[1])
    }
    @Test fun permanentFailureKeepsItsType() = runBlocking {
        extractor.failure = StreamUnavailableException("private video")
        assertTrue(source.resolve(id).exceptionOrNull() is StreamUnavailableException)
    }
    @Test fun otherFailuresStayOrdinary() = runBlocking {
        extractor.failure = java.io.IOException("timeout")
        val e = source.resolve(id).exceptionOrNull(); assertTrue(e is java.io.IOException)
    }
    @Test fun nonYouTubeIdsAreRejected() = runBlocking {
        assertTrue(source.resolve(TrackId.parse("local:abc")).isFailure)
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.Track
import app.aria.domain.TrackId
import app.aria.innertube.models.Artist
import app.aria.innertube.models.SongItem
import app.aria.innertube.models.WatchEndpoint
import app.aria.innertube.pages.NextResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class YouTubeRadioSourceTest {
    private fun song(id: String) = SongItem(id = id, title = id, artists = listOf(Artist("A", null)), thumbnail = "t")
    private val seed = Track(TrackId.youtube("aaaaaaaaaaa"), "Seed", listOf("A"), null, null)

    @Test fun excludesSeedAndRespectsLimit() = runBlocking {
        var asked: WatchEndpoint? = null
        val src = YouTubeRadioSource { ep -> asked = ep; Result.success(NextResult(items = listOf(song("aaaaaaaaaaa"), song("bbbbbbbbbbb"), song("ccccccccccc"), song("ddddddddddd")), continuation = null, endpoint = ep)) }
        val r = src.related(seed, 2).getOrThrow()
        assertEquals(listOf("bbbbbbbbbbb", "ccccccccccc"), r.map { it.id.raw })
        assertEquals("RDAMVMaaaaaaaaaaa", asked?.playlistId); assertEquals("aaaaaaaaaaa", asked?.videoId)
    }
    @Test fun failuresAreReturned() = runBlocking {
        assertTrue(YouTubeRadioSource { Result.failure(RuntimeException("x")) }.related(seed, 5).isFailure)
    }
    @Test fun nonYouTubeSeedFails() = runBlocking {
        assertTrue(YouTubeRadioSource { error("unused") }.related(seed.copy(id = TrackId.parse("local:x")), 5).isFailure)
    }
}
```

- [ ] **Step 2:** Push. Expected: FAIL.

- [ ] **Step 3: Implement.**

3.1 Pure pieces:

```kotlin
// FailedClientMemory.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.extraction

class FailedClientMemory(private val ttlMs: Long = 300_000L, private val clock: () -> Long) {
    private val failures = HashMap<String, MutableMap<String, Long>>()
    @Synchronized fun record(videoId: String, client: String) { failures.getOrPut(videoId) { HashMap() }[client] = clock() }
    @Synchronized fun failed(videoId: String): Set<String> {
        val now = clock(); val m = failures[videoId] ?: return emptySet()
        m.entries.removeAll { now - it.value > ttlMs }
        if (m.isEmpty()) failures.remove(videoId)
        return m.keys.toSet()
    }
}
```

```kotlin
// ExtractedAudio.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.extraction

import app.aria.domain.ResolvedStream

data class ExtractedAudio(
    val url: String, val mimeType: String, val codecs: String?, val bitrate: Int, val contentLength: Long?,
    val expiresAtEpochMs: Long?, val loudnessDb: Double?, val client: String,
) {
    fun toResolvedStream(): ResolvedStream = ResolvedStream(
        url = url, mimeType = if (codecs.isNullOrBlank()) mimeType else "$mimeType; codecs=\"$codecs\"",
        bitrate = bitrate, contentLength = contentLength, expiresAtEpochMs = expiresAtEpochMs, loudnessDb = loudnessDb,
    )
}
```

`YouTubeStreamSource`:

```kotlin
class YouTubeStreamSource(
    private val extractor: AudioExtractor,
    private val memory: FailedClientMemory,
    private val clock: () -> Long = System::currentTimeMillis,
) : StreamSource {
    private val lastClient = java.util.concurrent.ConcurrentHashMap<TrackId, String>()

    override suspend fun resolve(id: TrackId): Result<ResolvedStream> = runCatching {
        require(id.source == TrackSource.YOUTUBE) { "Not a YouTube track: $id" }
        val audio = extractor.extract(id.value, memory.failed(id.value))
        lastClient[id] = audio.client
        val stream = audio.toResolvedStream()
        stream.copy(expiresAtEpochMs = stream.expiresAtEpochMs ?: (clock() + DEFAULT_TTL_MS))
    }

    override fun markRejected(id: TrackId) { lastClient[id]?.let { memory.record(id.value, it) } }

    companion object {
        const val DEFAULT_TTL_MS = 300_000L
        fun create(session: ExtractorSession) = YouTubeStreamSource(session.audioExtractor(), FailedClientMemory(clock = System::currentTimeMillis))
    }
}
```

`YouTubeRadioSource`:

```kotlin
class YouTubeRadioSource(
    private val next: suspend (WatchEndpoint) -> Result<NextResult> = { YouTube.next(it) },
) : RadioSource {
    override suspend fun related(seed: Track, limit: Int): Result<List<Track>> = runCatching {
        require(seed.id.source == TrackSource.YOUTUBE) { "Radio needs a YouTube seed" }
        val id = seed.id.value
        next(WatchEndpoint(videoId = id, playlistId = "RDAMVM$id")).getOrThrow()
            .items.filter { it.id != id }.take(limit).map { it.toTrack() }
    }
}
```

3.2 **Extractor wiring (needs the library source; do this after 3.1 is green).** Read, via WebFetch (raw GitHub is blocked in the shell): Meld `app/src/main/kotlin/com/metrolist/music/utils/InnerTubeXPlayer.kt` at commit `fe9a666227ff3d6667075e65a4330518a724524b`, and InnerTubeX v0.5.2 (`MetrolistGroup/innertubex`) classes `extraction/InnerTubeExtractor`, `ExtractedStream`, `StreamResolveException`, `TokenProvider`, `PoTokenResult`, `cipher/YouTubeCipherService`, `cipher/RemotePlayerConfigStore`, `PlayerConfigRepository`. Port **only** the bundle-building and `extract` logic into `ExtractorSession` and `InnerTubeXAudioExtractor` (everything below is what Meld's file does; names marked **(verify)**):
- A cached "bundle" rebuilt when `YouTube.extractionTransport().generation` changes (under a `Mutex`; dispose the old cipher service): `RemotePlayerConfigStore(httpClient, configRepository, logger)` → `YouTubeCipherService(httpClient, remoteStore, logger)`; `YtConfigParserImpl(httpClient, innerTube, remoteStore, logger).withEmbeddedConfigFallback()`; `InnerTubeExtractor(configParser, cipherService, innerTube, tokenProvider, logger)`.
- `configRepository` is the Task 8 `AriaPlayerConfigRepository` (never Meld's `AndroidPlayerConfigRepository`; no ZemerTeam URL may appear anywhere in Aria's tree: add `scripts/` grep in Task 13).
- `tokenProvider`: anonymous `TokenProvider` with `capabilities = TokenProviderCapabilities(providers = setOf(PoTokenProviderKind.WEB_BOTGUARD), usesWebView = true)` and `getPoToken(videoId, visitorData, cookie)` delegating to `poTokens?.webClientPoToken(...)` mapped to `PoTokenResult(playerRequestToken, streamingDataToken, visitorData)`; when `poTokens == null` return a provider without WebView capability **(verify how the extractor treats a missing token provider; if it requires one, return a provider that throws a clear exception and let the extractor fall back to clients that need no token)**.
- `extract`: call `extractor.extract(...)` with HLS and SABR disabled, `excludeClients`, audio quality HIGH, a fresh `generateClientPlaybackNonce()`, and `allowBoundedRange` as Meld does; reject a SABR bootstrap result; rethrow `CancellationException`; for `StreamResolveException` whose kind is NETWORK rethrow its cause (transient), for other kinds throw `StreamUnavailableException(message)` (permanent). Map `ExtractedStream` to `ExtractedAudio` using `audioUrl`, `mimeType`, `codecs`, `bitrate ?: 0`, `contentLengthBytes`, `expiresAt` (epoch ms), `loudnessDb ?: perceptualLoudnessDb`, `clientName`.
- Drop from the port: Meld's `AudioQuality` constants, `PlaybackData`, playlist and uploaded-content handling, connectivity-based quality, the Android `Context` requirement (Aria's repository needs none).
- `Mutex`, transport generation caching, `markStreamClientFailed` TTL map: **not** ported; `FailedClientMemory` replaces the TTL map.
- Add the provenance row for `innertube/src/main/kotlin/app/aria/innertube/extraction` (origin Meld commit above, license read from the file header or Meld's LICENSE, kind `adapted`).

3.3 Delete the old `YouTubeStreamSource` body, `bestAudio()` and its two `MappingTest` cases (`bestAudioPicksHighestBitrateWithUrl`, `bestAudioIsNullWhenNoUrls`) once nothing uses them. Update `AppModule.streams()` to `YouTubeStreamSource.create(ExtractorSession.createDefault(poTokens = null))` as a temporary wiring; Task 11 replaces it. Update `PlaybackSmokeTest` to build the source the same way (no PoToken in a JVM test).

- [ ] **Step 4:** Push; fix compile errors against the real InnerTubeX API. Expected: PASS for all unit tests; the smoke test stays skipped in normal CI.
- [ ] **Step 5: Commit** `feat(innertube): extractor-based stream source, failed-client memory, radio source`.

---

### Task 10: WebView PoToken generator (port, confined)

Spec section 4 "WebView confinement" (branch 3, security, ADR-0002).

**Files:**
- Create: `innertube/src/main/kotlin/app/aria/innertube/potoken/{PoTokenWebView,PoTokenGenerator,JavaScriptUtil,PoTokenResult,PoTokenException,WebViewPoTokenSource,PoTokenValidator,NavigationPolicy}.kt`
- Create: `scripts/check-webview-safety.sh`, `scripts/test_check_webview_safety.py`
- Modify: `.github/workflows/ci.yml` (add the script to "Static checks"), `docs/provenance.csv`, `docs/data-flow.md`, `docs/providers/youtube-music.md`, `app/proguard-rules.pro`
- Test: `innertube/src/test/kotlin/app/aria/innertube/potoken/{PoTokenValidatorTest,NavigationPolicyTest}.kt`, `scripts/test_check_webview_safety.py`

**Interfaces:**
- Consumes: Task 9 `PoTokenSource`, `PoTokens`.
- Produces: `class WebViewPoTokenSource(context: Context) : PoTokenSource`; `object PoTokenValidator { fun isValid(token: String): Boolean }`; `object NavigationPolicy { fun isAllowed(url: String): Boolean }`.

**Acceptance Criteria:**
- Ported from Meld's `potoken/` package; upstream license read and recorded; ledger row present.
- The WebView is never attached to a window; `allowFileAccess`, `allowContentAccess`, geolocation, and `allowFileAccessFromFileURLs`/`allowUniversalAccessFromFileURLs` are off; cookies are not shared with other sessions (cookie manager untouched, `setAcceptThirdPartyCookies(false)`); navigation and subresource requests go through `NavigationPolicy` and anything else is cancelled or answered with an empty response.
- Exactly one JavaScript-bridge object with one `@JavascriptInterface` method that receives only a string; every token passes `PoTokenValidator` (`[A-Za-z0-9_\-+/=]{20,4096}`) before use.
- The WebView is destroyed after each batch (token pair cached in the generator, not the WebView).
- `scripts/check-webview-safety.sh` fails the build if `addJavascriptInterface`, `WebView(` construction or `setJavaScriptEnabled(true)`/`javaScriptEnabled = true` appears outside `innertube/src/main/kotlin/app/aria/innertube/potoken/PoTokenWebView.kt`, or if `allowFileAccess = true`/`setAllowFileAccess(true)` or `loadUrl("file:` appears anywhere.
- `docs/data-flow.md` states that BotGuard sends visitor data and a challenge request to Google and involves no Aria server.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.potoken

import org.junit.Assert.*
import org.junit.Test

class PoTokenValidatorTest {
    @Test fun acceptsBase64UrlTokens() = assertTrue(PoTokenValidator.isValid("MnQ3_abc-DEF0123456789=="))
    @Test fun rejectsShortLongAndOddCharacters() {
        assertFalse(PoTokenValidator.isValid("short")); assertFalse(PoTokenValidator.isValid("a".repeat(4097)))
        assertFalse(PoTokenValidator.isValid("abc def ghi jkl mno pqr")); assertFalse(PoTokenValidator.isValid("<script>alert(1)</script>"))
    }
}

class NavigationPolicyTest {
    @Test fun allowsOnlyHttpsOnAllowedHosts() {
        assertTrue(NavigationPolicy.isAllowed("https://www.youtube.com/api/jnn/v1/Create"))
        assertFalse(NavigationPolicy.isAllowed("http://www.youtube.com/"))
        assertFalse(NavigationPolicy.isAllowed("https://evil.example/"))
        assertFalse(NavigationPolicy.isAllowed("https://www.youtube.com.evil.example/"))
        assertFalse(NavigationPolicy.isAllowed("file:///data/data/app.aria.music/databases/aria.db"))
        assertFalse(NavigationPolicy.isAllowed("javascript:alert(1)"))
    }
}
```

`scripts/test_check_webview_safety.py` (pytest): builds a temp tree with `innertube/src/main/kotlin/app/aria/innertube/potoken/PoTokenWebView.kt` containing `addJavascriptInterface(` and runs the script (exit 0); then adds `app/src/Bad.kt` with `webView.addJavascriptInterface(` (exit 1 and the file name in the output); `allowFileAccess = true` in any file (exit 1); `loadUrl("file:///x")` (exit 1); a clean tree (exit 0). The script takes the repo root as `$1` (default `.`).

- [ ] **Step 2:** Push. Expected: FAIL.

- [ ] **Step 3: Implement.**

3.1 `scripts/check-webview-safety.sh`:

```bash
#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# WebView confinement (ADR-0002): the only WebView, JS bridge and JS enablement live in PoTokenWebView.kt.
set -uo pipefail
root="${1:-.}"
allowed="innertube/src/main/kotlin/app/aria/innertube/potoken/PoTokenWebView.kt"
fail=0
files() { find "$root" -name '*.kt' -not -path '*/build/*' -not -path '*/.git/*' -not -path '*/.internal/*'; }
check_confined() { # pattern, label
  while IFS= read -r f; do
    rel="${f#"$root"/}"
    if [ "$rel" != "$allowed" ] && grep -nE "$1" "$f" >/dev/null; then echo "FAIL: $2 outside $allowed: $rel"; fail=1; fi
  done < <(files)
}
check_forbidden() {
  while IFS= read -r f; do
    if grep -nE "$1" "$f" >/dev/null; then echo "FAIL: $2: ${f#"$root"/}"; fail=1; fi
  done < <(files)
}
check_confined 'addJavascriptInterface\(' 'addJavascriptInterface'
check_confined '[^A-Za-z]WebView\(' 'WebView construction'
check_confined 'setJavaScriptEnabled\(true\)|javaScriptEnabled *= *true' 'JavaScript enablement'
check_forbidden 'setAllowFileAccess\(true\)|allowFileAccess *= *true' 'file access enabled'
check_forbidden 'loadUrl\("file:' 'file: URL loaded'
exit $fail
```

Make it executable (`chmod +x`), add `scripts/check-webview-safety.sh` to the "Static checks" step in `ci.yml`.

3.2 **Port.** Read the license and the five files `PoTokenGenerator.kt`, `PoTokenWebView.kt`, `JavaScriptUtil.kt`, `PoTokenResult.kt`, `PoTokenException.kt` under `app/src/main/kotlin/com/metrolist/music/utils/potoken/` of `FrancescoGrazioso/Meld` at commit `fe9a666227ff3d6667075e65a4330518a724524b` via WebFetch (if a file is truncated, fetch the GitHub "raw" or "blame" view in pieces; do not retype from memory). Package `app.aria.innertube.potoken`; keep upstream headers, add SPDX; replace Meld-specific imports (`NewPipeExtractor`/`Timber` use is fine if the dependency exists; otherwise use `timber`, which `:innertube` already has); add the provenance row for the directory (`innertube/src/main/kotlin/app/aria/innertube/potoken`).

3.3 **Hardening edits in `PoTokenWebView.kt`** (each is a checked acceptance item):
- `WebView(context)` is created on the main thread, never added to a view hierarchy.
- `settings.javaScriptEnabled = true` (needed by BotGuard), `allowFileAccess = false`, `allowContentAccess = false`, `allowFileAccessFromFileURLs = false`, `allowUniversalAccessFromFileURLs = false`, `setGeolocationEnabled(false)`, `domStorageEnabled` only if the ported JS needs it, `mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW`, `blockNetworkLoads = false`; `CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)`.
- `WebViewClient`: `shouldOverrideUrlLoading` returns `!NavigationPolicy.isAllowed(url)`; `shouldInterceptRequest` returns an empty `WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))` for disallowed URLs; `onReceivedSslError` always `handler.cancel()`.
- One bridge: `class Bridge { @JavascriptInterface fun onResult(value: String) }` registered under a fixed name; the ported JS posts the token string to it; Kotlin validates with `PoTokenValidator` before use. No app object is exposed.
- `destroy()`: `webView.stopLoading(); webView.loadUrl("about:blank"); webView.removeJavascriptInterface(NAME); webView.destroy()` after each batch.
- `NavigationPolicy.isAllowed(url)`: parse with `java.net.URI`; allowed iff scheme `https` and host in `setOf("www.youtube.com")`; extend the set **only** with hosts the ported JavaScript is observed to need, one line of justification per host in a comment. `about:blank` is allowed.
- `PoTokenValidator`: `Regex("^[A-Za-z0-9_\\-+/=]{20,4096}$")`.
- `PoTokenGenerator`: keeps the last token pair and its expiry (as Meld does) but **not** the WebView; each cache miss creates a WebView, generates the player-request and streaming tokens, destroys the WebView in `finally`.

3.4 `WebViewPoTokenSource(context)` implements `PoTokenSource` using the generator (`webClientPoToken(videoId, visitorData)` returns `PoTokens`); `close()` destroys any WebView.

3.5 Docs and R8: `docs/data-flow.md` row (YouTube BotGuard challenge: sent = visitor data and a challenge request; to = Google; when = first playback and when the token expires; no Aria server); `docs/providers/youtube-music.md`: replace "Known gap: stream resolution may require a PoToken…" with the generator description and the "stays open until the maintainer confirms playback on a device" note; `app/proguard-rules.pro`: `-keepclassmembers class app.aria.innertube.potoken.PoTokenWebView$Bridge { @android.webkit.JavascriptInterface <methods>; }`.

- [ ] **Step 4:** Push; `scripts` pytest, unit tests, lint (WebView lint warnings such as `SetJavaScriptEnabled` and `JavascriptInterface` need `@SuppressLint` with a one-line justification pointing to ADR-0002), both flavors build. Expected: PASS.
- [ ] **Step 5: Commit** `feat(innertube): confined WebView PoToken generator ported from Meld`.

### Task 11: App wiring and player UI

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/{ConnectivityNetworkMonitor,PlayerScreen}.kt`
- Modify: `app/src/main/kotlin/app/aria/music/{AriaApp,MainActivity}.kt`, `app/src/main/kotlin/app/aria/music/di/AppModule.kt`, `app/src/main/AndroidManifest.xml`
- Delete: the Task 7 shim text in `MainActivity` (replaced), any remaining `SkeletonScreen.kt`, `SharedPrefsResumeStore.kt`

**Interfaces:**
- Consumes: everything from Tasks 1 to 10.
- Produces: the engine as a Hilt singleton bound as both `DefaultPlaybackEngine` and `PlaybackEngine`; `AriaApp : PlaybackEngineProvider`.

**Acceptance Criteria:**
- Both flavors assemble; `checkModuleGraph`, lint (including `NewApi`) and the license check pass.
- `AriaApp` implements `PlaybackEngineProvider` with the injected engine, and calls `engine.restore()` once at startup on the main thread.
- The screen offers: search; tap a result to play it with the result list as queue; "Next" and "Add" per result; now-playing block with seek slider, previous/play-pause/next, shuffle and repeat toggles; queue list with remove and move up/down; sleep timer (15 min, end of track, off); switches for skip silence, loudness normalization and endless radio; skip and stop events shown as a message.
- Starting playback calls `AriaPlaybackService.start(context)` first and requests `POST_NOTIFICATIONS` on API 33+ (playback continues if declined).
- `ACCESS_NETWORK_STATE` is declared; no `TODO(sub-project 2)` remains anywhere (`grep -rn "TODO(sub-project 2)" .` over source returns nothing).

- [ ] **Step 1: Build check (no new JVM test: the logic lives in the engine).** Push the first compile of the changes below; the emulator test in Task 12 covers the wiring.

- [ ] **Step 2: Implement.** Manifest: add `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`.

```kotlin
// ConnectivityNetworkMonitor.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.aria.domain.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ConnectivityNetworkMonitor(context: Context) : NetworkMonitor {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val state = MutableStateFlow(currentlyOnline())
    override val online: StateFlow<Boolean> = state

    private fun currentlyOnline(): Boolean =
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { state.value = true }
            override fun onLost(network: Network) { state.value = false }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        })
    }
}
```

`AppModule.kt` (keep `database` and `playEvents`, `search`; drop `resume`):

```kotlin
    @Provides @Singleton fun network(@ApplicationContext c: Context): NetworkMonitor = ConnectivityNetworkMonitor(c)
    @Provides @Singleton fun queueStore(db: AriaDatabase): QueueStore = QueueStoreImpl(db.queueDao())
    @Provides @Singleton fun radio(): RadioSource = YouTubeRadioSource()
    @Provides @Singleton fun poTokens(@ApplicationContext c: Context): PoTokenSource = WebViewPoTokenSource(c)
    @Provides @Singleton fun extractorSession(p: PoTokenSource): ExtractorSession = ExtractorSession.createDefault(p)
    @Provides @Singleton fun streams(s: ExtractorSession): StreamSource = YouTubeStreamSource.create(s)

    @Provides @Singleton
    fun engine(
        streams: StreamSource, store: QueueStore, radio: RadioSource, history: PlayEventRepository, network: NetworkMonitor,
    ): DefaultPlaybackEngine = DefaultPlaybackEngine(streams, store, radio, history, network, Dispatchers.Main.immediate)

    @Provides @Singleton fun playbackEngine(e: DefaultPlaybackEngine): PlaybackEngine = e
```

`AriaApp.kt`: add `import javax.inject.Inject`, `kotlinx.coroutines.MainScope`, `launch`; class header `class AriaApp : Application(), PlaybackEngineProvider`; members:

```kotlin
    @Inject lateinit var engine: DefaultPlaybackEngine
    override val playbackEngine: DefaultPlaybackEngine get() = engine
```
and at the end of `onCreate()`: `MainScope().launch { engine.restore() }`.

`MainActivity.kt`:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var search: SearchSource
    @Inject lateinit var engine: PlaybackEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PlayerScreen(search, engine) { AriaPlaybackService.start(this) } } }
    }
}
```

`PlayerScreen.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aria.designsystem.GlassSurface
import app.aria.domain.*
import kotlinx.coroutines.launch

private fun time(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

@Composable
fun PlayerScreen(search: SearchSource, engine: PlaybackEngine, startService: () -> Unit) {
    val state by engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var drag by remember { mutableStateOf<Float?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(engine) {
        engine.events.collect { e ->
            message = when (e) { is PlaybackEvent.Skipped -> "Skipped ${e.track.title}: ${e.reason}"; is PlaybackEvent.Stopped -> e.reason }
        }
    }
    fun begin() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        startService()
    }

    GlassSurface(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(BuildConfig.BRAND_NAME, style = MaterialTheme.typography.titleLarge) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") }, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        scope.launch {
                            search.searchTracks(query)
                                .onSuccess { results = it; message = "${it.size} results" }
                                .onFailure { message = "Search failed: ${it.javaClass.simpleName}" }
                        }
                    }) { Text("Search") }
                }
            }
            if (message.isNotEmpty()) item { Text(message) }
            item {
                val current = state.current
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(current?.let { "${it.track.title} - ${it.track.artists.joinToString()}" } ?: "Nothing playing", style = MaterialTheme.typography.titleMedium)
                    Text(when (val s = state.status) {
                        PlaybackStatus.Idle -> "Idle"; PlaybackStatus.Buffering -> "Buffering"; PlaybackStatus.Playing -> "Playing"
                        PlaybackStatus.Paused -> "Paused"; PlaybackStatus.WaitingForNetwork -> "Waiting for network"; is PlaybackStatus.Error -> s.message
                    })
                    val duration = state.durationMs.coerceAtLeast(1L)
                    Slider(
                        value = drag ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                        onValueChange = { drag = it },
                        onValueChangeFinished = { drag?.let { engine.seekTo((it * duration).toLong()) }; drag = null },
                        enabled = current != null,
                    )
                    Text("${time(state.positionMs)} / ${time(state.durationMs)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = engine::previous, enabled = current != null) { Text("Prev") }
                        Button(onClick = { if (state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Buffering) engine.pause() else engine.play() }, enabled = current != null) { Text("Play/Pause") }
                        Button(onClick = engine::next, enabled = current != null) { Text("Next") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = state.shuffle, onClick = { engine.setShuffle(!state.shuffle) }, label = { Text("Shuffle") })
                        FilterChip(
                            selected = state.repeat != RepeatMode.OFF,
                            onClick = { engine.setRepeat(when (state.repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF }) },
                            label = { Text("Repeat ${state.repeat.name.lowercase()}") },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.sleepTimer?.endsAtEpochMs != null, onClick = { engine.setSleepTimer(15 * 60_000L) }, label = { Text("Sleep 15 min") })
                        FilterChip(selected = state.sleepTimer?.endOfTrack == true, onClick = { engine.setSleepAtEndOfTrack(true) }, label = { Text("End of track") })
                        FilterChip(selected = false, onClick = { engine.setSleepTimer(null) }, label = { Text("Sleep off") })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(state.skipSilence, engine::setSkipSilence); Text(" Skip silence") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(state.normalizeLoudness, engine::setNormalizeLoudness); Text(" Loudness normalization") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(state.endlessRadio, engine::setEndlessRadio); Text(" Endless radio") }
                }
            }
            item { Text("Results", style = MaterialTheme.typography.titleSmall) }
            itemsIndexed(results) { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = { begin(); engine.playNow(results, i) }) { Text("${t.title} - ${t.artists.joinToString()}", maxLines = 1) }
                    TextButton(onClick = { begin(); engine.playNext(t) }) { Text("Next") }
                    TextButton(onClick = { begin(); engine.addToEnd(listOf(t)) }) { Text("Add") }
                }
            }
            item { Text("Queue (${state.queue.size})", style = MaterialTheme.typography.titleSmall) }
            itemsIndexed(state.queue, key = { _, item -> item.queueId }) { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (i == state.currentIndex) "> " else "") + item.track.title, modifier = Modifier.weight(1f), maxLines = 1)
                    TextButton(onClick = { engine.skipTo(i) }) { Text("Play") }
                    TextButton(onClick = { engine.move(i, i - 1) }, enabled = i > 0) { Text("Up") }
                    TextButton(onClick = { engine.move(i, i + 1) }, enabled = i < state.queue.lastIndex) { Text("Down") }
                    TextButton(onClick = { engine.removeAt(i) }) { Text("Remove") }
                }
            }
        }
    }
}
```

- [ ] **Step 3:** Push; fix compile and lint findings from CI. Expected: both flavors assemble, all existing jobs green.
- [ ] **Step 4: Commit** `feat(app): engine wiring, restore at startup and player screen`.

---

### Task 12: Service emulator tests and CI job

Spec section 6, stress-test branch 2.

**Files:**
- Modify: `playback/build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `.github/workflows/ci.yml`
- Create: `playback/src/androidTest/kotlin/app/aria/playback/{AriaTestRunner,TestAriaApp,SilentWav,ServiceFakes,PlaybackServiceTest}.kt`, `app/src/androidTest/kotlin/app/aria/music/{HiltTestRunner,HiltWiringTest}.kt`

**Interfaces:**
- Consumes: `DefaultPlaybackEngine`, `PlaybackEngineProvider`, `AriaPlaybackService`, `TrustPolicy`, the fakes pattern from `EngineTestKit.kt` (copy the small fakes `FakeStreams`-style into `ServiceFakes.kt`; an androidTest cannot see `src/test`).
- Produces: CI job `playback-service` (matrix API 26 and 34).

**Acceptance Criteria:**
- On API 26 and 34 emulators: playback of a locally served silent WAV advances the position; the media notification is posted; a `MediaController` from the same package connects, `pause()` pauses the engine and `seekToNext()` moves to the next track; the app's Hilt graph resolves `PlaybackEngine`.
- The untrusted-controller rule is covered by `TrustPolicyTest` (a foreign-package controller cannot be created from an instrumentation test; this is a recorded deviation from spec section 6).
- CI job fails the build on any failure.

- [ ] **Step 1: Catalog and build wiring.** Copy the instrumentation setup from `designsystem/build.gradle.kts` (runner, dependencies, `testInstrumentationRunner`) and adapt. Add catalog entries if absent: `androidx-test-runner = androidx.test:runner:1.7.0`, `androidx-test-rules = androidx.test:rules:1.7.0`, `androidx-test-ext-junit = androidx.test.ext:junit:1.3.0`, `hilt-testing = com.google.dagger:hilt-android-testing (version.ref hilt)`, `media3-session` already exists. `playback/build.gradle.kts`: `defaultConfig { testInstrumentationRunner = "app.aria.playback.AriaTestRunner" }`; `androidTestImplementation(libs.androidx.test.runner, libs.androidx.test.rules, libs.androidx.test.ext.junit, libs.androidx.test.core, libs.coroutines.test, libs.media3.session)`. `app/build.gradle.kts`: `defaultConfig { testInstrumentationRunner = "app.aria.music.HiltTestRunner" }`, `androidTestImplementation(libs.hilt.testing, libs.androidx.test.runner, libs.androidx.test.ext.junit)`, `kspAndroidTest(libs.hilt.compiler)`.

- [ ] **Step 2: Test support (`playback/src/androidTest`).**

```kotlin
// AriaTestRunner.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Swaps in an Application that provides a real engine with fake sources, so the real service can be tested without Hilt. */
class AriaTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestAriaApp::class.java.name, context)
}
```

```kotlin
// SilentWav.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes a mono 8 kHz 8-bit PCM WAV of silence; no audio asset needs to be committed. */
object SilentWav {
    fun write(dir: File, seconds: Int = 6): File {
        val rate = 8000; val data = ByteArray(rate * seconds) { 0x80.toByte() }
        val b = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray()).put("fmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate).putShort(1).putShort(8)
            .put("data".toByteArray()).putInt(data.size).put(data)
        return File(dir, "silence.wav").also { it.writeBytes(b.array()) }
    }
}
```

`ServiceFakes.kt`: `ServiceStreams(url: String) : StreamSource` returning `ResolvedStream(url, "audio/wav", 64_000, null, null)`; `NoopStore : QueueStore` (null load, ignores saves); `NoopRadio : RadioSource` (empty list); `NoopHistory : PlayEventRepository`; `AlwaysOnline : NetworkMonitor`.

```kotlin
// TestAriaApp.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.app.Application
import kotlinx.coroutines.Dispatchers

class TestAriaApp : Application(), PlaybackEngineProvider {
    override val playbackEngine: DefaultPlaybackEngine by lazy {
        val wav = SilentWav.write(cacheDir)
        DefaultPlaybackEngine(ServiceStreams("file://${wav.absolutePath}"), NoopStore(), NoopRadio(), NoopHistory(), AlwaysOnline(), Dispatchers.Main.immediate)
    }
}
```

- [ ] **Step 3: The test.**

```kotlin
// PlaybackServiceTest.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.aria.domain.*
import org.junit.*
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext
    private val engine get() = (ctx.applicationContext as PlaybackEngineProvider).playbackEngine
    private fun track(n: Int) = Track(TrackId.youtube("track%06d".format(n)), "T$n", listOf("A"), 6_000L, null)
    private fun onMain(block: () -> Unit) = inst.runOnMainSync(block)

    private fun waitUntil(timeoutMs: Long = 20_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return; Thread.sleep(100) }
        Assert.fail("condition not met within $timeoutMs ms; state=${engine.state.value}")
    }

    @Before fun start() { onMain { AriaPlaybackService.start(ctx); engine.setEndlessRadio(false); engine.playNow(listOf(track(1), track(2))) } }
    @After fun stop() { onMain { engine.clear() }; ctx.stopService(android.content.Intent(ctx, AriaPlaybackService::class.java)) }

    @Test fun positionAdvancesAndNotificationIsPosted() {
        waitUntil { engine.state.value.status == PlaybackStatus.Playing && engine.state.value.positionMs > 1_000 }
        waitUntil {
            ctx.getSystemService(NotificationManager::class.java).activeNotifications.any { it.packageName == ctx.packageName }
        }
    }

    @Test fun sessionControlsReachTheEngine() {
        waitUntil { engine.state.value.status == PlaybackStatus.Playing }
        val token = SessionToken(ctx, ComponentName(ctx, AriaPlaybackService::class.java))
        val controller = MediaController.Builder(ctx, token).buildAsync().get(15, TimeUnit.SECONDS)
        onMain { controller.pause() }
        waitUntil { engine.state.value.status == PlaybackStatus.Paused }
        onMain { controller.seekToNext() }
        waitUntil { engine.state.value.currentIndex == 1 }
        onMain { controller.release() }
    }
}
```

`app/src/androidTest`:

```kotlin
// HiltTestRunner.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
```

```kotlin
// HiltWiringTest.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import app.aria.domain.PlaybackEngine
import app.aria.domain.StreamSource
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class HiltWiringTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var engine: PlaybackEngine
    @Inject lateinit var streams: StreamSource

    @Test fun graphResolves() { hilt.inject(); assertNotNull(engine); assertNotNull(streams) }
}
```

- [ ] **Step 4: CI job** in `ci.yml` (copy the `glass-ui` job: same pinned actions, KVM step, emulator runner, matrix `api: [26, 34]`), job name `playback-service`, script `scripts/ci-gradle.sh :playback:connectedDebugAndroidTest :app:connectedFossDebugAndroidTest`. If the ABI split config in `:app` breaks the connected test, set `isEnable = !project.hasProperty("noAbiSplits")` in `app/build.gradle.kts` and pass `-PnoAbiSplits` in this job only.
- [ ] **Step 5:** Push; iterate until green on both APIs (read failure details from check-run annotations; emulator logcat is not available, so on a service crash add a temporary `adb logcat -d` step in the job script and remove it after). Expected: PASS.
- [ ] **Step 6: Commit** `test(playback): service emulator tests and Hilt wiring test`.

---

### Task 13: Debug APK workflow, smoke extension, docs and final checks

Spec section 7 (definition of done) and section 6 (smoke, manual checklist).

**Files:**
- Create: `.github/workflows/debug-apk.yml`, `docs/manual-device-checklist.md`
- Modify: `.github/workflows/ci.yml` (foreign-host grep), `innertube/src/test/kotlin/app/aria/innertube/smoke/PlaybackSmokeTest.kt`, `docs/providers/youtube-music.md`, `docs/providers/innertubex.md`, `docs/data-flow.md`, `docs/release-gates.md`, `PRIVACY.md`

**Acceptance Criteria:**
- `debug-apk` runs on push to `main` and on manual dispatch, builds `foss` and `gms` debug APKs, uploads them as Actions artifacts named with the commit SHA (retention 30 days) and publishes **no** release.
- The nightly smoke test still runs as exactly one test method (`tests="1"` check in `smoke.yml` stays valid) and additionally checks that the radio source returns tracks for a fixed seed and that each resolved URL is `https` with an expiry.
- CI fails if any tracked source or resource file other than docs mentions `ZemerTeam` or `raw.githubusercontent.com` (no dependency on another project's server).
- Docs match behaviour: provider record (extractor, PoToken generator, bundled cipher config, radio call), data-flow rows, `PRIVACY.md`, release gates (cipher keys; PoToken gate "closes after the maintainer confirms playback on a device"; debug APKs only), and a manual device checklist (play with screen off for five minutes, unplug headphones, receive a call, force-stop and relaunch to resume, airplane mode then restore, sleep timer, shuffle and repeat, kill service from the task manager and press play).
- `grep -rn "TODO(sub-project 2)\|PlaybackHost\|PlayerController" --include=*.kt --include=*.md --include=*.kts . ` (excluding `.internal/`) returns nothing.

- [ ] **Step 1: `.github/workflows/debug-apk.yml`:**

```yaml
# SPDX-License-Identifier: GPL-3.0-only
name: debug-apk
on:
  push: { branches: [main] }
  workflow_dispatch:
permissions: { contents: read }
jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.4.0
        with: { persist-credentials: false }
      - uses: actions/setup-java@cf277c60eb25467037889841efdb72551f06f6c3 # v4.9.1
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@748248ddd2a24f49513d8f472f81c3a07d4d50e1 # v4.4.4
      - run: scripts/ci-gradle.sh :app:assembleFossDebug :app:assembleGmsDebug
      - uses: actions/upload-artifact@<SHA from Task 6> # <tag>
        with:
          name: aria-debug-${{ github.sha }}
          path: app/build/outputs/apk/**/debug/*.apk
          retention-days: 30
          if-no-files-found: error
```

This is a private artifact (visible to people with repo access) and deliberately not a release: public builds stay blocked by `docs/release-gates.md`.

- [ ] **Step 2: Smoke test.** Extend the single existing test method: after the resolve loop, `val radio = YouTubeRadioSource().related(seedTrack, 5)` with `seedTrack` built from the first id (assert `isSuccess` and non-empty), and per resolved stream assert `url.startsWith("https://")` and `expiresAtEpochMs != null`. Do not add a second `@Test`.

- [ ] **Step 3: Foreign-host guard** in `ci.yml` "Static checks":

```bash
          if git grep -nE 'ZemerTeam|raw\.githubusercontent\.com' -- . ':!docs' ':!.internal' ':!*.csv' ':!*.md' ':!.github'; then echo "Dependence on another project's server found"; exit 1; fi
```

- [ ] **Step 4: Docs.** Write `docs/manual-device-checklist.md` (checklist above, each item with expected result). Update the provider record, data-flow, `PRIVACY.md` and release gates as listed. In `docs/release-gates.md` replace the v1.0 line about PoToken with: `- [ ] PoToken generation exists (implemented in sub-project 2); closes after the maintainer confirms real playback on a device with a debug build.`

- [ ] **Step 5: Final verification.**
  1. Push; every CI job (verify, glass-ui, playback-service, debug-apk) green on the final commit; download the debug APK artifact name from the run to confirm both flavors uploaded.
  2. Run the grep from the acceptance criteria.
  3. Controller dispatches a final whole-branch review (fresh reviewer, spec + this plan as inputs), fixes findings, and re-checks CI.
  4. Report to the maintainer: which APK to install, the manual checklist, and that the PoToken gate remains open until they confirm playback.
- [ ] **Step 6: Commit** `ci,docs: debug APK artifacts, smoke extension, docs and checklist`.

---

## Self-Review

**Spec coverage** (every requirement maps to a task):

| Spec item | Task |
|---|---|
| Queue ops, shuffle, repeat, previous rule (3) | 2 |
| Stream URLs, expiry, prefetch, 403 refresh (3) | 3 |
| Error classes, backoff, offline wait, 3-skip stop (3, branch 6) | 3 |
| History once at 30 s (3, branch 7) | 4 |
| Resume, structure-on-change, 15 s position (3, branch 7) | 4, 6 |
| Endless radio, batch 25, cap 500 (3, branch 7) | 5 |
| Sleep timer, skip silence, attenuate-only loudness (3, branch 7) | 5 |
| Ownership and queue mirroring (2, branch 1) | 2, 7 |
| `PlaybackEngineProvider`, no Hilt in `:playback`, trusted controllers (2, branch 2) | 7, 11, 12 |
| `QueueStore` Room table (4) | 6 |
| Extractor adapter, loudness mapping, failed-client memory (4) | 9 |
| Radio source, no third-party endpoints (4) | 9, 13 |
| Cipher config signed, bundled default, disabled (4, branch 4) | 8 |
| WebView confinement + static check (4, branch 3) | 10 |
| Diagnostics logger (4) | Not a separate task: no new logging of stream URLs, tokens or IPs is added, and `check-webview-safety`/Timber use is limited to messages without URLs. **Surfaced cut:** a `Fix403`-style diagnostics logger is deferred to when a user-facing log export exists (sub-project 3). |
| Service emulator tests (6, branch 2) | 12 |
| Real extractor smoke (6) | 13 |
| Manual device checklist (6) | 13 |
| Debug APK artifacts, no release (7, branch 5) | 13 |
| Docs, ledger rows, release gates (7) | 8, 9, 10, 13 |
| Skeleton `PlaybackHost` and TODOs gone (7) | 7, 11, 13 |
| PoToken gate stays open (7) | 13 |

**Deviations to surface to the maintainer:** (1) the "untrusted controller is rejected" check is a pure `TrustPolicy` unit test, not an emulator test, because a foreign-package controller cannot be created from an instrumentation test; (2) skip silence, loudness and endless-radio toggles are in memory only until sub-project 3 adds settings; (3) the diagnostics logger is deferred (table above); (4) the cipher-config bundled default may be blocked on a license answer (Task 8 Step 3.4), in which case the controller asks the maintainer before continuing.

**Placeholder scan:** the only deliberate forward references are the five `/* Task 5 */` method bodies in Task 2 (replaced and grep-checked in Task 5) and the **(verify)** marks on third-party API names, which CI resolves. No other TBD/TODO text.

**Type consistency:** `PlayerOutput`, `PlayerListener`, `PlayerError`, `QueueStore`, `SavedQueue(items, originalOrder, shuffle, repeat, nextQueueId)`, `SavedPosition(index, positionMs)`, `ResolvedStream.loudnessDb`, `StreamSource.markRejected`, `RadioSource.related(seed, limit)`, `NetworkMonitor.online`, `DefaultPlaybackEngine(streams, store, radio, history, network, dispatcher, clock, random)` (with `attach`, `detach`, `release`, `restore`, `streamFor`) are used with identical names and signatures in Tasks 1 to 12. The engine property is `repeatMode` internally and `repeat` on `PlaybackState`.
