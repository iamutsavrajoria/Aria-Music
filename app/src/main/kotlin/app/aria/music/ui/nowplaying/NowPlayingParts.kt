// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aria.domain.RepeatMode
import app.aria.music.R
import app.aria.music.player.PlayerStatus
import app.aria.music.ui.Formatters
import app.aria.music.ui.common.AriaIcons
import app.aria.music.ui.common.EmptyState

/** Pieces shared by every Now Playing skin, so the skins differ in layout only and never in behaviour or semantics. */

/** The empty state (nothing queued): a back button above the shared placeholder. [modifier] must give it a height. */
@Composable
internal fun NowPlayingEmpty(onBack: () -> Unit, modifier: Modifier = Modifier, backIcon: ImageVector = AriaIcons.ArrowBack) {
    Column(modifier) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(backIcon, stringResource(R.string.back)) }
        EmptyState(R.string.np_nothing_playing, modifier = Modifier.weight(1f))
    }
}

/** The offline notice and the error notice with its Retry. Shows nothing for any other status. */
@Composable
internal fun NowPlayingBanners(status: PlayerStatus, onRetry: () -> Unit) {
    if (status == PlayerStatus.WAITING_FOR_NETWORK) {
        Text(stringResource(R.string.np_offline_banner), Modifier.testTag(TAG_NP_OFFLINE), color = MaterialTheme.colorScheme.error)
    }
    if (status == PlayerStatus.ERROR) {
        Row(Modifier.fillMaxWidth().testTag(TAG_NP_ERROR), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.np_error_banner), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
        }
    }
}

/** Shuffle, repeat, like and the sleep menu in one row. */
@Composable
internal fun SecondaryControls(state: NowPlayingUiState.Content, actions: NowPlayingActions, modifier: Modifier = Modifier) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
        SleepMenu(state, actions, Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun ModeButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) { Icon(icon, description, tint = tint) }
}

@Composable
internal fun SleepMenu(state: NowPlayingUiState.Content, actions: NowPlayingActions, modifier: Modifier = Modifier) {
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
                text = { Text(pluralStringResource(R.plurals.np_sleep_in_minutes, state.sleepDefaultMinutes, state.sleepDefaultMinutes)) },
                onClick = { open = false; actions.onSleepMinutes(state.sleepDefaultMinutes) },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.np_sleep_end_of_track)) }, onClick = { open = false; actions.onSleepEndOfTrack() })
            DropdownMenuItem(text = { Text(stringResource(R.string.np_sleep_off)) }, onClick = { open = false; actions.onSleepOff() })
        }
    }
}

@Composable
internal fun SeekBar(positionMs: () -> Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val position = positionMs()                       // only this composable reads the position
    val duration = durationMs.coerceAtLeast(1L)
    val fraction = drag ?: (position.toFloat() / duration).coerceIn(0f, 1f)
    // Never show or commit more than the track length (a stale position can briefly exceed a fallback duration).
    val shownMs = (drag?.let { (it * duration).toLong() } ?: position).coerceIn(0L, duration)
    val desc = stringResource(R.string.np_seek_position, Formatters.duration(shownMs), Formatters.duration(durationMs))
    Column {
        Slider(
            value = fraction,
            onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { onSeek((it * duration).toLong().coerceIn(0L, duration)) }; drag = null },
            enabled = durationMs > 0,
            // The semantics node sits outside the sizing so its bounds are the 48dp target (the slider itself measures 44dp) and it merges the slider into one node.
            modifier = Modifier.testTag(TAG_NP_SEEK).semantics(mergeDescendants = true) { stateDescription = desc }.fillMaxWidth().heightIn(min = 48.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Formatters.duration(shownMs), style = MaterialTheme.typography.labelMedium)
            Text(Formatters.duration(durationMs), style = MaterialTheme.typography.labelMedium)
        }
    }
}
