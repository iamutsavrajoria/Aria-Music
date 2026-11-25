// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aria.domain.BrowseItem
import app.aria.domain.Shelf
import app.aria.domain.Track
import app.aria.music.R
import app.aria.music.image.AriaImage

/** What tapping a tile of a shelf does; shared by Home and the artist page. */
data class ShelfActions(
    val onPlay: (List<Track>, Int) -> Unit,
    val onOpenAlbum: (String) -> Unit,
    val onOpenArtist: (String) -> Unit,
    val onOpenPlaylist: (String) -> Unit,
)

/** A titled row of tiles. [listTag] tags the LazyRow for tests. */
@Composable
fun ShelfRow(shelf: Shelf, listTag: String, actions: ShelfActions) {
    // BrowseItem.key is unique per kind+id, not per list: drop repeats so the LazyRow keys are unique.
    val items = remember(shelf) { shelf.items.distinctBy { it.key } }
    val songs = remember(items) { items.filterIsInstance<BrowseItem.Song>().map { it.track } }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(shelf.title, Modifier.padding(horizontal = 16.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        if (!shelf.subtitle.isNullOrBlank()) Text(shelf.subtitle!!, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(Modifier.fillMaxWidth().padding(top = 8.dp).testTag(listTag), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            itemsIndexed(items, key = { _, it -> it.key }) { _, item ->
                when (item) {
                    is BrowseItem.Song -> Tile(item.track.thumbnailUrl, item.track.title, item.track.artists.joinToString(stringResource(R.string.artist_separator)), false, R.string.browse_desc_song) {
                        actions.onPlay(songs, songs.indexOf(item.track).coerceAtLeast(0))
                    }
                    is BrowseItem.Album -> Tile(item.album.thumbnailUrl, item.album.title, item.album.artists.joinToString(stringResource(R.string.artist_separator)), false, R.string.browse_desc_album) { actions.onOpenAlbum(item.album.id) }
                    is BrowseItem.Artist -> Tile(item.artist.thumbnailUrl, item.artist.name, null, true, R.string.browse_desc_artist) { actions.onOpenArtist(item.artist.id) }
                    is BrowseItem.Playlist -> Tile(item.playlist.thumbnailUrl, item.playlist.title, item.playlist.subtitle, false, R.string.browse_desc_playlist) { actions.onOpenPlaylist(item.playlist.id) }
                }
            }
        }
    }
}

@Composable
fun Tile(thumbnailUrl: String?, title: String, subtitle: String?, round: Boolean, @StringRes typeDescription: Int, onClick: () -> Unit) {
    val description = stringResource(typeDescription, title)
    Column(
        Modifier.width(144.dp).semantics(mergeDescendants = true) { contentDescription = description }.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AriaImage(thumbnailUrl, null, Modifier.size(144.dp).clip(if (round) CircleShape else RoundedCornerShape(8.dp)))
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        if (!subtitle.isNullOrBlank()) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

