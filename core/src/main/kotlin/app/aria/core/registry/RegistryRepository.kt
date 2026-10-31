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
