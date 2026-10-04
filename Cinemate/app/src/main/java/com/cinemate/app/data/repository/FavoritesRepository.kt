package com.cinemate.app.data.repository

import com.cinemate.app.data.local.dao.FavoritesDao
import com.cinemate.app.data.local.entity.FavoriteEntity
import com.cinemate.app.domain.model.TitleDetails
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    private val dao: FavoritesDao
) {
    fun observeIsFavorite(id: Int, type: String): Flow<Boolean> =
        dao.observeIsFavorite(id, type)

    /** По алфавиту. */
    fun observeAllByTitle(): Flow<List<FavoriteEntity>> = dao.observeAllByTitle()

    /** По дате добавления (свежие сверху). */
    fun observeAllByDate(): Flow<List<FavoriteEntity>> = dao.observeAllByDate()

    /** Удаление по ключу — для свайпа в списке избранного. */
    suspend fun delete(id: Int, type: String) {
        dao.delete(id, type)
    }

    /**
     * Toggle без чтения состояния: пытаемся удалить.
     * Удалили 0 строк — фильма не было, добавляем. Удалили 1 — убрали.
     */
    suspend fun toggle(details: TitleDetails) {
        val type = details.type.name.lowercase()
        val deleted = dao.delete(details.id, type)
        if (deleted == 0) {
            dao.insert(
                FavoriteEntity(
                    id = details.id,
                    type = type,
                    title = details.title,
                    posterPath = details.posterPath,
                    year = details.releaseDate?.take(4),
                    rating = details.rating,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }
}