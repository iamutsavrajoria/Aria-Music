// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ContinuationItemRenderer(
    val continuationEndpoint: ContinuationEndpoint?,
) {
    @Serializable
    data class ContinuationEndpoint(
        val continuationCommand: ContinuationCommand?,
    ) {
        @Serializable
        data class ContinuationCommand(
            val token: String?,
        )
    }
}