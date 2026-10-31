// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.app.Application
import android.os.Build
import app.aria.core.crash.CrashReportFormatter
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class AriaApp : Application() {
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
    }
}
