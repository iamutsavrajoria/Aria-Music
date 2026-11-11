### Task 13: Service emulator tests and CI job

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
    override val allowLocalStreams: Boolean = true   // this test app serves a local WAV; production never does
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

