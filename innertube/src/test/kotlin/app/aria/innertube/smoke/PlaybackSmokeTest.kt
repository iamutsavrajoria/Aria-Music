// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.smoke

import app.aria.domain.Track
import app.aria.domain.TrackId
import app.aria.innertube.adapter.YouTubeRadioSource
import app.aria.innertube.adapter.YouTubeStreamSource
import app.aria.innertube.extraction.ExtractorSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Nightly early-warning for YouTube breakage. Runs only with -PrunSmoke (see innertube/build.gradle.kts). */
class PlaybackSmokeTest {
    private val ids = listOf("dQw4w9WgXcQ", "kJQP7kiw5Fk", "9bZkp7q19f0")

    @Test fun fixedPublicTracksResolveToAnAudioUrl() = runBlocking {
        assumeTrue(System.getProperty("aria.smoke") == "true")
        val source = YouTubeStreamSource.create(ExtractorSession.createDefault(poTokens = null))
        val results = ids.associateWith { id -> source.resolve(TrackId.youtube(id)) }
        val failures = results.filterValues { it.isFailure }.keys
        assertTrue("Could not resolve: $failures", failures.isEmpty())

        val streams = results.values.map { it.getOrThrow() }
        val badUrls = streams.filter { !it.url.startsWith("https://") }
        assertTrue("Resolved URLs that are not https: ${badUrls.size}", badUrls.isEmpty())
        val noExpiry = streams.filter { it.expiresAtEpochMs == null }
        assertTrue("Resolved streams without an expiry: ${noExpiry.size}", noExpiry.isEmpty())

        val seed = Track(TrackId.youtube(ids.first()), "Seed", emptyList(), null, null)
        val radio = YouTubeRadioSource().related(seed, 5)
        assertTrue("Radio failed: ${radio.exceptionOrNull()}", radio.isSuccess)
        assertFalse("Radio returned no tracks", radio.getOrThrow().isEmpty())
    }
}
