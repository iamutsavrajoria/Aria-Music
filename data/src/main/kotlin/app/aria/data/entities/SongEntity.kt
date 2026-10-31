// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "song", indices = [Index(value = ["albumId"])])
data class SongEntity(
  @PrimaryKey val id: String,
  val title: String,
  val duration: Int = -1,
  val thumbnailUrl: String? = null,
  val albumId: String? = null,
  val albumName: String? = null,
  @ColumnInfo(defaultValue = "0") val explicit: Boolean = false,
  val year: Int? = null,
  val date: LocalDateTime? = null,
  val dateModified: LocalDateTime? = null,
  val liked: Boolean = false,
  val likedDate: LocalDateTime? = null,
  val totalPlayTime: Long = 0,
  val inLibrary: LocalDateTime? = null,
  val dateDownload: LocalDateTime? = null,
  @ColumnInfo(name = "isLocal", defaultValue = false.toString()) val isLocal: Boolean = false,
  val libraryAddToken: String? = null,
  val libraryRemoveToken: String? = null,
  @ColumnInfo(defaultValue = "0") val lyricsOffset: Int = 0,
  @ColumnInfo(defaultValue = true.toString()) val romanizeLyrics: Boolean = true,
  @ColumnInfo(defaultValue = "0") val isDownloaded: Boolean = false,
  @ColumnInfo(name = "isUploaded", defaultValue = false.toString()) val isUploaded: Boolean = false,
  @ColumnInfo(name = "isVideo", defaultValue = false.toString()) val isVideo: Boolean = false,
  @ColumnInfo(defaultValue = "0") val hideFromQuickPicks: Boolean = false
) {
  fun localToggleLike() =
    copy(
      liked = !liked,
      likedDate = if (!liked) LocalDateTime.now() else null,
    )

  // Aria change: Echo's toggleLike()/toggleLibrary() also fired YouTube network calls from the entity.
  // The entity stays pure; remote sync belongs to a provider module. These are the local-only state changes.
  fun toggleLike() =
    copy(
      liked = !liked,
      likedDate = if (!liked) LocalDateTime.now() else null,
      inLibrary = if (!liked) inLibrary ?: LocalDateTime.now() else inLibrary,
    )

  fun toggleLibrary() =
    copy(
      liked = if (inLibrary == null) liked else false,
      inLibrary = if (inLibrary == null) LocalDateTime.now() else null,
      likedDate = if (inLibrary == null) likedDate else null,
    )

  fun toggleUploaded() = copy(isUploaded = !isUploaded)

  fun toggleHideFromQuickPicks() = copy(hideFromQuickPicks = !hideFromQuickPicks)
}
