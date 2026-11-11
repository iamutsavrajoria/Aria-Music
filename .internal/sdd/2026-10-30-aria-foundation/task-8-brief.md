### Task 8: `:innertube` port and the `SearchSource` / `StreamSource` adapters

Depends on Task 0 (license and store findings). **Do not start this task if Task 0 found InnerTubeX's license incompatible or unknown.**

**Files:**
- Create: `innertube/build.gradle.kts`, `innertube/src/main/kotlin/app/aria/innertube/**` (ported), `innertube/src/main/kotlin/app/aria/innertube/adapter/YouTubeSearchSource.kt`, `innertube/src/main/kotlin/app/aria/innertube/adapter/YouTubeStreamSource.kt`, `innertube/src/main/kotlin/app/aria/innertube/adapter/Mapping.kt`, `innertube/src/test/kotlin/app/aria/innertube/adapter/MappingTest.kt`
- Modify: `docs/provenance.csv`

**Interfaces:**
- Consumes: `Track`, `TrackId`, `ResolvedStream`, `StreamUnavailableException`, `SearchSource`, `StreamSource` (Task 2).
- Produces: `class YouTubeSearchSource : SearchSource`, `class YouTubeStreamSource : StreamSource`, `fun SongItem.toTrack(): Track`, `fun YouTube... PlayerResponse.StreamingData.bestAudio(): Format?`.

**Acceptance Criteria:**
- Ported module compiles under the new package `app.aria.innertube`; the library import `com.metrolist.innertubex` is unchanged.
- `SongItem.toTrack()` maps id → `TrackId.youtube`, artist names, `duration` seconds → milliseconds, thumbnail.
- `bestAudio()` picks the highest-bitrate audio format that has a non-null `url` and returns null when none does; `resolve()` then fails with `StreamUnavailableException`.
- Ledger row recorded for the module (origin Meld, commit `fe9a666227ff3d6667075e65a4330518a724524b`).

- [ ] **Step 1: Port Meld's module with a package rename**

```bash
M=/home/claude/francescograzioso/meld/innertube
D=/home/claude/aria/innertube
mkdir -p $D/src/main/kotlin/app/aria/innertube
cp -r $M/src/main/kotlin/com/metrolist/innertube/. $D/src/main/kotlin/app/aria/innertube/
cp $M/build.gradle.kts $D/build.gradle.kts
# \b keeps com.metrolist.innertubex (the library) untouched
grep -rl "com\.metrolist\.innertube\b" $D | xargs sed -i 's/com\.metrolist\.innertube\b/app.aria.innertube/g'
sed -i 's/namespace = "com.metrolist.innertube"/namespace = "app.aria.innertube"/' $D/build.gradle.kts
grep -rn "com.metrolist.innertubex" $D | wc -l
grep -rn "com\.metrolist\.innertube[^x]" $D | head -3
cd /home/claude/aria && scripts/port-scan.sh innertube/src/main/kotlin
```
Expected: the first count is non-zero (library imports kept); the second command prints nothing; the scan prints `distinct foreign imports: 0 (limit 10)` (a dry run against Meld at the pinned commit gave 0). More than 10 means stop and ask the maintainer.

Then edit `innertube/build.gradle.kts`: Meld's file is already in the explicit template form, so keep its plugins, `android` and `kotlin` blocks; change only the namespace (done by the sed above), add `testOptions { unitTests.isIncludeAndroidResources = true }`, and add `api(project(":domain"))` to `dependencies`. Add `include(":innertube")` to `settings.gradle.kts`.

- [ ] **Step 2: Add SPDX headers and the ledger row**

```bash
for f in $(find $D/src -name '*.kt'); do grep -q -E "SPDX-License-Identifier|Licensed under" "$f" || sed -i '1i // SPDX-License-Identifier: GPL-3.0-only' "$f"; done
printf 'innertube,FrancescoGrazioso/Meld,fe9a666227ff3d6667075e65a4330518a724524b,GPL-3.0,adapted,"Ported module (about 9,700 lines); package renamed; wraps InnerTubeX; parsing remains in-tree"\n' >> /home/claude/aria/docs/provenance.csv
```

- [ ] **Step 3: Compile the port**

Run: `./gradlew :innertube:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Fix unresolved references by reading the errors; anything that referenced Metrolist app code outside `innertube/` is removed or stubbed behind the nearest `:domain` interface.

- [ ] **Step 4: Write the failing mapping tests**

`innertube/src/test/kotlin/app/aria/innertube/adapter/MappingTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.TrackId
import app.aria.innertube.models.Artist
import app.aria.innertube.models.SongItem
import app.aria.innertube.models.response.PlayerResponse.StreamingData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappingTest {
    private fun format(itag: Int, bitrate: Int, url: String?, audio: Boolean = true) = StreamingData.Format(
        itag = itag, url = url, mimeType = "audio/webm; codecs=\"opus\"", bitrate = bitrate,
        width = if (audio) null else 1920, height = if (audio) null else 1080,
        contentLength = 1_000L, quality = "tiny", fps = null, qualityLabel = null, averageBitrate = null,
        audioQuality = "AUDIO_QUALITY_MEDIUM", approxDurationMs = null, audioSampleRate = null,
        audioChannels = null, loudnessDb = null, lastModified = null, signatureCipher = null, cipher = null,
        audioTrack = null,
    )

    @Test fun songItemMapsToTrack() {
        val item = SongItem(
            id = "dQw4w9WgXcQ", title = "Song", artists = listOf(Artist("A", "id1"), Artist("B", null)),
            duration = 213, thumbnail = "https://example.invalid/t.jpg",
        )
        val t = item.toTrack()
        assertEquals(TrackId.youtube("dQw4w9WgXcQ"), t.id)
        assertEquals(listOf("A", "B"), t.artists)
        assertEquals(213_000L, t.durationMs)
        assertEquals("https://example.invalid/t.jpg", t.thumbnailUrl)
    }

    @Test fun missingDurationStaysNull() {
        val item = SongItem(id = "dQw4w9WgXcQ", title = "S", artists = emptyList(), thumbnail = "x")
        assertNull(item.toTrack().durationMs)
    }

    @Test fun bestAudioPicksHighestBitrateWithUrl() {
        val data = StreamingData(
            formats = null, expiresInSeconds = 21_540,
            adaptiveFormats = listOf(
                format(1, 50_000, "https://a"), format(2, 160_000, "https://b"),
                format(3, 320_000, null), format(4, 900_000, "https://video", audio = false),
            ),
        )
        assertEquals(2, data.bestAudio()?.itag)
    }

    @Test fun bestAudioIsNullWhenNoUrls() {
        val data = StreamingData(null, listOf(format(1, 50_000, null)), 21_540)
        assertNull(data.bestAudio())
    }
}
```
Run `grep -n "class SongItem" -A 20 innertube/src/main/kotlin/app/aria/innertube/models/YTItem.kt` first; the constructor above was read from Meld at the pinned commit, so if the port differs, change the test, not the intent.

- [ ] **Step 5: Run to verify failure**

Run: `./gradlew :innertube:testDebugUnitTest --tests '*MappingTest'`
Expected: FAIL, unresolved `toTrack` / `bestAudio`.

- [ ] **Step 6: Implement mapping and adapters**

`innertube/src/main/kotlin/app/aria/innertube/adapter/Mapping.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.Track
import app.aria.domain.TrackId
import app.aria.innertube.models.SongItem
import app.aria.innertube.models.response.PlayerResponse.StreamingData

fun SongItem.toTrack(): Track = Track(
    id = TrackId.youtube(id),
    title = title,
    artists = artists.map { it.name },
    durationMs = duration?.let { it * 1000L },
    thumbnailUrl = thumbnail,
)

/** Highest-bitrate audio-only format that already carries a direct URL. */
fun StreamingData.bestAudio(): StreamingData.Format? =
    adaptiveFormats.filter { it.isAudio && it.url != null }.maxByOrNull { it.bitrate }
```

`innertube/src/main/kotlin/app/aria/innertube/adapter/YouTubeSearchSource.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.SearchSource
import app.aria.domain.Track
import app.aria.innertube.YouTube
import app.aria.innertube.models.SongItem

class YouTubeSearchSource : SearchSource {
    override suspend fun searchTracks(query: String): Result<List<Track>> =
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).map { result ->
            result.items.filterIsInstance<SongItem>().map { it.toTrack() }
        }
}
```

`innertube/src/main/kotlin/app/aria/innertube/adapter/YouTubeStreamSource.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.ResolvedStream
import app.aria.domain.StreamSource
import app.aria.domain.StreamUnavailableException
import app.aria.domain.TrackId
import app.aria.domain.TrackSource
import app.aria.innertube.YouTube
import com.metrolist.innertubex.models.YouTubeClient

/**
 * Skeleton resolver: asks the ANDROID_MUSIC client for the player response and takes a direct audio
 * URL. Formats that need signature deciphering or a PoToken are not handled here; they surface as
 * StreamUnavailableException and are covered later by the playback sub-project.
 */
class YouTubeStreamSource(private val clock: () -> Long = System::currentTimeMillis) : StreamSource {
    override suspend fun resolve(id: TrackId): Result<ResolvedStream> = runCatching {
        require(id.source == TrackSource.YOUTUBE) { "Not a YouTube track: $id" }
        val response = YouTube.player(id.value, client = YouTubeClient.ANDROID_MUSIC).getOrThrow()
        val data = response.streamingData ?: throw StreamUnavailableException("No streaming data for $id")
        val best = data.bestAudio() ?: throw StreamUnavailableException("No direct audio URL for $id")
        ResolvedStream(
            url = best.url!!,
            mimeType = best.mimeType,
            bitrate = best.bitrate,
            contentLength = best.contentLength,
            expiresAtEpochMs = clock() + data.expiresInSeconds * 1000L,
        )
    }
}
```
If `YouTube.player` is declared on a different receiver after the port, or `PlayerResponse.streamingData` has another name, run `grep -n "streamingData" innertube/src/main/kotlin/app/aria/innertube/models/response/PlayerResponse.kt` and adjust the line, not the interface.

- [ ] **Step 7: Run to verify pass and commit**

Run: `./gradlew :innertube:testDebugUnitTest`
Expected: PASS (mapping tests; any ported tests too).

```bash
git add -A && git commit -m "feat(innertube): port Meld module and add SearchSource/StreamSource adapters (provenance recorded)"
```

---

