// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import app.aria.domain.PlaybackEngine
import app.aria.domain.SearchSource
import app.aria.playback.AriaPlaybackService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var search: SearchSource
    @Inject lateinit var engine: PlaybackEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PlayerScreen(search, engine) { AriaPlaybackService.start(this@MainActivity) }
            }
        }
    }
}
