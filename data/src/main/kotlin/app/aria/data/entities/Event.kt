// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
  tableName = "event",
  foreignKeys =
    [
      ForeignKey(
        entity = SongEntity::class,
        parentColumns = ["id"],
        childColumns = ["songId"],
        onDelete = ForeignKey.CASCADE,
      ),
    ],
)
data class Event(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @ColumnInfo(index = true) val songId: String,
  val timestamp: LocalDateTime,
  val playTime: Long,
)
