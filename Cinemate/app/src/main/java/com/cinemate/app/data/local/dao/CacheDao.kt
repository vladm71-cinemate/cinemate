package com.cinemate.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cinemate.app.data.local.entity.ActorsCacheEntity
import com.cinemate.app.data.local.entity.KeywordsCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {

    // ---------- Актёры ----------

    @Query("SELECT * FROM actors_cache WHERE titleKey = :titleKey")
    suspend fun getActors(titleKey: String): ActorsCacheEntity?

    @Query("SELECT * FROM actors_cache WHERE titleKey = :titleKey")
    fun observeActors(titleKey: String): Flow<ActorsCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActors(entity: ActorsCacheEntity)

    // ---------- Ключевые слова ----------

    @Query("SELECT * FROM keywords_cache WHERE titleKey = :titleKey")
    suspend fun getKeywords(titleKey: String): KeywordsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeywords(entity: KeywordsCacheEntity)

    // ---------- Служебное ----------

    /** Чистка просроченных записей — вызываем при открытии карточки. */
    @Query("DELETE FROM actors_cache WHERE cachedAt < :threshold")
    suspend fun deleteStaleActors(threshold: Long)

    @Query("DELETE FROM keywords_cache WHERE cachedAt < :threshold")
    suspend fun deleteStaleKeywords(threshold: Long)
}