// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import app.aria.music.ui.common.TrackMenuAction
import app.aria.music.ui.common.TrackRow
import app.aria.music.ui.playlists.AddToPlaylistDialog

/** How many rows before the end the next page is requested. */
private const val PREFETCH_ROWS = 5

data class PlaylistActions(
    val onBack: () -> Unit,
    val onPlay: (Int) -> Unit,
    val onShuffle: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onLoadMore: () -> Unit,
    val onTrackMenu: (Track, TrackMenuAction) -> Unit,
    val onRetry: () -> Unit,
    val onAddToPlaylist: (Track) -> Unit = {},
)

@Composable
fun PlaylistContent(state: PlaylistUiState, actions: PlaylistActions, modifier: Modifier = Modifier) {
    DetailFrame(actions.onBack, modifier) {
        when (state) {
            PlaylistUiState.Loading -> LoadingState()
            PlaylistUiState.Error -> ErrorState(actions.onRetry)
            PlaylistUiState.Offline -> OfflineState(actions.onRetry)
            is PlaylistUiState.Content -> PlaylistList(state, actions)
        }
    }
}

@Composable
private fun PlaylistList(state: PlaylistUiState.Content, actions: PlaylistActions) {
    val playlist = state.detail.playlist
    val tracks = state.detail.tracks
    val listState = rememberLazyListState()
    // The next page loads on its own near the end, but never while one is loading and never after a failure: that waits for Retry.
    val canLoad = state.detail.continuation != null && !state.loadingMore && !state.moreFailed
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index
            last != null && last >= info.totalItemsCount - 1 - PREFETCH_ROWS
        }
    }
    LaunchedEffect(nearEnd, canLoad, tracks.size) { if (nearEnd && canLoad) actions.onLoadMore() }
    val total = playlist.trackCount ?: tracks.size
    LazyColumn(Modifier.fillMaxSize().testTag(TAG_DETAIL_LIST), listState) {
        item(key = "header") {
            Column {
                DetailHeader(playlist.thumbnailUrl, playlist.title, playlist.subtitle, pluralStringResource(R.plurals.detail_track_count, total, total))
                DetailActionRow(playlist.title, tracks.isNotEmpty(), { actions.onPlay(0) }, actions.onShuffle) {
                    AddToQueueButton(playlist.title, tracks.isNotEmpty(), actions.onAddToQueue)
                }
            }
        }
        itemsIndexed(tracks, key = { i, t -> "track:$i:${t.id.raw}" }) { i, t ->
            TrackRow(t, onClick = { actions.onPlay(i) }, onMenu = { actions.onTrackMenu(t, it) }, modifier = Modifier.testTag("detail:track:$i"), liked = t.id in state.liked, extraActions = listOf(R.string.track_add_to_playlist to { actions.onAddToPlaylist(t) }))
        }
        if (state.loadingMore) item(key = "footer:loading") {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text(stringResource(R.string.detail_loading_more), style = MaterialTheme.typography.bodyMedium)
            }
        } else if (state.moreFailed) item(key = "footer:failed") {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp).testTag(TAG_DETAIL_MORE_FAILED), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.detail_more_failed), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                TextButton(actions.onLoadMore, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

@Composable
fun PlaylistScreen(onBack: () -> Unit, viewModel: PlaylistViewModel = hiltViewModel()) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var pending by remember { mutableStateOf<Track?>(null) }
    val actions = remember(viewModel, onBack) {
        PlaylistActions(
            onBack = onBack, onPlay = viewModel::play, onShuffle = viewModel::shuffle, onAddToQueue = viewModel::addToQueue,
            onLoadMore = viewModel::loadMore, onTrackMenu = viewModel::trackAction, onRetry = viewModel::retry, onAddToPlaylist = { pending = it },
        )
    }
    PlaylistContent(state, actions)
    pending?.let { AddToPlaylistDialog(it, onDismiss = { pending = null }) }
}
