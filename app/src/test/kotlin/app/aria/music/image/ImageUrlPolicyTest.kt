// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageUrlPolicyTest {
    @Test fun allowsYouTubeImageHosts() {
        for (u in listOf("https://i.ytimg.com/vi/x/hq.jpg", "https://lh3.googleusercontent.com/abc=w544", "https://yt3.ggpht.com/a", "https://yt3.googleusercontent.com/b", "https://i9.ytimg.com/c")) assertTrue(ImageUrlPolicy.isAllowed(u), u)
    }
    @Test fun rejectsEverythingElse() {
        for (u in listOf(
            "http://i.ytimg.com/x", "file:///sdcard/x.jpg", "content://media/external/1", "data:image/png;base64,AAAA", "https://evil.example/x.png",
            "https://evilytimg.com/x", "https://ytimg.com.evil.example/x", "https://i.ytimg.com@evil.example/x", "https://i.ytimg.com:8443/x",
            "https://user:pw@i.ytimg.com/x", "", "not a url", "https:///x",
        )) assertFalse(ImageUrlPolicy.isAllowed(u), u)
    }
    @Test fun hostMatchIsCaseInsensitive() = assertTrue(ImageUrlPolicy.isAllowed("https://I.YTIMG.COM/x"))

    @Test fun rejectedHostIsHostOnly() {
        assertEquals("evil.example", ImageUrlPolicy.hostOf("https://user:pw@Evil.Example:8443/secret/path?token=abc#frag"))
        assertNull(ImageUrlPolicy.hostOf("file:///etc/passwd"))
        assertNull(ImageUrlPolicy.hostOf("not a url"))
    }

    @Test fun placeholderUnlessLoaderPresentAndUrlAllowed() {
        assertNull(imageModelOrNull(null, hasLoader = true))
        assertNull(imageModelOrNull("https://i.ytimg.com/x", hasLoader = false))
        assertNull(imageModelOrNull("https://evil.example/x", hasLoader = true))
        assertEquals("https://i.ytimg.com/x", imageModelOrNull("https://i.ytimg.com/x", hasLoader = true))
    }
}
