// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import app.aria.domain.PlaybackEngine
import app.aria.domain.StreamSource
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class HiltWiringTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var engine: PlaybackEngine
    @Inject lateinit var streams: StreamSource

    @Test fun graphResolves() { hilt.inject(); assertNotNull(engine); assertNotNull(streams) }
}
