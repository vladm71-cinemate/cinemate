package com.cinemate.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Ответ /movie/{id}/credits и /tv/{id}/credits.
 * Берем только актёров (cast); съёмочную группу (crew) не парсим.
 */
@JsonClass(generateAdapter = true)
data class CreditsDto(
    val cast: List<CastMemberDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CastMemberDto(
    val id: Int,
    val name: String,
    /** Роль/персонаж ("Джон Уик"). Может отсутствовать. */
    val character: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    /** Порядок в титрах — сортируем по нему. */
    val order: Int? = null
)