// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationV2Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AriaDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun v1ToV2KeepsDataAndAddsTheCacheTable() {
        helper.createDatabase("aria-mig", 1).apply {
            execSQL("INSERT INTO song (id, title, duration, liked, totalPlayTime) VALUES ('dQw4w9WgXcQ', 'Kept', 200, 0, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate("aria-mig", 2, true, AriaMigrations.MIGRATION_1_2)
        db.query("SELECT title FROM song WHERE id = 'dQw4w9WgXcQ'").use {
            assertTrue(it.moveToFirst()); assertEquals("Kept", it.getString(0))
        }
        db.query("SELECT COUNT(*) FROM home_shelf_cache").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.execSQL("INSERT INTO home_shelf_cache (shelfKey, position, payload, fetchedAtEpochMs) VALUES ('yt:0', 0, '{}', 1)")
        db.close()
    }

    @Test fun migrationIsNotDestructive() {
        helper.createDatabase("aria-mig2", 1).close()
        val db = helper.runMigrationsAndValidate("aria-mig2", 2, true, AriaMigrations.MIGRATION_1_2)
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'queue_item'").use { assertTrue(it.moveToFirst()) }
        assertFalse(db.query("SELECT name FROM sqlite_master WHERE name = 'nope'").use { it.moveToFirst() })
        db.close()
    }
}
