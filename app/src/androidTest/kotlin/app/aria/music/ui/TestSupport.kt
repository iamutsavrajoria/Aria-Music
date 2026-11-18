// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Density
import java.util.Locale

/** [base] with the en-XA pseudolocale (long accented strings) applied; its resources return the pseudolocalized strings. */
fun pseudoContext(base: Context): Context =
    base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("en-XA")) })

/**
 * Test wrapper: optional en-XA pseudolocale and font scale, on the app theme without dynamic color.
 * stringResource reads LocalResources, not LocalContext, so all three locals are provided together.
 */
@Composable
fun AriaTestTheme(pseudo: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseCfg = LocalConfiguration.current
    val cfg = remember(baseCfg, pseudo, fontScale) {
        Configuration(baseCfg).apply { if (pseudo) setLocale(Locale.forLanguageTag("en-XA")); this.fontScale = fontScale }
    }
    val ctx = remember(cfg) { base.createConfigurationContext(cfg) }
    // The default owner is found by walking LocalContext to the Activity; the wrapped context hides it, so keep the real one.
    val registryOwner = LocalActivityResultRegistryOwner.current
    CompositionLocalProvider(
        *listOfNotNull(registryOwner?.let { LocalActivityResultRegistryOwner provides it }).toTypedArray(),
        LocalContext provides ctx, LocalConfiguration provides cfg, LocalResources provides ctx.resources,
        LocalDensity provides Density(ctx.resources.displayMetrics.density, fontScale),
    ) { AriaTheme(dynamic = false, content = content) }
}
