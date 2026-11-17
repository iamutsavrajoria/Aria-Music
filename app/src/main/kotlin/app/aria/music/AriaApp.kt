// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.app.Application
import android.os.Build
import app.aria.core.crash.CrashReportFormatter
import app.aria.music.image.AriaImageLoader
import app.aria.music.player.EngineSettingsApplier
import app.aria.music.player.PlayerController
import app.aria.playback.AriaPlaybackService
import app.aria.playback.DefaultPlaybackEngine
import app.aria.playback.PlaybackEngineProvider
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class AriaApp : Application(), PlaybackEngineProvider, SingletonImageLoader.Factory {
    @Inject lateinit var engine: DefaultPlaybackEngine
    @Inject lateinit var settingsApplier: EngineSettingsApplier
    // Injected here because the controller is a lazy singleton: instantiating it at startup means early engine events are not lost.
    @Inject lateinit var playerController: PlayerController
    override val playbackEngine: DefaultPlaybackEngine get() = engine

    /** The app-wide default Coil loader is the policy loader, so a request that forgets `imageLoader =` still passes the URL gate. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = AriaImageLoader.create(context)

    override fun onCreate() {
        super.onCreate()
        // Release builds plant no log tree at all, so Timber calls are no-ops there; nothing logs tokens or cookies.
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
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
        // Transport commands after a restore or an idle stop find no service; this starts it (the UI's own start is harmless alongside).
        engine.serviceStarter = { AriaPlaybackService.start(this) }
        settingsApplier.start()
        MainScope().launch { engine.restore() }
    }
}
