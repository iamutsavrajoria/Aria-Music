// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aria.domain.RepeatMode
import app.aria.music.R
import app.aria.music.image.AriaImage
import app.aria.music.player.PlayerStatus
import app.aria.music.ui.Formatters
import app.aria.music.ui.common.AriaIcons
import app.aria.music.ui.common.EmptyState

/** The Material skin of Now Playing. Other skins reuse [NowPlayingUiState] and [NowPlayingActions]. */
@Composable
fun NowPlayingContent(state: NowPlayingUiState, positionMs: () -> Long, actions: NowPlayingActions, modifier: Modifier = Modifier) {
    when (state) {
        NowPlayingUiState.Empty -> Column(modifier) {
            BackButton(actions.onBack)
            EmptyState(R.string.np_nothing_playing, modifier = Modifier.weight(1f))
        }
        is NowPlayingUiState.Content -> Playing(state, positionMs, actions, modifier)
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(AriaIcons.ArrowBack, stringResource(R.string.back)) }
}

@Composable
private fun Playing(state: NowPlayingUiState.Content, positionMs: () -> Long, actions: NowPlayingActions, modifier: Modifier) {
    // Scrolls so every control stays reachable at large font scales and with long translations.
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BackButton(actions.onBack)
            IconButton(onClick = actions.onOpenQueue, modifier = Modifier.size(48.dp).testTag(TAG_NP_QUEUE)) {
                Icon(AriaIcons.QueueMusic, stringResource(R.string.np_queue))
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AriaImage(state.track.thumbnailUrl, null, Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
        }
        Column {
            Text(state.track.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                state.track.artists.joinToString(stringResource(R.string.artist_separator)),
                style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.status == PlayerStatus.WAITING_FOR_NETWORK) {
            Text(stringResource(R.string.np_offline_banner), Modifier.testTag(TAG_NP_OFFLINE), color = MaterialTheme.colorScheme.error)
        }
        if (state.status == PlayerStatus.ERROR) {
            Row(Modifier.fillMaxWidth().testTag(TAG_NP_ERROR), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.np_error_banner), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = actions.onToggle, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
            }
        }
        SeekBar(positionMs, state.durationMs, actions.onSeek)
        TransportRow(state, actions)
        ModeRow(state, actions)
        LyricsSlot(Modifier.testTag(TAG_NP_LYRICS))
    }
}

@Composable
private fun TransportRow(state: NowPlayingUiState.Content, actions: NowPlayingActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onPrevious, modifier = Modifier.size(48.dp)) { Icon(AriaIcons.SkipPrevious, stringResource(R.string.action_previous)) }
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            FilledIconButton(onClick = actions.onToggle, modifier = Modifier.size(64.dp).testTag(TAG_NP_PLAY)) {
                Icon(
                    if (state.isPlaying) AriaIcons.Pause else AriaIcons.PlayArrow,
                    contentDescription = stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                )
            }
            // A ring around the button while buffering; the button keeps its Pause description.
            if (state.status == PlayerStatus.BUFFERING) CircularProgressIndicator(Modifier.size(72.dp), strokeWidth = 3.dp)
        }
        IconButton(onClick = actions.onNext, modifier = Modifier.size(48.dp)) { Icon(AriaIcons.SkipNext, stringResource(R.string.action_next)) }
    }
}

@Composable
private fun ModeRow(state: NowPlayingUiState.Content, actions: NowPlayingActions) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        ModeButton(
            AriaIcons.Shuffle, stringResource(if (state.shuffle) R.string.np_shuffle_on else R.string.np_shuffle_off),
            if (state.shuffle) on else off, { actions.onShuffle(!state.shuffle) },
        )
        ModeButton(
            if (state.repeat == RepeatMode.ONE) AriaIcons.RepeatOne else AriaIcons.Repeat,
            stringResource(when (state.repeat) { RepeatMode.OFF -> R.string.np_repeat_off; RepeatMode.ALL -> R.string.np_repeat_all; RepeatMode.ONE -> R.string.np_repeat_one }),
            if (state.repeat == RepeatMode.OFF) off else on, actions.onRepeat,
        )
        ModeButton(
            if (state.liked) AriaIcons.Favorite else AriaIcons.FavoriteBorder,
            stringResource(if (state.liked) R.string.np_unlike else R.string.np_like),
            if (state.liked) on else off, actions.onLike, Modifier.testTag(TAG_NP_LIKE),
        )
        SleepButton(state, actions, Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun ModeButton(icon: ImageVector, description: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) { Icon(icon, description, tint = tint) }
}

@Composable
private fun SleepButton(state: NowPlayingUiState.Content, actions: NowPlayingActions, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val timer = state.sleepTimer
    Box(modifier) {
        TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp).testTag(TAG_NP_SLEEP)) {
            Text(
                stringResource(when { timer == null -> R.string.np_sleep; timer.endOfTrack -> R.string.np_sleep_end_active; else -> R.string.np_sleep_active }),
                textAlign = TextAlign.Center,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.np_sleep_in_minutes, state.sleepDefaultMinutes)) },
                onClick = { open = false; actions.onSleepMinutes(state.sleepDefaultMinutes) },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.np_sleep_end_of_track)) }, onClick = { open = false; actions.onSleepEndOfTrack() })
            DropdownMenuItem(text = { Text(stringResource(R.string.np_sleep_off)) }, onClick = { open = false; actions.onSleepOff() })
        }
    }
}

@Composable
private fun SeekBar(positionMs: () -> Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val position = positionMs()                       // only this composable reads the position
    val duration = durationMs.coerceAtLeast(1L)
    val fraction = drag ?: (position.toFloat() / duration).coerceIn(0f, 1f)
    val shownMs = drag?.let { (it * duration).toLong() } ?: position
    val desc = stringResource(R.string.np_seek_position, Formatters.duration(shownMs), Formatters.duration(durationMs))
    Column {
        Slider(
            value = fraction,
            onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { onSeek((it * duration).toLong()) }; drag = null },
            enabled = durationMs > 0,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(TAG_NP_SEEK).semantics { stateDescription = desc },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Formatters.duration(shownMs), style = MaterialTheme.typography.labelMedium)
            Text(Formatters.duration(durationMs), style = MaterialTheme.typography.labelMedium)
        }
    }
}
