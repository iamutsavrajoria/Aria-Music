// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.aria.data.entities.AlbumArtistMap
import app.aria.data.entities.AlbumEntity
import app.aria.data.entities.ArtistEntity
import app.aria.data.entities.BeatInfoEntity
import app.aria.data.entities.Event
import app.aria.data.entities.FormatEntity
import app.aria.data.entities.LyricsEntity
import app.aria.data.entities.PlayCountEntity
import app.aria.data.entities.PlaylistEntity
import app.aria.data.entities.PlaylistSongMap
import app.aria.data.entities.PlaylistSongMapPreview
import app.aria.data.entities.PodcastEntity
import app.aria.data.entities.RecognitionHistory
import app.aria.data.entities.RecommendationExclusionEntity
import app.aria.data.entities.RelatedSongMap
import app.aria.data.entities.SearchHistory
import app.aria.data.entities.SetVideoIdEntity
import app.aria.data.entities.SongAlbumMap
import app.aria.data.entities.SongArtistMap
import app.aria.data.entities.SongEntity
import app.aria.data.entities.SongPlayStatsEntity
import app.aria.data.entities.SortedSongAlbumMap
import app.aria.data.entities.SortedSongArtistMap
import app.aria.data.entities.SpeedDialItem
import app.aria.data.entities.TasteProfileEntity
import app.aria.data.entities.TrackMatchEntity

/**
 * Schema version 1. Until the first public build the exported JSON in `schemas/` is regenerated
 * freely; from the first beta on it is frozen and every change needs a tested migration.
 * Release builds must never use destructive migration fallback.
 *
 * Tables: 23 (21 ported from Echo, `podcast` from Meld, plus Aria's `track_match`) and 3 views. Meld's
 * `ArtistPageCache.kt` is not a table (plain serialization DTOs) and is not ported here.
 */
@Database(
    entities = [
        TrackMatchEntity::class, SongEntity::class, Event::class, AlbumEntity::class, ArtistEntity::class,
        PlaylistEntity::class, PlaylistSongMap::class, SongArtistMap::class, SongAlbumMap::class,
        AlbumArtistMap::class, LyricsEntity::class, FormatEntity::class, PlayCountEntity::class,
        RelatedSongMap::class, SearchHistory::class, SpeedDialItem::class, SetVideoIdEntity::class,
        RecognitionHistory::class, BeatInfoEntity::class, RecommendationExclusionEntity::class,
        SongPlayStatsEntity::class, TasteProfileEntity::class, PodcastEntity::class,
    ],
    views = [SortedSongArtistMap::class, SortedSongAlbumMap::class, PlaylistSongMapPreview::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AriaDatabase : RoomDatabase() {
    abstract fun trackMatchDao(): TrackMatchDao
    abstract fun playEventDao(): PlayEventDao
}
