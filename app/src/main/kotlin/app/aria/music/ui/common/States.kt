// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aria.music.R

const val TAG_LOADING = "state:loading"
const val TAG_EMPTY = "state:empty"
const val TAG_ERROR = "state:error"
const val TAG_OFFLINE = "state:offline"

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.loading)
    Box(modifier.fillMaxSize().padding(24.dp).testTag(TAG_LOADING), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}

@Composable
fun EmptyState(@StringRes title: Int, @StringRes body: Int? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp).testTag(TAG_EMPTY),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) =
    MessageWithRetry(TAG_ERROR, R.string.error_title, R.string.error_body, onRetry, modifier)

@Composable
fun OfflineState(onRetry: () -> Unit, modifier: Modifier = Modifier) =
    MessageWithRetry(TAG_OFFLINE, R.string.offline_title, R.string.offline_body, onRetry, modifier)

@Composable
private fun MessageWithRetry(tag: String, @StringRes title: Int, @StringRes body: Int, onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
    }
}
