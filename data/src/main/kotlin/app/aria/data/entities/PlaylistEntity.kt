// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "playlist")
data class PlaylistEntity(
  @PrimaryKey val id: String = generatePlaylistId(),
  val name: String,
  val browseId: String? = null,
  val createdAt: LocalDateTime? = LocalDateTime.now(),
  val lastUpdateTime: LocalDateTime? = LocalDateTime.now(),
  @ColumnInfo(name = "isEditable", defaultValue = true.toString()) val isEditable: Boolean = true,
  val bookmarkedAt: LocalDateTime? = null,
  val remoteSongCount: Int? = null,
  val playEndpointParams: String? = null,
  val thumbnailUrl: String? = null,
  val shuffleEndpointParams: String? = null,
  val radioEndpointParams: String? = null,
  @ColumnInfo(name = "isLocal", defaultValue = false.toString()) val isLocal: Boolean = false,
  @ColumnInfo(name = "isAutoSync", defaultValue = false.toString()) val isAutoSync: Boolean = false,
  @ColumnInfo(name = "isPinned", defaultValue = false.toString()) val isPinned: Boolean = false
) {
  companion object {
    const val LIKED_PLAYLIST_ID = "LP_LIKED"
    const val DOWNLOADED_PLAYLIST_ID = "LP_DOWNLOADED"

    fun generatePlaylistId() = "LP" + randomLetters(8)
  }

  // Aria change: Echo's shareLink pointed at Echo's own share domain; Aria depends on no other project's server.

  fun localToggleLike() =
    copy(bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now())

  // Aria change: Echo's toggleLike() also fired a YouTube network call; remote sync belongs to a provider module.
  fun toggleLike() = localToggleLike()
}
