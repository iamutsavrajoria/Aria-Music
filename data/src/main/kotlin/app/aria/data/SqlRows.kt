// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.data.entities.ArtistEntity
import app.aria.data.entities.SongEntity
import app.aria.domain.AlbumRef
import app.aria.domain.ArtistRef
import app.aria.domain.LibrarySong
import app.aria.domain.PlaylistRef
import app.aria.domain.Track
import app.aria.domain.TrackId

/** Unit separator between joined artist names; names never contain it. */
internal const val NAME_SEP = "\u001F"

/** Slim song columns shared by every list query; artists are joined in position order. */
internal const val SONG_ROW_COLUMNS =
    "s.id AS id, s.title AS title, s.duration AS duration, s.thumbnailUrl AS thumbnailUrl, s.liked AS liked, " +
        "(SELECT group_concat(n, char(31)) FROM (SELECT a.name AS n FROM song_artist_map m " +
        "JOIN artist a ON a.id = m.artistId WHERE m.songId = s.id ORDER BY m.position)) AS artistNames"

data class SongRow(
    val id: String, val title: String, val duration: Int, val thumbnailUrl: String?, val liked: Boolean, val artistNames: String?,
)
data class AlbumRow(val id: String, val title: String, val year: Int?, val thumbnailUrl: String?, val artistNames: String?)
data class ArtistRow(val id: String, val name: String, val thumbnailUrl: String?)
data class PlaylistRow(val id: String, val name: String, val thumbnailUrl: String?, val trackCount: Int)
data class PlaylistEntryRow(
    val entryId: Int, val id: String, val title: String, val duration: Int, val thumbnailUrl: String?, val liked: Boolean, val artistNames: String?,
)

internal fun String?.splitNames(): List<String> = this?.split(NAME_SEP)?.filter { it.isNotBlank() }.orEmpty()

internal fun SongRow.toTrack(): Track? = runCatching {
    Track(TrackId.parse(id), title, artistNames.splitNames(), duration.takeIf { it >= 0 }?.let { it * 1000L }, thumbnailUrl)
}.getOrNull()

internal fun SongRow.toLibrarySong(): LibrarySong? = toTrack()?.let { LibrarySong(it, liked) }
internal fun AlbumRow.toRef() = AlbumRef(id, title, artistNames.splitNames(), year, thumbnailUrl)
internal fun ArtistRow.toRef() = ArtistRef(id, name, thumbnailUrl)
internal fun PlaylistRow.toRef() = PlaylistRef(id, name, null, thumbnailUrl, trackCount)

/** A track as the rows it needs: the song and its artists (artists known only by name get a name-derived local id). */
class TrackWrite(val song: SongEntity, val artists: List<ArtistEntity>)

internal fun artistIdForName(name: String) = "LN:" + name.trim().lowercase()

internal fun Track.toWrite() = TrackWrite(
    SongEntity(id = id.raw, title = title, duration = durationMs?.let { (it / 1000).toInt() } ?: -1, thumbnailUrl = thumbnailUrl),
    artists.filter { it.isNotBlank() }.map { ArtistEntity(id = artistIdForName(it), name = it.trim()) },
)

/** Escapes LIKE wildcards; queries use ESCAPE '\'. */
internal fun String.escapeLike(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
