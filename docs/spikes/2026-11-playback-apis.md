<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Spike: playback API and license findings

Date: 2026-11-03. Task 0 of the playback-core plan (`.internal/plans/2026-11-02-aria-playback-core.md`). No production code was written and nothing was compiled; declarations below are copied byte for byte from the library sources at the pinned tags or commits (line ranges are given so each block can be re-checked).

## How the sources were read

`WebFetch` was refused for `raw.githubusercontent.com` (client error) and for `github.com` tree pages (`robots_disallowed`), and the shell cannot reach those hosts either. GitHub **git** reads are served by the sandbox's git proxy, so each repository was cloned (shallow, at the exact tag or commit) and the files were read from the checkout. The brief names `developer.android.com` reference pages as an alternative; they were not needed because the Media3 Java source at tag `1.10.1` is authoritative and more precise than the rendered reference.

| Source | URL | Ref read | Commit |
|---|---|---|---|
| InnerTubeX | https://github.com/MetrolistGroup/innertubex/tree/v0.5.2 | tag `v0.5.2` | `93b650b877530738cbc4d2183b4322b77bb9033e` ("chore(release): prepare v0.5.2") |
| zemer-cipher | https://github.com/ZemerTeam/zemer-cipher | `master` at read time | `ec2bcbba2e31f1beecc6f4b12361dd30c53560df` (2026-11-02, "fix(monitor): a side surface blinded only by HTTP 429 ...") |
| Media3 | https://github.com/androidx/media/tree/1.10.1 | tag `1.10.1` | `5fb306449733dd71595700c1227ad6087578c559` ("Bump version to 1.10.1") |
| Meld | https://github.com/FrancescoGrazioso/Meld | commit as pinned by the plan | `fe9a666227ff3d6667075e65a4330518a724524b` ("chore(spotify): update GQL hashes [automated]", 2026-10-28) |

## Summary of conclusions

1. **No BLOCKED conclusion.** The `ZemerTeam/zemer-cipher` license is GPL-3.0 and compatible with Aria's `GPL-3.0-only`.
2. **InnerTubeX v0.5.2 does NOT embed a default player config.** Its only bundled resources are the yt-dlp EJS solver scripts. Item 1 therefore resolves to "bundle ZemerTeam file (license compatible)".
3. **But a bundled config is dead data under the plan's Task 9 design.** InnerTubeX reads `cachedJson` only when `repository.enabled == true` and `sourceUrl` is on an allow-list of `MetrolistGroup/faraday` URLs. The plan's `AriaPlayerConfigRepository` has `enabled = false` and blank URLs, which makes `RemotePlayerConfigStore.getConfig()` return null always. See "Plan changes needed" P1 for the decision this forces.
4. **`withEmbeddedConfigFallback()` is not an InnerTubeX API.** It is a private-to-Meld extension function (copied below) and must be written in Aria.
5. **The extractor accepts a null token provider.** The plan's "return a provider that throws" fallback is unnecessary and worse.
6. Several names in Tasks 9 and 10 differ from the real declarations (`Reason` not "kind", `excludedClients`, `expiresAt` is a `kotlin.time.Instant`). Exact edits are listed at the end.
7. Meld's `potoken/` directory has five files, but the port also needs `app/src/main/assets/po_token.html` (214 lines), which the plan does not list.
8. The InnerTubeX `-only` / `-or-later` variant is **not declared anywhere in the repository**; the evidence points to version 3 only. The release gate is left unticked (see item 1b).

## 1. Cipher config license and embedded default

### 1a. `ZemerTeam/zemer-cipher` license

Source: https://github.com/ZemerTeam/zemer-cipher/blob/ec2bcbba2e31f1beecc6f4b12361dd30c53560df/LICENSE and `README.md` (same commit).

- `LICENSE` is the unmodified FSF text, 674 lines. First lines:

```text
                     GNU GENERAL PUBLIC LICENSE
                       Version 3, 29 June 2007

 Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>
 Everyone is permitted to copy and distribute verbatim copies
 of this license document, but changing it is not allowed.
```

- `README.md` (line 169 onward), verbatim:

```text
## License

GPL-3.0
```

- No SPDX identifiers or copyright headers in any `.kt` or `.kts` file of the repository (searched). The README credits `alltechdev` as the original author of the cipher code and says the BotGuard poToken client "follows BgUtils (MIT License) patterns".
- The license-relevant lines of GPLv3 section 14, present in the LICENSE file (quoted so the variant question is visible):

```text
  14. Revised Versions of this License.

  The Free Software Foundation may publish revised and/or new versions of
the GNU General Public License from time to time.  Such new versions will
be similar in spirit to the present version, but may differ in detail to
address new problems or concerns.

  Each version is given a distinguishing version number.  If the
Program specifies that a certain numbered version of the GNU General
Public License "or any later version" applies to it, you have the
option of following the terms and conditions either of that numbered
version or of any later version published by the Free Software
Foundation.  If the Program does not specify a version number of the
```

- Target file: `library/src/main/assets/player_configs.json` at commit `ec2bcbba2e31f1beecc6f4b12361dd30c53560df`, 55,351 bytes, sha256 `6ea5426b812e3cf09d0507c7b4a58c75cb86698c44c5bf364a297980a776f5d2`, top-level keys `schemaVersion` (1) and `players` (554 entries, shape `"<8-hex hash>": { "sig", "nClass", "sts", "aliases" }`). The repository is a shallow read, so the commit shown is the branch head, not necessarily the last commit that touched this file.
- Staleness: the README says the file is fetched at runtime and pushed to `master` whenever YouTube rotates its player (`player_dates.json` shows rotations as recent as 2026-10-30). A copy bundled at one commit will go stale within weeks.

**Result:** GPL-3.0 (the README does not say `-only` or `-or-later`; both are compatible with Aria's `GPL-3.0-only` for a data file copied into the tree). Not BLOCKED on license.

### 1b. InnerTubeX license variant (completes `docs/spikes/2026-10-innertubex.md` item 1)

Source: https://github.com/MetrolistGroup/innertubex at tag `v0.5.2` (commit `93b650b877530738cbc4d2183b4322b77bb9033e`): `LICENSE`, `README.md`, `build.gradle.kts`, all `.kt` files.

- `LICENSE` is the unmodified FSF GPLv3 text (identical opening to the one quoted above).
- `README.md` line 5: `[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)`.
- `build.gradle.kts` lines 94 to 99, the published POM metadata:

```kotlin
        licenses {
            license {
                name.set("GNU General Public License, version 3")
                url.set("https://www.gnu.org/licenses/gpl-3.0.html")
                distribution.set("repo")
            }
        }
```

- No `.kt` file carries a license header or SPDX identifier (searched: the only hits for "GPL" are the build file and the README badge).
- The words "or any later version" appear only inside the stock GPLv3 text (section 14), never in a notice that applies them to InnerTubeX.

**Result:** the repository never says `-or-later`; the POM says "version 3". The best-supported reading is `GPL-3.0-only`, but the author has not stated it, so it is **not confirmed**. Either variant is compatible with Aria. `docs/release-gates.md` line 17 is therefore left unticked; the controller can tick it if "variant undeclared, POM says version 3" is accepted as the recorded answer.

### 1c. Does InnerTubeX v0.5.2 embed a default player config?

**No.** Evidence at tag `v0.5.2`:

- `src/commonMain/resources/` contains only `yt_ejs/yt.solver.core.min.js` and `yt_ejs/yt.solver.lib.min.js` (the yt-dlp EJS solver). `find src -name '*.json'` finds nothing outside tests.
- `PlayerConfigRepository` (below) has no default-content member; `PlayerConfigRepository.disabled()` returns empty strings.
- `grep -rn "EmbeddedConfigFallback"` finds nothing in the library. The `embedded` hits in `InnerTubeExtractor`/`YtConfigParserImpl` concern YouTube's *embed page* (`/embed/<id>`), not an embedded cipher config.
- The README's own example uses `PlayerConfigRepository.disabled()`, i.e. the library's stated no-remote-config mode, in which `YouTubeCipherService` falls back to the yt-dlp EJS solver and regex parser.

### 1d. How the library actually consumes a config (this decides how a bundled file can be used)

`RemotePlayerConfigStore` (full source in item 2) gates everything on `repository.enabled`:

- `getConfig(playerUrl)`: `if (!repository.enabled) return null`; then `val sourceUrl = configuredUrl() ?: return null`; then `refreshIfStale()`; then the config for the hash, only when `configSourceUrl == sourceUrl`.
- `configuredUrl()` returns non-null only if `repository.sourceUrl` ends in `/player_configs.json` (or equals the legacy faraday release URL, which maps to `defaultSourceUrl`) **and** passes `validatedSourceUrlOrNull`, which accepts only https URLs on these three hosts and prefixes:
  - `raw.githubusercontent.com` with path starting `/MetrolistGroup/faraday/`
  - `cdn.jsdelivr.net` with path starting `/gh/MetrolistGroup/faraday@`
  - `github.com` with path starting `/MetrolistGroup/faraday/releases/download/`
- `ensureLoadedFromCache` parses `repository.cachedJson` only when `repository.cachedSourceUrl == sourceUrl`.
- `refreshIfStale()` skips the network only if `cachedJson` is non-blank, `cachedSourceUrl == sourceUrl` and `now - cachedAtMs < 6 hours`. `forceRefresh` (unknown player hash) and `refreshAfterStreamRejection` fetch from the allowed URL regardless, with 60 s and 5 min cooldowns.
- `GitHubPlayerConfigClient.solve` returns immediately when `!repository.enabled`.
- The ZemerTeam URL in Meld's repository (`https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json`) would be **rejected by `validatedSourceUrlOrNull`** if it were used as `sourceUrl` in v0.5.2: the host/prefix allow-list names only `MetrolistGroup/faraday`. (Meld's `AndroidPlayerConfigRepository` below still sets it; with v0.5.2 that path yields `configuredUrl() == null`, so Meld's remote config is effectively inert in this version.)

The JSON schema InnerTubeX parses (`RemotePlayerConfigParser`, `schemaVersion` 1, `players` object with `sig`, `nClass`, `sts`, `aliases`) is the same schema as the ZemerTeam file.

### 1e. Conclusion for item 1

**"bundle ZemerTeam file (license compatible)"**, with a design caveat that the controller must resolve before Task 9 (plan change P1): the file can only influence extraction if `AriaPlayerConfigRepository` reports `enabled = true` with a faraday-prefixed `sourceUrl`, which re-introduces optional network refreshes to `MetrolistGroup/faraday` hosts. With the plan's `enabled = false` the bundled file is never read. Not BLOCKED.

## 2. InnerTubeX v0.5.2 declarations (Tasks 9 and 10)

All paths below are under `src/commonMain/kotlin/com/metrolist/innertubex/` at tag `v0.5.2`.

### `PlayerConfigRepository` (`cipher/PlayerConfigRepository.kt`, whole file, every member)

```kotlin
package com.metrolist.innertubex.cipher

interface PlayerConfigRepository {
    val enabled: Boolean
    val sourceUrl: String
    val defaultSourceUrl: String

    var cachedJson: String
    var cachedAtMs: Long
    var cachedSourceUrl: String
    var cachedEtag: String

    companion object {
        /** Creates a non-persistent repository with remote config loading disabled. */
        fun disabled(): PlayerConfigRepository =
            object : PlayerConfigRepository {
                override val enabled: Boolean = false
                override val sourceUrl: String = ""
                override val defaultSourceUrl: String = ""
                override var cachedJson: String = ""
                override var cachedAtMs: Long = 0L
                override var cachedSourceUrl: String = ""
                override var cachedEtag: String = ""
            }
    }
}
```

Members: `enabled`, `sourceUrl`, `defaultSourceUrl` (read-only `val`s) and `cachedJson`, `cachedAtMs`, `cachedSourceUrl`, `cachedEtag` (mutable `var`s), plus `companion fun disabled()`. There is no "default JSON" member.

### `RemotePlayerConfigStore` constructor (`cipher/RemotePlayerConfigStore.kt` lines 26 to 30)

```kotlin
class RemotePlayerConfigStore(
    private val httpClient: HttpClient,
    internal val repository: PlayerConfigRepository,
    private val logger: InnerTubeLogger = InnerTubeLogger.NONE,
) {
```

Public/internal members that matter: `suspend fun getSignatureTimestamp(playerUrl: String): Int?` (public); `refreshIfStale()`, `forceRefresh(missingHash)`, `refreshAfterStreamRejection()`, `getConfig(playerUrl)`, `validatedSourceUrlOrNull(value)` are `internal`. The allow-list is in `validatedSourceUrlOrNull` (see 1d).

### `YouTubeCipherService` constructor and lifecycle (`cipher/YouTubeCipherService.kt` lines 30 to 34, 98, 105, 1036)

```kotlin
class YouTubeCipherService(
    private val httpClient: HttpClient,
    private val remotePlayerConfigStore: RemotePlayerConfigStore? = null,
    private val logger: InnerTubeLogger = InnerTubeLogger.NONE,
) {
```

```kotlin
    suspend fun prewarmEjs() = operationMutex.withLock { ejs.ensureLoaded() }
```

```kotlin
    suspend fun refreshAfterStreamRejection(): Boolean = remotePlayerConfigStore?.refreshAfterStreamRejection() ?: false
```

```kotlin
    suspend fun dispose() =
```

The store argument is nullable (default `null`): with `null`, `solveWithZemerConfig` returns an empty result (line 641 of the same file) and the EJS and regex solvers are used.

### `YtConfigParser` and `YtConfigParserImpl` constructor (`extraction/YtConfigParser.kt`, `extraction/YtConfigParserImpl.kt` lines 20 to 25)

```kotlin
package com.metrolist.innertubex.extraction

public interface YtConfigParser {
    /** Sends login cookies only to the validated YouTube watch/embed page. */
    public suspend fun fetchConfig(
        videoId: String,
        useLoginCookies: Boolean = false,
    ): PlayerConfig

    public suspend fun fetchEmbeddedConfig(
        videoId: String,
        useLoginCookies: Boolean = false,
    ): PlayerConfig = fetchConfig(videoId, useLoginCookies)
}
```

```kotlin
public class YtConfigParserImpl(
    private val httpClient: HttpClient,
    private val innerTube: InnerTube,
    private val remotePlayerConfigStore: RemotePlayerConfigStore? = null,
    private val logger: InnerTubeLogger = InnerTubeLogger.NONE,
) : YtConfigParser {
```

`fetchEmbeddedConfig` (YtConfigParserImpl lines 39 to 48) fetches `https://www.youtube.com/embed/$videoId?html5=1` with `Referer: https://www.reddit.com/`.

### `withEmbeddedConfigFallback`: NOT an InnerTubeX declaration

It does not exist in InnerTubeX v0.5.2 (`grep -rn withEmbeddedConfigFallback` over the tag: no matches). It is defined in Meld at `app/src/main/kotlin/com/metrolist/music/utils/InnerTubeXPlayer.kt` lines 269 to 280, commit `fe9a666227ff3d6667075e65a4330518a724524b`, verbatim:

```kotlin
    internal fun YtConfigParser.withEmbeddedConfigFallback(): YtConfigParser =
        object : YtConfigParser by this {
            override suspend fun fetchConfig(
                videoId: String,
                useLoginCookies: Boolean,
            ) =
                try {
                    this@withEmbeddedConfigFallback.fetchConfig(videoId, useLoginCookies)
                } catch (_: IllegalStateException) {
                    this@withEmbeddedConfigFallback.fetchEmbeddedConfig(videoId, useLoginCookies = false)
                }
        }
```

### Reference implementer of `PlayerConfigRepository`: Meld `AndroidPlayerConfigRepository` (`InnerTubeXPlayer.kt` lines 244 to 267, commit `fe9a666227ff3d6667075e65a4330518a724524b`)

```kotlin
    private class AndroidPlayerConfigRepository(context: Context) : PlayerConfigRepository {
        private val preferences = context.getSharedPreferences("innertubex_player_config", Context.MODE_PRIVATE)

        override val enabled: Boolean = true
        override val sourceUrl: String = PLAYER_CONFIG_URL
        override val defaultSourceUrl: String = PLAYER_CONFIG_URL
        override var cachedJson: String
            get() = preferences.getString("json", "").orEmpty()
            set(value) = preferences.edit().putString("json", value).apply()
        override var cachedAtMs: Long
            get() = preferences.getLong("cached_at_ms", 0L)
            set(value) = preferences.edit().putLong("cached_at_ms", value).apply()
        override var cachedSourceUrl: String
            get() = preferences.getString("source_url", "").orEmpty()
            set(value) = preferences.edit().putString("source_url", value).apply()
        override var cachedEtag: String
            get() = preferences.getString("etag", "").orEmpty()
            set(value) = preferences.edit().putString("etag", value).apply()

        private companion object {
            const val PLAYER_CONFIG_URL =
                "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
        }
    }
```

### `InnerTubeExtractor` constructor (`extraction/InnerTubeExtractor.kt` lines 31 to 62)

```kotlin
@OptIn(ExperimentalSabrApi::class)
class InnerTubeExtractor internal constructor(
    private val configParser: YtConfigParser,
    private val clientDirector: PlayerClientDirector,
    private val cipherService: ExtractionCipherService,
    private val innerTube: InnerTube,
    private val tokenProvider: TokenProvider = UnavailableTokenProvider,
    private val logger: InnerTubeLogger = InnerTubeLogger.NONE,
) : StreamExtractor {
    constructor(
        configParser: YtConfigParser,
        cipherService: YouTubeCipherService,
        innerTube: InnerTube,
        fallbackStrategy: ClientFallbackStrategy = ContentAwareFallbackStrategy(),
        tokenProvider: TokenProvider? = null,
        clientHealthMonitor: ClientHealthMonitor = ClientHealthMonitor.NONE,
        logger: InnerTubeLogger = InnerTubeLogger.NONE,
    ) : this(
        configParser = configParser,
        clientDirector =
            PlayerClientDirector(
                innerTube = innerTube,
                fallbackStrategy = fallbackStrategy,
                tokenProvider = tokenProvider ?: UnavailableTokenProvider,
                clientHealthMonitor = clientHealthMonitor,
                logger = logger,
            ),
        cipherService = DefaultExtractionCipherService(cipherService),
        innerTube = innerTube,
        tokenProvider = tokenProvider ?: UnavailableTokenProvider,
        logger = logger,
    )
```

The public constructor is the second one (the primary is `internal`). `tokenProvider` is nullable and defaults to `null`. `ClientFallbackStrategy` and `ContentAwareFallbackStrategy` are in `extraction/strategy/`; `ClientHealthMonitor.NONE` exists (`strategy/ClientHealthTracker.kt` lines 52 and 70).

### `extract(...)` (`extraction/StreamExtractor.kt`, whole file; override at `InnerTubeExtractor.kt` lines 171 to 177)

```kotlin
package com.metrolist.innertubex.extraction

import com.metrolist.innertubex.extraction.AudioQuality

public interface StreamExtractor {
    suspend fun prewarm()

    public suspend fun extract(
        videoId: String,
        hints: ContentHints,
        excludedClients: Set<String> = emptySet(),
        audioQuality: AudioQuality = AudioQuality.AUTO,
        clientPlaybackNonce: String = generateClientPlaybackNonce(),
    ): ExtractedStream?
}
```

```kotlin
    override suspend fun extract(
        videoId: String,
        hints: ContentHints,
        excludedClients: Set<String>,
        audioQuality: AudioQuality,
        clientPlaybackNonce: String,
    ): ExtractedStream? {
```

`AudioQuality` (`extraction/AudioQuality.kt`): `AUTO`, `LOW`, `HIGH`.

### `ContentHints` and stream-capability switches (`extraction/ExtractionModels.kt` lines 8 to 36)

```kotlin
public data class ContentHints(
    val isExplicit: Boolean? = null,
    val isKidsContent: Boolean? = null,
    val isAgeRestricted: Boolean? = null,
    val isLive: Boolean? = null,
    val isUploaded: Boolean? = null,
    val isLocal: Boolean? = null,
    val endpointParams: String? = null,
    val wantVideo: Boolean = false,
    val playbackClientOverrideId: String? = null,
    val sabrFirst: Boolean = false,
    val maxVideoHeight: Int? = null,
) {
    public var allowHls: Boolean = true
        private set
    public var allowSabr: Boolean = true
        private set
    public var allowBoundedRange: Boolean = true
        private set

    public fun withStreamCapabilities(
        allowHls: Boolean = true,
        allowSabr: Boolean = true,
        allowBoundedRange: Boolean = true,
    ): ContentHints =
        copy().also {
            it.allowHls = allowHls
            it.allowSabr = allowSabr
            it.allowBoundedRange = allowBoundedRange
```

### `ExtractedStream` (`extraction/ExtractionModels.kt` lines 130 to 164)

```kotlin
public data class ExtractedStream(
    val videoId: String,
    val audioUrl: String,
    val headers: Map<String, String>,
    val loudnessDb: Double?,
    val expiresAt: Instant?,
    val contentLengthBytes: Long?,
    val itag: Int,
    val mimeType: String?,
    val codecs: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val clientName: String,
    val profileId: String,
    val requireBoundedRange: Boolean,
    val rangeChunkSizeBytes: Long,
    val useRangeChunks: Boolean = false,
    val playbackTracking: PlaybackTrackingData? = null,
    val streamDiagnostics: StreamDiagnostics? = null,
    val videoUrl: String? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val videoMimeType: String? = null,
    val videoCodecs: String? = null,
    val videoBitrate: Int? = null,
    val videoItag: Int? = null,
    val videoContentLengthBytes: Long? = null,
    val sabrBootstrap: SabrBootstrap? = null,
    val sabrVideoBootstrap: SabrBootstrap? = null,
    val availableVideoHeights: List<Int> = emptyList(),
) {
    public var perceptualLoudnessDb: Double? = null
        internal set
    public var mediaMetadata: ExtractedMediaMetadata? = null
        internal set
```

`ExtractedStream` fields Task 10 maps: `audioUrl: String`, `mimeType: String?`, `codecs: String?`, `bitrate: Int?`, `contentLengthBytes: Long?`, `expiresAt: kotlin.time.Instant?` (**not** epoch millis), `loudnessDb: Double?`, `perceptualLoudnessDb: Double?` (public getter, internal setter), `clientName: String`, `headers: Map<String, String>`, `sabrBootstrap: SabrBootstrap?`. `SabrBootstrap` lives in `sabr/SabrModels.kt`, which declares `annotation class ExperimentalSabrApi` at `RequiresOptIn.Level.WARNING` (reading it may warn, not error). `toString()` redacts the URL and headers.

### `StreamResolveException` and its enum (`extraction/ExtractionModels.kt` lines 195 to 212)

```kotlin
public class StreamResolveException(
    public val reason: Reason,
    message: String,
    cause: Throwable? = null,
    public val diagnostics: StreamDiagnostics? = null,
) : IllegalStateException(message, cause) {
    public enum class Reason {
        NO_PLAYABLE_STREAM,
        EXPLICIT_UNSUPPORTED,
        UNAVAILABLE,
        AGE_RESTRICTED,
        NO_MUSIC_VIDEO,
        NETWORK,
        UNKNOWN,
    }
}

```

The property is `reason` and the enum is `StreamResolveException.Reason` (the plan says "kind"). `NETWORK` wraps the original exception in `cause`; `extract` itself wraps any non-`StreamResolveException` exception as `NETWORK` (`InnerTubeExtractor.kt` lines 220 to 235).

### `TokenProvider`, `TokenProviderCapabilities`, `PoTokenResult` (`extraction/TokenProvider.kt`, whole file)

```kotlin
package com.metrolist.innertubex.extraction

import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind

public data class TokenProviderCapabilities(
    val providers: Set<PoTokenProviderKind> = emptySet(),
    val usesWebView: Boolean = false,
)

/** Implementations handle sensitive cookies and attestation tokens; never log their values. */
public interface TokenProvider {
    public val capabilities: TokenProviderCapabilities
        get() = TokenProviderCapabilities()

    /** Cookie and returned token values are sensitive and must not be logged. */
    public suspend fun getPoToken(
        videoId: String,
        visitorData: String,
        cookie: String? = null,
    ): PoTokenResult?

    public suspend fun prewarm(cookie: String? = null) {}

    public suspend fun invalidateAttestation() {}

    public suspend fun close() {}
}

internal object UnavailableTokenProvider : TokenProvider {
    override suspend fun getPoToken(
        videoId: String,
        visitorData: String,
        cookie: String?,
    ): PoTokenResult? = null
}

public data class PoTokenResult(
    val playerRequestToken: String,
    val streamingDataToken: String,
    val visitorData: String,
) {
    override fun toString(): String =
        "PoTokenResult(" +
            "playerRequestToken=${playerRequestToken.presence()}, " +
            "streamingDataToken=${streamingDataToken.presence()}, " +
            "visitorData=${visitorData.presence()})"

    private fun String.presence(): String = if (isBlank()) "missing" else "present"
}
```

### `PoTokenProviderKind` (`extraction/strategy/PlaybackClientManifest.kt` lines 63 to 69)

```kotlin
enum class PoTokenProviderKind {
    WEB_BOTGUARD,
    WEBPAGE_ATTESTATION,
    ANDROID_DROIDGUARD,
    IOS_ATTESTATION,
    EXTERNAL,
}
```

### `generateClientPlaybackNonce` (`extraction/PlaybackNonce.kt` lines 6 to 9)

```kotlin
public fun generateClientPlaybackNonce(): String =
    buildString(16) {
        repeat(16) { append(CPN_ALPHABET[Random.nextInt(CPN_ALPHABET.length)]) }
    }
```

### Null or absent token provider

**Accepted.** The public `InnerTubeExtractor` constructor takes `tokenProvider: TokenProvider? = null` and substitutes `UnavailableTokenProvider` (an `internal object` whose `getPoToken` returns `null`, with default `capabilities = TokenProviderCapabilities()`: empty `providers`, `usesWebView = false`). Consequences, all read in source:

- `extract` skips the PO-token prefetch because `tokenProvider.capabilities.providers.isNotEmpty()` is false (`InnerTubeExtractor.kt` lines 185 to 192; `prewarm()` at line 90 has the same check).
- `PlayerClientDirector` receives `availablePoTokenProviders = emptySet()` and `webViewAvailable = false` (`PlayerClientDirector.kt` lines 81 and 83), so clients whose manifest requires a PO token are not eligible and the strategy uses the token-free clients.
- `prewarm()` does not fetch visitor data for tokens.

So for `poTokens == null` pass `tokenProvider = null` (or omit it). The plan's "provider that throws a clear exception" is not needed and would be harmful: a provider with non-empty `providers` would make the strategy select token-requiring clients and then fail at `getPoToken`.

## 3. Media3 1.10.1 declarations (Tasks 7 and 8)

Source: https://github.com/androidx/media/tree/1.10.1 (commit `5fb306449733dd71595700c1227ad6087578c559`), paths under `libraries/`. `@UnstableApi` is declared as `@RequiresOptIn(level = RequiresOptIn.Level.ERROR)` (`common/src/main/java/androidx/media3/common/util/UnstableApi.java`), so a Kotlin call to an annotated declaration without opt-in is a compile error.

### `MediaSession.Callback.onConnect` (`session/.../MediaSession.java` lines 1596 to 1598)

```java
    default ConnectionResult onConnect(MediaSession session, ControllerInfo controller) {
      return new ConnectionResult.AcceptedResultBuilder(session).build();
    }
```

Not annotated: no opt-in needed to override it.

### `ControllerInfo`, `isTrusted`, `getPackageName`, `getUid` (lines 543, 643, 653, 686 to 689)

```java
  public static final class ControllerInfo {
```

```java
    public String getPackageName() {
```

```java
    public int getUid() {
```

```java
    @UnstableApi
    public boolean isTrusted() {
      return isTrusted;
    }
```

`ControllerInfo` is a stable `public static final class`; `getPackageName()` and `getUid()` are stable; `isTrusted()` is `@UnstableApi`. In Kotlin, `controller.isTrusted` and `controller.packageName` read as properties.

### `MediaSession.isMediaNotificationController` (lines 1027 to 1030)

```java
  @UnstableApi
  public boolean isMediaNotificationController(ControllerInfo controllerInfo) {
    return impl.isMediaNotificationController(controllerInfo);
  }
```

`@UnstableApi`. (`getMediaNotificationControllerInfo()`, lines 1060 to 1062, is also `@UnstableApi`.)

### `ConnectionResult.reject()`, `accept(...)`, `AcceptedResultBuilder` (lines 2091, 2094 to 2095, 2108, 2291 to 2292, 2304, plus `build()`)

```java
  public static final class ConnectionResult {
    @UnstableApi
    public static class AcceptedResultBuilder {
      public AcceptedResultBuilder(MediaSession mediaSession) {
    public static ConnectionResult accept(
        SessionCommands availableSessionCommands, Player.Commands availablePlayerCommands) {
    public static ConnectionResult reject() {
      public ConnectionResult build() {
```

`ConnectionResult` and its `reject()` / `accept(SessionCommands, Player.Commands)` are stable. `AcceptedResultBuilder` (class, constructor `(MediaSession)`, `build()`) is `@UnstableApi`; so are the builder setters (`setAvailableSessionCommands`, `setAvailablePlayerCommands`, `setCustomLayout`, `setMediaButtonPreferences`, `setSessionExtras`, `setSessionActivity`) and the `DEFAULT_*_COMMANDS` constants. The default `onConnect` body is `new ConnectionResult.AcceptedResultBuilder(session).build()` (line 1597).

### `ResolvingDataSource`, `Factory`, `Resolver` (`datasource/.../ResolvingDataSource.java`)

```text
    line 28:@UnstableApi
    line 29:public final class ResolvingDataSource implements DataSource {
    line 32:  public interface Resolver {
    line 45:    DataSpec resolveDataSpec(DataSpec dataSpec) throws IOException;
    line 59:    default Uri resolveReportedUri(Uri uri) {
    line 65:  public static final class Factory implements DataSource.Factory {
    line 75:    public Factory(DataSource.Factory upstreamFactory, Resolver resolver) {
    line 81:    public ResolvingDataSource createDataSource() {
```

The whole class is `@UnstableApi` (line before `public final class ResolvingDataSource`). `Resolver` has one abstract method (`resolveDataSpec(DataSpec) throws IOException`) and one `default` method, so Kotlin SAM conversion works: `ResolvingDataSource.Factory(upstream) { spec -> ... }` compiles as the plan writes it. `DataSpec` is `@UnstableApi` at class level (`DataSpec.java` line 38), which covers `withUri(Uri)` (line 552), so `Media3PlayerOutput` needs the opt-in for it too.

### `DefaultMediaSourceFactory(DataSource.Factory)` (`exoplayer/.../source/DefaultMediaSourceFactory.java` lines 165 to 168)

```java
  @UnstableApi
  public DefaultMediaSourceFactory(DataSource.Factory dataSourceFactory) {
    this(dataSourceFactory, new DefaultExtractorsFactory());
  }
```

`@UnstableApi` on this constructor (the class itself is not). `DefaultDataSource` and `DefaultDataSource.Factory(Context)` are stable (no annotation on the class or the constructor).

### `ForwardingPlayer` (`common/.../ForwardingPlayer.java`)

```java
@UnstableApi
public class ForwardingPlayer implements Player {
```

Class-level `@UnstableApi`. Its Javadoc (lines 31 to 52) says that when removing a command the subclass must also override `isCommandAvailable(int)`, `getAvailableCommands()` and wrap `addListener`; and recommends `ForwardingSimpleBasePlayer` for many customizations. Signatures of the members Task 8 overrides (all `public`, all exist, return `void`):

```java
public void play() {
public void pause() {
public void setPlayWhenReady(boolean playWhenReady) {
public void seekToNext() {
public void seekToNextMediaItem() {
public void seekToPrevious() {
public void seekToPreviousMediaItem() {
public void seekTo(long positionMs) {
public void seekTo(int mediaItemIndex, long positionMs) {
```

Lines in `ForwardingPlayer.java`: `play` 266, `pause` 272, `setPlayWhenReady` 278, `seekTo(long)` 332, `seekTo(int,long)` 338, `seekToPreviousMediaItem` 374, `seekToPrevious` 380, `seekToNextMediaItem` 398, `seekToNext` 404.

Also present and relevant to correctness: `isCommandAvailable(int)` (line 217), `getAvailableCommands()` (line 229), `hasNextMediaItem()` (line 392), `prepare()` (line 235), `setMediaItems(List<MediaItem>, int, long)` (line 127). Kotlin note: overriding `seekTo(Long)` / `seekTo(Int, Long)` and `setPlayWhenReady(Boolean)` matches these Java signatures.

### `MediaMetadata.Builder` setters (`common/.../MediaMetadata.java`)

```java
public Builder setTitle(@Nullable CharSequence title) {
public Builder setArtist(@Nullable CharSequence artist) {
public Builder setArtworkUri(@Nullable Uri artworkUri) {
public Builder setIsPlayable(@Nullable Boolean isPlayable) {
public Builder setIsBrowsable(@Nullable Boolean isBrowsable) {
```

Lines in `MediaMetadata.java`: `setTitle` 143, `setArtist` 150, `setArtworkUri` 270, `setIsBrowsable` 305, `setIsPlayable` 312.

`MediaMetadata` and its `Builder` are stable (no `@UnstableApi` on the class or on these setters). Note `setIsPlayable(Boolean?)` and `setIsBrowsable(Boolean?)` take the boxed type, which Kotlin accepts as `true`/`false`. Also stable: `MediaItem.Builder` (`setMediaId`, `setUri`, `setMediaMetadata`), `ExoPlayer.Builder(Context)`, `setMediaSourceFactory`, `setAudioAttributes(AudioAttributes, boolean)`, `setHandleAudioBecomingNoisy(boolean)`, `setWakeMode(int)`, `MediaSession.Builder(Context, Player)`, `setCallback`, `MediaSessionService`.

### Which of these need `@OptIn(UnstableApi::class)`

| Declaration | Needs opt-in |
|---|---|
| `MediaSession.Callback.onConnect` (override) | no |
| `MediaSession.ControllerInfo.getPackageName()` / `getUid()` | no |
| `MediaSession.ControllerInfo.isTrusted()` | **yes** |
| `MediaSession.isMediaNotificationController(ControllerInfo)` | **yes** |
| `MediaSession.ConnectionResult.reject()`, `accept(...)` | no |
| `MediaSession.ConnectionResult.AcceptedResultBuilder` (and `.build()`) | **yes** |
| `ResolvingDataSource`, `.Factory`, `.Resolver` | **yes** (class-level) |
| `DataSpec` and `DataSpec.withUri` | **yes** (class-level) |
| `DefaultMediaSourceFactory(DataSource.Factory)` | **yes** (constructor) |
| `DefaultDataSource.Factory(Context)` | no |
| `ForwardingPlayer` (and so any subclass) | **yes** (class-level) |
| `MediaMetadata.Builder` setters used by the plan | no |
| `ExoPlayer.Builder` methods used by the plan, `MediaSession.Builder`, `MediaSessionService` | no |

The plan already puts `@UnstableApi` on `Media3PlayerOutput`, `EngineForwardingPlayer` and `AriaPlaybackService`. Annotating the declaration with the marker propagates the requirement to callers, which is correct for classes that expose these types, but any *caller* of those classes in `:app` then also needs `@OptIn(UnstableApi::class)` (or its own `@UnstableApi`). `TrustedCallback.onConnect` (a nested class in `AriaPlaybackService`) is covered because the enclosing declaration carries the marker (this is Kotlin's opt-in propagation rule and is **not compiled here**; CI will confirm).

## 4. Meld `potoken/` files at `fe9a666227ff3d6667075e65a4330518a724524b`

Source: https://github.com/FrancescoGrazioso/Meld/tree/fe9a666227ff3d6667075e65a4330518a724524b/app/src/main/kotlin/com/metrolist/music/utils/potoken

| Path (under `app/src/main/kotlin/com/metrolist/music/utils/potoken/`) | Lines | Header text |
|---|---|---|
| `JavaScriptUtil.kt` | 129 | none; line 1 is `package com.metrolist.music.utils.potoken` |
| `PoTokenException.kt` | 13 | none; line 1 is `package com.metrolist.music.utils.potoken` |
| `PoTokenGenerator.kt` | 157 | none; line 1 is `package com.metrolist.music.utils.potoken` |
| `PoTokenResult.kt` | 6 | none; line 1 is `package com.metrolist.music.utils.potoken` |
| `PoTokenWebView.kt` | 494 | none; line 1 is `package com.metrolist.music.utils.potoken` |

Total 799 lines. **No file has a license header, copyright line or SPDX identifier** (so "keep upstream headers" in plan Task 11 Step 3.2 has nothing to keep; add SPDX only). Meld has no `SPDX` string anywhere under `app/src/main/kotlin`.

**A sixth file is required and the plan omits it:** `app/src/main/assets/po_token.html` (214 lines, last changed in `fe9a666227ff3d6667075e65a4330518a724524b`). `PoTokenWebView.loadHtmlAndObtainBotguard()` reads it with `webView.context.assets.open("po_token.html")`. Its header comment reads:

```text
BotGuard client for generating poTokens.
Updated to fix JavaScript 'this' binding issues and match BgUtils v3.2.0 patterns.
```

`BgUtils` is LuanRT/BgUtils (MIT, per the zemer-cipher README). Meld's README credits do not mention BgUtils or NewPipe; MIT attribution for the BgUtils-derived JS is something Aria's `CREDITS.md` and the ledger row should carry. The origin of Meld's Kotlin WebView generator (its comments mirror NewPipe's `PoTokenWebView`: "Mark init resumed", `buildExceptionForJsError`, `BadWebViewException`) was not traced and is **NOT VERIFIED**.

### Hosts contacted

The JavaScript in `po_token.html` itself contains no URLs and no `fetch`, `XMLHttpRequest`, `importScripts` or `createElement` (searched). It runs Google-supplied code via `new Function(interpreterJavascript)()` where `interpreterJavascript` is the `privateDoNotAccessOrElseSafeScriptWrappedValue` string from the Create response (`po_token.html` lines 94 to 97; the `...TrustedResourceUrlWrappedValue` field is passed through `JavaScriptUtil.parseChallengeData` but not loaded by the page).

All network traffic is made by Kotlin through OkHttp, not by the WebView (`webViewSettings.blockNetworkLoads = true`, `PoTokenWebView.kt` line 77):

| Where | URL / host | Purpose |
|---|---|---|
| `PoTokenWebView.kt` line 169 | `https://www.youtube.com/api/jnn/v1/Create` (POST, OkHttp) | BotGuard challenge |
| `PoTokenWebView.kt` line 210 | `https://www.youtube.com/api/jnn/v1/GenerateIT` (POST, OkHttp) | integrity token |
| `PoTokenWebView.kt` line 156 | `loadDataWithBaseURL("https://www.youtube.com", data, "text/html", "utf-8", null)` | page origin only; no request to this URL is made by the load |
| `PoTokenWebView.kt` line 433 | `webView.loadUrl("about:blank")` | teardown |

So the only host is **`www.youtube.com`**. Request headers (lines 375 to 386; the key is sent at line 381) include a hard-coded API key `x-goog-api-key` (`GOOGLE_API_KEY`, line 444, a Google web API key) and `REQUEST_KEY` (line 445), and a spoofed Chrome 131 desktop `User-Agent`. The OkHttp client uses `.proxy(YouTube.proxy)` (Meld's `YouTube` object; Aria's port must replace this).

**Input for `NavigationPolicy`:** allowed top-level navigations are `about:blank` and the `loadDataWithBaseURL` document whose base is `https://www.youtube.com`; the WebView needs no other host. Any `shouldOverrideUrlLoading`/`shouldInterceptRequest` rule can deny every other scheme and host, and keeping `blockNetworkLoads = true` (as Meld does) is consistent with that. Note the plan's Task 11 hardening says `blockNetworkLoads = false`; Meld's code works with `true`, so the plan's `false` loosens the posture without a need (plan change P6).

## 5. Meld license text relevant to those files and to `InnerTubeXPlayer.kt`

Source: https://github.com/FrancescoGrazioso/Meld/blob/fe9a666227ff3d6667075e65a4330518a724524b/LICENSE and `README.md`.

- `LICENSE` is the unmodified FSF text, "GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007". Opening:

```text
                     GNU GENERAL PUBLIC LICENSE
                       Version 3, 29 June 2007

 Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>
 Everyone is permitted to copy and distribute verbatim copies
```

- `README.md` line 164: "Both Meld and Metrolist are fully open-source projects using the GPL-3.0 license, with the entire source code accessible through GitHub." The README badge reads the license from GitHub.
- Neither `InnerTubeXPlayer.kt` nor any of the five `potoken/` files (nor `po_token.html`) has a header. They are covered only by the repository-level license, which says GPL-3.0 without an "or later" notice; the same "variant not declared, version 3" position as InnerTubeX. A GPL-3.0-only copy into Aria (a `GPL-3.0-only` project) is license-compatible. `InnerTubeXPlayer.kt` has no copyright line of its own, and the `po_token.html` header points at MIT-licensed BgUtils.

## Plan changes needed

Edits are against `.internal/plans/2026-11-02-aria-playback-core.md` (the plan file was not edited by this task). Line numbers are those of the file at the time of reading.

**P1 (decision needed before Task 9; affects Steps 2 and 3.4).** Line 2274, old: ``implementing InnerTubeX's repository: `enabled = false`, `sourceUrl`/`defaultSourceUrl` empty (so InnerTubeX's remote store never makes a network call), content = verified stored config if present, else the bundled default.`` In v0.5.2 that makes the bundled config unreachable (item 1d): `getConfig` returns null whenever `enabled` is false. Choose one:

- **P1-a (recommended, simplest, no license or staleness exposure):** do not bundle the ZemerTeam file. Use `PlayerConfigRepository.disabled()` (the library's documented mode), drop `AriaPlayerConfigRepository`'s bundled-default role and Step 3.4, and let `YouTubeCipherService` use the EJS and regex solvers. Pass `remotePlayerConfigStore = null` (or a store over `disabled()`) in `YouTubeCipherService(...)` and `YtConfigParserImpl(...)`. New text for 2274: "`AriaPlayerConfigRepository` delegates to `PlayerConfigRepository.disabled()` semantics (`enabled = false`, empty URLs, empty cache); signed configs from `CipherConfigStore` are staged but not consumed by InnerTubeX v0.5.2 (it only reads configs through an allow-listed faraday URL). Revisit when InnerTubeX offers an injection point."
- **P1-b:** bundle the ZemerTeam file (GPL-3.0, compatible) and make it usable: `enabled = true`, `sourceUrl = defaultSourceUrl = "https://raw.githubusercontent.com/MetrolistGroup/faraday/main/player_configs.json"` (the URL used in `RemotePlayerConfigStoreTest.kt` line 125; **its content and license were not read here, NOT VERIFIED**), `cachedSourceUrl` equal to it, `cachedJson` = bundled JSON, and accept that stale caches and unknown hashes trigger GETs to the faraday hosts. This contradicts "InnerTubeX's remote store never makes a network call" and needs a new provider record and a privacy-doc line.

Either way, Step 3.4 (a) should read, old: ``(look for `withEmbeddedConfigFallback`, an `assets`/`resources` JSON, or a default in `PlayerConfigRepository`); if it does, use that and skip (b)``, new: "InnerTubeX v0.5.2 embeds no default (verified in `docs/spikes/2026-11-playback-apis.md` item 1c); go to (b) or take P1-a."

**P2 (Task 10, line 2691).** Old: ``YtConfigParserImpl(httpClient, innerTube, remoteStore, logger).withEmbeddedConfigFallback()``. New: add to `ExtractorSession.kt` a private extension copied from Meld (spike item 2) and note that `withEmbeddedConfigFallback` is **not** an InnerTubeX API. `remoteStore` is `RemotePlayerConfigStore?`, so with P1-a pass `null`.

**P3 (Task 10, line 2693).** Old: ``**(verify how the extractor treats a missing token provider; if it requires one, return a provider that throws a clear exception and let the extractor fall back to clients that need no token)**``. New: "When `poTokens == null`, pass `tokenProvider = null`: the public `InnerTubeExtractor` constructor takes `TokenProvider? = null` and substitutes a provider that returns null with empty capabilities, so token-requiring clients are skipped (spike item 2)."

**P4 (Task 10, line 2694).** Old: ``for `StreamResolveException` whose kind is NETWORK rethrow its cause (transient)``. New: ``for `StreamResolveException` whose `reason == StreamResolveException.Reason.NETWORK` rethrow its cause when non-null (transient)``. Old: ``Map `ExtractedStream` to `ExtractedAudio` using ... `expiresAt` (epoch ms)``. New: "... `expiresAt?.toEpochMilliseconds()` (`expiresAt` is `kotlin.time.Instant?`)". Add: the named argument on `InnerTubeExtractor.extract` is `excludedClients` (with a `d`), and `hints` must be built with `ContentHints(...).withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = ...)`; `ExtractedStream.perceptualLoudnessDb` is a property of the stream, readable but not settable. Also add `YouTubeCipherService.dispose()` is `suspend`.

**P5 (Task 11 Step 3.2, line 2796).** Old: ``Read the license and the five files ... keep upstream headers, add SPDX``. New: "the six files: the five `potoken/*.kt` plus `app/src/main/assets/po_token.html` (214 lines) copied to `innertube/src/main/assets/po_token.html` (or the module that owns the WebView); the five Kotlin files carry no upstream header, so add SPDX only; record MIT attribution for LuanRT/BgUtils (named in the HTML header) in `CREDITS.md` and the ledger." Add the HTML file to the Files list at line 2711 and to the provenance row for that directory.

**P6 (Task 11 Step 3.3, hardening list).** Old: ``blockNetworkLoads = false``. New: ``blockNetworkLoads = true`` (Meld does this; all traffic is OkHttp to `www.youtube.com`; the page makes no requests). `NavigationPolicy` should allow only `about:blank` and the `https://www.youtube.com` base-URL document (spike item 4). Also: Meld's `PoTokenWebView` uses `YouTube.proxy` and a hard-coded Google web API key and `REQUEST_KEY`; the port must supply its own proxy hook and record those constants in the provider record.

**P7 (Task 7/8 text and Step 4 `(verify: ...)` list at line 2251).** All of `isTrusted`, `isMediaNotificationController`, `ConnectionResult.reject()`, the `ForwardingPlayer` overrides listed in the plan, and the `ResolvingDataSource.Factory` lambda form match 1.10.1 as written. Replace the `(verify ...)` note with "verified against Media3 1.10.1 (spike item 3)". Opt-in is needed for `isTrusted`, `isMediaNotificationController`, `AcceptedResultBuilder`, `ResolvingDataSource`, `DataSpec`, `DefaultMediaSourceFactory(DataSource.Factory)` and `ForwardingPlayer`; it is not needed for `onConnect`, `reject()` or the `MediaMetadata`/`ExoPlayer` builders. Per the `ForwardingPlayer` Javadoc, consider also overriding `isCommandAvailable`/`getAvailableCommands` if any command is withheld.

**P8 (documentation).** `docs/spikes/2026-10-innertubex.md` item 1 and `docs/providers/innertubex.md` ("License:" bullet) now record the 1b finding (done in this task for the spike; the provider record's line still says "LICENSE text and source headers have not been read" and should be updated by whoever next edits it).

## Not verified

- The content and license of `MetrolistGroup/faraday` (the only host family InnerTubeX v0.5.2 will fetch configs from), and whether its `player_configs.json` is current.
- Whether InnerTubeX at any later tag adds an embedded default or a wider URL allow-list.
- Origin of Meld's Kotlin `PoTokenWebView` beyond what the files say (NewPipe lineage not traced).
- Anything about runtime behavior: nothing was compiled or run.
