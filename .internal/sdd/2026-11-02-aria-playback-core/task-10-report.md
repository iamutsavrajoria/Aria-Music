# Task 10 report

Status: DONE_WITH_CONCERNS (nothing compiled or run; the sandbox cannot run Gradle. CI is the first compile.)

## Implemented
- extraction/: PoTokenSource (+PoTokens), ExtractedAudio, FailedClientMemory, AudioExtractor, ExtractorSession, InnerTubeXAudioExtractor.
- adapter/YouTubeStreamSource rewritten (extractor + memory, https-only, default expiry now+5min); adapter/YouTubeRadioSource added.
- Removed Mapping.bestAudio() and its two MappingTest cases (plus the now-unused `format` helper and imports). Grep: no remaining references.
- AppModule.streams() and PlaybackSmokeTest use YouTubeStreamSource.create(ExtractorSession.createDefault(poTokens = null)).
- provenance.csv row added for innertube/.../extraction (Meld fe9a666, GPL-3.0, adapted).

## Tests written, not run
FailedClientMemoryTest (3), ExtractedAudioTest (3), YouTubeStreamSourceTest (7), YouTubeRadioSourceTest (3), copied verbatim from the brief. No test deps added: they use JUnit only (already declared).

## Deviations from the brief/spike
- ExtractorSession constructor takes `PlayerConfigRepository` (library interface; AriaPlayerConfigRepository implements it). createDefault passes `AriaPlayerConfigRepository(null)`. A RemotePlayerConfigStore is built only if `configRepository.enabled`; otherwise null (disabled mode), per spike.
- Brief contradicts itself on Mutex: 3.2 asks for a bundle rebuild under a Mutex, a later bullet says Mutex is not ported. I kept the Mutex plus generation caching (needed for safe concurrent rebuild); the TTL map is dropped. Provenance note says so.
- withEmbeddedConfigFallback copied verbatim from Meld (spike), as a private member extension in ExtractorSession.kt.
- Spike over brief: `reason` / `Reason.NETWORK` (not "kind"); `expiresAt` converted with toEpochMilliseconds(); `tokenProvider` is null when no PoTokenSource.
- Token provider's getPoToken lets PoTokenSource exceptions propagate (no null mapping), since PoTokenSource returns non-null PoTokens.
- Logger: Timber-backed InnerTubeLogger (Meld's), no Context.
- allowBoundedRange = true (Meld's default when unspecified).
- mimeType null from the library falls back to "audio/webm".

## Concerns
- Unverified by compiler: member-extension `YtConfigParser by this` delegation; named-arg overload resolution of the public InnerTubeExtractor constructor; Kotlin 2.4 `kotlin.time.Instant.toEpochMilliseconds()`; ExperimentalSabrApi warnings (WARNING level only, no -Werror found).
- Disabled mode means the cipher solver uses the library built-in path only; signed-config staging remains unconsumed (Task 9 note).
