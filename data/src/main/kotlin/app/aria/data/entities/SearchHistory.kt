// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "search_history",
  indices =
    [
      Index(
        value = ["query"],
        unique = true,
      ),
    ],
)
data class SearchHistory(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val query: String,
)
