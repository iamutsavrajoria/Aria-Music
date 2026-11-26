// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.aria.music.R

const val TAG_NAME_FIELD = "name:field"

/** Asks for a playlist name; Save stays disabled until the name has a visible character, and the confirmed name is already cleaned. */
@Composable
fun NameDialog(@StringRes title: Int, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.take(PlaylistNames.MAX)) }
    val clean = PlaylistNames.clean(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(PlaylistNames.MAX) },
                modifier = Modifier.fillMaxWidth().testTag(TAG_NAME_FIELD),
                singleLine = true,
                label = { Text(stringResource(R.string.playlist_name_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton({ clean?.let(onConfirm) }, Modifier.heightIn(min = 48.dp), enabled = clean != null) { Text(stringResource(R.string.playlist_name_confirm)) }
        },
        dismissButton = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.playlist_name_cancel)) } },
    )
}
