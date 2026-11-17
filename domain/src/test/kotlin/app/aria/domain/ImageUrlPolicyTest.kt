// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

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
            "https://i.ytimg.com./x", "https://lh3.googleusercontent.com.evil.com/x", "https://evilgoogleusercontent.com/x",
            "https://googleusercontent.com/x", "https://.ytimg.com/x", "ftp://i.ytimg.com/x", "//i.ytimg.com/x",
        )) assertFalse(ImageUrlPolicy.isAllowed(u), u)
    }
    @Test fun hostMatchIsCaseInsensitive() = assertTrue(ImageUrlPolicy.isAllowed("https://I.YTIMG.COM/x"))
    @Test fun schemeMatchIsCaseInsensitive() = assertTrue(ImageUrlPolicy.isAllowed("HTTPS://i.ytimg.com/x"))
    @Test fun explicitDefaultPortIsAllowedOthersAreNot() {
        assertTrue(ImageUrlPolicy.isAllowed("https://i.ytimg.com:443/x"))
        assertFalse(ImageUrlPolicy.isAllowed("https://i.ytimg.com:444/x"))
        assertFalse(ImageUrlPolicy.isAllowed("https://i.ytimg.com:80/x"))
    }

    @Test fun rejectedHostIsHostOnly() {
        assertEquals("evil.example", ImageUrlPolicy.hostOf("https://user:pw@Evil.Example:8443/secret/path?token=abc#frag"))
        assertNull(ImageUrlPolicy.hostOf("file:///etc/passwd"))
        assertNull(ImageUrlPolicy.hostOf("not a url"))
    }
}
