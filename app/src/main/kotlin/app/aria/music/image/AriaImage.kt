// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage

/** No loader provided means no image request is ever made (tests, previews). MainActivity provides the real one. */
val LocalAriaImageLoader: ProvidableCompositionLocal<ImageLoader?> = staticCompositionLocalOf { null }

/** The URL to load, or null when a neutral placeholder must be shown instead (null URL, no loader, URL not allowed). */
internal fun imageModelOrNull(url: String?, hasLoader: Boolean): String? =
    if (url == null || !hasLoader || !ImageUrlPolicy.isAllowed(url)) null else url

/** Artwork is decorative in lists (the title is read next to it); pass a description only where the image carries meaning. */
@Composable
fun AriaImage(url: String?, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val loader = LocalAriaImageLoader.current
    val model = imageModelOrNull(url, loader != null)
    if (model == null || loader == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    AsyncImage(model = model, contentDescription = contentDescription, imageLoader = loader, modifier = modifier, contentScale = contentScale)
}
