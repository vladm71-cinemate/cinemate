package com.cinemate.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TvDetailsDto(
    val id: Int,
    val name: String? = null,
    @Json(name = "original_name") val originalName: String? = null,
    val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val status: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    val genres: List<GenreDto> = emptyList(),
    @Json(name = "number_of_seasons") val numberOfSeasons: Int? = null,
    @Json(name = "production_countries") val productionCountries: List<CountryDto> = emptyList(),
    val networks: List<CompanyDto> = emptyList(),
    @Json(name = "spoken_languages") val spokenLanguages: List<LanguageDto> = emptyList(),
    /** Следующая серия. null = сериал завершён или отменён. */
    @Json(name = "next_episode_to_air") val nextEpisodeToAir: NextEpisodeDto? = null
)

/** Следующая вышедающая серия сериала. */
@JsonClass(generateAdapter = true)
data class NextEpisodeDto(
    val id: Int,
    val name: String? = null,
    /** "2026-05-12" */
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "episode_number") val episodeNumber: Int? = null
)