// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamUrlPolicyTest {
    @Test fun httpsWithHostIsAllowed() = assertTrue(StreamUrlPolicy.isAllowed("https://rr1---sn.googlevideo.com/videoplayback?x=1", allowLocal = false))
    @Test fun everythingElseIsRejected() {
        listOf("http://a.example/x", "file:///data/data/app/aria.db", "content://contacts/people", "asset:///x", "rawresource:///1",
            "data:audio/wav;base64,AAAA", "javascript:alert(1)", "https:///nohost", "not a url", "").forEach {
            assertFalse(StreamUrlPolicy.isAllowed(it, allowLocal = false), it)
        }
    }
    @Test fun localFilesOnlyWhenExplicitlyAllowed() {
        assertTrue(StreamUrlPolicy.isAllowed("file:///data/user/0/x/cache/silence.wav", allowLocal = true))
        assertFalse(StreamUrlPolicy.isAllowed("content://x/y", allowLocal = true))
    }
    @Test fun foreignSchemesNeverPassEvenWhenLocalIsAllowed() {
        listOf("aria://track/x", "ARIA://track/x", "ftp://a.example/x", "android.resource://app/1", "content://x/y", "asset:///x", "http://a.example/x")
            .forEach { assertFalse(StreamUrlPolicy.isAllowed(it, allowLocal = true), it) }
    }
}
