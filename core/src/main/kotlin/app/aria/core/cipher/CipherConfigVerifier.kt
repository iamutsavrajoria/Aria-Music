// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.cipher

import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.GeneralSecurityException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed interface CipherVerifyResult {
    data class Valid(val version: Long, val configJson: String) : CipherVerifyResult
    data class Invalid(val reason: String) : CipherVerifyResult
}

@Serializable
private data class Envelope(val version: Long, val issuedAtEpochMs: Long, val config: JsonObject)

/**
 * Verifies a signed cipher player-config envelope. The config is signed, not encrypted, and hosted publicly:
 * never put secrets in it.
 */
class CipherConfigVerifier(
    private val publicKeys: List<ByteArray>,
    private val allowedTopLevelKeys: Set<String>,
    private val maxAgeMs: Long,
    private val maxFutureSkewMs: Long = 300_000L,
    private val lastAcceptedVersion: () -> Long,
    private val clock: () -> Long,
) {
    private val json = Json { ignoreUnknownKeys = false }

    fun verify(payload: ByteArray, signature: ByteArray): CipherVerifyResult {
        if (payload.size > MAX_BYTES) return CipherVerifyResult.Invalid("payload larger than $MAX_BYTES bytes")
        if (!signedByAnyKey(payload, signature)) {
            return CipherVerifyResult.Invalid("signature does not match any embedded key")
        }
        val envelope = try {
            json.decodeFromString<Envelope>(payload.decodeToString())
        } catch (e: Exception) {
            return CipherVerifyResult.Invalid("malformed cipher config: ${e.javaClass.simpleName}")
        }
        val last = lastAcceptedVersion()
        if (envelope.version <= last) {
            return CipherVerifyResult.Invalid("version must be greater than $last, got ${envelope.version}")
        }
        val age = clock() - envelope.issuedAtEpochMs
        if (age < -maxFutureSkewMs) {
            return CipherVerifyResult.Invalid("config is dated ${-age} ms in the future, limit $maxFutureSkewMs")
        }
        if (age > maxAgeMs) return CipherVerifyResult.Invalid("config is $age ms old, limit $maxAgeMs")
        envelope.config.keys.forEach { key ->
            if (key !in allowedTopLevelKeys) return CipherVerifyResult.Invalid("unexpected key: $key")
        }
        walk(envelope.config, depth = 1, regexContext = false)?.let { return CipherVerifyResult.Invalid(it) }
        return CipherVerifyResult.Valid(envelope.version, envelope.config.toString())
    }

    /** Returns a rejection reason, or null when the element is acceptable. Recursion is bounded by [MAX_DEPTH]. */
    private fun walk(element: JsonElement, depth: Int, regexContext: Boolean): String? {
        when (element) {
            is JsonObject -> {
                if (depth > MAX_DEPTH) return "json too deep (more than $MAX_DEPTH levels)"
                for ((key, value) in element) {
                    if (key.length > MAX_STRING) return "string too long (key over $MAX_STRING chars)"
                    val keyIsRegex = regexContext || REGEX_KEY.containsMatchIn(key)
                    walk(value, depth + 1, keyIsRegex)?.let { return it }
                }
            }
            is JsonArray -> {
                if (depth > MAX_DEPTH) return "json too deep (more than $MAX_DEPTH levels)"
                for (value in element) walk(value, depth + 1, regexContext)?.let { return it }
            }
            is JsonPrimitive -> {
                if (element.isString) {
                    val text = element.content
                    if (text.length > MAX_STRING) return "string too long (over $MAX_STRING chars)"
                    if (regexContext && !RegexGuard.isSafe(text)) return "unsafe regex at key"
                }
            }
        }
        return null
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
        private val CONTEXT = "aria-cipher-config-v1\n".toByteArray()

        /** The exact bytes that are signed. CI signing tools must use this same function. */
        fun signingInput(payload: ByteArray): ByteArray = CONTEXT + payload

        private const val MAX_BYTES = 256 * 1024
        private const val MAX_DEPTH = 6
        private const val MAX_STRING = 2048
        private val REGEX_KEY = Regex("regex|pattern", RegexOption.IGNORE_CASE)
    }
}
