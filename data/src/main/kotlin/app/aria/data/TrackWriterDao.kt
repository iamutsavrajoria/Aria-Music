// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import app.aria.data.entities.ArtistEntity
import app.aria.data.entities.SongArtistMap
import app.aria.data.entities.SongEntity

/** Base for DAOs that upsert songs (decision 2: songs are upserted on first like, add, playlist-add or play). Existing rows are never overwritten. */
abstract class TrackWriterDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertSong(song: SongEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertArtist(artist: ArtistEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertSongArtist(map: SongArtistMap): Long

    @Transaction
    open suspend fun ensure(write: TrackWrite) {
        insertSong(write.song)
        write.artists.forEachIndexed { i, a -> insertArtist(a); insertSongArtist(SongArtistMap(write.song.id, a.id, i)) }
    }
}
