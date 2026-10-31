// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import app.aria.domain.PlayEventRepository
import app.aria.domain.SearchSource
import app.aria.domain.StreamSource
import app.aria.playback.ResumeStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var search: SearchSource
    @Inject lateinit var streams: StreamSource
    @Inject lateinit var events: PlayEventRepository
    @Inject lateinit var resume: ResumeStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SkeletonScreen(search, streams, events, resume) } }
    }
}
