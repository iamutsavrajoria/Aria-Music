// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aria.music.R
import app.aria.music.image.AriaImage
import app.aria.music.player.PlayerStatus
import app.aria.music.ui.common.AriaIcons

/** The Material skin of Now Playing. Other skins reuse [NowPlayingUiState], [NowPlayingActions] and the parts in NowPlayingParts.kt. */
@Composable
fun NowPlayingContent(state: NowPlayingUiState, positionMs: () -> Long, actions: NowPlayingActions, modifier: Modifier = Modifier) {
    when (state) {
        NowPlayingUiState.Empty -> NowPlayingEmpty(actions.onBack, modifier)
        is NowPlayingUiState.Content -> Playing(state, positionMs, actions, modifier)
    }
}

@Composable
private fun Playing(state: NowPlayingUiState.Content, positionMs: () -> Long, actions: NowPlayingActions, modifier: Modifier) {
    // Scrolls so every control stays reachable at large font scales and with long translations.
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack, modifier = Modifier.size(48.dp)) { Icon(AriaIcons.ArrowBack, stringResource(R.string.back)) }
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
        NowPlayingBanners(state.status, actions.onToggle)
        SeekBar(positionMs, state.durationMs, actions.onSeek)
        TransportRow(state, actions)
        SecondaryControls(state, actions)
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
