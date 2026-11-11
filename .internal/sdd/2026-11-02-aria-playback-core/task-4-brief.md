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

