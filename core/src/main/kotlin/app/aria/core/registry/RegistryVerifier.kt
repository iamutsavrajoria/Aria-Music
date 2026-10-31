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
