// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationPermissionTest {
    @Test fun explainsOnlyWhenNeededOnceOnApi33Plus() {
        assertEquals(true, NotificationPermissionPolicy.shouldExplain(sdk = 34, granted = false, alreadyAsked = false, hasQueue = true))
        assertEquals(false, NotificationPermissionPolicy.shouldExplain(33, true, false, true))
        assertEquals(false, NotificationPermissionPolicy.shouldExplain(34, false, true, true))
        assertEquals(false, NotificationPermissionPolicy.shouldExplain(34, false, false, false))
        assertEquals(false, NotificationPermissionPolicy.shouldExplain(32, false, false, true))
    }
}
