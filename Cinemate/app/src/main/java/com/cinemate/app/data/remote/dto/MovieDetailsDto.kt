package com.cinemate.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MovieDetailsDto(
    val id: Int,
    val title: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    val tagline: String? = null,
    val status: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    val genres: List<GenreDto> = emptyList(),
    // ---------- блок «Подробнее» ----------
    val budget: Long? = null,
    val revenue: Long? = null,
    @Json(name = "production_companies") val productionCompanies: List<CompanyDto> = emptyList(),
    @Json(name = "production_countries") val productionCountries: List<CountryDto> = emptyList(),
    @Json(name = "spoken_languages") val spokenLanguages: List<LanguageDto> = emptyList()
)

/** Студия-производитель. */
@JsonClass(generateAdapter = true)
data class CompanyDto(
    val id: Int,
    val name: String
)

/** Страна производства (name уже на языке запроса — русском). */
@JsonClass(generateAdapter = true)
data class CountryDto(
    @Json(name = "iso_3166_1") val code: String,
    val name: String
)

/** Язык оригинала/озвучки (english_name и name на языке запроса). */
@JsonClass(generateAdapter = true)
data class LanguageDto(
    @Json(name = "english_name") val englishName: String? = null,
    val name: String? = null
)