// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.aria.data.entities.AlbumArtistMap
import app.aria.data.entities.AlbumEntity
import app.aria.data.entities.ArtistEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
abstract class LibraryDao : TrackWriterDao() {
    @Query(
        "SELECT $SONG_ROW_COLUMNS FROM song s WHERE s.inLibrary IS NOT NULL AND (:q = '' OR s.title LIKE '%' || :q || '%' ESCAPE '\\' " +
            "OR EXISTS (SELECT 1 FROM song_artist_map m JOIN artist a ON a.id = m.artistId WHERE m.songId = s.id AND a.name LIKE '%' || :q || '%' ESCAPE '\\')) " +
            "ORDER BY CASE WHEN :byTitle = 1 THEN LOWER(s.title) END ASC, s.inLibrary DESC, s.id",
    )
    abstract fun songs(q: String, byTitle: Int): Flow<List<SongRow>>

    @Query(
        "SELECT al.id AS id, al.title AS title, al.year AS year, al.thumbnailUrl AS thumbnailUrl, " +
            "(SELECT group_concat(n, char(31)) FROM (SELECT a.name AS n FROM album_artist_map m JOIN artist a ON a.id = m.artistId " +
            "WHERE m.albumId = al.id ORDER BY m.`order`, a.id)) AS artistNames FROM album al " +
            "WHERE al.inLibrary IS NOT NULL AND (:q = '' OR al.title LIKE '%' || :q || '%' ESCAPE '\\') " +
            "ORDER BY CASE WHEN :byTitle = 1 THEN LOWER(al.title) END ASC, al.inLibrary DESC, al.id",
    )
    abstract fun albums(q: String, byTitle: Int): Flow<List<AlbumRow>>

    @Query(
        "SELECT a.id AS id, a.name AS name, a.thumbnailUrl AS thumbnailUrl FROM artist a " +
            "WHERE a.bookmarkedAt IS NOT NULL AND (:q = '' OR a.name LIKE '%' || :q || '%' ESCAPE '\\') " +
            "ORDER BY CASE WHEN :byTitle = 1 THEN LOWER(a.name) END ASC, a.bookmarkedAt DESC, a.id",
    )
    abstract fun artists(q: String, byTitle: Int): Flow<List<ArtistRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM song WHERE id = :id AND liked = 1)") abstract fun isLiked(id: String): Flow<Boolean>
    @Query("SELECT EXISTS(SELECT 1 FROM song WHERE id = :id AND inLibrary IS NOT NULL)") abstract fun isInLibrary(id: String): Flow<Boolean>
    @Query("SELECT EXISTS(SELECT 1 FROM album WHERE id = :id AND inLibrary IS NOT NULL)") abstract fun isAlbumSaved(id: String): Flow<Boolean>
    @Query("SELECT EXISTS(SELECT 1 FROM artist WHERE id = :id AND bookmarkedAt IS NOT NULL)") abstract fun isArtistFollowed(id: String): Flow<Boolean>

    @Query("UPDATE song SET liked = 1, likedDate = :now, inLibrary = COALESCE(inLibrary, :now) WHERE id = :id")
    protected abstract suspend fun like(id: String, now: LocalDateTime)
    @Query("UPDATE song SET liked = 0, likedDate = NULL WHERE id = :id") protected abstract suspend fun unlike(id: String)
    @Query("UPDATE song SET inLibrary = COALESCE(inLibrary, :now) WHERE id = :id") protected abstract suspend fun addToLibrary(id: String, now: LocalDateTime)
    @Query("UPDATE song SET inLibrary = NULL, liked = 0, likedDate = NULL WHERE id = :id") protected abstract suspend fun removeFromLibrary(id: String)

    @Transaction open suspend fun setLiked(write: TrackWrite, liked: Boolean, now: LocalDateTime) {
        ensure(write); if (liked) like(write.song.id, now) else unlike(write.song.id)
    }
    @Transaction open suspend fun setInLibrary(write: TrackWrite, inLibrary: Boolean, now: LocalDateTime) {
        ensure(write); if (inLibrary) addToLibrary(write.song.id, now) else removeFromLibrary(write.song.id)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertAlbumIgnore(album: AlbumEntity): Long
    /** Marks the album saved (keeping the original saved date) and fills descriptive columns only from non-null incoming values. */
    @Query(
        "UPDATE album SET inLibrary = COALESCE(inLibrary, :now), year = COALESCE(:year, year), " +
            "thumbnailUrl = COALESCE(:thumbnailUrl, thumbnailUrl) WHERE id = :id",
    )
    protected abstract suspend fun markAlbumSaved(id: String, now: LocalDateTime, year: Int?, thumbnailUrl: String?)
    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertArtistIgnore(artist: ArtistEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertAlbumArtist(map: AlbumArtistMap): Long
    @Query("UPDATE album SET inLibrary = NULL WHERE id = :id") protected abstract suspend fun unsaveAlbum(id: String)

    @Transaction open suspend fun saveAlbum(album: AlbumEntity, artists: List<ArtistEntity>) {
        insertAlbumIgnore(album)
        markAlbumSaved(album.id, album.inLibrary ?: LocalDateTime.now(), album.year, album.thumbnailUrl)
        artists.forEachIndexed { i, a -> insertArtistIgnore(a); insertAlbumArtist(AlbumArtistMap(album.id, a.id, i)) }
    }
    @Transaction open suspend fun removeAlbum(id: String) = unsaveAlbum(id)

    /** Marks the artist followed (keeping the original follow date) and fills the thumbnail only from a non-null incoming value. */
    @Query("UPDATE artist SET bookmarkedAt = COALESCE(bookmarkedAt, :now), thumbnailUrl = COALESCE(:thumbnailUrl, thumbnailUrl) WHERE id = :id")
    protected abstract suspend fun markArtistFollowed(id: String, now: LocalDateTime, thumbnailUrl: String?)

    @Transaction open suspend fun followArtist(artist: ArtistEntity) {
        insertArtistIgnore(artist)
        markArtistFollowed(artist.id, artist.bookmarkedAt ?: LocalDateTime.now(), artist.thumbnailUrl)
    }
    @Query("UPDATE artist SET bookmarkedAt = NULL WHERE id = :id") abstract suspend fun unfollowArtist(id: String)
}
