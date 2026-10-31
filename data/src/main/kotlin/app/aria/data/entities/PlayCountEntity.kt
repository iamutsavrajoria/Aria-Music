// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.Entity

@Entity(tableName = "playCount", primaryKeys = ["song", "year", "month"])
class PlayCountEntity(
  val song: String,
  val year: Int = -1,
  val month: Int = -1,
  val count: Int = -1,
)
