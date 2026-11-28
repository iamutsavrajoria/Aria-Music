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

    // A real dismissal (Cancel, Done, tap outside) must not leave a half-created playlist for the next dialog to reuse.
    // Disposal is not used for this: a rotation disposes too, and would lose the playlist a retry has to reuse.
    val dismiss = { viewModel.dismissed(); onDismiss() }

    /** [onSettled] runs when the add had an outcome other than Busy (a Busy tap changed nothing, so whatever the person typed stays on screen). */
    fun run(name: String, onSettled: () -> Unit = {}, block: suspend () -> PlaylistPickerViewModel.Outcome) {
        scope.launch {
            val next = try { PickerState(added, failed).after(block(), name) } catch (e: CancellationException) { throw e } catch (e: Exception) { PickerState(added, failed = true) }
            if (next != null) { added = next.added; failed = next.failed; onSettled() }
        }
    }

    if (naming && added == null) {
        NameDialog(R.string.playlist_name_title_new, "", onConfirm = { raw ->
            // Stays open until the add settles: if another add is in flight the tap is ignored and the typed name is not lost.
            run(PlaylistNames.clean(raw).orEmpty(), onSettled = { naming = false }) { viewModel.createAndAdd(raw, track) }
        }, onDismiss = { naming = false })
        return
    }
    AlertDialog(
        onDismissRequest = dismiss,
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
                        PickerRow(p.title, Modifier.testTag("picker:playlist:${p.id}")) { run(p.title) { viewModel.addTo(p.id, track) } }
                    }
                }
            }
        },
        confirmButton = {
            if (added != null) TextButton(dismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.playlist_done)) }
        },
        dismissButton = {
            if (added == null) TextButton(dismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.playlist_name_cancel)) }
        },
    )
}

/** What the dialog shows after an add. */
internal data class PickerState(val added: String?, val failed: Boolean) {
    /** The state after [outcome], or null when nothing changed (Busy: another add is running, so the typed name must stay on screen). */
    fun after(outcome: PlaylistPickerViewModel.Outcome, name: String): PickerState? = when (outcome) {
        is PlaylistPickerViewModel.Outcome.Added -> PickerState(name, failed = false)
        PlaylistPickerViewModel.Outcome.Busy -> null
        PlaylistPickerViewModel.Outcome.BlankName -> copy(failed = true)
    }
}

@Composable
private fun PickerRow(title: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Text(title, Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
