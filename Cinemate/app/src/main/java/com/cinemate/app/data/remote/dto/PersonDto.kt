package com.cinemate.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Ответ /person/{id} — данные актёра. */
@JsonClass(generateAdapter = true)
data class PersonDto(
    val id: Int,
    val name: String,
    val biography: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    /** Известность: "Актёр", "Режиссёр"... */
    @Json(name = "known_for_department") val knownForDepartment: String? = null,
    @Json(name = "birthday") val birthday: String? = null,
    @Json(name = "place_of_birth") val placeOfBirth: String? = null
)

/**
 * Ответ /person/{id}/combined_credits — все фильмы и сериалы
 * с участием персоны. Поля названия — как у MovieDto (title/name).
 */
@JsonClass(generateAdapter = true)
data class PersonCreditsDto(
    val id: Int,
    val cast: List<MovieDto> = emptyList(),
    val crew: List<MovieDto> = emptyList()
)