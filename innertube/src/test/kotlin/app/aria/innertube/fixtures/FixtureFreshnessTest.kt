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
    private fun tempDir(): java.io.File = java.nio.file.Files.createTempDirectory("fixtures").toFile()

    @Test fun fixtureFilesWithoutManifestFail() {
        val dir = tempDir()
        java.io.File(dir, "home.json").writeText("{}")
        val msg = FixtureFreshness.checkDir(dir, LocalDate.of(2026, 11, 8))
        assertNotNull("fixtures without manifest.json must fail", msg)
        assertEquals(true, msg!!.contains("manifest.json"))
    }
    @Test fun emptyDirOrMissingDirIsTheOpenGateNotAFailure() {
        assertNull(FixtureFreshness.checkDir(tempDir(), LocalDate.of(2026, 11, 8)))
        assertNull(FixtureFreshness.checkDir(java.io.File(tempDir(), "nope"), LocalDate.of(2026, 11, 8)))
    }
    @Test fun manifestWithoutCapturedAtFails() {
        val dir = tempDir()
        java.io.File(dir, "manifest.json").writeText("{}")
        assertNotNull(FixtureFreshness.checkDir(dir, LocalDate.of(2026, 11, 8)))
    }
    @Test fun staleManifestFails() {
        val dir = tempDir()
        java.io.File(dir, "manifest.json").writeText("{\"capturedAt\": \"2026-01-01\"}")
        assertNotNull(FixtureFreshness.checkDir(dir, LocalDate.of(2026, 11, 8)))
        assertNull(FixtureFreshness.checkDir(dir, LocalDate.of(2026, 2, 1)))
    }

    /** Whatever is committed must be fresh, and fixtures must never exist without a manifest. With neither (no maintainer capture yet) this passes; the release gate stays open. */
    @Test fun committedFixturesAreFresh() {
        val dir = java.io.File(FixtureFreshness.DIR)
        if (!java.io.File(dir, "manifest.json").exists() && dir.listFiles { f -> f.extension == "json" }.isNullOrEmpty()) {
            println("No live fixtures captured yet; release gate 'parsers verified against live responses' is open.")
        }
        assertNull(FixtureFreshness.checkDir(dir, LocalDate.now()))
    }
}
