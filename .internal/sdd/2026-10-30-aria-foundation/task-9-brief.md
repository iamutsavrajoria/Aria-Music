### Task 9: `:playback` minimal service and controller

Smallest Media3 foreground service that proves the boundary: depends only on `:domain`.

**Files:**
- Create: `playback/build.gradle.kts`, `playback/src/main/AndroidManifest.xml`, `playback/src/main/kotlin/app/aria/playback/AriaPlaybackService.kt`, `playback/src/main/kotlin/app/aria/playback/PlayerController.kt`, `playback/src/main/kotlin/app/aria/playback/ResumeStore.kt`, `playback/src/test/kotlin/app/aria/playback/PlayerControllerTest.kt`

**Interfaces:**
- Consumes: `StreamSource`, `ResolvedStream`, `PlayEventRepository`, `Track`, `TrackId` (Task 2).
- Produces:
  - `interface Player { fun play(url: String, startPositionMs: Long); fun positionMs(): Long; fun stop() }` (a seam so the controller is testable without Media3)
  - `interface ResumeStore { fun save(id: TrackId, positionMs: Long); fun load(id: TrackId): Long }`
  - `class PlayerController(streams: StreamSource, events: PlayEventRepository, player: Player, resume: ResumeStore, clock: () -> Long)` with `suspend fun start(track: Track): Result<Unit>` and `suspend fun stopAndRecord(track: Track)`
  - `class AriaPlaybackService : MediaSessionService`

**Acceptance Criteria:**
- `start` resolves the stream through `StreamSource`, starts the player at the saved resume position, and returns failure (without touching the player) if resolution fails.
- `stopAndRecord` saves the current position as the resume position and records one play event with the elapsed play time.
- `:playback` has no dependency on any project except `:domain` (checked by `checkModuleGraph`).

- [ ] **Step 1: Write `playback/build.gradle.kts`**

```kotlin
plugins { id("com.android.library") }

android {
    namespace = "app.aria.playback"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":domain"))
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
```
Run `grep -n "media3\|coroutines" gradle/libs.versions.toml` and use the existing alias names; add missing ones at the already-pinned version.

- [ ] **Step 2: Write the failing controller tests**

`playback/src/test/kotlin/app/aria/playback/PlayerControllerTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.PlayEventRepository
import app.aria.domain.ResolvedStream
import app.aria.domain.StreamSource
import app.aria.domain.StreamUnavailableException
import app.aria.domain.Track
import app.aria.domain.TrackId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControllerTest {
    private val track = Track(TrackId.youtube("dQw4w9WgXcQ"), "Song", listOf("A"), 200_000L, null)

    private class FakePlayer : Player {
        var played: Pair<String, Long>? = null
        var position = 0L
        var stopped = false
        override fun play(url: String, startPositionMs: Long) { played = url to startPositionMs }
        override fun positionMs() = position
        override fun stop() { stopped = true }
    }

    private class FakeResume(var saved: Long = 0L) : ResumeStore {
        override fun save(id: TrackId, positionMs: Long) { saved = positionMs }
        override fun load(id: TrackId) = saved
    }

    private class FakeEvents : PlayEventRepository {
        val recorded = mutableListOf<Triple<Track, Long, Long>>()
        override suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long) {
            recorded += Triple(track, playedAtEpochMs, playTimeMs)
        }
        override suspend fun playCount(id: TrackId) = recorded.size
    }

    private fun streams(result: Result<ResolvedStream>) = object : StreamSource {
        override suspend fun resolve(id: TrackId) = result
    }

    private val stream = ResolvedStream("https://example.invalid/a", "audio/webm", 160_000, 10L, null)

    @Test fun startPlaysAtSavedResumePosition() = runTest {
        val player = FakePlayer()
        val c = PlayerController(streams(Result.success(stream)), FakeEvents(), player, FakeResume(12_345L)) { 0L }
        assertTrue(c.start(track).isSuccess)
        assertEquals("https://example.invalid/a" to 12_345L, player.played)
    }

    @Test fun failedResolveDoesNotTouchPlayer() = runTest {
        val player = FakePlayer()
        val c = PlayerController(streams(Result.failure(StreamUnavailableException("no"))), FakeEvents(), player, FakeResume()) { 0L }
        assertTrue(c.start(track).isFailure)
        assertEquals(null, player.played)
    }

    @Test fun stopSavesPositionAndRecordsOneEvent() = runTest {
        val player = FakePlayer()
        val resume = FakeResume()
        val events = FakeEvents()
        var now = 1_000L
        val c = PlayerController(streams(Result.success(stream)), events, player, resume) { now }
        c.start(track)
        now = 31_000L
        player.position = 30_500L
        c.stopAndRecord(track)
        assertEquals(30_500L, resume.saved)
        assertEquals(1, events.recorded.size)
        assertEquals(30_000L, events.recorded.single().third)
        assertTrue(player.stopped)
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :playback:testDebugUnitTest`
Expected: FAIL, unresolved `PlayerController`, `Player`, `ResumeStore`.

- [ ] **Step 4: Implement the controller**

`playback/src/main/kotlin/app/aria/playback/PlayerController.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.PlayEventRepository
import app.aria.domain.StreamSource
import app.aria.domain.Track

/** Seam over the real Media3 player so the controller is testable on the JVM. */
interface Player {
    fun play(url: String, startPositionMs: Long)
    fun positionMs(): Long
    fun stop()
}

interface ResumeStore {
    fun save(id: app.aria.domain.TrackId, positionMs: Long)
    fun load(id: app.aria.domain.TrackId): Long
}

class PlayerController(
    private val streams: StreamSource,
    private val events: PlayEventRepository,
    private val player: Player,
    private val resume: ResumeStore,
    private val clock: () -> Long,
) {
    private var startedAt: Long = 0L

    suspend fun start(track: Track): Result<Unit> =
        streams.resolve(track.id).map { stream ->
            player.play(stream.url, resume.load(track.id))
            startedAt = clock()
        }

    suspend fun stopAndRecord(track: Track) {
        val position = player.positionMs()
        val playTime = clock() - startedAt
        player.stop()
        resume.save(track.id, position)
        events.record(track, playedAtEpochMs = startedAt, playTimeMs = playTime)
    }
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :playback:testDebugUnitTest`
Expected: PASS, 3 tests.

- [ ] **Step 6: Add the Media3 service and adapters**

`playback/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.INTERNET" />
    <application>
        <service
            android:name="app.aria.playback.AriaPlaybackService"
            android:exported="true"
            android:foregroundServiceType="mediaPlayback">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

`playback/src/main/kotlin/app/aria/playback/AriaPlaybackService.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Minimal foreground playback service. Real queue, crossfade and downloads arrive in sub-project 2. */
class AriaPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this).build()
        session = MediaSession.Builder(this, exo).build()
        PlaybackHost.player = Media3Player(exo)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (session?.player?.playWhenReady != true) stopSelf()
    }

    override fun onDestroy() {
        PlaybackHost.player = null
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/**
 * Process-local handle the app uses to reach the running service's player.
 * TODO(sub-project 2): skeleton shortcut. Replace with a bound MediaController; do not build on this.
 */
object PlaybackHost {
    @Volatile var player: Player? = null
}

class Media3Player(private val exo: ExoPlayer) : Player {
    override fun play(url: String, startPositionMs: Long) {
        exo.setMediaItem(MediaItem.fromUri(url), startPositionMs)
        exo.prepare()
        exo.play()
    }
    override fun positionMs(): Long = exo.currentPosition
    override fun stop() = exo.stop()
}
```
`ResumeStore`'s Android implementation (SharedPreferences) lives in `:app` (Task 10) so `:playback` stays free of storage choices.

- [ ] **Step 7: Verify the boundary and commit**

Run: `./gradlew :playback:assembleDebug checkModuleGraph`
Expected: `BUILD SUCCESSFUL`.

```bash
git add -A && git commit -m "feat(playback): add minimal Media3 service and PlayerController behind domain interfaces"
```

---

