// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aria.domain.NowPlayingSkin

const val TAG_NP_PLAY = "np:play"
const val TAG_NP_SEEK = "np:seek"
const val TAG_NP_LIKE = "np:like"
const val TAG_NP_QUEUE = "np:queue"
const val TAG_NP_SLEEP = "np:sleep"
const val TAG_NP_ERROR = "np:error"
const val TAG_NP_OFFLINE = "np:offline"
const val TAG_NP_LYRICS = "np:lyrics"

@Composable
fun NowPlayingScreen(onBack: () -> Unit, onOpenQueue: () -> Unit, viewModel: NowPlayingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Only the seek bar invokes this lambda, so a position tick never recomposes the screen itself.
    val position = viewModel.positionMs.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onBack, onOpenQueue) { viewModel.actions(onBack, onOpenQueue) }
    val skin by viewModel.skin.collectAsStateWithLifecycle()
    when (skin) {
        NowPlayingSkin.MATERIAL -> NowPlayingContent(state, { position.value }, actions)
        NowPlayingSkin.APPLE -> AppleNowPlayingContent(state, { position.value }, actions)
    }
}

/** Reserved for synced lyrics; filled by sub-project 4. Intentionally empty. */
@Composable
fun LyricsSlot(modifier: Modifier = Modifier) {
    Box(modifier)
}
