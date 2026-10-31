// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.crash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportFormatterTest {
    private fun report(message: String) =
        CrashReportFormatter.format(IllegalStateException(message), "0.1.0", 34, "foss")

    @Test fun includesVersionSdkFlavorAndExceptionType() {
        val r = report("boom")
        assertTrue(r.contains("Aria 0.1.0"))
        assertTrue(r.contains("SDK 34"))
        assertTrue(r.contains("flavor foss"))
        assertTrue(r.contains("IllegalStateException"))
    }

    @Test fun redactsQueryStringSecrets() {
        val r = report("GET https://x.invalid/a?sig=SECRET123&token=TOP&ok=1 failed")
        assertFalse(r.contains("SECRET123"))
        assertFalse(r.contains("TOP"))
        assertTrue(r.contains("ok=1"))
    }

    @Test fun redactsSpotifyCookieAndBearerTokens() {
        val r = report("sp_dc=AQCsecretcookie; Authorization: Bearer abc.def.ghi")
        assertFalse(r.contains("AQCsecretcookie"))
        assertFalse(r.contains("abc.def.ghi"))
    }
}
