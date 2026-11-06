// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.potoken

import java.net.URI
import java.net.URISyntaxException

/**
 * What the hidden PoToken WebView may navigate to or request (ADR-0002). The page itself is loaded with
 * loadDataWithBaseURL("https://www.youtube.com", ...) and makes no requests; all network traffic is made by
 * Kotlin, not by the WebView. So the allowed set is `about:blank` plus https on the hosts below.
 */
object NavigationPolicy {
    private val ALLOWED_HOSTS = setOf(
        // Origin of the base-URL document and host of the BotGuard Create/GenerateIT endpoints (docs/spikes/2026-11-playback-apis.md item 4).
        "www.youtube.com",
    )

    fun isAllowed(url: String): Boolean {
        if (url == "about:blank") return true
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return false
        }
        if (uri.scheme != "https") return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host ?: return false
        return host.lowercase() in ALLOWED_HOSTS
    }
}
