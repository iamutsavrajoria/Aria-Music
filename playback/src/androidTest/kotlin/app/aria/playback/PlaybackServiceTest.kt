// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.aria.domain.PlaybackEvent
import app.aria.domain.PlaybackStatus
import app.aria.domain.Track
import app.aria.domain.TrackId
import org.junit.After
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    // POST_NOTIFICATIONS exists only from API 33; granting it on older images fails the rule.
    @get:Rule val permission: GrantPermissionRule =
        if (android.os.Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext
    private val engine get() = (ctx.applicationContext as PlaybackEngineProvider).playbackEngine
    private fun track(n: Int) = Track(TrackId.youtube("track%06d".format(n)), "T$n", listOf("A"), 6_000L, null)
    private fun onMain(block: () -> Unit) = inst.runOnMainSync(block)

    private fun waitUntil(timeoutMs: Long = 20_000, diagnostics: () -> String = { "" }, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return; Thread.sleep(100) }
        Assert.fail("condition not met within $timeoutMs ms; state=${engine.state.value}; events=${seen.toList()}; ${diagnostics()}")
    }

    private val seen = java.util.concurrent.CopyOnWriteArrayList<Any>()
    private val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)

    @Before fun start() { onMain { collector.launch { engine.events.collect { seen.add(it) } }; AriaPlaybackService.start(ctx); engine.setEndlessRadio(false); engine.playNow(listOf(track(1), track(2))) } }
    @After fun stop() { onMain { collector.cancel(); engine.clear() }; ctx.stopService(Intent(ctx, AriaPlaybackService::class.java)) }

    @Test fun positionAdvancesAndNotificationIsPosted() {
        waitUntil { engine.state.value.status == PlaybackStatus.Playing && engine.state.value.positionMs > 1_000 }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        waitUntil(diagnostics = {
            "enabled=${nm.areNotificationsEnabled()} channels=${nm.notificationChannels.map { it.id }} active=${nm.activeNotifications.map { it.packageName + ':' + it.id }}"
        }) { nm.activeNotifications.any { it.packageName == ctx.packageName } }
    }

    @Test fun sessionControlsReachTheEngine() {
        waitUntil { engine.state.value.status == PlaybackStatus.Playing }
        val token = SessionToken(ctx, ComponentName(ctx, AriaPlaybackService::class.java))
        val controller = MediaController.Builder(ctx, token).buildAsync().get(15, TimeUnit.SECONDS)
        onMain { controller.pause() }
        waitUntil { engine.state.value.status == PlaybackStatus.Paused }
        onMain { controller.seekToNext() }
        waitUntil { engine.state.value.currentIndex == 1 }
        onMain { controller.release() }
    }

    /** After a PlaybackException ExoPlayer is idle; the skip must prepare it again or the next track never starts. */
    @Test fun permanentFailureSkipsToTheNextTrackAndPlaysIt() {
        onMain { engine.playNow(listOf(Track(TestAriaApp.UNAVAILABLE, "Gone", listOf("A"), 6_000L, null), track(2))) }
        waitUntil { engine.state.value.currentIndex == 1 }
        waitUntil { engine.state.value.status == PlaybackStatus.Playing && engine.state.value.positionMs > 500 }
        Assert.assertTrue("expected a Skipped event; events=${seen.toList()}", seen.any { it is PlaybackEvent.Skipped })
        Assert.assertEquals("T2", engine.state.value.current?.track?.title)
    }

    /** Queue edits, modes and stop would bypass the engine (and StreamUrlPolicy), so a controller must not be offered them. */
    @Test fun controllerIsNotOfferedCommandsThatBypassTheEngine() {
        waitUntil { engine.state.value.status == PlaybackStatus.Playing }
        val token = SessionToken(ctx, ComponentName(ctx, AriaPlaybackService::class.java))
        val controller = MediaController.Builder(ctx, token).buildAsync().get(15, TimeUnit.SECONDS)
        var transport = false
        val withheld = mutableListOf<Int>()
        onMain {
            transport = controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)
            listOf(
                Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_SHUFFLE_MODE, Player.COMMAND_STOP,
            ).filterTo(withheld) { controller.isCommandAvailable(it) }
        }
        onMain { controller.release() }
        Assert.assertTrue("transport must stay available", transport)
        Assert.assertEquals("commands still offered: $withheld", emptyList<Int>(), withheld)
    }
}
