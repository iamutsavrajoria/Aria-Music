// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.Embedded
import androidx.room.Relation

data class EventWithSong(
  @Embedded val event: Event,
  @Relation(
    entity = SongEntity::class,
    parentColumn = "songId",
    entityColumn = "id",
  )
  val song: Song,
)
