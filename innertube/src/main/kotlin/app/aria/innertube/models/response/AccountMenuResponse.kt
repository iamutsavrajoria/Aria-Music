// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.models.response

import app.aria.innertube.models.AccountInfo
import app.aria.innertube.models.Runs
import app.aria.innertube.models.Thumbnails
import app.aria.innertube.models.Thumbnail
import kotlinx.serialization.Serializable

@Serializable
data class AccountMenuResponse(
    val actions: List<Action>,
) {
    @Serializable
    data class Action(
        val openPopupAction: OpenPopupAction,
    ) {
        @Serializable
        data class OpenPopupAction(
            val popup: Popup,
        ) {
            @Serializable
            data class Popup(
                val multiPageMenuRenderer: MultiPageMenuRenderer,
            ) {
                @Serializable
                data class MultiPageMenuRenderer(
                    val header: Header?,
                ) {
                    @Serializable
                    data class Header(
                        val activeAccountHeaderRenderer: ActiveAccountHeaderRenderer,
                    ) {
                        @Serializable
                        data class ActiveAccountHeaderRenderer(
                            val accountName: Runs,
                            val email: Runs?,
                            val channelHandle: Runs?,
                            val accountPhoto: Thumbnails,
                        ) {
                            fun toAccountInfo() =
                                AccountInfo(
                                    name = accountName.runs!!.first().text,
                                    email = email?.runs?.first()?.text,
                                    channelHandle = channelHandle?.runs?.first()?.text,
                                    thumbnailUrl = accountPhoto.thumbnails.lastOrNull()?.url,
                                )
                        }
                    }
                }
            }
        }
    }
}
