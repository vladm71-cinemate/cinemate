package com.cinemate.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cinemate.app.data.local.entity.TrackedSeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedSeriesDao {

    @Query("SELECT EXISTS(SELECT 1 FROM tracked_series WHERE id = :id)")
    fun observeIsTracked(id: Int): Flow<Boolean>

    @Query("SELECT * FROM tracked_series")
    fun observeAll(): Flow<List<TrackedSeriesEntity>>

    /** Однократное чтение — для проверки при запуске. */
    @Query("SELECT * FROM tracked_series")
    suspend fun getAllOnce(): List<TrackedSeriesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TrackedSeriesEntity)

    @Query("DELETE FROM tracked_series WHERE id = :id")
    suspend fun delete(id: Int): Int

    @Query("UPDATE tracked_series SET lastNotifiedSeason = :season, lastNotifiedEpisode = :episode WHERE id = :id")
    suspend fun updateLastNotified(id: Int, season: Int, episode: Int)
}