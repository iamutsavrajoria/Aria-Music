// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import kotlin.test.Test
import kotlin.test.assertEquals

class ImageSizeCapTest {
    @Test fun capsEachSideAndFillsUnknowns() {
        assertEquals(300 to 200, ImageSizeCap.cap(300, 200))
        assertEquals(1_024 to 1_024, ImageSizeCap.cap(5_000, 9_000))
        assertEquals(1_024 to 1_024, ImageSizeCap.cap(null, null))
        assertEquals(500 to 1_024, ImageSizeCap.cap(500, null))
        assertEquals(1 to 1, ImageSizeCap.cap(0, -3))
    }
}
