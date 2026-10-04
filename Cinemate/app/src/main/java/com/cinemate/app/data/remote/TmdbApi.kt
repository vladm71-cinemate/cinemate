package com.cinemate.app.data.remote

import com.cinemate.app.data.remote.dto.CreditsDto
import com.cinemate.app.data.remote.dto.GenresResponseDto
import com.cinemate.app.data.remote.dto.KeywordsDto
import com.cinemate.app.data.remote.dto.MovieDetailsDto
import com.cinemate.app.data.remote.dto.MovieDto
import com.cinemate.app.data.remote.dto.PagedResponseDto
import com.cinemate.app.data.remote.dto.PersonCreditsDto
import com.cinemate.app.data.remote.dto.PersonDto
import com.cinemate.app.data.remote.dto.ReleaseDatesResponseDto
import com.cinemate.app.data.remote.dto.TvDetailsDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("with_genres") withGenres: String? = null,
        @Query("primary_release_year") year: Int? = null,
        @Query("vote_count.gte") voteCountGte: Int? = null,
        @Query("with_origin_country") withOriginCountry: String? = null,
    ): PagedResponseDto<MovieDto>

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("with_genres") withGenres: String? = null,
        @Query("first_air_date_year") year: Int? = null,
        @Query("vote_count.gte") voteCountGte: Int? = null,
        @Query("with_origin_country") withOriginCountry: String? = null,
    ): PagedResponseDto<MovieDto>

    @GET("genre/movie/list")
    suspend fun movieGenres(@Query("language") language: String? = null): GenresResponseDto

    @GET("genre/tv/list")
    suspend fun tvGenres(@Query("language") language: String? = null): GenresResponseDto

    @GET("movie/{id}")
    suspend fun movieDetails(@Path("id") id: Int): MovieDetailsDto

    @GET("tv/{id}")
    suspend fun tvDetails(@Path("id") id: Int): TvDetailsDto

    @GET("movie/{id}/credits")
    suspend fun movieCredits(@Path("id") id: Int): CreditsDto

    @GET("tv/{id}/credits")
    suspend fun tvCredits(@Path("id") id: Int): CreditsDto

    @GET("movie/{id}/keywords")
    suspend fun movieKeywords(@Path("id") id: Int): KeywordsDto

    @GET("tv/{id}/keywords")
    suspend fun tvKeywords(@Path("id") id: Int): KeywordsDto

    @GET("movie/{id}/similar")
    suspend fun similarMovies(
        @Path("id") id: Int,
        @Query("page") page: Int = 1
    ): PagedResponseDto<MovieDto>

    @GET("tv/{id}/similar")
    suspend fun similarTv(
        @Path("id") id: Int,
        @Query("page") page: Int = 1
    ): PagedResponseDto<MovieDto>

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String? = null,
        @Query("include_adult") includeAdult: Boolean = false
    ): PagedResponseDto<MovieDto>

    @GET("search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String? = null,
        @Query("include_adult") includeAdult: Boolean = false
    ): PagedResponseDto<MovieDto>

    /** Универсальный поиск: фильмы + сериалы + персоны одним списком. */
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String? = null,
        @Query("include_adult") includeAdult: Boolean = false
    ): PagedResponseDto<MovieDto>

    @GET("movie/{id}/release_dates")
    suspend fun movieReleaseDates(@Path("id") id: Int): ReleaseDatesResponseDto

    @GET("person/{id}")
    suspend fun personDetails(@Path("id") id: Int): PersonDto

    @GET("person/{id}/combined_credits")
    suspend fun personCombinedCredits(@Path("id") id: Int): PersonCreditsDto
}