// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import java.util.Locale

/** Test wrapper: optional en-XA pseudolocale (long accented strings) and font scale, on the app theme without dynamic color. */
@Composable
fun AriaTestTheme(pseudo: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseCfg = LocalConfiguration.current
    val cfg = remember(baseCfg, pseudo, fontScale) {
        Configuration(baseCfg).apply { if (pseudo) setLocale(Locale.forLanguageTag("en-XA")); this.fontScale = fontScale }
    }
    val ctx = remember(cfg) { base.createConfigurationContext(cfg) }
    CompositionLocalProvider(
        LocalContext provides ctx, LocalConfiguration provides cfg, LocalDensity provides Density(ctx.resources.displayMetrics.density, fontScale),
    ) { AriaTheme(dynamic = false, content = content) }
}
