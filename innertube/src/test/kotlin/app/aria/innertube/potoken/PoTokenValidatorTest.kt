// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.potoken

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoTokenValidatorTest {
    @Test fun acceptsBase64UrlTokens() = assertTrue(PoTokenValidator.isValid("MnQ3_abc-DEF0123456789=="))

    @Test fun rejectsShortLongAndOddCharacters() {
        assertFalse(PoTokenValidator.isValid("short")); assertFalse(PoTokenValidator.isValid("a".repeat(4097)))
        assertFalse(PoTokenValidator.isValid("abc def ghi jkl mno pqr")); assertFalse(PoTokenValidator.isValid("<script>alert(1)</script>"))
    }
}
