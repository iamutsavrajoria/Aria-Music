### Task 6: `:core` signed registry verification

Implements the spec's registry security: strict schema, Ed25519 signature against embedded keys, max age, last-known-good fallback. Data only, never code.

**Files:**
- Create: `core/build.gradle.kts`, `core/src/main/kotlin/app/aria/core/registry/Registry.kt`, `core/src/main/kotlin/app/aria/core/registry/RegistryVerifier.kt`, `core/src/main/kotlin/app/aria/core/registry/RegistryRepository.kt`, `core/src/test/kotlin/app/aria/core/registry/RegistryVerifierTest.kt`, `core/src/test/kotlin/app/aria/core/registry/RegistryRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class Registry(issuedAtEpochMs: Long, operations: Map<String, HashEntry>, totpSecret: String?, killSwitches: Map<String, Boolean>, youtubeClients: Map<String, String>)`; `@Serializable data class HashEntry(hash: String, previousHash: String?)`
  - `class RegistryVerifier(publicKeys: List<ByteArray>, allowedOperations: Set<String>, maxAgeMs: Long, maxFutureSkewMs: Long = 300_000L, clock: () -> Long)` with `fun verify(payload: ByteArray, signature: ByteArray): VerifyResult` and `companion fun signingInput(payload: ByteArray): ByteArray` (the exact bytes that are signed: the context prefix `aria-registry-v1\n` followed by the payload; CI signing tools and tests must use it)
  - `sealed interface VerifyResult { data class Valid(val registry: Registry); data class Invalid(val reason: String) }`
  - `interface RegistryStore { fun read(): ByteArray?; fun write(bytes: ByteArray) }`
  - `class RegistryRepository(verifier, store, embeddedFallback: Registry?)` with `fun accept(payload: ByteArray, signature: ByteArray): VerifyResult` (also rejects a registry older than the current one) and `fun current(): Registry?`

**Acceptance Criteria:**
- A correctly signed, in-schema, fresh payload is `Valid`. A payload signed by an unknown key, signed without the context prefix, with a tampered byte, older than `maxAgeMs`, dated more than `maxFutureSkewMs` in the future, over 64 KiB, or with a hash that is not 64 lowercase hex characters, an operation name outside the allowlist, or a `youtubeClients` value outside `^[A-Za-z0-9_.-]{1,64}$` is `Invalid` with a reason.
- Either of two embedded keys verifies (key rotation).
- `RegistryRepository.accept` stores only `Valid` payloads; after an `Invalid` result `current()` still returns the previously accepted registry; with nothing stored it returns `embeddedFallback`.
- Anti-rollback: a valid registry with `issuedAtEpochMs` lower than the currently held one (stored, or the embedded fallback when nothing is stored) is rejected; an equal timestamp is accepted.
- The registry is signed, not encrypted, and is published on a public host: it must never contain anything confidential. (`totpSecret` is already public in the community source it is taken from.)

- [ ] **Step 1: Write `core/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.aria.core"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":domain"))
    implementation(libs.tink.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
```
If `kotlinx-serialization-json` has no catalog alias, `grep -n serialization gradle/libs.versions.toml` and use the existing one.

- [ ] **Step 2: Write the failing verifier tests**

`core/src/test/kotlin/app/aria/core/registry/RegistryVerifierTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.registry

import com.google.crypto.tink.subtle.Ed25519Sign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryVerifierTest {
    private val now = 1_000_000_000L
    private val hash64 = "a".repeat(64)
    private val keyA = Ed25519Sign.KeyPair.newKeyPair()
    private val keyB = Ed25519Sign.KeyPair.newKeyPair()
    private val unknown = Ed25519Sign.KeyPair.newKeyPair()

    private fun verifier(maxAge: Long = 86_400_000L) = RegistryVerifier(
        publicKeys = listOf(keyA.publicKey, keyB.publicKey),
        allowedOperations = setOf("fetchPlaylist", "searchDesktop"),
        maxAgeMs = maxAge,
        clock = { now },
    )

    private fun json(
        issuedAt: Long = now,
        hash: String = hash64,
        op: String = "searchDesktop",
        client: String = "ANDROID_MUSIC",
    ) = """{"issuedAtEpochMs":$issuedAt,"operations":{"$op":{"hash":"$hash"}},"killSwitches":{"kugou":true},"youtubeClients":{"music":"$client"}}"""
        .toByteArray()

    private fun sign(payload: ByteArray, key: Ed25519Sign.KeyPair = keyA) =
        Ed25519Sign(key.privateKey).sign(RegistryVerifier.signingInput(payload))

    @Test fun validPayloadVerifies() {
        val p = json()
        val r = verifier().verify(p, sign(p))
        assertTrue(r is VerifyResult.Valid)
        assertEquals(true, (r as VerifyResult.Valid).registry.killSwitches["kugou"])
    }

    @Test fun secondEmbeddedKeyAlsoVerifies() {
        val p = json()
        assertTrue(verifier().verify(p, sign(p, keyB)) is VerifyResult.Valid)
    }

    @Test fun unknownKeyIsRejected() {
        val p = json()
        assertTrue(verifier().verify(p, sign(p, unknown)) is VerifyResult.Invalid)
    }

    @Test fun tamperedPayloadIsRejected() {
        val p = json()
        val sig = sign(p)
        p[p.size - 3] = (p[p.size - 3] + 1).toByte()
        assertTrue(verifier().verify(p, sig) is VerifyResult.Invalid)
    }

    @Test fun signatureOverBarePayloadWithoutContextIsRejected() {
        val p = json()
        val bare = Ed25519Sign(keyA.privateKey).sign(p)
        assertTrue(verifier().verify(p, bare) is VerifyResult.Invalid)
    }

    @Test fun farFutureRegistryIsRejected() {
        val p = json(issuedAt = now + 10 * 60_000L)
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
    }

    @Test fun slightlyFutureRegistryWithinSkewIsAccepted() {
        val p = json(issuedAt = now + 60_000L)
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Valid)
    }

    @Test fun staleRegistryIsRejected() {
        val p = json(issuedAt = now - 90_000_000L)
        assertTrue(verifier(maxAge = 86_400_000L).verify(p, sign(p)) is VerifyResult.Invalid)
    }

    @Test fun badHashIsRejected() {
        val p = json(hash = "XYZ")
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
        val upper = json(hash = "A".repeat(64))
        assertTrue(verifier().verify(upper, sign(upper)) is VerifyResult.Invalid)
    }

    @Test fun operationOutsideAllowlistIsRejected() {
        val p = json(op = "somethingElse")
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
    }

    @Test fun clientConfigMustBePlainData() {
        val p = json(client = "a; rm -rf /")
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
    }

    @Test fun oversizedPayloadIsRejected() {
        val big = ByteArray(65 * 1024) { 'x'.code.toByte() }
        assertTrue(verifier().verify(big, sign(big)) is VerifyResult.Invalid)
    }

    @Test fun malformedJsonIsRejected() {
        val p = "not json".toByteArray()
        assertTrue(verifier().verify(p, sign(p)) is VerifyResult.Invalid)
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :core:testDebugUnitTest --tests '*RegistryVerifierTest'`
Expected: FAIL, unresolved `RegistryVerifier`.

- [ ] **Step 4: Implement model and verifier**

`core/src/main/kotlin/app/aria/core/registry/Registry.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.registry

import kotlinx.serialization.Serializable

@Serializable
data class HashEntry(val hash: String, val previousHash: String? = null)

/** Data only. The registry never carries executable code. */
@Serializable
data class Registry(
    val issuedAtEpochMs: Long,
    val operations: Map<String, HashEntry> = emptyMap(),
    val totpSecret: String? = null,
    val killSwitches: Map<String, Boolean> = emptyMap(),
    val youtubeClients: Map<String, String> = emptyMap(),
)

sealed interface VerifyResult {
    data class Valid(val registry: Registry) : VerifyResult
    data class Invalid(val reason: String) : VerifyResult
}
```

`core/src/main/kotlin/app/aria/core/registry/RegistryVerifier.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.registry

import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.GeneralSecurityException
import kotlinx.serialization.json.Json

class RegistryVerifier(
    private val publicKeys: List<ByteArray>,
    private val allowedOperations: Set<String>,
    private val maxAgeMs: Long,
    private val maxFutureSkewMs: Long = 300_000L,
    private val clock: () -> Long,
) {
    private val json = Json { ignoreUnknownKeys = false }

    fun verify(payload: ByteArray, signature: ByteArray): VerifyResult {
        if (payload.size > MAX_BYTES) return VerifyResult.Invalid("payload larger than $MAX_BYTES bytes")
        if (!signedByAnyKey(payload, signature)) return VerifyResult.Invalid("signature does not match any embedded key")
        val registry = try {
            json.decodeFromString<Registry>(payload.decodeToString())
        } catch (e: Exception) {
            return VerifyResult.Invalid("malformed registry: ${e.javaClass.simpleName}")
        }
        val age = clock() - registry.issuedAtEpochMs
        if (age < -maxFutureSkewMs) return VerifyResult.Invalid("registry is dated ${-age} ms in the future, limit $maxFutureSkewMs")
        if (age > maxAgeMs) return VerifyResult.Invalid("registry is $age ms old, limit $maxAgeMs")
        registry.operations.forEach { (name, entry) ->
            if (name !in allowedOperations) return VerifyResult.Invalid("operation not allowed: $name")
            if (!HASH.matches(entry.hash)) return VerifyResult.Invalid("bad hash for $name")
            if (entry.previousHash != null && !HASH.matches(entry.previousHash)) {
                return VerifyResult.Invalid("bad previous hash for $name")
            }
        }
        registry.youtubeClients.forEach { (key, value) ->
            if (!CLIENT_VALUE.matches(value)) return VerifyResult.Invalid("bad client config value for $key")
        }
        return VerifyResult.Valid(registry)
    }

    private fun signedByAnyKey(payload: ByteArray, signature: ByteArray): Boolean =
        publicKeys.any { key ->
            try {
                Ed25519Verify(key).verify(signature, signingInput(payload))
                true
            } catch (_: GeneralSecurityException) {
                false
            }
        }

    companion object {
        /** The registry is signed, not encrypted, and published on a public host: never put secrets in it. */
        private val CONTEXT = "aria-registry-v1\n".toByteArray()

        /** The exact bytes that are signed. CI signing tools must use this same function. */
        fun signingInput(payload: ByteArray): ByteArray = CONTEXT + payload

        private const val MAX_BYTES = 64 * 1024
        private val HASH = Regex("^[0-9a-f]{64}$")
        private val CLIENT_VALUE = Regex("^[A-Za-z0-9_.-]{1,64}$")
    }
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :core:testDebugUnitTest --tests '*RegistryVerifierTest'`
Expected: PASS, 13 tests.

- [ ] **Step 6: Write the failing repository tests**

`core/src/test/kotlin/app/aria/core/registry/RegistryRepositoryTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.registry

import com.google.crypto.tink.subtle.Ed25519Sign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryRepositoryTest {
    private val now = 1_000L
    private val key = Ed25519Sign.KeyPair.newKeyPair()
    private val verifier = RegistryVerifier(listOf(key.publicKey), setOf("searchDesktop"), 10_000L) { now }

    private class MemStore : RegistryStore {
        var bytes: ByteArray? = null
        override fun read() = bytes
        override fun write(bytes: ByteArray) { this.bytes = bytes }
    }

    private fun payload(issuedAt: Long, killKugou: Boolean) =
        """{"issuedAtEpochMs":$issuedAt,"killSwitches":{"kugou":$killKugou}}""".toByteArray()

    private fun sig(p: ByteArray) = Ed25519Sign(key.privateKey).sign(RegistryVerifier.signingInput(p))

    @Test fun validPayloadIsStoredAndCurrent() {
        val repo = RegistryRepository(verifier, MemStore(), embeddedFallback = null)
        val p = payload(now, killKugou = true)
        assertTrue(repo.accept(p, sig(p)) is VerifyResult.Valid)
        assertEquals(true, repo.current()?.killSwitches?.get("kugou"))
    }

    @Test fun invalidPayloadKeepsLastKnownGood() {
        val repo = RegistryRepository(verifier, MemStore(), embeddedFallback = null)
        val good = payload(now, killKugou = true)
        repo.accept(good, sig(good))
        val bad = payload(now, killKugou = false)
        assertTrue(repo.accept(bad, ByteArray(64)) is VerifyResult.Invalid)
        assertEquals(true, repo.current()?.killSwitches?.get("kugou"))
    }

    @Test fun olderRegistryThanStoredIsRejectedAsRollback() {
        val repo = RegistryRepository(verifier, MemStore(), embeddedFallback = null)
        val newer = payload(now, killKugou = true)
        repo.accept(newer, sig(newer))
        val older = payload(now - 5_000L, killKugou = false)
        assertTrue(repo.accept(older, sig(older)) is VerifyResult.Invalid)
        assertEquals(true, repo.current()?.killSwitches?.get("kugou"))
    }

    @Test fun equalTimestampIsAccepted() {
        val repo = RegistryRepository(verifier, MemStore(), embeddedFallback = null)
        val p = payload(now, killKugou = true)
        repo.accept(p, sig(p))
        assertTrue(repo.accept(p, sig(p)) is VerifyResult.Valid)
    }

    @Test fun registryOlderThanEmbeddedFallbackIsRejected() {
        val embedded = Registry(issuedAtEpochMs = now + 100L, killSwitches = mapOf("kugou" to true))
        val repo = RegistryRepository(verifier, MemStore(), embedded)
        val older = payload(now, killKugou = false)
        assertTrue(repo.accept(older, sig(older)) is VerifyResult.Invalid)
        assertEquals(embedded, repo.current())
    }

    @Test fun nothingStoredFallsBackToEmbedded() {
        val embedded = Registry(issuedAtEpochMs = 1L, killSwitches = mapOf("kugou" to false))
        val repo = RegistryRepository(verifier, MemStore(), embedded)
        assertEquals(embedded, repo.current())
    }

    @Test fun nothingStoredAndNoFallbackIsNull() {
        assertNull(RegistryRepository(verifier, MemStore(), null).current())
    }
}
```

- [ ] **Step 7: Run to verify failure, then implement**

Run: `./gradlew :core:testDebugUnitTest --tests '*RegistryRepositoryTest'`
Expected: FAIL, unresolved `RegistryRepository`.

`core/src/main/kotlin/app/aria/core/registry/RegistryRepository.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.registry

import kotlinx.serialization.json.Json

interface RegistryStore {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
}

/** Keeps the last registry that passed verification; invalid input never replaces it. */
class RegistryRepository(
    private val verifier: RegistryVerifier,
    private val store: RegistryStore,
    private val embeddedFallback: Registry?,
) {
    private val json = Json { ignoreUnknownKeys = false }

    fun accept(payload: ByteArray, signature: ByteArray): VerifyResult {
        val result = verifier.verify(payload, signature)
        if (result !is VerifyResult.Valid) return result
        // Anti-rollback: a replayed older registry could re-enable a killed provider or restore a rotated hash.
        val floor = current()?.issuedAtEpochMs
        if (floor != null && result.registry.issuedAtEpochMs < floor) {
            return VerifyResult.Invalid("registry is older than the current one (rollback)")
        }
        store.write(payload)
        return result
    }

    /** Stored payloads were verified when accepted; they are re-parsed, not re-trusted blindly on age. */
    fun current(): Registry? {
        val stored = store.read()?.let { runCatching { json.decodeFromString<Registry>(it.decodeToString()) }.getOrNull() }
        return stored ?: embeddedFallback
    }
}
```

- [ ] **Step 8: Run to verify pass and commit**

Run: `./gradlew :core:testDebugUnitTest`
Expected: PASS, 20 tests.

```bash
git add -A && git commit -m "feat(core): add signed registry verification with last-known-good fallback"
```

---

