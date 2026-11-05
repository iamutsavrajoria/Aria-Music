// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.app.Application
import android.os.Build
import app.aria.core.crash.CrashReportFormatter
import app.aria.playback.DefaultPlaybackEngine
import app.aria.playback.PlaybackEngineProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class AriaApp : Application(), PlaybackEngineProvider {
    @Inject lateinit var engine: DefaultPlaybackEngine
    override val playbackEngine: DefaultPlaybackEngine get() = engine

    override fun onCreate() {
        super.onCreate()
        // Release builds plant no verbose log tree; nothing logs tokens or cookies.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val dir = File(filesDir, "crash").apply { mkdirs() }
                File(dir, "last.txt").writeText(
                    CrashReportFormatter.format(error, BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, BuildConfig.FLAVOR),
                )
            }
            previous?.uncaughtException(thread, error)
        }
        MainScope().launch { engine.restore() }
    }
}
