// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.Embedded
import androidx.room.Relation

data class PlaylistSong(
  @Embedded val map: PlaylistSongMap,
  @Relation(
    parentColumn = "songId",
    entityColumn = "id",
    entity = SongEntity::class,
  )
  val song: Song,
)
