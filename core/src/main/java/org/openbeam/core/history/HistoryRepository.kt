package org.openbeam.core.history

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class HistoryRepository private constructor(context: Context) {
    private val dao: HistoryDao = HistoryDatabase.getInstance(context).historyDao()

    fun getAllEntries(): Flow<List<HistoryEntry>> = dao.getAll()

    suspend fun addEntry(entry: HistoryEntry) {
        withContext(Dispatchers.IO) { dao.insert(entry) }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) { dao.clear() }
    }

    companion object {
        @Volatile
        private var INSTANCE: HistoryRepository? = null
        fun getInstance(context: Context): HistoryRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = HistoryRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}