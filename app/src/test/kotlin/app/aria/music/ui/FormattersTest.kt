// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {
    @Test fun durations() {
        assertEquals("0:00", Formatters.duration(0)); assertEquals("0:07", Formatters.duration(7_400)); assertEquals("3:07", Formatters.duration(187_000))
        assertEquals("1:02:03", Formatters.duration(3_723_000)); assertEquals("0:00", Formatters.duration(-5))
    }
    @Test fun countsFollowTheLocale() {
        assertEquals("1,234", Formatters.count(1_234, Locale.US)); assertEquals("1.234", Formatters.count(1_234, Locale.GERMANY))
    }
}
