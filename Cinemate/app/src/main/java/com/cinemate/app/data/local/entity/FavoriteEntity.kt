package com.cinemate.app.data.local.entity

import androidx.room.Entity

/**
 * Избранное. Составной ключ (id + type): фильм и сериал с одним id
 * TMDb — разные записи.
 */
@Entity(tableName = "favorites", primaryKeys = ["id", "type"])
data class FavoriteEntity(
    val id: Int,
    val type: String,        // "movie" | "tv"
    val title: String,
    val posterPath: String?,
    val year: String?,       // "2019"
    val rating: Double,
    /** Момент добавления, epoch millis. Для сортировки «по дате добавления». */
    val addedAt: Long = 0L
)