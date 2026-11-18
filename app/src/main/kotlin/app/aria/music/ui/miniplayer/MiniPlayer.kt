// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.miniplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aria.designsystem.GlassSurface
import app.aria.music.R
import app.aria.music.image.AriaImage
import app.aria.music.player.PlayerStatus
import app.aria.music.ui.common.AriaIcons

const val TAG_MINI_PLAYER = "miniplayer"
const val TAG_MINI_PLAY = "miniplayer:play"
const val TAG_MINI_NEXT = "miniplayer:next"

@Composable
fun MiniPlayerContent(
    state: MiniPlayerUiState.Showing,
    positionMs: () -> Long,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val problem = stringResource(R.string.state_playback_problem)
    val waiting = stringResource(R.string.state_waiting_for_network)
    val buffering = stringResource(R.string.state_buffering)
    val open = stringResource(R.string.mini_open_now_playing, state.title)
    GlassSurface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag(TAG_MINI_PLAYER).semantics {
            when (state.status) {
                PlayerStatus.ERROR -> stateDescription = problem
                PlayerStatus.WAITING_FOR_NETWORK -> stateDescription = waiting
                PlayerStatus.BUFFERING -> stateDescription = buffering
                else -> Unit
            }
        },
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                // One merged target: artwork + title + artists open Now Playing.
                Row(
                    Modifier.weight(1f).heightIn(min = 56.dp).clickable(onClickLabel = open, role = Role.Button, onClick = onExpand)
                        .semantics { contentDescription = open },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AriaImage(state.thumbnailUrl, null, Modifier.padding(8.dp).size(40.dp).clip(RoundedCornerShape(6.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            state.artists.joinToString(stringResource(R.string.artist_separator)),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(48.dp).testTag(TAG_MINI_PLAY)) {
                    Icon(
                        if (state.isPlaying) AriaIcons.Pause else AriaIcons.PlayArrow,
                        contentDescription = stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp).testTag(TAG_MINI_NEXT)) {
                    Icon(AriaIcons.SkipNext, contentDescription = stringResource(R.string.action_next))
                }
            }
            // positionMs() is read inside the progress lambda, so only this draw scope re-reads it on each tick.
            LinearProgressIndicator(
                progress = { if (state.durationMs <= 0) 0f else (positionMs().toFloat() / state.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun MiniPlayerHost(onExpand: () -> Unit, viewModel: MiniPlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val position = viewModel.positionMs.collectAsStateWithLifecycle()
    (state as? MiniPlayerUiState.Showing)?.let { MiniPlayerContent(it, { position.value }, viewModel::toggle, viewModel::next, onExpand) }
}
