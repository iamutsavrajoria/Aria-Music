// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.potoken

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {
    @Test fun allowsOnlyHttpsOnAllowedHosts() {
        assertTrue(NavigationPolicy.isAllowed("https://www.youtube.com/api/jnn/v1/Create"))
        assertFalse(NavigationPolicy.isAllowed("http://www.youtube.com/"))
        assertFalse(NavigationPolicy.isAllowed("https://evil.example/"))
        assertFalse(NavigationPolicy.isAllowed("https://www.youtube.com.evil.example/"))
        assertFalse(NavigationPolicy.isAllowed("file:///data/data/app.aria.music/databases/aria.db"))
        assertFalse(NavigationPolicy.isAllowed("javascript:alert(1)"))
    }
}
