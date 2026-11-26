// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aria.domain.Track
import app.aria.music.R
import app.aria.music.ui.common.ErrorState
import app.aria.music.ui.common.LoadingState
import app.aria.music.ui.common.OfflineState
import app.aria.music.ui.common.Tile
import app.aria.music.ui.common.TrackMenuAction
import app.aria.music.ui.common.TrackRow
import app.aria.music.ui.playlists.AddToPlaylistDialog

data class AlbumActions(
    val onBack: () -> Unit,
    val onPlay: (Int) -> Unit,
    val onShuffle: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onToggleSaved: () -> Unit,
    val onTrackMenu: (Track, TrackMenuAction) -> Unit,
    val onOpenAlbum: (String) -> Unit,
    val onRetry: () -> Unit,
    val onAddToPlaylist: (Track) -> Unit = {},
)

@Composable
fun AlbumContent(state: AlbumUiState, actions: AlbumActions, modifier: Modifier = Modifier) {
    DetailFrame(actions.onBack, modifier) {
        when (state) {
            AlbumUiState.Loading -> LoadingState()
            AlbumUiState.Error -> ErrorState(actions.onRetry)
            AlbumUiState.Offline -> OfflineState(actions.onRetry)
            is AlbumUiState.Content -> AlbumList(state, actions)
        }
    }
}

@Composable
private fun AlbumList(state: AlbumUiState.Content, actions: AlbumActions) {
    val album = state.detail.album
    val tracks = state.detail.tracks
    val separator = stringResource(R.string.artist_separator)
    val count = pluralStringResource(R.plurals.detail_track_count, tracks.size, tracks.size)
    val meta = listOfNotNull(album.year?.toString(), count).joinToString(separator)
    // Repeated versions would repeat lazy keys.
    val others = remember(state.detail) { state.detail.otherVersions.distinctBy { it.id } }
    LazyColumn(Modifier.fillMaxSize().testTag(TAG_DETAIL_LIST)) {
        item(key = "header") {
            Column {
                DetailHeader(album.thumbnailUrl, album.title, album.artists.joinToString(separator), meta)
                DetailActionRow(album.title, tracks.isNotEmpty(), { actions.onPlay(0) }, actions.onShuffle) {
                    AddToQueueButton(album.title, tracks.isNotEmpty(), actions.onAddToQueue)
                    HeartToggle(state.saved, stringResource(R.string.detail_unsave), stringResource(R.string.detail_save), actions.onToggleSaved)
                }
            }
        }
        itemsIndexed(tracks, key = { i, t -> "track:$i:${t.id.raw}" }) { i, t ->
            TrackRow(t, onClick = { actions.onPlay(i) }, onMenu = { actions.onTrackMenu(t, it) }, modifier = Modifier.testTag("detail:track:$i"), liked = t.id in state.liked, extraActions = listOf(R.string.track_add_to_playlist to { actions.onAddToPlaylist(t) }))
        }
        if (others.isNotEmpty()) {
            item(key = "other:title") { SectionTitle(stringResource(R.string.detail_other_versions)) }
            item(key = "other:row") {
                LazyRow(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp).testTag("detail:other-versions"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    itemsIndexed(others, key = { _, a -> "other:" + a.id }) { _, a ->
                        Tile(a.thumbnailUrl, a.title, a.artists.joinToString(separator), false, R.string.browse_desc_album) { actions.onOpenAlbum(a.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumScreen(onBack: () -> Unit, onAlbum: (String) -> Unit, viewModel: AlbumViewModel = hiltViewModel()) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var pending by remember { mutableStateOf<Track?>(null) }
    val actions = remember(viewModel, onBack, onAlbum) {
        AlbumActions(
            onBack = onBack, onPlay = viewModel::play, onShuffle = viewModel::shuffle, onAddToQueue = viewModel::addToQueue, onToggleSaved = viewModel::toggleSaved,
            onTrackMenu = viewModel::trackAction, onOpenAlbum = onAlbum, onRetry = viewModel::retry, onAddToPlaylist = { pending = it },
        )
    }
    AlbumContent(state, actions)
    pending?.let { AddToPlaylistDialog(it, onDismiss = { pending = null }) }
}
