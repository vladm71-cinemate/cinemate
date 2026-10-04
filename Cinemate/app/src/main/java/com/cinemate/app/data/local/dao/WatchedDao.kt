package com.cinemate.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cinemate.app.data.local.entity.WatchedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedDao {

    /** Для экрана истории: свежие сверху, максимум 100. */
    @Query("SELECT * FROM watched_items ORDER BY watchedAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<WatchedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchedItemEntity)

    /** Есть ли уже запись с таким хешем (для обновления даты, а не дубля). */
    @Query("SELECT * FROM watched_items WHERE infoHash = :hash LIMIT 1")
    suspend fun get(hash: String): WatchedItemEntity?

    @Query("DELETE FROM watched_items WHERE infoHash = :hash")
    suspend fun delete(hash: String)

    @Query("DELETE FROM watched_items")
    suspend fun clearAll()
}