// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "artist")
data class ArtistEntity(
  @PrimaryKey val id: String,
  val name: String,
  val thumbnailUrl: String? = null,
  val channelId: String? = null,
  val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
  val bookmarkedAt: LocalDateTime? = null,
  @ColumnInfo(name = "isLocal", defaultValue = false.toString()) val isLocal: Boolean = false
) {
  val isYouTubeArtist: Boolean
    get() = id.startsWith("UC") || id.startsWith("FEmusic_library_privately_owned_artist")

  val isPrivatelyOwnedArtist: Boolean
    get() = id.startsWith("FEmusic_library_privately_owned_artist")

  fun localToggleLike() =
    copy(
      bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now(),
    )

  // Aria change: Echo's toggleLike() also fired YouTube network calls; remote sync belongs to a provider module.
  fun toggleLike() = localToggleLike()

  companion object {
    fun generateArtistId() = "LA" + randomLetters(8)
  }
}
