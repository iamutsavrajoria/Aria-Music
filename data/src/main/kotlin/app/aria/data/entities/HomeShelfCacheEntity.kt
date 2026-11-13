// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One cached remote Home shelf: [payload] is the versioned JSON of a domain `Shelf` (see ShelfJsonCodec). */
@Entity(tableName = "home_shelf_cache")
data class HomeShelfCacheEntity(
    @PrimaryKey val shelfKey: String,
    val position: Int,
    val payload: String,
    val fetchedAtEpochMs: Long,
)
