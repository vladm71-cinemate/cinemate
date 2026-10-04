package com.cinemate.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Ответ /movie/{id}/release_dates.
 * У каждого релиза type: 1=Premiere, 2=Limited, 3=Theatrical,
 * 4=Digital, 5=Physical. По ТЗ ищем 4 или 5.
 */
@JsonClass(generateAdapter = true)
data class ReleaseDatesResponseDto(
    val id: Int,
    val results: List<CountryReleaseDatesDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CountryReleaseDatesDto(
    @Json(name = "iso_3166_1") val countryCode: String,
    @Json(name = "release_dates") val releaseDates: List<ReleaseDateEntryDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ReleaseDateEntryDto(
    /** "2026-03-01T00:00:00.000Z" — берём первые 10 символов как дату. */
    @Json(name = "release_date") val releaseDate: String? = null,
    val type: Int? = null
)