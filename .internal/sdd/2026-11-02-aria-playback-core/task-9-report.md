# Task 9 report: signed cipher config (updates disabled)

Status: DONE (nothing compiled or run; sandbox cannot run Gradle, CI is the first compile).

## Implemented
- core/cipher: RegexGuard (as in brief), CipherConfigVerifier + CipherVerifyResult (check order: size, signature, parse, version, age, future, top-level keys, recursive walk for depth > 6, string > 2048 incl. keys, unsafe regex under any key containing regex/pattern, applied to every string in that subtree).
- innertube/cipher: CipherConfigStore, FileCipherConfigStore (temp file + atomic move, two files), CipherConfigUpdater (+ FetchedConfig, UpdateResult; CancellationException is rethrown, other fetch errors give NoUpdate), AriaPlayerConfigRepository (+ AriaCipherConfig.ALLOWED_TOP_LEVEL_KEYS = schemaVersion, players, matches spike 1d).
- AriaPlayerConfigRepository members copied from the spike: enabled/sourceUrl/defaultSourceUrl vals (false, "", ""), cachedJson/cachedAtMs/cachedSourceUrl/cachedEtag vars (disabled values); extra `stagedConfigJson` reads store (staged, unused).
- No player_configs.json, no foreign hosts anywhere.

## Tests written, NOT run
CipherConfigVerifierTest (12), RegexGuardTest (3), CipherConfigUpdaterTest (5), FileCipherConfigStoreTest (3), ConfigRepositoryTest (3: disabled values, allowed keys, scan of innertube/src/main for bundled config / raw.github... / zemer strings; uses relative path src/main, Gradle runs unit tests in the module dir).

## Files changed
core/src/main/.../cipher/{CipherConfigVerifier,RegexGuard}.kt; innertube/src/main/.../cipher/{CipherConfigStore,FileCipherConfigStore,CipherConfigUpdater,AriaPlayerConfigRepository}.kt; tests above; innertube/build.gradle.kts (implementation :core, testImplementation coroutines.test and tink.android); docs/provenance.csv (2 original rows); docs/release-gates.md (one gate line incl. the "ignores injected cipher configs" note).

## Deviations
- No `testImplementation(kotlin("test"))`: all tests use org.junit (brief's own code), and kotlin("test") caused resolution trouble earlier.
- Added testImplementation(libs.tink.android) to innertube: tests call Ed25519Sign and :core exposes tink only as implementation.
- Brief did not add kotlinx-serialization to innertube: not needed by main code, skipped.
- Depth convention: config object counts as level 1 (valid test config reaches 4, deep test config 8).
- Regex rule applied to all strings under a regex/pattern key subtree (stricter than brief's single string value); key names are length-checked too.

## Concerns
- First compile is CI. Risks: kotlinx `JsonObject` as @Serializable property type in a private data class; `data object` (Kotlin 2.4, fine).
- ConfigRepositoryTest depends on working dir being module dir.
- innertube exposes core types in a public class signature while using `implementation`; fine until another module consumes the updater.
