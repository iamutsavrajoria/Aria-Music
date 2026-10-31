// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class Playlist(
  @Embedded val playlist: PlaylistEntity,
  val songCount: Int,
  @Relation(
    entity = SongEntity::class,
    entityColumn = "id",
    parentColumn = "id",
    projection = ["thumbnailUrl"],
    associateBy =
      Junction(
        value = PlaylistSongMapPreview::class,
        parentColumn = "playlistId",
        entityColumn = "songId",
      ),
  )
  val songThumbnails: List<String?>,
) : LocalItem() {
  override val id: String
    get() = playlist.id

  override val title: String
    get() = playlist.name

  override val thumbnailUrl: String?
    get() = null

  val thumbnails: List<String>
    get() {
      return if (playlist.thumbnailUrl != null) listOf(playlist.thumbnailUrl)
      else songThumbnails.filterNotNull()
    }
}
