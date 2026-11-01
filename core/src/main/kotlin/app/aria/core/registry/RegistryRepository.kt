// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.registry

import kotlinx.serialization.json.Json

interface RegistryStore {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)

    /** Highest `issuedAtEpochMs` ever accepted. Kept apart from the payload so a corrupt or deleted payload cannot lower it. */
    fun readFloor(): Long?
    fun writeFloor(issuedAtEpochMs: Long)
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
        val floor = floor()
        if (floor != null && result.registry.issuedAtEpochMs < floor) {
            return VerifyResult.Invalid("registry is older than the current one (rollback)")
        }
        // Floor first: if we are interrupted between the two writes, the floor is too high rather than too low.
        store.writeFloor(maxOf(floor ?: Long.MIN_VALUE, result.registry.issuedAtEpochMs))
        store.write(payload)
        return result
    }

    /**
     * The newer of the stored registry and the embedded fallback. Stored payloads were verified when accepted;
     * they are re-parsed, not re-trusted blindly on age. An unparseable stored payload is ignored here, but it
     * does not lower the anti-rollback floor (see [floor]).
     */
    fun current(): Registry? {
        val stored = parseStored()
        val embedded = embeddedFallback
        return when {
            stored == null -> embedded
            embedded == null -> stored
            embedded.issuedAtEpochMs > stored.issuedAtEpochMs -> embedded
            else -> stored
        }
    }

    /** The oldest `issuedAtEpochMs` still acceptable: the highest of the recorded floor, the stored payload and the embedded fallback. */
    private fun floor(): Long? =
        listOfNotNull(store.readFloor(), parseStored()?.issuedAtEpochMs, embeddedFallback?.issuedAtEpochMs).maxOrNull()

    private fun parseStored(): Registry? =
        store.read()?.let { runCatching { json.decodeFromString<Registry>(it.decodeToString()) }.getOrNull() }
}
