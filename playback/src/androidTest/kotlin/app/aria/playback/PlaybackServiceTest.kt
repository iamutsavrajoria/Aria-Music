// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.aria.domain.PlaybackStatus
import app.aria.domain.Track
import app.aria.domain.TrackId
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext
    private val engine get() = (ctx.applicationContext as PlaybackEngineProvider).playbackEngine
    private fun track(n: Int) = Track(TrackId.youtube("track%06d".format(n)), "T$n", listOf("A"), 6_000L, null)
    private fun onMain(block: () -> Unit) = inst.runOnMainSync(block)

    private fun waitUntil(timeoutMs: Long = 20_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return; Thread.sleep(100) }
        Assert.fail("condition not met within $timeoutMs ms; state=${engine.state.value}")
    }

    @Before fun start() { onMain { AriaPlaybackService.start(ctx); engine.setEndlessRadio(false); engine.playNow(listOf(track(1), track(2))) } }
    @After fun stop() { onMain { engine.clear() }; ctx.stopService(Intent(ctx, AriaPlaybackService::class.java)) }

    @Test fun positionAdvancesAndNotificationIsPosted() {
        waitUntil { engine.state.value.status == PlaybackStatus.Playing && engine.state.value.positionMs > 1_000 }
        waitUntil {
            ctx.getSystemService(NotificationManager::class.java).activeNotifications.any { it.packageName == ctx.packageName }
        }
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
}
