// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.potoken

/** Every token produced by the WebView passes this check before it is cached or returned (ADR-0002). */
object PoTokenValidator {
    private val TOKEN = Regex("^[A-Za-z0-9_\\-+/=]{20,4096}$")

    // matches() requires the whole input to match, so a trailing newline cannot slip past the "$" anchor.
    fun isValid(token: String): Boolean = TOKEN.matches(token)
}
