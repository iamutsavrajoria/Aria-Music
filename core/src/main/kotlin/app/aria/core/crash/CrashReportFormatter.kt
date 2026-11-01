// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.crash

/**
 * Builds the text a user can copy into a GitHub issue. Secrets are redacted before anything is shown.
 *
 * Redaction is deny-by-default for URLs: the whole query string and fragment of any http(s) URL is replaced,
 * because stream URLs carry signed, account- and device-bound values (sig, lsig, pot, ip, ei, visitorData ...)
 * and a deny-list of parameter names would miss new ones. Text that is not a URL is additionally scrubbed by
 * name for the parameters, cookies and headers we know about.
 */
object CrashReportFormatter {
    private const val REDACTED = "[redacted]"

    // Whole header lines whose value is never safe to show, whatever the scheme (Bearer, Basic, SAPISIDHASH ...).
    private val SECRET_HEADER = Regex(
        "((?:proxy-)?authorization|(?:set-)?cookie|x-goog-visitor-id|x-goog-api-key|x-youtube-identity-token|x-goog-authuser)(\\s*[:=]\\s*)[^\\r\\n]+",
        RegexOption.IGNORE_CASE,
    )

    private val URL = Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)

    // Query-style parameters outside URLs (log lines, "&sig=..." fragments, form bodies).
    private val PARAM_NAMES = listOf(
        "sig", "lsig", "signature", "token", "access_token", "refresh_token", "id_token", "key", "api_key", "apikey",
        "pot", "potoken", "po_token", "ip", "ipbits", "n", "visitordata", "visitor_id", "expire", "ei", "auth",
        "authorization", "cookie", "cookies", "session", "sessionid", "session_id", "secret", "client_secret",
        "password", "passwd", "pwd", "client", "sapisid", "totp", "code", "sp_dc",
    )
    private val QUERY_SECRET = Regex(
        "(?<![A-Za-z0-9_])((?:${PARAM_NAMES.joinToString("|")})=)[^&;\\s,\"']*",
        RegexOption.IGNORE_CASE,
    )

    // Cookie and credential pairs, by name.
    private val COOKIE_NAMES = listOf(
        "sp_dc", "sp_key", "sp_t", "SAPISID", "APISID", "HSID", "SSID", "SID", "SIDCC", "LOGIN_INFO",
        "SAPISIDHASH", "SAPISID1PHASH", "SAPISID3PHASH", "VISITOR_INFO1_LIVE", "VISITOR_PRIVACY_METADATA", "YSC",
    )
    private val COOKIE_PAIR = Regex(
        "(?<![A-Za-z0-9_-])((?:${COOKIE_NAMES.joinToString("|")}|__Secure-[A-Za-z0-9_-]+|__Host-[A-Za-z0-9_-]+)=)[^;\\s,\"']*",
        RegexOption.IGNORE_CASE,
    )

    // SAPISIDHASH-style schemes: "SAPISIDHASH 1699_abcdef" without a leading Authorization header.
    private val HASH_SCHEME = Regex("(SAPISID(?:1P|3P)?HASH\\s+)[^\\s,;]+", RegexOption.IGNORE_CASE)
    private val BEARER = Regex("((?:Bearer|Basic)\\s+)[A-Za-z0-9._~+/=-]+", RegexOption.IGNORE_CASE)

    fun format(t: Throwable, appVersion: String, sdkInt: Int, flavor: String): String {
        val trace = t.stackTraceToString()
        return buildString {
            appendLine("Aria $appVersion, SDK $sdkInt, flavor $flavor")
            appendLine(redact(trace))
        }
    }

    internal fun redact(text: String): String =
        text.replace(SECRET_HEADER) { it.groupValues[1] + it.groupValues[2] + REDACTED }
            .replace(URL) { redactUrl(it.value) }
            .replace(HASH_SCHEME, "$1$REDACTED")
            .replace(BEARER, "$1$REDACTED")
            .replace(COOKIE_PAIR, "$1$REDACTED")
            .replace(QUERY_SECRET, "$1$REDACTED")

    private fun redactUrl(url: String): String {
        val cut = url.indexOfFirst { it == '?' || it == '#' }
        // Signed stream hosts can carry secrets in the path too; keep only the host.
        val hostEnd = url.indexOf('/', url.indexOf("://") + 3).let { if (it < 0) url.length else it }
        if (url.substring(0, hostEnd).contains("googlevideo.com", ignoreCase = true) && hostEnd < url.length) {
            return url.substring(0, hostEnd) + "/<redacted>"
        }
        if (cut < 0) return url
        val base = url.substring(0, cut)
        return if (url[cut] == '?') "$base?<redacted>" else "$base#<redacted>"
    }
}
