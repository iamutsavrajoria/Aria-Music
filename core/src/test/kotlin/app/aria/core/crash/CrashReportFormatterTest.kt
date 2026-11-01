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
        // Deny-by-default: the whole query string goes, including parameters not on any list.
        assertFalse(r.contains("ok=1"))
        assertTrue(r.contains("https://x.invalid/a?<redacted>"))
    }

    @Test fun redactsSpotifyCookieAndBearerTokens() {
        val r = report("sp_dc=AQCsecretcookie; Authorization: Bearer abc.def.ghi")
        assertFalse(r.contains("AQCsecretcookie"))
        assertFalse(r.contains("abc.def.ghi"))
    }

    @Test fun redactsLsigPotIpAndVisitorDataInUrls() {
        val r = report("fetch https://x.invalid/p?lsig=LSIGVAL&pot=POTVAL&ip=203.0.113.7&visitorData=VISVAL&expire=1700000000&ei=EIVAL&n=NVAL ok")
        for (v in listOf("LSIGVAL", "POTVAL", "203.0.113.7", "VISVAL", "1700000000", "EIVAL", "NVAL")) assertFalse(v, r.contains(v))
    }

    @Test fun redactsBareParametersOutsideUrls() {
        val r = report("params &lsig=LSIGVAL&pot=POTVAL&ip=203.0.113.7 visitorData=VISVAL; n=NVAL")
        for (v in listOf("LSIGVAL", "POTVAL", "203.0.113.7", "VISVAL", "NVAL")) assertFalse(v, r.contains(v))
        assertTrue(r.contains("lsig=[redacted]"))
    }

    @Test fun doesNotRedactHarmlessWordsContainingParameterNames() {
        val r = report("tokenizer=fine mission=ok")
        assertTrue(r.contains("tokenizer=fine"))
        assertTrue(r.contains("mission=ok"))
    }

    @Test fun redactsGoogleCookies() {
        val r = report(
            "Cookie names: SAPISID=SAPISIDVAL; APISID=APISIDVAL; HSID=HSIDVAL; SSID=SSIDVAL; SID=SIDVAL; " +
                "__Secure-1PSID=P1VAL; __Secure-3PSID=P3VAL; __Secure-1PAPISID=PA1VAL; __Secure-3PAPISID=PA3VAL; " +
                "SIDCC=SIDCCVAL; __Secure-3PSIDCC=PSIDCCVAL; sp_dc=SPDCVAL",
        )
        for (v in listOf("SAPISIDVAL", "APISIDVAL", "HSIDVAL", "SSIDVAL", "SIDVAL", "P1VAL", "P3VAL", "PA1VAL", "PA3VAL", "SIDCCVAL", "PSIDCCVAL", "SPDCVAL")) {
            assertFalse(v, r.contains(v))
        }
    }

    @Test fun redactsWholeCookieHeader() {
        val r = report("Cookie: a=1; custom_session_blob=ZZZ\nnext line")
        assertFalse(r.contains("ZZZ"))
        assertTrue(r.contains("next line"))
    }

    @Test fun redactsSapisidhashAuthorizationHeader() {
        val r = report("Authorization: SAPISIDHASH 1699999999_deadbeefcafe SAPISID1PHASH 1699999999_feedface")
        assertFalse(r.contains("deadbeefcafe"))
        assertFalse(r.contains("feedface"))
    }

    @Test fun redactsAuthorizationHeaderOfAnyScheme() {
        for (h in listOf("Authorization: Basic dXNlcjpwYXNz", "authorization: Bearer abc123", "Authorization: Custom-Scheme tok3n")) {
            val r = report(h)
            assertFalse(h, r.contains("dXNlcjpwYXNz") || r.contains("abc123") || r.contains("tok3n"))
        }
    }

    @Test fun redactsFullGooglevideoUrlInsideExceptionMessage() {
        val url = "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1700000000&ei=EIVAL&ip=203.0.113.7" +
            "&id=o-IDVAL&itag=251&source=youtube&pot=POTVAL&sig=SIGVAL&lsig=LSIGVAL&n=NVAL&c=WEB"
        val r = report("Unable to open $url after 3 retries")
        assertTrue(r.contains("googlevideo.com"))
        assertTrue(r.contains("after 3 retries"))
        for (v in listOf("EIVAL", "203.0.113.7", "o-IDVAL", "POTVAL", "SIGVAL", "LSIGVAL", "NVAL", "1700000000", "videoplayback")) {
            assertFalse(v, r.contains(v))
        }
    }

    @Test fun redactsUrlFragments() {
        val r = report("redirect https://x.invalid/cb#access_token=FRAGSECRET&state=s")
        assertFalse(r.contains("FRAGSECRET"))
    }
}
