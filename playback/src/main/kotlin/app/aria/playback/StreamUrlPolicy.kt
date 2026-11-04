// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import java.net.URI

/** A resolved stream URL comes from parsed remote data; DefaultDataSource would also read file:, content: and asset: URIs, so only https may pass. */
object StreamUrlPolicy {
    fun isAllowed(url: String, allowLocal: Boolean): Boolean {
        val uri = try { URI(url) } catch (_: Exception) { return false }
        return when (uri.scheme?.lowercase()) {
            "https" -> !uri.host.isNullOrEmpty()
            "file" -> allowLocal
            else -> false
        }
    }
}
