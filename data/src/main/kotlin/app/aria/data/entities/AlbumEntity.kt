// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "album")
data class AlbumEntity(
  @PrimaryKey val id: String,
  val playlistId: String? = null,
  val title: String,
  val year: Int? = null,
  val thumbnailUrl: String? = null,
  val themeColor: Int? = null,
  val songCount: Int,
  val duration: Int,
  @ColumnInfo(defaultValue = "0") val explicit: Boolean = false,
  val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
  val bookmarkedAt: LocalDateTime? = null,
  val likedDate: LocalDateTime? = null,
  val inLibrary: LocalDateTime? = null,
  val description: String? = null,
  @ColumnInfo(name = "isLocal", defaultValue = false.toString()) val isLocal: Boolean = false,
  @ColumnInfo(name = "isUploaded", defaultValue = false.toString()) val isUploaded: Boolean = false,
) {
  fun localToggleLike() =
    copy(bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now())

  fun toggleUploaded() = copy(isUploaded = !isUploaded)

  fun toggleLibrary() = copy(inLibrary = if (inLibrary != null) null else LocalDateTime.now())

  // Aria change: Echo's toggleLike() also fired a YouTube network call; remote sync belongs to a provider module.
  fun toggleLike() = localToggleLike()
}
