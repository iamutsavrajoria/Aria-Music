// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned JSON of one domain Shelf. decode never throws: anything unexpected is null. */
object ShelfJsonCodec {
    private const val VERSION = 1

    fun encode(shelf: Shelf): String = JSONObject()
        .put("v", VERSION).put("title", shelf.title).put("subtitle", shelf.subtitle ?: JSONObject.NULL)
        .put("items", JSONArray().also { a -> shelf.items.forEach { a.put(encodeItem(it)) } })
        .toString()

    fun decode(json: String): Shelf? = try {
        val o = JSONObject(json)
        if (o.getInt("v") != VERSION) null
        else {
            val items = o.getJSONArray("items")
            Shelf(o.getString("title"), o.str("subtitle"), (0 until items.length()).map { decodeItem(items.getJSONObject(it)) ?: return null })
        }
    } catch (e: Exception) { null }

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)
    private fun JSONObject.int(k: String): Int? = if (isNull(k)) null else getInt(k)
    private fun JSONObject.strings(k: String): List<String> = getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } }
    private fun nul(v: Any?): Any = v ?: JSONObject.NULL

    private fun encodeItem(i: BrowseItem): JSONObject = when (i) {
        is BrowseItem.Song -> JSONObject().put("t", "song").put("id", i.track.id.raw).put("title", i.track.title)
            .put("artists", JSONArray(i.track.artists)).put("dur", nul(i.track.durationMs)).put("thumb", nul(i.track.thumbnailUrl))
        is BrowseItem.Album -> JSONObject().put("t", "album").put("id", i.album.id).put("title", i.album.title)
            .put("artists", JSONArray(i.album.artists)).put("year", nul(i.album.year)).put("thumb", nul(i.album.thumbnailUrl))
        is BrowseItem.Artist -> JSONObject().put("t", "artist").put("id", i.artist.id).put("title", i.artist.name).put("thumb", nul(i.artist.thumbnailUrl))
        is BrowseItem.Playlist -> JSONObject().put("t", "playlist").put("id", i.playlist.id).put("title", i.playlist.title)
            .put("sub", nul(i.playlist.subtitle)).put("thumb", nul(i.playlist.thumbnailUrl)).put("count", nul(i.playlist.trackCount))
    }

    private fun decodeItem(o: JSONObject): BrowseItem? = when (o.getString("t")) {
        "song" -> BrowseItem.Song(Track(TrackId.parse(o.getString("id")), o.getString("title"), o.strings("artists"), if (o.isNull("dur")) null else o.getLong("dur"), o.str("thumb")))
        "album" -> BrowseItem.Album(AlbumRef(o.getString("id"), o.getString("title"), o.strings("artists"), o.int("year"), o.str("thumb")))
        "artist" -> BrowseItem.Artist(ArtistRef(o.getString("id"), o.getString("title"), o.str("thumb")))
        "playlist" -> BrowseItem.Playlist(PlaylistRef(o.getString("id"), o.getString("title"), o.str("sub"), o.str("thumb"), o.int("count")))
        else -> null
    }
}
