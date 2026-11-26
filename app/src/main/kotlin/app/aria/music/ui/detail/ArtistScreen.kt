// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aria.domain.BrowseItem
import app.aria.domain.Track
import app.aria.music.R
import app.aria.music.ui.common.ErrorState
import app.aria.music.ui.common.LoadingState
import app.aria.music.ui.common.OfflineState
import app.aria.music.ui.common.ShelfActions
import app.aria.music.ui.common.ShelfRow
import app.aria.music.ui.common.TrackMenuAction
import app.aria.music.ui.common.TrackRow
import app.aria.music.ui.playlists.AddToPlaylistDialog

data class ArtistActions(
    val onBack: () -> Unit,
    val onPlayTop: () -> Unit,
    val onShuffleTop: () -> Unit,
    val onToggleFollowed: () -> Unit,
    val onPlaySongs: (List<Track>, Int) -> Unit,
    val onOpenAlbum: (String) -> Unit,
    val onOpenArtist: (String) -> Unit,
    val onOpenPlaylist: (String) -> Unit,
    val onTrackMenu: (Track, TrackMenuAction) -> Unit,
    val onRetry: () -> Unit,
    val onAddToPlaylist: (Track) -> Unit = {},
)

@Composable
fun ArtistContent(state: ArtistUiState, actions: ArtistActions, modifier: Modifier = Modifier) {
    DetailFrame(actions.onBack, modifier) {
        when (state) {
            ArtistUiState.Loading -> LoadingState()
            ArtistUiState.Error -> ErrorState(actions.onRetry)
            ArtistUiState.Offline -> OfflineState(actions.onRetry)
            is ArtistUiState.Content -> ArtistList(state, actions)
        }
    }
}

@Composable
private fun ArtistList(state: ArtistUiState.Content, actions: ArtistActions) {
    val detail = state.detail
    val hasTop = remember(detail) { detail.topTracks.isNotEmpty() }
    // BrowseItem.key is unique per kind+id, not per list: drop repeats so lazy keys stay unique.
    val shelves = remember(detail) { detail.shelves.map { it.copy(items = it.items.distinctBy { item -> item.key }) } }
    val shelfActions = remember(actions) { ShelfActions(actions.onPlaySongs, actions.onOpenAlbum, actions.onOpenArtist, actions.onOpenPlaylist) }
    LazyColumn(Modifier.fillMaxSize().testTag(TAG_DETAIL_LIST)) {
        item(key = "header") {
            Column {
                DetailHeader(detail.artist.thumbnailUrl, detail.artist.name, detail.subscriberText, null, round = true)
                DetailActionRow(detail.artist.name, hasTop, actions.onPlayTop, actions.onShuffleTop) {
                    HeartToggle(state.followed, stringResource(R.string.detail_unfollow), stringResource(R.string.detail_follow), actions.onToggleFollowed)
                }
            }
        }
        if (!detail.description.isNullOrBlank()) {
            item(key = "about") {
                Column {
                    SectionTitle(stringResource(R.string.detail_about))
                    Text(detail.description!!, Modifier.fillMaxWidth().padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        shelves.forEachIndexed { i, shelf ->
            val songs = shelf.items.filterIsInstance<BrowseItem.Song>().map { it.track }
            if (shelf.items.isNotEmpty() && songs.size == shelf.items.size) {
                // A shelf of songs only reads as a list of rows that play from the tapped one.
                item(key = "shelf:$i:title") { SectionTitle(shelf.title) }
                itemsIndexed(songs, key = { j, t -> "shelf:$i:song:$j:${t.id.raw}" }) { j, t ->
                    TrackRow(t, onClick = { actions.onPlaySongs(songs, j) }, onMenu = { actions.onTrackMenu(t, it) }, modifier = Modifier.testTag("detail:shelf:$i:$j"), liked = t.id in state.liked, extraActions = listOf(R.string.track_add_to_playlist to { actions.onAddToPlaylist(t) }))
                }
            } else {
                item(key = "shelf:$i") { ShelfRow(shelf, "artist:shelf:$i", shelfActions) }
            }
        }
    }
}

@Composable
fun ArtistScreen(
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var pending by remember { mutableStateOf<Track?>(null) }
    val actions = remember(viewModel, onBack, onAlbum, onArtist, onPlaylist) {
        ArtistActions(
            onBack = onBack, onPlayTop = viewModel::playTop, onShuffleTop = viewModel::shuffleTop, onToggleFollowed = viewModel::toggleFollowed,
            onPlaySongs = viewModel::playShelfSongs, onOpenAlbum = onAlbum, onOpenArtist = onArtist, onOpenPlaylist = onPlaylist,
            onTrackMenu = viewModel::trackAction, onRetry = viewModel::retry, onAddToPlaylist = { pending = it },
        )
    }
    ArtistContent(state, actions)
    pending?.let { AddToPlaylistDialog(it, onDismiss = { pending = null }) }
}
