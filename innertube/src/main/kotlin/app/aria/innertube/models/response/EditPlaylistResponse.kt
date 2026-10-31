// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class EditPlaylistResponse(
    val newHeader: BrowseResponse.Header?,
)
