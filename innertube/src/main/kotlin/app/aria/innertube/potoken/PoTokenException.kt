// SPDX-License-Identifier: GPL-3.0-only
// Ported from Meld (GPL-3.0) app/src/main/kotlin/com/metrolist/music/utils/potoken/PoTokenException.kt at fe9a666; see docs/provenance.csv.
package app.aria.innertube.potoken

class PoTokenException(message: String) : Exception(message)

// to be thrown if the WebView provided by the system is broken
class BadWebViewException(message: String) : Exception(message)

fun buildExceptionForJsError(error: String): Exception {
    return if (error.contains("SyntaxError"))
        BadWebViewException(error)
    else
        PoTokenException(error)
}
