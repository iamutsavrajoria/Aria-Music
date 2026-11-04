// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

/** Who may connect to the media session. Android Auto and other controllers are added in a later sub-project. */
object TrustPolicy {
    fun isAllowed(packageName: String, ownPackage: String, isTrusted: Boolean, isNotificationController: Boolean): Boolean =
        packageName == ownPackage || isTrusted || isNotificationController
}
