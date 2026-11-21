// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aria.music.R
import app.aria.music.image.AriaImage
import app.aria.music.player.PlayerController
import app.aria.music.ui.Formatters
import app.aria.music.ui.common.AriaIcons
import app.aria.music.ui.common.EmptyState

const val TAG_QUEUE_LIST = "queue:list"

@Composable
fun QueueScreen(onBack: () -> Unit, viewModel: QueueViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onBack) {
        QueueActions(onBack, viewModel::play, viewModel::remove, viewModel::moveUp, viewModel::moveDown, viewModel::clear)
    }
    QueueContent(state, actions)
}

@Composable
fun QueueContent(state: QueueUiState, actions: QueueActions, modifier: Modifier = Modifier) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val rows = (state as? QueueUiState.Content)?.rows.orEmpty()
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(actions.onBack, Modifier.size(48.dp)) { Icon(AriaIcons.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.queue_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        }
        if (rows.isNotEmpty()) {
            // Count and Clear sit below the title (not beside it) so large fonts and long translations cannot squeeze the list out.
            val count = pluralStringResource(R.plurals.queue_count, rows.size, Formatters.count(rows.size))
            val full = rows.size >= PlayerController.QUEUE_CAP
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    if (full) stringResource(R.string.queue_count_at_limit, count, Formatters.count(PlayerController.QUEUE_CAP)) else count,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton({ confirmClear = true }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.queue_clear)) }
            }
        }
        when (state) {
            QueueUiState.Empty -> EmptyState(R.string.queue_empty)
            is QueueUiState.Content -> LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(TAG_QUEUE_LIST)) {
                // queueId is unique per queue entry (the engine assigns one per item), so duplicates of a song keep distinct stable keys.
                items(state.rows, key = { it.queueId }) { QueueRowItem(it, state.rows.lastIndex, actions) }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.queue_clear_title)) },
            confirmButton = { TextButton({ confirmClear = false; actions.onClear() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.queue_clear_confirm)) } },
            dismissButton = { TextButton({ confirmClear = false }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.notif_not_now)) } },
        )
    }
}

@Composable
private fun QueueRowItem(row: QueueRow, lastIndex: Int, actions: QueueActions) {
    val artists = row.track.artists.joinToString(stringResource(R.string.artist_separator))
    val nowPlaying = stringResource(R.string.queue_now_playing)
    val description = if (row.isCurrent) nowPlaying + stringResource(R.string.artist_separator) + row.track.title else null
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().testTag("queue:row:${row.queueId}").heightIn(min = 56.dp)
            .clickable { actions.onPlay(row.index) }
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AriaImage(row.track.thumbnailUrl, null, Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                row.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (row.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(artists, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        row.track.durationMs?.let { Text(Formatters.duration(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton({ menu = true }, Modifier.size(48.dp).testTag("queue:menu:${row.queueId}")) {
            Icon(AriaIcons.MoreVert, stringResource(R.string.queue_row_more, row.track.title))
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.queue_play)) }, { menu = false; actions.onPlay(row.index) })
            DropdownMenuItem({ Text(stringResource(R.string.queue_move_up)) }, { menu = false; actions.onMoveUp(row.index) }, enabled = row.index > 0)
            DropdownMenuItem({ Text(stringResource(R.string.queue_move_down)) }, { menu = false; actions.onMoveDown(row.index) }, enabled = row.index < lastIndex)
            DropdownMenuItem({ Text(stringResource(R.string.queue_remove)) }, { menu = false; actions.onRemove(row.index) })
        }
    }
}
