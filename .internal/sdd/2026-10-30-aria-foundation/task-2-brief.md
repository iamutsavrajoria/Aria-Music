### Task 2: `:domain` module (TrackId and interfaces)

**Files:**
- Create: `domain/build.gradle.kts`, `domain/src/main/kotlin/app/aria/domain/TrackId.kt`, `domain/src/main/kotlin/app/aria/domain/Sources.kt`, `domain/src/test/kotlin/app/aria/domain/TrackIdTest.kt`

**Interfaces:**
- Produces:
  - `enum class TrackSource(val prefix: String) { YOUTUBE(""), LOCAL("local:"), PODCAST("pod:") }`
  - `@JvmInline value class TrackId` with `val raw: String`, `val source: TrackSource`, `val value: String`; `TrackId.of(source, value)`, `TrackId.youtube(videoId)`, `TrackId.parse(raw)`
  - `data class Track(id: TrackId, title: String, artists: List<String>, durationMs: Long?, thumbnailUrl: String?)`
  - `data class ResolvedStream(url: String, mimeType: String, bitrate: Int, contentLength: Long?, expiresAtEpochMs: Long?)`
  - `class StreamUnavailableException(message: String) : Exception(message)`
  - `interface SearchSource { suspend fun searchTracks(query: String): Result<List<Track>> }`
  - `interface StreamSource { suspend fun resolve(id: TrackId): Result<ResolvedStream> }`
  - `interface PlayEventRepository { suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long); suspend fun playCount(id: TrackId): Int }`
  - `interface LyricsProvider { val id: String; suspend fun lyrics(track: Track): Result<String?> }`

**Acceptance Criteria:**
- `TrackId.parse("dQw4w9WgXcQ").source == TrackSource.YOUTUBE`; `TrackId.parse("local:abc").source == LOCAL`; round-trip `parse(id.raw) == id` for every source.
- A bare string that is not an 11-character YouTube ID is rejected; an empty prefixed value is rejected.
- `:domain` has no Android or project dependencies.

- [ ] **Step 1: Write `domain/build.gradle.kts`**

```kotlin
plugins { id("org.jetbrains.kotlin.jvm") }

kotlin { jvmToolchain(21) }

tasks.withType<Test> { useJUnit() }

dependencies {
    testImplementation(kotlin("test"))
}
```

- [ ] **Step 2: Write the failing tests**

`domain/src/test/kotlin/app/aria/domain/TrackIdTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TrackIdTest {
    @Test fun bareElevenCharIdIsYoutube() {
        val id = TrackId.parse("dQw4w9WgXcQ")
        assertEquals(TrackSource.YOUTUBE, id.source)
        assertEquals("dQw4w9WgXcQ", id.value)
        assertEquals("dQw4w9WgXcQ", id.raw)
    }

    @Test fun prefixedIdsKeepTheirSource() {
        assertEquals(TrackSource.LOCAL, TrackId.parse("local:abc").source)
        assertEquals("abc", TrackId.parse("local:abc").value)
        assertEquals(TrackSource.PODCAST, TrackId.parse("pod:xyz").source)
    }

    @Test fun roundTripsForEverySource() {
        val ids = listOf(
            TrackId.youtube("dQw4w9WgXcQ"),
            TrackId.of(TrackSource.LOCAL, "file-1"),
            TrackId.of(TrackSource.PODCAST, "ep-9"),
        )
        ids.forEach { assertEquals(it, TrackId.parse(it.raw)) }
    }

    @Test fun rejectsBareStringThatIsNotAYoutubeId() {
        assertFailsWith<IllegalArgumentException> { TrackId.parse("local_track_1") }
        assertFailsWith<IllegalArgumentException> { TrackId.parse("short") }
    }

    @Test fun rejectsEmptyPrefixedValue() {
        assertFailsWith<IllegalArgumentException> { TrackId.parse("local:") }
        assertFailsWith<IllegalArgumentException> { TrackId.of(TrackSource.PODCAST, " ") }
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :domain:test`
Expected: FAIL, compilation error "Unresolved reference: TrackId".

- [ ] **Step 4: Implement**

`domain/src/main/kotlin/app/aria/domain/TrackId.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

enum class TrackSource(val prefix: String) {
    YOUTUBE(""),
    LOCAL("local:"),
    PODCAST("pod:"),
}

/**
 * The only way code builds or reads track IDs. Stored as one string: bare for YouTube (so data
 * keyed by video ID stays compatible with Meld and Echo), prefixed for every other source.
 */
@JvmInline
value class TrackId private constructor(val raw: String) {
    val source: TrackSource
        get() = PREFIXED.firstOrNull { raw.startsWith(it.prefix) } ?: TrackSource.YOUTUBE

    val value: String
        get() = raw.removePrefix(source.prefix)

    override fun toString(): String = raw

    companion object {
        private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")
        private val PREFIXED = TrackSource.entries.filter { it.prefix.isNotEmpty() }

        fun of(source: TrackSource, value: String): TrackId {
            require(value.isNotBlank()) { "TrackId value must not be blank" }
            if (source == TrackSource.YOUTUBE) {
                require(YOUTUBE_ID.matches(value)) { "Not a YouTube video ID: $value" }
            }
            return TrackId(source.prefix + value)
        }

        fun youtube(videoId: String): TrackId = of(TrackSource.YOUTUBE, videoId)

        fun parse(raw: String): TrackId {
            val source = PREFIXED.firstOrNull { raw.startsWith(it.prefix) } ?: TrackSource.YOUTUBE
            return of(source, raw.removePrefix(source.prefix))
        }
    }
}
```

`domain/src/main/kotlin/app/aria/domain/Sources.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

data class Track(
    val id: TrackId,
    val title: String,
    val artists: List<String>,
    val durationMs: Long?,
    val thumbnailUrl: String?,
)

data class ResolvedStream(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val contentLength: Long?,
    val expiresAtEpochMs: Long?,
)

class StreamUnavailableException(message: String) : Exception(message)

interface SearchSource {
    suspend fun searchTracks(query: String): Result<List<Track>>
}

interface StreamSource {
    suspend fun resolve(id: TrackId): Result<ResolvedStream>
}

interface PlayEventRepository {
    suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long)
    suspend fun playCount(id: TrackId): Int
}

interface LyricsProvider {
    val id: String
    suspend fun lyrics(track: Track): Result<String?>
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :domain:test`
Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 6: Commit**

```bash
git add domain && git commit -m "feat(domain): add TrackId, track models and source interfaces"
```

---

