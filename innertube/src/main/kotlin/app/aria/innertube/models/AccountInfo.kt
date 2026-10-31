// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.models

data class AccountInfo(
    val name: String,
    val email: String?,
    val channelHandle: String?,
    val thumbnailUrl: String?,
)
