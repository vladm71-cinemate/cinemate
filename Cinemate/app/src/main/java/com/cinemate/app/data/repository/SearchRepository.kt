package com.cinemate.app.data.repository

import com.cinemate.app.data.local.dao.SearchHistoryDao
import com.cinemate.app.data.local.entity.SearchHistoryEntity
import com.cinemate.app.data.mapper.toDomain
import com.cinemate.app.data.remote.TmdbApi
import com.cinemate.app.domain.model.ContentType
import com.cinemate.app.domain.model.Movie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Результат поиска: фильм/сериал + тип для маршрутизации в карточку. */
data class SearchItem(
    val movie: Movie,
    val isTv: Boolean
)

@Singleton
class SearchRepository @Inject constructor(
    private val api: TmdbApi,
    private val historyDao: SearchHistoryDao
) {

    companion object {
        private const val RU_RESULTS_THRESHOLD = 3

        private const val LANG_RU = "ru-RU"
        private const val LANG_EN = "en-US"

        private const val HISTORY_LIMIT = 50
    }

    /**
     * Поиск по типу: сначала ru-RU, при малом результате добор en-US.
     */
    suspend fun search(type: ContentType, query: String): List<Movie> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val ruResults = when (type) {
            ContentType.MOVIE -> api.searchMovies(trimmed, language = LANG_RU).results
            ContentType.TV -> api.searchTv(trimmed, language = LANG_RU).results
        }

        if (ruResults.size >= RU_RESULTS_THRESHOLD) {
            return ruResults.map { it.toDomain() }
        }

        val enResults = when (type) {
            ContentType.MOVIE -> api.searchMovies(trimmed, language = LANG_EN).results
            ContentType.TV -> api.searchTv(trimmed, language = LANG_EN).results
        }

        return (ruResults + enResults)
            .distinctBy { it.id }
            .map { it.toDomain() }
    }

    /**
     * Универсальный поиск /search/multi: фильмы + сериалы + персоны одним списком.
     * Мы берём только фильмы/сериалы; тип определяем по наличию first_air_date.
     * Русско-английский дедуп как в search.
     */
    suspend fun searchMulti(query: String): List<SearchItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val ru = api.searchMulti(trimmed, language = LANG_RU).results
        val combined = if (ru.size >= RU_RESULTS_THRESHOLD) ru
        else {
            val en = api.searchMulti(trimmed, language = LANG_EN).results
            (ru + en).distinctBy { it.id }
        }

        return combined
            .filter { dto ->
                // Убираем персоны: у них нет ни release_date, ни first_air_date
                dto.releaseDate != null || dto.firstAirDate != null
            }
            .distinctBy { it.id }
            .map { dto ->
                SearchItem(
                    movie = dto.toDomain(),
                    isTv = dto.firstAirDate != null
                )
            }
    }

    // ---------- История ----------

    fun observeHistory(): Flow<List<String>> =
        historyDao.observeRecent().map { list -> list.map { it.query } }

    suspend fun addToHistory(rawQuery: String) {
        val normalized = rawQuery.trim().lowercase()
        if (normalized.isEmpty()) return

        historyDao.upsert(
            SearchHistoryEntity(
                query = normalized,
                timestamp = System.currentTimeMillis()
            )
        )
        historyDao.trimToLimit()
    }

    suspend fun clearHistory() = historyDao.clearAll()
}