// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustPolicyTest {
    private fun allowed(pkg: String, trusted: Boolean = false, notification: Boolean = false) =
        TrustPolicy.isAllowed(packageName = pkg, ownPackage = "app.aria.music", isTrusted = trusted, isNotificationController = notification)

    @Test fun ownPackageIsAllowed() = assertTrue(allowed("app.aria.music"))
    @Test fun systemTrustedIsAllowed() = assertTrue(allowed("com.android.systemui", trusted = true))
    @Test fun notificationControllerIsAllowed() = assertTrue(allowed("androidx.media3.session", notification = true))
    @Test fun unknownPackageIsRejected() = assertFalse(allowed("com.evil.app"))
    @Test fun lookAlikePackageIsRejected() = assertFalse(allowed("app.aria.music.evil"))
}
