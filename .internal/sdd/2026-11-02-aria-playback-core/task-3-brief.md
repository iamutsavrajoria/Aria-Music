### Task 3: Engine stream URLs and error handling

**Files:**
- Modify: `playback/src/main/kotlin/app/aria/playback/DefaultPlaybackEngine.kt`
- Test: `playback/src/test/kotlin/app/aria/playback/EngineErrorsTest.kt`

**Interfaces:**
- Consumes: Task 2 engine (`onCurrentChanged`, `goTo`, `nextIndexOrNull`, `stopAtEnd`, `updateTicker`, `publish`, `_events`), `FakeStreams`, `FakeNetwork`, `FakeOutput.retries`.
- Produces: `suspend fun streamFor(id: TrackId): Result<ResolvedStream>` (thread-safe; the Media3 adapter calls it from the loader thread); error behaviour per spec section 3 "Errors".

**Acceptance Criteria:**
- A resolved URL is cached; an expired one (within 60 s of `expiresAtEpochMs`) is re-resolved.
- (Stress-test branch 3) Resolution is bounded: `streamFor` fails with an `IOException` (transient) after 20 s, and concurrent requests for the same track share one in-flight resolve, so prefetch and the loader thread never start two extractor runs.
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
    @Test fun hungResolveTimesOutAsTransientFailure() = runTest {
        val h = Harness(testScheduler).attach(); h.streams.hang = true
        val result = async { h.engine.streamFor(track(1).id) }
        h.advance(19_999); assertFalse(result.isCompleted)
        h.advance(2); assertTrue(result.await().exceptionOrNull() is java.io.IOException)
        h.release()
    }
    @Test fun concurrentRequestsShareOneResolve() = runTest {
        val h = Harness(testScheduler).attach(); h.streams.delayMs = 1_000; val id = track(1).id
        val a = async { h.engine.streamFor(id) }; val b = async { h.engine.streamFor(id) }
        h.advance(1_000)
        assertTrue(a.await().isSuccess && b.await().isSuccess)
        assertEquals(1, h.streams.resolved.count { it == id })
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

(Add `import kotlinx.coroutines.flow.toList`, `import kotlinx.coroutines.launch` and `import kotlinx.coroutines.async` at the top.)

- [ ] **Step 2:** Push. Expected: FAIL (`streamFor` missing; `onError` sets a generic error).

- [ ] **Step 3: Implement.** In `DefaultPlaybackEngine.kt` add imports `java.util.concurrent.ConcurrentHashMap`, then these members (place the fields after `private var tickJob`, the functions before `companion object`), **replace** the Task 2 `onError`, **extend** `onCurrentChanged` and `onBufferingChanged`:

```kotlin
    // fields
    private val resolved = ConcurrentHashMap<TrackId, ResolvedStream>()
    private var consecutiveFailures = 0
    private var retryCount = 0
    private var refreshTried = false
    private var retryJob: Job? = null

    private val inFlight = ConcurrentHashMap<TrackId, Deferred<Result<ResolvedStream>>>()

    /**
     * Thread-safe: also called from ExoPlayer's loader thread by the Media3 adapter. Bounded by [RESOLVE_TIMEOUT_MS]
     * and de-duplicated per track (prefetch and the loader share one resolve). A timeout is an ordinary IOException,
     * so the engine retries it as transient.
     */
    suspend fun streamFor(id: TrackId): Result<ResolvedStream> {
        resolved[id]?.takeIf { !it.isExpired() }?.let { return Result.success(it) }
        val job = inFlight.computeIfAbsent(id) { scope.async(workDispatcher, CoroutineStart.LAZY) { resolveBounded(id) } }
        return try { job.await() } finally { inFlight.remove(id, job) }
    }

    private suspend fun resolveBounded(id: TrackId): Result<ResolvedStream> {
        val result = try {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { streams.resolve(id) }
                ?: Result.failure(java.io.IOException("Stream resolution timed out"))
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
        result.onSuccess { resolved[id] = it }
        return result
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

Add to the `companion object`: `const val RESOLVE_TIMEOUT_MS = 20_000L`, `const val EXPIRY_MARGIN_MS = 60_000L`, `const val MAX_CONSECUTIVE_FAILURES = 3`, `val BACKOFF_MS = longArrayOf(1_000, 2_000, 4_000)`. In `play()` the error branch must also reset: `consecutiveFailures = 0`.

- [ ] **Step 4:** Push; `:playback:test` green (Task 2 tests still pass). Expected: PASS.
- [ ] **Step 5: Commit** `feat(playback): stream URL cache and classified error handling`.

---

