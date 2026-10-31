// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.crash

/** Builds the text a user can copy into a GitHub issue. Secrets are redacted before anything is shown. */
object CrashReportFormatter {
    private val QUERY_SECRET = Regex("([?&](?:sig|signature|token|key|cookie|auth|authorization|access_token)=)[^&\\s]+", RegexOption.IGNORE_CASE)
    private val SP_DC = Regex("(sp_dc=)[^;\\s]+", RegexOption.IGNORE_CASE)
    private val BEARER = Regex("(Bearer\\s+)[A-Za-z0-9._~+/=-]+", RegexOption.IGNORE_CASE)

    fun format(t: Throwable, appVersion: String, sdkInt: Int, flavor: String): String {
        val trace = t.stackTraceToString()
        return buildString {
            appendLine("Aria $appVersion, SDK $sdkInt, flavor $flavor")
            appendLine(redact(trace))
        }
    }

    internal fun redact(text: String): String =
        text.replace(QUERY_SECRET, "$1[redacted]")
            .replace(SP_DC, "$1[redacted]")
            .replace(BEARER, "$1[redacted]")
}
