// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.*
import app.aria.innertube.models.*
import app.aria.innertube.pages.*

/** `toTrack()` throws on an invalid video ID; page data is untrusted, so browse mapping drops such rows instead. */
internal fun SongItem.toTrackOrNull(): Track? = runCatching { toTrack() }.getOrNull()

internal fun String?.parseCount(): Int? =
    this?.let { Regex("\\d[\\d,.]*").find(it)?.value?.filter(Char::isDigit)?.toIntOrNull() }

internal fun AlbumItem.toRef() = AlbumRef(browseId, title, artists.orEmpty().map { it.name }.filter { it.isNotBlank() }, year, thumbnail.takeIf { it.isNotBlank() })
internal fun ArtistItem.toRef() = ArtistRef(id, title, thumbnail)
internal fun PlaylistItem.toRef() = PlaylistRef(id, title, author?.name, thumbnail, songCountText.parseCount())

internal fun YTItem.toBrowseItem(): BrowseItem? = when (this) {
    is SongItem -> if (isEpisode) null else toTrackOrNull()?.let { BrowseItem.Song(it) }
    is AlbumItem -> BrowseItem.Album(toRef())
    is ArtistItem -> BrowseItem.Artist(toRef())
    is PlaylistItem -> if (isPodcast) null else BrowseItem.Playlist(toRef())
    is PodcastItem, is EpisodeItem -> null
}

/** Maps each item on its own (a throwing item is dropped) and removes duplicate keys, keeping the first. */
private fun List<YTItem>.toBrowseItems(map: (YTItem) -> BrowseItem?): List<BrowseItem> =
    mapNotNull { runCatching { map(it) }.getOrNull() }.distinctBy { it.key }

/** Each section maps on its own: one that throws on every item is Failed, the others are unaffected. */
internal fun HomePage.toShelfStates(map: (YTItem) -> BrowseItem? = YTItem::toBrowseItem): List<ShelfState> =
    sections.mapNotNull { s ->
        val results = s.items.map { runCatching { map(it) } }
        val items = results.mapNotNull { it.getOrNull() }.distinctBy { it.key }
        when {
            items.isNotEmpty() -> ShelfState.Ready(Shelf(s.title, s.label, items))
            results.any { it.isFailure } -> ShelfState.Failed(s.title)
            else -> null
        }
    }

internal fun AlbumPage.toDetail() = AlbumDetail(album.toRef(), songs.mapNotNull { it.toTrackOrNull() }, otherVersions.map { it.toRef() })

internal fun ArtistPage.toDetail() = ArtistDetail(
    artist = artist.toRef(), description = description, subscriberText = subscriberCountText,
    shelves = sections.mapNotNull { sec ->
        val items = sec.items.toBrowseItems(YTItem::toBrowseItem)
        if (items.isEmpty()) null else Shelf(sec.title, null, items)
    },
)

internal fun PlaylistPage.toDetail() = PlaylistDetail(playlist.toRef(), songs.mapNotNull { it.toTrackOrNull() }, songsContinuation ?: continuation)

internal fun List<YTItem>.toSearchResults() = SearchResults(
    songs = filterIsInstance<SongItem>().filterNot { it.isEpisode }.mapNotNull { it.toTrackOrNull() }.distinctBy { it.id },
    albums = filterIsInstance<AlbumItem>().map { it.toRef() }.distinctBy { it.id },
    artists = filterIsInstance<ArtistItem>().map { it.toRef() }.distinctBy { it.id },
    playlists = filterIsInstance<PlaylistItem>().filterNot { it.isPodcast }.map { it.toRef() }.distinctBy { it.id },
)
