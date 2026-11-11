# Task 7 report

Status: DONE_WITH_CONCERNS (tests written, NOT run; sandbox cannot run Gradle)

Commit: 0a7298e feat(playback): trust, stream-url and error-mapping policies

## Implemented
PlaybackEngineProvider, StreamUrlPolicy, TrustPolicy, PermanentStreamException + PlayerErrorMapper, exactly as in the brief.

## Tests (written, not run)
TrustPolicyTest (5), StreamUrlPolicyTest (3), PlayerErrorMapperTest (3, Robolectric), plus playback/src/test/resources/robolectric.properties (sdk=35, with the same comment as :data).

## Files changed
playback/build.gradle.kts (+libs.robolectric, +libs.androidx.test.core; kotlin("test-junit") already present), the four main files, three test files, robolectric.properties.

## Deviations
- Added `@OptIn(UnstableApi::class)` on PlayerErrorMapper and PlayerErrorMapperTest (spike: HttpDataSource/DataSpec are @UnstableApi; avoids a lint UnsafeOptInUsageError). Not in the brief; no behavior change.
- No media3-datasource catalog alias added: media3-exoplayer and media3-okhttp expose datasource transitively; the compiler is the arbiter. Fallback if CI cannot resolve it: add the alias.
- Spike section 3 has no entry for HttpDataSource.InvalidResponseCodeException, so its 6-arg constructor and `responseCode` field are from the brief and unverified against the spike.

## Concerns
- Unverified by compile: the InvalidResponseCodeException constructor (cause param nullable `IOException?` passed as null) and `responseCode`.
- `URI("not a url")` and `URI("")`: "not a url" throws (rejected); "" parses with null scheme (rejected via else). "https:///nohost" parses with null host (rejected). Reasoned, not run.
