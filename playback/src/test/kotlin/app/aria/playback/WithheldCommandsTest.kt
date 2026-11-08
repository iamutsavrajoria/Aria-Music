// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import androidx.media3.common.Player
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WithheldCommandsTest {
    private val withheld = listOf(
        Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_SET_MEDIA_ITEM,
        Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_SHUFFLE_MODE, Player.COMMAND_STOP,
    )

    @Test fun queueModeAndStopCommandsAreRemoved() {
        val restricted = WithheldCommands.restrict(Player.Commands.Builder().addAllCommands().build())
        withheld.forEach { assertFalse(restricted.contains(it), "command $it") }
    }
    @Test fun transportCommandsSurvive() {
        val restricted = WithheldCommands.restrict(Player.Commands.Builder().addAllCommands().build())
        listOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_METADATA)
            .forEach { assertTrue(restricted.contains(it), "command $it") }
    }
}
