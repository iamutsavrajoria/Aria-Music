// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recommendation_exclusions")
data class RecommendationExclusionEntity(
  @PrimaryKey val trackKey: String,
  val excludedAtMillis: Long,
  val trackName: String = "",
  val artistName: String = "",
)
