package com.cinemate.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cinemate.app.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id AND type = :type)")
    fun observeIsFavorite(id: Int, type: String): Flow<Boolean>

    /** По алфавиту (дефолт). */
    @Query("SELECT * FROM favorites ORDER BY title")
    fun observeAllByTitle(): Flow<List<FavoriteEntity>>

    /** По дате добавления: свежие сверху. */
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC, title")
    fun observeAllByDate(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id AND type = :type")
    suspend fun delete(id: Int, type: String): Int
}