// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.TrackId
import app.aria.innertube.models.Artist
import app.aria.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappingTest {
    @Test fun songItemMapsToTrack() {
        val item = SongItem(
            id = "dQw4w9WgXcQ", title = "Song", artists = listOf(Artist("A", "id1"), Artist("B", null)),
            duration = 213, thumbnail = "https://example.invalid/t.jpg",
        )
        val t = item.toTrack()
        assertEquals(TrackId.youtube("dQw4w9WgXcQ"), t.id)
        assertEquals(listOf("A", "B"), t.artists)
        assertEquals(213_000L, t.durationMs)
        assertEquals("https://example.invalid/t.jpg", t.thumbnailUrl)
    }

    @Test fun missingDurationStaysNull() {
        val item = SongItem(id = "dQw4w9WgXcQ", title = "S", artists = emptyList(), thumbnail = "x")
        assertNull(item.toTrack().durationMs)
    }
}
