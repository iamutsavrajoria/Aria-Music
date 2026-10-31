// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicDescriptionShelfRenderer(
    val header: Runs?,
    val subheader: Runs?,
    val description: Runs?,
    val footer: Runs?,
)
