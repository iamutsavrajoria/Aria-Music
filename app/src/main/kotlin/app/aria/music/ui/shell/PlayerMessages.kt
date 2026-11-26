// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import android.content.res.Resources
import app.aria.music.R
import app.aria.music.player.PlayerController
import app.aria.music.player.PlayerMessage
import app.aria.music.player.SkipReason
import app.aria.music.ui.Formatters

fun PlayerMessage.text(r: Resources): String = when (this) {
    is PlayerMessage.TrackSkipped -> r.getString(
        when (reason) {
            SkipReason.UNAVAILABLE -> R.string.msg_skipped_unavailable
            SkipReason.NETWORK -> R.string.msg_skipped_network
            SkipReason.OTHER -> R.string.msg_skipped_other
        },
        title,
    )
    PlayerMessage.TooManyFailures -> r.getString(R.string.msg_too_many_failures)
    is PlayerMessage.QueueWindowed -> r.getString(R.string.msg_queue_windowed, Formatters.count(queued), Formatters.count(total))
    PlayerMessage.LikeFailed -> r.getString(R.string.msg_like_failed)
    PlayerMessage.PlaylistFailed -> r.getString(R.string.msg_playlist_failed)
    PlayerMessage.HistoryFailed -> r.getString(R.string.msg_history_failed)
    PlayerMessage.RefreshFailed -> r.getString(R.string.msg_refresh_failed)
    is PlayerMessage.QueueFull -> r.getQuantityString(R.plurals.msg_queue_full, dropped, dropped, Formatters.count(PlayerController.QUEUE_CAP))
}
