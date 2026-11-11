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

