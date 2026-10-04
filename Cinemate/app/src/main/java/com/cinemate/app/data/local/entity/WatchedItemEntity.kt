package com.cinemate.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * История «Смотрел»: что запускали на боксе через «Смотреть».
 * Первичный ключ — infoHash магнита: повторный запуск обновляет дату, а не дубль.
 */
@Entity(tableName = "watched_items")
data class WatchedItemEntity(
    @PrimaryKey val infoHash: String,
    val title: String,
    val posterPath: String?,
    /** Epoch millis последнего запуска. */
    val watchedAt: Long
)