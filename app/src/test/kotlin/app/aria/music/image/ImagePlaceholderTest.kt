// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImagePlaceholderTest {
    @Test fun placeholderUnlessLoaderPresentAndUrlAllowed() {
        assertNull(imageModelOrNull(null, hasLoader = true))
        assertNull(imageModelOrNull("https://i.ytimg.com/x", hasLoader = false))
        assertNull(imageModelOrNull("https://evil.example/x", hasLoader = true))
        assertEquals("https://i.ytimg.com/x", imageModelOrNull("https://i.ytimg.com/x", hasLoader = true))
    }
}
