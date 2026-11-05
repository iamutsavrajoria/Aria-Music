// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.cipher

import app.aria.core.cipher.CipherConfigVerifier
import app.aria.core.cipher.CipherVerifyResult
import kotlin.coroutines.cancellation.CancellationException

class FetchedConfig(val payload: ByteArray, val signature: ByteArray)

sealed interface UpdateResult {
    data object Disabled : UpdateResult
    data object NoUpdate : UpdateResult
    data class Applied(val version: Long) : UpdateResult
    data class Rejected(val reason: String) : UpdateResult
}

/**
 * Fetches a signed cipher config and stores it only if [verifier] accepts it. Disabled in the alpha: with
 * `enabled = false` or a blank url, [fetch] is never called, so there is no network access.
 */
class CipherConfigUpdater(
    private val enabled: Boolean,
    private val url: String?,
    private val fetch: suspend (String) -> FetchedConfig?,
    private val verifier: CipherConfigVerifier,
    private val store: CipherConfigStore,
) {
    suspend fun updateOnce(): UpdateResult {
        if (!enabled || url.isNullOrBlank()) return UpdateResult.Disabled
        val fetched: FetchedConfig? = try {
            fetch(url)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (fetched == null) return UpdateResult.NoUpdate
        return when (val result = verifier.verify(fetched.payload, fetched.signature)) {
            is CipherVerifyResult.Valid -> {
                store.write(result.configJson, result.version)
                UpdateResult.Applied(result.version)
            }
            is CipherVerifyResult.Invalid -> UpdateResult.Rejected(result.reason)
        }
    }
}
