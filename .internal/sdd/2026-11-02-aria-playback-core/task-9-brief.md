### Task 9: Signed cipher-config handling (updates disabled)

Spec section 4 "Cipher player-config hosting and trust" (stress-test branch 4, ADR-0002). Ported Meld code fetches player configs from another project's GitHub repository; Aria bundles no config (spike item 1: InnerTubeX v0.5.2 would not read one without fetching from `MetrolistGroup/faraday` hosts; maintainer chose **disabled mode**, so InnerTubeX falls back to its built-in yt-dlp EJS solver and regex parser with no network call). Signed-config verification and storage are built and tested but staged: nothing consumes a stored config until InnerTubeX offers an injection point, and the update path stays **off** until production keys exist.

**Files:**
- Create: `core/src/main/kotlin/app/aria/core/cipher/{CipherConfigVerifier,RegexGuard}.kt`
- Create: `innertube/src/main/kotlin/app/aria/innertube/cipher/{CipherConfigStore,FileCipherConfigStore,CipherConfigUpdater,AriaPlayerConfigRepository}.kt`
- Modify: `innertube/build.gradle.kts` (add `implementation(project(":core"))` for the verifier, `implementation(libs.kotlinx.serialization.json)` and Tink if the cipher store needs them directly, and `testImplementation(kotlin("test"))`, `testImplementation(libs.coroutines.test)`; `ModuleGraphRules` already allows `:innertube` to depend on `:core`), `docs/provenance.csv`, `docs/release-gates.md`
- Test: `core/src/test/kotlin/app/aria/core/cipher/{CipherConfigVerifierTest,RegexGuardTest}.kt`, `innertube/src/test/kotlin/app/aria/innertube/cipher/{CipherConfigUpdaterTest,FileCipherConfigStoreTest}.kt`

**Interfaces:**
- Consumes: Tink `Ed25519Verify` / `Ed25519Sign` (already in `:core`), kotlinx.serialization JSON (already in `:core`), the Task 0 spike findings (`docs/spikes/2026-11-playback-apis.md` items 1 and 2; maintainer decision: no bundled config, disabled mode), InnerTubeX `PlayerConfigRepository` **(already recorded in the spike; if not, read `cipher/PlayerConfigRepository.kt` at tag v0.5.2; Meld's `AndroidPlayerConfigRepository` exposes `enabled`, `sourceUrl`, `defaultSourceUrl` and a cached JSON, so mirror whatever members the interface declares)**.
- Produces:
  - `class CipherConfigVerifier(publicKeys: List<ByteArray>, allowedTopLevelKeys: Set<String>, maxAgeMs: Long, maxFutureSkewMs: Long = 300_000, lastAcceptedVersion: () -> Long, clock: () -> Long)` with `fun verify(payload: ByteArray, signature: ByteArray): CipherVerifyResult` (`Valid(version: Long, configJson: String)` / `Invalid(reason: String)`) and `companion fun signingInput(payload): ByteArray` (context `aria-cipher-config-v1\n`).
  - `object RegexGuard { fun isSafe(pattern: String): Boolean }`.
  - `interface CipherConfigStore { fun readConfig(): String?; fun lastVersion(): Long; fun write(json: String, version: Long) }`, `class FileCipherConfigStore(dir: File)`.
  - `class CipherConfigUpdater(enabled: Boolean, url: String?, fetch: suspend (String) -> FetchedConfig?, verifier: CipherConfigVerifier, store: CipherConfigStore)` with `suspend fun updateOnce(): UpdateResult` (`Disabled`, `NoUpdate`, `Applied(version)`, `Rejected(reason)`); `data class FetchedConfig(val payload: ByteArray, val signature: ByteArray)`.
  - `class AriaPlayerConfigRepository(store: CipherConfigStore?)` implementing InnerTubeX's `PlayerConfigRepository` with the library's `disabled()` semantics (`enabled = false`, empty `sourceUrl`/`defaultSourceUrl`/cache, per the spike's member list), so InnerTubeX's remote store never makes a network call and `getConfig` returns null. The verified stored config (`store?.readConfig()`) is staged only: InnerTubeX v0.5.2 offers no injection point, so nothing consumes it yet.

**Acceptance Criteria:**
- Verifier rejects: bad signature, tampered payload, payload over 256 KB, unknown envelope field, config with a top-level key not in `allowedTopLevelKeys`, version not greater than `lastAcceptedVersion()`, older than `maxAgeMs`, dated more than 5 min in the future, JSON deeper than 6 levels, any string over 2048 chars, any value under a key containing `regex` or `pattern` (case-insensitive) that fails `RegexGuard`.
- `RegexGuard` rejects patterns over 512 chars, invalid patterns, nested quantifiers such as `(a+)+$`, and any pattern that exhausts a 200 000-character-read budget on adversarial inputs; accepts ordinary patterns.
- `CipherConfigUpdater` with `enabled = false` (or a blank url) never calls `fetch`.
- No code path performs a network call to a repository other than an Aria-controlled URL; with updates disabled there is no network call at all.
- No bundled cipher config and no `player_configs.json` exist in the tree; `AriaPlayerConfigRepository` reports `enabled = false`. `AriaCipherConfig.ALLOWED_TOP_LEVEL_KEYS` is the constant set of top-level keys InnerTubeX's `RemotePlayerConfigParser` accepts (spike item 1d: `schemaVersion`, `players`; confirm against the spike's copy).

- [ ] **Step 1: Failing tests (core).**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.cipher

import com.google.crypto.tink.subtle.Ed25519Sign
import org.junit.Assert.*
import org.junit.Test

class CipherConfigVerifierTest {
    private val pair = Ed25519Sign.KeyPair.newKeyPair()
    private val other = Ed25519Sign.KeyPair.newKeyPair()
    private var now = 10_000_000L
    private var lastVersion = 4L
    private val keys = setOf("playerConfigs", "version")

    private fun verifier() = CipherConfigVerifier(listOf(pair.publicKey), keys, maxAgeMs = 7 * 86_400_000L, lastAcceptedVersion = { lastVersion }, clock = { now })
    private fun sign(payload: ByteArray, key: ByteArray = pair.privateKey) =
        Ed25519Sign(key).sign(CipherConfigVerifier.signingInput(payload))
    private fun envelope(version: Long = 5, issued: Long = now, config: String = """{"playerConfigs":{"a":{"regex":"[a-z]+"}}}""") =
        """{"version":$version,"issuedAtEpochMs":$issued,"config":$config}""".toByteArray()
    private fun check(payload: ByteArray, sig: ByteArray = sign(payload)) = verifier().verify(payload, sig)
    private fun assertInvalid(r: CipherVerifyResult, part: String) {
        assertTrue("expected Invalid but was $r", r is CipherVerifyResult.Invalid)
        assertTrue((r as CipherVerifyResult.Invalid).reason, r.reason.contains(part, ignoreCase = true))
    }

    @Test fun validEnvelopePasses() {
        val r = check(envelope()); assertTrue(r.toString(), r is CipherVerifyResult.Valid)
        assertEquals(5L, (r as CipherVerifyResult.Valid).version)
    }
    @Test fun wrongKeyRejected() = assertInvalid(check(envelope(), sign(envelope(), other.privateKey)), "signature")
    @Test fun tamperedPayloadRejected() { val p = envelope(); val s = sign(p); assertInvalid(check(envelope(version = 6), s), "signature") }
    @Test fun oversizedRejected() = assertInvalid(check(ByteArray(256 * 1024 + 1)), "larger")
    @Test fun unknownEnvelopeFieldRejected() =
        assertInvalid(check("""{"version":5,"issuedAtEpochMs":$now,"config":{},"extra":1}""".toByteArray()), "malformed")
    @Test fun unknownTopLevelKeyRejected() = assertInvalid(check(envelope(config = """{"playerConfigs":{},"evil":{}}""")), "key")
    @Test fun rollbackRejected() { assertInvalid(check(envelope(version = 4)), "version"); assertInvalid(check(envelope(version = 2)), "version") }
    @Test fun staleRejected() = assertInvalid(check(envelope(issued = now - 8 * 86_400_000L)), "old")
    @Test fun futureRejected() = assertInvalid(check(envelope(issued = now + 600_000L)), "future")
    @Test fun deepJsonRejected() = assertInvalid(check(envelope(config = """{"playerConfigs":{"a":{"b":{"c":{"d":{"e":{"f":{}}}}}}}}""")), "deep")
    @Test fun longStringRejected() = assertInvalid(check(envelope(config = """{"playerConfigs":{"a":"${"x".repeat(2049)}"}}""")), "long")
    @Test fun unsafeRegexRejected() = assertInvalid(check(envelope(config = """{"playerConfigs":{"a":{"regex":"(a+)+$"}}}""")), "regex")
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.cipher

import org.junit.Assert.*
import org.junit.Test

class RegexGuardTest {
    @Test fun ordinaryPatternsPass() {
        assertTrue(RegexGuard.isSafe("[a-zA-Z0-9_$]{1,8}"))
        assertTrue(RegexGuard.isSafe("""function\s*\(a\)\{[^}]*\}"""))
    }
    @Test fun nestedQuantifiersRejected() {
        assertFalse(RegexGuard.isSafe("(a+)+$")); assertFalse(RegexGuard.isSafe("(.*)*x")); assertFalse(RegexGuard.isSafe("(a|aa)+$"))
    }
    @Test fun invalidAndHugeRejected() { assertFalse(RegexGuard.isSafe("([")); assertFalse(RegexGuard.isSafe("a".repeat(513))) }
}
```

Updater and store tests (innertube, JVM, no Android):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.cipher

import app.aria.core.cipher.CipherConfigVerifier
import com.google.crypto.tink.subtle.Ed25519Sign
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CipherConfigUpdaterTest {
    @get:Rule val tmp = TemporaryFolder()
    private val pair = Ed25519Sign.KeyPair.newKeyPair()
    private val now = 5_000_000L
    private fun store() = FileCipherConfigStore(tmp.newFolder())
    private fun verifier(store: CipherConfigStore) =
        CipherConfigVerifier(listOf(pair.publicKey), setOf("playerConfigs"), 86_400_000L, lastAcceptedVersion = store::lastVersion, clock = { now })
    private fun fetched(version: Long, key: ByteArray = pair.privateKey): FetchedConfig {
        val p = """{"version":$version,"issuedAtEpochMs":$now,"config":{"playerConfigs":{}}}""".toByteArray()
        return FetchedConfig(p, Ed25519Sign(key).sign(CipherConfigVerifier.signingInput(p)))
    }

    @Test fun disabledNeverFetches() = runBlocking {
        var calls = 0; val s = store()
        val u = CipherConfigUpdater(false, "https://example.invalid/c", { calls++; fetched(1) }, verifier(s), s)
        assertEquals(UpdateResult.Disabled, u.updateOnce()); assertEquals(0, calls)
    }
    @Test fun blankUrlNeverFetches() = runBlocking {
        var calls = 0; val s = store()
        assertEquals(UpdateResult.Disabled, CipherConfigUpdater(true, "", { calls++; null }, verifier(s), s).updateOnce()); assertEquals(0, calls)
    }
    @Test fun validSignedConfigIsStored() = runBlocking {
        val s = store()
        val r = CipherConfigUpdater(true, "https://example.invalid/c", { fetched(7) }, verifier(s), s).updateOnce()
        assertEquals(UpdateResult.Applied(7), r); assertEquals(7L, s.lastVersion()); assertNotNull(s.readConfig())
    }
    @Test fun badSignatureLeavesStoreUntouched() = runBlocking {
        val s = store(); val other = Ed25519Sign.KeyPair.newKeyPair()
        val r = CipherConfigUpdater(true, "https://example.invalid/c", { fetched(7, other.privateKey) }, verifier(s), s).updateOnce()
        assertTrue(r is UpdateResult.Rejected); assertEquals(0L, s.lastVersion()); assertNull(s.readConfig())
    }
    @Test fun fetchFailureIsNoUpdate() = runBlocking {
        val s = store()
        assertEquals(UpdateResult.NoUpdate, CipherConfigUpdater(true, "https://example.invalid/c", { null }, verifier(s), s).updateOnce())
    }
}
```

`FileCipherConfigStoreTest`: write `{"a":1}` v3, read it back, `lastVersion() == 3`, a new instance on the same dir sees the same values, `lastVersion()` is 0 when empty.

- [ ] **Step 2:** Push. Expected: FAIL (classes missing).

- [ ] **Step 3: Implement.**

3.1 `RegexGuard.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.cipher

/** Bounds regexes that arrive in signed-but-remote data. Heuristics plus a work budget: a pattern that cannot finish on adversarial input is unsafe. */
object RegexGuard {
    private const val MAX_LENGTH = 512
    private const val BUDGET = 200_000
    private val NESTED_QUANTIFIER = Regex("""[+*}]\)\s*[+*{]""")
    private val QUANTIFIED_ALTERNATION = Regex("""\([^()]*\|[^()]*\)[+*{]""")
    private val ADVERSARIAL = listOf("a".repeat(48) + "!", "ab".repeat(32) + "!", " ".repeat(48) + "x", "0".repeat(64) + "?")

    fun isSafe(pattern: String): Boolean {
        if (pattern.length > MAX_LENGTH) return false
        val regex = try { Regex(pattern) } catch (_: Exception) { return false }
        if (NESTED_QUANTIFIER.containsMatchIn(pattern) || QUANTIFIED_ALTERNATION.containsMatchIn(pattern)) return false
        return ADVERSARIAL.all { input ->
            try { regex.containsMatchIn(Budgeted(input, BUDGET)); true } catch (_: BudgetExceeded) { false }
        }
    }

    private class BudgetExceeded : RuntimeException()

    private class Budgeted(private val base: String, private var left: Int) : CharSequence {
        override val length get() = base.length
        override fun get(index: Int): Char { if (--left < 0) throw BudgetExceeded(); return base[index] }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = Budgeted(base.substring(startIndex, endIndex), left)
        override fun toString() = base
    }
}
```

3.2 `CipherConfigVerifier.kt`: follow `RegistryVerifier` (same package style, `Ed25519Verify`, `GeneralSecurityException`). Envelope:

```kotlin
@Serializable private data class Envelope(val version: Long, val issuedAtEpochMs: Long, val config: JsonObject)
```
decode with `Json { ignoreUnknownKeys = false }`; order of checks: size, signature, parse (reason starts `malformed`), version (`version must be greater than N`), age (`config is N ms old`), future (`dated N ms in the future`), top-level keys (`unexpected key: x`), then a recursive walk (`depth > 6` → "json too deep"; string over 2048 → "string too long"; value under key matching `regex|pattern` that is a string failing `RegexGuard` → "unsafe regex at key"). Reason texts must contain the words the tests check (`signature`, `larger`, `malformed`, `key`, `version`, `old`, `future`, `deep`, `long`, `regex`). `Valid(version, configJson = envelope.config.toString())`. Never put secrets in the config: it is signed, not encrypted, and public.

3.3 innertube classes: `FileCipherConfigStore(dir)` writes `config.json` and `version.txt` via a temp file and atomic rename; `readConfig()` returns null if absent; `lastVersion()` 0 if absent or unreadable. `CipherConfigUpdater.updateOnce()`: `if (!enabled || url.isNullOrBlank()) return Disabled`; `val f = runCatching { fetch(url) }.getOrNull() ?: return NoUpdate`; `when (val r = verifier.verify(f.payload, f.signature)) { is Valid -> { store.write(r.configJson, r.version); Applied(r.version) } is Invalid -> Rejected(r.reason) }`. `AriaPlayerConfigRepository`: implement the InnerTubeX interface found in Step 3.0 below; it exposes `store?.readConfig()` as staged JSON only and keeps every network-related member at its disabled value.

3.4 **No bundled default (maintainer decision, spike P1-a).** Do not add `player_configs.json` or any ZemerTeam file. Define `object AriaCipherConfig { val ALLOWED_TOP_LEVEL_KEYS = setOf("schemaVersion", "players") }` (verify against the spike's `RemotePlayerConfigParser` notes) and a `ConfigRepositoryTest` asserting `enabled == false`, empty URLs, and that no code in `:innertube` references `raw.githubusercontent.com` or `zemer`. Record in `docs/release-gates.md`: "InnerTubeX v0.5.2 ignores injected cipher configs; signed config is staged until a later release."

3.5 `docs/release-gates.md`, "Before the first public build": add `- [ ] Cipher config: generate production Ed25519 key(s), host the signed config at an Aria-controlled URL, embed the public key(s), then enable \`CipherConfigUpdater\` (disabled in the alpha; extractor hot-fixes between app releases need this).`

- [ ] **Step 4:** Push; `:core:test`, `:innertube:testDebugUnitTest`, provenance and license checks green. Expected: PASS.
- [ ] **Step 5: Commit** `feat(innertube,core): signed cipher config with updates disabled`.

---

