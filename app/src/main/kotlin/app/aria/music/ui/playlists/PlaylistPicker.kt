// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aria.domain.Track
import app.aria.music.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

const val TAG_PICKER_NEW = "picker:new"

/** Lists the user's playlists plus "New playlist"; choosing one adds [track], says "Added to <name>" and waits for Done. */
@Composable
fun AddToPlaylistDialog(track: Track, onDismiss: () -> Unit, viewModel: PlaylistPickerViewModel = hiltViewModel()) {
    val playlists = viewModel.playlists.collectAsStateWithLifecycle().value
    val scope = rememberCoroutineScope()
    var added by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }

    fun run(name: String, block: suspend () -> Boolean) {
        scope.launch {
            try { if (block()) { added = name; failed = false } else failed = true } catch (e: CancellationException) { throw e } catch (e: Exception) { failed = true }
        }
    }

    if (naming) {
        NameDialog(R.string.playlist_name_title_new, "", onConfirm = { raw ->
            naming = false
            run(PlaylistNames.clean(raw).orEmpty()) { viewModel.createAndAdd(raw, track) != null }
        }, onDismiss = { naming = false })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_pick_title)) },
        text = {
            val done = added
            if (done != null) {
                Text(stringResource(R.string.playlist_added_to, done), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    if (failed) item(key = "failed") {
                        Text(stringResource(R.string.playlist_add_failed), Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    item(key = "new") { PickerRow(stringResource(R.string.playlist_new), Modifier.testTag(TAG_PICKER_NEW)) { naming = true } }
                    items(playlists, key = { "p:" + it.id }) { p ->
                        PickerRow(p.title, Modifier.testTag("picker:playlist:${p.id}")) { run(p.title) { viewModel.addTo(p.id, track); true } }
                    }
                }
            }
        },
        confirmButton = {
            if (added != null) TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.playlist_done)) }
        },
        dismissButton = {
            if (added == null) TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.playlist_name_cancel)) }
        },
    )
}

@Composable
private fun PickerRow(title: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Text(title, Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
