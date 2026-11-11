### Task 10: Extractor adapter, failed-client memory and radio source

**Files:**
- Create: `innertube/src/main/kotlin/app/aria/innertube/extraction/{PoTokenSource,ExtractedAudio,FailedClientMemory,AudioExtractor,ExtractorSession,InnerTubeXAudioExtractor}.kt`, `innertube/src/main/kotlin/app/aria/innertube/adapter/YouTubeRadioSource.kt`
- Modify: `innertube/src/main/kotlin/app/aria/innertube/adapter/YouTubeStreamSource.kt`, `innertube/src/test/kotlin/app/aria/innertube/smoke/PlaybackSmokeTest.kt`, `app/src/main/kotlin/app/aria/music/di/AppModule.kt` (constructor change only), `docs/provenance.csv`
- Test: `innertube/src/test/kotlin/app/aria/innertube/extraction/{FailedClientMemoryTest,ExtractedAudioTest}.kt`, `innertube/src/test/kotlin/app/aria/innertube/adapter/{YouTubeStreamSourceTest,YouTubeRadioSourceTest}.kt`

**Interfaces:**
- Consumes: Task 9 `AriaPlayerConfigRepository`, `StreamSource`, `RadioSource`, `YouTube.next`, `WatchEndpoint`, `NextResult`, `SongItem.toTrack()`.
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
- `YouTubeStreamSource` rejects (as `StreamUnavailableException`) any extractor URL that is not `https` before it is cached (test: an extractor returning `file:///x` yields a failure).
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
        val excluded = mutableListOf<Set<String>>(); var failure: Throwable? = null; var client = "ANDROID_VR"; var expires: Long? = null; var url: String? = null
        override suspend fun extract(videoId: String, excludeClients: Set<String>): ExtractedAudio {
            excluded += excludeClients; failure?.let { throw it }
            return ExtractedAudio(url ?: "https://x/$videoId", "audio/webm", "opus", 128_000, 10L, expires, 2.0, client)
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
    @Test fun nonHttpsUrlIsRejected() = runBlocking {
        extractor.url = "file:///data/data/app.aria.music/databases/aria.db"
        assertTrue(source.resolve(id).exceptionOrNull() is StreamUnavailableException)
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
        if (!audio.url.startsWith("https://")) throw StreamUnavailableException("Extractor returned a non-https stream URL")
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

3.2 **Extractor wiring (use the declarations copied into `docs/spikes/2026-11-playback-apis.md` item 2; do this after 3.1 is green).** Where the spike lacks something, read, via WebFetch (raw GitHub is blocked in the shell): Meld `app/src/main/kotlin/com/metrolist/music/utils/InnerTubeXPlayer.kt` at commit `fe9a666227ff3d6667075e65a4330518a724524b`, and InnerTubeX v0.5.2 (`MetrolistGroup/innertubex`) classes `extraction/InnerTubeExtractor`, `ExtractedStream`, `StreamResolveException`, `TokenProvider`, `PoTokenResult`, `cipher/YouTubeCipherService`, `cipher/RemotePlayerConfigStore`, `PlayerConfigRepository`. Port **only** the bundle-building and `extract` logic into `ExtractorSession` and `InnerTubeXAudioExtractor` (everything below is what Meld's file does; names marked **(verify)**):
- A cached "bundle" rebuilt when `YouTube.extractionTransport().generation` changes (under a `Mutex`; dispose the old cipher service): `RemotePlayerConfigStore(httpClient, configRepository, logger)` → `YouTubeCipherService(httpClient, remoteStore, logger)`; `YtConfigParserImpl(...)` as recorded in the spike (item 2; `remoteStore` is `RemotePlayerConfigStore?`, pass `null` in disabled mode). `withEmbeddedConfigFallback` is **not** an InnerTubeX API: if the spike's copy of Meld's private extension is needed, add it to `ExtractorSession.kt`; otherwise omit it; `InnerTubeExtractor(configParser, cipherService, innerTube, tokenProvider, logger)`.
- `configRepository` is the Task 9 `AriaPlayerConfigRepository` (never Meld's `AndroidPlayerConfigRepository`; no ZemerTeam URL may appear anywhere in Aria's tree: add `scripts/` grep in Task 14).
- `tokenProvider`: anonymous `TokenProvider` with `capabilities = TokenProviderCapabilities(providers = setOf(PoTokenProviderKind.WEB_BOTGUARD), usesWebView = true)` and `getPoToken(videoId, visitorData, cookie)` delegating to `poTokens?.webClientPoToken(...)` mapped to `PoTokenResult(playerRequestToken, streamingDataToken, visitorData)`; when `poTokens == null` pass `tokenProvider = null`: the public `InnerTubeExtractor` constructor takes `TokenProvider? = null` and substitutes a provider returning null with empty capabilities, so token-requiring clients are skipped (spike item 2).
- `extract`: call `extractor.extract(...)` with HLS and SABR disabled, `excludedClients` (named argument as in the spike), `hints = ContentHints(...).withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = ...)`, audio quality HIGH, a fresh `generateClientPlaybackNonce()`, and `allowBoundedRange` as Meld does; reject a SABR bootstrap result; rethrow `CancellationException`; for `StreamResolveException` whose `reason == StreamResolveException.Reason.NETWORK` rethrow its cause when non-null (transient), for other kinds throw `StreamUnavailableException(message)` (permanent). Map `ExtractedStream` to `ExtractedAudio` using `audioUrl`, `mimeType`, `codecs`, `bitrate ?: 0`, `contentLengthBytes`, `expiresAt?.toEpochMilliseconds()` (`expiresAt` is a `kotlin.time.Instant?`), `loudnessDb ?: perceptualLoudnessDb`, `clientName`.
- Drop from the port: Meld's `AudioQuality` constants, `PlaybackData`, playlist and uploaded-content handling, connectivity-based quality, the Android `Context` requirement (Aria's repository needs none).
- `Mutex`, transport generation caching, `markStreamClientFailed` TTL map: **not** ported; `FailedClientMemory` replaces the TTL map.
- Add the provenance row for `innertube/src/main/kotlin/app/aria/innertube/extraction` (origin Meld commit above, license read from the file header or Meld's LICENSE, kind `adapted`).

3.3 Delete the old `YouTubeStreamSource` body, `bestAudio()` and its two `MappingTest` cases (`bestAudioPicksHighestBitrateWithUrl`, `bestAudioIsNullWhenNoUrls`) once nothing uses them. Update `AppModule.streams()` to `YouTubeStreamSource.create(ExtractorSession.createDefault(poTokens = null))` as a temporary wiring; Task 12 replaces it. Update `PlaybackSmokeTest` to build the source the same way (no PoToken in a JVM test).

- [ ] **Step 4:** Push; fix compile errors against the real InnerTubeX API. Expected: PASS for all unit tests; the smoke test stays skipped in normal CI.
- [ ] **Step 5: Commit** `feat(innertube): extractor-based stream source, failed-client memory, radio source`.

---

