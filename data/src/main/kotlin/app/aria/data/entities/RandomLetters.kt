// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import kotlin.random.Random

private val LETTERS = ('a'..'z') + ('A'..'Z')

/** Replaces commons-lang3 `RandomStringUtils.insecure().next(n, true, false)` that Echo used for local IDs. */
internal fun randomLetters(count: Int): String = buildString(count) { repeat(count) { append(LETTERS[Random.nextInt(LETTERS.size)]) } }
