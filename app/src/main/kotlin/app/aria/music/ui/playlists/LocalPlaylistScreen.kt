// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.aria.music.ui.common.AriaIcons
import app.aria.music.ui.common.EmptyState
import app.aria.music.ui.common.LoadingState
import app.aria.music.ui.common.TrackMenuAction
import app.aria.music.ui.common.TrackRow
import app.aria.music.ui.detail.AddToQueueButton
import app.aria.music.ui.detail.DetailActionRow
import app.aria.music.ui.detail.DetailFrame
import app.aria.music.ui.detail.DetailHeader
import app.aria.music.ui.detail.TAG_DETAIL_LIST

const val TAG_PLAYLIST_RENAME = "playlist:rename"
const val TAG_PLAYLIST_DELETE = "playlist:delete"
const val TAG_NEW_PLAYLIST = "playlist:new"

data class LocalPlaylistActions(
    val onBack: () -> Unit,
    val onPlay: (Int) -> Unit,
    val onShuffle: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onRename: (String) -> Unit,
    val onDelete: () -> Unit,
    val onRemove: (Int) -> Unit,
    val onMoveUp: (Int) -> Unit,
    val onMoveDown: (Int) -> Unit,
    val onTrackMenu: (Track, TrackMenuAction) -> Unit,
)

@Composable
fun LocalPlaylistContent(state: LocalPlaylistUiState, actions: LocalPlaylistActions, modifier: Modifier = Modifier) {
    DetailFrame(actions.onBack, modifier) {
        when (state) {
            LocalPlaylistUiState.Loading -> LoadingState()
            LocalPlaylistUiState.NotFound -> EmptyState(R.string.playlist_not_found)
            // Local data has no offline state and the page follows its Flows, so there is nothing to retry: the failure is only reported.
            LocalPlaylistUiState.Error -> EmptyState(R.string.error_title, R.string.error_body)
            is LocalPlaylistUiState.Content -> PlaylistBody(state, actions)
        }
    }
}

@Composable
private fun PlaylistBody(state: LocalPlaylistUiState.Content, actions: LocalPlaylistActions) {
    val playlist = state.playlist
    val rows = state.rows
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag(TAG_DETAIL_LIST)) {
        item(key = "header") {
            Column {
                DetailHeader(null, playlist.title, null, pluralStringResource(R.plurals.playlist_local_count, rows.size, rows.size))
                DetailActionRow(playlist.title, rows.isNotEmpty(), { actions.onPlay(0) }, actions.onShuffle) {
                    AddToQueueButton(playlist.title, rows.isNotEmpty(), actions.onAddToQueue)
                    IconButton({ renaming = true }, Modifier.size(48.dp).testTag(TAG_PLAYLIST_RENAME)) { Icon(AriaIcons.Edit, stringResource(R.string.playlist_rename)) }
                    IconButton({ confirmDelete = true }, Modifier.size(48.dp).testTag(TAG_PLAYLIST_DELETE)) { Icon(AriaIcons.Delete, stringResource(R.string.playlist_delete)) }
                }
            }
        }
        if (rows.isEmpty()) item(key = "empty") {
            Text(
                stringResource(R.string.playlist_empty_hint), Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Keyed by entry id: the same song may be in the playlist twice.
        itemsIndexed(rows, key = { _, r -> r.entryId }) { i, r ->
            val t = r.track
            TrackRow(
                t, onClick = { actions.onPlay(i) }, onMenu = { },
                modifier = Modifier.testTag("playlist:row:${r.entryId}"),
                showDefaultMenu = false, menuTag = "playlist:menu:${r.entryId}",
                disabledActions = buildSet { if (i == 0) add(R.string.queue_move_up); if (i == rows.lastIndex) add(R.string.queue_move_down) },
                extraActions = listOf(
                    R.string.track_play_next to { actions.onTrackMenu(t, TrackMenuAction.PLAY_NEXT) },
                    R.string.track_add_to_queue to { actions.onTrackMenu(t, TrackMenuAction.ADD_TO_QUEUE) },
                    R.string.queue_move_up to { actions.onMoveUp(r.entryId) },
                    R.string.queue_move_down to { actions.onMoveDown(r.entryId) },
                    R.string.playlist_remove_track to { actions.onRemove(r.entryId) },
                ),
            )
        }
    }
    if (renaming) NameDialog(R.string.playlist_name_title_rename, playlist.title, { renaming = false; actions.onRename(it) }, { renaming = false })
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.playlist_delete_title)) },
        text = { Text(playlist.title) },
        confirmButton = { TextButton({ confirmDelete = false; actions.onDelete() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.playlist_delete_confirm)) } },
        dismissButton = { TextButton({ confirmDelete = false }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.playlist_name_cancel)) } },
    )
}

@Composable
fun LocalPlaylistScreen(onBack: () -> Unit, viewModel: LocalPlaylistViewModel = hiltViewModel()) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val deleted = viewModel.deleted.collectAsStateWithLifecycle().value
    LaunchedEffect(deleted) { if (deleted) onBack() }
    val actions = remember(viewModel, onBack) {
        LocalPlaylistActions(
            onBack = onBack, onPlay = viewModel::play, onShuffle = viewModel::shuffle, onAddToQueue = viewModel::addToQueue,
            onRename = viewModel::rename, onDelete = viewModel::delete, onRemove = viewModel::remove,
            onMoveUp = viewModel::moveUp, onMoveDown = viewModel::moveDown, onTrackMenu = viewModel::trackAction,
        )
    }
    LocalPlaylistContent(state, actions)
}
