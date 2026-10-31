// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.Embedded

data class Artist(
  @Embedded val artist: ArtistEntity,
  val songCount: Int,
  val timeListened: Int? = 0,
) : LocalItem() {
  override val id: String
    get() = artist.id

  override val title: String
    get() = artist.name

  override val thumbnailUrl: String?
    get() = artist.thumbnailUrl
}
