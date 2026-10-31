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
