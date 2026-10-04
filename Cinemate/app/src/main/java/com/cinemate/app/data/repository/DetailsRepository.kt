package com.cinemate.app.data.repository

import com.cinemate.app.data.local.dao.CacheDao
import com.cinemate.app.data.local.entity.ActorsCacheEntity
import com.cinemate.app.data.local.entity.KeywordsCacheEntity
import com.cinemate.app.data.mapper.toActorList
import com.cinemate.app.data.mapper.toDomain
import com.cinemate.app.data.mapper.toKeywordList
import com.cinemate.app.data.remote.TmdbApi
import com.cinemate.app.data.remote.dto.CastMemberDto
import com.cinemate.app.data.remote.dto.KeywordDto
import com.cinemate.app.domain.model.Actor
import com.cinemate.app.domain.model.ContentType
import com.cinemate.app.domain.model.Keyword
import com.cinemate.app.domain.model.Movie
import com.cinemate.app.domain.model.Person
import com.cinemate.app.domain.model.TitleDetails
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetailsRepository @Inject constructor(
    private val api: TmdbApi,
    private val cacheDao: CacheDao,
    moshi: Moshi
) {

    companion object {
        /** Срок жизни кэша актёров и ключевых слов: 7 дней. */
        private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

        private const val KEY_PREFIX_MOVIE = "movie_"
        private const val KEY_PREFIX_TV = "tv_"

        private const val CAST_LIMIT = 20
    }

    private val castAdapter = moshi.adapter<List<CastMemberDto>>(
        Types.newParameterizedType(List::class.java, CastMemberDto::class.java)
    )
    private val keywordAdapter = moshi.adapter<List<KeywordDto>>(
        Types.newParameterizedType(List::class.java, KeywordDto::class.java)
    )

    private fun cacheKey(type: ContentType, id: Int): String =
        (if (type == ContentType.MOVIE) KEY_PREFIX_MOVIE else KEY_PREFIX_TV) + id

    // ---------- Детали ----------

    suspend fun details(type: ContentType, id: Int): TitleDetails =
        if (type == ContentType.MOVIE) api.movieDetails(id).toDomain()
        else api.tvDetails(id).toDomain()

    // ---------- Актёры (кэш 7 дней) ----------

    suspend fun cast(type: ContentType, id: Int): List<Actor> {
        val key = cacheKey(type, id)
        val now = System.currentTimeMillis()

        runCatching { cacheDao.deleteStaleActors(now - CACHE_TTL_MS) }

        val cached = cacheDao.getActors(key)
        if (cached != null && now - cached.cachedAt < CACHE_TTL_MS) {
            return runCatching { castAdapter.fromJson(cached.castJson) }
                .getOrNull()
                ?.toActorList()
                ?: emptyList()
        }

        val cast = (if (type == ContentType.MOVIE) {
            api.movieCredits(id).cast
        } else {
            api.tvCredits(id).cast
        })
            .distinctBy { it.id }
            .take(CAST_LIMIT)

        cacheDao.insertActors(
            ActorsCacheEntity(
                titleKey = key,
                castJson = castAdapter.toJson(cast),
                cachedAt = now
            )
        )
        return cast.toActorList()
    }

    // ---------- Ключевые слова (кэш 7 дней) ----------

    suspend fun keywords(type: ContentType, id: Int): List<Keyword> {
        val key = cacheKey(type, id)
        val now = System.currentTimeMillis()

        runCatching { cacheDao.deleteStaleKeywords(now - CACHE_TTL_MS) }

        val cached = cacheDao.getKeywords(key)
        if (cached != null && now - cached.cachedAt < CACHE_TTL_MS) {
            return runCatching { keywordAdapter.fromJson(cached.keywordsJson) }
                .getOrNull()
                ?.toKeywordList()
                ?: emptyList()
        }

        val words = (if (type == ContentType.MOVIE) {
            api.movieKeywords(id)
        } else {
            api.tvKeywords(id)
        }).all()
            .distinctBy { it.id }

        cacheDao.insertKeywords(
            KeywordsCacheEntity(
                titleKey = key,
                keywordsJson = keywordAdapter.toJson(words),
                cachedAt = now
            )
        )
        return words.toKeywordList()
    }

    // ---------- Похожие ----------

    suspend fun similar(type: ContentType, id: Int): List<Movie> {
        val response = if (type == ContentType.MOVIE) {
            api.similarMovies(id)
        } else {
            api.similarTv(id)
        }
        return response.results
            .distinctBy { it.id }
            .map { it.toDomain() }
    }

    // ---------- Персона и фильмография (10.2c) ----------

    /**
     * Актёр + его фильмография.
     * Дедуп по id внутри каждой группы, сортировка по дате — новые сверху.
     * Работы без даты — в конец.
     */
    suspend fun person(personId: Int): Person {
        val details = api.personDetails(personId)
        val credits = api.personCombinedCredits(personId)

        val castFilmography = credits.cast
            .distinctBy { it.id }
            .map { it.toDomain() }
            .sortedByDescending { it.releaseDate ?: "0000" }

        val crewFilmography = credits.crew
            .distinctBy { it.id }
            .map { it.toDomain() }
            .sortedByDescending { it.releaseDate ?: "0000" }

        return Person(
            id = details.id,
            name = details.name,
            biography = details.biography?.takeIf { it.isNotBlank() },
            profilePath = details.profilePath,
            knownForDepartment = details.knownForDepartment,
            birthday = details.birthday,
            placeOfBirth = details.placeOfBirth,
            castFilmography = castFilmography,
            crewFilmography = crewFilmography
        )
    }
}