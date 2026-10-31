// SPDX-License-Identifier: GPL-3.0-only
package app.aria.designsystem

import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext

/**
 * Glass when the device and mode allow it, otherwise a translucent Surface of the same shape.
 * The fallback is a first-class design, not an error state.
 *
 * The glass lens effect only supports [CornerBasedShape]; any other shape takes the fallback.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    mode: GlassMode = GlassMode.AUTO,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val batterySaver = (context.getSystemService(PowerManager::class.java))?.isPowerSaveMode == true
    val glass = shape is CornerBasedShape && GlassResolver.resolve(mode, Build.VERSION.SDK_INT, batterySaver)
    if (glass) {
        Box(modifier = modifier.liquidGlass(config = LocalGlassEffectConfig.current, shape = shape as CornerBasedShape, applyEdgeEffects = true)) {
            content()
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AriaTokens.glassFallbackAlpha),
            content = content,
        )
    }
}
