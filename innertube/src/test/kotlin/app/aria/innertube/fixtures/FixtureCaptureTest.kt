// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.fixtures

import app.aria.domain.*
import app.aria.innertube.adapter.YouTubeBrowseSource
import app.aria.innertube.adapter.YouTubeSearchRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Maintainer-run only (scripts/capture-innertube-fixtures). Writes sanitized summaries of what the adapters return for real responses. */
class FixtureCaptureTest {
    private val pretty = Json { prettyPrint = true }
    private fun clean(url: String?) = url?.substringBefore('?')

    private fun item(i: BrowseItem): JsonObject = buildJsonObject {
        when (i) {
            is BrowseItem.Song -> { put("type", "song"); put("id", i.track.id.raw); put("title", i.track.title); put("thumb", clean(i.track.thumbnailUrl)) }
            is BrowseItem.Album -> { put("type", "album"); put("id", i.album.id); put("title", i.album.title); put("thumb", clean(i.album.thumbnailUrl)) }
            is BrowseItem.Artist -> { put("type", "artist"); put("id", i.artist.id); put("title", i.artist.name); put("thumb", clean(i.artist.thumbnailUrl)) }
            is BrowseItem.Playlist -> { put("type", "playlist"); put("id", i.playlist.id); put("title", i.playlist.title); put("thumb", clean(i.playlist.thumbnailUrl)) }
        }
    }
    private fun tracks(list: List<Track>) = JsonArray(list.map { item(BrowseItem.Song(it)) })
    private fun write(dir: File, name: String, obj: JsonObject) { File(dir, "$name.json").writeText(pretty.encodeToString(JsonObject.serializer(), obj)) }

    @Test fun capture() = runBlocking {
        assumeTrue(System.getProperty("aria.capture") == "true")
        val dir = File(FixtureFreshness.DIR).also { it.mkdirs() }
        val browse = YouTubeBrowseSource(); val search = YouTubeSearchRepository()
        val files = mutableListOf<String>()

        val home = browse.home().getOrThrow()
        write(dir, "home", buildJsonObject {
            put("shelves", JsonArray(home.map { s ->
                when (s) {
                    is ShelfState.Ready -> buildJsonObject { put("title", s.shelf.title); put("items", JsonArray(s.shelf.items.map { item(it) })) }
                    is ShelfState.Failed -> buildJsonObject { put("title", s.title); put("failed", true) }
                }
            }))
        }); files += "home.json"

        val results = search.search("daft punk").getOrThrow()
        write(dir, "search", buildJsonObject {
            put("songs", tracks(results.songs)); put("albums", JsonArray(results.albums.map { item(BrowseItem.Album(it)) }))
            put("artists", JsonArray(results.artists.map { item(BrowseItem.Artist(it)) })); put("playlists", JsonArray(results.playlists.map { item(BrowseItem.Playlist(it)) }))
        }); files += "search.json"
        write(dir, "suggestions", buildJsonObject { put("queries", JsonArray(search.suggestions("daft").getOrThrow().map { JsonPrimitive(it) })) }); files += "suggestions.json"

        results.albums.firstOrNull()?.let { a ->
            val d = browse.album(a.id).getOrThrow()
            write(dir, "album", buildJsonObject { put("title", d.album.title); put("tracks", tracks(d.tracks)) }); files += "album.json"
        }
        results.artists.firstOrNull()?.let { a ->
            val d = browse.artist(a.id).getOrThrow()
            write(dir, "artist", buildJsonObject { put("name", d.artist.name); put("shelves", JsonArray(d.shelves.map { s -> buildJsonObject { put("title", s.title); put("items", JsonArray(s.items.map { item(it) })) } })) }); files += "artist.json"
        }
        results.playlists.firstOrNull()?.let { p ->
            val d = browse.playlist(p.id).getOrThrow()
            write(dir, "playlist", buildJsonObject { put("title", d.playlist.title); put("tracks", tracks(d.tracks)); put("hasMore", d.continuation != null) }); files += "playlist.json"
        }
        File(dir, "manifest.json").writeText(pretty.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("capturedAt", LocalDate.now().toString()); put("files", JsonArray(files.map { JsonPrimitive(it) }))
        }))
    }
}
