// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AriaMigrations {
    /** Adds the Home shelf cache (UI shell, decision 3). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `home_shelf_cache` (`shelfKey` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                    "`payload` TEXT NOT NULL, `fetchedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`shelfKey`))",
            )
        }
    }
}
