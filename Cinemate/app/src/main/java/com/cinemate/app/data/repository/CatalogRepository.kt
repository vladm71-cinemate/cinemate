package com.cinemate.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.cinemate.app.data.mapper.toDomain
import com.cinemate.app.data.remote.TmdbApi
import com.cinemate.app.data.remote.dto.MovieDto
import com.cinemate.app.domain.model.CatalogFilter
import com.cinemate.app.domain.model.Countries
import com.cinemate.app.domain.model.ContentType
import com.cinemate.app.domain.model.Genre
import com.cinemate.app.domain.model.Movie
import com.cinemate.app.domain.model.SortOption
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Источник страниц с учётом всех фильтров.
 *
 * Белый список стран — на сервере (with_origin_country).
 * Чёрный список — на клиенте:
 *   - сериалы: точный отсев по origin_country из ответа;
 *   - фильмы: по original_language (языки стран берём из Countries).
 */
class DiscoverPagingSource(
    private val api: TmdbApi,
    private val filter: CatalogFilter
) : PagingSource<Int, Movie>() {

    /** Проходит ли элемент через чёрный список стран. */
    private fun MovieDto.passesExclusion(): Boolean {
        val excluded = filter.excludedCountries
        if (excluded.isEmpty()) return true

        val excludedLanguages = Countries.languagesOf(excluded)

        return when (filter.contentType) {
            ContentType.TV -> {
                // У сериалов страны приходят точно — фильтруем по ним
                val originCountries = originCountry.orEmpty()
                if (originCountries.isEmpty()) {
                    // Страны не указаны (редкий случай) — не выкидываем вслепую
                    true
                } else {
                    originCountries.none { it in excluded }
                }
            }
            ContentType.MOVIE -> {
                // У фильмов стран нет — фильтруем по языку оригинала
                val lang = originalLanguage
                if (lang == null) true
                else lang !in excludedLanguages
            }
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Movie> {
        val page = params.key ?: 1
        return try {
            val withGenres = filter.genreIds.takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "|") // OR-логика жанров
            // Защита от мусора при сортировке по рейтингу: минимум голосов
            val voteGte = if (filter.sort == SortOption.RATING) 300 else null

            val response = if (filter.contentType == ContentType.MOVIE) {
                api.discoverMovies(
                    page = page,
                    sortBy = filter.apiSortBy,
                    withGenres = withGenres,
                    year = filter.year,
                    voteCountGte = voteGte,
                    withOriginCountry = filter.withOriginCountry
                )
            } else {
                api.discoverTv(
                    page = page,
                    sortBy = filter.apiSortBy,
                    withGenres = withGenres,
                    year = filter.year,
                    voteCountGte = voteGte,
                    withOriginCountry = filter.withOriginCountry
                )
            }

            // Чёрный список: выбрасываем нежелательное ДО отдачи в UI
            val visible = response.results.filter { it.passesExclusion() }

            LoadResult.Page(
                data = visible.map { it.toDomain() },
                prevKey = if (page == 1) null else page - 1,
                // nextKey отдаём всегда, пока у TMDb есть страницы:
                // даже если отсев опустошил страницу, Paging сам возьмёт следующую
                nextKey = if (page < response.totalPages) page + 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Movie>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
        }
}

@Singleton
class CatalogRepository @Inject constructor(
    private val api: TmdbApi
) {
    fun discover(filter: CatalogFilter): Flow<PagingData<Movie>> = Pager(
        config = PagingConfig(
            pageSize = 20,
            prefetchDistance = 5,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { DiscoverPagingSource(api, filter) }
    ).flow

    // ---------- Жанры (кэш 10 минут) ----------

    private val genresCache: MutableMap<String, List<Genre>> = mutableMapOf()
    private val genresCacheTime: MutableMap<String, Long> = mutableMapOf()

    private fun genresCacheKey(contentType: ContentType, language: String): String =
        "${contentType.name}_$language"

    /**
     * Жанры на выбранном языке (language добавляется поверх запроса).
     * Кэш в памяти на 10 минут: повторные смены фильтров/языка
     * не дёргают TMDb.
     */
    suspend fun genres(contentType: ContentType, language: String): List<Genre> {
        val key = genresCacheKey(contentType, language)
        val cached = genresCache[key]
        val cachedAt = genresCacheTime[key] ?: 0L
        if (cached != null && System.currentTimeMillis() - cachedAt < 10 * 60 * 1000) {
            return cached
        }
        val fresh = if (contentType == ContentType.MOVIE)
            api.movieGenres(language = language).genres.map { it.toDomain() }
        else
            api.tvGenres(language = language).genres.map { it.toDomain() }
        genresCache[key] = fresh
        genresCacheTime[key] = System.currentTimeMillis()
        return fresh
    }
}