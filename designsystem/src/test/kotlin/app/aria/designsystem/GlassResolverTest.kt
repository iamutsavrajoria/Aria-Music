// SPDX-License-Identifier: GPL-3.0-only
package app.aria.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassResolverTest {
    @Test fun neverEnabledBelowApi31() {
        GlassMode.entries.forEach { mode ->
            listOf(26, 30).forEach { sdk -> assertEquals("$mode@$sdk", false, GlassResolver.resolve(mode, sdk, false)) }
        }
    }

    @Test fun onIsEnabledOnApi31Plus() {
        assertEquals(true, GlassResolver.resolve(GlassMode.ON, 31, batterySaver = true))
        assertEquals(true, GlassResolver.resolve(GlassMode.ON, 36, batterySaver = false))
    }

    @Test fun autoFollowsSupportAndBatterySaver() {
        assertEquals(true, GlassResolver.resolve(GlassMode.AUTO, 33, batterySaver = false))
        assertEquals(false, GlassResolver.resolve(GlassMode.AUTO, 33, batterySaver = true))
    }

    @Test fun offIsAlwaysOff() {
        assertEquals(false, GlassResolver.resolve(GlassMode.OFF, 36, batterySaver = false))
    }

    @Test fun supportThresholdIsApi31() {
        assertEquals(false, GlassResolver.isSupported(30))
        assertEquals(true, GlassResolver.isSupported(31))
    }
}
