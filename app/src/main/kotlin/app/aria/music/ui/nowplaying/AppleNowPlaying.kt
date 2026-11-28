// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aria.designsystem.GlassSurface
import app.aria.music.R
import app.aria.music.image.AriaImage
import app.aria.music.player.PlayerStatus
import app.aria.music.ui.common.AriaIcons

private val ArtworkShape = RoundedCornerShape(20.dp)

/**
 * The Apple-style skin of Now Playing: big rounded artwork with a shadow, centred title, and the seek bar and
 * a round play button on a glass panel. It adds no behaviour; every control, tag and description is the Material skin's.
 */
@Composable
fun AppleNowPlayingContent(state: NowPlayingUiState, positionMs: () -> Long, actions: NowPlayingActions, modifier: Modifier = Modifier) {
    when (state) {
        NowPlayingUiState.Empty -> NowPlayingEmpty(actions.onBack, modifier, AriaIcons.ExpandMore)
        is NowPlayingUiState.Content -> ApplePlaying(state, positionMs, actions, modifier)
    }
}

@Composable
private fun ApplePlaying(state: NowPlayingUiState.Content, positionMs: () -> Long, actions: NowPlayingActions, modifier: Modifier) {
    // Scrolls so every control stays reachable at large font scales and with long translations.
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack, modifier = Modifier.size(48.dp)) { Icon(AriaIcons.ExpandMore, stringResource(R.string.back)) }
            IconButton(onClick = actions.onOpenQueue, modifier = Modifier.size(48.dp).testTag(TAG_NP_QUEUE)) {
                Icon(AriaIcons.QueueMusic, stringResource(R.string.np_queue))
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            AriaImage(
                state.track.thumbnailUrl, null,
                Modifier.widthIn(max = 360.dp).fillMaxWidth(0.86f).aspectRatio(1f).shadow(24.dp, ArtworkShape).clip(ArtworkShape),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            state.track.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
            state.track.artists.joinToString(stringResource(R.string.artist_separator)), Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        NowPlayingBanners(state.status, actions.onToggle)
        GlassSurface(Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(32.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SeekBar(positionMs, state.durationMs, actions.onSeek)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = actions.onPrevious, modifier = Modifier.size(56.dp)) { Icon(AriaIcons.SkipPrevious, stringResource(R.string.action_previous), Modifier.size(32.dp)) }
                    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                        FilledIconButton(onClick = actions.onToggle, modifier = Modifier.size(72.dp).testTag(TAG_NP_PLAY)) {
                            Icon(
                                if (state.isPlaying) AriaIcons.Pause else AriaIcons.PlayArrow,
                                contentDescription = stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        // Same buffering cue as the Material skin; the button keeps its Pause description.
                        if (state.status == PlayerStatus.BUFFERING) CircularProgressIndicator(Modifier.size(80.dp), strokeWidth = 3.dp)
                    }
                    IconButton(onClick = actions.onNext, modifier = Modifier.size(56.dp)) { Icon(AriaIcons.SkipNext, stringResource(R.string.action_next), Modifier.size(32.dp)) }
                }
            }
        }
        SecondaryControls(state, actions, Modifier.padding(top = 12.dp))
        LyricsSlot(Modifier.testTag(TAG_NP_LYRICS))
    }
}
