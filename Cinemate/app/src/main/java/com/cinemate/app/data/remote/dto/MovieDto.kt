package com.cinemate.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Элемент списка discover/search И для фильмов, И для сериалов:
 * у фильмов название в "title", дата — "release_date";
 * у сериалов — "name" и "first_air_date".
 * Все поля с defaults — парсер не падает на недостающих ключах.
 */
@JsonClass(generateAdapter = true)
data class MovieDto(
    val id: Int,
    // --- фильм ---
    val title: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    // --- сериал ---
    val name: String? = null,
    @Json(name = "original_name") val originalName: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    // --- общее ---
    val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    val popularity: Double? = null,
    @Json(name = "genre_ids") val genreIds: List<Int>? = null,
    // --- для фильтра стран ---
    /** Язык оригинала ("hi", "ko", "ru"...). Приходят и у фильмов, и у сериалов. */
    @Json(name = "original_language") val originalLanguage: String? = null,
    /** Страны производства — только у сериалов. У фильмов будет null. */
    @Json(name = "origin_country") val originCountry: List<String>? = null
)