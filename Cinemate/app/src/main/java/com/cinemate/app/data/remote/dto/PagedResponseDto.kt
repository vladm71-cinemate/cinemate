package com.cinemate.app.data.remote.dto

import com.squareup.moshi.Json

/**
 * Общая обёртка пагинированных ответов TMDb.
 * Это дженерик — без @JsonClass: его собирает KotlinJsonAdapterFactory (рефлексия).
 */
data class PagedResponseDto<T>(
    val page: Int = 0,
    val results: List<T> = emptyList(),
    @Json(name = "total_pages") val totalPages: Int = 0,
    @Json(name = "total_results") val totalResults: Int = 0
)
