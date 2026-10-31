// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.models

data class SearchSuggestions(
    val queries: List<String>,
    val recommendedItems: List<YTItem>,
)
