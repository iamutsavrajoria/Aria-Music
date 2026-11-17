// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import app.aria.domain.PlaybackEngine
import app.aria.domain.SearchSource
import app.aria.music.image.LocalAriaImageLoader
import app.aria.music.ui.AriaTheme
import app.aria.music.ui.shell.AriaRoot
import app.aria.playback.AriaPlaybackService
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var search: SearchSource
    @Inject lateinit var engine: PlaybackEngine
    @Inject lateinit var imageLoader: ImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AriaTheme {
                // The app-wide default loader is the policy loader, but AriaImage only loads when a loader is provided here.
                CompositionLocalProvider(LocalAriaImageLoader provides imageLoader) {
                    AriaRoot(legacySearch = { PlayerScreen(search, engine) { AriaPlaybackService.start(this@MainActivity) } })
                }
            }
        }
    }
}
