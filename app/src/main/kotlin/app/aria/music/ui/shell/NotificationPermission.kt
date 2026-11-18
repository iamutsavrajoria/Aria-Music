// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.aria.music.R

object NotificationPermissionPolicy {
    fun shouldExplain(sdk: Int, granted: Boolean, alreadyAsked: Boolean, hasQueue: Boolean): Boolean =
        sdk >= 33 && !granted && !alreadyAsked && hasQueue
}

/** Explains why the notification permission is wanted at the first moment a queue exists; "Not now" dismisses it for the session. */
@Composable
fun NotificationPermissionGate(hasQueue: Boolean) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val granted = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (NotificationPermissionPolicy.shouldExplain(Build.VERSION.SDK_INT, granted, asked, hasQueue)) {
        NotificationPermissionDialog(
            onAllow = { asked = true; launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            onNotNow = { asked = true },
        )
    }
}

/** Stateless so the button wiring can be tested without the system permission dialog. Dismissing counts as "Not now". */
@Composable
fun NotificationPermissionDialog(onAllow: () -> Unit, onNotNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(stringResource(R.string.notif_title)) },
        text = { Text(stringResource(R.string.notif_body)) },
        confirmButton = { TextButton(onClick = onAllow) { Text(stringResource(R.string.notif_allow)) } },
        dismissButton = { TextButton(onClick = onNotNow) { Text(stringResource(R.string.notif_not_now)) } },
    )
}
