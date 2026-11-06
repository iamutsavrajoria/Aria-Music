// SPDX-License-Identifier: GPL-3.0-only
// Ported from Meld (GPL-3.0) app/src/main/kotlin/com/metrolist/music/utils/potoken/PoTokenResult.kt at fe9a666; see docs/provenance.csv.
package app.aria.innertube.potoken

class PoTokenResult(
    val playerRequestPoToken: String,
    val streamingDataPoToken: String,
) {
    // Token values are sensitive: never print them.
    override fun toString(): String = "PoTokenResult(playerRequestPoToken=present, streamingDataPoToken=present)"
}
