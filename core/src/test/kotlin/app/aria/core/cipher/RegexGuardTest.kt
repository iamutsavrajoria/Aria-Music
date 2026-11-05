// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.cipher

import org.junit.Assert.*
import org.junit.Test

class RegexGuardTest {
    @Test fun ordinaryPatternsPass() {
        assertTrue(RegexGuard.isSafe("[a-zA-Z0-9_$]{1,8}"))
        assertTrue(RegexGuard.isSafe("""function\s*\(a\)\{[^}]*\}"""))
    }
    @Test fun nestedQuantifiersRejected() {
        assertFalse(RegexGuard.isSafe("(a+)+$")); assertFalse(RegexGuard.isSafe("(.*)*x")); assertFalse(RegexGuard.isSafe("(a|aa)+$"))
    }
    @Test fun invalidAndHugeRejected() { assertFalse(RegexGuard.isSafe("([")); assertFalse(RegexGuard.isSafe("a".repeat(513))) }
}
