// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ReturnYouTubeDislikeResponse(
    val id: String? = null,
    val dateCreated: String? = null,
    val likes: Int? = null,
    val dislikes: Int? = null,
    val rating: Double? = null,
    val viewCount: Int? = null,
    val deleted: Boolean? = null,
)
