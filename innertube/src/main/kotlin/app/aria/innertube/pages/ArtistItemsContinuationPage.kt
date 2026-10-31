// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.pages

import app.aria.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
