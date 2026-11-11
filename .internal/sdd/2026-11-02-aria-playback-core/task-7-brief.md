### Task 7: Trust, stream-URL and error policies (pure `:playback` code)

**Files:**
- Create: `playback/src/main/kotlin/app/aria/playback/{PlaybackEngineProvider,TrustPolicy,StreamUrlPolicy,PlayerErrorMapper}.kt`
- Modify: `playback/build.gradle.kts`
- Test: `playback/src/test/kotlin/app/aria/playback/TrustPolicyTest.kt`, `StreamUrlPolicyTest.kt`, `PlayerErrorMapperTest.kt`, `playback/src/test/resources/robolectric.properties` (`sdk=35`)

**Interfaces:**
- Consumes: `PlayerError` (Task 1), `DefaultPlaybackEngine` (type only), and the Media3 declarations in `docs/spikes/2026-11-playback-apis.md` item 3 (**use the spike's signatures wherever they differ from the code below**).
- Produces: `interface PlaybackEngineProvider { val playbackEngine: DefaultPlaybackEngine; val allowLocalStreams: Boolean get() = false }` (implemented by the `Application`; only the emulator test app overrides the flag), `object StreamUrlPolicy { fun isAllowed(url: String, allowLocal: Boolean): Boolean }`, `object TrustPolicy { fun isAllowed(...): Boolean }`, `PlayerErrorMapper.map(Throwable): PlayerError`, `class PermanentStreamException`.
- Nothing in `:app` changes, so CI stays green; the old skeleton player is untouched until Task 8.

**Acceptance Criteria:**
- A same-package controller, the system, the media-notification controller and a trusted controller are accepted; any other package is rejected (`TrustPolicy`).
- `StreamUrlPolicy` (stress-test branch 2, security): only `https` URLs with a non-empty host are allowed; `http`, `file`, `content`, `asset`, `rawresource`, `data`, `javascript` and malformed URLs are rejected; with `allowLocal = true` (emulator test only) `file` is also allowed.
- `PlayerErrorMapper`: cause chain containing `PermanentStreamException` maps to `Permanent`; `HttpDataSource.InvalidResponseCodeException` to `Http(code)`; anything else to `Io`.

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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamUrlPolicyTest {
    @Test fun httpsWithHostIsAllowed() = assertTrue(StreamUrlPolicy.isAllowed("https://rr1---sn.googlevideo.com/videoplayback?x=1", allowLocal = false))
    @Test fun everythingElseIsRejected() {
        listOf("http://a.example/x", "file:///data/data/app/aria.db", "content://contacts/people", "asset:///x", "rawresource:///1",
            "data:audio/wav;base64,AAAA", "javascript:alert(1)", "https:///nohost", "not a url", "").forEach {
            assertFalse(StreamUrlPolicy.isAllowed(it, allowLocal = false), it)
        }
    }
    @Test fun localFilesOnlyWhenExplicitlyAllowed() {
        assertTrue(StreamUrlPolicy.isAllowed("file:///data/user/0/x/cache/silence.wav", allowLocal = true))
        assertFalse(StreamUrlPolicy.isAllowed("content://x/y", allowLocal = true))
    }
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

- [ ] **Step 3: Implement.** `playback/build.gradle.kts`: add `testImplementation(kotlin("test-junit"))` (plain `kotlin("test")` does not resolve `kotlin.test.Test` in this Android module, per Task 2 CI) (if not yet), `testImplementation(libs.robolectric)`, `testImplementation(libs.androidx.test.core)`; `implementation(libs.media3.datasource)` is not needed (ExoPlayer brings `media3-datasource`; if the compiler cannot see `ResolvingDataSource`, add the catalog alias `media3-datasource = androidx.media3:media3-datasource` and use it). Add `src/test/resources/robolectric.properties` with `sdk=35`.

```kotlin
// PlaybackEngineProvider.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

/** Implemented by the Application. No DI framework in :playback: the service asks the Application for the engine. */
interface PlaybackEngineProvider {
    val playbackEngine: DefaultPlaybackEngine
    /** Only the emulator test app returns true (it serves a local WAV). Production never plays local URLs. */
    val allowLocalStreams: Boolean get() = false
}
```

```kotlin
// StreamUrlPolicy.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import java.net.URI

/** A resolved stream URL comes from parsed remote data; DefaultDataSource would also read file:, content: and asset: URIs, so only https may pass. */
object StreamUrlPolicy {
    fun isAllowed(url: String, allowLocal: Boolean): Boolean {
        val uri = try { URI(url) } catch (_: Exception) { return false }
        return when (uri.scheme?.lowercase()) {
            "https" -> !uri.host.isNullOrEmpty()
            "file" -> allowLocal
            else -> false
        }
    }
}
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

- [ ] **Step 4:** Push; `:playback:test` green for the three new tests. Fix API-name mismatches reported by CI **(verify: `HttpDataSource.InvalidResponseCodeException.responseCode`)**. Expected: PASS.
- [ ] **Step 5: Commit** `feat(playback): trust, stream-url and error-mapping policies`.

---

