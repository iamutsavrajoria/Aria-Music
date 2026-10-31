// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

// Aria change: Echo's toYTItem() / fromYTItem() converted to and from InnerTube model types. :data must not
// depend on a provider module, so they are dropped here; the provider module that owns those types supplies
// the conversion (using `type`, which is one of SONG, ALBUM, ARTIST, PLAYLIST).
@Entity(tableName = "speed_dial_item")
data class SpeedDialItem(
  @PrimaryKey val id: String,
  val secondaryId: String? = null,
  val title: String,
  val subtitle: String? = null,
  val thumbnailUrl: String? = null,
  val type: String,
  val explicit: Boolean = false,
  val createDate: Long = System.currentTimeMillis()
)
