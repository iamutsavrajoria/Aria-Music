// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities


data class SongWithStats(
  val id: String,
  val title: String,
  val artistName: String? = null,
  val thumbnailUrl: String? = null,
  val songCountListened: Int? = 0,
  val timeListened: Long? = 0L,
  val isVideo: Boolean = false,
)
