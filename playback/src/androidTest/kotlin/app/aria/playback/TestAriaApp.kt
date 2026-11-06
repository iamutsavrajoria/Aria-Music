// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.app.Application
import kotlinx.coroutines.Dispatchers

class TestAriaApp : Application(), PlaybackEngineProvider {
    override val allowLocalStreams: Boolean = true   // this test app serves a local WAV; production never does
    override val playbackEngine: DefaultPlaybackEngine by lazy {
        val wav = SilentWav.write(cacheDir)
        DefaultPlaybackEngine(ServiceStreams("file://${wav.absolutePath}"), NoopStore(), NoopRadio(), NoopHistory(), AlwaysOnline(), Dispatchers.Main.immediate)
    }
}
