// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.fixtures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FixtureFreshnessTest {
    @Test fun youngFixturesPass() = assertNull(FixtureFreshness.check(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 20)))
    @Test fun oldFixturesFailLoudly() {
        val msg = FixtureFreshness.check(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 8))
        assertNotNull(msg); assertEquals(true, msg!!.contains("scripts/capture-innertube-fixtures"))
    }
    @Test fun theBoundaryIsSixtyDays() {
        assertNull(FixtureFreshness.check(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 2)))
        assertNotNull(FixtureFreshness.check(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 3)))
    }
    /** Whatever is committed must be fresh. With no manifest yet (no maintainer capture) this passes; the release gate stays open. */
    @Test fun committedManifestIsFresh() {
        val manifest = java.io.File(FixtureFreshness.DIR, "manifest.json")
        if (!manifest.exists()) { println("No live fixtures captured yet; release gate 'parsers verified against live responses' is open."); return }
        val date = Regex("\"capturedAt\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"").find(manifest.readText())?.groupValues?.get(1)
        assertNotNull("manifest.json has no capturedAt", date)
        assertNull(FixtureFreshness.check(LocalDate.parse(date), LocalDate.now()))
    }
}
