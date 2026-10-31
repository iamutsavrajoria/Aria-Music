// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class UrlEndpoint(
    val url: String? = null,
    val target: String? = null,
)
