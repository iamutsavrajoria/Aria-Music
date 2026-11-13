// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.domain.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow

class SearchHistoryRepositoryImpl(private val dao: SearchHistoryDao) : SearchHistoryRepository {
    override fun recent(limit: Int): Flow<List<String>> = dao.recent(limit)
    override suspend fun add(query: String) { val q = query.trim(); if (q.isNotEmpty()) dao.add(q) }
    override suspend fun remove(query: String) = dao.delete(query)
    override suspend fun clear() = dao.clear()
}
