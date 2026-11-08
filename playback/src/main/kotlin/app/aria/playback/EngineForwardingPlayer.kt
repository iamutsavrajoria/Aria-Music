// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.aria.domain.PlaybackEngine

/**
 * Routes every transport command from the session, notification and headset buttons to the engine. Commands that would
 * edit the queue or modes behind the engine's back are withheld, so no controller can load a URI of its own.
 * The engine's own edits go to the inner player directly, not through this wrapper.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class EngineForwardingPlayer(player: Player, private val engine: PlaybackEngine) : ForwardingPlayer(player) {
    override fun isCommandAvailable(command: Int): Boolean =
        command !in WithheldCommands.ALL && super.isCommandAvailable(command)
    override fun getAvailableCommands(): Player.Commands = WithheldCommands.restrict(super.getAvailableCommands())

    override fun play() = engine.play()
    override fun pause() = engine.pause()
    override fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) engine.play() else engine.pause()
    override fun seekToNext() = engine.next()
    override fun seekToNextMediaItem() = engine.next()
    override fun seekToPrevious() = engine.previous()
    override fun seekToPreviousMediaItem() = engine.previous()
    override fun seekTo(positionMs: Long) = engine.seekTo(positionMs)
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex == currentMediaItemIndex) engine.seekTo(positionMs) else engine.skipTo(mediaItemIndex)
    }
}

/** Session commands that bypass the engine (queue edits, modes, stop); see [EngineForwardingPlayer]. */
internal object WithheldCommands {
    val ALL = intArrayOf(
        Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_SET_MEDIA_ITEM,
        Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_SHUFFLE_MODE, Player.COMMAND_STOP,
    )

    @androidx.annotation.OptIn(UnstableApi::class)
    fun restrict(commands: Player.Commands): Player.Commands = commands.buildUpon().removeAll(*ALL).build()
}
