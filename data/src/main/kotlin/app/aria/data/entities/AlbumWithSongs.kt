// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class AlbumWithSongs(
  @Embedded val album: AlbumEntity,
  @Relation(
    entity = ArtistEntity::class,
    entityColumn = "id",
    parentColumn = "id",
    associateBy =
      Junction(
        value = AlbumArtistMap::class,
        parentColumn = "albumId",
        entityColumn = "artistId",
      ),
  )
  val artists: List<ArtistEntity>,
  @Relation(
    entity = SongEntity::class,
    entityColumn = "id",
    parentColumn = "id",
    associateBy =
      Junction(
        value = SortedSongAlbumMap::class,
        parentColumn = "albumId",
        entityColumn = "songId",
      ),
  )
  val songs: List<Song>,
)
