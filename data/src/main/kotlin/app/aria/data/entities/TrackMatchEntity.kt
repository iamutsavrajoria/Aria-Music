// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One generic table replacing Meld's `spotify_match` and `qobuz_match`. Maps a track on one
 * provider to its best match on another. A manual override is chosen by the user and must never
 * be replaced by the automatic matcher.
 */
@Entity(
    tableName = "track_match",
    primaryKeys = ["sourceProvider", "sourceId", "targetProvider"],
    indices = [Index(value = ["targetProvider", "targetId"])],
)
data class TrackMatchEntity(
    val sourceProvider: String,
    val sourceId: String,
    val targetProvider: String,
    val targetId: String,
    val score: Double,
    @ColumnInfo(defaultValue = "0") val isManualOverride: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis(),
)
