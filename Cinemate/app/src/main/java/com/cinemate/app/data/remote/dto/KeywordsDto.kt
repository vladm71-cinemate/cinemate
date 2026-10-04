package com.cinemate.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KeywordsDto(
    /** У фильмов: /movie/{id}/keywords -> {"keywords": [...]} */
    val keywords: List<KeywordDto>? = null,
    /** У сериалов: /tv/{id}/keywords -> {"results": [...]} */
    val results: List<KeywordDto>? = null
) {
    /** Независимо от типа контента — список слов. */
    fun all(): List<KeywordDto> = keywords ?: results ?: emptyList()
}

@JsonClass(generateAdapter = true)
data class KeywordDto(
    val id: Int,
    val name: String
)