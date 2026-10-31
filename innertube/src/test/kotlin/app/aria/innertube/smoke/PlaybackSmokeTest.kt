// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.smoke

import app.aria.domain.TrackId
import app.aria.innertube.adapter.YouTubeStreamSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Nightly early-warning for YouTube breakage. Runs only with -PrunSmoke (see innertube/build.gradle.kts). */
class PlaybackSmokeTest {
    private val ids = listOf("dQw4w9WgXcQ", "kJQP7kiw5Fk", "9bZkp7q19f0")

    @Test fun fixedPublicTracksResolveToAnAudioUrl() = runBlocking {
        assumeTrue(System.getProperty("aria.smoke") == "true")
        val source = YouTubeStreamSource()
        val failures = ids.filter { id -> source.resolve(TrackId.youtube(id)).isFailure }
        assertTrue("Could not resolve: $failures", failures.isEmpty())
    }
}
