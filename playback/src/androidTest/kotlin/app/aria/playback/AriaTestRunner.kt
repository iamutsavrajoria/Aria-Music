// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Swaps in an Application that provides a real engine with fake sources, so the real service can be tested without Hilt. */
class AriaTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestAriaApp::class.java.name, context)
}
