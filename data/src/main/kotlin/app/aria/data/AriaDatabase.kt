// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.aria.data.entities.TrackMatchEntity

/**
 * Schema version 1. Until the first public build the exported JSON in `schemas/` is regenerated
 * freely; from the first beta on it is frozen and every change needs a tested migration.
 * Release builds must never use destructive migration fallback.
 */
@Database(entities = [TrackMatchEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AriaDatabase : RoomDatabase() {
    abstract fun trackMatchDao(): TrackMatchDao
}
