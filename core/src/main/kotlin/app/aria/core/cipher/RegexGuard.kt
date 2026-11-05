// SPDX-License-Identifier: GPL-3.0-only
package app.aria.core.cipher

/** Bounds regexes that arrive in signed-but-remote data. Heuristics plus a work budget: a pattern that cannot finish on adversarial input is unsafe. */
object RegexGuard {
    private const val MAX_LENGTH = 512
    private const val BUDGET = 200_000
    private val NESTED_QUANTIFIER = Regex("""[+*}]\)\s*[+*{]""")
    private val QUANTIFIED_ALTERNATION = Regex("""\([^()]*\|[^()]*\)[+*{]""")
    private val ADVERSARIAL = listOf("a".repeat(48) + "!", "ab".repeat(32) + "!", " ".repeat(48) + "x", "0".repeat(64) + "?")

    fun isSafe(pattern: String): Boolean {
        if (pattern.length > MAX_LENGTH) return false
        val regex = try {
            Regex(pattern)
        } catch (_: Exception) {
            return false
        }
        if (NESTED_QUANTIFIER.containsMatchIn(pattern) || QUANTIFIED_ALTERNATION.containsMatchIn(pattern)) return false
        return ADVERSARIAL.all { input ->
            try {
                regex.containsMatchIn(Budgeted(input, BUDGET))
                true
            } catch (_: BudgetExceeded) {
                false
            }
        }
    }

    private class BudgetExceeded : RuntimeException()

    private class Budgeted(private val base: String, private var left: Int) : CharSequence {
        override val length: Int get() = base.length

        override fun get(index: Int): Char {
            if (--left < 0) throw BudgetExceeded()
            return base[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            Budgeted(base.substring(startIndex, endIndex), left)

        override fun toString() = base
    }
}
