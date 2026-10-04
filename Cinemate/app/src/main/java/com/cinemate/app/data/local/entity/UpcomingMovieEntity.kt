package com.cinemate.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ожидаемые фильмы: проверка выхода и уведомления — этап 8. */
@Entity(tableName = "upcoming_movies")
data class UpcomingMovieEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val posterPath: String?,
    val releaseDate: String?,    // "2026-03-01" (цифровой релиз уточним на этапе 8)
    val notified: Boolean = false
)
