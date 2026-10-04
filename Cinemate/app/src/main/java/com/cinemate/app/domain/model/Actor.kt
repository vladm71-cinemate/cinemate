package com.cinemate.app.domain.model

/** Актёр из блока credits. */
data class Actor(
    val id: Int,
    val name: String,
    /** Роль/персонаж. */
    val character: String?,
    /** Путь к фото профиля (может быть null — покажем заглушку). */
    val profilePath: String?
)