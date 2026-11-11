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

Hook edits: `onCurrentChanged` (end, before `publish()`) adds `applyVolume(); maybeTopUp()`. `resolveBounded` after `result.onSuccess { resolved[id] = it }` also `if (id == model.current?.track?.id) scope.launch { applyVolume() }` (that launch runs on the engine dispatcher, so the volume is applied on the main thread). `setRepeat` adds `maybeTopUp()`. `onItemTransition` after `onCurrentChanged()` adds `applyEndOfTrackSleep(index)`. `onEnded`: first line `sleepEndOfTrack = false; sleepTimer = null`. Companion: `RADIO_TRIGGER = 5`, `RADIO_BATCH = 25`, `QUEUE_CAP = 500`, `FADE_MS = 5_000L`, `FADE_STEPS = 20`, `MIN_GAIN = 0.1f`.

- [ ] **Step 4:** Push; all engine tests green. Expected: PASS. Then `grep -n "Task 5" playback/src/main` returns nothing.
- [ ] **Step 5: Commit** `feat(playback): endless radio, sleep timer, skip silence, loudness`.

