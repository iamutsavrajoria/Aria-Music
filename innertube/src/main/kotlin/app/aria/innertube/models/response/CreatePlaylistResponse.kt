// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class CreatePlaylistResponse(
    val playlistId: String
)
