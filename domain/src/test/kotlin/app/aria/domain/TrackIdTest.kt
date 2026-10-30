// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TrackIdTest {
    @Test fun bareElevenCharIdIsYoutube() {
        val id = TrackId.parse("dQw4w9WgXcQ")
        assertEquals(TrackSource.YOUTUBE, id.source)
        assertEquals("dQw4w9WgXcQ", id.value)
        assertEquals("dQw4w9WgXcQ", id.raw)
    }

    @Test fun prefixedIdsKeepTheirSource() {
        assertEquals(TrackSource.LOCAL, TrackId.parse("local:abc").source)
        assertEquals("abc", TrackId.parse("local:abc").value)
        assertEquals(TrackSource.PODCAST, TrackId.parse("pod:xyz").source)
    }

    @Test fun roundTripsForEverySource() {
        val ids = listOf(
            TrackId.youtube("dQw4w9WgXcQ"),
            TrackId.of(TrackSource.LOCAL, "file-1"),
            TrackId.of(TrackSource.PODCAST, "ep-9"),
        )
        ids.forEach { assertEquals(it, TrackId.parse(it.raw)) }
    }

    @Test fun rejectsBareStringThatIsNotAYoutubeId() {
        assertFailsWith<IllegalArgumentException> { TrackId.parse("local_track_1") }
        assertFailsWith<IllegalArgumentException> { TrackId.parse("short") }
    }

    @Test fun rejectsElevenCharBareIdWithInvalidCharacter() {
        assertFailsWith<IllegalArgumentException> { TrackId.parse("dQw4w9WgXc!") }
    }

    @Test fun rejectsEmptyPrefixedValue() {
        assertFailsWith<IllegalArgumentException> { TrackId.parse("local:") }
        assertFailsWith<IllegalArgumentException> { TrackId.of(TrackSource.PODCAST, " ") }
    }
}
