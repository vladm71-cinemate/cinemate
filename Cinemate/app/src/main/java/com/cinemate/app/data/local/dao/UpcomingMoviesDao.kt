package com.cinemate.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cinemate.app.data.local.entity.UpcomingMovieEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UpcomingMoviesDao {

    @Query("SELECT EXISTS(SELECT 1 FROM upcoming_movies WHERE id = :id)")
    fun observeIsAwaited(id: Int): Flow<Boolean>

    @Query("SELECT * FROM upcoming_movies ORDER BY releaseDate")
    fun observeAll(): Flow<List<UpcomingMovieEntity>>

    /** Однократное чтение — для проверки при запуске. */
    @Query("SELECT * FROM upcoming_movies")
    suspend fun getAllOnce(): List<UpcomingMovieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UpcomingMovieEntity)

    @Query("DELETE FROM upcoming_movies WHERE id = :id")
    suspend fun delete(id: Int): Int

    @Query("UPDATE upcoming_movies SET notified = 1 WHERE id = :id")
    suspend fun markNotified(id: Int)
}