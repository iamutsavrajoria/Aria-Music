// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

sealed class LocalItem {
  abstract val id: String
  abstract val title: String
  abstract val thumbnailUrl: String?
}
