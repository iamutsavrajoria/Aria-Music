// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.cipher

import com.metrolist.innertubex.cipher.PlayerConfigRepository

object AriaCipherConfig {
    /** Top-level keys the InnerTubeX v0.5.2 remote player-config parser accepts (spike item 1d). */
    val ALLOWED_TOP_LEVEL_KEYS: Set<String> = setOf("schemaVersion", "players")
}

/**
 * Disabled-mode repository: same semantics as `PlayerConfigRepository.disabled()`, so InnerTubeX never makes a
 * network call for player configs and falls back to its built-in solver. [store] holds a verified config that
 * is staged only: InnerTubeX v0.5.2 offers no injection point, so nothing consumes it yet.
 */
class AriaPlayerConfigRepository(private val store: CipherConfigStore?) : PlayerConfigRepository {
    override val enabled: Boolean = false
    override val sourceUrl: String = ""
    override val defaultSourceUrl: String = ""

    override var cachedJson: String = ""
    override var cachedAtMs: Long = 0L
    override var cachedSourceUrl: String = ""
    override var cachedEtag: String = ""

    /** Verified config awaiting a library injection point; never read by InnerTubeX. */
    val stagedConfigJson: String? get() = store?.readConfig()
}
