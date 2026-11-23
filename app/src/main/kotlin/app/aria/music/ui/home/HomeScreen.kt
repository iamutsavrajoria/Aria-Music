// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aria.domain.BrowseItem
import app.aria.domain.Shelf
import app.aria.domain.ShelfState
import app.aria.domain.Track
import app.aria.music.R
import app.aria.music.image.AriaImage
import app.aria.music.ui.common.AriaIcons
import app.aria.music.ui.common.ErrorState
import app.aria.music.ui.common.LoadingState
import app.aria.music.ui.common.OfflineState
import app.aria.music.ui.common.TrackMenuAction
import app.aria.music.ui.common.TrackRow

private val STATE_HEIGHT = 280.dp

const val TAG_HOME_LIST = "home:list"
const val TAG_HOME_STALE = "home:stale"
const val TAG_HOME_SHELF_FAILED = "home:shelf-failed"

data class HomeActions(
    val onPlay: (List<Track>, Int) -> Unit,
    val onOpenAlbum: (String) -> Unit,
    val onOpenArtist: (String) -> Unit,
    val onOpenPlaylist: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onTrackMenu: (Track, TrackMenuAction) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    PullToRefreshBox(state.refreshing, actions.onRefresh, modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag(TAG_HOME_LIST)) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    IconButton(actions.onOpenSettings, Modifier.size(48.dp)) { Icon(AriaIcons.MoreVert, stringResource(R.string.home_settings)) }
                }
            }
            localSection("quick", R.string.home_quick_picks, R.string.home_quick_picks_hint, state, state.quickPicks, actions)
            remoteSection(state.remote, actions)
            localSection("recent", R.string.home_recently_played, R.string.home_recent_hint, state, state.recent, actions)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.localSection(
    name: String, @StringRes title: Int, @StringRes hint: Int, state: HomeUiState, tracks: List<Track>, actions: HomeActions,
) {
    item(key = "$name:header") { SectionHeader(title) }
    if (tracks.isEmpty()) {
        item(key = "$name:hint") {
            Text(
                stringResource(hint), Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        itemsIndexed(tracks, key = { i, t -> "$name:$i:${t.id.raw}" }) { i, track ->
            TrackRow(
                track, { actions.onPlay(tracks, i) }, { actions.onTrackMenu(track, it) },
                Modifier.testTag("home:$name:$i"), liked = track.id in state.liked,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.remoteSection(remote: RemoteSection, actions: HomeActions) {
    when (remote) {
        RemoteSection.Loading -> item(key = "remote:loading") { Box(Modifier.fillMaxWidth().height(STATE_HEIGHT)) { LoadingState() } }
        // The shared states fill their parent and scroll on their own: inside a LazyColumn they need a bounded height.
        RemoteSection.Error -> item(key = "remote:error") { ErrorState(actions.onRefresh, Modifier.fillMaxWidth().height(STATE_HEIGHT)) }
        RemoteSection.Offline -> item(key = "remote:offline") { OfflineState(actions.onRefresh, Modifier.fillMaxWidth().height(STATE_HEIGHT)) }
        is RemoteSection.Content -> {
            if (remote.outdated) item(key = "remote:stale") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp).testTag(TAG_HOME_STALE), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_stale), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(actions.onRefresh, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.home_refresh)) }
                }
            }
            if (remote.refreshFailed) item(key = "remote:refresh-failed") {
                Text(
                    stringResource(R.string.home_refresh_failed), Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }
            // Index-qualified: two shelves may share a title.
            remote.shelves.forEachIndexed { i, shelf ->
                when (shelf) {
                    is ShelfState.Ready -> item(key = "shelf:$i") { ShelfRow(i, shelf.shelf, actions) }
                    is ShelfState.Failed -> item(key = "shelf:$i") { FailedShelf(shelf.title, actions.onRefresh) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int) {
    Text(
        stringResource(title), Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun FailedShelf(title: String?, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(TAG_HOME_SHELF_FAILED)) {
        if (!title.isNullOrBlank()) Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_section_failed), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onRetry, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun ShelfRow(index: Int, shelf: Shelf, actions: HomeActions) {
    // BrowseItem.key is unique per kind+id, not per list: drop repeats so the LazyRow keys are unique.
    val items = remember(shelf) { shelf.items.distinctBy { it.key } }
    val songs = remember(items) { items.filterIsInstance<BrowseItem.Song>().map { it.track } }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(shelf.title, Modifier.padding(horizontal = 16.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        if (!shelf.subtitle.isNullOrBlank()) Text(shelf.subtitle!!, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("home:shelf:$index"), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
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
private fun Tile(thumbnailUrl: String?, title: String, subtitle: String?, round: Boolean, @StringRes typeDescription: Int, onClick: () -> Unit) {
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

@Composable
fun HomeScreen(
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val actions = remember(viewModel, onAlbum, onArtist, onPlaylist, onSettings) {
        HomeActions(
            onPlay = viewModel::play, onOpenAlbum = onAlbum, onOpenArtist = onArtist, onOpenPlaylist = onPlaylist,
            onRefresh = viewModel::refresh, onOpenSettings = onSettings,
            onTrackMenu = { track, action ->
                when (action) {
                    TrackMenuAction.PLAY_NEXT -> viewModel.playNext(track)
                    TrackMenuAction.ADD_TO_QUEUE -> viewModel.addToQueue(track)
                    TrackMenuAction.LIKE -> viewModel.like(track)
                    TrackMenuAction.UNLIKE -> viewModel.unlike(track)
                }
            },
        )
    }
    HomeContent(state, actions)
}
