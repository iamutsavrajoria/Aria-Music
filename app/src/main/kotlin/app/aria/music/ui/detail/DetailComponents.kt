// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aria.music.R
import app.aria.music.image.AriaImage
import app.aria.music.ui.common.AriaIcons

const val TAG_DETAIL_SAVE = "detail:save"
const val TAG_DETAIL_PLAY = "detail:play"
const val TAG_DETAIL_SHUFFLE = "detail:shuffle"
const val TAG_DETAIL_MORE_FAILED = "detail:more-failed"
const val TAG_DETAIL_LIST = "detail:list"

/**
 * Back button on top, the page below. [content] gets a bounded slot, so the shared Loading, Error and Offline states can
 * fill it and the lists scroll inside it (no statusBarsPadding: the app has one inset policy, see the shell).
 */
@Composable
internal fun DetailFrame(onBack: () -> Unit, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Column(modifier.fillMaxSize()) {
        IconButton(onBack, Modifier.padding(start = 4.dp).size(48.dp)) { Icon(AriaIcons.ArrowBack, stringResource(R.string.back)) }
        Box(Modifier.weight(1f).fillMaxWidth(), content = content)
    }
}

@Composable
internal fun DetailHeader(thumbnailUrl: String?, title: String, subtitle: String?, meta: String?, round: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AriaImage(thumbnailUrl, null, Modifier.size(160.dp).clip(if (round) CircleShape else RoundedCornerShape(8.dp)))
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (!meta.isNullOrBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * Play and Shuffle side by side (their text wraps, so big fonts and long translations never clip), with [trailing] icon buttons on a row of their own.
 * The spoken names carry the noun ("Play Album 1"); the visible words stay short.
 */
@Composable
internal fun DetailActionRow(title: String, enabled: Boolean, onPlay: () -> Unit, onShuffle: () -> Unit, trailing: @Composable RowScope.() -> Unit) {
    val playDescription = stringResource(R.string.detail_play_named, title)
    val shuffleDescription = stringResource(R.string.detail_shuffle_named, title)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onPlay, Modifier.weight(1f).heightIn(min = 48.dp).testTag(TAG_DETAIL_PLAY).semantics { contentDescription = playDescription }, enabled = enabled) {
                Text(stringResource(R.string.detail_play))
            }
            OutlinedButton(onShuffle, Modifier.weight(1f).heightIn(min = 48.dp).testTag(TAG_DETAIL_SHUFFLE).semantics { contentDescription = shuffleDescription }, enabled = enabled) {
                Text(stringResource(R.string.detail_shuffle))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
internal fun AddToQueueButton(title: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp), enabled = enabled) { Icon(AriaIcons.QueueMusic, stringResource(R.string.detail_add_to_queue_named, title)) }
}

/** The heart toggle of Save to library and Follow; [on] picks the icon and the spoken name. */
@Composable
internal fun HeartToggle(on: Boolean, onDescription: String, offDescription: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp).testTag(TAG_DETAIL_SAVE)) {
        Icon(if (on) AriaIcons.Favorite else AriaIcons.FavoriteBorder, if (on) onDescription else offDescription)
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(
        title, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}
