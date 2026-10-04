package com.cinemate.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cinemate.app.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    /** История для UI: свежие сверху, максимум 50. */
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 50")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    /**
     * UPSERT: REPLACE по первичному ключу (query) — повторный запрос
     * обновляет timestamp, дубликат не появляется.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchHistoryEntity)

    /** Держим не больше 50 записей: хвост старше лимита удаляем после вставки. */
    @Query(
        "DELETE FROM search_history WHERE query NOT IN " +
                "(SELECT query FROM search_history ORDER BY timestamp DESC LIMIT 50)"
    )
    suspend fun trimToLimit()

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}